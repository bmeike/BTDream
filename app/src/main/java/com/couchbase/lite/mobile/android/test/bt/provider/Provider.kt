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

import com.couchbase.lite.mobile.android.test.bt.provider.ble.CBLBLEDevice
import kotlinx.coroutines.flow.Flow


interface Provider {
    val PERMISSIONS: Set<String>
    suspend fun startPublishing(): Flow<PublisherState>?
    suspend fun startBrowsing(): Flow<Peer>?
    suspend fun startServer(): Flow<PublisherState>?
    suspend fun connectToPeer(peer: Peer): Flow<String>?
    suspend fun sendToPeer(peer: Peer, msg: String)
}

sealed interface PublisherState {
    class Started() : PublisherState
    data class Message(val peer: Peer, val msg: String) : PublisherState
    data class Stopped(val err: Throwable? = null) : PublisherState
}

class Peer(device: CBLBLEDevice, val state: State = State.DISCOVERED) {
    enum class State(private val sym: String) {
        DISCOVERED("+"),
        CONNECTED("!"),
        LOST("-");

        override fun toString() = sym
    }

    val id: String = device.cblId ?: device.address
    val name: String = device.name
    val address: String = device.address
    val port: Int? = device.port

    override fun toString() = "${state}${name}(${id}) @${address}:${port}"

    override fun hashCode() = id.hashCode()

    override fun equals(other: Any?): Boolean {
        val p = other as? Peer ?: return false
        return id == p.id
    }
}
