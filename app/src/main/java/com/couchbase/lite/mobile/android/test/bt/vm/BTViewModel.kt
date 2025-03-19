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

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.couchbase.lite.mobile.android.test.bt.bluetooth.BTService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.launch

class BTViewModel(private val btService: BTService) : ServiceModel() {
    companion object {
        private const val TAG = "BT_MODEL"

        private val BT_TYPES = mapOf(
            0 to "Unknown",
            1 to "Classic",
            2 to "LE",
            3 to "Dual"
        )
    }


    override val PERMISSIONS = listOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT
    )

    override val peers = mutableStateOf(emptySet<String>())

    var packageManager: PackageManager? = null

    private var job: Job? = null

    override fun start() {
        if (job != null) {
            return
        }

        job = viewModelScope.launch(Dispatchers.IO) {
            try {
                btService.startDiscovery().cancellable().collect {
                    val devType = BT_TYPES[it.peer.type] ?: "Unknown"
                    val peer = peers.value + "${it.peer.name}: ${devType} @${it.peer.address}"

                    if (it.visible) {
                        peers.value = peers.value + peer
                    } else {
                        peers.value = peers.value - peer
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Discovery failed", e)
            }
        }
    }

    override fun stop() {
        Log.i(TAG, "Stopping: $job")
        val flow = job
        job = null
        flow?.cancel()
    }
}