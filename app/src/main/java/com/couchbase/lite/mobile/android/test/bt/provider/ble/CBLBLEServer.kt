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
import android.util.Log
import java.util.UUID
import kotlin.random.Random


// Couchbase Lite P2P sync service UUID (randomly generated)
val P2P_NAMESPACE_ID: UUID = UUID.fromString("E0C3793A-0739-42A2-A800-8BED236D8815")

// Service characteristic whose value is the L2CAP port (PSM) the peer is listening on
val PORT_CHARACTERISTIC_ID: UUID = UUID.fromString("ABDD3056-28FA-441D-A470-55A75A52553A")

// Service characteristic whose value is the peer's Fleece-encoded metadata (randomly generated)
val METADATA_CHARACTERISTIC_ID: UUID = UUID.fromString("936D7669-E532-42BF-8B8D-97E3C1073F74")


@SuppressWarnings("MissingPermission")
class CBLBLEServer(private val btService: BTService) : BluetoothGattServerCallback() {
    companion object {
        private const val TAG = "BT_SERVER"
        private val DEVICE_ID = Random.nextInt(999999).toString().toByteArray(Charsets.UTF_8)
    }

    private var gattServer: BluetoothGattServer? = null

    override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
        Log.i(TAG, "state change(${device.address}, ${status}): ${newState}")
    }

    override fun onServiceAdded(status: Int, service: BluetoothGattService) {
        Log.i(TAG, "service added(${status}): ${service}")
    }

    override fun onCharacteristicReadRequest(
        device: BluetoothDevice,
        requestId: Int,
        offset: Int,
        characteristic: BluetoothGattCharacteristic
    ) {
        Log.i(TAG, "characteristic read request(${device.address}): ${characteristic.uuid}")
        when (characteristic.uuid) {
            PORT_CHARACTERISTIC_ID -> {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, DEVICE_ID)
            }

            METADATA_CHARACTERISTIC_ID -> {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }

            else -> {
                Log.w(TAG, "read for unrecognized characteristic")
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
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
        Log.i(TAG, "characteristic write request(${device.address}): ${requestId}")
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
        Log.i(TAG, "descriptor write request(${device.address}):: ${requestId}")
    }

    override fun onDescriptorReadRequest(
        device: BluetoothDevice,
        requestId: Int,
        offset: Int,
        descriptor: BluetoothGattDescriptor
    ) {
        Log.i(TAG, "descriptor read request(${device.address}):: ${requestId}")
    }

    override fun onNotificationSent(device: BluetoothDevice, status: Int) {
        Log.i(TAG, "notification sent(${device.address}, ${status})")
    }

    override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
        Log.i(TAG, "mtu changed(${device.address}): ${mtu}")
    }

    override fun onExecuteWrite(device: BluetoothDevice, requestId: Int, execute: Boolean) {
        Log.i(TAG, "execute write(${device.address}): ${requestId}, ${execute}")
    }

    fun start() {
        val cblService = BluetoothGattService(P2P_NAMESPACE_ID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        cblService.addCharacteristic(
            BluetoothGattCharacteristic(
                PORT_CHARACTERISTIC_ID,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
        )
        val server = btService.btMgr.openGattServer(btService.context, this)
        server.addService(cblService)
        gattServer = server
    }

    fun stop() = gattServer?.close()
}