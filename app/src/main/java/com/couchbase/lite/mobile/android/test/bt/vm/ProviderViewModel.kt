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
package com.couchbase.lite.mobile.android.test.bt.vm

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.lifecycle.ViewModel
import com.couchbase.lite.mobile.android.test.bt.provider.Peer


abstract class ProviderViewModel : ViewModel() {
    abstract val peers: MutableState<Map<Peer, String>>

    abstract fun getRequiredPermissions(context: Context): Set<String>
    abstract fun startPublishing()
    abstract fun stopPublishing()
    abstract fun startBrowsing()
    abstract fun stopBrowsing()
    abstract fun connectPeer(peer: Peer)
    abstract fun sendToPeer(peer: Peer, msg: String)
}