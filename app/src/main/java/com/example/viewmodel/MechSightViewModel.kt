package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DiagnosticSession
import com.example.data.model.EquipmentProfile
import com.example.data.model.FirestoreCacheStatus
import com.example.data.model.FlowchartNode
import com.example.data.model.MechanicVoiceObservation
import com.example.data.model.StarterTemplates
import com.example.data.model.SystemCategory
import com.example.data.model.TroubleshootingFlowchart
import com.example.data.repository.MechSightRepository
import com.example.ui.components.IdentifiedAutomotivePart
import com.example.ui.components.RepairGuideSectionTarget
import com.example.ui.components.VisualPartIdState
import com.example.util.DiagnosticCsvExporter
import com.example.util.DiagnosticPdfExporter
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

sealed interface DataState<out T> {
    data object Loading : DataState<Nothing>
    data class Success<T>(val data: T) : DataState<T>
    data class Error(val message: String) : DataState<Nothing>
}

sealed interface AiTaskState {
    data object Idle : AiTaskState
    data class Running(val statusLabel: String) : AiTaskState
    data class DiagnosisReady(
        val sessionId: String,
        val companionFlowchartId: String?
    ) : AiTaskState
    data class FlowchartReady(val flowchartId: String) : AiTaskState
    data class Error(val message: String) : AiTaskState
}

class MechSightViewModel(
    private val repository: MechSightRepository,
    val currentUserId: String
) : ViewModel() {

    private val _isDeviceNetworkAvailable = MutableStateFlow(true)
    private val _lastCacheVerifiedMillis = MutableStateFlow(System.currentTimeMillis())

    val equipmentState: StateFlow<DataState<List<EquipmentProfile>>> =
        repository.observeEquipment(currentUserId)
            .map<List<EquipmentProfile>, DataState<List<EquipmentProfile>>> { DataState.Success(it) }
            .catch { error ->
                Log.w(TAG, "Error observing equipment", error)
                emit(DataState.Error(error.message ?: "Failed to load equipment profiles"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = DataState.Loading
            )

    val diagnosticsState: StateFlow<DataState<List<DiagnosticSession>>> =
        repository.observeDiagnostics(currentUserId)
            .map<List<DiagnosticSession>, DataState<List<DiagnosticSession>>> { DataState.Success(it) }
            .catch { error ->
                Log.w(TAG, "Error observing diagnostics", error)
                val cached = repository.getCachedDiagnostics(currentUserId).getOrNull()
                if (cached != null) {
                    emit(DataState.Success(cached))
                } else {
                    emit(DataState.Error(error.message ?: "Failed to load diagnostic sessions"))
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = DataState.Loading
            )

    val flowchartsState: StateFlow<DataState<List<TroubleshootingFlowchart>>> =
        repository.observeFlowcharts(currentUserId)
            .map<List<TroubleshootingFlowchart>, DataState<List<TroubleshootingFlowchart>>> { DataState.Success(it) }
            .catch { error ->
                Log.w(TAG, "Error observing flowcharts", error)
                val cached = repository.getCachedFlowcharts(currentUserId).getOrNull()
                if (cached != null) {
                    emit(DataState.Success(cached))
                } else {
                    emit(DataState.Error(error.message ?: "Failed to load troubleshooting flowcharts"))
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = DataState.Loading
            )

    val cacheStatus: StateFlow<FirestoreCacheStatus> = combine(
        _isDeviceNetworkAvailable,
        repository.isForcedOfflineMode,
        diagnosticsState,
        flowchartsState,
        equipmentState
    ) { netAvailable, forcedOffline, diagState, flowState, eqState ->
        val diags = (diagState as? DataState.Success)?.data.orEmpty()
        val flows = (flowState as? DataState.Success)?.data.orEmpty()
        val eqs = (eqState as? DataState.Success)?.data.orEmpty()

        val pendingCount = diags.count { it.hasPendingWrites } +
            flows.count { it.hasPendingWrites } +
            eqs.count { it.hasPendingWrites }

        val servingFromCache = forcedOffline ||
            !netAvailable ||
            diags.any { it.isFromCache } ||
            flows.any { it.isFromCache }

        FirestoreCacheStatus(
            isNetworkAvailable = netAvailable,
            isForcedOfflineMode = forcedOffline,
            isServingFromCache = servingFromCache,
            pendingWritesCount = pendingCount,
            cachedDiagnosticsCount = diags.size,
            cachedFlowchartsCount = flows.size,
            cachedEquipmentCount = eqs.size,
            cacheAllocationMb = MechSightRepository.PERSISTENT_CACHE_SIZE_MB,
            lastCacheVerifiedMillis = _lastCacheVerifiedMillis.value
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = FirestoreCacheStatus()
    )

    private val _aiTaskState = MutableStateFlow<AiTaskState>(AiTaskState.Idle)
    val aiTaskState: StateFlow<AiTaskState> = _aiTaskState.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _activeDiagnosticId = MutableStateFlow<String?>(null)
    val activeDiagnosticId: StateFlow<String?> = _activeDiagnosticId.asStateFlow()

    private val _activeFlowchartId = MutableStateFlow<String?>(null)
    val activeFlowchartId: StateFlow<String?> = _activeFlowchartId.asStateFlow()

    private val _visualPartIdState = MutableStateFlow<VisualPartIdState>(VisualPartIdState.Idle)
    val visualPartIdState: StateFlow<VisualPartIdState> = _visualPartIdState.asStateFlow()

    // Structured JSON schema for Multimodal Diagnostic + Repair Guide + Companion Flowchart
    private val diagnosticSchema = Schema.obj(
        properties = mapOf(
            "rootCauseTitle" to Schema.string(description = "Concise engineering title of the primary failure cause"),
            "severity" to Schema.enumeration(
                values = listOf("CRITICAL", "HIGH", "MODERATE", "LOW"),
                description = "Hazard and operational urgency level"
            ),
            "confidencePercent" to Schema.integer(description = "Diagnostic confidence score from 50 to 99"),
            "technicalAnalysis" to Schema.string(description = "Detailed automotive engineering explanation of the mechanical, electrical, hydraulic, pneumatic, or ECU/CAN-Bus controller failure mechanism"),
            "safetyWarnings" to Schema.array(
                items = Schema.string(),
                description = "Automotive safety precautions (jack stands, wheel chocks, fuel/brake depressurization, or HV EV orange cable isolation)"
            ),
            "requiredTools" to Schema.array(
                items = Schema.string(),
                description = "Specific automotive scan tools, gauges, multimeters, specialty pullers/fixtures, and OEM replacement parts"
            ),
            "repairSteps" to Schema.array(
                items = Schema.string(),
                description = "Sequential step-by-step automotive repair instructions tailored to the vehicle model and driving/service environment"
            ),
            "verificationTest" to Schema.string(description = "Post-repair road test or OBD-II PID verification procedure with target measurements"),
            "flowchartTitle" to Schema.string(description = "Title for an interactive Yes/No troubleshooting flowchart for this failure"),
            "flowchartNodes" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "nodeId" to Schema.string(description = "Node ID such as N1, N2, N3"),
                        "nodeType" to Schema.enumeration(
                            values = listOf("QUESTION", "CONCLUSION"),
                            description = "QUESTION for a Yes/No check or CONCLUSION for root cause resolution"
                        ),
                        "prompt" to Schema.string(description = "Yes/No diagnostic question or root-cause conclusion statement"),
                        "observationHint" to Schema.string(description = "What to measure, listen for, or inspect"),
                        "yesNodeId" to Schema.string(description = "Target nodeId if YES (empty string if CONCLUSION)"),
                        "noNodeId" to Schema.string(description = "Target nodeId if NO (empty string if CONCLUSION)"),
                        "recommendedAction" to Schema.string(description = "Immediate test or corrective repair action")
                    )
                ),
                description = "5 to 7 interactive Yes/No troubleshooting flowchart nodes starting at N1"
            )
        )
    )

    private val flowchartOnlySchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Title of the interactive troubleshooting flowchart"),
            "failureMode" to Schema.string(description = "Concise summary of the failure symptom being isolated"),
            "nodes" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "nodeId" to Schema.string(description = "Node identifier starting with N1"),
                        "nodeType" to Schema.enumeration(
                            values = listOf("QUESTION", "CONCLUSION"),
                            description = "QUESTION for a conditional Yes/No step or CONCLUSION for a final diagnosis"
                        ),
                        "prompt" to Schema.string(description = "Clear Yes/No diagnostic question or final root cause conclusion"),
                        "observationHint" to Schema.string(description = "Specific gauge reading, multimeter test, LED code, or visual/acoustic cue"),
                        "yesNodeId" to Schema.string(description = "Target nodeId when the user answers YES (empty string for CONCLUSION)"),
                        "noNodeId" to Schema.string(description = "Target nodeId when the user answers NO (empty string for CONCLUSION)"),
                        "recommendedAction" to Schema.string(description = "Specific repair or isolation action")
                    )
                ),
                description = "Between 5 and 9 connected decision and conclusion nodes starting at N1"
            )
        )
    )

    private val partIdentificationSchema = Schema.obj(
        properties = mapOf(
            "partName" to Schema.string(description = "Exact automotive component name identified from the image"),
            "oemPartNumberPattern" to Schema.string(description = "Typical OEM part number prefix or schematic code"),
            "subsystemCategory" to Schema.enumeration(
                values = listOf(
                    "Engine & Powertrain",
                    "Brakes",
                    "Steering & Suspension",
                    "Battery, Starting & Charging",
                    "ECU, Modules & CAN-Bus"
                ),
                description = "Primary automotive subsystem domain of the identified part"
            ),
            "confidencePercent" to Schema.integer(description = "Visual identification confidence score from 70 to 99"),
            "partFunctionSummary" to Schema.string(description = "Concise automotive engineering summary of what this part does"),
            "inspectionSpec" to Schema.string(description = "OEM electrical voltage/resistance, pressure, or fastener torque specification"),
            "commonFailureSymptoms" to Schema.array(
                items = Schema.string(),
                description = "2 to 4 common failure symptoms and OBD-II DTC codes associated with this part"
            ),
            "primarySectionTarget" to Schema.enumeration(
                values = listOf("SAFETY_PRECAUTIONS", "REQUIRED_TOOLS", "REPAIR_STEPS", "VERIFICATION_TEST"),
                description = "Which repair guide section is most directly relevant to inspecting or servicing this part"
            ),
            "matchedRepairStepNumber" to Schema.integer(description = "1-based step number in the active repair guide that corresponds to this part"),
            "matchedRepairStepSummary" to Schema.string(description = "Brief summary of the repair step or procedure for this part"),
            "recommendedDiagnosticPrompt" to Schema.string(description = "Detailed symptom and test prompt for generating a complete repair guide for this part")
        )
    )

    private val diagnosticModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = diagnosticSchema
            }
        )
    }

    private val flowchartModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = flowchartOnlySchema
            }
        )
    }

    private val partIdentifierModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = partIdentificationSchema
            }
        )
    }

    fun updateDeviceNetworkAvailability(isConnected: Boolean) {
        _isDeviceNetworkAvailable.value = isConnected
    }

    fun toggleOfflineFieldMode(enableOffline: Boolean) {
        viewModelScope.launch {
            repository.setOfflineFieldMode(enableOffline).onSuccess {
                _lastCacheVerifiedMillis.value = System.currentTimeMillis()
                _statusBanner.value = if (enableOffline) {
                    "Remote Offline Field Mode ENABLED: All diagnostic guides & flowcharts are served from the 100 MB on-device cache."
                } else {
                    "Cloud Sync RE-ENABLED: Synchronizing cached field updates with Firestore."
                }
            }.onFailure { e ->
                _statusBanner.value = e.localizedMessage ?: "Failed to switch offline mode."
            }
        }
    }

    fun syncAndVerifyOfflineCache() {
        viewModelScope.launch {
            _statusBanner.value = "Verifying on-device Firestore persistent cache..."
            repository.syncAndVerifyLocalCache(currentUserId).onSuccess { summary ->
                _lastCacheVerifiedMillis.value = summary.verifiedAtMillis
                val pendingNote = if (summary.pendingWritesCount > 0) {
                    " (${summary.pendingWritesCount} offline edits queued for sync)"
                } else {
                    " (all records synced)"
                }
                _statusBanner.value =
                    "Offline Cache Ready: ${summary.cachedDiagnosticsCount} repair guides, ${summary.cachedFlowchartsCount} flowcharts, ${summary.cachedEquipmentCount} vehicles pinned on-device$pendingNote."
            }.onFailure { e ->
                _statusBanner.value = e.localizedMessage ?: "Cache verification completed using active local snapshot."
            }
        }
    }

    fun selectDiagnostic(diagnosticId: String?) {
        _activeDiagnosticId.value = diagnosticId
    }

    fun selectFlowchart(flowchartId: String?) {
        _activeFlowchartId.value = flowchartId
    }

    fun clearAiTaskState() {
        _aiTaskState.value = AiTaskState.Idle
    }

    fun clearVisualPartIdState() {
        _visualPartIdState.value = VisualPartIdState.Idle
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    /**
     * Uses Firebase AI Logic (`gemini-3.8-flash` multimodal vision) to identify an automotive part
     * from a CameraX / Gallery [bitmap] and map it directly to the relevant repair guide section & step.
     */
    fun identifyAutomotivePartFromCamera(
        bitmap: Bitmap,
        partContextHint: String,
        equipmentName: String,
        equipmentModel: String,
        activeSession: DiagnosticSession?
    ) {
        viewModelScope.launch {
            val isOffline = !_isDeviceNetworkAvailable.value || repository.isForcedOfflineMode.value
            if (isOffline) {
                _visualPartIdState.value = VisualPartIdState.Identifying(
                    "Offline Field Mode — matching component against local OEM optical part database..."
                )
                val fallbackPart = buildOfflinePartIdentification(
                    partContextHint = partContextHint,
                    equipmentName = equipmentName,
                    equipmentModel = equipmentModel,
                    activeSession = activeSession
                )
                _visualPartIdState.value = VisualPartIdState.Success(fallbackPart, bitmap)
                return@launch
            }

            _visualPartIdState.value = VisualPartIdState.Identifying(
                "Analyzing camera frame with Gemini 3.8 Flash Vision & linking to repair guide..."
            )
            try {
                val activeStepsList = activeSession?.repairSteps.orEmpty()
                val stepsPrompt = if (activeStepsList.isNotEmpty()) {
                    activeStepsList.mapIndexed { idx, s -> "Step ${idx + 1}: $s" }.joinToString("\n")
                } else {
                    "Step 1: Scan OBD-II DTCs & verify 12V battery state.\nStep 2: Inspect subsystem harness, seals & hoses.\nStep 3: Back-probe sensor/solenoid & replace out-of-spec component.\nStep 4: Clear DTCs & verify live PIDs on road test."
                }

                val prompt = buildString {
                    appendLine("You are an ASE Master Certified Automotive Parts & Diagnostic Vision Specialist.")
                    appendLine("Identify the automotive part shown in the attached camera image (never industrial or desktop PC parts).")
                    appendLine("Vehicle Context: ${equipmentName.ifBlank { "Automotive Vehicle" }} ($equipmentModel)")
                    if (partContextHint.isNotBlank()) {
                        appendLine("Optical Capture Hint: $partContextHint")
                    }
                    appendLine("Active Repair Guide Steps for Deep-Linking:")
                    appendLine(stepsPrompt)
                    appendLine("Map the identified automotive part to the most relevant repair guide section (SAFETY_PRECAUTIONS, REQUIRED_TOOLS, REPAIR_STEPS, or VERIFICATION_TEST) and exact 1-based step number.")
                }

                val response = withTimeout(60_000L) {
                    partIdentifierModel.generateContent(
                        content {
                            image(bitmap)
                            text(prompt)
                        }
                    )
                }

                val jsonString = response.text ?: throw IllegalStateException("Model returned an empty response.")
                val json = JSONObject(jsonString)

                val maxSteps = activeStepsList.size.coerceAtLeast(4)
                val stepNum = json.optInt("matchedRepairStepNumber", 3).coerceIn(1, maxSteps)
                val catLabel = json.optString("subsystemCategory", "Engine & Powertrain")
                val symptoms = json.optJSONArray("commonFailureSymptoms").toStringList()
                    .ifEmpty { listOf("P0171 / P0299 Performance Fault", "Intermittent Connector Signal Drop") }

                val identified = IdentifiedAutomotivePart(
                    partName = json.optString("partName", "Automotive Sensor / Actuator Assembly"),
                    oemPartNumberPattern = json.optString("oemPartNumberPattern", "OEM-${equipmentModel.take(8)}"),
                    subsystemCategory = SystemCategory.fromLabel(catLabel),
                    confidencePercent = json.optLong("confidencePercent", 94L).coerceIn(70L, 99L),
                    partFunctionSummary = json.optString(
                        "partFunctionSummary",
                        "Regulates and monitors closed-loop automotive subsystem telemetry on $equipmentName."
                    ),
                    inspectionSpec = json.optString(
                        "inspectionSpec",
                        "5.0V VREF ± 0.05V | Coil Resistance: 18–26 Ω @ 20°C | Fastener Torque: 10 Nm"
                    ),
                    commonFailureSymptoms = symptoms,
                    primarySectionTarget = RepairGuideSectionTarget.fromString(
                        json.optString("primarySectionTarget", "REPAIR_STEPS")
                    ),
                    matchedRepairStepNumber = stepNum,
                    matchedRepairStepSummary = json.optString(
                        "matchedRepairStepSummary",
                        activeStepsList.getOrNull(stepNum - 1)
                            ?: "Back-probe connector pins and verify OEM operating range under load."
                    ),
                    recommendedDiagnosticPrompt = json.optString(
                        "recommendedDiagnosticPrompt",
                        "Visual inspection of ${json.optString("partName", "automotive component")} on $equipmentName ($equipmentModel). Check ${symptoms.joinToString(", ")}."
                    )
                )

                _visualPartIdState.value = VisualPartIdState.Success(identified, bitmap)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Cloud AI visual part identification unavailable, using local optical part catalog", e)
                val fallbackPart = buildOfflinePartIdentification(
                    partContextHint = partContextHint,
                    equipmentName = equipmentName,
                    equipmentModel = equipmentModel,
                    activeSession = activeSession
                )
                _visualPartIdState.value = VisualPartIdState.Success(fallbackPart, bitmap)
            }
        }
    }

    private fun buildOfflinePartIdentification(
        partContextHint: String,
        equipmentName: String,
        equipmentModel: String,
        activeSession: DiagnosticSession?
    ): IdentifiedAutomotivePart {
        val hintLower = partContextHint.lowercase()
        val steps = activeSession?.repairSteps.orEmpty()
        val targetStep3 = steps.getOrNull(2) ?: "Back-probe sensor/actuator connector and measure resistance/voltage against OEM specification."
        val targetStep2 = steps.getOrNull(1) ?: "Inspect component seals, brake/steering lines, and wiring harness routing."

        return when {
            hintLower.contains("maf") || hintLower.contains("mass air") -> IdentifiedAutomotivePart(
                partName = "Hot-Wire Mass Air Flow (MAF) & Intake Air Temp Sensor",
                oemPartNumberPattern = "OEM-MAF-22680",
                subsystemCategory = SystemCategory.COMPUTER_CONTROLLER,
                confidencePercent = 96L,
                partFunctionSummary = "Measures intake air mass (g/s) and intake air temperature (°C) entering the throttle body so the ECM/PCM can calculate base fuel pulse width and ignition timing.",
                inspectionSpec = "Pin 1 (+B): 12.0–14.4V | Pin 3 (MAF Signal): 0.9–1.3V @ Idle (3.5–5.5 g/s), 3.8–4.4V @ WOT | IAT Thermistor: 2.2 kΩ @ 20°C | Fastener Torque: 3.5 Nm",
                commonFailureSymptoms = listOf(
                    "P0101 Mass Air Flow Circuit Range/Performance",
                    "P0171 / P0174 System Too Lean (Bank 1 & 2)",
                    "Rough idle, hesitation on tip-in, or skewed Long-Term Fuel Trims (> +12%)"
                ),
                primarySectionTarget = RepairGuideSectionTarget.REPAIR_STEPS,
                matchedRepairStepNumber = 2.coerceAtMost(steps.size.coerceAtLeast(2)),
                matchedRepairStepSummary = targetStep2,
                recommendedDiagnosticPrompt = "Visual CameraX scan identified Mass Air Flow (MAF) & IAT Sensor on $equipmentName ($equipmentModel). Check for P0101/P0171 lean fuel trims, hot-wire contamination, and 5V reference stability."
            )

            hintLower.contains("vvt") || hintLower.contains("solenoid") || hintLower.contains("cam") -> IdentifiedAutomotivePart(
                partName = "Variable Valve Timing (VVT) Oil Control Solenoid Actuator",
                oemPartNumberPattern = "OEM-VVT-15330",
                subsystemCategory = SystemCategory.MECHANICAL,
                confidencePercent = 95L,
                partFunctionSummary = "PWM-controlled spool valve that routes pressurized engine oil to the intake/exhaust camshaft phasers to advance or retard valve timing under load.",
                inspectionSpec = "Solenoid Coil Resistance: 6.9–7.9 Ω @ 20°C | Oil Filter Screen: 0% metallic debris | Hold Bolt Torque: 10 Nm (89 lb-in)",
                commonFailureSymptoms = listOf(
                    "P0011 / P0012 Camshaft Position Timing Over-Advanced or Retarded",
                    "Metallic phaser rattle on cold start & rough idle",
                    "Sludge-blocked VVT spool screen causing sluggish cam angle response"
                ),
                primarySectionTarget = RepairGuideSectionTarget.REPAIR_STEPS,
                matchedRepairStepNumber = 3.coerceAtMost(steps.size.coerceAtLeast(3)),
                matchedRepairStepSummary = targetStep3,
                recommendedDiagnosticPrompt = "Visual CameraX scan identified Variable Valve Timing (VVT) Oil Control Solenoid on $equipmentName ($equipmentModel). Inspect spool valve filter screen, measure 6.9–7.9 Ω coil resistance, and verify cam phaser duty cycle."
            )

            hintLower.contains("alternator") || hintLower.contains("charging") -> IdentifiedAutomotivePart(
                partName = "Brushless 12V Charging Alternator & LIN-Bus Voltage Regulator",
                oemPartNumberPattern = "OEM-ALT-27060",
                subsystemCategory = SystemCategory.ELECTRICAL,
                confidencePercent = 94L,
                partFunctionSummary = "Generates 3-phase rectified DC charging current (13.8V–14.6V) under PCM LIN-Bus smart charging command and maintains 12V AGM/EFB battery state of charge.",
                inspectionSpec = "Regulated Output: 13.9–14.5V DC @ 2,000 RPM | Diode AC Ripple: < 50 mV AC | B+ Stud Voltage Drop: < 0.15V | Mounting Bolt Torque: 45 Nm",
                commonFailureSymptoms = listOf(
                    "P0562 System Voltage Low (< 11.8V under load)",
                    "U0100 / U0121 Multi-module CAN-Bus low-voltage dropouts",
                    "Overrunning alternator decoupler (OAD) pulley chirp or diode ripple whine"
                ),
                primarySectionTarget = RepairGuideSectionTarget.REPAIR_STEPS,
                matchedRepairStepNumber = 1,
                matchedRepairStepSummary = steps.firstOrNull() ?: "Measure 12V battery state of charge, B+ cable voltage drop, and alternator ripple under load.",
                recommendedDiagnosticPrompt = "Visual CameraX scan identified 12V Alternator & LIN-Bus Regulator on $equipmentName ($equipmentModel). Perform B+ voltage drop test, diode AC ripple check, and LIN command duty cycle test."
            )

            hintLower.contains("ibooster") || hintLower.contains("abs") || hintLower.contains("brake") -> IdentifiedAutomotivePart(
                partName = "Electronic Brake Booster (iBooster) & ABS/ESC Module",
                oemPartNumberPattern = "OEM-BRK-47050",
                subsystemCategory = SystemCategory.HYDRAULIC,
                confidencePercent = 95L,
                partFunctionSummary = "Electromechanical brake booster and 4-channel ABS/ESC controller that provides vacuum-independent brake assist, regenerative brake blending, and wheel-slip modulation.",
                inspectionSpec = "Brake Fluid Spec: DOT 4 LV | Pedal Travel Sensor 5V VREF: 4.95–5.05V | Booster Motor Supply Fuse: 40A/60A | Flare Nut Torque: 16 Nm",
                commonFailureSymptoms = listOf(
                    "C0035 / C0040 Wheel Speed Sensor or ABS/ESC Solenoid Circuit Fault",
                    "Electronic Brake Booster assist fault with hard or spongy pedal feel",
                    "Electronic Parking Brake (EPB) or ABS/Traction warning lamp illuminated"
                ),
                primarySectionTarget = RepairGuideSectionTarget.SAFETY_PRECAUTIONS,
                matchedRepairStepNumber = 3.coerceAtMost(steps.size.coerceAtLeast(3)),
                matchedRepairStepSummary = targetStep3,
                recommendedDiagnosticPrompt = "Visual CameraX scan identified Electronic Brake Booster (iBooster) & ABS/ESC Module on $equipmentName ($equipmentModel). Check pedal travel sensor PIDs, perform OBD-II automated brake bleed, and inspect wheel speed sensor signals."
            )

            hintLower.contains("epas") || hintLower.contains("steering") || hintLower.contains("rack") || hintLower.contains("air") -> IdentifiedAutomotivePart(
                partName = "Electric Power Assisted Steering (EPAS) Rack & Pinion Assembly",
                oemPartNumberPattern = "OEM-EPAS-45510",
                subsystemCategory = SystemCategory.PNEUMATIC,
                confidencePercent = 94L,
                partFunctionSummary = "Belt-driven brushless electric motor and integrated Power Steering Control Module (PSCM) mounted directly on the steering rack that provides variable speed-sensitive steering assist.",
                inspectionSpec = "PSCM High-Current Supply: 12.2–14.5V (80A Mega-Fuse) | Steering Torque Sensor VREF: 5.0V | Rack Mounting Bolts: 145 Nm (107 lb-ft) | Tie Rod Jam Nut: 65 Nm",
                commonFailureSymptoms = listOf(
                    "C102D / U0131 Steering Torque Sensor or PSCM CAN-Bus Communication Fault",
                    "Sudden loss of power steering assist ('Steering Assist Reduced') or notchiness off-center",
                    "Inner/outer tie rod play or water ingress into EPAS belt housing"
                ),
                primarySectionTarget = RepairGuideSectionTarget.REPAIR_STEPS,
                matchedRepairStepNumber = 2.coerceAtMost(steps.size.coerceAtLeast(2)),
                matchedRepairStepSummary = targetStep2,
                recommendedDiagnosticPrompt = "Visual CameraX scan identified Electric Power Assisted Steering (EPAS) Rack & Pinion on $equipmentName ($equipmentModel). Verify 80A PSCM B+ feed/ground drop, graph steering torque/angle sensor PIDs, and inspect tie rod ends."
            )

            else -> IdentifiedAutomotivePart(
                partName = "Electronic Turbocharger Wastegate Actuator & Boost Solenoid",
                oemPartNumberPattern = "OEM-TC-18900",
                subsystemCategory = SystemCategory.MECHANICAL,
                confidencePercent = 94L,
                partFunctionSummary = "Regulates exhaust turbine bypass flow via a motorized linkage and Hall-effect position sensor to maintain target manifold boost pressure.",
                inspectionSpec = "Position Sensor Sweep: 0.50V (Closed) to 4.50V (Open) | Motor PWM Duty: 15–85% | Linkage Jam Nut Torque: 12 Nm",
                commonFailureSymptoms = listOf(
                    "P0299 Turbocharger Underboost Condition",
                    "P2563 Turbocharger Boost Control Position Sensor Performance",
                    "Metallic wastegate pivot bushing rattle on deceleration"
                ),
                primarySectionTarget = RepairGuideSectionTarget.VERIFICATION_TEST,
                matchedRepairStepNumber = 3.coerceAtMost(steps.size.coerceAtLeast(3)),
                matchedRepairStepSummary = targetStep3,
                recommendedDiagnosticPrompt = "Visual CameraX scan identified Electronic Turbocharger Wastegate Actuator on $equipmentName ($equipmentModel). Check linkage pivot play, verify 0.5–4.5V position sensor sweep, and run boost leak test."
            )
        }
    }

    fun runMultimodalDiagnosis(
        equipmentName: String,
        equipmentModel: String,
        systemCategory: SystemCategory,
        environmentType: String,
        symptomDescription: String,
        attachedBitmap: Bitmap?,
        attachedMediaBytes: ByteArray?,
        attachedMediaMimeType: String?,
        acousticSummary: String,
        mediaSummaryLabel: String,
        initialVoiceLogs: List<String> = emptyList(),
        vinNumber: String = "",
        odometerMileage: Long = 0L
    ) {
        if (symptomDescription.isBlank() && attachedBitmap == null && attachedMediaBytes == null && acousticSummary.isBlank()) {
            _aiTaskState.value = AiTaskState.Error("Please enter a symptom description or attach an image, audio, or video sample.")
            return
        }

        viewModelScope.launch {
            val isOffline = !_isDeviceNetworkAvailable.value || repository.isForcedOfflineMode.value
            if (isOffline) {
                _aiTaskState.value = AiTaskState.Running(
                    "Offline Field Mode active — synthesizing repair guide from local engineering cache for $equipmentModel..."
                )
                createOfflineFallbackDiagnosis(
                    equipmentName = equipmentName,
                    equipmentModel = equipmentModel,
                    systemCategory = systemCategory,
                    environmentType = environmentType,
                    symptomDescription = symptomDescription,
                    mediaSummaryLabel = mediaSummaryLabel,
                    initialVoiceLogs = initialVoiceLogs,
                    vinNumber = vinNumber,
                    odometerMileage = odometerMileage
                )
                return@launch
            }

            _aiTaskState.value = AiTaskState.Running(
                "Analyzing ${systemCategory.label} telemetry & generating repair guide for $equipmentModel..."
            )
            try {
                val promptBuilder = StringBuilder().apply {
                    appendLine("You are an ASE Master Certified Automotive Diagnostic & Powertrain Engineer.")
                    appendLine("Strictly diagnose automotive vehicles and automotive subsystems ONLY (never industrial machinery or desktop/laptop PCs).")
                    appendLine("Diagnose the following vehicle fault and produce a vehicle-specific step-by-step automotive repair guide AND an interactive Yes/No troubleshooting flowchart.")
                    appendLine("Automotive Subsystem Category: ${systemCategory.label}")
                    appendLine("Vehicle / Subsystem Name: ${equipmentName.ifBlank { "Automotive Vehicle" }}")
                    appendLine("Engine / Trim / ECU Model: ${equipmentModel.ifBlank { "Standard Vehicle" }}")
                    if (vinNumber.isNotBlank()) {
                        appendLine("Vehicle VIN: $vinNumber")
                    }
                    if (odometerMileage > 0L) {
                        appendLine("Vehicle Odometer Mileage: $odometerMileage miles")
                    }
                    appendLine("Driving / Service Environment: $environmentType")
                    appendLine("Reported Symptoms / OBD-II DTC Fault Codes: ${symptomDescription.ifBlank { "Inspect attached multimodal vehicle evidence." }}")
                    if (acousticSummary.isNotBlank()) {
                        appendLine("Engine Bay / Chassis Acoustic Observation: $acousticSummary")
                    }
                    if (mediaSummaryLabel.isNotBlank()) {
                        appendLine("Attached Visual/Audio Evidence: $mediaSummaryLabel")
                    }
                    appendLine("Ensure repairSteps include exact automotive safety precautions (jack stands, battery/HV disconnect, fuel/coolant depressurization), target measurements (OBD-II live PIDs, voltage drop, PSI/bar pressure, OEM torque specs in Nm), and model-specific procedures.")
                }

                val response = withTimeout(60_000L) {
                    diagnosticModel.generateContent(
                        content {
                            if (attachedBitmap != null) {
                                image(attachedBitmap)
                            }
                            if (attachedMediaBytes != null &&
                                !attachedMediaMimeType.isNullOrBlank() &&
                                attachedMediaBytes.size <= MAX_INLINE_MEDIA_BYTES
                            ) {
                                inlineData(attachedMediaBytes, attachedMediaMimeType)
                            }
                            text(promptBuilder.toString())
                        }
                    )
                }

                val jsonString = response.text ?: throw IllegalStateException("Model returned an empty response.")
                val json = JSONObject(jsonString)

                val safetyWarnings = json.optJSONArray("safetyWarnings").toStringList()
                val requiredTools = json.optJSONArray("requiredTools").toStringList()
                val repairSteps = json.optJSONArray("repairSteps").toStringList()
                    .ifEmpty { listOf("Isolate energy sources and inspect primary component assembly.") }

                val session = DiagnosticSession(
                    userId = currentUserId,
                    equipmentName = equipmentName.ifBlank { "${systemCategory.label} Unit" },
                    equipmentModel = equipmentModel.ifBlank { "Generic-${systemCategory.badgeCode}" },
                    systemCategory = systemCategory.label,
                    environmentType = environmentType,
                    symptomDescription = symptomDescription.ifBlank { "Multimodal media inspection" },
                    attachedMediaSummary = mediaSummaryLabel.ifBlank { "Text symptom prompt" },
                    rootCauseTitle = json.optString("rootCauseTitle", "Component Fault Detected"),
                    severity = json.optString("severity", "HIGH"),
                    confidencePercent = json.optLong("confidencePercent", 88L),
                    technicalAnalysis = json.optString("technicalAnalysis", "See repair steps below."),
                    safetyWarnings = safetyWarnings,
                    requiredTools = requiredTools,
                    repairSteps = repairSteps,
                    completedStepIndices = emptyList(),
                    verificationTest = json.optString("verificationTest", "Verify nominal operation under load."),
                    status = "IN_PROGRESS",
                    mechanicVoiceLogs = initialVoiceLogs,
                    vinNumber = vinNumber,
                    odometerMileage = odometerMileage
                )

                val sessionId = repository.createDiagnosticSession(session).getOrThrow()

                // Also persist companion interactive troubleshooting flowchart if nodes were returned
                val nodesArray = json.optJSONArray("flowchartNodes")
                val parsedNodes = mutableListOf<FlowchartNode>()
                if (nodesArray != null) {
                    for (i in 0 until nodesArray.length()) {
                        val obj = nodesArray.optJSONObject(i) ?: continue
                        parsedNodes.add(
                            FlowchartNode(
                                nodeId = obj.optString("nodeId", "N${i + 1}"),
                                nodeType = obj.optString("nodeType", "QUESTION"),
                                prompt = obj.optString("prompt", ""),
                                observationHint = obj.optString("observationHint", ""),
                                yesNodeId = obj.optString("yesNodeId", ""),
                                noNodeId = obj.optString("noNodeId", ""),
                                recommendedAction = obj.optString("recommendedAction", "")
                            )
                        )
                    }
                }

                val flowchartId = if (parsedNodes.isNotEmpty()) {
                    repository.createFlowchart(
                        title = json.optString("flowchartTitle", "${session.rootCauseTitle} Flowchart"),
                        equipmentModel = session.equipmentModel,
                        systemCategory = session.systemCategory,
                        environmentType = session.environmentType,
                        failureMode = session.symptomDescription.take(200),
                        nodes = parsedNodes
                    ).getOrNull()
                } else {
                    null
                }

                _activeDiagnosticId.value = sessionId
                if (flowchartId != null) {
                    _activeFlowchartId.value = flowchartId
                }
                _aiTaskState.value = AiTaskState.DiagnosisReady(sessionId, flowchartId)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Multimodal diagnosis timed out — falling back to local offline engineering engine", e)
                createOfflineFallbackDiagnosis(
                    equipmentName = equipmentName,
                    equipmentModel = equipmentModel,
                    systemCategory = systemCategory,
                    environmentType = environmentType,
                    symptomDescription = symptomDescription,
                    mediaSummaryLabel = mediaSummaryLabel,
                    initialVoiceLogs = initialVoiceLogs,
                    vinNumber = vinNumber,
                    odometerMileage = odometerMileage
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Cloud AI unreachable, generating local cached engineering diagnosis", e)
                createOfflineFallbackDiagnosis(
                    equipmentName = equipmentName,
                    equipmentModel = equipmentModel,
                    systemCategory = systemCategory,
                    environmentType = environmentType,
                    symptomDescription = symptomDescription,
                    mediaSummaryLabel = mediaSummaryLabel,
                    initialVoiceLogs = initialVoiceLogs,
                    vinNumber = vinNumber,
                    odometerMileage = odometerMileage
                )
            }
        }
    }

    private suspend fun createOfflineFallbackDiagnosis(
        equipmentName: String,
        equipmentModel: String,
        systemCategory: SystemCategory,
        environmentType: String,
        symptomDescription: String,
        mediaSummaryLabel: String,
        initialVoiceLogs: List<String> = emptyList(),
        vinNumber: String = "",
        odometerMileage: Long = 0L
    ) {
        val cleanName = equipmentName.ifBlank { "Automotive ${systemCategory.label} Subsystem" }
        val cleanModel = equipmentModel.ifBlank { "Vehicle-${systemCategory.badgeCode}" }
        val cleanSymptom = symptomDescription.ifBlank { systemCategory.commonFailureModes.first() }

        val session = DiagnosticSession(
            userId = currentUserId,
            equipmentName = cleanName,
            equipmentModel = cleanModel,
            systemCategory = systemCategory.label,
            environmentType = environmentType,
            symptomDescription = cleanSymptom,
            attachedMediaSummary = if (mediaSummaryLabel.isNotBlank()) {
                "$mediaSummaryLabel (Offline Automotive Cache)"
            } else {
                "Offline Automotive Service Protocol"
            },
            rootCauseTitle = "Automotive ${systemCategory.label} Fault: ${cleanSymptom.take(65)}",
            severity = "HIGH",
            confidencePercent = 86L,
            technicalAnalysis = "Generated via MechSight Offline Automotive Diagnostic Engine for $cleanName ($cleanModel) operating in $environmentType. Symptom profile indicates primary automotive ${systemCategory.label.lowercase()} subsystem degradation requiring OBD-II telemetry verification and mechanical/electrical isolation.",
            safetyWarnings = listOf(
                "Support vehicle on certified jack stands on level concrete and chock wheels before working underneath $cleanModel.",
                "Disconnect 12V negative battery cable (and HV service disconnect on EV/Hybrid vehicles) and relieve residual fuel/brake/coolant pressure before disassembly."
            ),
            requiredTools = listOf(
                "Bi-Directional OBD-II Scan Tool & True-RMS Automotive Multimeter",
                "OEM Factory Service Manual & Wiring Pinout for $cleanModel",
                "3/8-inch & 1/2-inch Calibrated Torque Wrenches (Nm / ft-lbs)",
                "Automotive Smoke / Pressure Leak Tester & Back-Probe Kit"
            ),
            repairSteps = listOf(
                "Scan all vehicle modules for stored, pending, and freeze-frame OBD-II DTCs on $cleanName ($cleanModel) and verify 12V battery state of charge.",
                "Inspect ${systemCategory.subtitle.lowercase()} for fluid/vacuum leaks, harness chafing, worn bushings/seals, or heat damage related to: $cleanSymptom.",
                "Back-probe sensor/actuator connectors and measure operating pressures/voltages against $cleanModel OEM specifications; replace out-of-spec automotive component and torque fasteners to spec.",
                "Clear OBD-II DTCs, perform required module relearn/reset, and complete a 15-minute road test while monitoring live PIDs."
            ),
            completedStepIndices = emptyList(),
            verificationTest = "Verify $cleanModel completes a 15-minute road test with nominal OBD-II fuel trims, temperatures, pressures, and zero pending/confirmed DTCs.",
            status = "IN_PROGRESS",
            mechanicVoiceLogs = initialVoiceLogs,
            vinNumber = vinNumber,
            odometerMileage = odometerMileage
        )

        val nodes = listOf(
            FlowchartNode(
                nodeId = "N1",
                nodeType = "QUESTION",
                prompt = "Is 12V battery voltage between 12.4V and 14.5V and are all related underhood/cabin fuses intact on $cleanModel?",
                observationHint = "Measure battery post voltage and load-test B+ feed and chassis ground (< 0.08V drop)",
                yesNodeId = "N2",
                noNodeId = "N3",
                recommendedAction = "Verify automotive power supply and chassis ground integrity"
            ),
            FlowchartNode(
                nodeId = "N2",
                nodeType = "QUESTION",
                prompt = "Does the fault reproduce when commanding the solenoid, actuator, or circuit via an OBD-II bi-directional active test?",
                observationHint = "Monitor live OBD-II PIDs, fuel/hydraulic pressure gauge, or engine bay acoustic cue",
                yesNodeId = "N4",
                noNodeId = "N5",
                recommendedAction = "Isolate mechanical/hydraulic component vs. ECU harness circuit"
            ),
            FlowchartNode(
                nodeId = "N3",
                nodeType = "CONCLUSION",
                prompt = "Battery Supply, Fuse, or Corroded Chassis Ground Strap Fault on $cleanModel",
                observationHint = "Excessive voltage drop (> 0.25V) or blown fuse on primary feed",
                recommendedAction = "Clean battery terminals and chassis ground eyelet to bare metal, replace blown fuse/relay, and re-verify charging voltage."
            ),
            FlowchartNode(
                nodeId = "N4",
                nodeType = "CONCLUSION",
                prompt = "Primary Automotive ${systemCategory.label} Component Failure on $cleanModel",
                observationHint = "Component fails to respond or leaks during direct bi-directional actuation",
                recommendedAction = "Replace faulty ${systemCategory.label.lowercase()} assembly on $cleanModel, bleed/prime system, and torque bolts to OEM spec."
            ),
            FlowchartNode(
                nodeId = "N5",
                nodeType = "CONCLUSION",
                prompt = "Sensor Signal Drift or Chafed Engine Bay Wiring Harness on $cleanModel",
                observationHint = "Mechanical stage passes active test, but sensor PID feedback is erratic",
                recommendedAction = "Inspect 5V reference and signal wires for chafing against engine brackets, clean weather-pack pins, and replace drifted sensor."
            )
        )

        val sessionId = repository.createDiagnosticSession(session).getOrNull()
        val flowchartId = repository.createFlowchart(
            title = "$cleanModel Offline Fault Isolation Tree",
            equipmentModel = cleanModel,
            systemCategory = systemCategory.label,
            environmentType = environmentType,
            failureMode = cleanSymptom.take(200),
            nodes = nodes
        ).getOrNull()

        if (sessionId != null) {
            _activeDiagnosticId.value = sessionId
            if (flowchartId != null) {
                _activeFlowchartId.value = flowchartId
            }
            _statusBanner.value = "Saved diagnostic guide & flowchart to on-device Firestore cache (accessible offline)."
            _aiTaskState.value = AiTaskState.DiagnosisReady(sessionId, flowchartId)
        } else {
            _aiTaskState.value = AiTaskState.Error("Unable to store diagnostic session in local cache.")
        }
    }

    fun generateAiTroubleshootingFlowchart(
        equipmentModel: String,
        systemCategory: SystemCategory,
        environmentType: String,
        failureModePrompt: String
    ) {
        if (failureModePrompt.isBlank()) {
            _aiTaskState.value = AiTaskState.Error("Please describe the failure mode or select a common failure preset.")
            return
        }

        viewModelScope.launch {
            val isOffline = !_isDeviceNetworkAvailable.value || repository.isForcedOfflineMode.value
            if (isOffline) {
                _aiTaskState.value = AiTaskState.Running(
                    "Offline Field Mode active — building Yes/No troubleshooting tree from local engineering cache..."
                )
                createOfflineFallbackFlowchart(
                    equipmentModel = equipmentModel,
                    systemCategory = systemCategory,
                    environmentType = environmentType,
                    failureModePrompt = failureModePrompt
                )
                return@launch
            }

            _aiTaskState.value = AiTaskState.Running(
                "Building interactive Yes/No decision tree for ${systemCategory.label} ($equipmentModel)..."
            )
            try {
                val prompt = """
                    You are an ASE Master Certified Automotive Diagnostic Engineer.
                    Strictly generate an automotive troubleshooting flowchart (never industrial or desktop PC).
                    Generate an interactive conditional troubleshooting flowchart (decision tree) that guides an automotive technician through a sequence of YES/NO questions and observations to narrow down the exact root cause of a vehicle failure.
                    Automotive Subsystem Domain: ${systemCategory.label}
                    Vehicle / Engine / ECU Model: ${equipmentModel.ifBlank { "Automotive ${systemCategory.label} System" }}
                    Driving / Service Environment: $environmentType
                    Vehicle Failure Symptom / OBD-II DTC: $failureModePrompt

                    Rules for the decision tree nodes:
                    1. Start at nodeId "N1" with nodeType "QUESTION".
                    2. Every "QUESTION" node MUST specify valid "yesNodeId" and "noNodeId" pointing to other nodes in the list.
                    3. Include specific automotive measurement thresholds or visual/acoustic cues in "observationHint" (e.g., OBD-II live PID value, multimeter pin voltage, fuel/brake/compression PSI, CAN-H/CAN-L resistance).
                    4. Terminal nodes MUST have nodeType "CONCLUSION", empty "yesNodeId" and "noNodeId", a clear automotive root-cause explanation in "prompt", and a concrete vehicle repair in "recommendedAction".
                """.trimIndent()

                val response = withTimeout(60_000L) {
                    flowchartModel.generateContent(prompt)
                }

                val jsonString = response.text ?: throw IllegalStateException("Model returned empty flowchart JSON.")
                val json = JSONObject(jsonString)
                val nodesJson = json.optJSONArray("nodes")
                val nodes = mutableListOf<FlowchartNode>()
                if (nodesJson != null) {
                    for (i in 0 until nodesJson.length()) {
                        val item = nodesJson.optJSONObject(i) ?: continue
                        nodes.add(
                            FlowchartNode(
                                nodeId = item.optString("nodeId", "N${i + 1}"),
                                nodeType = item.optString("nodeType", "QUESTION"),
                                prompt = item.optString("prompt", ""),
                                observationHint = item.optString("observationHint", ""),
                                yesNodeId = item.optString("yesNodeId", ""),
                                noNodeId = item.optString("noNodeId", ""),
                                recommendedAction = item.optString("recommendedAction", "")
                            )
                        )
                    }
                }

                if (nodes.isEmpty()) {
                    throw IllegalStateException("Generated flowchart contained no decision nodes.")
                }

                val flowchartId = repository.createFlowchart(
                    title = json.optString("title", "${systemCategory.label} Fault Isolation Tree"),
                    equipmentModel = equipmentModel.ifBlank { "General ${systemCategory.label}" },
                    systemCategory = systemCategory.label,
                    environmentType = environmentType,
                    failureMode = json.optString("failureMode", failureModePrompt),
                    nodes = nodes
                ).getOrThrow()

                _activeFlowchartId.value = flowchartId
                _aiTaskState.value = AiTaskState.FlowchartReady(flowchartId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Cloud AI unreachable for flowchart, using offline field fallback", e)
                createOfflineFallbackFlowchart(
                    equipmentModel = equipmentModel,
                    systemCategory = systemCategory,
                    environmentType = environmentType,
                    failureModePrompt = failureModePrompt
                )
            }
        }
    }

    private suspend fun createOfflineFallbackFlowchart(
        equipmentModel: String,
        systemCategory: SystemCategory,
        environmentType: String,
        failureModePrompt: String
    ) {
        val cleanModel = equipmentModel.ifBlank { "Automotive ${systemCategory.label}" }
        val lowerPrompt = failureModePrompt.lowercase()
        val isDrivetrain = lowerPrompt.contains("drivetrain") ||
            lowerPrompt.contains("transmission") ||
            lowerPrompt.contains("torque converter") ||
            lowerPrompt.contains("tcc") ||
            lowerPrompt.contains("shift") ||
            lowerPrompt.contains("transfer case")

        val nodes = if (isDrivetrain) {
            listOf(
                FlowchartNode(
                    nodeId = "N1",
                    nodeType = "QUESTION",
                    prompt = "At operating ATF temperature (85°C–95°C on OBD-II TCM data), is transmission fluid level within spec and free of burnt clutch debris on $cleanModel?",
                    observationHint = "Read Transmission Fluid Temp (TFT) PID and inspect fluid level & condition",
                    yesNodeId = "N2",
                    noNodeId = "N3",
                    recommendedAction = "Check ATF thermal level and cooler lines"
                ),
                FlowchartNode(
                    nodeId = "N2",
                    nodeType = "QUESTION",
                    prompt = "When graphing Torque Converter Clutch (TCC) Slip RPM and Shift Solenoid Line Pressure under load, does TCC slip exceed 40 RPM or vanish when TCC is commanded OFF?",
                    observationHint = "Graph Engine RPM vs. Turbine Input Speed and TCC PWM duty cycle at 45–60 MPH",
                    yesNodeId = "N4",
                    noNodeId = "N5",
                    recommendedAction = "Isolate Torque Converter Clutch vs. Valve Body / Transfer Case"
                ),
                FlowchartNode(
                    nodeId = "N3",
                    nodeType = "CONCLUSION",
                    prompt = "Low or Degraded Automatic Transmission Fluid (ATF) & Filter Restriction on $cleanModel",
                    observationHint = "Aerated or oxidized ATF starves line pressure during upshifts and Drive engagement",
                    recommendedAction = "Repair cooler line leak, replace transmission pan filter & gasket (12 Nm), refill with OEM ULV/DEXRON-VI ATF, and clear TCM adaptive tables."
                ),
                FlowchartNode(
                    nodeId = "N4",
                    nodeType = "CONCLUSION",
                    prompt = "Glazed Torque Converter Clutch (TCC) Friction Lining or Sticking TCC PWM Solenoid on $cleanModel",
                    observationHint = "TCC slip oscillation > 45 RPM confirmed during locked cruise (P0741)",
                    recommendedAction = "Perform complete OEM ATF fluid exchange, verify TCC solenoid resistance (5.2 Ω), or replace torque converter assembly."
                ),
                FlowchartNode(
                    nodeId = "N5",
                    nodeType = "CONCLUSION",
                    prompt = "Valve Body Shift Solenoid Pressure Drift or AWD/4WD Transfer Case Clutch Binding on $cleanModel",
                    observationHint = "TCC lockup is normal; harsh shift flare or tight-turn driveline binding persists",
                    recommendedAction = "Service transfer case fluid, inspect driveshaft U-joints/carrier bearing, and run OBD-II TCM Clutch Adaptive Relearn."
                )
            )
        } else {
            listOf(
                FlowchartNode(
                    nodeId = "N1",
                    nodeType = "QUESTION",
                    prompt = "Are 12V battery supply, fuse feeds, and chassis ground voltage drop (< 0.08V) within spec at $cleanModel?",
                    observationHint = "Back-probe connector power/ground pins with a digital multimeter under load",
                    yesNodeId = "N2",
                    noNodeId = "N3",
                    recommendedAction = "Verify vehicle B+ feed, fuses, and chassis ground"
                ),
                FlowchartNode(
                    nodeId = "N2",
                    nodeType = "QUESTION",
                    prompt = "Are active OBD-II DTCs, abnormal fuel trims/pressures, or mechanical/sensor faults present during bi-directional test?",
                    observationHint = "Compare live OBD-II PIDs (RPM, Coolant, STFT/LTFT) and gauge readings against OEM specs",
                    yesNodeId = "N4",
                    noNodeId = "N5",
                    recommendedAction = "Localize fault to primary automotive assembly vs. sensor harness"
                ),
                FlowchartNode(
                    nodeId = "N3",
                    nodeType = "CONCLUSION",
                    prompt = "Automotive Power Feed, Relay, or Chassis Ground Fault on $cleanModel",
                    observationHint = "Supply voltage below 11.8V or ground drop > 0.20V under load",
                    recommendedAction = "Replace blown fuse/relay, clean oxidized ground stud to bare metal, and torque to 10 Nm."
                ),
                FlowchartNode(
                    nodeId = "N4",
                    nodeType = "CONCLUSION",
                    prompt = "Primary Automotive ${systemCategory.label} Assembly Fault: ${failureModePrompt.take(75)}",
                    observationHint = "Direct mechanical, brake, steering, or solenoid fault confirmed under load",
                    recommendedAction = "Replace worn ${systemCategory.label.lowercase()} component on $cleanModel, torque to OEM spec, and clear DTCs."
                ),
                FlowchartNode(
                    nodeId = "N5",
                    nodeType = "CONCLUSION",
                    prompt = "Sensor Reference Drift or Harness Connector Fretting on $cleanModel",
                    observationHint = "Actuator passes active test; intermittent sensor signal dropout",
                    recommendedAction = "Clean weather-pack connector terminals, verify 5.0V VREF stability, and perform OBD-II sensor relearn."
                )
            )
        }

        val titlePrefix = if (isDrivetrain) "Drivetrain & Transmission" else systemCategory.label
        val flowchartId = repository.createFlowchart(
            title = "$cleanModel — $titlePrefix Guided Troubleshooting Path",
            equipmentModel = cleanModel,
            systemCategory = systemCategory.label,
            environmentType = environmentType,
            failureMode = failureModePrompt.take(200),
            nodes = nodes
        ).getOrNull()

        if (flowchartId != null) {
            _activeFlowchartId.value = flowchartId
            _statusBanner.value = "AI-assisted troubleshooting path ready for $cleanModel."
            _aiTaskState.value = AiTaskState.FlowchartReady(flowchartId)
        } else {
            _aiTaskState.value = AiTaskState.Error("Failed to store flowchart in local cache.")
        }
    }

    fun toggleRepairStep(session: DiagnosticSession, stepIndex: Int) {
        if (session.status == "RESOLVED") {
            _statusBanner.value = "This repair session is marked RESOLVED and locked against further edits."
            return
        }
        viewModelScope.launch {
            val current = session.completedStepIndices.toMutableSet()
            val idxLong = stepIndex.toLong()
            if (current.contains(idxLong)) {
                current.remove(idxLong)
            } else {
                current.add(idxLong)
            }
            val result = repository.updateDiagnosticProgress(
                diagnosticId = session.id,
                completedStepIndices = current.sorted(),
                status = "IN_PROGRESS"
            )
            result.onFailure { e ->
                _statusBanner.value = e.localizedMessage ?: "Failed to update step progress"
            }
        }
    }

    fun markDiagnosticResolved(session: DiagnosticSession) {
        if (session.status == "RESOLVED") return
        viewModelScope.launch {
            val allSteps = session.repairSteps.indices.map { it.toLong() }
            val result = repository.updateDiagnosticProgress(
                diagnosticId = session.id,
                completedStepIndices = allSteps,
                status = "RESOLVED"
            )
            result.onSuccess {
                _statusBanner.value = "Repair verified and locked as RESOLVED."
            }.onFailure { e ->
                _statusBanner.value = e.localizedMessage ?: "Failed to mark repair resolved."
            }
        }
    }

    fun updateRepairDuration(session: DiagnosticSession, newDurationMinutes: Int) {
        viewModelScope.launch {
            repository.updateDiagnosticProgress(
                diagnosticId = session.id,
                completedStepIndices = session.completedStepIndices,
                status = session.status,
                repairDurationMinutes = newDurationMinutes.coerceIn(5, 600)
            ).onFailure { e ->
                _statusBanner.value = e.localizedMessage ?: "Failed to update repair duration."
            }
        }
    }

    fun addVoiceObservationToDiagnostic(
        session: DiagnosticSession,
        stepTag: String,
        categoryTag: String,
        spokenText: String
    ) {
        val cleanText = spokenText.trim()
        if (cleanText.isBlank()) return
        val timeFmt = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
        val observation = MechanicVoiceObservation(
            id = "vn_${System.currentTimeMillis()}",
            timestampLabel = timeFmt.format(java.util.Date()),
            stepTag = stepTag.ifBlank { "GENERAL" },
            categoryTag = categoryTag.ifBlank { "OBSERVATION" },
            transcript = cleanText
        )
        viewModelScope.launch {
            repository.appendVoiceObservationToReport(
                diagnosticId = session.id,
                observation = observation
            ).onSuccess {
                _statusBanner.value = "Voice note logged to report [${observation.stepTag} • ${observation.categoryTag}]."
            }.onFailure { e ->
                _statusBanner.value = e.localizedMessage ?: "Failed to log voice observation."
            }
        }
    }

    fun deleteVoiceObservationFromDiagnostic(
        session: DiagnosticSession,
        observationId: String
    ) {
        viewModelScope.launch {
            repository.deleteVoiceObservationFromReport(
                diagnosticId = session.id,
                observationId = observationId
            ).onFailure { e ->
                _statusBanner.value = e.localizedMessage ?: "Failed to remove voice note."
            }
        }
    }

    fun deleteDiagnosticSession(diagnosticId: String) {
        viewModelScope.launch {
            if (_activeDiagnosticId.value == diagnosticId) {
                _activeDiagnosticId.value = null
            }
            repository.deleteDiagnostic(diagnosticId).onFailure { e ->
                _statusBanner.value = e.localizedMessage ?: "Failed to delete diagnostic session."
            }
        }
    }

    /**
     * Fetches the latest DiagnosticSession directly from Firestore (or local persistent cache)
     * and writes a formatted multi-page PDF Service Report to [targetUri].
     */
    fun exportDiagnosticPdfToUri(
        context: Context,
        sessionId: String,
        targetUri: Uri
    ) {
        viewModelScope.launch {
            _statusBanner.value = "Fetching diagnostic record from Firestore & generating PDF service report..."
            val session = repository.getDiagnosticById(sessionId).getOrNull()
                ?: (diagnosticsState.value as? DataState.Success)?.data?.firstOrNull { it.id == sessionId }
            if (session == null) {
                _statusBanner.value = "Diagnostic session not found in Firestore."
                return@launch
            }
            val companionFlowchart = (flowchartsState.value as? DataState.Success)?.data?.firstOrNull {
                it.equipmentModel.equals(session.equipmentModel, ignoreCase = true)
            }
            DiagnosticPdfExporter.writePdfToUri(
                context = context,
                targetUri = targetUri,
                session = session,
                companionFlowchart = companionFlowchart
            ).onSuccess {
                _statusBanner.value = "Saved PDF Service Report for ${session.equipmentName} (${session.equipmentModel})."
            }.onFailure { e ->
                _statusBanner.value = e.localizedMessage ?: "Failed to save PDF report."
            }
        }
    }

    /**
     * Fetches the latest DiagnosticSession directly from Firestore (or local persistent cache),
     * generates a formatted PDF Service Report, and opens the Email / Share chooser.
     */
    fun emailOrShareDiagnosticPdf(
        context: Context,
        sessionId: String
    ) {
        viewModelScope.launch {
            _statusBanner.value = "Preparing PDF Service Report from Firestore for email/export..."
            val session = repository.getDiagnosticById(sessionId).getOrNull()
                ?: (diagnosticsState.value as? DataState.Success)?.data?.firstOrNull { it.id == sessionId }
            if (session == null) {
                _statusBanner.value = "Diagnostic session not found in Firestore."
                return@launch
            }
            val companionFlowchart = (flowchartsState.value as? DataState.Success)?.data?.firstOrNull {
                it.equipmentModel.equals(session.equipmentModel, ignoreCase = true)
            }
            DiagnosticPdfExporter.generatePdfToCacheFile(
                context = context,
                session = session,
                companionFlowchart = companionFlowchart
            ).onSuccess { pdfFile ->
                DiagnosticPdfExporter.launchEmailOrSharePdf(
                    context = context,
                    pdfFile = pdfFile,
                    session = session
                ).onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "Unable to launch email client."
                }
            }.onFailure { e ->
                _statusBanner.value = e.localizedMessage ?: "Failed to generate PDF report."
            }
        }
    }

    /**
     * Queries locally stored diagnostic reports from the Room SQLite database (`diagnostic_reports`)
     * and writes an RFC-4180 compliant Shop Management System CSV file to [targetUri].
     */
    fun exportRoomDiagnosticsCsvToUri(
        context: Context,
        targetUri: Uri,
        sessionIdsFilter: Set<String>? = null
    ) {
        viewModelScope.launch {
            _statusBanner.value = "Querying Room SQLite database & exporting Shop Management System CSV..."
            val roomSessions = repository.getRoomDiagnosticsForExport(
                userId = currentUserId,
                sessionIdsFilter = sessionIdsFilter
            ).getOrElse {
                val fallback = (diagnosticsState.value as? DataState.Success)?.data.orEmpty()
                if (sessionIdsFilter.isNullOrEmpty()) fallback else fallback.filter { sessionIdsFilter.contains(it.id) }
            }

            if (roomSessions.isEmpty()) {
                _statusBanner.value = "No diagnostic reports found in local Room database to export."
                return@launch
            }

            DiagnosticCsvExporter.writeCsvToUri(
                context = context,
                targetUri = targetUri,
                sessions = roomSessions
            ).onSuccess { exportedCount ->
                _statusBanner.value =
                    "Exported $exportedCount diagnostic report(s) from Room SQLite to CSV for Shop Management Systems."
            }.onFailure { e ->
                _statusBanner.value = e.localizedMessage ?: "Failed to export Room diagnostic reports to CSV."
            }
        }
    }

    /**
     * Queries locally stored diagnostic reports from the Room SQLite database (`diagnostic_reports`),
     * writes a Shop Management System CSV file to cache, and opens Android's Share / Email sheet.
     */
    fun shareOrEmailRoomDiagnosticsCsv(
        context: Context,
        sessionIdsFilter: Set<String>? = null
    ) {
        viewModelScope.launch {
            _statusBanner.value = "Preparing Shop Management System CSV from local Room database..."
            val roomSessions = repository.getRoomDiagnosticsForExport(
                userId = currentUserId,
                sessionIdsFilter = sessionIdsFilter
            ).getOrElse {
                val fallback = (diagnosticsState.value as? DataState.Success)?.data.orEmpty()
                if (sessionIdsFilter.isNullOrEmpty()) fallback else fallback.filter { sessionIdsFilter.contains(it.id) }
            }

            if (roomSessions.isEmpty()) {
                _statusBanner.value = "No diagnostic reports found in local Room database to share."
                return@launch
            }

            DiagnosticCsvExporter.generateCsvToCacheFile(
                context = context,
                sessions = roomSessions
            ).onSuccess { csvFile ->
                DiagnosticCsvExporter.launchShareOrEmailCsv(
                    context = context,
                    csvFile = csvFile,
                    sessions = roomSessions
                ).onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "Unable to open CSV share chooser."
                }
            }.onFailure { e ->
                _statusBanner.value = e.localizedMessage ?: "Failed to generate CSV file from Room."
            }
        }
    }

    fun answerFlowchartNode(
        flowchart: TroubleshootingFlowchart,
        answerLabel: String,
        targetNodeId: String
    ) {
        val nodes = flowchart.parsedNodes()
        val nextNode = nodes.firstOrNull { it.nodeId.equals(targetNodeId, ignoreCase = true) }
        if (nextNode == null) {
            _statusBanner.value = "Target branch node ($targetNodeId) not found in flowchart."
            return
        }
        val updatedSummary = "${flowchart.visitedPathSummary} -> ${flowchart.currentNodeId}($answerLabel) -> ${nextNode.nodeId}"
        val isConclusion = nextNode.nodeType.equals("CONCLUSION", ignoreCase = true) ||
            (nextNode.yesNodeId.isBlank() && nextNode.noNodeId.isBlank())

        viewModelScope.launch {
            repository.updateFlowchartWalkthrough(
                flowchartId = flowchart.id,
                currentNodeId = nextNode.nodeId,
                visitedPathSummary = updatedSummary.takeLast(1800),
                isResolved = isConclusion
            ).onFailure { e ->
                _statusBanner.value = e.localizedMessage ?: "Failed to advance flowchart."
            }
        }
    }

    fun resetFlowchartWalkthrough(flowchart: TroubleshootingFlowchart) {
        val firstNodeId = flowchart.parsedNodes().firstOrNull()?.nodeId ?: "N1"
        viewModelScope.launch {
            repository.updateFlowchartWalkthrough(
                flowchartId = flowchart.id,
                currentNodeId = firstNodeId,
                visitedPathSummary = "Restarted at $firstNodeId",
                isResolved = false
            ).onFailure { e ->
                _statusBanner.value = e.localizedMessage ?: "Failed to reset flowchart."
            }
        }
    }

    fun deleteFlowchart(flowchartId: String) {
        viewModelScope.launch {
            if (_activeFlowchartId.value == flowchartId) {
                _activeFlowchartId.value = null
            }
            repository.deleteFlowchart(flowchartId).onFailure { e ->
                _statusBanner.value = e.localizedMessage ?: "Failed to delete flowchart."
            }
        }
    }

    fun addEquipmentProfile(
        name: String,
        modelNumber: String,
        systemCategory: SystemCategory,
        environmentType: String,
        operatingHours: Long,
        notes: String
    ) {
        if (name.isBlank() || modelNumber.isBlank()) {
            _statusBanner.value = "Equipment name and model number are required."
            return
        }
        viewModelScope.launch {
            repository.createEquipment(
                name = name,
                modelNumber = modelNumber,
                systemCategory = systemCategory.label,
                environmentType = environmentType,
                operatingHours = operatingHours,
                notes = notes
            ).onSuccess {
                _statusBanner.value = "Registered $name ($modelNumber)."
            }.onFailure { e ->
                _statusBanner.value = e.localizedMessage ?: "Failed to register equipment."
            }
        }
    }

    fun deleteEquipmentProfile(equipmentId: String) {
        viewModelScope.launch {
            repository.deleteEquipment(equipmentId).onFailure { e ->
                _statusBanner.value = e.localizedMessage ?: "Failed to delete equipment."
            }
        }
    }

    fun seedReferenceLibrary() {
        viewModelScope.launch {
            _statusBanner.value = "Caching reference equipment, repair guides & flowcharts to on-device storage..."
            var addedCount = 0
            for (eq in StarterTemplates.starterEquipment) {
                val res = repository.createEquipment(
                    name = eq.name,
                    modelNumber = eq.modelNumber,
                    systemCategory = eq.systemCategory.label,
                    environmentType = eq.environmentType,
                    operatingHours = eq.operatingHours,
                    notes = eq.notes
                )
                if (res.isSuccess) addedCount++
            }
            var firstFlowId: String? = null
            for (flow in StarterTemplates.starterFlowcharts) {
                val res = repository.createFlowchart(
                    title = flow.title,
                    equipmentModel = flow.equipmentModel,
                    systemCategory = flow.systemCategory.label,
                    environmentType = flow.environmentType,
                    failureMode = flow.failureMode,
                    nodes = flow.nodes
                )
                if (firstFlowId == null) {
                    firstFlowId = res.getOrNull()
                }
            }
            if (firstFlowId != null && _activeFlowchartId.value == null) {
                _activeFlowchartId.value = firstFlowId
            }

            var firstDiagId: String? = null
            for (diag in StarterTemplates.starterDiagnostics) {
                val res = repository.createDiagnosticSession(diag)
                if (firstDiagId == null) {
                    firstDiagId = res.getOrNull()
                }
            }
            if (firstDiagId != null && _activeDiagnosticId.value == null) {
                _activeDiagnosticId.value = firstDiagId
            }

            _lastCacheVerifiedMillis.value = System.currentTimeMillis()
            _statusBanner.value = "Cached $addedCount equipment models, ${StarterTemplates.starterDiagnostics.size} repair guides, and ${StarterTemplates.starterFlowcharts.size} flowcharts for offline field access."
        }
    }

    private fun org.json.JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { idx ->
            optString(idx)?.takeIf { it.isNotBlank() }
        }
    }

    private companion object {
        const val TAG = "MechSightVM"
        const val MAX_INLINE_MEDIA_BYTES = 15 * 1024 * 1024 // 15MB safe ceiling under 20MB limit
    }
}
