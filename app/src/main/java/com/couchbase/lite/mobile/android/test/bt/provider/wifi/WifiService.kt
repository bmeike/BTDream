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
package com.couchbase.lite.mobile.android.test.bt.provider.wifi

import android.Manifest
import androidx.activity.ComponentActivity
import com.couchbase.lite.mobile.android.test.bt.provider.PeerVisibilityChange
import com.couchbase.lite.mobile.android.test.bt.provider.Provider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlin.time.Duration.Companion.seconds

class WifiService : Provider {
    companion object {
        const val TAG = "WIFI_SVC"
    }

    override val PERMISSIONS = listOf(
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.CHANGE_NETWORK_STATE,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.NEARBY_WIFI_DEVICES
    )

    private var i = 0

    fun startDiscovery(): Flow<String> {
        return flow {
            android.util.Log.i(TAG, "Started")
            while (true) {
                delay(10.seconds)
                if (i++ < 0) break
                emit("Wifi#$i")
            }
        }.onCompletion {
            android.util.Log.i(TAG, "Stopped")
        }
    }

    override fun init() {
        TODO("Not yet implemented")
    }

    override fun startPublishing(act: ComponentActivity) {
        TODO("Not yet implemented")
    }

    override fun stopPublishing() {
        TODO("Not yet implemented")
    }

    override fun startBrowsing(): Flow<PeerVisibilityChange>? {
        TODO("Not yet implemented")
    }

    override fun stopBrowsing() {
        TODO("Not yet implemented")
    }
}
