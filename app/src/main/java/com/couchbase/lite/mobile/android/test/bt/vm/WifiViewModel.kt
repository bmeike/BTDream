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

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.couchbase.lite.mobile.android.test.bt.provider.Peer
import com.couchbase.lite.mobile.android.test.bt.provider.wifi.WifiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.launch


class WifiViewModel(private val wifiService: WifiService) : ProviderViewModel() {
    companion object {
        private const val TAG = "WIFI_MODEL"
    }


    private var browser: Job? = null
    override val peers = mutableStateOf(emptyList<Peer.VisiblePeer>())

    override fun getRequiredPermissions(context: Context) = wifiService.PERMISSIONS

    override fun startBrowsing() {
        synchronized(this) {
            if (browser != null) {
                return
            }

            browser = viewModelScope.launch(Dispatchers.IO) {
                try {
                    wifiService.startBrowsing()?.cancellable()?.collect { changedPeers ->
                        val currentPeers = peers.value.toMutableList()
                        changedPeers.forEach {
                            when (it) {
                                is Peer.VisiblePeer -> currentPeers.add(it)
                                is Peer.VanishedPeer -> currentPeers.remove(it as Peer)
                            }
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
    }

    override fun startPublishing() = Unit

    override fun stopPublishing() = Unit
}
