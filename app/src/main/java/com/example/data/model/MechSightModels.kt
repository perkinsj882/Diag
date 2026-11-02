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
        label = "Mechanical",
        badgeCode = "MECH",
        subtitle = "Bearings, Gearboxes, Shafts, Turbines & Engines",
        commonFailureModes = listOf(
            "High-frequency bearing vibration & thermal rise",
            "Gearbox tooth backlash & metallic oil debris",
            "Shaft misalignment & coupling shear under load"
        )
    ),
    ELECTRICAL(
        label = "Electrical",
        badgeCode = "ELEC",
        subtitle = "3-Phase Motors, Transformers, Relays & Switchgear",
        commonFailureModes = listOf(
            "3-phase motor overload trip & winding insulation breakdown",
            "Contactor chatter & control transformer voltage sag",
            "Ground fault interrupter nuisance tripping under moisture"
        )
    ),
    HYDRAULIC(
        label = "Hydraulic",
        badgeCode = "HYDR",
        subtitle = "Pumps, Cylinders, Proportional Valves & Accumulators",
        commonFailureModes = listOf(
            "Pump cavitation whine & slow actuator cycle under load",
            "Directional spool valve sticking & pressure relief drift",
            "Cylinder internal bypass & hydraulic fluid overheating"
        )
    ),
    PNEUMATIC(
        label = "Pneumatic",
        badgeCode = "PNEU",
        subtitle = "Compressors, Solenoid Manifolds, FRLs & Cylinders",
        commonFailureModes = listOf(
            "Actuator sluggish stroke & manifold exhaust air leak",
            "FRL regulator pressure drop & moisture carryover in lines",
            "Compressor unloader valve failure & short-cycling"
        )
    ),
    COMPUTER_CONTROLLER(
        label = "Computer Controller",
        badgeCode = "CTRL",
        subtitle = "PLCs, CNCs, VFD/Servo Drives, ECUs & SCADA RTUs",
        commonFailureModes = listOf(
            "PLC CPU Fault LED flashing red & fieldbus I/O communication loss",
            "VFD DC bus overvoltage trip during deceleration",
            "ECU CAN-bus timeout & analog sensor 4-20mA signal drift"
        )
    ),
    COMPUTER(
        label = "Computer",
        badgeCode = "COMP",
        subtitle = "Industrial PCs, Rack Servers, Workstations & Edge Nodes",
        commonFailureModes = listOf(
            "No POST / Beep code sequence & DRAM/PCIe initialization halt",
            "Kernel panic / WHEA uncorrectable hardware error under load",
            "Thermal throttling, VRM overheating & NVMe RAID array degraded"
        )
    );

    companion object {
        fun fromLabel(label: String): SystemCategory {
            return entries.firstOrNull { it.label.equals(label, ignoreCase = true) } ?: MECHANICAL
        }
    }
}

enum class EnvironmentPreset(
    val label: String,
    val hazardNote: String
) {
    MANUFACTURING("Manufacturing Floor", "EMI, coolant mist, continuous 24/7 duty cycle"),
    HEAVY_INDUSTRIAL("Heavy Industrial", "High shock loads, abrasive dust, extreme thermal swings"),
    DATA_CENTER("Data Center / Server Room", "ESD sensitive, strict airflow & redundant PSU rails"),
    AUTOMOTIVE_FLEET("Automotive / Fleet", "Road vibration, 12V/24V transient spikes, weather exposure"),
    MARINE_OFFSHORE("Marine / Offshore", "Salt-fog corrosion, high humidity, bilge vapor hazards"),
    HVAC_BUILDING("HVAC / Building", "Rooftop UV exposure, refrigerant pressure, damper linkage"),
    FIELD_AGRICULTURAL("Field / Agricultural", "Mud/debris ingress, hydraulic implement contamination")
}

@IgnoreExtraProperties
data class EquipmentProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val modelNumber: String = "",
    val systemCategory: String = SystemCategory.MECHANICAL.label,
    val environmentType: String = EnvironmentPreset.MANUFACTURING.label,
    val operatingHours: Long = 0L,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    @get:Exclude val isFromCache: Boolean = false,
    @get:Exclude val hasPendingWrites: Boolean = false
)

@IgnoreExtraProperties
data class DiagnosticSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val equipmentName: String = "",
    val equipmentModel: String = "",
    val systemCategory: String = SystemCategory.MECHANICAL.label,
    val environmentType: String = EnvironmentPreset.MANUFACTURING.label,
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
    @get:Exclude val hasPendingWrites: Boolean = false
)

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
    val environmentType: String = EnvironmentPreset.MANUFACTURING.label,
    val failureMode: String = "",
    val currentNodeId: String = "N1",
    val visitedPathSummary: String = "Started interactive troubleshooting",
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
