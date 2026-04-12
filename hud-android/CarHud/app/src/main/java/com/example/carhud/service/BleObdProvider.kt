package com.example.carhud.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import com.example.carhud.model.GpsDataPayload
import com.example.carhud.model.HudDataPayload
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Bluetooth Low Energy ELM327 (e.g. VeePeak OBDCheck BLE) adapter.
 * Writes to FFF1, notifications on FFF2 under service FFF0.
 * When connected, sends merged [HudDataPayload] (OBD + GPS from [gpsSupplier]) via [HudConnectionHolder].
 */
class BleObdProvider(
    private val appContext: Context
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val adapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null

    private val rxBuffer = StringBuilder()
    private val cmdMutex = Mutex()
    private var responseWait: CompletableDeferred<String>? = null

    private var pollJob: Job? = null
    private var gpsSupplier: (() -> GpsDataPayload?)? = null

    private val discoveredDevices = ConcurrentHashMap<String, BluetoothDevice>()

    private val _connectionState = MutableStateFlow<ObdBleConnectionState>(ObdBleConnectionState.Disconnected)
    val connectionState: StateFlow<ObdBleConnectionState> = _connectionState.asStateFlow()

    private val _discoveredList = MutableStateFlow<List<BleDeviceUi>>(emptyList())
    val discoveredList: StateFlow<List<BleDeviceUi>> = _discoveredList.asStateFlow()

    /** When true, [HudConnectionService] should not send standalone gps_data (OBD sends merged hud_data). */
    fun isObdBleActive(): Boolean =
        _connectionState.value is ObdBleConnectionState.Connected

    fun setGpsSupplier(supplier: () -> GpsDataPayload?) {
        gpsSupplier = supplier
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val a = adapter ?: run {
            _connectionState.value = ObdBleConnectionState.Error("Bluetooth not available")
            return
        }
        if (!a.isEnabled) {
            _connectionState.value = ObdBleConnectionState.Error("Bluetooth is off")
            return
        }
        discoveredDevices.clear()
        _discoveredList.value = emptyList()
        _connectionState.value = ObdBleConnectionState.Scanning
        scanner = a.bluetoothLeScanner
        scanner?.startScan(
            null,
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build(),
            scanCallback
        )
        scope.launch {
            delay(SCAN_DURATION_MS)
            stopScanInternal()
            if (_connectionState.value is ObdBleConnectionState.Scanning) {
                _connectionState.value = ObdBleConnectionState.Disconnected
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScanInternal() {
        runCatching { scanner?.stopScan(scanCallback) }
        scanner = null
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val dev = result.device ?: return
            val addr = dev.address ?: return
            if (discoveredDevices.putIfAbsent(addr, dev) == null) {
                val name = dev.name?.takeIf { it.isNotBlank() } ?: "Unknown"
                _discoveredList.value = _discoveredList.value + BleDeviceUi(
                    address = addr,
                    displayName = name
                )
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _connectionState.value = ObdBleConnectionState.Error("BLE scan failed ($errorCode)")
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        val device = discoveredDevices[address] ?: run {
            _connectionState.value = ObdBleConnectionState.Error("Device not in list — scan again")
            return
        }
        stopScanInternal()
        disconnectGattOnly()
        _connectionState.value = ObdBleConnectionState.Connecting(device.name)
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            @Suppress("DEPRECATION")
            device.connectGatt(appContext, false, gattCallback)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        stopScanInternal()
        pollJob?.cancel()
        pollJob = null
        disconnectGattOnly()
        _connectionState.value = ObdBleConnectionState.Disconnected
    }

    @SuppressLint("MissingPermission")
    private fun disconnectGattOnly() {
        writeChar = null
        rxBuffer.clear()
        responseWait?.cancel()
        responseWait = null
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.requestMtu(517)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                pollJob?.cancel()
                pollJob = null
                if (_connectionState.value !is ObdBleConnectionState.Disconnected) {
                    _connectionState.value = ObdBleConnectionState.Disconnected
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            gatt.discoverServices()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = ObdBleConnectionState.Error("GATT service discovery failed")
                disconnect()
                return
            }
            val svc = gatt.getService(SERVICE_UUID) ?: run {
                _connectionState.value = ObdBleConnectionState.Error("FFF0 service not found")
                disconnect()
                return
            }
            val write = svc.getCharacteristic(WRITE_UUID) ?: run {
                _connectionState.value = ObdBleConnectionState.Error("FFF1 write characteristic not found")
                disconnect()
                return
            }
            val notify = svc.getCharacteristic(NOTIFY_UUID) ?: run {
                _connectionState.value = ObdBleConnectionState.Error("FFF2 notify characteristic not found")
                disconnect()
                return
            }
            writeChar = write
            gatt.setCharacteristicNotification(notify, true)
            val cccd = notify.getDescriptor(CCCD_UUID)
            if (cccd != null) {
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(cccd)
            } else {
                ioScope.launch { runElmInitAndPoll(gatt) }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (descriptor.uuid == CCCD_UUID && status == BluetoothGatt.GATT_SUCCESS) {
                ioScope.launch { runElmInitAndPoll(gatt) }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appendRx(value)
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                characteristic.value?.let { appendRx(it) }
            }
        }

        private fun appendRx(bytes: ByteArray) {
            val text = String(bytes, Charsets.US_ASCII)
            synchronized(rxBuffer) {
                rxBuffer.append(text)
                if (rxBuffer.contains('>')) {
                    val s = rxBuffer.toString()
                    rxBuffer.clear()
                    responseWait?.complete(s)
                    responseWait = null
                }
            }
        }
    }

    private suspend fun runElmInitAndPoll(gatt: BluetoothGatt) {
        delay(200)
        runCatching {
            sendElmCommand(gatt, "ATZ", timeoutMs = 8000)
            delay(500)
            sendElmCommand(gatt, "ATE0")
            sendElmCommand(gatt, "ATL0")
            sendElmCommand(gatt, "ATS0")
            sendElmCommand(gatt, "ATSP0")
        }.onFailure { e ->
            _connectionState.value = ObdBleConnectionState.Error("ELM init failed: ${e.message}")
            disconnect()
            return
        }
        _connectionState.value = ObdBleConnectionState.Connected(gatt.device?.name)
        pollJob?.cancel()
        pollJob = ioScope.launch {
            while (isActive) {
                if (writeChar == null || !isObdBleActive()) break
                runCatching {
                    val speedKmh = queryPid(gatt, "010D")?.let { parseSpeedKmh(it) }
                    val rpm = queryPid(gatt, "010C")?.let { parseRpm(it) }
                    val coolantC = queryPid(gatt, "0105")?.let { parseCoolantC(it) }
                    val fuelPct = queryPid(gatt, "012F")?.let { parseFuelLevelFraction(it) }
                    val fuelLh = queryPid(gatt, "015E")?.let { parseFuelRateLh(it) }

                    val speedMph = speedKmh?.let { (it * KMH_TO_MPH).roundToInt().toString() } ?: ""
                    val rpmStr = rpm?.roundToInt()?.toString() ?: ""
                    val coolantStr = coolantC?.let { formatCoolantF(it) } ?: ""
                    val fuelStr = fuelPct?.let { "${(it * 100f).roundToInt()}%" } ?: ""

                    val speedMphFloat = speedKmh?.let { (it * KMH_TO_MPH).toFloat() } ?: 0f
                    val mpgStr = computeMpg(speedMphFloat, fuelLh)

                    val gps = gpsSupplier?.invoke()
                    val payload = HudDataPayload(
                        speed = speedMph,
                        gpsSpeed = gps?.gpsSpeed ?: "",
                        rpm = rpmStr,
                        coolantTemp = coolantStr,
                        mpg = mpgStr,
                        range = "",
                        fuelLevel = fuelStr,
                        turn = gps?.turn ?: "",
                        distance = gps?.distance ?: "",
                        maneuver = gps?.maneuver ?: "",
                        eta = gps?.eta ?: "",
                        speedLimit = gps?.speedLimit ?: "",
                        timestamp = System.currentTimeMillis()
                    )
                    HudConnectionHolder.send(payload.toMessage())
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun sendElmCommand(gatt: BluetoothGatt, cmd: String, timeoutMs: Long = 3000) {
        cmdMutex.withLock {
            synchronized(rxBuffer) { rxBuffer.clear() }
            val wait = CompletableDeferred<String>()
            responseWait = wait
            writeRaw(gatt, cmd + "\r")
            val got = withTimeoutOrNull(timeoutMs) { wait.await() }
            if (got == null) {
                wait.cancel()
                responseWait = null
                throw IllegalStateException("Timeout: $cmd")
            }
        }
    }

    private suspend fun queryPid(gatt: BluetoothGatt, pid: String): String? = cmdMutex.withLock {
        synchronized(rxBuffer) { rxBuffer.clear() }
        val wait = CompletableDeferred<String>()
        responseWait = wait
        writeRaw(gatt, pid + "\r")
        val response = withTimeoutOrNull(4000) { wait.await() }
        if (response == null) {
            wait.cancel()
            responseWait = null
            return@withLock null
        }
        val r = response.trim()
        if (r.contains("NO DATA", ignoreCase = true) || r.contains("UNABLE", ignoreCase = true)) {
            return@withLock null
        }
        r
    }

    @SuppressLint("MissingPermission")
    private suspend fun writeRaw(gatt: BluetoothGatt, ascii: String) {
        val ch = writeChar ?: return
        val bytes = ascii.toByteArray(Charsets.US_ASCII)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val status = gatt.writeCharacteristic(ch, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            if (status != BluetoothGatt.GATT_SUCCESS) {
                throw IllegalStateException("GATT write failed ($status)")
            }
        } else {
            ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            ch.value = bytes
            if (!gatt.writeCharacteristic(ch)) {
                throw IllegalStateException("GATT write failed")
            }
        }
        delay(25)
    }

    companion object {
        private val SERVICE_UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        private val WRITE_UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
        private val NOTIFY_UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val SCAN_DURATION_MS = 15_000L
        private const val POLL_INTERVAL_MS = 500L
        private const val KMH_TO_MPH = 0.621371f

        /** Bytes after ELM header `41` + [pidByte] in a positive response. */
        private fun obdDataBytes(response: String, pidByte: Int): List<Int>? {
            val hex = Regex("""[0-9A-Fa-f]{2}""").findAll(response.uppercase()).map { it.value.toInt(16) }.toList()
            for (i in 0 until hex.size - 2) {
                if (hex[i] == 0x41 && hex[i + 1] == pidByte) {
                    return hex.drop(i + 2)
                }
            }
            return null
        }

        private fun parseSpeedKmh(response: String): Float? {
            val data = obdDataBytes(response, 0x0D) ?: return null
            val b = data.firstOrNull() ?: return null
            return b.toFloat()
        }

        private fun parseRpm(response: String): Float? {
            val data = obdDataBytes(response, 0x0C) ?: return null
            if (data.size < 2) return null
            val v = (data[0] shl 8) or data[1]
            return v / 4f
        }

        private fun parseCoolantC(response: String): Float? {
            val data = obdDataBytes(response, 0x05) ?: return null
            val b = data.firstOrNull() ?: return null
            return b - 40f
        }

        private fun parseFuelLevelFraction(response: String): Float? {
            val data = obdDataBytes(response, 0x2F) ?: return null
            val b = data.firstOrNull() ?: return null
            return b / 255f
        }

        private fun parseFuelRateLh(response: String): Float? {
            val data = obdDataBytes(response, 0x5E) ?: return null
            if (data.size < 2) return null
            val v = (data[0] shl 8) or data[1]
            return v * 0.05f
        }

        private fun formatCoolantF(celsius: Float): String {
            val f = celsius * 9f / 5f + 32f
            return "${f.roundToInt()}°F"
        }

        private fun computeMpg(speedMph: Float, fuelRateLh: Float?): String {
            if (fuelRateLh == null || fuelRateLh <= 0.001f || speedMph < 1f) return ""
            val gph = fuelRateLh * 0.264172f
            val mpg = speedMph / gph
            if (mpg.isNaN() || mpg.isInfinite() || mpg <= 0f) return ""
            return "%.1f".format(mpg)
        }
    }
}

sealed class ObdBleConnectionState {
    data object Disconnected : ObdBleConnectionState()
    data object Scanning : ObdBleConnectionState()
    data class Connecting(val deviceName: String?) : ObdBleConnectionState()
    data class Connected(val deviceName: String?) : ObdBleConnectionState()
    data class Error(val message: String) : ObdBleConnectionState()
}

data class BleDeviceUi(
    val address: String,
    val displayName: String
)
