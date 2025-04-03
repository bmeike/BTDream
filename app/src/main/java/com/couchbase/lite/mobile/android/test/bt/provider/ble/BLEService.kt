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

import android.Manifest
import android.bluetooth.BluetoothAdapter
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.cancellable
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


// Don't dispatch tasks too quickly:  Try to wait until
// the previous task is finished... but give up after a while.
class BlockingTask(private val task: Runnable) : Runnable {
    private val latch = CountDownLatch(1)

    override fun run() {
        task.run()
        latch.await(2, TimeUnit.SECONDS)
    }

    fun done() = latch.countDown()
}

@SuppressWarnings("MissingPermission")
class BTService(context: Context) : Provider {
    companion object {
        private const val TAG = "BT_SVC"
    }

    override val PERMISSIONS: List<String>

    interface DeviceListener {
        fun onDeviceFound(scan: ScanResult)
    }

    val context
        get() = ctxt.get() ?: throw IllegalStateException("Context is null")

    val btMgr
        get() = ctxt.get()?.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: throw IllegalStateException("Bluetooth not supported on this device")

    val btAdapter: BluetoothAdapter
        get() = btMgr.adapter


    private val btExecutor: Executor = Executors.newSingleThreadExecutor()
    private val ctxt = WeakReference(context.applicationContext)

    private val pendingDevices: MutableMap<String, CBLBLEDevice> = mutableMapOf()
    private val cblPeers: MutableMap<String, CBLBLEDevice> = mutableMapOf()

    private val btServer = CBLBLEServer(this)

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

    fun runTaskBlocking(task: () -> Unit): BlockingTask {
        val blockingTask = BlockingTask(task)
        btExecutor.execute(blockingTask)
        return blockingTask
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    override suspend fun startPublishing(): Flow<Boolean> {
        val advertiser = btAdapter.bluetoothLeAdvertiser

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(P2P_NAMESPACE_ID))
            .setIncludeDeviceName(true)
            .build()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        btServer.start()

        return callbackFlow {
            var advertisingTask: BlockingTask? = null

            val advertiseCallback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    advertisingTask?.done()
                    Log.i(TAG, "Publication running")
                    trySend(true)
                }

                override fun onStartFailure(errorCode: Int) {
                    advertisingTask?.done()
                    Log.w(TAG, "Publication failed: $errorCode")
                    trySend(false)
                }
            }

            advertisingTask = runTaskBlocking {
                advertiser.startAdvertising(settings, data, null, advertiseCallback)
                Log.i(TAG, "Publication started")
            }

            awaitClose {
                runTaskBlocking {
                    advertiser.stopAdvertising(advertiseCallback)
                    btServer.stop()
                    Log.i(TAG, "Publication stopped")
                }.done()
            }
        }.cancellable()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override suspend fun startBrowsing(): Flow<Peer> {
        Log.i(TAG, "Browsing started")
        return callbackFlow {
            val peerListener = object : PeerListener {
                override fun addPeer(peer: CBLBLEDevice) {
                    pendingDevices.remove(peer.address)

                    val id = peer.cblId ?: return
                    if (cblPeers.containsKey(id)) {
                        return
                    }

                    cblPeers[id] = peer
                    trySend(Peer.VisiblePeer(id, peer.name, peer.address, peer.rssi))
                }

                override fun removePeer(peer: CBLBLEDevice) {
                    // !!! A device that fails will stay failed forever.
                    if (peer.state != CBLBLEDevice.State.FAILED) {
                        pendingDevices.remove(peer.address)
                    }

                    val id = peer.cblId ?: return
                    if (cblPeers.remove(id) != null) {
                        trySend(Peer.VanishedPeer(id))
                    }
                }
            }

            scan { scan ->
                val device = scan.device
                val addr = device.address

                // ??? Should check the pending device to see if anything has changed
                // and replace and reconnect if appropriate?
                if (!pendingDevices.containsKey(addr)) {
                    val cblDevice = CBLBLEDevice(this@BTService, device, scan.rssi, peerListener)
                    pendingDevices[addr] = cblDevice
                    cblDevice.connect()
                }
            }

            awaitClose {
                Log.i(TAG, "Browsing stopped")
            }
        }.cancellable()
    }

    private fun scan(scanListener: (ScanResult) -> Unit) {
        val settings = ScanSettings.Builder()
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setLegacy(false)
            .setMatchMode(ScanSettings.MATCH_MODE_STICKY)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .build()

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(P2P_NAMESPACE_ID))
            .build()

        val btScanner = btAdapter.bluetoothLeScanner

        var advertisingTask: BlockingTask? = null

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                advertisingTask?.done()
                scanListener(result)
            }

            override fun onScanFailed(errorCode: Int) {
                advertisingTask?.done()
                Log.i(TAG, "Browsing failed")
            }

            override fun onBatchScanResults(results: List<ScanResult?>?) {
                Log.i(TAG, "batch???")
            }
        }

        advertisingTask = runTaskBlocking {
            btScanner.startScan(listOf(filter), settings, scanCallback)
            Log.i(TAG, "Scan started")
        }
    }
}

