package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.local.EquipmentEntity
import com.example.data.local.FlowchartEntity
import com.example.data.local.MechSightDao
import com.example.data.local.MechSightDatabase
import com.example.data.local.toRoomEntity
import com.example.data.model.DiagnosticSession
import com.example.data.model.EquipmentProfile
import com.example.data.model.FlowchartNode
import com.example.data.model.MechanicVoiceObservation
import com.example.data.model.StarterTemplates
import com.example.data.model.TroubleshootingFlowchart
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date
import java.util.UUID

data class CacheVerificationSummary(
    val cachedDiagnosticsCount: Int,
    val cachedFlowchartsCount: Int,
    val cachedEquipmentCount: Int,
    val pendingWritesCount: Int,
    val verifiedAtMillis: Long = System.currentTimeMillis()
)

class MechSightRepository(
    private val db: FirebaseFirestore,
    private val roomDao: MechSightDao? = null
) {

    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ).also { firestore ->
            configurePersistentCache(firestore)
        },
        roomDao = MechSightDatabase.getInstance(context.applicationContext).mechSightDao()
    )

    init {
        configurePersistentCache(db)
    }

    private val auth = Firebase.auth

    private val _isForcedOfflineMode = MutableStateFlow(false)
    val isForcedOfflineMode: StateFlow<Boolean> = _isForcedOfflineMode.asStateFlow()

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun activeOrLocalUserId(fallback: String = "local_field_operator"): String {
        return auth.currentUser?.uid ?: fallback
    }

    private fun isLegacyNonAutomotiveRecord(titleOrName: String, model: String, category: String): Boolean {
        if (category.equals("Computer", ignoreCase = true)) return true
        val combined = "$titleOrName $model".lowercase()
        val legacyMarkers = listOf(
            "siemens", "simatic", "s7-1500", "profinet", "poweredge", "r750",
            "caterpillar", "320d", "festo", "cpx-fb36", "abb m3bp", "haas", "vf-4ss",
            "spindle", "rack server", "workstation", "desktop", "laptop", "plc"
        )
        return legacyMarkers.any { combined.contains(it) }
    }

    /**
     * Ensures the local Room SQLite database is pre-populated with reference industrial
     * equipment, interactive troubleshooting flowcharts, and diagnostic history on initial launch.
     */
    suspend fun ensureLocalRoomSeeded(userId: String = activeOrLocalUserId()) {
        val dao = roomDao ?: return
        val existingDiagCount = dao.getDiagnosticsCount()
        val existingEqCount = dao.getEquipmentCount()
        if (existingDiagCount > 0 || existingEqCount > 0) return

        val baseTime = System.currentTimeMillis()
        val eqEntities = StarterTemplates.starterEquipment.mapIndexed { idx, seed ->
            EquipmentEntity(
                id = "room_eq_${idx + 1}",
                userId = userId,
                name = seed.name,
                modelNumber = seed.modelNumber,
                systemCategory = seed.systemCategory.label,
                environmentType = seed.environmentType,
                operatingHours = seed.operatingHours,
                notes = seed.notes,
                createdAtMillis = baseTime - ((idx + 1) * 3_600_000L),
                updatedAtMillis = baseTime - ((idx + 1) * 1_800_000L),
                hasPendingSync = false
            )
        }
        dao.insertEquipmentList(eqEntities)

        val flowEntities = StarterTemplates.starterFlowcharts.mapIndexed { idx, seed ->
            val firstNodeId = seed.nodes.firstOrNull()?.nodeId ?: "N1"
            FlowchartEntity(
                id = "room_flow_${idx + 1}",
                userId = userId,
                title = seed.title,
                equipmentModel = seed.equipmentModel,
                systemCategory = seed.systemCategory.label,
                environmentType = seed.environmentType,
                failureMode = seed.failureMode,
                currentNodeId = firstNodeId,
                visitedPathSummary = "Initialized at $firstNodeId",
                nodesListJson = org.json.JSONArray(seed.nodes.map { it.toEncodedString() }).toString(),
                isResolved = false,
                createdAtMillis = baseTime - ((idx + 1) * 3_200_000L),
                updatedAtMillis = baseTime - ((idx + 1) * 1_600_000L),
                hasPendingSync = false
            )
        }
        dao.insertFlowcharts(flowEntities)

        val diagEntities = StarterTemplates.starterDiagnostics.mapIndexed { idx, session ->
            val ts = Timestamp(Date(baseTime - ((idx + 1) * 7_200_000L)))
            session.copy(
                id = "room_diag_${idx + 1}",
                userId = userId,
                createdAt = ts,
                updatedAt = ts
            ).toRoomEntity(
                overrideId = "room_diag_${idx + 1}",
                overrideUserId = userId,
                pendingSync = false
            )
        }
        dao.insertDiagnostics(diagEntities)
    }

    /**
     * Toggles offline field mode so technicians can lock operations to local Room SQLite +
     * Firestore persistent cache and sync on demand.
     */
    suspend fun setOfflineFieldMode(enableOffline: Boolean): Result<Unit> = runCatching {
        if (enableOffline) {
            runCatching { db.disableNetwork().await() }
            _isForcedOfflineMode.value = true
        } else {
            runCatching { db.enableNetwork().await() }
            _isForcedOfflineMode.value = false
            withTimeoutOrNull(3_000L) {
                runCatching { db.waitForPendingWrites().await() }
            }
        }
    }

    /**
     * Verifies local Room SQLite database + Firestore PersistentCache counts.
     */
    suspend fun syncAndVerifyLocalCache(userId: String = activeOrLocalUserId()): Result<CacheVerificationSummary> = runCatching {
        val dao = roomDao
        if (dao != null) {
            ensureLocalRoomSeeded(userId)
            val diags = dao.getAllDiagnosticsSnapshot()
            val flows = dao.getAllFlowchartsSnapshot()
            val eqs = dao.getAllEquipmentSnapshot()
            val pending = diags.count { it.hasPendingSync } +
                flows.count { it.hasPendingSync } +
                eqs.count { it.hasPendingSync }
            return@runCatching CacheVerificationSummary(
                cachedDiagnosticsCount = diags.size,
                cachedFlowchartsCount = flows.size,
                cachedEquipmentCount = eqs.size,
                pendingWritesCount = pending,
                verifiedAtMillis = System.currentTimeMillis()
            )
        }

        val diagSnap = db.collection("diagnostics")
            .whereEqualTo("userId", userId)
            .get(Source.CACHE)
            .await()
        val flowSnap = db.collection("flowcharts")
            .whereEqualTo("userId", userId)
            .get(Source.CACHE)
            .await()
        val eqSnap = db.collection("equipment")
            .whereEqualTo("userId", userId)
            .get(Source.CACHE)
            .await()

        val pendingCount = diagSnap.documents.count { it.metadata.hasPendingWrites() } +
            flowSnap.documents.count { it.metadata.hasPendingWrites() } +
            eqSnap.documents.count { it.metadata.hasPendingWrites() }

        CacheVerificationSummary(
            cachedDiagnosticsCount = diagSnap.size(),
            cachedFlowchartsCount = flowSnap.size(),
            cachedEquipmentCount = eqSnap.size(),
            pendingWritesCount = pendingCount,
            verifiedAtMillis = System.currentTimeMillis()
        )
    }

    suspend fun getCachedDiagnostics(userId: String = activeOrLocalUserId()): Result<List<DiagnosticSession>> = runCatching {
        val dao = roomDao
        if (dao != null) {
            ensureLocalRoomSeeded(userId)
            return@runCatching dao.getAllDiagnosticsSnapshot()
                .filterNot { isLegacyNonAutomotiveRecord(it.equipmentName, it.equipmentModel, it.systemCategory) }
                .map { it.toDomainModel() }
        }
        val snap = db.collection("diagnostics")
            .whereEqualTo("userId", userId)
            .get(Source.CACHE)
            .await()
        snap.documents.mapNotNull { doc ->
            doc.toObject(
                DiagnosticSession::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )?.copy(
                isFromCache = true,
                hasPendingWrites = doc.metadata.hasPendingWrites()
            )
        }.filterNot {
            isLegacyNonAutomotiveRecord(it.equipmentName, it.equipmentModel, it.systemCategory)
        }.sortedByDescending { it.createdAt?.seconds ?: 0L }
    }

    /**
     * Queries locally stored diagnostic reports directly from the Room SQLite `diagnostic_reports` table
     * (optionally filtered to [sessionIdsFilter]) for exporting to Shop Management System CSV files.
     */
    suspend fun getRoomDiagnosticsForExport(
        userId: String = activeOrLocalUserId(),
        sessionIdsFilter: Set<String>? = null
    ): Result<List<DiagnosticSession>> = runCatching {
        val allCached = getCachedDiagnostics(userId).getOrThrow()
        if (sessionIdsFilter.isNullOrEmpty()) {
            allCached
        } else {
            allCached.filter { sessionIdsFilter.contains(it.id) }.ifEmpty { allCached }
        }
    }

    suspend fun getCachedFlowcharts(userId: String = activeOrLocalUserId()): Result<List<TroubleshootingFlowchart>> = runCatching {
        val dao = roomDao
        if (dao != null) {
            ensureLocalRoomSeeded(userId)
            return@runCatching dao.getAllFlowchartsSnapshot().map { it.toDomainModel() }
        }
        val snap = db.collection("flowcharts")
            .whereEqualTo("userId", userId)
            .get(Source.CACHE)
            .await()
        snap.documents.mapNotNull { doc ->
            doc.toObject(
                TroubleshootingFlowchart::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )?.copy(
                isFromCache = true,
                hasPendingWrites = doc.metadata.hasPendingWrites()
            )
        }.sortedByDescending { it.createdAt?.seconds ?: 0L }
    }

    // --- EQUIPMENT PROFILES ---

    fun observeEquipment(userId: String = activeOrLocalUserId()): Flow<List<EquipmentProfile>> = flow {
        val dao = roomDao
        if (dao != null) {
            ensureLocalRoomSeeded(userId)
            coroutineScope {
                if (auth.currentUser != null && !_isForcedOfflineMode.value) {
                    launch {
                        runCatching {
                            db.collection("equipment")
                                .whereEqualTo("userId", auth.currentUser!!.uid)
                                .snapshots(MetadataChanges.INCLUDE)
                                .collect { snapshot ->
                                    val entities = snapshot.documents.mapNotNull { doc ->
                                        val obj = doc.toObject(
                                            EquipmentProfile::class.java,
                                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                                        ) ?: return@mapNotNull null
                                        if (isLegacyNonAutomotiveRecord(obj.name, obj.modelNumber, obj.systemCategory)) {
                                            return@mapNotNull null
                                        }
                                        obj.toRoomEntity(
                                            overrideId = doc.id,
                                            overrideUserId = auth.currentUser?.uid ?: userId,
                                            pendingSync = doc.metadata.hasPendingWrites()
                                        )
                                    }
                                    if (entities.isNotEmpty()) {
                                        dao.insertEquipmentList(entities)
                                    }
                                }
                        }
                    }
                }
                emitAll(
                    dao.observeAllEquipment().map { list ->
                        list.map { it.toDomainModel() }
                    }
                )
            }
            return@flow
        }

        val path = "equipment"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", userId)
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(
                            EquipmentProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )?.copy(
                            isFromCache = doc.metadata.isFromCache,
                            hasPendingWrites = doc.metadata.hasPendingWrites()
                        )
                    }.sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun createEquipment(
        name: String,
        modelNumber: String,
        systemCategory: String,
        environmentType: String,
        operatingHours: Long,
        notes: String
    ): Result<String> = runCatching {
        val dao = roomDao
        if (dao != null) {
            val uid = activeOrLocalUserId()
            val generatedId = "eq_${UUID.randomUUID().toString().take(12)}"
            val now = System.currentTimeMillis()
            val entity = EquipmentEntity(
                id = generatedId,
                userId = uid,
                name = name.trim().take(120),
                modelNumber = modelNumber.trim().take(100),
                systemCategory = systemCategory,
                environmentType = environmentType.trim().take(80),
                operatingHours = operatingHours.coerceIn(0L, 10_000_000L),
                notes = notes.trim().take(1000),
                createdAtMillis = now,
                updatedAtMillis = now,
                hasPendingSync = auth.currentUser == null || _isForcedOfflineMode.value
            )
            dao.insertEquipment(entity)

            if (auth.currentUser != null && !_isForcedOfflineMode.value) {
                runCatching {
                    val docRef = db.collection("equipment").document(generatedId)
                    val payload = mapOf(
                        "userId" to auth.currentUser!!.uid,
                        "name" to entity.name,
                        "modelNumber" to entity.modelNumber,
                        "systemCategory" to entity.systemCategory,
                        "environmentType" to entity.environmentType,
                        "operatingHours" to entity.operatingHours,
                        "notes" to entity.notes,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    awaitWriteWithOfflineCache(docRef.set(payload), docRef.path, OperationType.CREATE)
                    dao.insertEquipment(entity.copy(hasPendingSync = false))
                }
            }
            return@runCatching generatedId
        }

        val uid = requireUserId()
        val collectionRef = db.collection("equipment")
        val docRef = collectionRef.document()
        val payload = mapOf(
            "userId" to uid,
            "name" to name.trim().take(120),
            "modelNumber" to modelNumber.trim().take(100),
            "systemCategory" to systemCategory,
            "environmentType" to environmentType.trim().take(80),
            "operatingHours" to operatingHours.coerceIn(0L, 10_000_000L),
            "notes" to notes.trim().take(1000),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            awaitWriteWithOfflineCache(docRef.set(payload), docRef.path, OperationType.CREATE)
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun getEquipmentById(equipmentId: String): Result<EquipmentProfile> = runCatching {
        val dao = roomDao
        if (dao != null) {
            val local = dao.getEquipmentById(equipmentId)?.toDomainModel()
            if (local != null) return@runCatching local
        }
        val docRef = db.collection("equipment").document(equipmentId)
        try {
            val snap = fetchDocumentCacheFallback(docRef)
            snap.toObject(
                EquipmentProfile::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )?.copy(
                isFromCache = snap.metadata.isFromCache,
                hasPendingWrites = snap.metadata.hasPendingWrites()
            ) ?: throw NoSuchElementException("Equipment $equipmentId not found")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun deleteEquipment(equipmentId: String): Result<Unit> = runCatching {
        val dao = roomDao
        if (dao != null) {
            dao.deleteEquipmentById(equipmentId)
            if (auth.currentUser != null && !_isForcedOfflineMode.value) {
                runCatching {
                    val docRef = db.collection("equipment").document(equipmentId)
                    awaitWriteWithOfflineCache(docRef.delete(), docRef.path, OperationType.DELETE)
                }
            }
            return@runCatching
        }

        val docRef = db.collection("equipment").document(equipmentId)
        try {
            awaitWriteWithOfflineCache(docRef.delete(), docRef.path, OperationType.DELETE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- DIAGNOSTIC SESSIONS ---

    fun observeDiagnostics(userId: String = activeOrLocalUserId()): Flow<List<DiagnosticSession>> = flow {
        val dao = roomDao
        if (dao != null) {
            ensureLocalRoomSeeded(userId)
            coroutineScope {
                if (auth.currentUser != null && !_isForcedOfflineMode.value) {
                    launch {
                        runCatching {
                            db.collection("diagnostics")
                                .whereEqualTo("userId", auth.currentUser!!.uid)
                                .snapshots(MetadataChanges.INCLUDE)
                                .collect { snapshot ->
                                    val entities = snapshot.documents.mapNotNull { doc ->
                                        val existing = dao.getDiagnosticById(doc.id)
                                        val obj = doc.toObject(
                                            DiagnosticSession::class.java,
                                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                                        ) ?: return@mapNotNull null
                                        if (isLegacyNonAutomotiveRecord(obj.equipmentName, obj.equipmentModel, obj.systemCategory)) {
                                            return@mapNotNull null
                                        }
                                        obj.copy(
                                            repairDurationMinutes = existing?.repairDurationMinutes ?: 0,
                                            mechanicVoiceLogs = existing?.toDomainModel()?.mechanicVoiceLogs.orEmpty(),
                                            vinNumber = existing?.vinNumber.orEmpty(),
                                            odometerMileage = existing?.odometerMileage ?: 0L
                                        ).toRoomEntity(
                                            overrideId = doc.id,
                                            overrideUserId = auth.currentUser?.uid ?: userId,
                                            pendingSync = doc.metadata.hasPendingWrites()
                                        )
                                    }
                                    if (entities.isNotEmpty()) {
                                        dao.insertDiagnostics(entities)
                                    }
                                }
                        }
                    }
                }
                emitAll(
                    dao.observeAllDiagnostics().map { list ->
                        list.map { it.toDomainModel() }
                    }
                )
            }
            return@flow
        }

        val path = "diagnostics"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", userId)
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(
                            DiagnosticSession::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )?.copy(
                            isFromCache = doc.metadata.isFromCache,
                            hasPendingWrites = doc.metadata.hasPendingWrites()
                        )
                    }.sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun createDiagnosticSession(session: DiagnosticSession): Result<String> = runCatching {
        val safeSteps = session.repairSteps.map { it.take(800) }.take(20)
            .ifEmpty { listOf("Inspect component and verify operating parameters.") }
        val normalizedSeverity = session.severity.uppercase().let {
            if (it in listOf("CRITICAL", "HIGH", "MODERATE", "LOW")) it else "HIGH"
        }
        val normalizedStatus = if (session.status == "RESOLVED") "RESOLVED" else "IN_PROGRESS"

        val dao = roomDao
        if (dao != null) {
            val uid = activeOrLocalUserId(session.userId.ifBlank { "local_field_operator" })
            val generatedId = session.id.ifBlank { "diag_${UUID.randomUUID().toString().take(12)}" }
            val nowTs = Timestamp.now()
            val cleanedSession = session.copy(
                id = generatedId,
                userId = uid,
                equipmentName = session.equipmentName.trim().ifBlank { "Automotive Vehicle" }.take(120),
                equipmentModel = session.equipmentModel.trim().ifBlank { "Standard Vehicle" }.take(100),
                environmentType = session.environmentType.trim().ifBlank { "Automotive Service Bay" }.take(80),
                symptomDescription = session.symptomDescription.trim().ifBlank { "Unspecified symptom" }.take(3000),
                attachedMediaSummary = session.attachedMediaSummary.take(500),
                rootCauseTitle = session.rootCauseTitle.trim().ifBlank { "Potential Component Fault" }.take(250),
                severity = normalizedSeverity,
                confidencePercent = session.confidencePercent.coerceIn(0L, 100L),
                technicalAnalysis = session.technicalAnalysis.trim().ifBlank { "Detailed inspection required." }.take(5000),
                safetyWarnings = session.safetyWarnings.map { it.take(400) }.take(10),
                requiredTools = session.requiredTools.map { it.take(300) }.take(15),
                repairSteps = safeSteps,
                completedStepIndices = session.completedStepIndices.take(20),
                verificationTest = session.verificationTest.take(1500),
                status = normalizedStatus,
                createdAt = session.createdAt ?: nowTs,
                updatedAt = nowTs
            )
            val entity = cleanedSession.toRoomEntity(
                overrideId = generatedId,
                overrideUserId = uid,
                pendingSync = auth.currentUser == null || _isForcedOfflineMode.value
            )
            dao.insertDiagnostic(entity)

            if (auth.currentUser != null && !_isForcedOfflineMode.value) {
                runCatching {
                    val docRef = db.collection("diagnostics").document(generatedId)
                    val payload = mapOf(
                        "userId" to auth.currentUser!!.uid,
                        "equipmentName" to cleanedSession.equipmentName,
                        "equipmentModel" to cleanedSession.equipmentModel,
                        "systemCategory" to cleanedSession.systemCategory,
                        "environmentType" to cleanedSession.environmentType,
                        "symptomDescription" to cleanedSession.symptomDescription,
                        "attachedMediaSummary" to cleanedSession.attachedMediaSummary,
                        "rootCauseTitle" to cleanedSession.rootCauseTitle,
                        "severity" to cleanedSession.severity,
                        "confidencePercent" to cleanedSession.confidencePercent,
                        "technicalAnalysis" to cleanedSession.technicalAnalysis,
                        "safetyWarnings" to cleanedSession.safetyWarnings,
                        "requiredTools" to cleanedSession.requiredTools,
                        "repairSteps" to cleanedSession.repairSteps,
                        "completedStepIndices" to cleanedSession.completedStepIndices,
                        "verificationTest" to cleanedSession.verificationTest,
                        "status" to cleanedSession.status,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    awaitWriteWithOfflineCache(docRef.set(payload), docRef.path, OperationType.CREATE)
                    dao.insertDiagnostic(entity.copy(hasPendingSync = false))
                }
            }
            return@runCatching generatedId
        }

        val uid = requireUserId()
        val docRef = db.collection("diagnostics").document()
        val payload = mapOf(
            "userId" to uid,
            "equipmentName" to session.equipmentName.trim().ifBlank { "Automotive Vehicle" }.take(120),
            "equipmentModel" to session.equipmentModel.trim().ifBlank { "Standard Vehicle" }.take(100),
            "systemCategory" to session.systemCategory,
            "environmentType" to session.environmentType.trim().ifBlank { "Automotive Service Bay" }.take(80),
            "symptomDescription" to session.symptomDescription.trim().ifBlank { "Unspecified symptom" }.take(3000),
            "attachedMediaSummary" to session.attachedMediaSummary.take(500),
            "rootCauseTitle" to session.rootCauseTitle.trim().ifBlank { "Potential Component Fault" }.take(250),
            "severity" to normalizedSeverity,
            "confidencePercent" to session.confidencePercent.coerceIn(0L, 100L),
            "technicalAnalysis" to session.technicalAnalysis.trim().ifBlank { "Detailed inspection required." }.take(5000),
            "safetyWarnings" to session.safetyWarnings.map { it.take(400) }.take(10),
            "requiredTools" to session.requiredTools.map { it.take(300) }.take(15),
            "repairSteps" to safeSteps,
            "completedStepIndices" to session.completedStepIndices.take(20),
            "verificationTest" to session.verificationTest.take(1500),
            "status" to normalizedStatus,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            awaitWriteWithOfflineCache(docRef.set(payload), docRef.path, OperationType.CREATE)
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun getDiagnosticById(diagnosticId: String): Result<DiagnosticSession> = runCatching {
        val dao = roomDao
        if (dao != null) {
            val local = dao.getDiagnosticById(diagnosticId)?.toDomainModel()
            if (local != null) return@runCatching local
        }
        val docRef = db.collection("diagnostics").document(diagnosticId)
        try {
            val snap = fetchDocumentCacheFallback(docRef)
            snap.toObject(
                DiagnosticSession::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )?.copy(
                isFromCache = snap.metadata.isFromCache,
                hasPendingWrites = snap.metadata.hasPendingWrites()
            ) ?: throw NoSuchElementException("Diagnostic session $diagnosticId not found")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun updateDiagnosticProgress(
        diagnosticId: String,
        completedStepIndices: List<Long>,
        status: String,
        repairDurationMinutes: Int? = null
    ): Result<Unit> = runCatching {
        val cleanIndices = completedStepIndices.distinct().take(20)
        val cleanStatus = if (status == "RESOLVED") "RESOLVED" else "IN_PROGRESS"

        val dao = roomDao
        if (dao != null) {
            val existing = dao.getDiagnosticById(diagnosticId)?.toDomainModel()
            if (existing != null) {
                val updatedSession = existing.copy(
                    completedStepIndices = cleanIndices,
                    status = cleanStatus,
                    repairDurationMinutes = repairDurationMinutes ?: existing.effectiveRepairMinutes(),
                    updatedAt = Timestamp.now()
                )
                dao.insertDiagnostic(
                    updatedSession.toRoomEntity(
                        overrideId = diagnosticId,
                        overrideUserId = existing.userId,
                        pendingSync = auth.currentUser == null || _isForcedOfflineMode.value
                    )
                )
            }
            if (auth.currentUser != null && !_isForcedOfflineMode.value) {
                runCatching {
                    val docRef = db.collection("diagnostics").document(diagnosticId)
                    val updates = mapOf(
                        "completedStepIndices" to cleanIndices,
                        "status" to cleanStatus,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    awaitWriteWithOfflineCache(docRef.update(updates), docRef.path, OperationType.UPDATE)
                }
            }
            return@runCatching
        }

        requireUserId()
        val docRef = db.collection("diagnostics").document(diagnosticId)
        val updates = mapOf(
            "completedStepIndices" to cleanIndices,
            "status" to cleanStatus,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            awaitWriteWithOfflineCache(docRef.update(updates), docRef.path, OperationType.UPDATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun appendVoiceObservationToReport(
        diagnosticId: String,
        observation: MechanicVoiceObservation
    ): Result<Unit> = runCatching {
        val dao = roomDao ?: return@runCatching
        val existing = dao.getDiagnosticById(diagnosticId)?.toDomainModel()
            ?: throw NoSuchElementException("Diagnostic session $diagnosticId not found")
        val updatedLogs = (existing.mechanicVoiceLogs + observation.toEncodedString()).takeLast(50)
        val updatedSession = existing.copy(
            mechanicVoiceLogs = updatedLogs,
            updatedAt = Timestamp.now()
        )
        dao.insertDiagnostic(
            updatedSession.toRoomEntity(
                overrideId = diagnosticId,
                overrideUserId = existing.userId,
                pendingSync = existing.hasPendingWrites
            )
        )
    }

    suspend fun deleteVoiceObservationFromReport(
        diagnosticId: String,
        observationId: String
    ): Result<Unit> = runCatching {
        val dao = roomDao ?: return@runCatching
        val existing = dao.getDiagnosticById(diagnosticId)?.toDomainModel()
            ?: return@runCatching
        val updatedLogs = existing.parsedVoiceObservations()
            .filterNot { it.id == observationId }
            .map { it.toEncodedString() }
        val updatedSession = existing.copy(
            mechanicVoiceLogs = updatedLogs,
            updatedAt = Timestamp.now()
        )
        dao.insertDiagnostic(
            updatedSession.toRoomEntity(
                overrideId = diagnosticId,
                overrideUserId = existing.userId,
                pendingSync = existing.hasPendingWrites
            )
        )
    }

    suspend fun deleteDiagnostic(diagnosticId: String): Result<Unit> = runCatching {
        val dao = roomDao
        if (dao != null) {
            dao.deleteDiagnosticById(diagnosticId)
            if (auth.currentUser != null && !_isForcedOfflineMode.value) {
                runCatching {
                    val docRef = db.collection("diagnostics").document(diagnosticId)
                    awaitWriteWithOfflineCache(docRef.delete(), docRef.path, OperationType.DELETE)
                }
            }
            return@runCatching
        }

        val docRef = db.collection("diagnostics").document(diagnosticId)
        try {
            awaitWriteWithOfflineCache(docRef.delete(), docRef.path, OperationType.DELETE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- INTERACTIVE TROUBLESHOOTING FLOWCHARTS ---

    fun observeFlowcharts(userId: String = activeOrLocalUserId()): Flow<List<TroubleshootingFlowchart>> = flow {
        val dao = roomDao
        if (dao != null) {
            ensureLocalRoomSeeded(userId)
            coroutineScope {
                if (auth.currentUser != null && !_isForcedOfflineMode.value) {
                    launch {
                        runCatching {
                            db.collection("flowcharts")
                                .whereEqualTo("userId", auth.currentUser!!.uid)
                                .snapshots(MetadataChanges.INCLUDE)
                                .collect { snapshot ->
                                    val entities = snapshot.documents.mapNotNull { doc ->
                                        val obj = doc.toObject(
                                            TroubleshootingFlowchart::class.java,
                                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                                        ) ?: return@mapNotNull null
                                        if (isLegacyNonAutomotiveRecord(obj.title, obj.equipmentModel, obj.systemCategory)) {
                                            return@mapNotNull null
                                        }
                                        obj.toRoomEntity(
                                            overrideId = doc.id,
                                            overrideUserId = auth.currentUser?.uid ?: userId,
                                            pendingSync = doc.metadata.hasPendingWrites()
                                        )
                                    }
                                    if (entities.isNotEmpty()) {
                                        dao.insertFlowcharts(entities)
                                    }
                                }
                        }
                    }
                }
                emitAll(
                    dao.observeAllFlowcharts().map { list ->
                        list.map { it.toDomainModel() }
                    }
                )
            }
            return@flow
        }

        val path = "flowcharts"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", userId)
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(
                            TroubleshootingFlowchart::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )?.copy(
                            isFromCache = doc.metadata.isFromCache,
                            hasPendingWrites = doc.metadata.hasPendingWrites()
                        )
                    }.sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun createFlowchart(
        title: String,
        equipmentModel: String,
        systemCategory: String,
        environmentType: String,
        failureMode: String,
        nodes: List<FlowchartNode>
    ): Result<String> = runCatching {
        val encodedNodes = nodes.map { it.toEncodedString() }.take(25).ifEmpty {
            listOf(
                FlowchartNode(
                    nodeId = "N1",
                    nodeType = "CONCLUSION",
                    prompt = "Inspect primary power, fluid pressure, and fault logs.",
                    observationHint = "Check main breaker and system gauge",
                    recommendedAction = "Restore nominal supply and re-test"
                ).toEncodedString()
            )
        }
        val firstNodeId = nodes.firstOrNull()?.nodeId ?: "N1"

        val dao = roomDao
        if (dao != null) {
            val uid = activeOrLocalUserId()
            val generatedId = "flow_${UUID.randomUUID().toString().take(12)}"
            val now = System.currentTimeMillis()
            val entity = FlowchartEntity(
                id = generatedId,
                userId = uid,
                title = title.trim().ifBlank { "Automotive Troubleshooting Flowchart" }.take(160),
                equipmentModel = equipmentModel.trim().ifBlank { "Automotive Subsystem" }.take(100),
                systemCategory = systemCategory,
                environmentType = environmentType.trim().ifBlank { "Automotive Service Bay" }.take(80),
                failureMode = failureMode.trim().ifBlank { "Common Failure" }.take(250),
                currentNodeId = firstNodeId.take(64),
                visitedPathSummary = "Initialized at $firstNodeId",
                nodesListJson = org.json.JSONArray(encodedNodes).toString(),
                isResolved = false,
                createdAtMillis = now,
                updatedAtMillis = now,
                hasPendingSync = auth.currentUser == null || _isForcedOfflineMode.value
            )
            dao.insertFlowchart(entity)

            if (auth.currentUser != null && !_isForcedOfflineMode.value) {
                runCatching {
                    val docRef = db.collection("flowcharts").document(generatedId)
                    val payload = mapOf(
                        "userId" to auth.currentUser!!.uid,
                        "title" to entity.title,
                        "equipmentModel" to entity.equipmentModel,
                        "systemCategory" to entity.systemCategory,
                        "environmentType" to entity.environmentType,
                        "failureMode" to entity.failureMode,
                        "currentNodeId" to entity.currentNodeId,
                        "visitedPathSummary" to entity.visitedPathSummary,
                        "nodesList" to encodedNodes,
                        "isResolved" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    awaitWriteWithOfflineCache(docRef.set(payload), docRef.path, OperationType.CREATE)
                    dao.insertFlowchart(entity.copy(hasPendingSync = false))
                }
            }
            return@runCatching generatedId
        }

        val uid = requireUserId()
        val docRef = db.collection("flowcharts").document()
        val payload = mapOf(
            "userId" to uid,
            "title" to title.trim().ifBlank { "Troubleshooting Flowchart" }.take(160),
            "equipmentModel" to equipmentModel.trim().ifBlank { "General System" }.take(100),
            "systemCategory" to systemCategory,
            "environmentType" to environmentType.trim().ifBlank { "Industrial" }.take(80),
            "failureMode" to failureMode.trim().ifBlank { "Common Failure" }.take(250),
            "currentNodeId" to firstNodeId.take(64),
            "visitedPathSummary" to "Initialized at $firstNodeId",
            "nodesList" to encodedNodes,
            "isResolved" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            awaitWriteWithOfflineCache(docRef.set(payload), docRef.path, OperationType.CREATE)
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateFlowchartWalkthrough(
        flowchartId: String,
        currentNodeId: String,
        visitedPathSummary: String,
        isResolved: Boolean
    ): Result<Unit> = runCatching {
        val dao = roomDao
        if (dao != null) {
            val existing = dao.getFlowchartById(flowchartId)
            if (existing != null) {
                dao.insertFlowchart(
                    existing.copy(
                        currentNodeId = currentNodeId.trim().take(64),
                        visitedPathSummary = visitedPathSummary.trim().take(2000),
                        isResolved = isResolved,
                        updatedAtMillis = System.currentTimeMillis(),
                        hasPendingSync = auth.currentUser == null || _isForcedOfflineMode.value
                    )
                )
            }
            if (auth.currentUser != null && !_isForcedOfflineMode.value) {
                runCatching {
                    val docRef = db.collection("flowcharts").document(flowchartId)
                    val updates = mapOf(
                        "currentNodeId" to currentNodeId.trim().take(64),
                        "visitedPathSummary" to visitedPathSummary.trim().take(2000),
                        "isResolved" to isResolved,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    awaitWriteWithOfflineCache(docRef.update(updates), docRef.path, OperationType.UPDATE)
                }
            }
            return@runCatching
        }

        requireUserId()
        val docRef = db.collection("flowcharts").document(flowchartId)
        val updates = mapOf(
            "currentNodeId" to currentNodeId.trim().take(64),
            "visitedPathSummary" to visitedPathSummary.trim().take(2000),
            "isResolved" to isResolved,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            awaitWriteWithOfflineCache(docRef.update(updates), docRef.path, OperationType.UPDATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteFlowchart(flowchartId: String): Result<Unit> = runCatching {
        val dao = roomDao
        if (dao != null) {
            dao.deleteFlowchartById(flowchartId)
            if (auth.currentUser != null && !_isForcedOfflineMode.value) {
                runCatching {
                    val docRef = db.collection("flowcharts").document(flowchartId)
                    awaitWriteWithOfflineCache(docRef.delete(), docRef.path, OperationType.DELETE)
                }
            }
            return@runCatching
        }

        val docRef = db.collection("flowcharts").document(flowchartId)
        try {
            awaitWriteWithOfflineCache(docRef.delete(), docRef.path, OperationType.DELETE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    private suspend fun fetchDocumentCacheFallback(
        docRef: com.google.firebase.firestore.DocumentReference
    ): DocumentSnapshot {
        if (_isForcedOfflineMode.value) {
            return docRef.get(Source.CACHE).await()
        }
        return try {
            val snap = withTimeoutOrNull(3_500L) {
                docRef.get(Source.DEFAULT).await()
            }
            snap ?: docRef.get(Source.CACHE).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val cacheSnap = runCatching { docRef.get(Source.CACHE).await() }.getOrNull()
            if (cacheSnap != null && cacheSnap.exists()) {
                cacheSnap
            } else {
                throw e
            }
        }
    }

    private suspend fun awaitWriteWithOfflineCache(
        task: Task<Void>,
        path: String,
        operationType: OperationType
    ) {
        task.addOnFailureListener { error ->
            handleFirestoreError(error, operationType, path)
        }
        val timeoutMs = if (_isForcedOfflineMode.value) 120L else 3_500L
        withTimeoutOrNull(timeoutMs) {
            task.await()
        }
    }

    companion object {
        const val PERSISTENT_CACHE_SIZE_MB = 100
        private const val PERSISTENT_CACHE_SIZE_BYTES = 100L * 1024L * 1024L

        fun configurePersistentCache(firestore: FirebaseFirestore) {
            try {
                val settings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(
                        PersistentCacheSettings.newBuilder()
                            .setSizeBytes(PERSISTENT_CACHE_SIZE_BYTES)
                            .build()
                    )
                    .build()
                firestore.firestoreSettings = settings
            } catch (_: Exception) {
                // Firestore settings already locked after initial operation; safe to ignore
            }
        }
    }
}
