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

abstract class Peer(val id: String) {
    override fun hashCode() = id.hashCode()

    override fun equals(other: Any?): Boolean {
        val p = other as? Peer ?: return false
        return id == p.id
    }
}

class VanishedPeer(id: String) : Peer(id) {
    override fun toString() = "-${id}"
}

open class VisiblePeer(
    id: String,
    val name: String,
    val address: String,
    val port: Int?
) : Peer(id) {
    override fun toString() = "+${name}(${id}) @${address}:${port}"
}

class ConnectedPeer(
    id: String,
    name: String,
    address: String,
    port: Int?
) : VisiblePeer(id, name, address, port) {
    override fun toString() = "o${name}(${id}) @${address}:${port}"
}


interface Provider {
    val PERMISSIONS: Set<String>
    suspend fun startPublishing(): Flow<Boolean>?
    suspend fun startBrowsing(): Flow<Peer>?
    suspend fun connect(peer: VisiblePeer): Flow<String>?
    suspend fun send(peer: ConnectedPeer, msg: String)
}
