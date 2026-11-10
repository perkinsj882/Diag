package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.SystemCategory
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HazardCrimson
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SignalEmerald
import com.example.util.AutomotiveUnitConverter
import com.example.util.AutomotiveUnitSystem
import com.example.util.DiscoveredObdBleDevice
import com.example.util.ObdBatteryHealthLevel
import com.example.util.ObdBatteryMonitorState
import com.example.util.ObdBleScannerManager
import com.example.util.ObdBleUiState
import com.example.util.ObdConnectionState
import com.example.util.ObdDtcFault
import com.example.util.ObdLiveSensorData
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ObdBleScannerBannerCard(
    unitSystem: AutomotiveUnitSystem = AutomotiveUnitSystem.METRIC,
    onUnitSystemChange: ((AutomotiveUnitSystem) -> Unit)? = null,
    onInjectObdTelemetry: (
        symptomPrompt: String,
        telemetrySummaryLabel: String,
        detectedCategory: SystemCategory?,
        detectedVehicleName: String?
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bleManager = remember(context) { ObdBleScannerManager.getInstance(context) }
    val uiState by bleManager.uiState.collectAsStateWithLifecycle()
    var showScannerDialog by remember { mutableStateOf(false) }
    var showInlineRecharts by remember { mutableStateOf(false) }

    DisposableEffect(bleManager) {
        onDispose {
            bleManager.stopBleScan()
        }
    }

    val isConnectedOrSimulating = uiState.connectionState == ObdConnectionState.STREAMING_LIVE ||
        uiState.connectionState == ObdConnectionState.SAFETY_SIMULATION_MODE

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        bleManager.refreshPermissionsAndAdapterState()
        showScannerDialog = true
        if (ObdBleScannerManager.hasRequiredBlePermissions(context)) {
            bleManager.startBleScan()
        }
    }

    if (showScannerDialog) {
        ObdBleScannerDialog(
            uiState = uiState,
            unitSystem = unitSystem,
            onUnitSystemChange = onUnitSystemChange,
            onRequestScan = {
                if (ObdBleScannerManager.hasRequiredBlePermissions(context)) {
                    bleManager.startBleScan()
                } else {
                    permissionLauncher.launch(ObdBleScannerManager.requiredPermissionsArray())
                }
            },
            onStopScan = { bleManager.stopBleScan() },
            onConnectDevice = { dev -> bleManager.connectToDevice(dev) },
            onStartSafetySimulation = { presetIdx ->
                bleManager.startSafetySimulationMode(presetIdx)
            },
            onSimulateLowBatteryDrain = {
                bleManager.simulateLongSessionLowBatteryDrain()
            },
            onSimulateBatteryRecovery = {
                bleManager.simulateHealthyAlternatorRecovery()
            },
            onPollBatteryVoltage = {
                bleManager.pollBatteryVoltageNow()
            },
            onDisconnect = { bleManager.stopSimulationAndDisconnect() },
            onReadDtcs = { bleManager.requestDtcScanNow() },
            onClearDtcs = { bleManager.clearDtcsMode04() },
            onSendCustomCommand = { cmd -> bleManager.sendCustomCommand(cmd) },
            onInjectIntoDiagnosis = {
                val prompt = buildInjectedPrompt(
                    uiState.activeDtcs,
                    uiState.liveSensors,
                    uiState.batteryMonitor,
                    uiState.connectedAdapterName
                )
                val summary = uiState.liveSensors.toTelemetrySummaryString(
                    dtcs = uiState.activeDtcs,
                    adapterName = uiState.connectedAdapterName.ifBlank { "ELM327 BLE" },
                    isSimulation = uiState.isSafetySimulationActive
                )
                val category = if (uiState.batteryMonitor.isLowBatteryWarningActive && uiState.activeDtcs.isEmpty()) {
                    SystemCategory.ELECTRICAL
                } else {
                    uiState.activeDtcs.firstOrNull()?.systemCategory
                }
                val vehicleHint = uiState.connectedAdapterName
                    .substringAfter("(", "")
                    .substringBefore(")", "")
                    .takeIf { it.isNotBlank() }
                onInjectObdTelemetry(prompt, summary, category, vehicleHint)
                showScannerDialog = false
            },
            onDismiss = { showScannerDialog = false }
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (uiState.batteryMonitor.isLowBatteryWarningActive) 1.5.dp else 1.dp,
                color = when {
                    uiState.batteryMonitor.isLowBatteryWarningActive -> HazardCrimson
                    uiState.isSafetySimulationActive -> SafetyAmber.copy(alpha = 0.65f)
                    isConnectedOrSimulating -> SignalEmerald.copy(alpha = 0.65f)
                    else -> ElectricCyan.copy(alpha = 0.45f)
                },
                shape = RoundedCornerShape(14.dp)
            )
            .testTag("obd_ble_scanner_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    uiState.batteryMonitor.isLowBatteryWarningActive -> HazardCrimson.copy(alpha = 0.22f)
                                    isConnectedOrSimulating -> SignalEmerald.copy(alpha = 0.2f)
                                    else -> ElectricCyan.copy(alpha = 0.18f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                uiState.batteryMonitor.isLowBatteryWarningActive -> Icons.Default.BatteryAlert
                                uiState.connectionState == ObdConnectionState.SCANNING -> Icons.Default.BluetoothSearching
                                uiState.connectionState == ObdConnectionState.STREAMING_LIVE ||
                                    uiState.connectionState == ObdConnectionState.SAFETY_SIMULATION_MODE -> Icons.Default.BluetoothConnected
                                else -> Icons.Default.Bluetooth
                            },
                            contentDescription = "OBD-II BLE ELM327 Scanner",
                            tint = when {
                                uiState.batteryMonitor.isLowBatteryWarningActive -> HazardCrimson
                                isConnectedOrSimulating -> SignalEmerald
                                else -> ElectricCyan
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "OBD-II BLE ELM327 & Battery Monitor",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            if (uiState.isSafetySimulationActive) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(SafetyAmber.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "SAFETY SIMULATION MODE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SafetyAmber,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (isConnectedOrSimulating) {
                                "${uiState.connectedAdapterName} • ${uiState.activeDtcs.size} DTC(s) • ${String.format(Locale.US, "%.2fV", uiState.batteryMonitor.currentVoltage)}"
                            } else {
                                "Connect via BLE GATT for live DTCs, PIDs & 12V battery drain guard"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = {
                        if (ObdBleScannerManager.hasRequiredBlePermissions(context)) {
                            showScannerDialog = true
                        } else {
                            permissionLauncher.launch(ObdBleScannerManager.requiredPermissionsArray())
                        }
                    },
                    modifier = Modifier.testTag("open_obd_ble_scanner_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConnectedOrSimulating) SignalEmerald else ElectricCyan,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Bluetooth,
                        contentDescription = "Scan OBD-II BLE",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isConnectedOrSimulating) "Live PIDs" else "Scan BLE",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Quick-Test Strip for Battery Status Monitor even before opening full dialog
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { bleManager.simulateLongSessionLowBatteryDrain() },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("banner_simulate_low_battery_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.BatteryAlert,
                        contentDescription = "Test Low Battery Warning",
                        tint = HazardCrimson,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Test Low Battery (11.68V)",
                        style = MaterialTheme.typography.labelSmall,
                        color = HazardCrimson,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = { bleManager.simulateHealthyAlternatorRecovery() },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("banner_simulate_battery_ok_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.BatteryChargingFull,
                        contentDescription = "Simulate 14.1V Charging",
                        tint = SignalEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "14.1V Charging Bus",
                        style = MaterialTheme.typography.labelSmall,
                        color = SignalEmerald,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // OBD-II Battery Status Monitor & Low Battery Warning Banner
            AnimatedVisibility(visible = isConnectedOrSimulating) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                    ObdBatteryStatusMonitorPanel(
                        batteryMonitor = uiState.batteryMonitor,
                        onPollVoltageNow = { bleManager.pollBatteryVoltageNow() },
                        onSimulateLowBattery = { bleManager.simulateLongSessionLowBatteryDrain() },
                        onSimulateRecovery = { bleManager.simulateHealthyAlternatorRecovery() }
                    )

                    // Mini Live Sensor Strip (respects Metric / Imperial unitSystem)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MiniPidPill(
                            label = "RPM",
                            value = "${uiState.liveSensors.engineRpm}",
                            accent = ElectricCyan,
                            modifier = Modifier.weight(1f)
                        )
                        MiniPidPill(
                            label = "COOLANT",
                            value = AutomotiveUnitConverter.formatObdTemperature(
                                uiState.liveSensors.coolantTempC,
                                unitSystem
                            ),
                            accent = if (uiState.liveSensors.coolantTempC > 102) HazardCrimson else SignalEmerald,
                            modifier = Modifier.weight(1f)
                        )
                        MiniPidPill(
                            label = "MAP/BOOST",
                            value = if (unitSystem == AutomotiveUnitSystem.METRIC) {
                                "${uiState.liveSensors.manifoldPressureKpa} kPa"
                            } else {
                                String.format(Locale.US, "%+.1f PSI", uiState.liveSensors.boostPsi)
                            },
                            accent = SafetyAmber,
                            modifier = Modifier.weight(1f)
                        )
                        MiniPidPill(
                            label = "ECU VOLTS",
                            value = String.format(Locale.US, "%.2fV", uiState.batteryMonitor.currentVoltage),
                            accent = if (uiState.batteryMonitor.isLowBatteryWarningActive) HazardCrimson else ElectricCyan,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Active DTC Chips
                    if (uiState.activeDtcs.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            uiState.activeDtcs.forEach { dtc ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(HazardCrimson.copy(alpha = 0.16f))
                                        .border(1.dp, HazardCrimson.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "${dtc.code}: ${dtc.description.take(36)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = HazardCrimson,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val prompt = buildInjectedPrompt(
                                    uiState.activeDtcs,
                                    uiState.liveSensors,
                                    uiState.batteryMonitor,
                                    uiState.connectedAdapterName
                                )
                                val summary = uiState.liveSensors.toTelemetrySummaryString(
                                    dtcs = uiState.activeDtcs,
                                    adapterName = uiState.connectedAdapterName.ifBlank { "ELM327 BLE" },
                                    isSimulation = uiState.isSafetySimulationActive
                                )
                                val category = if (uiState.batteryMonitor.isLowBatteryWarningActive && uiState.activeDtcs.isEmpty()) {
                                    SystemCategory.ELECTRICAL
                                } else {
                                    uiState.activeDtcs.firstOrNull()?.systemCategory
                                }
                                val vehicleHint = uiState.connectedAdapterName
                                    .substringAfter("(", "")
                                    .substringBefore(")", "")
                                    .takeIf { it.isNotBlank() }
                                onInjectObdTelemetry(prompt, summary, category, vehicleHint)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("inject_obd_telemetry_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = ElectricCyan
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeveloperBoard,
                                contentDescription = "Inject OBD-II Data",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Apply DTCs, PIDs & Battery Status to Diagnosis")
                        }
                        OutlinedButton(
                            onClick = { showInlineRecharts = !showInlineRecharts },
                            modifier = Modifier.testTag("toggle_obd_inline_recharts_button")
                        ) {
                            Text(
                                text = if (showInlineRecharts) "Hide Recharts" else "Live Recharts",
                                style = MaterialTheme.typography.labelMedium,
                                color = ElectricCyan
                            )
                        }
                    }
                }
            }

            if (!isConnectedOrSimulating) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = { showInlineRecharts = !showInlineRecharts },
                        modifier = Modifier.testTag("toggle_obd_inline_recharts_button")
                    ) {
                        Text(
                            text = if (showInlineRecharts) {
                                "Hide Real-Time Engine Recharts (RPM / Coolant / Fuel Trims)"
                            } else {
                                "Show Real-Time Engine Recharts (RPM / Coolant / Fuel Trims)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                    }
                }
            }

            AnimatedVisibility(visible = showInlineRecharts) {
                ObdRealtimeRechartsDashboardCard(
                    unitSystem = unitSystem,
                    onUnitSystemChange = onUnitSystemChange
                )
            }
        }
    }
}

/**
 * Dedicated OBD-II 12V Battery Status Monitor & Low-Battery Long Session Warning Panel.
 */
@Composable
fun ObdBatteryStatusMonitorPanel(
    batteryMonitor: ObdBatteryMonitorState,
    onPollVoltageNow: () -> Unit,
    onSimulateLowBattery: () -> Unit,
    onSimulateRecovery: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = when (batteryMonitor.healthLevel) {
        ObdBatteryHealthLevel.OPTIMAL -> SignalEmerald
        ObdBatteryHealthLevel.MODERATE_KOEO_DRAIN -> SafetyAmber
        ObdBatteryHealthLevel.LOW_BATTERY_WARNING,
        ObdBatteryHealthLevel.CRITICAL_ECU_BROWNOUT,
        ObdBatteryHealthLevel.OVERCHARGING_WARNING -> HazardCrimson
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accentColor.copy(alpha = 0.1f))
            .border(
                width = if (batteryMonitor.isLowBatteryWarningActive) 1.5.dp else 1.dp,
                color = accentColor.copy(alpha = 0.65f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
            .testTag("obd_battery_monitor_panel"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (batteryMonitor.isLowBatteryWarningActive) {
                        Icons.Default.BatteryAlert
                    } else {
                        Icons.Default.BatteryChargingFull
                    },
                    contentDescription = "OBD-II 12V Battery Status Monitor",
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "OBD-II 12V Battery Monitor: ${String.format(Locale.US, "%.2fV", batteryMonitor.currentVoltage)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = accentColor,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(accentColor.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = batteryMonitor.healthLevel.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = accentColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = "${batteryMonitor.powerSourceStatus} • Session: ${batteryMonitor.formattedSessionDuration} • SoC: ${batteryMonitor.stateOfChargePercent}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onPollVoltageNow,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("poll_obd_battery_voltage_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Poll ATRV Battery Voltage",
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // State of Charge Progress Bar
        LinearProgressIndicator(
            progress = { (batteryMonitor.stateOfChargePercent / 100f).coerceIn(0.05f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = accentColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        // Prominent Low Battery Warning Banner during Long Diagnostic Sessions
        AnimatedVisibility(visible = batteryMonitor.isLowBatteryWarningActive) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = HazardCrimson.copy(alpha = 0.18f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, HazardCrimson, RoundedCornerShape(10.dp))
                    .testTag("obd_low_battery_warning_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Low Vehicle Battery Warning",
                        tint = HazardCrimson,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = batteryMonitor.warningHeadline,
                            style = MaterialTheme.typography.labelMedium,
                            color = HazardCrimson,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = batteryMonitor.warningRecommendation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Safe KOEO Window: ~${batteryMonitor.estimatedSafeMinutesRemaining} min before 11.5V CAN-Bus brownout • Min Recorded: ${String.format(Locale.US, "%.2fV", batteryMonitor.minVoltage)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = SafetyAmber
                        )
                    }
                }
            }
        }

        // Quick Battery Simulation Controls inside Monitor Panel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onSimulateLowBattery,
                modifier = Modifier
                    .weight(1f)
                    .testTag("simulate_low_battery_drain_button")
            ) {
                Text(
                    text = "Simulate 42m Low Battery (11.68V)",
                    style = MaterialTheme.typography.labelSmall,
                    color = HazardCrimson
                )
            }
            OutlinedButton(
                onClick = onSimulateRecovery,
                modifier = Modifier
                    .weight(1f)
                    .testTag("simulate_battery_recovery_button")
            ) {
                Text(
                    text = "Connect Charger (14.12V)",
                    style = MaterialTheme.typography.labelSmall,
                    color = SignalEmerald
                )
            }
        }
    }
}

@Composable
private fun MiniPidPill(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.1f))
            .border(0.5.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = accent,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ObdBleScannerDialog(
    uiState: ObdBleUiState,
    unitSystem: AutomotiveUnitSystem = AutomotiveUnitSystem.METRIC,
    onUnitSystemChange: ((AutomotiveUnitSystem) -> Unit)? = null,
    onRequestScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnectDevice: (DiscoveredObdBleDevice) -> Unit,
    onStartSafetySimulation: (Int) -> Unit,
    onSimulateLowBatteryDrain: () -> Unit,
    onSimulateBatteryRecovery: () -> Unit,
    onPollBatteryVoltage: () -> Unit,
    onDisconnect: () -> Unit,
    onReadDtcs: () -> Unit,
    onClearDtcs: () -> Unit,
    onSendCustomCommand: (String) -> Unit,
    onInjectIntoDiagnosis: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedPresetIdx by remember { mutableIntStateOf(0) }
    var customAtCommand by remember { mutableStateOf("") }
    var showTerminal by remember { mutableStateOf(false) }

    val isConnectedOrSimulating = uiState.connectionState == ObdConnectionState.STREAMING_LIVE ||
        uiState.connectionState == ObdConnectionState.SAFETY_SIMULATION_MODE

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(1.5.dp, ElectricCyan.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                .testTag("obd_ble_scanner_dialog"),
            color = MaterialTheme.colorScheme.background
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Dialog Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = "BLE OBD-II ELM327",
                                tint = ElectricCyan,
                                modifier = Modifier.size(26.dp)
                            )
                            Column {
                                Text(
                                    text = "OBD-II BLE ELM327 Scanner & Telemetry",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "GATT Services: FFF0 / FFE0 / Nordic UART • SAE J1979 Mode 01/03/04/07",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricCyan
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("close_obd_dialog_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close OBD Scanner"
                            )
                        }
                    }
                }

                // Prominent Safety Simulation Mode Banner when Bench Replay is Active
                if (uiState.isSafetySimulationActive) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = SafetyAmber.copy(alpha = 0.16f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, SafetyAmber, RoundedCornerShape(12.dp))
                                .testTag("obd_safety_simulation_banner")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Safety Simulation Mode Active",
                                    tint = SafetyAmber
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "SAFETY SIMULATION MODE ACTIVE (BENCH ECU REPLAY)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = SafetyAmber,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Replaying authentic ELM327 CAN-Bus hex responses without requiring a running vehicle engine.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // BLE Hardware Scan & Connection Controls Card
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = uiState.statusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (uiState.connectionState == ObdConnectionState.SCANNING) {
                                    OutlinedButton(
                                        onClick = onStopScan,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = ElectricCyan
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Stop BLE Scan")
                                    }
                                } else {
                                    Button(
                                        onClick = onRequestScan,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("start_ble_scan_button"),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ElectricCyan,
                                            contentColor = Color.Black
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.BluetoothSearching,
                                            contentDescription = "Scan for BLE Adapters",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Scan BLE Adapters", fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (isConnectedOrSimulating) {
                                    OutlinedButton(
                                        onClick = onDisconnect,
                                        modifier = Modifier.testTag("disconnect_obd_button")
                                    ) {
                                        Text("Disconnect")
                                    }
                                }
                            }

                            // Bench ECU Replay / Safety Simulation Mode Selector
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            Text(
                                text = "No physical ELM327 adapter plugged in? Test with Bench ECU Replay (Safety Simulation Mode):",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val presets = listOf(
                                    "F-150 3.5L (U0100 + P06A6 + P0299)",
                                    "Camry 2.5L (P0016 + P0171)",
                                    "Silverado 1500 (C0035 + P0562 Low Batt)"
                                )
                                presets.forEachIndexed { idx, label ->
                                    FilterChip(
                                        selected = uiState.isSafetySimulationActive && selectedPresetIdx == idx,
                                        onClick = {
                                            selectedPresetIdx = idx
                                            onStartSafetySimulation(idx)
                                        },
                                        label = { Text(label) },
                                        modifier = Modifier.testTag("obd_sim_preset_$idx")
                                    )
                                }
                            }
                        }
                    }
                }

                // Discovered BLE Devices List
                if (uiState.discoveredDevices.isNotEmpty() && !isConnectedOrSimulating) {
                    item {
                        Text(
                            text = "Discovered BLE Devices (${uiState.discoveredDevices.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = ElectricCyan
                        )
                    }
                    items(uiState.discoveredDevices, key = { it.address }) { dev ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onConnectDevice(dev) },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = dev.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (dev.isLikelyObdAdapter) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(SignalEmerald.copy(alpha = 0.2f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "ELM327 GATT",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = SignalEmerald
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "MAC: ${dev.address} • Signal: ${dev.rssi} dBm",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(onClick = { onConnectDevice(dev) }) {
                                    Text("Connect")
                                }
                            }
                        }
                    }
                }

                // Live Sensor Telemetry Dashboard, Battery Status Monitor & DTC Reader when Connected / Simulating
                if (isConnectedOrSimulating) {
                    // Dedicated OBD-II Battery Status Monitor Card inside Dialog
                    item {
                        ObdBatteryStatusMonitorPanel(
                            batteryMonitor = uiState.batteryMonitor,
                            onPollVoltageNow = onPollBatteryVoltage,
                            onSimulateLowBattery = onSimulateLowBatteryDrain,
                            onSimulateRecovery = onSimulateBatteryRecovery
                        )
                    }

                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Live SAE J1979 Mode 01 Sensor Telemetry",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = ElectricCyan
                                        )
                                        Text(
                                            text = "${uiState.connectedAdapterName} • ${uiState.elmFirmwareVersion}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (onUnitSystemChange != null) {
                                        OutlinedButton(
                                            onClick = {
                                                val next = if (unitSystem == AutomotiveUnitSystem.METRIC) {
                                                    AutomotiveUnitSystem.IMPERIAL
                                                } else {
                                                    AutomotiveUnitSystem.METRIC
                                                }
                                                onUnitSystemChange(next)
                                            },
                                            modifier = Modifier.testTag("dialog_toggle_obd_units_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.SwapHoriz,
                                                contentDescription = "Toggle OBD Units",
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(unitSystem.label)
                                        }
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Speed,
                                            contentDescription = "Live OBD-II PIDs",
                                            tint = SignalEmerald
                                        )
                                    }
                                }

                                val speedFormatted = AutomotiveUnitConverter.formatObdSpeed(
                                    uiState.liveSensors.vehicleSpeedKmh,
                                    unitSystem
                                )

                                // Row 1: RPM, Speed, Coolant
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SensorGaugeTile(
                                        pidCode = "010C",
                                        title = "ENGINE RPM",
                                        value = "${uiState.liveSensors.engineRpm}",
                                        unit = "rpm",
                                        accent = ElectricCyan,
                                        modifier = Modifier.weight(1f)
                                    )
                                    SensorGaugeTile(
                                        pidCode = "010D",
                                        title = "SPEED",
                                        value = speedFormatted.first,
                                        unit = speedFormatted.second,
                                        accent = SignalEmerald,
                                        modifier = Modifier.weight(1f)
                                    )
                                    SensorGaugeTile(
                                        pidCode = "0105",
                                        title = "COOLANT",
                                        value = AutomotiveUnitConverter.formatObdTemperature(
                                            uiState.liveSensors.coolantTempC,
                                            unitSystem
                                        ),
                                        unit = if (unitSystem == AutomotiveUnitSystem.METRIC) {
                                            "${(uiState.liveSensors.coolantTempC * 9 / 5) + 32}°F"
                                        } else {
                                            "${uiState.liveSensors.coolantTempC}°C"
                                        },
                                        accent = if (uiState.liveSensors.coolantTempC > 102) HazardCrimson else SafetyAmber,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                // Row 2: MAP/Boost, Fuel Trims, ECU Voltage
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SensorGaugeTile(
                                        pidCode = "010B",
                                        title = "MAP / BOOST",
                                        value = if (unitSystem == AutomotiveUnitSystem.METRIC) {
                                            "${uiState.liveSensors.manifoldPressureKpa} kPa"
                                        } else {
                                            String.format(Locale.US, "%.1f PSI", AutomotiveUnitConverter.kpaToPsi(uiState.liveSensors.manifoldPressureKpa.toDouble()))
                                        },
                                        unit = String.format(Locale.US, "Boost %+.1f PSI", uiState.liveSensors.boostPsi),
                                        accent = SafetyAmber,
                                        modifier = Modifier.weight(1f)
                                    )
                                    SensorGaugeTile(
                                        pidCode = "0106/07",
                                        title = "STFT / LTFT B1",
                                        value = String.format(Locale.US, "%+.1f%%", uiState.liveSensors.shortTermFuelTrimB1),
                                        unit = String.format(Locale.US, "LT %+.1f%%", uiState.liveSensors.longTermFuelTrimB1),
                                        accent = if (kotlin.math.abs(uiState.liveSensors.longTermFuelTrimB1) > 10.0) HazardCrimson else SignalEmerald,
                                        modifier = Modifier.weight(1f)
                                    )
                                    SensorGaugeTile(
                                        pidCode = "0142",
                                        title = "ECU VOLTAGE",
                                        value = String.format(Locale.US, "%.2fV", uiState.batteryMonitor.currentVoltage),
                                        unit = "SoC ${uiState.batteryMonitor.stateOfChargePercent}%",
                                        accent = if (uiState.batteryMonitor.isLowBatteryWarningActive) HazardCrimson else ElectricCyan,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                // Row 3: Throttle Position, Engine Load, IAT
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SensorGaugeTile(
                                        pidCode = "0111",
                                        title = "THROTTLE",
                                        value = "${uiState.liveSensors.throttlePositionPercent}%",
                                        unit = "TPS",
                                        accent = ElectricCyan,
                                        modifier = Modifier.weight(1f)
                                    )
                                    SensorGaugeTile(
                                        pidCode = "0104",
                                        title = "ENGINE LOAD",
                                        value = "${uiState.liveSensors.engineLoadPercent}%",
                                        unit = "CALC",
                                        accent = SafetyAmber,
                                        modifier = Modifier.weight(1f)
                                    )
                                    SensorGaugeTile(
                                        pidCode = "010F",
                                        title = "INTAKE AIR",
                                        value = AutomotiveUnitConverter.formatObdTemperature(
                                            uiState.liveSensors.intakeAirTempC,
                                            unitSystem
                                        ),
                                        unit = "IAT",
                                        accent = SignalEmerald,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // Diagnostic Trouble Codes (Mode 03 / Mode 07 / Mode 04) Card
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Diagnostic Trouble Codes (${uiState.activeDtcs.size})",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (uiState.activeDtcs.isNotEmpty()) HazardCrimson else SignalEmerald
                                        )
                                        Text(
                                            text = "SAE J1979 Mode 03 (Confirmed) & Mode 07 (Pending)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(
                                            onClick = onReadDtcs,
                                            modifier = Modifier.testTag("poll_dtcs_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Read DTCs",
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Read")
                                        }
                                        if (uiState.activeDtcs.isNotEmpty()) {
                                            OutlinedButton(
                                                onClick = onClearDtcs,
                                                modifier = Modifier.testTag("clear_dtcs_mode04_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteSweep,
                                                    contentDescription = "Clear DTCs Mode 04",
                                                    tint = HazardCrimson,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Clear (04)", color = HazardCrimson)
                                            }
                                        }
                                    }
                                }

                                if (uiState.activeDtcs.isEmpty()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "No DTCs",
                                            tint = SignalEmerald
                                        )
                                        Text(
                                            text = "No stored or pending Diagnostic Trouble Codes in ECU memory.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SignalEmerald
                                        )
                                    }
                                } else {
                                    uiState.activeDtcs.forEach { dtc ->
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = HazardCrimson.copy(alpha = 0.1f)
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .border(0.5.dp, HazardCrimson.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(10.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Text(
                                                            text = dtc.code,
                                                            style = MaterialTheme.typography.titleMedium,
                                                            color = HazardCrimson,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        CategoryBadge(categoryLabel = dtc.systemCategory.label)
                                                    }
                                                    Text(
                                                        text = dtc.statusType,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = SafetyAmber
                                                    )
                                                }
                                                Text(
                                                    text = dtc.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }

                                Button(
                                    onClick = onInjectIntoDiagnosis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("dialog_inject_obd_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ElectricCyan,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeveloperBoard,
                                        contentDescription = "Inject into AI Diagnosis"
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Use DTCs, Live PIDs & Battery State in AI Lab",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Raw ELM327 Hex Command Terminal
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showTerminal = !showTerminal },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Terminal,
                                            contentDescription = "ELM327 Terminal",
                                            tint = ElectricCyan
                                        )
                                        Text(
                                            text = "ELM327 AT / Hex Bus Monitor (${uiState.terminalLog.size} frames)",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = ElectricCyan
                                        )
                                    }
                                    Text(
                                        text = if (showTerminal) "Hide" else "Expand",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                AnimatedVisibility(visible = showTerminal) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = customAtCommand,
                                                onValueChange = { customAtCommand = it },
                                                label = { Text("Send ELM327 AT / PID Hex (e.g. ATRV, 03, 010C)") },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = {
                                                    onSendCustomCommand(customAtCommand)
                                                    customAtCommand = ""
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                                    contentDescription = "Send AT Command",
                                                    tint = ElectricCyan
                                                )
                                            }
                                        }

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF090E17))
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            uiState.terminalLog.take(10).forEach { entry ->
                                                Text(
                                                    text = "[${entry.timestamp}] > ${entry.rawCommand}  <=  ${entry.rawResponse}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = SignalEmerald
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SensorGaugeTile(
    pidCode: String,
    title: String,
    value: String,
    unit: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.1f))
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(vertical = 8.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "PID $pidCode",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = accent,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = unit,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun buildInjectedPrompt(
    dtcs: List<ObdDtcFault>,
    sensors: ObdLiveSensorData,
    batteryMonitor: ObdBatteryMonitorState,
    adapterName: String
): String {
    val dtcBlock = if (dtcs.isEmpty()) {
        "No stored/pending DTCs in Mode 03/07."
    } else {
        dtcs.joinToString("; ") { "${it.code} [${it.statusType}]: ${it.description}" }
    }
    val batteryNote = if (batteryMonitor.isLowBatteryWarningActive) {
        " [LOW BATTERY ALERT: ${String.format(Locale.US, "%.2fV", batteryMonitor.currentVoltage)} (${batteryMonitor.stateOfChargePercent}% SoC) after ${batteryMonitor.formattedSessionDuration} session — ${batteryMonitor.warningHeadline}]"
    } else {
        ""
    }
    return buildString {
        append("OBD-II BLE Scan ($adapterName) — ")
        append("Fault Codes: $dtcBlock. ")
        append("Freeze-Frame / Live Sensor PIDs: Engine ${sensors.engineRpm} RPM, ")
        append("Speed ${sensors.vehicleSpeedKmh} km/h, ")
        append("Coolant ${sensors.coolantTempC}°C, ")
        append("IAT ${sensors.intakeAirTempC}°C, ")
        append("MAP ${sensors.manifoldPressureKpa} kPa (${String.format(Locale.US, "%+.1f", sensors.boostPsi)} PSI boost), ")
        append("STFT B1 ${String.format(Locale.US, "%+.1f", sensors.shortTermFuelTrimB1)}%, ")
        append("LTFT B1 ${String.format(Locale.US, "%+.1f", sensors.longTermFuelTrimB1)}%, ")
        append("Throttle ${sensors.throttlePositionPercent}%, ")
        append("Engine Load ${sensors.engineLoadPercent}%, ")
        append("ECU Supply ${String.format(Locale.US, "%.2f", batteryMonitor.currentVoltage)}V.$batteryNote")
    }
}
