package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class SystemCategory(
    val label: String,
    val badgeCode: String,
    val subtitle: String,
    val commonFailureModes: List<String>
) {
    MECHANICAL(
        label = "Engine & Powertrain",
        badgeCode = "ENG",
        subtitle = "Engines, Valvetrain, Timing Chains, Turbo/Boost, Fuel Systems, Cooling & Transmissions",
        commonFailureModes = listOf(
            "Cold-start VVT cam phaser rattle & timing chain slack (P0016)",
            "Turbocharger underboost code (P0299) & intercooler charge-pipe boost leak",
            "Torque converter shudder at 40-55 MPH & transmission shift flare"
        )
    ),
    HYDRAULIC(
        label = "Brakes",
        badgeCode = "BRK",
        subtitle = "Disc & Drum Brakes, Electronic Brake Boosters (iBooster), Brake-by-Wire, ABS/ESC & EPB",
        commonFailureModes = listOf(
            "Electronic brake booster (iBooster) assist fault or spongy brake pedal feel",
            "ABS / Stability Control wheel speed sensor fault (C0035) & traction light",
            "Electronic Parking Brake (EPB) rear caliper actuator jam or service-mode fault"
        )
    ),
    PNEUMATIC(
        label = "Steering & Suspension",
        badgeCode = "STRG",
        subtitle = "Electric Rack & Pinion (EPAS), Tie Rods, Struts, Control Arms, Wheel Bearings & Air Ride",
        commonFailureModes = listOf(
            "Electric Power Assisted Steering (EPAS) rack & pinion torque sensor fault & loss of assist",
            "Front wheel hub bearing growl above 45 MPH or lower control arm ball joint clunk",
            "Electronic air suspension ride-height sensor fault or rear corner sag overnight"
        )
    ),
    ELECTRICAL(
        label = "Battery, Starting & Charging",
        badgeCode = "ELEC",
        subtitle = "12V/48V/HV EV Batteries, Alternators, Starters, Relays, Fuses & Wiring Harnesses",
        commonFailureModes = listOf(
            "Overnight parasitic battery drain (> 350 mA) & no-crank morning start",
            "Alternator voltage regulator ripple & battery warning light under load",
            "Starter solenoid click with high voltage drop across engine ground strap"
        )
    ),
    COMPUTER_CONTROLLER(
        label = "ECU, Modules & CAN-Bus",
        badgeCode = "ECU",
        subtitle = "ECM/PCM, TCM, BCM, ADAS Modules, 5V Sensor Reference & OBD-II CAN-Bus Networks",
        commonFailureModes = listOf(
            "OBD-II U0100 Lost Communication with ECM/PCM on High-Speed CAN-Bus",
            "5V sensor reference bus (VREF) pulled low by shorted pressure sensor (P06A6)",
            "TCM limp-home mode with CAN gateway or shift solenoid circuit fault"
        )
    );

    companion object {
        fun fromLabel(label: String): SystemCategory {
            val normalized = label.trim().lowercase()
            return when {
                normalized.contains("brake") || normalized == "brk" || normalized == "hydraulic" || normalized == "hydr" -> HYDRAULIC
                normalized.contains("steering") || normalized.contains("suspension") || normalized.contains("epas") ||
                    normalized == "strg" || normalized == "pneumatic" || normalized == "pneu" -> PNEUMATIC
                normalized.contains("battery") || normalized.contains("charging") || normalized.contains("electrical") ||
                    normalized == "elec" -> ELECTRICAL
                normalized.contains("ecu") || normalized.contains("can-bus") || normalized.contains("computer") ||
                    normalized.contains("module") -> COMPUTER_CONTROLLER
                normalized.contains("engine") || normalized.contains("powertrain") || normalized == "eng" ||
                    normalized == "mechanical" || normalized == "mech" -> MECHANICAL
                else -> entries.firstOrNull {
                    it.label.equals(label, ignoreCase = true) ||
                        it.badgeCode.equals(label, ignoreCase = true)
                } ?: MECHANICAL
            }
        }
    }
}

enum class EnvironmentPreset(
    val label: String,
    val hazardNote: String
) {
    AUTOMOTIVE_SERVICE_BAY("Automotive Service Bay", "2-post lift inspection, OBD-II bi-directional scan tool, battery support"),
    DAILY_STREET_HIGHWAY("Street & Highway Commute", "Stop-and-go thermal cycling, highway cruise load, road debris & moisture"),
    HEAVY_TOWING_HAULING("Heavy Towing & Hauling", "High ATF/coolant temperatures, steep grade boost load, heavy braking duty"),
    OFF_ROAD_TRAIL("Off-Road & 4x4 Trail", "Mud/water splash ingress, suspension articulation stress, 4WD transfer case load"),
    EXTREME_COLD_WINTER("Sub-Zero Winter / Cold Start", "High CCA cranking draw, thickened oil viscosity, road salt corrosion"),
    TRACK_PERFORMANCE("Track & High-Performance", "Sustained high RPM, turbo heat soak, elevated brake fluid temperatures"),
    EV_HYBRID_HIGH_VOLTAGE("EV & Hybrid High-Voltage", "400V/800V orange HV cables, inverter coolant loop, regenerative braking")
}

@IgnoreExtraProperties
data class EquipmentProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val modelNumber: String = "",
    val systemCategory: String = SystemCategory.MECHANICAL.label,
    val environmentType: String = EnvironmentPreset.AUTOMOTIVE_SERVICE_BAY.label,
    val operatingHours: Long = 0L,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    @get:Exclude val isFromCache: Boolean = false,
    @get:Exclude val hasPendingWrites: Boolean = false
)

data class MechanicVoiceObservation(
    val id: String = "",
    val timestampLabel: String = "",
    val stepTag: String = "GENERAL",
    val categoryTag: String = "MEASUREMENT",
    val transcript: String = ""
) {
    fun toEncodedString(): String {
        val cleanId = id.replace("::", "-").ifBlank { "vn_${System.currentTimeMillis()}" }
        val cleanTs = timestampLabel.replace("::", "-").ifBlank { "Logged" }
        val cleanStep = stepTag.replace("::", "-").ifBlank { "GENERAL" }
        val cleanCat = categoryTag.replace("::", "-").ifBlank { "OBSERVATION" }
        val cleanText = transcript.replace("::", " - ").trim().take(600)
        return listOf(cleanId, cleanTs, cleanStep, cleanCat, cleanText).joinToString("::")
    }

    companion object {
        fun fromEncodedString(encoded: String): MechanicVoiceObservation {
            val parts = encoded.split("::")
            return if (parts.size >= 5) {
                MechanicVoiceObservation(
                    id = parts[0].ifBlank { "vn_0" },
                    timestampLabel = parts[1].ifBlank { "Logged" },
                    stepTag = parts[2].ifBlank { "GENERAL" },
                    categoryTag = parts[3].ifBlank { "OBSERVATION" },
                    transcript = parts.drop(4).joinToString("::")
                )
            } else {
                MechanicVoiceObservation(
                    id = "vn_${encoded.hashCode()}",
                    timestampLabel = "Field Log",
                    stepTag = "GENERAL",
                    categoryTag = "OBSERVATION",
                    transcript = encoded
                )
            }
        }
    }
}

@IgnoreExtraProperties
data class DiagnosticSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val equipmentName: String = "",
    val equipmentModel: String = "",
    val systemCategory: String = SystemCategory.MECHANICAL.label,
    val environmentType: String = EnvironmentPreset.AUTOMOTIVE_SERVICE_BAY.label,
    val symptomDescription: String = "",
    val attachedMediaSummary: String = "",
    val rootCauseTitle: String = "",
    val severity: String = "HIGH",
    val confidencePercent: Long = 85L,
    val technicalAnalysis: String = "",
    val safetyWarnings: List<String> = emptyList(),
    val requiredTools: List<String> = emptyList(),
    val repairSteps: List<String> = emptyList(),
    val completedStepIndices: List<Long> = emptyList(),
    val verificationTest: String = "",
    val status: String = "IN_PROGRESS",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    @get:Exclude val isFromCache: Boolean = false,
    @get:Exclude val hasPendingWrites: Boolean = false,
    @get:Exclude val repairDurationMinutes: Int = 0,
    @get:Exclude val mechanicVoiceLogs: List<String> = emptyList(),
    @get:Exclude val vinNumber: String = "",
    @get:Exclude val odometerMileage: Long = 0L
) {
    fun parsedVoiceObservations(): List<MechanicVoiceObservation> {
        return mechanicVoiceLogs.map { MechanicVoiceObservation.fromEncodedString(it) }
    }

    fun effectiveRepairMinutes(): Int {
        if (repairDurationMinutes > 0) return repairDurationMinutes
        val basePerStep = when (severity.uppercase()) {
            "CRITICAL" -> 18
            "HIGH" -> 14
            "MODERATE" -> 11
            else -> 8
        }
        val domainOffset = when (SystemCategory.fromLabel(systemCategory)) {
            SystemCategory.HYDRAULIC -> 16
            SystemCategory.MECHANICAL -> 20
            SystemCategory.ELECTRICAL -> 12
            SystemCategory.COMPUTER_CONTROLLER -> 10
            SystemCategory.PNEUMATIC -> 8
        }
        return (repairSteps.size.coerceAtLeast(2) * basePerStep) + domainOffset
    }
}

data class FlowchartNode(
    val nodeId: String = "N1",
    val nodeType: String = "QUESTION", // QUESTION or CONCLUSION
    val prompt: String = "",
    val observationHint: String = "",
    val yesNodeId: String = "",
    val noNodeId: String = "",
    val recommendedAction: String = ""
) {
    fun toEncodedString(): String {
        val cleanPrompt = prompt.replace("::", " - ").take(180)
        val cleanHint = observationHint.replace("::", " - ").take(140)
        val cleanAction = recommendedAction.replace("::", " - ").take(180)
        return listOf(
            nodeId.trim(),
            nodeType.trim(),
            cleanPrompt,
            cleanHint,
            yesNodeId.trim(),
            noNodeId.trim(),
            cleanAction
        ).joinToString("::").take(580)
    }

    companion object {
        fun fromEncodedString(encoded: String): FlowchartNode {
            val parts = encoded.split("::")
            return FlowchartNode(
                nodeId = parts.getOrNull(0)?.ifBlank { "N1" } ?: "N1",
                nodeType = parts.getOrNull(1)?.ifBlank { "QUESTION" } ?: "QUESTION",
                prompt = parts.getOrNull(2).orEmpty(),
                observationHint = parts.getOrNull(3).orEmpty(),
                yesNodeId = parts.getOrNull(4).orEmpty(),
                noNodeId = parts.getOrNull(5).orEmpty(),
                recommendedAction = parts.getOrNull(6).orEmpty()
            )
        }
    }
}

@IgnoreExtraProperties
data class TroubleshootingFlowchart(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val equipmentModel: String = "",
    val systemCategory: String = SystemCategory.COMPUTER_CONTROLLER.label,
    val environmentType: String = EnvironmentPreset.AUTOMOTIVE_SERVICE_BAY.label,
    val failureMode: String = "",
    val currentNodeId: String = "N1",
    val visitedPathSummary: String = "Started interactive automotive troubleshooting",
    val nodesList: List<String> = emptyList(),
    @get:com.google.firebase.firestore.PropertyName("isResolved")
    @field:com.google.firebase.firestore.PropertyName("isResolved")
    val isResolved: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    @get:Exclude val isFromCache: Boolean = false,
    @get:Exclude val hasPendingWrites: Boolean = false
) {
    fun parsedNodes(): List<FlowchartNode> = nodesList.map { FlowchartNode.fromEncodedString(it) }
}

data class FirestoreCacheStatus(
    val isNetworkAvailable: Boolean = true,
    val isForcedOfflineMode: Boolean = false,
    val isServingFromCache: Boolean = false,
    val pendingWritesCount: Int = 0,
    val cachedDiagnosticsCount: Int = 0,
    val cachedFlowchartsCount: Int = 0,
    val cachedEquipmentCount: Int = 0,
    val cacheAllocationMb: Int = 100,
    val lastCacheVerifiedMillis: Long = System.currentTimeMillis()
) {
    val isEffectivelyOffline: Boolean
        get() = !isNetworkAvailable || isForcedOfflineMode || isServingFromCache
}
