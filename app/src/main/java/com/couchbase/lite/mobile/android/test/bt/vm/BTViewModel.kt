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
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.util.fastFilterNotNull
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import com.couchbase.lite.mobile.android.test.bt.provider.Peer
import com.couchbase.lite.mobile.android.test.bt.provider.ble.BTService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.launch


class BTViewModel(private val btService: BTService) : ProviderViewModel() {
    companion object {
        private const val TAG = "BT_MODEL"
    }

    override val peers = mutableStateOf(emptyList<Peer.VisiblePeer>())

    private var browser: Job? = null
    private var publisher: Job? = null

    override fun getRequiredPermissions(context: Context) = btService.PERMISSIONS.map {
        if (ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED) it else null
    }.fastFilterNotNull()

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    override fun startPublishing() {
        synchronized(this) {
            if (publisher != null) {
                return
            }

            publisher = viewModelScope.launch(Dispatchers.IO) {
                try {
                    btService.startPublishing().collect {
                        Log.i(TAG, "Publishing: $it")
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "Insufficient permissions for publication", e)
                }
            }
        }
    }

    override fun stopPublishing() {
        val job: Job?
        synchronized(this) {
            job = publisher
            publisher = null
        }
        job?.cancel()
    }

    override fun startBrowsing() {
        synchronized(this) {
            if (browser != null) {
                return
            }

            browser = viewModelScope.launch(Dispatchers.IO) {
                try {
                    btService.startBrowsing().cancellable().collect { peer ->
                        val currentPeers = peers.value.toMutableList()
                        when (peer) {
                            is Peer.VisiblePeer -> currentPeers.add(peer)
                            is Peer.VanishedPeer -> currentPeers.remove(peer as Peer)
                        }
                        peers.value = currentPeers.toList()
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "Insufficient permissions for discovery", e)
                }
            }
        }
    }

    override fun stopBrowsing() {
        val job: Job?
        synchronized(this) {
            job = browser
            browser = null
        }
        job?.cancel()
        peers.value = emptyList()
    }
}