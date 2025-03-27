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
package com.couchbase.lite.mobile.android.test.bt.provider.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import com.couchbase.lite.mobile.android.test.bt.provider.Peer
import com.couchbase.lite.mobile.android.test.bt.provider.Provider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.isActive
import java.lang.ref.WeakReference
import kotlin.coroutines.CoroutineContext
import kotlin.time.DurationUnit
import kotlin.time.toDuration


private class BTDevice(private val addr: String) : BluetoothGattCallback() {
    override fun onPhyUpdate(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {
        Log.d("CONNECT", "$addr - phy update: $txPhy, $rxPhy, $status")
    }

    override fun onPhyRead(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {
        Log.d("CONNECT", "$addr - phy read: $txPhy, $rxPhy, $status")
    }

    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
        Log.d("CONNECT", "$addr - connection state changed: $status, $newState")
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        Log.d("CONNECT", "$addr - services discovered: $status")
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int
    ) {
        Log.d("CONNECT", "$addr - characteristics read: $characteristic, $status")
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
        Log.d("CONNECT", "$addr - characteristics write: $characteristic, $status")
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        Log.d("CONNECT", "$addr - characteristics changed: $characteristic")
    }

    override fun onDescriptorRead(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int,
        value: ByteArray
    ) {
        Log.d("CONNECT", "$addr - descriptor read: $descriptor, $status")
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt?,
        descriptor: BluetoothGattDescriptor?,
        status: Int
    ) {
        Log.d("CONNECT", "$addr - descriptor write: $descriptor, $status")
    }

    override fun onReliableWriteCompleted(gatt: BluetoothGatt?, status: Int) {
        Log.d("CONNECT", "$addr - write completed:$status")
    }

    override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
        Log.d("CONNECT", "$addr - rssi: $rssi, $status")
    }

    override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
        Log.d("CONNECT", "$addr - mtu changed: $mtu, $status")
    }

    override fun onServiceChanged(gatt: BluetoothGatt) {
        Log.d("CONNECT", "$addr - service changed")
    }
}


class BTService(_context: Context) : Provider {
    companion object {
        private const val TAG = "BT_SVC"

        // Couchbase Lite P2P sync service UUID (randomly generated)
        private const val P2P_NAMESPACE_ID = "E0C3793A-0739-42A2-A800-8BED236D8815"

        // Service characteristic whose value is the L2CAP port (PSM) the peer is listening on
        private const val PORT_CHARACTERISTIC_ID = "ABDD3056-28FA-441D-A470-55A75A52553A"

        // Service characteristic whose value is the peer's Fleece-encoded metadata (randomly generated)
        private const val METADATA_CHARACTERISTIC_ID = "936D7669-E532-42BF-8B8D-97E3C1073F74"

        private val SCAN_DURATION = 10.toDuration(DurationUnit.SECONDS)
    }

    override val PERMISSIONS: List<String>

    init {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        perms += if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
            listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        else
            listOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        PERMISSIONS = perms.toList()
    }

    private val ctxt = WeakReference(_context.applicationContext)
    private val peers: MutableMap<String, ScanResult> = mutableMapOf()

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    override suspend fun startPublishing(): Flow<Boolean>? {
        val btAdapter = getBTAdapter(ctxt.get()) ?: return null
        if ((!btAdapter.isLe2MPhySupported) || (!btAdapter.isLeExtendedAdvertisingSupported)) return null
        val advertiser = btAdapter.bluetoothLeAdvertiser

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid.fromString(P2P_NAMESPACE_ID))
            .setIncludeDeviceName(true)
            .build()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        return callbackFlow {
            val advertiseCallback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    Log.i(TAG, "Publication started")
                    trySend(true)
                }

                override fun onStartFailure(errorCode: Int) {
                    Log.w(TAG, "Publication failed: $errorCode")
                    trySend(false)
                }
            }

            advertiser.startAdvertising(settings, data, null, advertiseCallback)

            awaitClose {
                advertiser.stopAdvertising(advertiseCallback)
                Log.i(TAG, "Publication closed")
            }
        }
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override suspend fun startBrowsing(): Flow<Set<Peer>>? {
        val btAdapter = getBTAdapter(ctxt.get()) ?: return null

        Log.i(TAG, "Browsing started")
        return flow {
            val context = currentCoroutineContext()
            while (context.isActive) {
                val changedPeers = mutableSetOf<Peer>()

                val currentPeers = scanOnce(context, btAdapter)

                (peers.keys - currentPeers.map { it.device.address })
                    .forEach {
                        peers.remove(it)
                        Log.d(TAG, "Peer vanished: $it")
                        changedPeers.add(Peer.VanishedPeer(it))
                    }

                currentPeers.forEach {
                    val id = it.device.address
                    if (!peers.containsKey(id)) {
                        Log.d(TAG, "New peer: $it")
                        changedPeers.add(Peer.VisiblePeer(id, it.device.name, it.device.address, it.rssi))
                    }
                    peers[id] = it
                }

                emit(changedPeers)
                Log.i(TAG, "Browsing completed")
            }
        }.onCompletion {
            peers.clear()
            Log.i(TAG, "Browsing closed")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private suspend fun scanOnce(context: CoroutineContext, btAdapter: BluetoothAdapter): Set<ScanResult> {
        val settings = ScanSettings.Builder()
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setLegacy(false)
            .setMatchMode(ScanSettings.MATCH_MODE_STICKY)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid.fromString(P2P_NAMESPACE_ID))
            .build()

        val currentPeers = mutableSetOf<ScanResult>()

        val scanCallback = object : ScanCallback() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                currentPeers.add(result)
            }

            override fun onScanFailed(errorCode: Int) {
                Log.i(TAG, "Browsing failed")
            }

            override fun onBatchScanResults(results: List<ScanResult?>?) {
                Log.i(TAG, "batch???")
            }
        }

        Log.i(TAG, "Scan started")
        val scanner = btAdapter.bluetoothLeScanner
        try {
            scanner.startScan(listOf(filter), settings, scanCallback)
            if (context.isActive) {
                delay(SCAN_DURATION)
            }
        } finally {
            scanner.stopScan(scanCallback)
            Log.i(TAG, "Scan finished")
        }

        return currentPeers
    }

    private fun getBTAdapter(context: Context?): BluetoothAdapter? {
        context ?: return null
        val btAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (btAdapter == null) {
            Log.w(TAG, "Bluetooth not enabled")
        }
        return btAdapter
    }
}
