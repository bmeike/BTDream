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
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.couchbase.lite.mobile.android.test.bt.provider.CBLDevice
import com.couchbase.lite.mobile.android.test.bt.provider.Peer
import com.couchbase.lite.mobile.android.test.bt.provider.Provider
import com.couchbase.lite.mobile.android.test.bt.provider.PublisherState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import java.io.IOException
import java.lang.ref.WeakReference


const val SERVICE_TYPE = "_couchbaseP2P._tcp"

const val PORT = 41331


class WifiService(context: Context) : Provider {
    companion object {
        const val TAG = "WIFI_SVC"
    }

    @SuppressWarnings("InlinedApi")
    override val PERMISSIONS = setOf(
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.CHANGE_NETWORK_STATE,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.NEARBY_WIFI_DEVICES
    )

    val context
        get() = ctxt.get() ?: throw IllegalStateException("Context is null")

    val wifiMgr
        get() = ctxt.get()?.getSystemService(Context.NSD_SERVICE) as? NsdManager
            ?: throw IllegalStateException("Bluetooth not supported on this device")

    private val ctxt = WeakReference(context.applicationContext)


    override suspend fun startPublishing(): Flow<PublisherState> {
        val serviceInfo = NsdServiceInfo()
        serviceInfo.serviceName = "BTDream"
        serviceInfo.serviceType = SERVICE_TYPE
        serviceInfo.setPort(PORT)

        return callbackFlow {
            val registrationCallback = object : NsdManager.RegistrationListener {

                override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                    trySend(PublisherState.Started())
                }

                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    trySend(PublisherState.Stopped(IOException("Registration failed: ${errorCode}")))
                }

                override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                    trySend(PublisherState.Stopped())
                }

                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    trySend(PublisherState.Stopped(IOException("Unregistration failed???: ${errorCode}")))
                }
            }

            wifiMgr.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationCallback)
            Log.i(TAG, "Registration begun")

            awaitClose {
                Log.i(TAG, "Registration ended")
            }
        }.cancellable()
    }

    override suspend fun startServer(): Flow<PublisherState> {
        return listOf(PublisherState.Started(), PublisherState.Stopped()).asFlow()
            .onStart { Log.i(TAG, "Server begun") }
            .onCompletion { Log.i(TAG, "Server ended") }
    }

    override suspend fun startBrowsing(): Flow<Peer> {
        return listOf(
            Peer(object : CBLDevice {
                override val cblId = "this device"
                override val address = "over here"
                override val name = "this device"
                override val port = 1234
                override val metadata: Map<String, Any>? = null
            }),
            Peer(object : CBLDevice {
                override val cblId = "that device"
                override val address = "over there"
                override val name = "that device"
                override val port = 4321
                override val metadata: Map<String, Any>? = null
            })
        ).asFlow()
            .onStart { Log.i(TAG, "Browseing begun") }
            .onCompletion { Log.i(TAG, "Browsing ended") }
    }

    override suspend fun connectToPeer(peer: Peer): Flow<String>? {
        Log.i(TAG, "Connect to peer: ${peer}")
        return null
    }

    override suspend fun sendToPeer(peer: Peer, msg: String) {
        Log.i(TAG, "Send to peer: ${peer}")
    }
}
