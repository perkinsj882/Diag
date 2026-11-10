package com.example.ui.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.EnvironmentPreset
import com.example.data.model.SystemCategory
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HazardCrimson
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SignalEmerald
import com.example.util.ObdBleScannerManager
import com.example.viewmodel.AiTaskState
import java.util.Locale

/**
 * Vehicle systems available in the Guided Diagnostic Workflow screen, explicitly supporting
 * Engine, Drivetrain, Steering & Suspension, Brakes, Electrical, and ECU / CAN-Bus.
 */
enum class GuidedVehicleSystem(
    val id: String,
    val displayTitle: String,
    val badgeCode: String,
    val subtitle: String,
    val mappedCategory: SystemCategory,
    val defaultVehicleModel: String,
    val defaultEnvironment: String,
    val symptomPresets: List<String>
) {
    ENGINE(
        id = "engine",
        displayTitle = "Engine",
        badgeCode = "ENG",
        subtitle = "VVT Timing, Fuel Trim, Turbo Boost, Misfires & Cooling",
        mappedCategory = SystemCategory.MECHANICAL,
        defaultVehicleModel = "2020 Toyota Camry 2.5L A25A / Ford 3.5L EcoBoost",
        defaultEnvironment = EnvironmentPreset.DAILY_STREET_HIGHWAY.label,
        symptomPresets = listOf(
            "Cold start metallic timing rattle for 3s (P0016)",
            "Positive fuel trim > +15% & rough idle (P0171 Lean)",
            "Turbocharger underboost & power loss on grade (P0299)",
            "Random / single-cylinder misfire under load (P0300)",
            "Coolant temp rising above 105°C / thermostat fault"
        )
    ),
    DRIVETRAIN(
        id = "drivetrain",
        displayTitle = "Drivetrain",
        badgeCode = "DRVT",
        subtitle = "Automatic/DCT Transmission, Torque Converter, Transfer Case & Diffs",
        mappedCategory = SystemCategory.MECHANICAL,
        defaultVehicleModel = "2021 Ford F-150 10R80 / GM 8L90 4WD Drivetrain",
        defaultEnvironment = EnvironmentPreset.HEAVY_TOWING_HAULING.label,
        symptomPresets = listOf(
            "Torque Converter Clutch (TCC) shudder at 45–60 MPH (P0741)",
            "Harsh 2-3 or 3-4 upshift flare & delayed shift engagement",
            "Delayed engagement (>2s) shifting from Park into Drive/Reverse",
            "AWD / 4WD transfer case binding or crow-hop on tight turns",
            "Driveline vibration or rear axle whine under highway load"
        )
    ),
    STEERING(
        id = "steering",
        displayTitle = "Steering & Suspension",
        badgeCode = "STRG",
        subtitle = "EPAS Electric Rack & Pinion, Torque Sensor, Tie Rods & Struts",
        mappedCategory = SystemCategory.PNEUMATIC,
        defaultVehicleModel = "2022 Ford Mustang Mach-E / F-150 EPAS Electric Steering Rack",
        defaultEnvironment = EnvironmentPreset.DAILY_STREET_HIGHWAY.label,
        symptomPresets = listOf(
            "'Steering Assist Fault' & heavy steering effort (EPAS C102D)",
            "Steering wheel off-center or vehicle pulls under braking/cruise",
            "Front suspension clunk over bumps (Tie rod / ball joint / strut)",
            "Notchy or binding steering feel returning to center",
            "Speed-sensitive front wheel hub bearing hum (35–65 MPH)"
        )
    ),
    BRAKES(
        id = "brakes",
        displayTitle = "Brakes",
        badgeCode = "BRK",
        subtitle = "Electronic Brake Booster (iBooster), ABS/ESC Modulator & EPB",
        mappedCategory = SystemCategory.HYDRAULIC,
        defaultVehicleModel = "2021 Chevrolet Silverado 1500 Electronic Brake Booster & ABS",
        defaultEnvironment = EnvironmentPreset.HEAVY_TOWING_HAULING.label,
        symptomPresets = listOf(
            "Spongy or hard pedal with Electronic Brake Booster fault (C0299)",
            "ABS & Traction Control warning lights active (C0035 Wheel Speed)",
            "Steering wheel & pedal pulsation under highway braking",
            "Electronic Parking Brake (EPB) actuator stuck or dragging",
            "Brake pedal slowly sinks at stop light (Master cylinder bypass)"
        )
    ),
    ELECTRICAL(
        id = "electrical",
        displayTitle = "Battery & Charging",
        badgeCode = "ELEC",
        subtitle = "12V AGM Battery, Smart Alternator, Starter & Parasitic Draw",
        mappedCategory = SystemCategory.ELECTRICAL,
        defaultVehicleModel = "2019 Honda CR-V 1.5T / 12V AGM Smart Charging System",
        defaultEnvironment = EnvironmentPreset.EXTREME_COLD_WINTER.label,
        symptomPresets = listOf(
            "Battery drains overnight — parasitic draw > 50 mA key-off",
            "Alternator bus voltage < 13.0V with engine running (P0562)",
            "Single starter solenoid click & voltage dip below 9.2V on crank",
            "Headlight flicker & high AC diode ripple (> 80 mV AC)",
            "BMS Battery Management System state-of-charge warning"
        )
    ),
    ECU_CAN(
        id = "ecu_can",
        displayTitle = "ECU & CAN-Bus",
        badgeCode = "ECU",
        subtitle = "ECM/PCM, TCM, BCM, 5V Reference (VREF) & CAN Network",
        mappedCategory = SystemCategory.COMPUTER_CONTROLLER,
        defaultVehicleModel = "2021 Ford F-150 3.5L EcoBoost PCM & High-Speed CAN-Bus",
        defaultEnvironment = EnvironmentPreset.AUTOMOTIVE_SERVICE_BAY.label,
        symptomPresets = listOf(
            "Lost communication with ECM/PCM on High-Speed CAN (U0100)",
            "5V Sensor Reference A pulled below 4.8V (P06A6 VREF short)",
            "Wrench limp-home mode with throttle body correlation fault",
            "CAN-H to CAN-L termination resistance out of 60 Ω spec",
            "Intermittent multi-module dash warning sweep when hot"
        )
    );

    fun icon(): ImageVector = when (this) {
        ENGINE -> Icons.Default.Build
        DRIVETRAIN -> Icons.Default.Settings
        STEERING -> Icons.Default.DirectionsCar
        BRAKES -> Icons.Default.Album
        ELECTRICAL -> Icons.Default.BatteryChargingFull
        ECU_CAN -> Icons.Default.Memory
    }

    fun accentColor(): Color = when (this) {
        ENGINE -> SafetyAmber
        DRIVETRAIN -> ElectricCyan
        STEERING -> SignalEmerald
        BRAKES -> HazardCrimson
        ELECTRICAL -> SafetyAmber
        ECU_CAN -> ElectricCyan
    }
}

private val DRIVING_CONDITION_TAGS = listOf(
    "At Cold Start",
    "At Hot Idle / Stop Light",
    "Under Load / Acceleration",
    "Highway Cruise (45–70 MPH)",
    "During Turning / Braking"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GuidedDiagnosticWorkflowPanel(
    aiTaskState: AiTaskState,
    onSelectSystemCategoryFilter: (SystemCategory?, String?) -> Unit,
    onGenerateGuidedAiPath: (
        equipmentModel: String,
        systemCategory: SystemCategory,
        environmentType: String,
        synthesizedFailurePrompt: String
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bleManager = remember(context) { ObdBleScannerManager.getInstance(context) }
    val obdState by bleManager.uiState.collectAsStateWithLifecycle()

    var selectedSystem by remember { mutableStateOf(GuidedVehicleSystem.ENGINE) }
    val selectedSymptoms = remember {
        mutableStateListOf(GuidedVehicleSystem.ENGINE.symptomPresets.first())
    }
    var selectedCondition by remember { mutableStateOf(DRIVING_CONDITION_TAGS[2]) }
    var vehicleModelInput by remember { mutableStateOf(GuidedVehicleSystem.ENGINE.defaultVehicleModel) }
    var customSymptomNotes by remember { mutableStateOf("") }
    var attachedObdSnapshot by remember { mutableStateOf<String?>(null) }

    val systemAccent = selectedSystem.accentColor()

    // Synthesize the complete symptom + condition + live OBD-II prompt
    val synthesizedSymptomPrompt = remember(
        selectedSystem,
        selectedSymptoms.toList(),
        selectedCondition,
        customSymptomNotes,
        attachedObdSnapshot
    ) {
        buildString {
            append("[${selectedSystem.displayTitle} Subsystem] ")
            if (selectedSymptoms.isNotEmpty()) {
                append("Symptoms: ")
                append(selectedSymptoms.joinToString("; "))
                append(". ")
            }
            append("Condition: $selectedCondition. ")
            if (customSymptomNotes.isNotBlank()) {
                append("Technician Notes / Codes: ${customSymptomNotes.trim()}. ")
            }
            if (!attachedObdSnapshot.isNullOrBlank()) {
                append("Live OBD-II Telemetry: $attachedObdSnapshot")
            }
        }.trim()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, systemAccent.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
            .testTag("guided_diagnostic_workflow_panel"),
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
            // Header
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
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(systemAccent.copy(alpha = 0.18f))
                            .border(1.dp, systemAccent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = selectedSystem.icon(),
                            contentDescription = selectedSystem.displayTitle,
                            tint = systemAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "GUIDED DIAGNOSTIC WORKFLOW • AI PATH BUILDER",
                            style = MaterialTheme.typography.labelMedium,
                            color = systemAccent,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Select Vehicle System & Symptom Inputs",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // =========================================================================
            // STEP 1: Select Vehicle System (Engine, Drivetrain, Steering, Brakes, etc.)
            // =========================================================================
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "STEP 1 • SELECT VEHICLE SYSTEM",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan,
                    fontWeight = FontWeight.Bold
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("guided_workflow_system_selector")
                ) {
                    GuidedVehicleSystem.entries.forEach { sys ->
                        val isSelected = selectedSystem == sys
                        val accent = sys.accentColor()
                        Card(
                            modifier = Modifier
                                .border(
                                    width = if (isSelected) 1.5.dp else 0.8.dp,
                                    color = if (isSelected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    selectedSystem = sys
                                    vehicleModelInput = sys.defaultVehicleModel
                                    selectedSymptoms.clear()
                                    selectedSymptoms.add(sys.symptomPresets.first())
                                    onSelectSystemCategoryFilter(sys.mappedCategory, sys.id)
                                }
                                .testTag("guided_system_chip_${sys.id}"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    accent.copy(alpha = 0.15f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = sys.icon(),
                                    contentDescription = sys.displayTitle,
                                    tint = if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "${sys.badgeCode} • ${sys.displayTitle}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) accent else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "${selectedSystem.displayTitle} Coverage: ${selectedSystem.subtitle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

            // =========================================================================
            // STEP 2: Select Specific Symptom Inputs & Operating Conditions
            // =========================================================================
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "STEP 2 • SELECT ${selectedSystem.displayTitle.uppercase()} SYMPTOM INPUTS",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${selectedSymptoms.size} selected",
                        style = MaterialTheme.typography.labelSmall,
                        color = systemAccent
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("guided_workflow_symptom_selector")
                ) {
                    selectedSystem.symptomPresets.forEachIndexed { idx, symptom ->
                        val isChecked = selectedSymptoms.contains(symptom)
                        FilterChip(
                            selected = isChecked,
                            onClick = {
                                if (isChecked) {
                                    if (selectedSymptoms.size > 1) {
                                        selectedSymptoms.remove(symptom)
                                    }
                                } else {
                                    selectedSymptoms.add(symptom)
                                }
                            },
                            label = {
                                Text(
                                    text = symptom,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            leadingIcon = if (isChecked) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected symptom",
                                        tint = systemAccent,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            } else null,
                            modifier = Modifier.testTag("guided_symptom_chip_$idx")
                        )
                    }
                }

                Text(
                    text = "When does the symptom occur?",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DRIVING_CONDITION_TAGS.forEachIndexed { idx, cond ->
                        FilterChip(
                            selected = selectedCondition == cond,
                            onClick = { selectedCondition = cond },
                            label = { Text(cond, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("guided_condition_chip_$idx")
                        )
                    }
                }

                // Live OBD-II Telemetry Context Attachment Strip
                val sensors = obdState.liveSensors
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                        .border(
                            1.dp,
                            if (attachedObdSnapshot != null) SignalEmerald else ElectricCyan.copy(alpha = 0.4f),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BluetoothConnected,
                                contentDescription = "Live OBD-II PIDs",
                                tint = if (attachedObdSnapshot != null) SignalEmerald else ElectricCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (attachedObdSnapshot != null) {
                                    "OBD-II TELEMETRY ATTACHED TO PATH"
                                } else {
                                    "LIVE OBD-II SNAPSHOT AVAILABLE"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (attachedObdSnapshot != null) SignalEmerald else ElectricCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "${sensors.engineRpm} RPM • Coolant ${sensors.coolantTempC}°C • STFT ${
                                String.format(Locale.US, "%+.1f%%", sensors.shortTermFuelTrimB1)
                            } • LTFT ${
                                String.format(Locale.US, "%+.1f%%", sensors.longTermFuelTrimB1)
                            }",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            if (attachedObdSnapshot != null) {
                                attachedObdSnapshot = null
                            } else {
                                attachedObdSnapshot = sensors.toTelemetrySummaryString(
                                    dtcs = obdState.activeDtcs,
                                    adapterName = obdState.connectedAdapterName.ifBlank { "ELM327 BLE" },
                                    isSimulation = obdState.isSafetySimulationActive
                                )
                            }
                        },
                        modifier = Modifier.testTag("guided_workflow_import_obd_button")
                    ) {
                        Text(
                            text = if (attachedObdSnapshot != null) "Detach OBD" else "Attach OBD-II",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                OutlinedTextField(
                    value = vehicleModelInput,
                    onValueChange = { vehicleModelInput = it },
                    label = { Text("Target Vehicle / Engine / Transmission Model") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("guided_workflow_vehicle_input")
                )

                OutlinedTextField(
                    value = customSymptomNotes,
                    onValueChange = { customSymptomNotes = it },
                    label = { Text("Additional Symptom Details / Specific OBD-II Codes (Optional)") },
                    minLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("guided_workflow_custom_symptom_input")
                )

                HandsFreeSpeechRecognizerCard(
                    onTextRecognized = { spokenText ->
                        customSymptomNotes = if (customSymptomNotes.isBlank()) {
                            spokenText
                        } else {
                            "${customSymptomNotes.trimEnd()}. $spokenText"
                        }
                    },
                    title = "Dictate Symptom Input Hands-Free",
                    hintText = "Speak symptom details, shift feel, or DTC codes to include in your AI troubleshooting path.",
                    testTagPrefix = "guided_workflow_speech"
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

            // =========================================================================
            // STEP 3: Launch AI-Assisted Troubleshooting Path
            // =========================================================================
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "STEP 3 • FOLLOW AI-ASSISTED TROUBLESHOOTING PATH",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Synthesized Path Query: $synthesizedSymptomPrompt",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            onGenerateGuidedAiPath(
                                vehicleModelInput,
                                selectedSystem.mappedCategory,
                                selectedSystem.defaultEnvironment,
                                synthesizedSymptomPrompt
                            )
                        },
                        enabled = aiTaskState !is AiTaskState.Running,
                        modifier = Modifier
                            .weight(1.35f)
                            .testTag("guided_workflow_generate_ai_path_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = systemAccent,
                            contentColor = Color.Black
                        )
                    ) {
                        if (aiTaskState is AiTaskState.Running) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Building AI Path...",
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Start AI Troubleshooting Path",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Start AI Troubleshooting Path",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            onSelectSystemCategoryFilter(selectedSystem.mappedCategory, selectedSystem.id)
                        },
                        modifier = Modifier
                            .weight(0.85f)
                            .testTag("guided_workflow_quick_load_system_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountTree,
                            contentDescription = "Load Pre-Built System Path",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Load ${selectedSystem.badgeCode} Tree")
                    }
                }
            }
        }
    }
}
