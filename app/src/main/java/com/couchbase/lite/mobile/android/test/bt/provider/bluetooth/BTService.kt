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
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import com.couchbase.lite.mobile.android.test.bt.provider.Peer
import com.couchbase.lite.mobile.android.test.bt.provider.Provider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.lang.ref.WeakReference


class BTService(_context: Context) : Provider {
    companion object {
        private const val TAG = "BT_SVC"

        // Couchbase Lite P2P sync service UUID (randomly generated)
        private const val P2P_NAMESPACE_ID = "E0C3793A-0739-42A2-A800-8BED236D8815"

        // Service characteristic whose value is the L2CAP port (PSM) the peer is listening on
        private const val PORT_CHARACTERISTIC_ID = "ABDD3056-28FA-441D-A470-55A75A52553A"

        // Service characteristic whose value is the peer's Fleece-encoded metadata (randomly generated)
        private const val METADATA_CHARACTERISTIC_ID = "936D7669-E532-42BF-8B8D-97E3C1073F74"

    }

    override val PERMISSIONS = if (android.os.Build.VERSION.SDK_INT <= 30)
        listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    else
        listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,

            // Android 12+ permissions
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT
        )

    private val ctxt = WeakReference(_context.applicationContext)

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    override fun startPublishing(): Flow<Boolean>? {
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
                    Log.d(TAG, "Publication started")
                    trySend(true)
                }

                override fun onStartFailure(errorCode: Int) {
                    Log.d(TAG, "Publication failed: $errorCode")
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

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun startBrowsing(): Flow<Set<Peer>>? {
        val btAdapter = getBTAdapter(ctxt.get()) ?: return null
        val scanner = btAdapter.bluetoothLeScanner

        val settings = ScanSettings.Builder()
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setLegacy(false)
            .setMatchMode(ScanSettings.MATCH_MODE_STICKY)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()


        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid.fromString(P2P_NAMESPACE_ID))
            .build()

        return callbackFlow {
            val scanCallback = object : ScanCallback() {
                @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val device = result.device
                    trySend(setOf(Peer(device.name, device.address, result.rssi)))
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.i(TAG, "Browsing failed")
                }

                override fun onBatchScanResults(results: List<ScanResult?>?) {
                    Log.i(TAG, "batch???")
                }
            }

            scanner.startScan(listOf(filter), settings, scanCallback)
            Log.i(TAG, "Browsing started")

            awaitClose {
                scanner.stopScan(scanCallback)
                Log.i(TAG, "Browsing closed")
            }
        }
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
