package com.couchbase.lite.mobile.android.test.bt.provider.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.util.Log
import java.util.UUID


interface PeerListener {
    fun addPeer(peer: CBLBLEDevice)
    fun removePeer(peer: CBLBLEDevice)
}

fun BluetoothGattCharacteristic.isReadable() =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_READ)

fun BluetoothGattCharacteristic.isWritable() =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)

fun BluetoothGattCharacteristic.isWritableWithoutResponse() =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)

fun BluetoothGattCharacteristic.containsProperty(property: Int) = properties and property != 0


@SuppressWarnings("MissingPermission")
class CBLBLEDevice(
    private val btService: BTService,
    private val device: BluetoothDevice,
    val rssi: Int,
    private val peerListener: PeerListener
) : BluetoothGattCallback() {
    companion object {
        private const val TAG = "CBL_DEVICE"
    }

    enum class State {
        DISCOVERED,
        CONNECTING,
        GET_SERVICES,
        GET_CHARACTERISTICS,
        CONNECTED,
        DISCONNECTED,
        FAILED;

        fun next() = State.entries[(ordinal + 1) % State.entries.size]
    }

    var cblId: String? = null
        private set

    val address
        get() = device.address

    val name
        get() = device.name

    var state = State.DISCOVERED
        private set

    private val lock = Any()
    private var peerGatt: BluetoothGatt? = null
    private var currentTask: BlockingTask? = null
    private var retries = 0

    override fun toString() = "${cblId}: ${name} @${address} (${state})"

    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
        taskComplete()
        if ((peerGatt == null) && (gatt != null)) {
            peerGatt = gatt
        }

        when (status) {
            BluetoothGatt.GATT_SUCCESS -> changeGattState(newState)

            // retry a 133 error
            133 -> {
                retry() ?: return
                connectOnce()
            }

            else -> fail("unexpected status on open: $status")
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        taskComplete()
        when (status) {
            BluetoothGatt.GATT_SUCCESS -> {
                Log.i(TAG, "$address: services discovered")
                getCBLService()?.let { findCBLCharacteristic(it) }
                    ?: fail("CBL service not found")
            }

            else -> fail("unexpected status on service discovery: $status")
        }
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int
    ) {
        taskComplete()
        when (status) {
            BluetoothGatt.GATT_SUCCESS -> {
                Log.i(TAG, "$address: characteristic read: ${value.size} bytes")
                parseCBLCharacteristic(value)
            }

            else -> fail("unexpected status on characteristic read: $status")
        }
    }

    override fun onPhyUpdate(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {
        Log.i(TAG, "$address(${status}): phy update: ${txPhy} ${txPhy}")
    }

    override fun onPhyRead(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {
        Log.i(TAG, "$address(${status}): phy read: ${txPhy} ${txPhy}")
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
        Log.i(TAG, "$address(${status}): characteristic write: ${characteristic}")
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        Log.i(TAG, "$address: characteristic changed: ${characteristic}, ${value.size}")
    }

    override fun onDescriptorRead(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int,
        value: ByteArray
    ) {
        Log.i(TAG, "$address(${status}): descriptor read: ${descriptor}, ${value.size}")
    }

    override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
        Log.i(TAG, "$address(${status}): descriptor write: ${descriptor}")
    }

    override fun onReliableWriteCompleted(gatt: BluetoothGatt?, status: Int) {
        Log.i(TAG, "$address(${status}): write complete")
    }

    override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
        Log.i(TAG, "$address(${status}): rssi read: ${rssi}")
    }

    override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
        Log.i(TAG, "$address(${status}): mtu changed: ${mtu}")
    }

    override fun onServiceChanged(gatt: BluetoothGatt) {
        Log.i(TAG, "$address: service changed")
    }

    fun connect() {
        setState(State.CONNECTING) ?: return
        connectOnce()
    }

    fun close(status: State = State.DISCONNECTED) {
        val gatt = peerGatt
        peerGatt = null
        gatt?.close()
        setState(State.FAILED)
        peerListener.removePeer(this)
    }

    private fun connectOnce() {
        setState(State.CONNECTING) ?: return
        startTask { device.connectGatt(btService.context, false, this, BluetoothDevice.TRANSPORT_LE) }
    }

    private fun changeGattState(newState: Int) {
        Log.i(TAG, "$address: gatt state changed: $newState")
        when (newState) {
            BluetoothGatt.STATE_CONNECTED -> discoverServices()
            BluetoothGatt.STATE_DISCONNECTED -> close()
        }
    }

    private fun discoverServices() {
        setState(State.GET_SERVICES) ?: return
        peerGatt?.let {
            startTask { it.discoverServices() }
        }
    }

    private fun getCBLService(): BluetoothGattService? {
        val gatt = peerGatt ?: return null

        gatt.services.forEach {
            Log.d(TAG, "$address: found service: ${it.uuid}")
        }

        return gatt.getService(P2P_NAMESPACE_ID)
    }

    private fun findCBLCharacteristic(cblService: BluetoothGattService) {
        setState(State.GET_CHARACTERISTICS) ?: return

        cblService.characteristics.forEach {
            Log.d(TAG, "$address: found characteristic: ${it.uuid} (${Integer.toHexString(it.properties)})")
        }

        val characteristic =
            cblService.getCharacteristic(PORT_CHARACTERISTIC_ID)

        if (characteristic == null) {
            fail("CBL characteristic not found")
            return
        }

        if (!characteristic.isReadable()) {
            fail("CBL characteristic is not readable")
            return
        }

        peerGatt?.let {
            startTask { it.readCharacteristic(characteristic) }
        }
    }

    private fun parseCBLCharacteristic(data: ByteArray) {
        setState(State.CONNECTED) ?: return

        // !!! we should be parsing this from the byte array
        // just use the address, for now.
        cblId = address

        peerListener.addPeer(this)
    }

    private fun retry(): Unit? {
        if (retries++ < 3) {
            return Unit
        }

        fail("too many retries")
        return null
    }

    private fun fail(msg: String) {
        Log.w(TAG, "$address: $msg")
        close(State.FAILED)
    }

    private fun setState(newState: State): State? {
        val prevState: State

        synchronized(lock) {
            retries = 0

            if (state == newState) return newState
            prevState = state
            state = newState
        }

        Log.w(TAG, "$address: state transition: $prevState -> $newState")
        if ((newState < State.DISCONNECTED) && (newState != prevState.next())) {
            fail("unexpected state transition:  $prevState -> $newState")
            return null
        }

        return newState
    }

    private fun startTask(block: () -> Unit) {
        currentTask = btService.runTask(block)
    }

    private fun taskComplete() {
        val curTask = currentTask
        currentTask = null
        curTask?.done()
    }
}