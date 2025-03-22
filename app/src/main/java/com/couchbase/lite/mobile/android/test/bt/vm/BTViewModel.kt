//
// Copyright (c) 2023 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.mobile.android.test.bt.vm

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.couchbase.lite.mobile.android.test.bt.provider.bluetooth.BTService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.launch


class BTViewModel(private val btService: BTService) : ProviderViewModel() {
    companion object {
        private const val TAG = "BT_MODEL"

        private val BT_TYPES = mapOf(
            0 to "Unknown",
            1 to "Classic",
            2 to "LE",
            3 to "Dual"
        )
    }

    interface Publisher : DefaultLifecycleObserver {
        fun startPublishing()
    }

    override val peers = mutableStateOf(emptySet<String>())

    private var discoveryJob: Job? = null
    private var publisher: Publisher? = null

    override fun init(act: ComponentActivity) {
        val publicationContract = object : ActivityResultContract<Int, Any?>() {
            override fun createIntent(context: Context, input: Int): Intent {
                return Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                    putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, input)
                }
            }

            override fun parseResult(resultCode: Int, intent: Intent?): Any? {
                return if (resultCode == BluetoothAdapter.ERROR) null else resultCode
            }
        }

        val registry = act.activityResultRegistry

        // ??? I'm don't really know if this is necessary or sufficient
        // to unregister the activity result launcher when the activity is destroyed
        val pub = object : Publisher {
            var launcher: ActivityResultLauncher<Int>? = null

            override fun onCreate(owner: LifecycleOwner) {
                launcher = registry.register("bt.publisher", owner, publicationContract) { t ->
                    android.util.Log.d(TAG, "Visible in bluetooth for $t seconds")
                }
            }

            override fun startPublishing() = launcher?.launch(300) ?: Unit
        }

        act.lifecycle.addObserver(pub)
        publisher = pub
    }

    override fun getRequiredPermissions() = btService.PERMISSIONS

    override fun startPublishing() = publisher?.startPublishing() ?: Unit

    override fun stopPublishing() = Unit

    override fun startBrowsing() {
        synchronized(this) {
            if (discoveryJob != null) {
                return
            }

            discoveryJob = viewModelScope.launch(Dispatchers.IO) {
                try {
                    btService.startBrowsing()?.cancellable()?.collect {
                        val devType = BT_TYPES[it.peer.type] ?: "Unknown"
                        val peer = "${it.peer.name}: ${devType} @${it.peer.address}"

                        if (it.visible) {
                            peers.value = peers.value + peer
                        } else {
                            peers.value = peers.value - peer
                        }
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "Insufficient permissions for discovery", e)
                }
            }
        }
    }

    override fun stopBrowsing() {
        val job = discoveryJob
        discoveryJob = null
        job?.cancel()
    }
}