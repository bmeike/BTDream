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
import android.os.Build
import android.util.Log
import androidx.lifecycle.AtomicReference
import com.couchbase.lite.mobile.android.test.bt.provider.CBLDevice
import com.couchbase.lite.mobile.android.test.bt.provider.Peer
import com.couchbase.lite.mobile.android.test.bt.provider.Provider
import com.couchbase.lite.mobile.android.test.bt.provider.PublisherState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.ServerSocket
import kotlin.random.Random


const val SERVICE_TYPE = "_couchbaseP2P._tcp."

class CBLWiFiDevice(
    override val cblId: String?,
    override val address: String,
    override val name: String,
    override val port: Int?,
    override val metadata: Map<String, Any>?
) : CBLDevice


class WifiService(context: Context) : Provider {
    companion object {
        const val TAG = "WIFI_SVC"
    }

    override val PERMISSIONS: Set<String>

    val context
        get() = ctxt.get() ?: throw IllegalStateException("Context is null")

    val wifiMgr
        get() = ctxt.get()?.getSystemService(Context.NSD_SERVICE) as? NsdManager
            ?: throw IllegalStateException("NSD not supported on this device")

    private val ctxt = WeakReference(context.applicationContext)
    private var serverSocket = AtomicReference<ServerSocket?>(null)

    private val devicesByAddress: MutableMap<String, CBLWiFiDevice> = mutableMapOf()

    init {
        val perms = mutableSetOf(
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.CHANGE_NETWORK_STATE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        PERMISSIONS = perms.toSet()
    }

    override suspend fun startServer(): Flow<PublisherState> {
        return callbackFlow {

            awaitClose {
                Log.i(TAG, "Server ended")
            }
        }
    }

    override suspend fun startPublishing(): Flow<PublisherState> {
        val socket = ServerSocket(0)
        if (!serverSocket.compareAndSet(null, socket)) {
            Log.i(TAG, "Server socket already open")
            socket.close()
            return flowOf(PublisherState.Stopped(IOException("Server socket already open")))
        }

        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                val skt = serverSocket.get() ?: break
                skt.accept()?.let {
                    Log.w(TAG, "received l2cap connection from ${it.inetAddress.hostAddress}")
                }
            }
        }

        return callbackFlow {
            val registrationCallback = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                    Log.i(TAG, "Service registered")
                    trySend(PublisherState.Started())
                }

                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.i(TAG, "Service registration failed")
                    trySend(PublisherState.Stopped(IOException("Registration failed: ${errorCode}")))
                }

                override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                    Log.i(TAG, "Service unregistered")
                    trySend(PublisherState.Stopped())
                }

                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.i(TAG, "Service unregistration failed")
                    trySend(PublisherState.Stopped(IOException("Unregistration failed???: ${errorCode}")))
                }
            }

            val serviceInfo = NsdServiceInfo().apply {
                serviceName = "BTDreams-${Random.nextInt(10, 99)}"
                serviceType = SERVICE_TYPE
                port = socket.localPort
            }

            wifiMgr.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationCallback)
            Log.i(TAG, "Registration begun")

            awaitClose {
                Log.i(TAG, "Registration ended")
            }
        }.cancellable()
    }

    override suspend fun startBrowsing(): Flow<Peer> {
        return callbackFlow {
            val resolveListener = object : NsdManager.ResolveListener {

                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    // Called when the resolve fails. Use the error code to debug.
                    Log.w(TAG, "Resolve failed: $errorCode")
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    Log.i(TAG, "Resolve succeeded. $serviceInfo")
                    val address = serviceInfo.host?.hostAddress ?: return
                    val device = CBLWiFiDevice(null, serviceInfo.serviceName, address, serviceInfo.port, null)
                    devicesByAddress.put(address, device)
                    trySend(Peer(device))
                }
            }
            val discoveryListener = object : NsdManager.DiscoveryListener {
                // Called as soon as service discovery begins.
                override fun onDiscoveryStarted(regType: String) {
                    Log.d(TAG, "Service discovery started")
                }

                override fun onDiscoveryStopped(serviceType: String) {
                    Log.i(TAG, "Discovery stopped: $serviceType")
                }

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.e(TAG, "Discovery failed: Error code:$errorCode")
                    wifiMgr.stopServiceDiscovery(this)
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.e(TAG, "Discovery failed: Error code:$errorCode")
                    wifiMgr.stopServiceDiscovery(this)
                }

                override fun onServiceFound(service: NsdServiceInfo) {
                    Log.d(TAG, "Service discovered: $service")
                    if (SERVICE_TYPE == service.serviceType) {
                        try { wifiMgr.resolveService(service, resolveListener) }
                        catch (e: Exception) { Log.e(TAG, "Failed to resolve service: $service", e) }
                    }
                }

                override fun onServiceLost(service: NsdServiceInfo) {
                    Log.e(TAG, "Service lost: $service")
                    service.host?.hostAddress?.let { address ->
                        (devicesByAddress.remove(address) as? CBLDevice)?.let { device ->
                            trySend(Peer(device, Peer.State.LOST))
                        }
                    }
                }
            }

            wifiMgr.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)

            awaitClose {
                Log.i(TAG, "Discovery ended")
            }
        }
    }

    override suspend fun connectToPeer(peer: Peer): Flow<String>? {
        Log.i(TAG, "Connect to peer: ${peer}")
        return null
    }

    override suspend fun sendToPeer(peer: Peer, msg: String) {
        Log.i(TAG, "Send to peer: ${peer}")
    }
}
