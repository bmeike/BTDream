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
import androidx.compose.runtime.mutableStateOf
import com.couchbase.lite.mobile.android.test.bt.provider.Peer
import com.couchbase.lite.mobile.android.test.bt.provider.wifi.WifiService


class WifiViewModel(private val wifiService: WifiService) : ProviderViewModel() {
    companion object {
        private const val TAG = "WIFI_MODEL"
    }

    override val peers = mutableStateOf(emptyList<Peer.VisiblePeer>())

    override fun getRequiredPermissions(context: Context) = wifiService.PERMISSIONS

    override fun startBrowsing() = Unit

    override fun stopBrowsing() = Unit

    override fun startPublishing() = Unit

    override fun stopPublishing() = Unit
}
