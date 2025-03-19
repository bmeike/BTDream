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
package com.couchbase.lite.mobile.android.test.bt.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.lang.ref.WeakReference

data class PeerVisibilityChange(val peer: BluetoothDevice, val visible: Boolean)

class BTService(_context: Context) {
    companion object {
        private const val TAG = "BT_SVC"
    }

    private val ctxt = WeakReference(_context.applicationContext)

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startDiscovery(): Flow<PeerVisibilityChange> {
        val context = ctxt.get() ?: return callbackFlow { awaitClose { } }
        val btAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: return callbackFlow { awaitClose { } }

        return callbackFlow {
            var oldPeers = setOf<BluetoothDevice>()
            var visiblePeers = mutableSetOf<BluetoothDevice>()
            val receiver = object : BroadcastReceiver() {
                @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
                override fun onReceive(context: Context, intent: Intent) {
                    when (intent.action) {
                        BluetoothDevice.ACTION_FOUND -> {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)?.let {
                                Log.i(TAG, "Discovered: ${it.name} @${it.address}")
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
                            val vanished = oldPeers - visiblePeers
                            Log.i(TAG, "Old peers: ${oldPeers}")
                            Log.i(TAG, "Visible Peers: ${visiblePeers}")
                            Log.i(TAG, "Discovery finished: ${vanished}")
                            vanished.forEach {
                                Log.i(TAG, "Vanished: ${it.name} @${it.address}")
                                trySend(PeerVisibilityChange(it, false))
                            }
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
}
