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
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.util.fastFilterNotNull
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import com.couchbase.lite.mobile.android.test.bt.provider.Peer
import com.couchbase.lite.mobile.android.test.bt.provider.PublisherState
import com.couchbase.lite.mobile.android.test.bt.provider.wifi.WifiService
import com.couchbase.lite.mobile.android.test.bt.vm.BTViewModel.Companion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


class WifiViewModel(private val wifiService: WifiService) : ProviderViewModel() {
    companion object {
        private const val TAG = "WIFI_MODEL"
    }

    override val peers = mutableStateOf(emptyMap<Peer, String>())

    private var browser: Job? = null
    private var publisher: Job? = null
    private var server: Job? = null

    override fun getRequiredPermissions(context: Context) = wifiService.PERMISSIONS.map {
        if (ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED) it else null
    }.fastFilterNotNull().toSet()

    override fun startPublishing() {
        synchronized(this) {
            if (publisher != null) {
                return
            }

            server = viewModelScope.launch(Dispatchers.IO) {
                try {
                    wifiService.startServer().collect {
                        when (it) {
                            is PublisherState.Started -> Log.i(TAG, "Server started")
                            is PublisherState.Stopped -> Log.i(TAG, "Server stopped", it.err)
                            is PublisherState.Message -> {
                                val currentPeers = peers.value.toMutableMap()
                                currentPeers[it.peer] = it.msg
                                peers.value = emptyMap() // ??? do *NOT* ask me why this is needed!  It is.
                                peers.value = currentPeers.toMap()
                            }
                        }
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "Insufficient permissions for server", e)
                }
            }

            publisher = viewModelScope.launch(Dispatchers.IO) {
                try {
                    wifiService.startPublishing().collect {
                        when (it) {
                            is PublisherState.Started -> Log.i(TAG, "Publisher notified started")
                            is PublisherState.Stopped -> Log.i(TAG, "Publisher notified stopped", it.err)
                            else -> Log.w(TAG, "Publisher state: $it")
                        }
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "Insufficient permissions for publisher", e)
                }
            }
        }
    }

    override fun startBrowsing() {
        synchronized(this) {
            if (browser != null) {
                return
            }

            browser = viewModelScope.launch(Dispatchers.IO) {
                try {
                    wifiService.startBrowsing().collect { peer ->
                        Log.d(TAG, "Peer changed state: ${peer}")
                        val currentPeers = peers.value.toMutableMap()
                        currentPeers.remove(peer)
                        if (peer.state != Peer.State.LOST) {
                            currentPeers[peer] = "discovered"
                        }
                        peers.value = emptyMap() // ??? do *NOT* ask me why this is needed!  It is.
                        peers.value = currentPeers.toMap()
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "Insufficient permissions for browser", e)
                }
            }
        }
    }

    override fun connectPeer(peer: Peer) {}

    override fun sendToPeer(peer: Peer, msg: String) {}

    override fun stopPublishing() {
        val publisherJob: Job?
        val serverJob: Job?
        synchronized(this) {
            publisherJob = publisher
            publisher = null
            serverJob = publisher
            server = null
        }
        publisherJob?.cancel()
        serverJob?.cancel()
        Log.i(TAG, "Publisher stopped")
    }

    override fun stopBrowsing() {
        val job: Job?
        synchronized(this) {
            job = browser
            browser = null
        }
        job?.cancel()
        peers.value = emptyMap()
        Log.i(TAG, "Browser stopped")
    }
}
