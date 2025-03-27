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
package com.couchbase.lite.mobile.android.test.bt.provider

import kotlinx.coroutines.flow.Flow


sealed class Peer(val id: String) {
    data class VisiblePeer(
        val pid: String,
        val name: String,
        val address: String,
        val rssi: Int,
        val metadata: Map<String, Any> = emptyMap()
    ) : Peer(pid) {
        override fun hashCode() = super.hashCode()
        override fun equals(o: Any?) = super.equals(o)
        override fun toString() = "${name} @${address}"
    }

    data class VanishedPeer(val pid: String) : Peer(pid) {
        override fun hashCode() = super.hashCode()
        override fun equals(o: Any?) = super.equals(o)
    }

    override fun hashCode() = id.hashCode()

    override fun equals(o: Any?): Boolean {
        val p = o as? Peer ?: return false
        return id == p.id
    }
}

interface Provider {
    val PERMISSIONS: List<String>
    suspend fun startPublishing(): Flow<Boolean>?
    suspend fun startBrowsing(): Flow<Set<Peer>>?
}