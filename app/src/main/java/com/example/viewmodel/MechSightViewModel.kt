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
import com.example.data.model.StarterTemplates
import com.example.data.model.SystemCategory
import com.example.data.model.TroubleshootingFlowchart
import com.example.data.repository.MechSightRepository
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

    // Structured JSON schema for Multimodal Diagnostic + Repair Guide + Companion Flowchart
    private val diagnosticSchema = Schema.obj(
        properties = mapOf(
            "rootCauseTitle" to Schema.string(description = "Concise engineering title of the primary failure cause"),
            "severity" to Schema.enumeration(
                values = listOf("CRITICAL", "HIGH", "MODERATE", "LOW"),
                description = "Hazard and operational urgency level"
            ),
            "confidencePercent" to Schema.integer(description = "Diagnostic confidence score from 50 to 99"),
            "technicalAnalysis" to Schema.string(description = "Detailed engineering explanation of the mechanical, electrical, hydraulic, pneumatic, controller, or computer failure mechanism"),
            "safetyWarnings" to Schema.array(
                items = Schema.string(),
                description = "Lockout/Tagout (LOTO), depressurization, ESD, or high-voltage safety warnings"
            ),
            "requiredTools" to Schema.array(
                items = Schema.string(),
                description = "Specific instruments, gauges, tools, software utilities, and replacement parts"
            ),
            "repairSteps" to Schema.array(
                items = Schema.string(),
                description = "Sequential step-by-step repair instructions tailored to the equipment model and environment"
            ),
            "verificationTest" to Schema.string(description = "Post-repair verification test procedure with target measurements"),
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
                    "Offline Cache Ready: ${summary.cachedDiagnosticsCount} repair guides, ${summary.cachedFlowchartsCount} flowcharts, ${summary.cachedEquipmentCount} assets pinned on-device$pendingNote."
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

    fun clearStatusBanner() {
        _statusBanner.value = null
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
        mediaSummaryLabel: String
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
                    mediaSummaryLabel = mediaSummaryLabel
                )
                return@launch
            }

            _aiTaskState.value = AiTaskState.Running(
                "Analyzing ${systemCategory.label} telemetry & generating repair guide for $equipmentModel..."
            )
            try {
                val promptBuilder = StringBuilder().apply {
                    appendLine("You are a Principal Field Service & Reliability Engineer.")
                    appendLine("Diagnose the following failure and produce a model-specific step-by-step repair guide AND an interactive Yes/No troubleshooting flowchart.")
                    appendLine("System Category: ${systemCategory.label}")
                    appendLine("Equipment Name: ${equipmentName.ifBlank { "Industrial System" }}")
                    appendLine("Equipment Model: ${equipmentModel.ifBlank { "Standard Model" }}")
                    appendLine("Operating Environment: $environmentType")
                    appendLine("Reported Symptoms / Fault Codes: ${symptomDescription.ifBlank { "Inspect attached multimodal evidence." }}")
                    if (acousticSummary.isNotBlank()) {
                        appendLine("Acoustic / Audio Telemetry Observation: $acousticSummary")
                    }
                    if (mediaSummaryLabel.isNotBlank()) {
                        appendLine("Attached Media Context: $mediaSummaryLabel")
                    }
                    appendLine("Ensure repairSteps include exact safety precautions, target measurements (voltage, pressure, torque, temperature, or diagnostic commands), and model-specific procedures.")
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
                    status = "IN_PROGRESS"
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
                    mediaSummaryLabel = mediaSummaryLabel
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
                    mediaSummaryLabel = mediaSummaryLabel
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
        mediaSummaryLabel: String
    ) {
        val cleanName = equipmentName.ifBlank { "${systemCategory.label} Assembly" }
        val cleanModel = equipmentModel.ifBlank { "Field-${systemCategory.badgeCode}-Unit" }
        val cleanSymptom = symptomDescription.ifBlank { systemCategory.commonFailureModes.first() }

        val session = DiagnosticSession(
            userId = currentUserId,
            equipmentName = cleanName,
            equipmentModel = cleanModel,
            systemCategory = systemCategory.label,
            environmentType = environmentType,
            symptomDescription = cleanSymptom,
            attachedMediaSummary = if (mediaSummaryLabel.isNotBlank()) {
                "$mediaSummaryLabel (Offline Field Cache)"
            } else {
                "Offline Field Engineering Protocol"
            },
            rootCauseTitle = "${systemCategory.label} Fault Isolation: ${cleanSymptom.take(70)}",
            severity = "HIGH",
            confidencePercent = 84L,
            technicalAnalysis = "Generated via MechSight Offline Field Engineering Engine for $cleanName ($cleanModel) operating in $environmentType. Symptom profile indicates primary ${systemCategory.label.lowercase()} subsystem degradation requiring sequential isolation and calibration.",
            safetyWarnings = listOf(
                "Execute Lockout/Tagout (LOTO) and verify zero stored energy before opening $cleanModel enclosures.",
                "Account for $environmentType hazards and wear appropriate PPE before probing live test points."
            ),
            requiredTools = listOf(
                "Calibrated Digital Multimeter / Process Calibrator",
                "OEM Service Schematic for $cleanModel",
                "Torque Wrench & Insulated Hand Tool Kit",
                "Thermal / Acoustic Inspection Probe"
            ),
            repairSteps = listOf(
                "Isolate primary energy supply to $cleanName ($cleanModel) and verify safe state across all gauges and test points.",
                "Inspect ${systemCategory.subtitle.lowercase()} for physical wear, thermal discoloration, fluid/air bypass, or loose terminations related to: $cleanSymptom.",
                "Measure baseline operating parameters against $cleanModel OEM tolerances and replace or recalibrate out-of-spec components.",
                "Clear stored fault latches, restore nominal supply, and run a 15-minute load cycle while monitoring stability."
            ),
            completedStepIndices = emptyList(),
            verificationTest = "Verify $cleanModel operates within nominal thermal, pressure, and electrical thresholds under full load for 15 minutes with zero fault recurrence.",
            status = "IN_PROGRESS"
        )

        val nodes = listOf(
            FlowchartNode(
                nodeId = "N1",
                nodeType = "QUESTION",
                prompt = "Are primary supply inputs (power, fluid pressure, or bus communication) within nominal specification at $cleanModel input terminals?",
                observationHint = "Check main supply gauge, breaker status, or input bus voltage",
                yesNodeId = "N2",
                noNodeId = "N3",
                recommendedAction = "Verify upstream supply integrity"
            ),
            FlowchartNode(
                nodeId = "N2",
                nodeType = "QUESTION",
                prompt = "Does the fault persist when the actuator, drive stage, or controller module is tested in manual/local bypass mode?",
                observationHint = "Observe local status indicators, acoustic signature, or actuator response",
                yesNodeId = "N4",
                noNodeId = "N5",
                recommendedAction = "Isolate control signal vs. mechanical/power stage"
            ),
            FlowchartNode(
                nodeId = "N3",
                nodeType = "CONCLUSION",
                prompt = "Upstream Supply / Regulation Fault Identified on $cleanModel",
                observationHint = "Input supply out of specification before primary stage",
                recommendedAction = "Restore upstream power/pressure regulation, replace blown fuses or clogged filters, and re-verify input stability."
            ),
            FlowchartNode(
                nodeId = "N4",
                nodeType = "CONCLUSION",
                prompt = "Primary ${systemCategory.label} Component Degradation on $cleanModel",
                observationHint = "Fault reproduces under direct local actuation",
                recommendedAction = "Overhaul or replace worn ${systemCategory.label.lowercase()} stage assembly on $cleanModel and torque to OEM specification."
            ),
            FlowchartNode(
                nodeId = "N5",
                nodeType = "CONCLUSION",
                prompt = "Control Loop / Feedback Sensor Drift on $cleanModel",
                observationHint = "Primary hardware stage operates normally in local bypass",
                recommendedAction = "Recalibrate feedback sensor, inspect shielded signal harness for EMI/corrosion, and reload controller parameters."
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
                    You are a Senior Diagnostic Systems Engineer.
                    Generate an interactive conditional troubleshooting flowchart (decision tree) that guides a technician through a sequence of YES/NO questions and observations to narrow down the exact root cause of a failure.
                    System Domain: ${systemCategory.label}
                    Equipment Model: ${equipmentModel.ifBlank { "Standard ${systemCategory.label} System" }}
                    Environment: $environmentType
                    Failure Symptom: $failureModePrompt

                    Rules for the decision tree nodes:
                    1. Start at nodeId "N1" with nodeType "QUESTION".
                    2. Every "QUESTION" node MUST specify valid "yesNodeId" and "noNodeId" pointing to other nodes in the list.
                    3. Include specific measurement thresholds or visual/acoustic cues in "observationHint" (e.g., multimeter voltage, pressure bar/PSI, POST hex code, PLC LED state, bearing temp).
                    4. Terminal nodes MUST have nodeType "CONCLUSION", empty "yesNodeId" and "noNodeId", a clear root-cause explanation in "prompt", and a concrete fix in "recommendedAction".
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
        val cleanModel = equipmentModel.ifBlank { "General ${systemCategory.label}" }
        val nodes = listOf(
            FlowchartNode(
                nodeId = "N1",
                nodeType = "QUESTION",
                prompt = "Are input power, supply pressure, or bus voltages within rated limits at $cleanModel?",
                observationHint = "Measure primary supply input with calibrated gauge or multimeter",
                yesNodeId = "N2",
                noNodeId = "N3",
                recommendedAction = "Check primary supply rails and safety interlocks"
            ),
            FlowchartNode(
                nodeId = "N2",
                nodeType = "QUESTION",
                prompt = "Are any active fault codes, abnormal thermal hotspots, or acoustic anomalies present on the primary stage?",
                observationHint = "Inspect controller diagnostics, bearing/valve temperature, and vibration",
                yesNodeId = "N4",
                noNodeId = "N5",
                recommendedAction = "Localize fault to primary actuator vs. feedback loop"
            ),
            FlowchartNode(
                nodeId = "N3",
                nodeType = "CONCLUSION",
                prompt = "Primary Supply / Interlock Interruption on $cleanModel",
                observationHint = "Supply below minimum threshold at input terminals",
                recommendedAction = "Restore supply regulation, reset tripped overload/interlock, and inspect upstream feed."
            ),
            FlowchartNode(
                nodeId = "N4",
                nodeType = "CONCLUSION",
                prompt = "Primary ${systemCategory.label} Stage Fault: ${failureModePrompt.take(80)}",
                observationHint = "Direct physical/electrical fault signature confirmed on stage",
                recommendedAction = "Service or replace affected ${systemCategory.label.lowercase()} component on $cleanModel and verify tolerances."
            ),
            FlowchartNode(
                nodeId = "N5",
                nodeType = "CONCLUSION",
                prompt = "Intermittent Signal / Calibration Drift on $cleanModel",
                observationHint = "Supply and primary stage nominal; feedback loop out of calibration",
                recommendedAction = "Clean connectors, verify shield grounding, and recalibrate sensor zero/span."
            )
        )

        val flowchartId = repository.createFlowchart(
            title = "$cleanModel Offline Troubleshooting Tree",
            equipmentModel = cleanModel,
            systemCategory = systemCategory.label,
            environmentType = environmentType,
            failureMode = failureModePrompt.take(200),
            nodes = nodes
        ).getOrNull()

        if (flowchartId != null) {
            _activeFlowchartId.value = flowchartId
            _statusBanner.value = "Flowchart generated offline and saved to on-device Firestore cache."
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
