package com.couchbase.lite.mobile.android.test.bt.provider.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import androidx.lifecycle.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class BLEL2CAPConnection(
    _socket: BluetoothSocket,
    private val onData: (String, ByteArray) -> Unit,
    private val onClose: (connection: BLEL2CAPConnection, error: Throwable?) -> Unit
) {
    private val TAG = "BT_CONNECT"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val socket = AtomicReference<BluetoothSocket?>(_socket)
    private val inputStream: InputStream?
        get() = socket.get()?.inputStream
    private val outputStream: OutputStream?
        get() = socket.get()?.outputStream
    val remoteDevice: BluetoothDevice?
        get() = socket.get()?.remoteDevice


    fun start() {
        val ins = inputStream ?: return
        scope.launch {
            val buffer = ByteArray(1024)
            try {
                while (true) {
                    val n = ins.read(buffer) ?: -1
                    if (n < 0) break
                    onInboundData(buffer.copyOf(n))
                }
            } catch (e: IOException) {
                close(e)
            }
        }
    }

    fun onInboundData(data: ByteArray) {
        Log.d(TAG, "received l2cap message from ${remoteDevice}: ${data.size}")
        if (data.isEmpty()) return
        val device = remoteDevice ?: return
        onData(device.address, data)
    }

    fun write(data: ByteArray) {
        Log.d(TAG, "sending l2cap message to ${remoteDevice}: ${data.size}")
        val outs = outputStream ?: return
        scope.launch {
            try {
                outs.write(data)
                outs.flush()
            } catch (e: IOException) {
                close(e)
            }
        }
    }

    fun close(error: Throwable? = null) {
        val skt = socket.getAndSet(null)
        try {
            skt?.close()
        } finally {
            onClose(this@BLEL2CAPConnection, error)
        }
    }
}