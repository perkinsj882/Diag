package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.data.model.SystemCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.random.Random

data class DiscoveredObdBleDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val isLikelyObdAdapter: Boolean,
    val advertisedServiceUuids: List<String> = emptyList(),
    val bluetoothDevice: BluetoothDevice? = null
)

data class ObdDtcFault(
    val code: String,
    val statusType: String, // "CONFIRMED (Mode 03)" or "PENDING (Mode 07)"
    val description: String,
    val systemCategory: SystemCategory,
    val severity: String
)

data class ObdLiveSensorData(
    val engineRpm: Int = 0,
    val vehicleSpeedKmh: Int = 0,
    val coolantTempC: Int = 0,
    val intakeAirTempC: Int = 0,
    val manifoldPressureKpa: Int = 0,
    val throttlePositionPercent: Int = 0,
    val engineLoadPercent: Int = 0,
    val shortTermFuelTrimB1: Double = 0.0,
    val longTermFuelTrimB1: Double = 0.0,
    val controlModuleVoltage: Double = 0.0,
    val elmBatteryVoltage: Double = 0.0,
    val protocolName: String = "ISO 15765-4 CAN (11 bit ID, 500 kbaud)",
    val lastUpdatedMillis: Long = 0L
) {
    val boostPsi: Double
        get() = ((manifoldPressureKpa - 101.3) * 0.145038)

    fun toTelemetrySummaryString(dtcs: List<ObdDtcFault>, adapterName: String, isSimulation: Boolean): String {
        val modeTag = if (isSimulation) " [Safety Simulation Bench Replay]" else ""
        val dtcSummary = if (dtcs.isEmpty()) {
            "No active DTCs (Mode 03/07 clean)"
        } else {
            dtcs.joinToString(", ") { "${it.code} (${it.description})" }
        }
        return buildString {
            append("OBD-II BLE ($adapterName$modeTag) • ")
            append("DTCs: $dtcSummary • ")
            append("Live PIDs: ${engineRpm} RPM, Coolant ${coolantTempC}°C, ")
            append("MAP ${manifoldPressureKpa} kPa (${String.format(Locale.US, "%+.1f", boostPsi)} PSI), ")
            append("STFT ${String.format(Locale.US, "%+.1f", shortTermFuelTrimB1)}%, ")
            append("LTFT ${String.format(Locale.US, "%+.1f", longTermFuelTrimB1)}%, ")
            append("ECU ${String.format(Locale.US, "%.2f", controlModuleVoltage)}V")
        }
    }
}

enum class ObdConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    INITIALIZING_ELM327,
    STREAMING_LIVE,
    SAFETY_SIMULATION_MODE,
    ERROR
}

enum class ObdBatteryHealthLevel(val label: String) {
    OPTIMAL("OPTIMAL"),
    MODERATE_KOEO_DRAIN("KOEO PARASITIC DRAIN"),
    LOW_BATTERY_WARNING("LOW BATTERY WARNING"),
    CRITICAL_ECU_BROWNOUT("CRITICAL ECU BROWNOUT RISK"),
    OVERCHARGING_WARNING("ALTERNATOR OVERVOLTAGE")
}

data class ObdBatteryMonitorState(
    val currentVoltage: Double = 0.0,
    val initialVoltage: Double = 0.0,
    val minVoltage: Double = 0.0,
    val maxVoltage: Double = 0.0,
    val stateOfChargePercent: Int = 100,
    val sessionElapsedSeconds: Long = 0L,
    val voltageDropPerHour: Double = 0.0,
    val estimatedSafeMinutesRemaining: Int = 90,
    val healthLevel: ObdBatteryHealthLevel = ObdBatteryHealthLevel.OPTIMAL,
    val powerSourceStatus: String = "STANDBY",
    val warningHeadline: String = "",
    val warningRecommendation: String = "",
    val isLowBatteryWarningActive: Boolean = false,
    val isLongSessionDrainSimulated: Boolean = false
) {
    val formattedSessionDuration: String
        get() {
            val mins = sessionElapsedSeconds / 60
            val secs = sessionElapsedSeconds % 60
            return String.format(Locale.US, "%02d:%02d", mins, secs)
        }
}

data class ObdTerminalLogEntry(
    val timestamp: String,
    val rawCommand: String,
    val rawResponse: String
)

data class ObdTelemetrySample(
    val sampleIndex: Int,
    val timestampLabel: String,
    val elapsedSeconds: Int,
    val engineRpm: Int,
    val coolantTempC: Int,
    val intakeAirTempC: Int,
    val shortTermFuelTrimB1: Double,
    val longTermFuelTrimB1: Double,
    val manifoldPressureKpa: Int,
    val throttlePositionPercent: Int,
    val engineLoadPercent: Int,
    val controlModuleVoltage: Double,
    val vehicleSpeedKmh: Int
) {
    val totalFuelTrimB1: Double
        get() = shortTermFuelTrimB1 + longTermFuelTrimB1
}

data class ObdBleUiState(
    val connectionState: ObdConnectionState = ObdConnectionState.DISCONNECTED,
    val isBluetoothSupported: Boolean = true,
    val isBluetoothEnabled: Boolean = true,
    val hasBlePermissions: Boolean = false,
    val connectedAdapterName: String = "",
    val connectedAdapterAddress: String = "",
    val elmFirmwareVersion: String = "",
    val discoveredDevices: List<DiscoveredObdBleDevice> = emptyList(),
    val activeDtcs: List<ObdDtcFault> = emptyList(),
    val liveSensors: ObdLiveSensorData = ObdBleScannerManager.buildInitialLiveSensorData(),
    val batteryMonitor: ObdBatteryMonitorState = ObdBatteryMonitorState(
        currentVoltage = 13.85,
        initialVoltage = 13.90,
        minVoltage = 13.78,
        maxVoltage = 14.15,
        stateOfChargePercent = 100,
        powerSourceStatus = "ALTERNATOR CHARGING (1850 RPM)"
    ),
    val telemetryHistory: List<ObdTelemetrySample> = ObdBleScannerManager.buildInitialTelemetryHistory(),
    val isTelemetryPaused: Boolean = false,
    val terminalLog: List<ObdTerminalLogEntry> = emptyList(),
    val statusMessage: String = "Ready to scan for BLE OBD-II ELM327 adapters.",
    val isSafetySimulationActive: Boolean = false
)

class ObdBleScannerManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _uiState = MutableStateFlow(
        ObdBleUiState(
            isBluetoothSupported = bluetoothAdapter != null,
            isBluetoothEnabled = bluetoothAdapter?.isEnabled == true,
            hasBlePermissions = hasRequiredBlePermissions(context)
        )
    )
    val uiState: StateFlow<ObdBleUiState> = _uiState.asStateFlow()

    private var activeGatt: BluetoothGatt? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var isScanning = false
    private var scanTimeoutJob: Job? = null
    private var pollingJob: Job? = null
    private var lastSentCommand: String = ""
    private val responseBuffer = StringBuilder()
    private var sessionStartedAtMillis: Long = 0L
    private var simulatedVoltageOverride: Double? = null
    private var simulatedElapsedOffsetSeconds: Long = 0L
    private var hasNotifiedLowBatteryInSession: Boolean = false
    private var sampleCounter: Int = _uiState.value.telemetryHistory.size
    private var lastSampleRecordedMillis: Long = 0L

    fun toggleTelemetryPause() {
        _uiState.update { state ->
            val nextPaused = !state.isTelemetryPaused
            state.copy(
                isTelemetryPaused = nextPaused,
                statusMessage = if (nextPaused) {
                    "Recharts live OBD-II telemetry graphing paused for inspection."
                } else {
                    "Recharts live OBD-II telemetry graphing resumed."
                }
            )
        }
    }

    fun clearTelemetryHistory() {
        sampleCounter = 0
        _uiState.update { state ->
            state.copy(
                telemetryHistory = emptyList(),
                statusMessage = "Cleared OBD-II time-series telemetry buffer."
            )
        }
    }

    fun refreshPermissionsAndAdapterState() {
        _uiState.update {
            it.copy(
                isBluetoothSupported = bluetoothAdapter != null,
                isBluetoothEnabled = bluetoothAdapter?.isEnabled == true,
                hasBlePermissions = hasRequiredBlePermissions(context)
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun startBleScan() {
        refreshPermissionsAndAdapterState()
        if (!hasRequiredBlePermissions(context)) {
            _uiState.update {
                it.copy(
                    connectionState = ObdConnectionState.ERROR,
                    statusMessage = "Bluetooth Scan & Connect permissions are required to discover OBD-II BLE adapters."
                )
            }
            return
        }
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _uiState.update {
                it.copy(
                    connectionState = ObdConnectionState.ERROR,
                    isBluetoothSupported = adapter != null,
                    isBluetoothEnabled = adapter?.isEnabled == true,
                    statusMessage = "Bluetooth is disabled or unavailable on this device. Enable Bluetooth or use Safety Simulation Mode (Bench ECU Replay)."
                )
            }
            return
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            _uiState.update {
                it.copy(
                    connectionState = ObdConnectionState.ERROR,
                    statusMessage = "BLE Scanner hardware interface unavailable. Use Safety Simulation Mode to test ELM327 telemetry."
                )
            }
            return
        }

        stopBleScan()
        stopSimulationAndDisconnect()

        // Also include any already-bonded OBD-II BLE devices so paired adapters appear immediately
        val bondedObdDevices = runCatching {
            adapter.bondedDevices?.map { dev ->
                val devName = dev.name ?: "Bonded BLE Device"
                DiscoveredObdBleDevice(
                    name = devName,
                    address = dev.address,
                    rssi = -58,
                    isLikelyObdAdapter = isObdDeviceName(devName),
                    advertisedServiceUuids = emptyList(),
                    bluetoothDevice = dev
                )
            }.orEmpty()
        }.getOrDefault(emptyList())

        _uiState.update {
            it.copy(
                connectionState = ObdConnectionState.SCANNING,
                discoveredDevices = bondedObdDevices.sortedByDescending { d -> d.isLikelyObdAdapter },
                statusMessage = "Scanning BLE spectrum for ELM327 / OBD-II GATT adapters..."
            )
        }

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            isScanning = true
            scanner.startScan(null, scanSettings, bleScanCallback)
            scanTimeoutJob = scope.launch {
                delay(12_000L)
                if (isScanning) {
                    stopBleScan()
                    _uiState.update { state ->
                        val count = state.discoveredDevices.size
                        state.copy(
                            connectionState = if (state.connectionState == ObdConnectionState.SCANNING) {
                                ObdConnectionState.DISCONNECTED
                            } else {
                                state.connectionState
                            },
                            statusMessage = if (count > 0) {
                                "BLE scan complete. Found $count device(s). Tap an ELM327 adapter to connect."
                            } else {
                                "No physical BLE OBD-II adapters found nearby. Ensure ignition is ON, or launch Safety Simulation Mode below."
                            }
                        )
                    }
                }
            }
        } catch (e: Exception) {
            isScanning = false
            _uiState.update {
                it.copy(
                    connectionState = ObdConnectionState.ERROR,
                    statusMessage = "BLE scan failed: ${e.localizedMessage ?: "Scanner error"}"
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopBleScan() {
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        if (isScanning) {
            isScanning = false
            runCatching {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(bleScanCallback)
            }
        }
        if (_uiState.value.connectionState == ObdConnectionState.SCANNING) {
            _uiState.update {
                it.copy(
                    connectionState = ObdConnectionState.DISCONNECTED,
                    statusMessage = "BLE scan stopped (${it.discoveredDevices.size} devices listed)."
                )
            }
        }
    }

    private val bleScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val rawName = result.scanRecord?.deviceName
                ?: runCatching { device.name }.getOrNull()
                ?: "Unknown BLE Node"
            val serviceUuids = result.scanRecord?.serviceUuids?.map { it.uuid.toString().lowercase() }.orEmpty()
            val matchesUuid = serviceUuids.any { uuid ->
                KNOWN_ELM327_SERVICE_UUIDS.any { it.toString().lowercase() == uuid }
            }
            val isLikelyObd = matchesUuid || isObdDeviceName(rawName)

            val item = DiscoveredObdBleDevice(
                name = rawName,
                address = device.address,
                rssi = result.rssi,
                isLikelyObdAdapter = isLikelyObd,
                advertisedServiceUuids = serviceUuids,
                bluetoothDevice = device
            )

            _uiState.update { current ->
                val mutable = current.discoveredDevices.toMutableList()
                val existingIdx = mutable.indexOfFirst { it.address == item.address }
                if (existingIdx >= 0) {
                    mutable[existingIdx] = item
                } else {
                    mutable.add(item)
                }
                val sorted = mutable.sortedWith(
                    compareByDescending<DiscoveredObdBleDevice> { it.isLikelyObdAdapter }
                        .thenByDescending { it.rssi }
                )
                current.copy(discoveredDevices = sorted)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            _uiState.update {
                it.copy(
                    connectionState = ObdConnectionState.ERROR,
                    statusMessage = "BLE scan failed with code $errorCode."
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(deviceItem: DiscoveredObdBleDevice) {
        stopBleScan()
        stopSimulationAndDisconnect()

        val hwDevice = deviceItem.bluetoothDevice
            ?: runCatching { bluetoothAdapter?.getRemoteDevice(deviceItem.address) }.getOrNull()

        if (hwDevice == null) {
            _uiState.update {
                it.copy(
                    connectionState = ObdConnectionState.ERROR,
                    statusMessage = "Unable to resolve Bluetooth hardware address ${deviceItem.address}."
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                connectionState = ObdConnectionState.CONNECTING,
                connectedAdapterName = deviceItem.name,
                connectedAdapterAddress = deviceItem.address,
                isSafetySimulationActive = false,
                statusMessage = "Connecting GATT session to ${deviceItem.name} (${deviceItem.address})..."
            )
        }

        activeGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            hwDevice.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            hwDevice.connectGatt(context, false, gattCallback)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _uiState.update {
                        it.copy(
                            connectionState = ObdConnectionState.INITIALIZING_ELM327,
                            statusMessage = "Connected to GATT server. Negotiating MTU & discovering ELM327 services..."
                        )
                    }
                    runCatching { gatt.requestMtu(185) }
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    pollingJob?.cancel()
                    txCharacteristic = null
                    rxCharacteristic = null
                    runCatching { gatt.close() }
                    activeGatt = null
                    _uiState.update {
                        it.copy(
                            connectionState = ObdConnectionState.DISCONNECTED,
                            statusMessage = "Disconnected from OBD-II BLE adapter (status=$status)."
                        )
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _uiState.update {
                    it.copy(
                        connectionState = ObdConnectionState.ERROR,
                        statusMessage = "GATT service discovery failed ($status)."
                    )
                }
                return
            }

            var foundTx: BluetoothGattCharacteristic? = null
            var foundRx: BluetoothGattCharacteristic? = null

            for (service in gatt.services) {
                for (characteristic in service.characteristics) {
                    val props = characteristic.properties
                    val canWrite = (props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                        (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                    val canNotify = (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
                        (props and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0

                    val charUuidStr = characteristic.uuid.toString().lowercase()
                    if (canNotify && (foundRx == null || charUuidStr in PREFERRED_ELM_CHAR_UUIDS)) {
                        foundRx = characteristic
                    }
                    if (canWrite && (foundTx == null || charUuidStr in PREFERRED_ELM_CHAR_UUIDS)) {
                        foundTx = characteristic
                    }
                }
            }

            if (foundTx == null || foundRx == null) {
                _uiState.update {
                    it.copy(
                        connectionState = ObdConnectionState.ERROR,
                        statusMessage = "Connected BLE device does not expose ELM327 UART TX/RX GATT characteristics."
                    )
                }
                return
            }

            txCharacteristic = foundTx
            rxCharacteristic = foundRx

            gatt.setCharacteristicNotification(foundRx, true)
            val cccd = foundRx.getDescriptor(CCCD_UUID)
            if (cccd != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(cccd)
                }
            }

            startRealElm327PollingLoop()
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncomingBleBytes(value)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            val bytes = characteristic.value ?: return
            handleIncomingBleBytes(bytes)
        }
    }

    private fun handleIncomingBleBytes(bytes: ByteArray) {
        val chunk = String(bytes, StandardCharsets.US_ASCII)
        synchronized(responseBuffer) {
            responseBuffer.append(chunk)
            if (chunk.contains(">") || responseBuffer.length > 256) {
                val fullResp = responseBuffer.toString()
                    .replace(">", "")
                    .replace("\r", " ")
                    .replace("\n", " ")
                    .trim()
                responseBuffer.clear()
                if (fullResp.isNotEmpty()) {
                    processElm327Response(lastSentCommand, fullResp)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeElmCommand(command: String): Boolean {
        val gatt = activeGatt ?: return false
        val tx = txCharacteristic ?: return false
        lastSentCommand = command
        val payload = "$command\r".toByteArray(StandardCharsets.US_ASCII)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val writeType = if ((tx.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                } else {
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                }
                gatt.writeCharacteristic(tx, payload, writeType) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                tx.value = payload
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(tx)
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun startRealElm327PollingLoop() {
        pollingJob?.cancel()
        sessionStartedAtMillis = System.currentTimeMillis()
        simulatedVoltageOverride = null
        simulatedElapsedOffsetSeconds = 0L
        hasNotifiedLowBatteryInSession = false

        pollingJob = scope.launch {
            val initCommands = listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0", "ATRV", "03", "07")
            for (cmd in initCommands) {
                if (!isActive) return@launch
                writeElmCommand(cmd)
                delay(320L)
            }

            _uiState.update {
                it.copy(
                    connectionState = ObdConnectionState.STREAMING_LIVE,
                    statusMessage = "Streaming live SAE J1979 PIDs, DTCs & 12V battery monitor from ${it.connectedAdapterName}."
                )
            }

            val cyclicPids = listOf("010C", "010D", "0105", "010F", "010B", "0111", "0104", "0106", "0107", "0142", "ATRV")
            var tick = 0
            while (isActive && activeGatt != null) {
                val pid = cyclicPids[tick % cyclicPids.size]
                writeElmCommand(pid)
                delay(280L)
                tick++
                if (tick % 25 == 0) {
                    writeElmCommand("03")
                    delay(300L)
                }
            }
        }
    }

    /**
     * Launches an interactive Bench ECU Replay mode (clearly badged as SAFETY SIMULATION MODE)
     * so mechanics can test live ELM327 hex frame decoding, DTC extraction, and AI prompt injection
     * even when not physically plugged into a running vehicle's OBD-II port.
     */
    fun startSafetySimulationMode(vehiclePresetIndex: Int = 0) {
        stopBleScan()
        stopSimulationAndDisconnect()

        val preset = SIMULATION_PRESETS[vehiclePresetIndex.coerceIn(0, SIMULATION_PRESETS.lastIndex)]
        sessionStartedAtMillis = System.currentTimeMillis()
        simulatedVoltageOverride = null
        // Preset 2 (Silverado C0035 + P0562) simulates an extended 36-minute diagnostic session with low voltage
        simulatedElapsedOffsetSeconds = if (preset.ecuVoltage < 12.1) 36 * 60L else 0L
        hasNotifiedLowBatteryInSession = false

        _uiState.update {
            it.copy(
                connectionState = ObdConnectionState.SAFETY_SIMULATION_MODE,
                isSafetySimulationActive = true,
                connectedAdapterName = "Vgate iCar Pro BLE 4.0 (${preset.vehicleLabel})",
                connectedAdapterAddress = "AA:BB:CC:11:22:33",
                elmFirmwareVersion = "ELM327 v2.3 (ISO 15765-4 CAN 11/500)",
                batteryMonitor = ObdBatteryMonitorState(),
                terminalLog = emptyList(),
                statusMessage = "SAFETY SIMULATION MODE: Replaying live ELM327 CAN-Bus hex frames for ${preset.vehicleLabel}."
            )
        }

        // Feed realistic raw ELM327 hex responses through the exact same SAE J1979 parser!
        processElm327Response("ATZ", "ELM327 v2.3")
        processElm327Response("ATRV", preset.atrvResponse)
        processElm327Response("03", preset.mode03Hex)
        processElm327Response("07", preset.mode07Hex)

        pollingJob = scope.launch {
            var step = 0
            while (isActive) {
                val overrideVolts = simulatedVoltageOverride
                val isKoeoDrain = overrideVolts != null && overrideVolts < 12.3
                val wavePhase = (step % 12)
                val rpmDelta = when (wavePhase) {
                    2, 3 -> 420 // Throttle tip-in acceleration
                    4, 5 -> 680 // Load peak
                    6 -> 260
                    9, 10 -> -90 // Deceleration coast
                    else -> Random.nextInt(-35, 45)
                }
                val rpm = if (isKoeoDrain) {
                    0 // Key-On Engine-Off (KOEO) extended diagnostic drain state
                } else {
                    (preset.baseRpm + rpmDelta + Random.nextInt(-25, 30)).coerceIn(680, 4200)
                }
                val rpmVal = rpm * 4
                val rpmA = (rpmVal shr 8) and 0xFF
                val rpmB = rpmVal and 0xFF
                processElm327Response("010C", String.format(Locale.US, "410C%02X%02X", rpmA, rpmB))

                val speed = if (isKoeoDrain) 0 else (preset.baseSpeedKmh + (rpmDelta / 30) + (step % 4)).coerceAtLeast(0)
                processElm327Response("010D", String.format(Locale.US, "410D%02X", speed))

                val coolantWave = ((step / 3) % 5) - 1
                val coolantC = (preset.coolantC + coolantWave).coerceIn(72, 108)
                val coolantRaw = (coolantC + 40).coerceIn(0, 255)
                processElm327Response("0105", String.format(Locale.US, "4105%02X", coolantRaw))

                val iatC = (preset.intakeTempC + ((step / 4) % 3) - 1).coerceIn(15, 75)
                val iatRaw = (iatC + 40).coerceIn(0, 255)
                processElm327Response("010F", String.format(Locale.US, "410F%02X", iatRaw))

                val mapWave = if (isKoeoDrain) 101 else (preset.mapKpa + (rpmDelta / 18) + Random.nextInt(-3, 4)).coerceIn(28, 235)
                processElm327Response("010B", String.format(Locale.US, "410B%02X", mapWave))

                val throttlePct = if (isKoeoDrain) 8 else (preset.throttlePercent + (rpmDelta / 22)).coerceIn(11, 88)
                val throttleRaw = ((throttlePct * 255) / 100).coerceIn(0, 255)
                processElm327Response("0111", String.format(Locale.US, "4111%02X", throttleRaw))

                val loadPct = if (isKoeoDrain) 0 else (preset.engineLoadPercent + (rpmDelta / 19)).coerceIn(16, 94)
                val loadRaw = ((loadPct * 255) / 100).coerceIn(0, 255)
                processElm327Response("0104", String.format(Locale.US, "4104%02X", loadRaw))

                // Closed-loop O2 feedback oscillation on STFT + load-dependent LTFT shift
                val stftOscillation = kotlin.math.sin(step * 0.75) * 2.4 + Random.nextDouble(-0.6, 0.6)
                val dynamicStft = (preset.stftPercent + stftOscillation).coerceIn(-25.0, 28.0)
                val stftRaw = (((dynamicStft / 100.0) * 128.0) + 128.0).roundToInt().coerceIn(0, 255)
                processElm327Response("0106", String.format(Locale.US, "4106%02X", stftRaw))

                val ltftDrift = kotlin.math.cos(step * 0.35) * 1.1
                val dynamicLtft = (preset.ltftPercent + ltftDrift).coerceIn(-25.0, 28.0)
                val ltftRaw = (((dynamicLtft / 100.0) * 128.0) + 128.0).roundToInt().coerceIn(0, 255)
                processElm327Response("0107", String.format(Locale.US, "4107%02X", ltftRaw))

                // Gradual KOEO parasitic drain when long session drain is active
                val baseVolts = overrideVolts ?: preset.ecuVoltage
                val progressiveDrain = if (isKoeoDrain) (step * 0.01).coerceAtMost(0.28) else 0.0
                val activeVolts = (baseVolts - progressiveDrain + Random.nextDouble(-0.02, 0.02)).coerceIn(10.8, 15.2)
                val mv = (activeVolts * 1000.0).roundToInt()
                val voltA = (mv shr 8) and 0xFF
                val voltB = mv and 0xFF
                processElm327Response("ATRV", String.format(Locale.US, "%.2fV", activeVolts))
                processElm327Response("0142", String.format(Locale.US, "4142%02X%02X", voltA, voltB))

                step++
                delay(1_050L)
            }
        }
    }

    /**
     * Immediately queries ELM327 Pin-16 (`ATRV`) and SAE J1979 Mode 01 PID `0142` (Control Module Voltage).
     */
    fun pollBatteryVoltageNow() {
        if (_uiState.value.isSafetySimulationActive) {
            val v = simulatedVoltageOverride
                ?: _uiState.value.liveSensors.controlModuleVoltage.takeIf { it > 0.0 }
                ?: 13.85
            processElm327Response("ATRV", String.format(Locale.US, "%.2fV", v))
            val mv = (v * 1000.0).roundToInt()
            val voltA = (mv shr 8) and 0xFF
            val voltB = mv and 0xFF
            processElm327Response("0142", String.format(Locale.US, "4142%02X%02X", voltA, voltB))
        } else if (activeGatt != null) {
            scope.launch {
                writeElmCommand("ATRV")
                delay(260L)
                writeElmCommand("0142")
            }
        }
    }

    /**
     * Simulates an extended 42-minute Key-On Engine-Off (KOEO) diagnostic session where parasitic
     * module draw has discharged the 12V vehicle battery down to 11.68V, triggering the OBD-II
     * Low Vehicle Battery Warning and system notification.
     */
    fun simulateLongSessionLowBatteryDrain() {
        if (_uiState.value.connectionState == ObdConnectionState.DISCONNECTED) {
            startSafetySimulationMode(2)
        }
        if (sessionStartedAtMillis == 0L) {
            sessionStartedAtMillis = System.currentTimeMillis()
        }
        simulatedVoltageOverride = 11.68
        simulatedElapsedOffsetSeconds = 42 * 60L // 42 minutes of KOEO diagnostic drain
        hasNotifiedLowBatteryInSession = false

        processElm327Response("010C", "410C0000") // 0 RPM (KOEO)
        processElm327Response("ATRV", "11.68V")
        val mv = 11680
        val voltA = (mv shr 8) and 0xFF
        val voltB = mv and 0xFF
        processElm327Response("0142", String.format(Locale.US, "4142%02X%02X", voltA, voltB))
        _uiState.update {
            it.copy(
                statusMessage = "WARNING: Vehicle 12V battery dropped to 11.68V during 42m KOEO diagnostic session!"
            )
        }
    }

    /**
     * Simulates connecting a 12V shop battery maintainer / starting the engine (14.12V)
     * to clear the low-battery condition.
     */
    fun simulateHealthyAlternatorRecovery() {
        if (_uiState.value.connectionState == ObdConnectionState.DISCONNECTED) {
            startSafetySimulationMode(0)
        }
        simulatedVoltageOverride = 14.12
        simulatedElapsedOffsetSeconds = 0L
        hasNotifiedLowBatteryInSession = false

        processElm327Response("010C", "410C0B80") // ~736 RPM idle
        processElm327Response("ATRV", "14.12V")
        val mv = 14120
        val voltA = (mv shr 8) and 0xFF
        val voltB = mv and 0xFF
        processElm327Response("0142", String.format(Locale.US, "4142%02X%02X", voltA, voltB))
        _uiState.update {
            it.copy(
                statusMessage = "12V Bus stabilized at 14.12V (Shop Maintainer / Alternator Charging Active)."
            )
        }
    }

    fun requestDtcScanNow() {
        if (_uiState.value.isSafetySimulationActive) {
            processElm327Response("03", "430200160299")
            processElm327Response("07", "47010171")
            _uiState.update {
                it.copy(statusMessage = "Mode 03 & Mode 07 DTC scan completed (${it.activeDtcs.size} fault codes decoded).")
            }
        } else if (activeGatt != null) {
            scope.launch {
                writeElmCommand("03")
                delay(320L)
                writeElmCommand("07")
            }
        }
    }

    fun clearDtcsMode04() {
        if (_uiState.value.isSafetySimulationActive) {
            processElm327Response("04", "44")
        } else if (activeGatt != null) {
            writeElmCommand("04")
        }
        _uiState.update {
            it.copy(
                activeDtcs = emptyList(),
                statusMessage = "Sent SAE J1979 Mode 04 (Clear DTCs / Reset MIL). Stored & pending fault codes cleared."
            )
        }
    }

    fun sendCustomCommand(rawCmd: String) {
        val clean = rawCmd.trim().uppercase()
        if (clean.isEmpty()) return
        if (_uiState.value.isSafetySimulationActive) {
            val simulatedReply = when (clean) {
                "ATZ" -> "ELM327 v2.3"
                "ATI" -> "ELM327 v2.3"
                "ATRV" -> String.format(Locale.US, "%.2fV", simulatedVoltageOverride ?: 14.1)
                "ATDP" -> "AUTO, ISO 15765-4 (CAN 11/500)"
                "03" -> "430200160299"
                "07" -> "47010171"
                "04" -> "44"
                else -> "OK"
            }
            processElm327Response(clean, simulatedReply)
        } else {
            writeElmCommand(clean)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopSimulationAndDisconnect() {
        pollingJob?.cancel()
        pollingJob = null
        sessionStartedAtMillis = 0L
        simulatedVoltageOverride = null
        simulatedElapsedOffsetSeconds = 0L
        hasNotifiedLowBatteryInSession = false
        runCatching {
            activeGatt?.disconnect()
            activeGatt?.close()
        }
        activeGatt = null
        txCharacteristic = null
        rxCharacteristic = null
        _uiState.update {
            it.copy(
                connectionState = ObdConnectionState.DISCONNECTED,
                isSafetySimulationActive = false,
                connectedAdapterName = "",
                connectedAdapterAddress = "",
                batteryMonitor = ObdBatteryMonitorState(),
                statusMessage = "OBD-II BLE session closed."
            )
        }
    }

    /**
     * Parses raw ELM327 AT responses and SAE J1979 hex frames (Mode 41 live PIDs, Mode 43/47 DTCs).
     */
    internal fun processElm327Response(command: String, rawResponse: String) {
        val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val compactHex = rawResponse.replace(" ", "").uppercase()

        var pendingNotificationState: ObdBatteryMonitorState? = null
        var pendingAdapterName = ""

        _uiState.update { state ->
            val updatedLogs = (listOf(
                ObdTerminalLogEntry(
                    timestamp = timeFmt,
                    rawCommand = command.ifBlank { "RX" },
                    rawResponse = rawResponse
                )
            ) + state.terminalLog).take(30)

            var sensors = state.liveSensors
            var dtcs = state.activeDtcs
            var firmware = state.elmFirmwareVersion

            if (rawResponse.contains("ELM327", ignoreCase = true)) {
                firmware = rawResponse.trim()
            } else if (command == "ATRV" || rawResponse.endsWith("V", ignoreCase = true)) {
                val v = rawResponse.uppercase().replace("V", "").trim().toDoubleOrNull()
                if (v != null) {
                    sensors = sensors.copy(
                        elmBatteryVoltage = v,
                        controlModuleVoltage = if (sensors.controlModuleVoltage == 0.0) v else sensors.controlModuleVoltage,
                        lastUpdatedMillis = System.currentTimeMillis()
                    )
                }
            } else if (compactHex.startsWith("41")) {
                sensors = parseMode01PidHex(compactHex, sensors)
            } else if (compactHex.startsWith("43")) {
                val parsedMode03 = parseDtcHexFrame(compactHex, "CONFIRMED (Mode 03)")
                val existingPending = dtcs.filter { it.statusType.startsWith("PENDING") }
                dtcs = (parsedMode03 + existingPending).distinctBy { it.code }
            } else if (compactHex.startsWith("47")) {
                val parsedMode07 = parseDtcHexFrame(compactHex, "PENDING (Mode 07)")
                val existingConfirmed = dtcs.filter { it.statusType.startsWith("CONFIRMED") }
                dtcs = (existingConfirmed + parsedMode07).distinctBy { it.code }
            } else if (compactHex.startsWith("44")) {
                dtcs = emptyList()
            }

            val updatedBattery = evaluateBatteryMonitorState(
                sensors = sensors,
                previous = state.batteryMonitor
            )

            if (updatedBattery.isLowBatteryWarningActive && !hasNotifiedLowBatteryInSession) {
                hasNotifiedLowBatteryInSession = true
                pendingNotificationState = updatedBattery
                pendingAdapterName = state.connectedAdapterName
            }

            val nowMillis = System.currentTimeMillis()
            val shouldAppendSample = !state.isTelemetryPaused && (
                command == "0142" ||
                    (compactHex.startsWith("41") && (nowMillis - lastSampleRecordedMillis >= 850L))
                )

            val updatedHistory = if (shouldAppendSample) {
                lastSampleRecordedMillis = nowMillis
                sampleCounter += 1
                val shortTime = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(nowMillis))
                val newSample = ObdTelemetrySample(
                    sampleIndex = sampleCounter,
                    timestampLabel = shortTime,
                    elapsedSeconds = updatedBattery.sessionElapsedSeconds.toInt(),
                    engineRpm = sensors.engineRpm,
                    coolantTempC = sensors.coolantTempC,
                    intakeAirTempC = sensors.intakeAirTempC,
                    shortTermFuelTrimB1 = sensors.shortTermFuelTrimB1,
                    longTermFuelTrimB1 = sensors.longTermFuelTrimB1,
                    manifoldPressureKpa = sensors.manifoldPressureKpa,
                    throttlePositionPercent = sensors.throttlePositionPercent,
                    engineLoadPercent = sensors.engineLoadPercent,
                    controlModuleVoltage = if (sensors.controlModuleVoltage > 0.0) {
                        sensors.controlModuleVoltage
                    } else {
                        updatedBattery.currentVoltage
                    },
                    vehicleSpeedKmh = sensors.vehicleSpeedKmh
                )
                (state.telemetryHistory + newSample).takeLast(36)
            } else {
                state.telemetryHistory
            }

            state.copy(
                elmFirmwareVersion = firmware,
                liveSensors = sensors,
                batteryMonitor = updatedBattery,
                telemetryHistory = updatedHistory,
                activeDtcs = dtcs,
                terminalLog = updatedLogs
            )
        }

        pendingNotificationState?.let { batt ->
            ServiceIntervalNotificationManager.postObdLowBatteryWarningNotification(
                context = context,
                adapterName = pendingAdapterName,
                voltage = batt.currentVoltage,
                sessionMinutes = (batt.sessionElapsedSeconds / 60L).toInt().coerceAtLeast(1),
                estimatedRemainingMinutes = batt.estimatedSafeMinutesRemaining,
                warningMessage = "${batt.warningHeadline} — ${batt.warningRecommendation}"
            )
        }
    }

    private fun evaluateBatteryMonitorState(
        sensors: ObdLiveSensorData,
        previous: ObdBatteryMonitorState
    ): ObdBatteryMonitorState {
        val rawVoltage = when {
            sensors.controlModuleVoltage > 0.0 -> sensors.controlModuleVoltage
            sensors.elmBatteryVoltage > 0.0 -> sensors.elmBatteryVoltage
            else -> previous.currentVoltage
        }
        if (rawVoltage <= 0.0) return previous

        val now = System.currentTimeMillis()
        val realElapsedSec = if (sessionStartedAtMillis > 0L) {
            ((now - sessionStartedAtMillis) / 1000L).coerceAtLeast(1L)
        } else {
            1L
        }
        val totalElapsedSec = realElapsedSec + simulatedElapsedOffsetSeconds
        val initialV = when {
            simulatedElapsedOffsetSeconds > 0L && rawVoltage < 12.2 -> 12.62 // Baseline before KOEO drain
            previous.initialVoltage > 0.0 -> previous.initialVoltage
            else -> rawVoltage
        }
        val minV = if (previous.minVoltage == 0.0) rawVoltage else minOf(previous.minVoltage, rawVoltage)
        val maxV = if (previous.maxVoltage == 0.0) rawVoltage else maxOf(previous.maxVoltage, rawVoltage)

        val elapsedHours = (totalElapsedSec / 3600.0).coerceAtLeast(0.05)
        val dropRatePerHour = ((initialV - rawVoltage) / elapsedHours).coerceAtLeast(0.0)

        // Estimate State of Charge (SoC %) for 12V lead-acid / AGM automotive starter battery
        val socPercent = when {
            rawVoltage >= 13.2 -> 100
            rawVoltage >= 12.65 -> 100
            rawVoltage <= 11.40 -> 8
            else -> (((rawVoltage - 11.40) / (12.65 - 11.40)) * 92.0 + 8.0).roundToInt().coerceIn(5, 100)
        }

        // Safe ECU brownout floor is 11.50V (below 11.5V, CAN transceivers and ECM flash risk dropout)
        val voltsAboveFloor = (rawVoltage - 11.50).coerceAtLeast(0.0)
        val effectiveDrainPerHour = when {
            rawVoltage >= 13.3 -> 0.0 // Alternator / charger actively supplying current
            dropRatePerHour > 0.15 -> dropRatePerHour
            else -> 0.65 // Typical 12A–18A KOEO ignition + module draw drains ~0.65V/hr
        }
        val estSafeMinutes = if (effectiveDrainPerHour <= 0.01) {
            180
        } else {
            ((voltsAboveFloor / effectiveDrainPerHour) * 60.0).roundToInt().coerceIn(2, 180)
        }

        val sessionMins = (totalElapsedSec / 60L).toInt()
        val isEngineRunning = sensors.engineRpm >= 450 && rawVoltage >= 13.1

        val healthLevel: ObdBatteryHealthLevel
        val powerSourceStatus: String
        val warningHeadline: String
        val warningRecommendation: String
        val isWarningActive: Boolean

        when {
            rawVoltage > 14.85 -> {
                healthLevel = ObdBatteryHealthLevel.OVERCHARGING_WARNING
                powerSourceStatus = "ALTERNATOR OVERVOLTAGE (>14.8V)"
                warningHeadline = "Alternator Regulator Overvoltage (${String.format(Locale.US, "%.2fV", rawVoltage)})"
                warningRecommendation = "Inspect alternator voltage regulator & sense wire; excessive voltage can damage CAN-Bus ECUs."
                isWarningActive = true
            }
            rawVoltage < 11.85 -> {
                healthLevel = ObdBatteryHealthLevel.CRITICAL_ECU_BROWNOUT
                powerSourceStatus = "CRITICAL KOEO BATTERY DRAIN (${sessionMins}m Session)"
                warningHeadline = "CRITICAL LOW VEHICLE BATTERY: ${String.format(Locale.US, "%.2fV", rawVoltage)} (${socPercent}% SoC)"
                warningRecommendation = "Only ~$estSafeMinutes min before 11.5V ECU brownout cutoff! Connect a 12V shop battery maintainer or start engine immediately before running bi-directional tests."
                isWarningActive = true
            }
            rawVoltage < 12.20 -> {
                healthLevel = ObdBatteryHealthLevel.LOW_BATTERY_WARNING
                powerSourceStatus = if (sensors.engineRpm > 500) {
                    "UNDERCHARGING / FAILING ALTERNATOR"
                } else {
                    "LOW KOEO BATTERY (${sessionMins}m Diagnostic Session)"
                }
                warningHeadline = "Low Vehicle Battery Warning: ${String.format(Locale.US, "%.2fV", rawVoltage)} during OBD-II Session"
                warningRecommendation = if (sensors.engineRpm > 500) {
                    "Engine is running (${sensors.engineRpm} RPM) but bus voltage is ${String.format(Locale.US, "%.2fV", rawVoltage)}. Check alternator output & ground strap."
                } else {
                    "Prolonged KOEO diagnostic session has discharged battery to ${socPercent}%. Connect a 12V battery charger or start engine (~$estSafeMinutes min safe time left)."
                }
                isWarningActive = true
            }
            !isEngineRunning && (rawVoltage < 12.48 || totalElapsedSec >= 20 * 60L) -> {
                healthLevel = ObdBatteryHealthLevel.MODERATE_KOEO_DRAIN
                powerSourceStatus = "KEY-ON ENGINE-OFF (KOEO DRAIN)"
                warningHeadline = "KOEO Parasitic Load Active (${String.format(Locale.US, "%.2fV", rawVoltage)} • ${sessionMins}m Elapsed)"
                warningRecommendation = "Turn off headlights/HVAC blower or attach a 12V maintainer for long diagnostic sessions (~$estSafeMinutes min safe window)."
                isWarningActive = totalElapsedSec >= 25 * 60L
            }
            else -> {
                healthLevel = ObdBatteryHealthLevel.OPTIMAL
                powerSourceStatus = if (isEngineRunning) {
                    "ALTERNATOR CHARGING (${sensors.engineRpm} RPM)"
                } else {
                    "12V BUS HEALTHY"
                }
                warningHeadline = "Vehicle 12V Battery & Charging Bus Nominal (${String.format(Locale.US, "%.2fV", rawVoltage)})"
                warningRecommendation = "Safe for extended OBD-II live telemetry and bi-directional actuator diagnostics."
                isWarningActive = false
            }
        }

        return ObdBatteryMonitorState(
            currentVoltage = rawVoltage,
            initialVoltage = initialV,
            minVoltage = minV,
            maxVoltage = maxV,
            stateOfChargePercent = socPercent,
            sessionElapsedSeconds = totalElapsedSec,
            voltageDropPerHour = dropRatePerHour,
            estimatedSafeMinutesRemaining = estSafeMinutes,
            healthLevel = healthLevel,
            powerSourceStatus = powerSourceStatus,
            warningHeadline = warningHeadline,
            warningRecommendation = warningRecommendation,
            isLowBatteryWarningActive = isWarningActive,
            isLongSessionDrainSimulated = simulatedElapsedOffsetSeconds > 0L
        )
    }

    private fun parseMode01PidHex(compactHex: String, current: ObdLiveSensorData): ObdLiveSensorData {
        if (compactHex.length < 6) return current
        val pid = compactHex.substring(2, 4)
        val dataHex = compactHex.substring(4)
        val now = System.currentTimeMillis()

        return try {
            when (pid) {
                "0C" -> { // Engine RPM: ((A * 256) + B) / 4
                    if (dataHex.length >= 4) {
                        val a = dataHex.substring(0, 2).toInt(16)
                        val b = dataHex.substring(2, 4).toInt(16)
                        val rpm = ((a * 256) + b) / 4
                        current.copy(engineRpm = rpm, lastUpdatedMillis = now)
                    } else current
                }
                "0D" -> { // Vehicle Speed km/h: A
                    val a = dataHex.substring(0, 2).toInt(16)
                    current.copy(vehicleSpeedKmh = a, lastUpdatedMillis = now)
                }
                "05" -> { // Engine Coolant Temp °C: A - 40
                    val a = dataHex.substring(0, 2).toInt(16)
                    current.copy(coolantTempC = a - 40, lastUpdatedMillis = now)
                }
                "0F" -> { // Intake Air Temp °C: A - 40
                    val a = dataHex.substring(0, 2).toInt(16)
                    current.copy(intakeAirTempC = a - 40, lastUpdatedMillis = now)
                }
                "0B" -> { // Intake Manifold Absolute Pressure kPa: A
                    val a = dataHex.substring(0, 2).toInt(16)
                    current.copy(manifoldPressureKpa = a, lastUpdatedMillis = now)
                }
                "11" -> { // Throttle Position %: (A * 100) / 255
                    val a = dataHex.substring(0, 2).toInt(16)
                    current.copy(throttlePositionPercent = (a * 100) / 255, lastUpdatedMillis = now)
                }
                "04" -> { // Calculated Engine Load %: (A * 100) / 255
                    val a = dataHex.substring(0, 2).toInt(16)
                    current.copy(engineLoadPercent = (a * 100) / 255, lastUpdatedMillis = now)
                }
                "06" -> { // Short Term Fuel Trim Bank 1 %: ((A - 128) * 100) / 128
                    val a = dataHex.substring(0, 2).toInt(16)
                    val stft = ((a - 128) * 100.0) / 128.0
                    current.copy(shortTermFuelTrimB1 = stft, lastUpdatedMillis = now)
                }
                "07" -> { // Long Term Fuel Trim Bank 1 %: ((A - 128) * 100) / 128
                    val a = dataHex.substring(0, 2).toInt(16)
                    val ltft = ((a - 128) * 100.0) / 128.0
                    current.copy(longTermFuelTrimB1 = ltft, lastUpdatedMillis = now)
                }
                "42" -> { // Control Module Voltage V: ((A * 256) + B) / 1000.0
                    if (dataHex.length >= 4) {
                        val a = dataHex.substring(0, 2).toInt(16)
                        val b = dataHex.substring(2, 4).toInt(16)
                        val volts = ((a * 256) + b) / 1000.0
                        current.copy(controlModuleVoltage = volts, lastUpdatedMillis = now)
                    } else current
                }
                else -> current
            }
        } catch (_: Exception) {
            current
        }
    }

    /**
     * Decodes SAE J1979 Mode 03 (43...) and Mode 07 (47...) hex payloads into 5-character OBD-II DTCs.
     */
    private fun parseDtcHexFrame(compactHex: String, statusType: String): List<ObdDtcFault> {
        val result = mutableListOf<ObdDtcFault>()
        // Format: 43 <count byte optional on CAN> <2-byte DTC 1> <2-byte DTC 2> ...
        val payload = compactHex.removePrefix("43").removePrefix("47")
        val alignedPayload = if (payload.length % 4 == 2) payload.substring(2) else payload

        var idx = 0
        while (idx + 4 <= alignedPayload.length) {
            val wordHex = alignedPayload.substring(idx, idx + 4)
            idx += 4
            if (wordHex == "0000") continue
            val b1 = wordHex.substring(0, 2).toIntOrNull(16) ?: continue
            val b2 = wordHex.substring(2, 4).toIntOrNull(16) ?: continue

            val typeBits = (b1 shr 6) and 0x03
            val prefix = when (typeBits) {
                0 -> 'P'
                1 -> 'C'
                2 -> 'B'
                else -> 'U'
            }
            val digit1 = (b1 shr 4) and 0x03
            val digit2 = b1 and 0x0F
            val digit34 = String.format(Locale.US, "%02X", b2)
            val code = "$prefix$digit1${ Integer.toHexString(digit2).uppercase() }$digit34"

            val meta = lookupDtcMetadata(code)
            result.add(
                ObdDtcFault(
                    code = code,
                    statusType = statusType,
                    description = meta.first,
                    systemCategory = meta.second,
                    severity = meta.third
                )
            )
        }
        return result
    }

    private data class SimulationVehiclePreset(
        val vehicleLabel: String,
        val atrvResponse: String,
        val mode03Hex: String,
        val mode07Hex: String,
        val baseRpm: Int,
        val baseSpeedKmh: Int,
        val coolantC: Int,
        val intakeTempC: Int,
        val mapKpa: Int,
        val throttlePercent: Int,
        val engineLoadPercent: Int,
        val stftPercent: Double,
        val ltftPercent: Double,
        val ecuVoltage: Double
    )

    companion object {
        @Volatile
        private var sharedInstance: ObdBleScannerManager? = null

        fun getInstance(context: Context): ObdBleScannerManager {
            return sharedInstance ?: synchronized(this) {
                sharedInstance ?: ObdBleScannerManager(context.applicationContext).also {
                    sharedInstance = it
                }
            }
        }

        internal fun buildInitialLiveSensorData(): ObdLiveSensorData {
            return ObdLiveSensorData(
                engineRpm = 1850,
                vehicleSpeedKmh = 68,
                coolantTempC = 95,
                intakeAirTempC = 43,
                manifoldPressureKpa = 146,
                throttlePositionPercent = 41,
                engineLoadPercent = 63,
                shortTermFuelTrimB1 = 8.6,
                longTermFuelTrimB1 = 11.7,
                controlModuleVoltage = 13.85,
                elmBatteryVoltage = 13.85,
                lastUpdatedMillis = System.currentTimeMillis()
            )
        }

        internal fun buildInitialTelemetryHistory(): List<ObdTelemetrySample> {
            val rpmSeries = listOf(
                820, 860, 1140, 1480, 1790, 2120, 2460, 2310,
                1940, 1810, 1860, 2240, 2580, 2190, 1910, 1850
            )
            val coolantSeries = listOf(
                84, 85, 86, 88, 89, 91, 92, 93,
                94, 94, 95, 96, 97, 96, 95, 95
            )
            val iatSeries = listOf(
                36, 36, 37, 38, 39, 41, 42, 42,
                41, 41, 42, 44, 45, 44, 43, 43
            )
            val stftSeries = listOf(
                3.1, 3.9, 5.5, 7.0, 8.6, 10.2, 11.7, 9.4,
                7.8, 6.3, 8.6, 10.9, 12.5, 9.4, 7.8, 8.6
            )
            val ltftSeries = listOf(
                9.4, 9.4, 10.2, 10.9, 11.7, 12.5, 12.5, 11.7,
                10.9, 10.9, 11.7, 12.5, 13.3, 12.5, 11.7, 11.7
            )
            val mapSeries = listOf(
                42, 46, 78, 108, 134, 156, 172, 160,
                138, 128, 136, 164, 182, 158, 144, 146
            )
            val throttleSeries = listOf(
                14, 16, 26, 35, 42, 54, 62, 52,
                39, 36, 40, 56, 66, 49, 42, 41
            )
            val loadSeries = listOf(
                22, 25, 38, 51, 61, 74, 82, 73,
                58, 54, 60, 76, 85, 70, 64, 63
            )
            return rpmSeries.indices.map { idx ->
                val sec = (idx + 1) * 2
                ObdTelemetrySample(
                    sampleIndex = idx + 1,
                    timestampLabel = String.format(Locale.US, "00:%02d", sec),
                    elapsedSeconds = sec,
                    engineRpm = rpmSeries[idx],
                    coolantTempC = coolantSeries[idx],
                    intakeAirTempC = iatSeries[idx],
                    shortTermFuelTrimB1 = stftSeries[idx],
                    longTermFuelTrimB1 = ltftSeries[idx],
                    manifoldPressureKpa = mapSeries[idx],
                    throttlePositionPercent = throttleSeries[idx],
                    engineLoadPercent = loadSeries[idx],
                    controlModuleVoltage = 13.85 + ((idx % 3) - 1) * 0.04,
                    vehicleSpeedKmh = if (rpmSeries[idx] < 1000) 0 else (rpmSeries[idx] / 28)
                )
            }
        }

        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        // Standard BLE Service UUIDs used by ELM327, Vgate iCar Pro, Veepeak, OBDLink CX, and Nordic UART adapters
        val KNOWN_ELM327_SERVICE_UUIDS: List<UUID> = listOf(
            UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb"),
            UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"),
            UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"),
            UUID.fromString("e7810a71-73ae-499d-8c15-faa9aef0c3f2"),
            UUID.fromString("000018f0-0000-1000-8000-00805f9b34fb")
        )

        private val PREFERRED_ELM_CHAR_UUIDS = setOf(
            "0000fff1-0000-1000-8000-00805f9b34fb",
            "0000fff2-0000-1000-8000-00805f9b34fb",
            "0000ffe1-0000-1000-8000-00805f9b34fb",
            "6e400002-b5a3-f393-e0a9-e50e24dcca9e",
            "6e400003-b5a3-f393-e0a9-e50e24dcca9e",
            "bef8d6c9-9c21-4c9e-b632-bd58c1009f9f"
        )

        private val SIMULATION_PRESETS = listOf(
            // Preset 0: Ford F-150 3.5L EcoBoost (U0100 + P06A6 + P0299)
            SimulationVehiclePreset(
                vehicleLabel = "2021 Ford F-150 3.5L EcoBoost",
                atrvResponse = "13.9V",
                mode03Hex = "4302C10006A6", // U0100 (C100) + P06A6 (06A6)
                mode07Hex = "47010299",     // P0299 Turbo Underboost
                baseRpm = 1850,
                baseSpeedKmh = 68,
                coolantC = 96,
                intakeTempC = 44,
                mapKpa = 148,
                throttlePercent = 42,
                engineLoadPercent = 64,
                stftPercent = 8.6,
                ltftPercent = 11.7,
                ecuVoltage = 13.85
            ),
            // Preset 1: Toyota Camry 2.5L VVT-iE (P0016 + P0171)
            SimulationVehiclePreset(
                vehicleLabel = "2020 Toyota Camry 2.5L A25A",
                atrvResponse = "14.2V",
                mode03Hex = "43010016",     // P0016 Crank-Cam Correlation
                mode07Hex = "47010171",     // P0171 System Too Lean Bank 1
                baseRpm = 780,
                baseSpeedKmh = 0,
                coolantC = 88,
                intakeTempC = 31,
                mapKpa = 36,
                throttlePercent = 14,
                engineLoadPercent = 24,
                stftPercent = 14.8,
                ltftPercent = 16.4,
                ecuVoltage = 14.18
            ),
            // Preset 2: Chevy Silverado 1500 ABS / Charging (C0035 + P0562)
            SimulationVehiclePreset(
                vehicleLabel = "2019 Chevy Silverado 1500",
                atrvResponse = "11.8V",
                mode03Hex = "430240350562", // C0035 (4035) Left Front Wheel Speed + P0562 System Voltage Low
                mode07Hex = "4700",
                baseRpm = 1100,
                baseSpeedKmh = 35,
                coolantC = 91,
                intakeTempC = 37,
                mapKpa = 58,
                throttlePercent = 22,
                engineLoadPercent = 38,
                stftPercent = -1.6,
                ltftPercent = 2.3,
                ecuVoltage = 11.75
            )
        )

        fun requiredPermissionsArray(): Array<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            } else {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            }
        }

        fun hasRequiredBlePermissions(context: Context): Boolean {
            return requiredPermissionsArray().all { perm ->
                ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
            }
        }

        fun isObdDeviceName(name: String): Boolean {
            val lower = name.lowercase()
            val keywords = listOf(
                "obd", "elm327", "vgate", "icar", "veepeak", "obdlink",
                "konnwei", "carista", "viecar", "bafx", "fixd", "bluedriver", "ios-vlink"
            )
            return keywords.any { lower.contains(it) }
        }

        fun lookupDtcMetadata(code: String): Triple<String, SystemCategory, String> {
            return when (code.uppercase()) {
                "P0016" -> Triple(
                    "Crankshaft Position - Camshaft Position Correlation (Bank 1 Sensor A)",
                    SystemCategory.MECHANICAL,
                    "HIGH"
                )
                "P0299" -> Triple(
                    "Turbocharger / Supercharger 'A' Underboost Condition",
                    SystemCategory.MECHANICAL,
                    "MODERATE"
                )
                "P0171" -> Triple(
                    "System Too Lean (Bank 1) - Vacuum Leak or Low Fuel Pressure",
                    SystemCategory.MECHANICAL,
                    "MODERATE"
                )
                "P0300" -> Triple(
                    "Random / Multiple Cylinder Misfire Detected",
                    SystemCategory.MECHANICAL,
                    "HIGH"
                )
                "P0455" -> Triple(
                    "Evaporative Emission System Leak Detected (Large Leak)",
                    SystemCategory.MECHANICAL,
                    "LOW"
                )
                "P0562" -> Triple(
                    "System Voltage Low - Alternator / Charging Circuit Or Ground Drop",
                    SystemCategory.ELECTRICAL,
                    "HIGH"
                )
                "P06A6" -> Triple(
                    "Sensor Reference Voltage 'A' Circuit Range/Performance (5V VREF1)",
                    SystemCategory.COMPUTER_CONTROLLER,
                    "CRITICAL"
                )
                "U0100" -> Triple(
                    "Lost Communication With ECM/PCM 'A' on High-Speed CAN-Bus",
                    SystemCategory.COMPUTER_CONTROLLER,
                    "CRITICAL"
                )
                "C0035" -> Triple(
                    "Left Front Wheel Speed Sensor / Electronic Brake Booster & ABS Circuit",
                    SystemCategory.HYDRAULIC,
                    "HIGH"
                )
                "C102D" -> Triple(
                    "EPAS Electric Rack & Pinion Steering Torque Sensor Circuit",
                    SystemCategory.PNEUMATIC,
                    "HIGH"
                )
                else -> {
                    val category = when (code.firstOrNull()?.uppercaseChar()) {
                        'U' -> SystemCategory.COMPUTER_CONTROLLER
                        'C' -> SystemCategory.HYDRAULIC
                        'B' -> SystemCategory.ELECTRICAL
                        else -> SystemCategory.MECHANICAL
                    }
                    Triple(
                        "SAE J2012 Automotive Diagnostic Trouble Code ($code)",
                        category,
                        "HIGH"
                    )
                }
            }
        }
    }
}
