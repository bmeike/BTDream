package com.couchbase.lite.mobile.android.test.bt.provider.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
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
    private val onData: (connection: BLEL2CAPConnection, data: ByteArray) -> Unit,
    private val onClose: (connection: BLEL2CAPConnection, error: Throwable?) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val socket = AtomicReference<BluetoothSocket?>(_socket)
    private val inputStream: InputStream?
        get() = socket.get()?.inputStream
    private val outputStream: OutputStream?
        get() = socket.get()?.outputStream
    val remoteDevice: BluetoothDevice?
        get() = socket.get()?.remoteDevice

    fun start() {
        inputStream ?: return
        scope.launch {
            val buffer = ByteArray(1024)
            try {
                while (true) {
                    val n = inputStream?.read(buffer) ?: -1
                    if (n < 0) break
                    onData(this@BLEL2CAPConnection, buffer.copyOf(n))
                }
            } catch (e: IOException) {
                close(e)
            }
        }
    }

    fun write(data: ByteArray) {
        inputStream ?: return
        scope.launch {
            try {
                outputStream?.write(data)
                outputStream?.flush()
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