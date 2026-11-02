package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.DiagnosticSession
import com.example.data.model.EquipmentProfile
import com.example.data.model.FlowchartNode
import com.example.data.model.TroubleshootingFlowchart
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

data class CacheVerificationSummary(
    val cachedDiagnosticsCount: Int,
    val cachedFlowchartsCount: Int,
    val cachedEquipmentCount: Int,
    val pendingWritesCount: Int,
    val verifiedAtMillis: Long = System.currentTimeMillis()
)

class MechSightRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ).also { firestore ->
            configurePersistentCache(firestore)
        }
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

    /**
     * Toggles Firestore network access so technicians in remote or air-gapped environments
     * can work strictly against the on-device persistent cache and sync on demand.
     */
    suspend fun setOfflineFieldMode(enableOffline: Boolean): Result<Unit> = runCatching {
        if (enableOffline) {
            db.disableNetwork().await()
            _isForcedOfflineMode.value = true
        } else {
            db.enableNetwork().await()
            _isForcedOfflineMode.value = false
            withTimeoutOrNull(4_000L) {
                db.waitForPendingWrites().await()
            }
        }
    }

    /**
     * Warms up the local persistent cache (if online) and verifies exact document counts
     * available in Firestore's on-device Source.CACHE.
     */
    suspend fun syncAndVerifyLocalCache(userId: String = requireUserId()): Result<CacheVerificationSummary> = runCatching {
        if (!_isForcedOfflineMode.value) {
            withTimeoutOrNull(4_000L) {
                db.waitForPendingWrites().await()
            }
            // Attempt server-backed fetch to warm local cache if connectivity permits
            withTimeoutOrNull(4_000L) {
                runCatching {
                    db.collection("diagnostics").whereEqualTo("userId", userId).get(Source.DEFAULT).await()
                    db.collection("flowcharts").whereEqualTo("userId", userId).get(Source.DEFAULT).await()
                    db.collection("equipment").whereEqualTo("userId", userId).get(Source.DEFAULT).await()
                }
            }
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

    /**
     * Explicitly reads diagnostic sessions directly from Firestore's on-device cache (Source.CACHE).
     */
    suspend fun getCachedDiagnostics(userId: String = requireUserId()): Result<List<DiagnosticSession>> = runCatching {
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
        }.sortedByDescending { it.createdAt?.seconds ?: 0L }
    }

    /**
     * Explicitly reads interactive flowcharts directly from Firestore's on-device cache (Source.CACHE).
     */
    suspend fun getCachedFlowcharts(userId: String = requireUserId()): Result<List<TroubleshootingFlowchart>> = runCatching {
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

    fun observeEquipment(userId: String = requireUserId()): Flow<List<EquipmentProfile>> = flow {
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

    fun observeDiagnostics(userId: String = requireUserId()): Flow<List<DiagnosticSession>> = flow {
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
        val uid = requireUserId()
        val docRef = db.collection("diagnostics").document()
        val safeSteps = session.repairSteps.map { it.take(800) }.take(20)
            .ifEmpty { listOf("Inspect component and verify operating parameters.") }
        val payload = mapOf(
            "userId" to uid,
            "equipmentName" to session.equipmentName.trim().ifBlank { "General Equipment" }.take(120),
            "equipmentModel" to session.equipmentModel.trim().ifBlank { "Standard Model" }.take(100),
            "systemCategory" to session.systemCategory,
            "environmentType" to session.environmentType.trim().ifBlank { "Industrial" }.take(80),
            "symptomDescription" to session.symptomDescription.trim().ifBlank { "Unspecified symptom" }.take(3000),
            "attachedMediaSummary" to session.attachedMediaSummary.take(500),
            "rootCauseTitle" to session.rootCauseTitle.trim().ifBlank { "Potential Component Fault" }.take(250),
            "severity" to session.severity.uppercase().let {
                if (it in listOf("CRITICAL", "HIGH", "MODERATE", "LOW")) it else "HIGH"
            },
            "confidencePercent" to session.confidencePercent.coerceIn(0L, 100L),
            "technicalAnalysis" to session.technicalAnalysis.trim().ifBlank { "Detailed inspection required." }.take(5000),
            "safetyWarnings" to session.safetyWarnings.map { it.take(400) }.take(10),
            "requiredTools" to session.requiredTools.map { it.take(300) }.take(15),
            "repairSteps" to safeSteps,
            "completedStepIndices" to session.completedStepIndices.take(20),
            "verificationTest" to session.verificationTest.take(1500),
            "status" to if (session.status == "RESOLVED") "RESOLVED" else "IN_PROGRESS",
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
        status: String
    ): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("diagnostics").document(diagnosticId)
        val updates = mapOf(
            "completedStepIndices" to completedStepIndices.distinct().take(20),
            "status" to if (status == "RESOLVED") "RESOLVED" else "IN_PROGRESS",
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

    suspend fun deleteDiagnostic(diagnosticId: String): Result<Unit> = runCatching {
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

    fun observeFlowcharts(userId: String = requireUserId()): Flow<List<TroubleshootingFlowchart>> = flow {
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
        val uid = requireUserId()
        val docRef = db.collection("flowcharts").document()
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

    /**
     * Fetches a document using Source.CACHE when in Forced Offline Mode, or tries Source.DEFAULT
     * first and automatically falls back to Source.CACHE when network connectivity is unavailable.
     */
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
            // Fall back to local cache if offline/network error, otherwise rethrow (e.g., PERMISSION_DENIED)
            val cacheSnap = runCatching { docRef.get(Source.CACHE).await() }.getOrNull()
            if (cacheSnap != null && cacheSnap.exists()) {
                cacheSnap
            } else {
                throw e
            }
        }
    }

    /**
     * Awaits a Firestore write task with offline awareness.
     * - When online, waits up to 3.5 seconds for server acknowledgment (propagating any security rule errors immediately).
     * - When offline or in Remote Field Offline Mode, Firestore commits the mutation to its local
     *   PersistentCache immediately and queues the write for automatic sync when connectivity returns.
     */
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
