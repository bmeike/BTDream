//
// Copyright (c) 2025 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.lite.mobile.android.test.bt.provider.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.util.Log
import androidx.lifecycle.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID
import kotlin.random.Random


// Couchbase Lite P2P sync service UUID (randomly generated)
val P2P_NAMESPACE_ID: UUID = UUID.fromString("E0C3793A-0739-42A2-A800-8BED236D8815")

// Service characteristic whose value is the ID of the peer device
val ID_CHARACTERISTIC_ID: UUID = UUID.fromString("A33AD1C8-2533-4FCC-B4F6-4465986E2243")

// Service characteristic whose value is the L2CAP port (PSM) the peer is listening on
val PORT_CHARACTERISTIC_ID: UUID = UUID.fromString("ABDD3056-28FA-441D-A470-55A75A52553A")

// Service characteristic whose value is the peer's Fleece-encoded metadata (randomly generated)
val METADATA_CHARACTERISTIC_ID: UUID = UUID.fromString("936D7669-E532-42BF-8B8D-97E3C1073F74")

val CBL_CHARACTERISTICS = mapOf(
    ID_CHARACTERISTIC_ID to "id",
    PORT_CHARACTERISTIC_ID to "port",
    METADATA_CHARACTERISTIC_ID to "metadata"
)

fun Int.toByteArray(): ByteArray {
    val byteArray = ByteArray(2)
    byteArray[0] = (this shr 8 and 0xFF).toByte()
    byteArray[1] = (this and 0xFF).toByte()
    return byteArray
}

fun ByteArray.toInt() = if (size != 2) {
    null
} else {
    (this[0].toInt() and 0xFF shl 8) or (this[1].toInt() and 0xFF)
}

@SuppressWarnings("MissingPermission")
class CBLBLEServer(private val bleService: BLEService) : BluetoothGattServerCallback() {
    companion object {
        private const val TAG = "BLE_SERVER"
        private val DEVICE_ID = Random.nextInt(100000, 999999).toString().toByteArray(Charsets.UTF_8)
    }

    private val lock = Any()
    private val gattServer = AtomicReference<BluetoothGattServer?>(null)
    private val l2CapPort = AtomicReference<Int?>(null)
    private var connections: MutableList<BLEL2CAPConnection>? = null


    fun start(
        authenticateConnection: (BluetoothDevice) -> Boolean,
        onInboundData: (String, ByteArray) -> Unit,
        onConnectionClosed: (String?, Throwable?) -> Unit
    ) {
        startGattServer()
        startL2CAPServer(authenticateConnection, onInboundData, onConnectionClosed)
    }

    fun stop() {
        // setting the gattServer null will stop the L2CAP server
        val server = gattServer.getAndSet(null)
        if (server == null) {
            Log.w(TAG, "gatt server already stopped")
        }

        closeL2CAPConnections()
        l2CapPort.set(null)

        server?.close()
    }

    override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
        Log.d(TAG, "state change for ${device.address}(${status}): ${newState}")
    }

    override fun onServiceAdded(status: Int, service: BluetoothGattService) {
        Log.d(TAG, "service added(${status}): ${service}")
    }

    override fun onCharacteristicReadRequest(
        device: BluetoothDevice,
        requestId: Int,
        offset: Int,
        characteristic: BluetoothGattCharacteristic
    ) {
        Log.i(TAG, "characteristic read request from ${device.address}: ${characteristic.uuid}")
        when (characteristic.uuid) {
            ID_CHARACTERISTIC_ID -> {
                gattServer.get()
                    ?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, DEVICE_ID)
            }

            PORT_CHARACTERISTIC_ID -> {
                gattServer.get()
                    ?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, l2CapPort.get()?.toByteArray())
            }

            METADATA_CHARACTERISTIC_ID -> {
                gattServer.get()
                    ?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }

            else -> {
                Log.w(TAG, "read for unrecognized characteristic")
                gattServer.get()?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
            }
        }
    }

    override fun onCharacteristicWriteRequest(
        device: BluetoothDevice,
        requestId: Int,
        characteristic: BluetoothGattCharacteristic,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray
    ) {
        Log.d(TAG, "characteristic write request from ${device.address}: ${requestId}")
    }

    override fun onDescriptorWriteRequest(
        device: BluetoothDevice,
        requestId: Int,
        descriptor: BluetoothGattDescriptor,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray
    ) {
        Log.d(TAG, "descriptor write request from ${device.address}: ${requestId}")
    }

    override fun onDescriptorReadRequest(
        device: BluetoothDevice,
        requestId: Int,
        offset: Int,
        descriptor: BluetoothGattDescriptor
    ) {
        Log.d(TAG, "descriptor read request from ${device.address}: ${requestId}")
    }

    override fun onNotificationSent(device: BluetoothDevice, status: Int) {
        Log.d(TAG, "notification sent to ${device.address}(${status})")
    }

    override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
        Log.d(TAG, "mtu changed for ${device.address}: ${mtu}")
    }

    override fun onExecuteWrite(device: BluetoothDevice, requestId: Int, execute: Boolean) {
        Log.d(TAG, "execute write for ${device.address}: ${requestId}, ${execute}")
    }

    private fun startGattServer() {
        if (gattServer.get() != null) {
            Log.w(TAG, "gatt server already started")
            return
        }

        val cblService = BluetoothGattService(P2P_NAMESPACE_ID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        CBL_CHARACTERISTICS.keys.forEach { uuid ->
            cblService.addCharacteristic(
                BluetoothGattCharacteristic(
                    uuid,
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ
                )
            )
        }
        val server = bleService.btMgr.openGattServer(bleService.context, this)
        server.addService(cblService)
        Log.i(TAG, "gatt server started")

        gattServer.compareAndSet(null, server)
        if (gattServer.get() != server) {
            server.close()
        }
    }

    private fun startL2CAPServer(
        authenticateConnection: (BluetoothDevice) -> Boolean,
        onInboundData: (String, ByteArray) -> Unit,
        onConnectionClosed: (String?, Throwable?) -> Unit
    ) {
        synchronized(lock) {
            if (connections != null) {
                Log.w(TAG, "l2cap server already started")
                return
            }
            connections = mutableListOf()
        }

        CoroutineScope(Dispatchers.IO).launch {
            var serverSocket: BluetoothServerSocket? = null
            try {
                serverSocket = bleService.btAdapter.listenUsingInsecureL2capChannel()
                Log.i(TAG, "l2cap server started @${serverSocket.psm}")
                if (!l2CapPort.compareAndSet(null, serverSocket.psm)) {
                    Log.w(TAG, "l2cap server already running")
                    return@launch
                }

                while (gattServer.get() != null) {
                    Log.d(TAG, "awaiting l2cap connection")
                    serverSocket.accept()?.let {
                        if (!authenticateConnection(it.remoteDevice)) {
                            Log.w(TAG, "rejected l2cap connection from ${it.remoteDevice.address}")
                            it.close()
                            return@let
                        }
                        openL2CAPConnection(it, onInboundData, onConnectionClosed)
                    }
                }
            } catch (e: IOException) {
                Log.w(TAG, "l2cap server failed", e)
            } finally {
                serverSocket?.close()
                Log.i(TAG, "l2cap server stopped")
            }
        }
    }

    private fun openL2CAPConnection(
        socket: BluetoothSocket,
        onInboundData: (String, ByteArray) -> Unit,
        onConnectionClosed: (String?, Throwable?) -> Unit
    ) {
        val connection = BLEL2CAPConnection(
            socket,
            onInboundData,
            { conn, err ->
                synchronized(lock) { connections?.remove(conn) }
                onConnectionClosed(conn.remoteDevice?.address, err)
            })
        var added: Boolean

        synchronized(lock) { added = connections?.add(connection) ?: false }

        if (!added) {
            connection.close()
            return
        }

        connection.start()
        Log.i(TAG, "accepted l2cap connection from ${socket.remoteDevice.address}")
    }

    private fun closeL2CAPConnections() {
        l2CapPort.set(null)

        val connects: MutableList<BLEL2CAPConnection>?
        synchronized(lock) {
            connects = connections
            connections = null
        }

        connects?.forEach {
            try {
                it.close()
                Log.i(TAG, "shutdown l2cap connection from ${it.remoteDevice?.address}")
            } catch (e: IOException) {
                Log.i(TAG, "failed closing l2cap connection from ${it.remoteDevice?.address}", e)
            }
        }
    }
}