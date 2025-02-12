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

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.couchbase.lite.mobile.android.test.bt.bluetooth.BTService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BTViewModel(private val btService: BTService) : ViewModel() {
    companion object {
        const val TAG = "BT_MODEL"
    }

    private var _peers: SnapshotStateList<String>? = null

    fun start(): SnapshotStateList<String> {
        var peers = _peers
        if (peers != null) return peers

        peers = mutableStateListOf()
        val peerFlow = btService.startDiscovery()
        viewModelScope.launch(Dispatchers.IO) {
            peerFlow.collect { peers.addAll(it) }
        }
        _peers = peers
        return peers
    }

    fun stop() {
        var peers = _peers
        _peers = null
        if (peers != null) {
            btService.stopDiscovery()
        }
    }
}