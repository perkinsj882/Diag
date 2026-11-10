package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.DiagnosticSession
import com.example.data.model.EquipmentProfile
import com.example.data.model.TroubleshootingFlowchart
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import java.util.Date

@Entity(tableName = "diagnostic_reports")
data class DiagnosticReportEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val equipmentName: String,
    val equipmentModel: String,
    val systemCategory: String,
    val environmentType: String,
    val symptomDescription: String,
    val attachedMediaSummary: String,
    val rootCauseTitle: String,
    val severity: String,
    val confidencePercent: Long,
    val technicalAnalysis: String,
    val safetyWarningsJson: String,
    val requiredToolsJson: String,
    val repairStepsJson: String,
    val completedStepIndicesJson: String,
    val verificationTest: String,
    val status: String,
    val repairDurationMinutes: Int,
    val mechanicVoiceLogsJson: String = "[]",
    val vinNumber: String = "",
    val odometerMileage: Long = 0L,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val hasPendingSync: Boolean = false
) {
    fun toDomainModel(): DiagnosticSession {
        return DiagnosticSession(
            id = id,
            userId = userId,
            equipmentName = equipmentName,
            equipmentModel = equipmentModel,
            systemCategory = systemCategory,
            environmentType = environmentType,
            symptomDescription = symptomDescription,
            attachedMediaSummary = attachedMediaSummary,
            rootCauseTitle = rootCauseTitle,
            severity = severity,
            confidencePercent = confidencePercent,
            technicalAnalysis = technicalAnalysis,
            safetyWarnings = decodeStringList(safetyWarningsJson),
            requiredTools = decodeStringList(requiredToolsJson),
            repairSteps = decodeStringList(repairStepsJson),
            completedStepIndices = decodeLongList(completedStepIndicesJson),
            verificationTest = verificationTest,
            status = status,
            createdAt = Timestamp(Date(createdAtMillis)),
            updatedAt = Timestamp(Date(updatedAtMillis)),
            isFromCache = true,
            hasPendingWrites = hasPendingSync,
            repairDurationMinutes = repairDurationMinutes,
            mechanicVoiceLogs = decodeStringList(mechanicVoiceLogsJson),
            vinNumber = vinNumber,
            odometerMileage = odometerMileage
        )
    }
}

@Entity(tableName = "troubleshooting_flowcharts")
data class FlowchartEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val equipmentModel: String,
    val systemCategory: String,
    val environmentType: String,
    val failureMode: String,
    val currentNodeId: String,
    val visitedPathSummary: String,
    val nodesListJson: String,
    val isResolved: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val hasPendingSync: Boolean = false
) {
    fun toDomainModel(): TroubleshootingFlowchart {
        return TroubleshootingFlowchart(
            id = id,
            userId = userId,
            title = title,
            equipmentModel = equipmentModel,
            systemCategory = systemCategory,
            environmentType = environmentType,
            failureMode = failureMode,
            currentNodeId = currentNodeId,
            visitedPathSummary = visitedPathSummary,
            nodesList = decodeStringList(nodesListJson),
            isResolved = isResolved,
            createdAt = Timestamp(Date(createdAtMillis)),
            updatedAt = Timestamp(Date(updatedAtMillis)),
            isFromCache = true,
            hasPendingWrites = hasPendingSync
        )
    }
}

@Entity(tableName = "equipment_profiles")
data class EquipmentEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val modelNumber: String,
    val systemCategory: String,
    val environmentType: String,
    val operatingHours: Long,
    val notes: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val hasPendingSync: Boolean = false
) {
    fun toDomainModel(): EquipmentProfile {
        return EquipmentProfile(
            id = id,
            userId = userId,
            name = name,
            modelNumber = modelNumber,
            systemCategory = systemCategory,
            environmentType = environmentType,
            operatingHours = operatingHours,
            notes = notes,
            createdAt = Timestamp(Date(createdAtMillis)),
            updatedAt = Timestamp(Date(updatedAtMillis)),
            isFromCache = true,
            hasPendingWrites = hasPendingSync
        )
    }
}

fun DiagnosticSession.toRoomEntity(
    overrideId: String = id,
    overrideUserId: String = userId,
    pendingSync: Boolean = hasPendingWrites
): DiagnosticReportEntity {
    val now = System.currentTimeMillis()
    val createdMs = createdAt?.toDate()?.time ?: now
    val updatedMs = updatedAt?.toDate()?.time ?: now
    return DiagnosticReportEntity(
        id = overrideId,
        userId = overrideUserId,
        equipmentName = equipmentName,
        equipmentModel = equipmentModel,
        systemCategory = systemCategory,
        environmentType = environmentType,
        symptomDescription = symptomDescription,
        attachedMediaSummary = attachedMediaSummary,
        rootCauseTitle = rootCauseTitle,
        severity = severity,
        confidencePercent = confidencePercent,
        technicalAnalysis = technicalAnalysis,
        safetyWarningsJson = encodeStringList(safetyWarnings),
        requiredToolsJson = encodeStringList(requiredTools),
        repairStepsJson = encodeStringList(repairSteps),
        completedStepIndicesJson = encodeLongList(completedStepIndices),
        verificationTest = verificationTest,
        status = status,
        repairDurationMinutes = effectiveRepairMinutes(),
        mechanicVoiceLogsJson = encodeStringList(mechanicVoiceLogs),
        vinNumber = vinNumber,
        odometerMileage = odometerMileage,
        createdAtMillis = createdMs,
        updatedAtMillis = updatedMs,
        hasPendingSync = pendingSync
    )
}

fun TroubleshootingFlowchart.toRoomEntity(
    overrideId: String = id,
    overrideUserId: String = userId,
    pendingSync: Boolean = hasPendingWrites
): FlowchartEntity {
    val now = System.currentTimeMillis()
    val createdMs = createdAt?.toDate()?.time ?: now
    val updatedMs = updatedAt?.toDate()?.time ?: now
    return FlowchartEntity(
        id = overrideId,
        userId = overrideUserId,
        title = title,
        equipmentModel = equipmentModel,
        systemCategory = systemCategory,
        environmentType = environmentType,
        failureMode = failureMode,
        currentNodeId = currentNodeId,
        visitedPathSummary = visitedPathSummary,
        nodesListJson = encodeStringList(nodesList),
        isResolved = isResolved,
        createdAtMillis = createdMs,
        updatedAtMillis = updatedMs,
        hasPendingSync = pendingSync
    )
}

fun EquipmentProfile.toRoomEntity(
    overrideId: String = id,
    overrideUserId: String = userId,
    pendingSync: Boolean = hasPendingWrites
): EquipmentEntity {
    val now = System.currentTimeMillis()
    val createdMs = createdAt?.toDate()?.time ?: now
    val updatedMs = updatedAt?.toDate()?.time ?: now
    return EquipmentEntity(
        id = overrideId,
        userId = overrideUserId,
        name = name,
        modelNumber = modelNumber,
        systemCategory = systemCategory,
        environmentType = environmentType,
        operatingHours = operatingHours,
        notes = notes,
        createdAtMillis = createdMs,
        updatedAtMillis = updatedMs,
        hasPendingSync = pendingSync
    )
}

private fun encodeStringList(list: List<String>): String {
    val array = JSONArray()
    list.forEach { array.put(it) }
    return array.toString()
}

private fun decodeStringList(json: String): List<String> {
    if (json.isBlank()) return emptyList()
    return try {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { idx ->
            array.optString(idx)?.takeIf { it.isNotEmpty() }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun encodeLongList(list: List<Long>): String {
    val array = JSONArray()
    list.forEach { array.put(it) }
    return array.toString()
}

private fun decodeLongList(json: String): List<Long> {
    if (json.isBlank()) return emptyList()
    return try {
        val array = JSONArray(json)
        (0 until array.length()).map { idx -> array.optLong(idx, 0L) }
    } catch (_: Exception) {
        emptyList()
    }
}

@Dao
interface MechSightDao {

    // --- DIAGNOSTIC REPORTS ---

    @Query("SELECT * FROM diagnostic_reports ORDER BY createdAtMillis DESC")
    fun observeAllDiagnostics(): Flow<List<DiagnosticReportEntity>>

    @Query("SELECT * FROM diagnostic_reports ORDER BY createdAtMillis DESC")
    suspend fun getAllDiagnosticsSnapshot(): List<DiagnosticReportEntity>

    @Query("SELECT * FROM diagnostic_reports WHERE id = :id LIMIT 1")
    suspend fun getDiagnosticById(id: String): DiagnosticReportEntity?

    @Query("SELECT COUNT(*) FROM diagnostic_reports")
    suspend fun getDiagnosticsCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiagnostic(entity: DiagnosticReportEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiagnostics(entities: List<DiagnosticReportEntity>)

    @Query("DELETE FROM diagnostic_reports WHERE id = :id")
    suspend fun deleteDiagnosticById(id: String)

    // --- TROUBLESHOOTING FLOWCHARTS ---

    @Query("SELECT * FROM troubleshooting_flowcharts ORDER BY createdAtMillis DESC")
    fun observeAllFlowcharts(): Flow<List<FlowchartEntity>>

    @Query("SELECT * FROM troubleshooting_flowcharts ORDER BY createdAtMillis DESC")
    suspend fun getAllFlowchartsSnapshot(): List<FlowchartEntity>

    @Query("SELECT * FROM troubleshooting_flowcharts WHERE id = :id LIMIT 1")
    suspend fun getFlowchartById(id: String): FlowchartEntity?

    @Query("SELECT COUNT(*) FROM troubleshooting_flowcharts")
    suspend fun getFlowchartsCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlowchart(entity: FlowchartEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlowcharts(entities: List<FlowchartEntity>)

    @Query("DELETE FROM troubleshooting_flowcharts WHERE id = :id")
    suspend fun deleteFlowchartById(id: String)

    // --- EQUIPMENT PROFILES ---

    @Query("SELECT * FROM equipment_profiles ORDER BY createdAtMillis DESC")
    fun observeAllEquipment(): Flow<List<EquipmentEntity>>

    @Query("SELECT * FROM equipment_profiles ORDER BY createdAtMillis DESC")
    suspend fun getAllEquipmentSnapshot(): List<EquipmentEntity>

    @Query("SELECT * FROM equipment_profiles WHERE id = :id LIMIT 1")
    suspend fun getEquipmentById(id: String): EquipmentEntity?

    @Query("SELECT COUNT(*) FROM equipment_profiles")
    suspend fun getEquipmentCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEquipment(entity: EquipmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEquipmentList(entities: List<EquipmentEntity>)

    @Query("DELETE FROM equipment_profiles WHERE id = :id")
    suspend fun deleteEquipmentById(id: String)
}

@Database(
    entities = [
        DiagnosticReportEntity::class,
        FlowchartEntity::class,
        EquipmentEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class MechSightDatabase : RoomDatabase() {
    abstract fun mechSightDao(): MechSightDao

    companion object {
        @Volatile
        private var INSTANCE: MechSightDatabase? = null

        fun getInstance(context: Context): MechSightDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MechSightDatabase::class.java,
                    "mechsight_automotive_offline.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
