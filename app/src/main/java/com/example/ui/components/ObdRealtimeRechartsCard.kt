package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HazardCrimson
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SignalEmerald
import com.example.util.AutomotiveUnitConverter
import com.example.util.AutomotiveUnitSystem
import com.example.util.ObdBleScannerManager
import com.example.util.ObdConnectionState
import com.example.util.ObdTelemetrySample
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Real-time Recharts Engine Telemetry Dashboard UI that visualizes incoming live OBD-II
 * parameters (Engine RPM, Coolant Temperature, and Short/Long-Term Fuel Trim percentages)
 * from the active BLE ELM327 connection or Bench ECU Replay stream.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ObdRealtimeRechartsDashboardCard(
    unitSystem: AutomotiveUnitSystem = AutomotiveUnitSystem.METRIC,
    onUnitSystemChange: ((AutomotiveUnitSystem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bleManager = remember(context) { ObdBleScannerManager.getInstance(context) }
    val obdState by bleManager.uiState.collectAsStateWithLifecycle()

    var localUnitSystem by remember(unitSystem) { mutableStateOf(unitSystem) }
    val activeUnits = if (onUnitSystemChange != null) unitSystem else localUnitSystem
    var selectedPresetIdx by remember { mutableIntStateOf(0) }
    var inspectedSampleIndex by remember { mutableStateOf<Int?>(null) }

    val samples = remember(obdState.telemetryHistory) {
        obdState.telemetryHistory.ifEmpty {
            ObdBleScannerManager.buildInitialTelemetryHistory()
        }
    }

    val latestSample = samples.last()
    val inspectedSample = remember(samples, inspectedSampleIndex) {
        val idx = inspectedSampleIndex
        if (idx != null && idx in samples.indices) {
            samples[idx]
        } else {
            latestSample
        }
    }

    val isLiveStreaming = obdState.connectionState == ObdConnectionState.STREAMING_LIVE ||
        obdState.connectionState == ObdConnectionState.SAFETY_SIMULATION_MODE

    val peakRpm = remember(samples) { samples.maxOfOrNull { it.engineRpm } ?: 0 }
    val minRpm = remember(samples) { samples.minOfOrNull { it.engineRpm } ?: 0 }
    val maxCoolantC = remember(samples) { samples.maxOfOrNull { it.coolantTempC } ?: 0 }
    val totalFuelTrim = inspectedSample.totalFuelTrimB1
    val isFuelTrimOutOfSpec = abs(totalFuelTrim) >= 15.0
    val isCoolantHot = inspectedSample.coolantTempC >= 104

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.5.dp,
                color = when {
                    isLiveStreaming && !obdState.isTelemetryPaused -> SignalEmerald.copy(alpha = 0.75f)
                    obdState.isTelemetryPaused -> SafetyAmber.copy(alpha = 0.75f)
                    else -> ElectricCyan.copy(alpha = 0.55f)
                },
                shape = RoundedCornerShape(18.dp)
            )
            .testTag("obd_realtime_recharts_dashboard_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header & Live OBD-II Connection Status Pill
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
                        imageVector = Icons.Default.ShowChart,
                        contentDescription = "Real-Time OBD-II Recharts Dashboard",
                        tint = ElectricCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "RECHARTS • LIVE OBD-II ENGINE TELEMETRY",
                            style = MaterialTheme.typography.labelMedium,
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Real-Time RPM, Coolant Temp & Fuel Trim % Stream",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Connection Status Badge
                val badgeColor = when {
                    obdState.isTelemetryPaused -> SafetyAmber
                    obdState.connectionState == ObdConnectionState.STREAMING_LIVE -> SignalEmerald
                    obdState.connectionState == ObdConnectionState.SAFETY_SIMULATION_MODE -> SignalEmerald
                    else -> ElectricCyan
                }
                val badgeLabel = when {
                    obdState.isTelemetryPaused -> "PAUSED (${samples.size} PTS)"
                    obdState.connectionState == ObdConnectionState.STREAMING_LIVE -> "LIVE BLE (1 Hz)"
                    obdState.connectionState == ObdConnectionState.SAFETY_SIMULATION_MODE -> "LIVE STREAM (1 Hz)"
                    else -> "BUFFER READY (${samples.size} PTS)"
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(badgeColor.copy(alpha = 0.15f))
                        .border(1.dp, badgeColor.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .testTag("recharts_obd_stream_status_badge"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(badgeColor)
                    )
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Stream Controls & Vehicle Telemetry Source Bar
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BluetoothConnected,
                                contentDescription = "OBD-II Source",
                                tint = if (isLiveStreaming) SignalEmerald else ElectricCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (obdState.connectedAdapterName.isNotBlank()) {
                                    obdState.connectedAdapterName
                                } else {
                                    "ELM327 CAN-Bus Telemetry (ISO 15765-4 500k)"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Metric / Imperial Toggle
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ElectricCyan.copy(alpha = 0.14f))
                                .border(1.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                .clickable {
                                    val next = if (activeUnits == AutomotiveUnitSystem.METRIC) {
                                        AutomotiveUnitSystem.IMPERIAL
                                    } else {
                                        AutomotiveUnitSystem.METRIC
                                    }
                                    localUnitSystem = next
                                    onUnitSystemChange?.invoke(next)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("recharts_obd_unit_toggle"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = "Toggle Units",
                                tint = ElectricCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (activeUnits == AutomotiveUnitSystem.METRIC) "METRIC (°C / kPa)" else "IMPERIAL (°F / PSI)",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Vehicle Stream Presets + Start / Pause / Clear Controls
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val presets = listOf(
                            0 to "F-150 3.5L EcoBoost",
                            1 to "Camry 2.5L VVT-iE (Lean)",
                            2 to "Silverado 1500 (Low V)"
                        )
                        presets.forEach { (idx, label) ->
                            FilterChip(
                                selected = isLiveStreaming && selectedPresetIdx == idx,
                                onClick = {
                                    selectedPresetIdx = idx
                                    inspectedSampleIndex = null
                                    bleManager.startSafetySimulationMode(idx)
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("recharts_obd_preset_$idx")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                inspectedSampleIndex = null
                                if (!isLiveStreaming) {
                                    bleManager.startSafetySimulationMode(selectedPresetIdx)
                                } else {
                                    bleManager.toggleTelemetryPause()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("recharts_obd_pause_resume_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when {
                                    !isLiveStreaming -> SignalEmerald
                                    obdState.isTelemetryPaused -> SignalEmerald
                                    else -> SafetyAmber
                                }
                            )
                        ) {
                            Icon(
                                imageVector = if (!isLiveStreaming || obdState.isTelemetryPaused) {
                                    Icons.Default.PlayArrow
                                } else {
                                    Icons.Default.Pause
                                },
                                contentDescription = "Start or Pause Live OBD-II Stream",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when {
                                    !isLiveStreaming -> "Start Live OBD-II Stream"
                                    obdState.isTelemetryPaused -> "Resume Live Stream"
                                    else -> "Pause Stream to Inspect"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                inspectedSampleIndex = null
                                bleManager.clearTelemetryHistory()
                            },
                            modifier = Modifier.testTag("recharts_obd_clear_buffer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Reset Chart Buffer",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // 4 Real-Time Engine Parameter KPI Readout Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LiveParameterKpiTile(
                    title = "ENGINE RPM (010C)",
                    primaryValue = "${inspectedSample.engineRpm} RPM",
                    subValue = "Min $minRpm • Peak $peakRpm",
                    accentColor = ElectricCyan,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("recharts_kpi_rpm")
                )
                val formatTemp: (Int) -> String = { tempC ->
                    if (activeUnits == AutomotiveUnitSystem.IMPERIAL) {
                        String.format(
                            Locale.US,
                            "%.1f °F",
                            AutomotiveUnitConverter.celsiusToFahrenheit(tempC.toDouble())
                        )
                    } else {
                        "$tempC °C"
                    }
                }
                LiveParameterKpiTile(
                    title = "COOLANT (0105)",
                    primaryValue = formatTemp(inspectedSample.coolantTempC),
                    subValue = "IAT: ${formatTemp(inspectedSample.intakeAirTempC)} • Peak ${formatTemp(maxCoolantC)}",
                    accentColor = if (isCoolantHot) HazardCrimson else SignalEmerald,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("recharts_kpi_coolant")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LiveParameterKpiTile(
                    title = "STFT BANK 1 (0106)",
                    primaryValue = String.format(Locale.US, "%+.1f%%", inspectedSample.shortTermFuelTrimB1),
                    subValue = "O2 Closed-Loop Trim",
                    accentColor = if (abs(inspectedSample.shortTermFuelTrimB1) > 10.0) SafetyAmber else ElectricCyan,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("recharts_kpi_stft")
                )
                LiveParameterKpiTile(
                    title = "LTFT BANK 1 (0107)",
                    primaryValue = String.format(Locale.US, "%+.1f%%", inspectedSample.longTermFuelTrimB1),
                    subValue = "Total Trim: ${String.format(Locale.US, "%+.1f%%", totalFuelTrim)}",
                    accentColor = if (isFuelTrimOutOfSpec) HazardCrimson else SafetyAmber,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("recharts_kpi_ltft")
                )
            }

            // Inspected Sample Crosshair Readout Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("recharts_crosshair_inspector_bar"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (inspectedSampleIndex != null) {
                        "INSPECTING SAMPLE #${inspectedSample.sampleIndex} (${inspectedSample.timestampLabel})"
                    } else {
                        "LIVE SAMPLE #${inspectedSample.sampleIndex} (${inspectedSample.timestampLabel}) • Tap any chart to inspect"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan,
                    fontFamily = FontFamily.Monospace
                )
                if (inspectedSampleIndex != null) {
                    Text(
                        text = "FOLLOW LIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = SafetyAmber,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { inspectedSampleIndex = null }
                    )
                } else {
                    Text(
                        text = "LOAD ${inspectedSample.engineLoadPercent}% • THR ${inspectedSample.throttlePositionPercent}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

            // =========================================================================
            // RECHARTS 1: Real-Time Engine RPM & Engine Load % Composed Area/Line Chart
            // =========================================================================
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "RPM Chart",
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "1. Live Engine RPM (PID 010C) & Load % (PID 0104)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "<ComposedChart>",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan,
                        fontFamily = FontFamily.Monospace
                    )
                }

                RechartsLiveRpmAndLoadCanvas(
                    samples = samples,
                    selectedIndex = inspectedSampleIndex ?: samples.lastIndex,
                    onSelectSample = { idx -> inspectedSampleIndex = idx },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .testTag("recharts_live_rpm_chart")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ChartLegendDot(color = ElectricCyan, label = "Engine Speed (RPM)")
                    ChartLegendDot(color = SignalEmerald, label = "Calculated Load (%)")
                    ChartLegendDot(color = HazardCrimson, label = "High Load / 3,000 RPM Threshold")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

            // =========================================================================
            // RECHARTS 2: Real-Time Short-Term & Long-Term Fuel Trim % Dual-Trace Chart
            // =========================================================================
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Fuel Trim Chart",
                            tint = SafetyAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "2. Short & Long-Term Fuel Trim % (PID 0106 / 0107)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = if (isFuelTrimOutOfSpec) "LEAN/RICH ALERT (>±15%)" else "STOICH SPEC (±10%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isFuelTrimOutOfSpec) HazardCrimson else SignalEmerald,
                        fontWeight = FontWeight.Bold
                    )
                }

                RechartsLiveFuelTrimCanvas(
                    samples = samples,
                    selectedIndex = inspectedSampleIndex ?: samples.lastIndex,
                    onSelectSample = { idx -> inspectedSampleIndex = idx },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .testTag("recharts_live_fuel_trim_chart")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ChartLegendDot(color = ElectricCyan, label = "STFT Bank 1 (%)")
                    ChartLegendDot(color = SafetyAmber, label = "LTFT Bank 1 (%)")
                    ChartLegendDot(color = SignalEmerald, label = "±10% Normal Band")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

            // =========================================================================
            // RECHARTS 3: Real-Time Coolant Temp & Intake Air Temp Area/Line Chart
            // =========================================================================
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Thermostat,
                            contentDescription = "Coolant Temperature Chart",
                            tint = SignalEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "3. Coolant Temp (PID 0105) & Intake Air (PID 010F)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = if (activeUnits == AutomotiveUnitSystem.METRIC) "°C SCALE" else "°F SCALE",
                        style = MaterialTheme.typography.labelSmall,
                        color = SignalEmerald,
                        fontFamily = FontFamily.Monospace
                    )
                }

                RechartsLiveTemperatureCanvas(
                    samples = samples,
                    unitSystem = activeUnits,
                    selectedIndex = inspectedSampleIndex ?: samples.lastIndex,
                    onSelectSample = { idx -> inspectedSampleIndex = idx },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .testTag("recharts_live_coolant_chart")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ChartLegendDot(color = SignalEmerald, label = "Coolant Temp (ECT)")
                    ChartLegendDot(color = ElectricCyan, label = "Intake Air Temp (IAT)")
                    ChartLegendDot(color = HazardCrimson, label = "Overheat Limit (105°C / 221°F)")
                }
            }
        }
    }
}

@Composable
private fun LiveParameterKpiTile(
    title: String,
    primaryValue: String,
    subValue: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = primaryValue,
            style = MaterialTheme.typography.titleMedium,
            color = accentColor,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = subValue,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun ChartLegendDot(
    color: Color,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Recharts-style ComposedChart for Live Engine RPM (Area + Line) and Engine Load % (Secondary Line).
 */
@Composable
private fun RechartsLiveRpmAndLoadCanvas(
    samples: List<ObdTelemetrySample>,
    selectedIndex: Int,
    onSelectSample: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val surfaceBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(surfaceBg)
            .border(1.dp, ElectricCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(samples.size) {
                    detectTapGestures { offset ->
                        if (samples.size > 1 && size.width > 0) {
                            val ratio = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            val idx = (ratio * (samples.size - 1)).roundToInt().coerceIn(samples.indices)
                            onSelectSample(idx)
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f || samples.isEmpty()) return@Canvas

            val maxRpmScale = 4000f
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)

            // Horizontal CartesianGrid lines (0, 1000, 2000, 3000, 4000 RPM)
            for (i in 0..4) {
                val y = h - (i / 4f) * h
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f,
                    pathEffect = dashEffect
                )
            }

            // 3,000 RPM High-Load ReferenceLine
            val ref3000Y = h - (3000f / maxRpmScale) * h
            drawLine(
                color = HazardCrimson.copy(alpha = 0.55f),
                start = Offset(0f, ref3000Y),
                end = Offset(w, ref3000Y),
                strokeWidth = 1.5f,
                pathEffect = dashEffect
            )

            val stepX = if (samples.size > 1) w / (samples.size - 1).toFloat() else w
            val rpmPath = Path()
            val rpmAreaPath = Path()
            val loadPath = Path()

            samples.forEachIndexed { index, sample ->
                val x = index * stepX
                val rpmY = (h - (sample.engineRpm.coerceIn(0, 4000) / maxRpmScale) * h).coerceIn(2f, h - 2f)
                val loadY = (h - (sample.engineLoadPercent.coerceIn(0, 100) / 100f) * h).coerceIn(2f, h - 2f)

                if (index == 0) {
                    rpmPath.moveTo(x, rpmY)
                    rpmAreaPath.moveTo(x, h)
                    rpmAreaPath.lineTo(x, rpmY)
                    loadPath.moveTo(x, loadY)
                } else {
                    rpmPath.lineTo(x, rpmY)
                    rpmAreaPath.lineTo(x, rpmY)
                    loadPath.lineTo(x, loadY)
                }
            }

            val lastX = (samples.size - 1) * stepX
            rpmAreaPath.lineTo(lastX, h)
            rpmAreaPath.close()

            // Area fill under RPM curve
            drawPath(
                path = rpmAreaPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        ElectricCyan.copy(alpha = 0.38f),
                        ElectricCyan.copy(alpha = 0.03f)
                    )
                )
            )

            // Secondary Engine Load % Line
            drawPath(
                path = loadPath,
                color = SignalEmerald.copy(alpha = 0.85f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // Primary Engine RPM Line
            drawPath(
                path = rpmPath,
                color = ElectricCyan,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            // Data Dots + Active Crosshair
            val safeSel = selectedIndex.coerceIn(samples.indices)
            samples.forEachIndexed { index, sample ->
                val x = index * stepX
                val rpmY = (h - (sample.engineRpm.coerceIn(0, 4000) / maxRpmScale) * h).coerceIn(2f, h - 2f)
                if (index == safeSel) {
                    drawLine(
                        color = ElectricCyan.copy(alpha = 0.7f),
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = 1.5f,
                        pathEffect = dashEffect
                    )
                    drawCircle(
                        color = ElectricCyan,
                        radius = 6.dp.toPx(),
                        center = Offset(x, rpmY)
                    )
                    drawCircle(
                        color = Color.Black,
                        radius = 2.5.dp.toPx(),
                        center = Offset(x, rpmY)
                    )
                } else {
                    drawCircle(
                        color = ElectricCyan.copy(alpha = 0.75f),
                        radius = 2.5.dp.toPx(),
                        center = Offset(x, rpmY)
                    )
                }
            }
        }
    }
}

/**
 * Recharts-style LineChart for Short-Term Fuel Trim (STFT %) and Long-Term Fuel Trim (LTFT %)
 * with a shaded ±10% stoichiometric reference band and 0% baseline.
 */
@Composable
private fun RechartsLiveFuelTrimCanvas(
    samples: List<ObdTelemetrySample>,
    selectedIndex: Int,
    onSelectSample: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val surfaceBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(surfaceBg)
            .border(1.dp, SafetyAmber.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(samples.size) {
                    detectTapGestures { offset ->
                        if (samples.size > 1 && size.width > 0) {
                            val ratio = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            val idx = (ratio * (samples.size - 1)).roundToInt().coerceIn(samples.indices)
                            onSelectSample(idx)
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f || samples.isEmpty()) return@Canvas

            // Range: -25% to +25% (span = 50%)
            fun trimToY(trimPercent: Double): Float {
                val clamped = trimPercent.coerceIn(-25.0, 25.0).toFloat()
                val normalized = (clamped + 25f) / 50f // 0..1
                return (h - normalized * h).coerceIn(2f, h - 2f)
            }

            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)

            // Shaded ±10% Normal OEM Spec ReferenceArea
            val yPlus10 = trimToY(10.0)
            val yMinus10 = trimToY(-10.0)
            drawRoundRect(
                color = SignalEmerald.copy(alpha = 0.10f),
                topLeft = Offset(0f, yPlus10),
                size = Size(w, (yMinus10 - yPlus10).coerceAtLeast(2f)),
                cornerRadius = CornerRadius(4f, 4f)
            )

            // +15% and -15% Lean/Rich Fault Threshold ReferenceLines
            val yPlus15 = trimToY(15.0)
            val yMinus15 = trimToY(-15.0)
            drawLine(
                color = HazardCrimson.copy(alpha = 0.5f),
                start = Offset(0f, yPlus15),
                end = Offset(w, yPlus15),
                strokeWidth = 1.2f,
                pathEffect = dashEffect
            )
            drawLine(
                color = HazardCrimson.copy(alpha = 0.5f),
                start = Offset(0f, yMinus15),
                end = Offset(w, yMinus15),
                strokeWidth = 1.2f,
                pathEffect = dashEffect
            )

            // 0.0% Stoichiometric Center ReferenceLine
            val yZero = trimToY(0.0)
            drawLine(
                color = gridColor.copy(alpha = 0.7f),
                start = Offset(0f, yZero),
                end = Offset(w, yZero),
                strokeWidth = 1.8f
            )

            val stepX = if (samples.size > 1) w / (samples.size - 1).toFloat() else w
            val stftPath = Path()
            val ltftPath = Path()

            samples.forEachIndexed { index, sample ->
                val x = index * stepX
                val stftY = trimToY(sample.shortTermFuelTrimB1)
                val ltftY = trimToY(sample.longTermFuelTrimB1)
                if (index == 0) {
                    stftPath.moveTo(x, stftY)
                    ltftPath.moveTo(x, ltftY)
                } else {
                    stftPath.lineTo(x, stftY)
                    ltftPath.lineTo(x, ltftY)
                }
            }

            // Draw LTFT Line (SafetyAmber)
            drawPath(
                path = ltftPath,
                color = SafetyAmber,
                style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
            )

            // Draw STFT Line (ElectricCyan)
            drawPath(
                path = stftPath,
                color = ElectricCyan,
                style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
            )

            // Highlight selected sample
            val safeSel = selectedIndex.coerceIn(samples.indices)
            val selSample = samples[safeSel]
            val selX = safeSel * stepX
            val selStftY = trimToY(selSample.shortTermFuelTrimB1)
            val selLtftY = trimToY(selSample.longTermFuelTrimB1)

            drawLine(
                color = SafetyAmber.copy(alpha = 0.65f),
                start = Offset(selX, 0f),
                end = Offset(selX, h),
                strokeWidth = 1.5f,
                pathEffect = dashEffect
            )
            drawCircle(
                color = ElectricCyan,
                radius = 5.5.dp.toPx(),
                center = Offset(selX, selStftY)
            )
            drawCircle(
                color = SafetyAmber,
                radius = 5.5.dp.toPx(),
                center = Offset(selX, selLtftY)
            )
        }
    }
}

/**
 * Recharts-style AreaChart for Engine Coolant Temperature (PID 0105) and Intake Air Temperature (PID 010F).
 */
@Composable
private fun RechartsLiveTemperatureCanvas(
    samples: List<ObdTelemetrySample>,
    unitSystem: AutomotiveUnitSystem,
    selectedIndex: Int,
    onSelectSample: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val surfaceBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(surfaceBg)
            .border(1.dp, SignalEmerald.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(samples.size, unitSystem) {
                    detectTapGestures { offset ->
                        if (samples.size > 1 && size.width > 0) {
                            val ratio = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            val idx = (ratio * (samples.size - 1)).roundToInt().coerceIn(samples.indices)
                            onSelectSample(idx)
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f || samples.isEmpty()) return@Canvas

            // Temperature scale in °C: 20°C to 120°C
            fun tempCToY(tempC: Int): Float {
                val clamped = tempC.coerceIn(20, 120).toFloat()
                val normalized = (clamped - 20f) / 100f
                return (h - normalized * h).coerceIn(2f, h - 2f)
            }

            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)

            for (i in 0..4) {
                val y = h - (i / 4f) * h
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f,
                    pathEffect = dashEffect
                )
            }

            // Overheat warning ReferenceLine at 105°C (221°F)
            val yOverheat = tempCToY(105)
            drawLine(
                color = HazardCrimson.copy(alpha = 0.65f),
                start = Offset(0f, yOverheat),
                end = Offset(w, yOverheat),
                strokeWidth = 1.5f,
                pathEffect = dashEffect
            )

            val stepX = if (samples.size > 1) w / (samples.size - 1).toFloat() else w
            val coolantPath = Path()
            val coolantAreaPath = Path()
            val iatPath = Path()

            samples.forEachIndexed { index, sample ->
                val x = index * stepX
                val ectY = tempCToY(sample.coolantTempC)
                val iatY = tempCToY(sample.intakeAirTempC)
                if (index == 0) {
                    coolantPath.moveTo(x, ectY)
                    coolantAreaPath.moveTo(x, h)
                    coolantAreaPath.lineTo(x, ectY)
                    iatPath.moveTo(x, iatY)
                } else {
                    coolantPath.lineTo(x, ectY)
                    coolantAreaPath.lineTo(x, ectY)
                    iatPath.lineTo(x, iatY)
                }
            }

            val lastX = (samples.size - 1) * stepX
            coolantAreaPath.lineTo(lastX, h)
            coolantAreaPath.close()

            drawPath(
                path = coolantAreaPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        SignalEmerald.copy(alpha = 0.34f),
                        SignalEmerald.copy(alpha = 0.02f)
                    )
                )
            )

            drawPath(
                path = iatPath,
                color = ElectricCyan.copy(alpha = 0.85f),
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
            )

            drawPath(
                path = coolantPath,
                color = SignalEmerald,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            val safeSel = selectedIndex.coerceIn(samples.indices)
            val selSample = samples[safeSel]
            val selX = safeSel * stepX
            val selEctY = tempCToY(selSample.coolantTempC)

            drawLine(
                color = SignalEmerald.copy(alpha = 0.65f),
                start = Offset(selX, 0f),
                end = Offset(selX, h),
                strokeWidth = 1.5f,
                pathEffect = dashEffect
            )
            drawCircle(
                color = SignalEmerald,
                radius = 5.5.dp.toPx(),
                center = Offset(selX, selEctY)
            )
        }
    }
}
