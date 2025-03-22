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
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.os.ParcelUuid
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresPermission
import com.couchbase.lite.mobile.android.test.bt.provider.PeerVisibilityChange
import com.couchbase.lite.mobile.android.test.bt.provider.Provider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.Executor


class BTService(_context: Context) : Provider {
    companion object {
        private const val TAG = "BT_SVC"
    }

    override val PERMISSIONS = listOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT
    )

    private val executor: Executor = Executor { it.run() }
    private val ctxt = WeakReference(_context.applicationContext)


    override fun init() = TODO("Not yet implemented")

    override fun startPublishing(act: ComponentActivity) = TODO("Not yet implemented")

    override fun stopPublishing() = TODO("Not yet implemented")

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun startBrowsing(): Flow<PeerVisibilityChange>? {
        return startBrowsingWithBroadcastReceiver()
    }

    override fun stopBrowsing() = Unit

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun startBrowsingWithBroadcastReceiver(): Flow<PeerVisibilityChange>? {
        val context = ctxt.get() ?: return null
        val btAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: return null

        return callbackFlow {
            var oldPeers = setOf<BluetoothDevice>()
            var visiblePeers = mutableSetOf<BluetoothDevice>()

            val receiver = object : BroadcastReceiver() {
                @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
                override fun onReceive(context: Context, intent: Intent) {
                    when (intent.action) {
                        BluetoothDevice.ACTION_FOUND -> {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)?.let {
                                if (!visiblePeers.contains(it)) {
                                    trySend(PeerVisibilityChange(it, true))
                                    visiblePeers.add(it)
                                }
                            }
                        }

                        BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                            Log.i(TAG, "Discovery started")
                        }

                        BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                            Log.i(TAG, "Discovery complete")
                            val vanished = oldPeers - visiblePeers
                            vanished.forEach { trySend(PeerVisibilityChange(it, false)) }
                            oldPeers = visiblePeers
                            visiblePeers = mutableSetOf()
                            btAdapter.startDiscovery()
                        }
                    }
                }
            }

            context.registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
            context.registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED))
            context.registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED))

            btAdapter.startDiscovery()

            awaitClose { ctxt.get()?.unregisterReceiver(receiver) }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun startBrowsingWithCompanionDeviceMgr(): Flow<PeerVisibilityChange>? {
        val context = ctxt.get() ?: return null
        val devMgr = (context.getSystemService(Context.COMPANION_DEVICE_SERVICE) as? CompanionDeviceManager)
            ?: return null

        val deviceFilter: BluetoothDeviceFilter = BluetoothDeviceFilter.Builder()
            .addServiceUuid(ParcelUuid(UUID(0x123abcL, -1L)), null)
            .build()

        val pairingRequest: AssociationRequest = AssociationRequest.Builder()
            .addDeviceFilter(deviceFilter)
            .build()

        return callbackFlow {
            devMgr.associate(
                pairingRequest,
                executor,
                object : CompanionDeviceManager.Callback() {
                    override fun onAssociationPending(intentSender: IntentSender) {
                        intentSender?.let {
//                            context.startIntentSenderForResult(it, 0, null, 0, 0, 0)
                        }
                    }

                    override fun onAssociationCreated(associationInfo: AssociationInfo) {
//                        trySend(PeerVisibilityChange(associationInfo.associatedDevice, true))
                    }

                    override fun onFailure(errorMessage: CharSequence?) {
                    }
                })
            awaitClose { }
        }
    }
}
