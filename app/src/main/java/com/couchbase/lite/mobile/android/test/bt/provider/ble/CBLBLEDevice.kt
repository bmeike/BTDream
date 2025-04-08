package com.couchbase.lite.mobile.android.test.bt.provider.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.util.UUID


fun BluetoothGattCharacteristic.isReadable() =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_READ)

fun BluetoothGattCharacteristic.isWritable() =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)

fun BluetoothGattCharacteristic.isWritableWithoutResponse() =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)

fun BluetoothGattCharacteristic.containsProperty(property: Int) = properties and property != 0


@SuppressWarnings("MissingPermission")
class CBLBLEDevice(
    private val bleService: BLEService,
    private val device: BluetoothDevice,
    val rssi: Int,
    private val onFound: (CBLBLEDevice) -> Unit,
    private val onConnected: (CBLBLEDevice) -> Unit,
    private val onLost: (CBLBLEDevice) -> Unit,
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
        OPENING,
        OPENED,
        DISCONNECTED,
        FAILED;

        fun next() = State.entries[(ordinal + 1) % State.entries.size]
    }


    val address: String
        get() = device.address

    val name: String
        get() = device.name

    var port: Int? = null
        private set

    var cblId: String? = null
        private set

    var metadata: Map<String, Any>? = null
        private set

    var state = State.DISCOVERED
        private set

    private val lock = Any()
    private var retries = 0
    private var currentTask: BlockingTask? = null
    private var connectedGatt: BluetoothGatt? = null
    private var connection: BLEL2CAPConnection? = null
    private var requiredCharacteristics: MutableList<UUID> = CBL_CHARACTERISTICS.keys.toMutableList()
    private val peerGatt: BluetoothGatt?
        get() {
            if (connectedGatt == null) {
                fail("peerGatt is null")
            }
            return connectedGatt
        }


    fun connect() {
        setState(State.CONNECTING) ?: return
        connectOnce()
    }

    fun open(onData: (String) -> Unit, onClose: (String?, Throwable?) -> Unit) {
        if (state >= State.OPENING) {
            return
        }
        setState(State.OPENING) ?: return
        val psm = port ?: return

        startTask {
            var socket: BluetoothSocket? = null
            try {
                socket = peerGatt?.device?.createInsecureL2capChannel(psm)
                socket?.connect()
            } catch (e: Exception) {
                fail("failed to create L2cap channel", e)
                socket?.close()
                socket = null
            }
            opened(socket, onData, onClose)
        }
    }

    fun close(finalState: State = State.DISCONNECTED) {
        val connect = connection
        connection = null
        connect?.close()

        val gatt = connectedGatt
        connectedGatt = null
        gatt?.close()

        setState(finalState)

        onLost(this)
    }

    fun send(msg: String) {
        Log.d(TAG, "$address: send to connection ${connection}")
        val connect = connection ?: return
        connect.write(msg.toByteArray(Charsets.UTF_8))
    }

    override fun toString() = "${cblId}: ${name} @${address} (${state})"

    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
        taskComplete()
        if ((connectedGatt == null) && (gatt != null)) {
            connectedGatt = gatt
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
        if (status != BluetoothGatt.GATT_SUCCESS) {
            fail("unexpected status on service discovery: $status")
            return
        }

        val cblService = getCBLService() ?: return
        readCharacteristics(cblService)
    }

    override fun onServiceChanged(gatt: BluetoothGatt) {
        Log.d(TAG, "$address: service changed")
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int
    ) {
        taskComplete()
        if (status != BluetoothGatt.GATT_SUCCESS) {
            fail("unexpected status on characteristic read: $status")
            return
        }

        parseCBLCharacteristic(characteristic, value)

        if (!readNextCharacteristic()) {
            connected()
        }
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
        Log.d(TAG, "$address(${status}): characteristic write: ${characteristic}")
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        Log.d(TAG, "$address: characteristic changed: ${characteristic}, ${value.size}")
    }

    @Deprecated("Deprecated in Java")
    override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
        Log.d(TAG, "$address: deprecated characteristic read(${status})")
    }

    @Deprecated("Deprecated in Java")
    override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
        Log.d(TAG, "$address: deprecated characteristic changed: ${characteristic}")
    }

    override fun onPhyUpdate(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {
        Log.d(TAG, "$address(${status}): phy update: ${txPhy} ${txPhy}")
    }

    override fun onPhyRead(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {
        Log.d(TAG, "$address(${status}): phy read: ${txPhy} ${txPhy}")
    }

    @Deprecated("Deprecated in Java")
    override fun onDescriptorRead(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
        Log.d(TAG, "$address: deprecated descriptor read(${status}): ${descriptor}")
    }

    override fun onDescriptorRead(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int,
        value: ByteArray
    ) {
        Log.d(TAG, "$address(${status}): descriptor read: ${descriptor}, ${value.size}")
    }

    override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
        Log.d(TAG, "$address(${status}): descriptor write: ${descriptor}")
    }

    override fun onReliableWriteCompleted(gatt: BluetoothGatt?, status: Int) {
        Log.d(TAG, "$address(${status}): write complete")
    }

    override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
        Log.d(TAG, "$address(${status}): rssi read: ${rssi}")
    }

    override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
        Log.d(TAG, "$address(${status}): mtu changed: ${mtu}")
    }

    private fun connectOnce() {
        setState(State.CONNECTING) ?: return
        startTask { device.connectGatt(bleService.context, false, this, BluetoothDevice.TRANSPORT_LE) }
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
        startTask { peerGatt?.discoverServices() }
    }

    private fun getCBLService(): BluetoothGattService? {
        val gatt = peerGatt ?: return null

        // debugging
        gatt.services.forEach {
            Log.d(TAG, "$address: found service: ${it.uuid}")
        }

        return getCBLService(gatt)
    }


    private fun readCharacteristics(cblService: BluetoothGattService) {
        setState(State.GET_CHARACTERISTICS) ?: return

        // debugging
        cblService.characteristics.forEach {
            Log.d(TAG, "$address: found characteristic: ${it.uuid} (${Integer.toHexString(it.properties)})")
        }

        readNextCharacteristic()
    }

    private fun readNextCharacteristic(): Boolean {
        // if there are no more required characteristics, then we are done
        Log.d(TAG, "$address: read next characteristic: ${requiredCharacteristics}")
        if (requiredCharacteristics.isEmpty()) {
            return false
        }

        val gatt = peerGatt ?: return true

        val cblService = getCBLService(gatt) ?: return true

        val uuid = requiredCharacteristics.removeAt(0)
        val characteristic = cblService.getCharacteristic(uuid)

        if (characteristic == null) {
            fail("CBL ${uuid} characteristic not found")
            return true
        }

        if (!characteristic.isReadable()) {
            fail("CBL ${uuid} characteristic is not readable")
            return true
        }

        startTask { gatt.readCharacteristic(characteristic) }

        return true
    }

    private fun parseCBLCharacteristic(characteristic: BluetoothGattCharacteristic, data: ByteArray) {
        Log.d(TAG, "$address: characteristic read: ${data.size} bytes")
        when (characteristic.uuid) {
            ID_CHARACTERISTIC_ID -> {
                cblId = data.decodeToString()
                Log.d(TAG, "$address: got id: ${cblId}")
            }

            PORT_CHARACTERISTIC_ID -> {
                data.toInt()?.let { port = it }
                    ?: fail("CBL port characteristic read failed")
                Log.d(TAG, "$address: got port: ${port}")
            }

            METADATA_CHARACTERISTIC_ID -> {
                Log.d(TAG, "$address: got metadata: ${metadata}")
                return
            }

            else -> {
                Log.w(TAG, "$address: unrecognized CBL characteristic: ${characteristic.uuid}")
                fail("unrecognized CBL characteristic")
                return
            }
        }
    }

    private fun connected() {
        setState(State.CONNECTED) ?: return
        onFound(this)
    }

    private fun opened(socket: BluetoothSocket?, onData: (String) -> Unit, onClose: (String?, Throwable?) -> Unit) {
        taskComplete()

        socket ?: return
        // ??? If this fails the socket doesn't get closed
        setState(State.OPENED) ?: return

        connection = BLEL2CAPConnection(
            socket,
            { _, data -> onData(data.decodeToString()) },
            { conn, err -> onClose(conn.remoteDevice?.address, err) }
        )
        connection?.start()

        Log.i(TAG, "$address: $device opened: ${socket.isConnected}")
        onConnected(this)
    }

    private fun getCBLService(gatt: BluetoothGatt): BluetoothGattService? {
        val cblService = gatt.getService(P2P_NAMESPACE_ID)
        if (cblService == null) {
            fail("CBL service not found")
        }
        return cblService
    }

    private fun retry(): Unit? {
        if (retries++ < 3) {
            return Unit
        }

        fail("too many retries")
        return null
    }

    private fun fail(msg: String, e: Throwable? = null) {
        Log.w(TAG, "$address: $msg", e)
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

        Log.d(TAG, "$address: state transition: $prevState -> $newState")
        return if ((newState > State.DISCONNECTED) || (newState == prevState.next())) {
            newState
        } else {
            fail("unexpected state transition:  $prevState -> $newState", Exception())
            null
        }
    }

    private fun startTask(block: () -> Unit) {
        currentTask = bleService.runTaskBlocking(block)
    }

    private fun taskComplete() {
        val curTask = currentTask
        currentTask = null
        curTask?.done()
    }
}