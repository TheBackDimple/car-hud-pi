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
import android.util.Log
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Bluetooth Low Energy ELM327 (e.g. VeePeak OBDCheck BLE) adapter.
 * Auto-detects write vs notify characteristics within the FFF0 service by inspecting
 * GATT properties (different VeePeak firmware versions swap FFF1/FFF2 roles).
 * When connected, sends merged [HudDataPayload] (OBD + GPS from [gpsSupplier]) via [HudConnectionHolder].
 */
class BleObdProvider(
    private val appContext: Context
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val adapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var connectRetries = 0

    private val rxBuffer = StringBuilder()
    private val cmdMutex = Mutex()
    private var responseWait: CompletableDeferred<String>? = null

    /** Serialization gate: `onCharacteristicWrite` completes this after each write. */
    private var writeGate: CompletableDeferred<Int>? = null

    private var pollJob: Job? = null
    private var gpsSupplier: (() -> GpsDataPayload?)? = null

    private val discoveredDevices = ConcurrentHashMap<String, BluetoothDevice>()

    private val _connectionState = MutableStateFlow<ObdBleConnectionState>(ObdBleConnectionState.Disconnected)
    val connectionState: StateFlow<ObdBleConnectionState> = _connectionState.asStateFlow()

    private val _discoveredList = MutableStateFlow<List<BleDeviceUi>>(emptyList())
    val discoveredList: StateFlow<List<BleDeviceUi>> = _discoveredList.asStateFlow()

    private val _debugLog = MutableStateFlow<List<String>>(emptyList())
    val debugLog: StateFlow<List<String>> = _debugLog.asStateFlow()

    private val _latestObd = MutableStateFlow<ObdLiveData?>(null)
    val latestObd: StateFlow<ObdLiveData?> = _latestObd.asStateFlow()

    fun isObdBleActive(): Boolean =
        _connectionState.value is ObdBleConnectionState.Connected

    fun setGpsSupplier(supplier: () -> GpsDataPayload?) {
        gpsSupplier = supplier
    }

    private fun log(msg: String) {
        Log.d(TAG, msg)
        _debugLog.value = (_debugLog.value + msg).takeLast(MAX_LOG_LINES)
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
        log("BLE scan starting")
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
                log("Scan timeout — ${discoveredDevices.size} device(s) found")
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
                log("Found: $name [$addr] rssi=${result.rssi}")
                _discoveredList.value = _discoveredList.value + BleDeviceUi(
                    address = addr,
                    displayName = name
                )
            }
        }

        override fun onScanFailed(errorCode: Int) {
            log("Scan failed, error=$errorCode")
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
        pollJob?.cancel()
        pollJob = null
        disconnectGattOnly()
        connectRetries = 0
        log("Connecting to ${device.name} [$address]")
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
        log("disconnect() called")
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
        writeGate?.cancel()
        writeGate = null
        _latestObd.value = null
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }

    @SuppressLint("MissingPermission")
    private fun retryConnect(device: BluetoothDevice) {
        scope.launch {
            delay(1000L * connectRetries)
            log("Retrying connectGatt…")
            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                @Suppress("DEPRECATION")
                device.connectGatt(appContext, false, gattCallback)
            }
        }
    }

    // ── GATT callbacks ──────────────────────────────────────────────────

    private val gattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            log("onConnectionStateChange status=$status newState=$newState")

            if (newState == BluetoothProfile.STATE_DISCONNECTED && status == 133 && connectRetries < MAX_CONNECT_RETRIES) {
                connectRetries++
                log("Status 133 (stale GATT cache) — auto-retry #$connectRetries after delay")
                val device = gatt.device
                gatt.close()
                if (device != null) {
                    retryConnect(device)
                }
                return
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    log("Connected but status=$status — treating as failure")
                    _connectionState.value = ObdBleConnectionState.Error("GATT connect status $status")
                    scope.launch { disconnect() }
                    return
                }
                connectRetries = 0
                log("GATT connected, discovering services…")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                log("GATT disconnected (status=$status)")
                pollJob?.cancel()
                pollJob = null
                if (_connectionState.value !is ObdBleConnectionState.Disconnected) {
                    _connectionState.value = ObdBleConnectionState.Disconnected
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            log("onServicesDiscovered status=$status")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                log("Service discovery failed, status=$status")
                _connectionState.value = ObdBleConnectionState.Error("GATT service discovery failed ($status)")
                scope.launch { disconnect() }
                return
            }

            for (svc in gatt.services) {
                log("  Service ${svc.uuid}")
                for (ch in svc.characteristics) {
                    log("    Char ${ch.uuid} props=0x${ch.properties.toString(16)}")
                    for (d in ch.descriptors) {
                        log("      Desc ${d.uuid}")
                    }
                }
            }

            val svc = gatt.getService(SERVICE_UUID) ?: run {
                log("FFF0 service NOT found")
                _connectionState.value = ObdBleConnectionState.Error("FFF0 service not found")
                scope.launch { disconnect() }
                return
            }

            // Auto-detect write vs notify chars by inspecting GATT properties.
            // Different VeePeak firmware versions swap FFF1/FFF2 roles.
            var detectedWrite: BluetoothGattCharacteristic? = null
            var detectedNotify: BluetoothGattCharacteristic? = null

            for (ch in svc.characteristics) {
                val p = ch.properties
                val canWrite = (p and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                    (p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                val canNotify = (p and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
                    (p and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0

                if (canWrite && detectedWrite == null) detectedWrite = ch
                if (canNotify && detectedNotify == null) detectedNotify = ch
            }

            if (detectedWrite == null) {
                log("No writable characteristic found in FFF0 service")
                _connectionState.value = ObdBleConnectionState.Error("No writable char in FFF0")
                scope.launch { disconnect() }
                return
            }
            if (detectedNotify == null) {
                log("No notify characteristic found in FFF0 service")
                _connectionState.value = ObdBleConnectionState.Error("No notify char in FFF0")
                scope.launch { disconnect() }
                return
            }

            log("Write char: ${detectedWrite.uuid} props=0x${detectedWrite.properties.toString(16)}")
            log("Notify char: ${detectedNotify.uuid} props=0x${detectedNotify.properties.toString(16)}")
            writeChar = detectedWrite

            gatt.setCharacteristicNotification(detectedNotify, true)
            val cccd = detectedNotify.getDescriptor(CCCD_UUID)
            if (cccd != null) {
                log("Writing CCCD on ${detectedNotify.uuid} to enable notifications…")
                scope.launch { enableNotifications(gatt, cccd) }
            } else {
                log("No CCCD on notify char — skipping descriptor write, starting ELM init")
                scope.launch { runElmInitAndPoll(gatt) }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            log("onDescriptorWrite uuid=${descriptor.uuid} status=$status")
            if (descriptor.uuid == CCCD_UUID) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    log("CCCD written OK — starting ELM init")
                    scope.launch { runElmInitAndPoll(gatt) }
                } else {
                    log("CCCD write FAILED status=$status")
                    _connectionState.value =
                        ObdBleConnectionState.Error("Enable notify failed (descriptor status $status)")
                    scope.launch { disconnect() }
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                log("onCharacteristicWrite FAILED status=$status")
            }
            writeGate?.complete(status)
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

    // ── Internal helpers ─────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private suspend fun enableNotifications(gatt: BluetoothGatt, cccd: BluetoothGattDescriptor) {
        withContext(Dispatchers.Main.immediate) {
            val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
                    BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(cccd)
            }
            if (!ok) {
                log("writeDescriptor() returned false — notify enable rejected")
                _connectionState.value =
                    ObdBleConnectionState.Error("Enable notify failed (descriptor write rejected)")
                disconnect()
            }
        }
    }

    private suspend fun runElmInitAndPoll(gatt: BluetoothGatt) {
        delay(600)
        log("ELM init: sending ATZ…")
        runCatching {
            val atzReply = sendElmCommand(gatt, "ATZ", timeoutMs = 8000)
            log("ATZ → ${atzReply.take(80)}")
            delay(500)
            sendElmCommand(gatt, "ATE0").also { log("ATE0 → ${it.take(40)}") }
            sendElmCommand(gatt, "ATL0").also { log("ATL0 → ${it.take(40)}") }
            sendElmCommand(gatt, "ATS0").also { log("ATS0 → ${it.take(40)}") }
            sendElmCommand(gatt, "ATSP0").also { log("ATSP0 → ${it.take(40)}") }
        }.onFailure { e ->
            log("ELM init FAILED: ${e.message}")
            _connectionState.value = ObdBleConnectionState.Error("ELM init failed: ${e.message}")
            disconnect()
            return
        }
        log("ELM init complete — connected!")
        _connectionState.value = ObdBleConnectionState.Connected(gatt.device?.name)
        pollJob?.cancel()
        pollJob = scope.launch {
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

                    _latestObd.value = ObdLiveData(
                        speedMph = speedMph,
                        rpm = rpmStr,
                        coolantTemp = coolantStr,
                        mpg = mpgStr,
                        fuelLevel = fuelStr
                    )

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
                }.onFailure { e ->
                    log("Poll error: ${e.message}")
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun sendElmCommand(gatt: BluetoothGatt, cmd: String, timeoutMs: Long = 3000): String {
        return cmdMutex.withLock {
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
            got
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

    private fun preferredWriteType(ch: BluetoothGattCharacteristic): Int {
        val p = ch.properties
        val noResp = (p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
        val withResp = (p and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
        return when {
            noResp && !withResp -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            withResp && !noResp -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            noResp && withResp -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            else -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
    }

    /**
     * Write raw bytes to the ELM327 and wait for `onCharacteristicWrite` confirmation.
     * Android BLE requires one outstanding GATT write at a time; issuing a second write
     * before the callback fires causes status 200 or silent disconnects (Samsung especially).
     */
    @SuppressLint("MissingPermission")
    private suspend fun writeRaw(gatt: BluetoothGatt, ascii: String) {
        val ch = writeChar ?: throw IllegalStateException("writeChar is null — not connected")
        val bytes = ascii.toByteArray(Charsets.US_ASCII)
        val writeType = preferredWriteType(ch)

        val gate = CompletableDeferred<Int>()
        writeGate = gate

        withContext(Dispatchers.Main.immediate) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                var status = gatt.writeCharacteristic(ch, bytes, writeType)
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    val alt = if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    log("writeCharacteristic($writeType) returned $status, retrying with $alt")
                    status = gatt.writeCharacteristic(ch, bytes, alt)
                }
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    gate.cancel()
                    writeGate = null
                    throw IllegalStateException("GATT write failed ($status)")
                }
            } else {
                @Suppress("DEPRECATION")
                ch.writeType = writeType
                @Suppress("DEPRECATION")
                ch.value = bytes
                @Suppress("DEPRECATION")
                if (!gatt.writeCharacteristic(ch)) {
                    val alt = if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    log("writeCharacteristic($writeType) returned false, retrying with $alt")
                    @Suppress("DEPRECATION")
                    ch.writeType = alt
                    @Suppress("DEPRECATION")
                    ch.value = bytes
                    @Suppress("DEPRECATION")
                    if (!gatt.writeCharacteristic(ch)) {
                        gate.cancel()
                        writeGate = null
                        throw IllegalStateException("GATT write failed")
                    }
                }
            }
        }

        val callbackStatus = withTimeoutOrNull(WRITE_CALLBACK_TIMEOUT_MS) { gate.await() }
        writeGate = null
        if (callbackStatus == null) {
            log("onCharacteristicWrite callback TIMEOUT")
            throw IllegalStateException("Write callback timeout")
        }
        if (callbackStatus != BluetoothGatt.GATT_SUCCESS) {
            log("onCharacteristicWrite status=$callbackStatus (not SUCCESS)")
            throw IllegalStateException("GATT write callback status $callbackStatus")
        }
    }

    companion object {
        private const val TAG = "BleObdProvider"
        private const val MAX_LOG_LINES = 120
        private const val MAX_CONNECT_RETRIES = 2

        private val SERVICE_UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val SCAN_DURATION_MS = 15_000L
        private const val POLL_INTERVAL_MS = 500L
        private const val KMH_TO_MPH = 0.621371f
        private const val WRITE_CALLBACK_TIMEOUT_MS = 5_000L

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

data class ObdLiveData(
    val speedMph: String,
    val rpm: String,
    val coolantTemp: String,
    val mpg: String,
    val fuelLevel: String
)
