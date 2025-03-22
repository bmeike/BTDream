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

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.couchbase.lite.mobile.android.test.bt.provider.wifi.WifiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.launch


class WifiViewModel(private val wifiService: WifiService) : ProviderViewModel() {
    companion object {
        private const val TAG = "WIFI_MODEL"
    }


    private var job: Job? = null
    override val peers = mutableStateOf(emptySet<String>())
    override fun init(act: ComponentActivity) = TODO("Not yet implemented")

    override fun getRequiredPermissions() = wifiService.PERMISSIONS

    override fun startBrowsing() {
        android.util.Log.i(TAG, "Starting: $job")
        if (job == null) {
            job = viewModelScope.launch(Dispatchers.IO) {
                wifiService.startDiscovery().cancellable().collect {
                    val v = peers.value + it
                    peers.value = v
                }
            }
        }
    }

    override fun stopBrowsing() {
        android.util.Log.i(TAG, "Stopping: $job")
        val flow = job
        job = null
        flow?.cancel()
    }

    override fun startPublishing() {
        TODO("Not yet implemented")
    }

    override fun stopPublishing() {
        TODO("Not yet implemented")
    }
}
