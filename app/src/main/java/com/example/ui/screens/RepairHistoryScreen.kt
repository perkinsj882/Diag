package com.example.ui.screens

import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DiagnosticSession
import com.example.data.model.FirestoreCacheStatus
import com.example.data.model.SystemCategory
import com.example.ui.components.CategoryBadge
import com.example.ui.components.DiagnosticReportVoiceLoggerCard
import com.example.ui.components.SeverityChip
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SignalEmerald
import com.example.util.DiagnosticCsvExporter
import com.example.util.DiagnosticPdfExporter
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RepairHistoryScreen(
    diagnosticsList: List<DiagnosticSession>,
    cacheStatus: FirestoreCacheStatus,
    onToggleOfflineFieldMode: (Boolean) -> Unit,
    onSyncAndVerifyCache: () -> Unit,
    onToggleRepairStep: (DiagnosticSession, Int) -> Unit,
    onMarkResolved: (DiagnosticSession) -> Unit,
    onAddVoiceObservation: (DiagnosticSession, String, String, String) -> Unit,
    onDeleteVoiceObservation: (DiagnosticSession, String) -> Unit,
    onSavePdfReport: (DiagnosticSession, Uri) -> Unit,
    onEmailPdfReport: (DiagnosticSession) -> Unit,
    onSaveCsvReports: (Set<String>?, Uri) -> Unit,
    onShareCsvReports: (Set<String>?) -> Unit,
    onDeleteDiagnostic: (String) -> Unit,
    onOpenInDiagnoseLab: (String) -> Unit,
    onSeedReferenceLibrary: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<SystemCategory?>(null) }
    var selectedStatus by remember { mutableStateOf<String?>(null) }
    var expandedSessionId by remember(diagnosticsList.firstOrNull()?.id) {
        mutableStateOf(diagnosticsList.firstOrNull()?.id)
    }
    var pendingPdfSaveSession by remember { mutableStateOf<DiagnosticSession?>(null) }
    var pendingCsvSessionIds by remember { mutableStateOf<Set<String>?>(null) }
    var showCsvSchemaPreview by remember { mutableStateOf(false) }

    val createPdfDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { targetUri: Uri? ->
        val sessionToExport = pendingPdfSaveSession
        if (targetUri != null && sessionToExport != null) {
            onSavePdfReport(sessionToExport, targetUri)
        }
        pendingPdfSaveSession = null
    }

    val createCsvDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { targetUri: Uri? ->
        if (targetUri != null) {
            onSaveCsvReports(pendingCsvSessionIds, targetUri)
        }
        pendingCsvSessionIds = null
    }

    val filteredSessions = remember(diagnosticsList, searchQuery, selectedCategory, selectedStatus) {
        diagnosticsList.filter { session ->
            val matchesQuery = if (searchQuery.isBlank()) {
                true
            } else {
                val q = searchQuery.trim().lowercase()
                session.equipmentName.lowercase().contains(q) ||
                    session.equipmentModel.lowercase().contains(q) ||
                    session.rootCauseTitle.lowercase().contains(q) ||
                    session.symptomDescription.lowercase().contains(q) ||
                    session.environmentType.lowercase().contains(q) ||
                    session.requiredTools.any { it.lowercase().contains(q) } ||
                    session.mechanicVoiceLogs.any { it.lowercase().contains(q) }
            }
            val matchesCategory = selectedCategory == null ||
                session.systemCategory.equals(selectedCategory!!.label, ignoreCase = true)
            val matchesStatus = selectedStatus == null ||
                session.status.equals(selectedStatus, ignoreCase = true)

            matchesQuery && matchesCategory && matchesStatus
        }
    }

    val resolvedCount = diagnosticsList.count { it.status == "RESOLVED" }
    val inProgressCount = diagnosticsList.size - resolvedCount
    val isOfflineActive = cacheStatus.isEffectivelyOffline

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (isOfflineActive) SafetyAmber.copy(alpha = 0.65f) else ElectricCyan.copy(alpha = 0.45f),
                        RoundedCornerShape(16.dp)
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = "Firestore Local Persistence",
                                tint = ElectricCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "FIRESTORE PERSISTENT CACHE (${cacheStatus.cacheAllocationMb} MB)",
                                style = MaterialTheme.typography.labelMedium,
                                color = ElectricCyan
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = when {
                                    cacheStatus.pendingWritesCount > 0 -> Icons.Default.CloudSync
                                    isOfflineActive -> Icons.Default.CloudOff
                                    else -> Icons.Default.CloudDone
                                },
                                contentDescription = "Cache Connectivity State",
                                tint = if (isOfflineActive || cacheStatus.pendingWritesCount > 0) SafetyAmber else SignalEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = when {
                                    cacheStatus.isForcedOfflineMode -> "FIELD OFFLINE MODE"
                                    !cacheStatus.isNetworkAvailable -> "NO SIGNAL • CACHED"
                                    cacheStatus.pendingWritesCount > 0 -> "${cacheStatus.pendingWritesCount} PENDING SYNC"
                                    else -> "OFFLINE READY"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isOfflineActive || cacheStatus.pendingWritesCount > 0) SafetyAmber else SignalEmerald
                            )
                        }
                    }

                    Text(
                        text = "Automotive Service Records & Repair Guides",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "Automotive diagnostic reports, step-by-step repair checklists, and OBD-II decision trees are stored on-device via Room SQLite + Firestore PersistentCache. Toggle Remote Field Offline Mode when working in subterranean garages, roadside breakdowns, or off-road trails.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ArchiveStatPill(
                            label = "CACHED GUIDES",
                            value = "${diagnosticsList.size}",
                            color = ElectricCyan,
                            modifier = Modifier.weight(1f)
                        )
                        ArchiveStatPill(
                            label = "IN PROGRESS",
                            value = "$inProgressCount",
                            color = SafetyAmber,
                            modifier = Modifier.weight(1f)
                        )
                        ArchiveStatPill(
                            label = "RESOLVED",
                            value = "$resolvedCount",
                            color = SignalEmerald,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                    // Remote Field Offline Mode Toggle + Verify/Pre-Cache Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Remote Field Offline Mode",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (cacheStatus.isForcedOfflineMode) {
                                    "Locked to on-device cache • Edits queue locally until reconnected"
                                } else {
                                    "Lock reads & writes to local persistent cache for zero-signal areas"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = cacheStatus.isForcedOfflineMode,
                            onCheckedChange = onToggleOfflineFieldMode,
                            modifier = Modifier.testTag("offline_field_mode_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onSyncAndVerifyCache,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("verify_offline_cache_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Verify Offline Cache",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (cacheStatus.pendingWritesCount > 0) {
                                    "Flush ${cacheStatus.pendingWritesCount} Queued Field Edits & Verify Cache"
                                } else {
                                    "Verify & Pin All Guides in On-Device Cache (${cacheStatus.cachedDiagnosticsCount + cacheStatus.cachedFlowchartsCount} items)"
                                }
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                    // Shop Management System (CSV) Export from Room SQLite
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = SignalEmerald.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .testTag("room_csv_export_card"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                    Icon(
                                        imageVector = Icons.Default.TableChart,
                                        contentDescription = "Room SQLite CSV Export",
                                        tint = SignalEmerald,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "SHOP MANAGEMENT SYSTEM (CSV) EXPORT",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = SignalEmerald,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Room SQLite (diagnostic_reports) • ${filteredSessions.size} of ${diagnosticsList.size} Work Orders Selected",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                FilterChip(
                                    selected = showCsvSchemaPreview,
                                    onClick = { showCsvSchemaPreview = !showCsvSchemaPreview },
                                    label = {
                                        Text(
                                            text = if (showCsvSchemaPreview) "Hide Schema" else "27 SMS Cols",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    modifier = Modifier.testTag("toggle_csv_schema_preview_chip")
                                )
                            }

                            Text(
                                text = "Export locally stored Room diagnostic reports into an RFC-4180 UTF-8 CSV file with VIN, odometer mileage, labor book hours, OBD-II DTCs, repair steps, and voice notes for Tekmetric, Mitchell 1 Manager SE, Shopmonkey, or Excel.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            AnimatedVisibility(visible = showCsvSchemaPreview) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.background
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "CSV HEADER SCHEMA (RFC-4180 UTF-8):",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ElectricCyan,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = DiagnosticCsvExporter.SHOP_SMS_CSV_HEADERS.joinToString(", "),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            val exportTargetIds = remember(filteredSessions, diagnosticsList) {
                                if (filteredSessions.size == diagnosticsList.size) {
                                    null
                                } else {
                                    filteredSessions.map { it.id }.toSet()
                                }
                            }
                            val exportCount = filteredSessions.size

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        pendingCsvSessionIds = exportTargetIds
                                        createCsvDocumentLauncher.launch(
                                            DiagnosticCsvExporter.buildSuggestedBatchFileName(exportCount)
                                        )
                                    },
                                    enabled = exportCount > 0,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("batch_save_csv_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TableChart,
                                        contentDescription = "Save Room Diagnostics to CSV File",
                                        tint = SignalEmerald,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Save CSV ($exportCount)")
                                }

                                Button(
                                    onClick = {
                                        onShareCsvReports(exportTargetIds)
                                    },
                                    enabled = exportCount > 0,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("batch_share_csv_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SignalEmerald,
                                        contentColor = MaterialTheme.colorScheme.onTertiary
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share CSV with Shop Management System",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Share Shop CSV", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Search & Filter Controls
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search cached guides by model, symptom, root cause, or tool...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Repair Guides"
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear Search"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("history_search_input")
                )

                // Status Filter Row
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedStatus == null,
                        onClick = { selectedStatus = null },
                        label = { Text("All Statuses") }
                    )
                    FilterChip(
                        selected = selectedStatus == "IN_PROGRESS",
                        onClick = {
                            selectedStatus = if (selectedStatus == "IN_PROGRESS") null else "IN_PROGRESS"
                        },
                        label = { Text("In Progress ($inProgressCount)") }
                    )
                    FilterChip(
                        selected = selectedStatus == "RESOLVED",
                        onClick = {
                            selectedStatus = if (selectedStatus == "RESOLVED") null else "RESOLVED"
                        },
                        label = { Text("Resolved ($resolvedCount)") }
                    )
                }

                // System Category Filter Row
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("All Domains") }
                    )
                    SystemCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = {
                                selectedCategory = if (selectedCategory == cat) null else cat
                            },
                            label = { Text(cat.badgeCode) }
                        )
                    }
                }
            }
        }

        if (diagnosticsList.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Empty Repair Log",
                            tint = ElectricCyan,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "No Cached Diagnostic Sessions Yet",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Every multimodal diagnosis you run is automatically stored in Firestore's local cache and cloud database. Pre-load sample diagnostic sessions to cache repair guides on this device for immediate offline access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onSeedReferenceLibrary,
                            modifier = Modifier.testTag("history_seed_button")
                        ) {
                            Text("Pre-Cache Sample Diagnostic Guides")
                        }
                    }
                }
            }
        } else if (filteredSessions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No Matching Cached Repair Guides",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Try clearing your search query or domain filters.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filteredSessions, key = { it.id }) { session ->
                val isExpanded = expandedSessionId == session.id
                SavedDiagnosticArchiveCard(
                    session = session,
                    isExpanded = isExpanded,
                    onToggleExpand = {
                        expandedSessionId = if (isExpanded) null else session.id
                    },
                    onToggleStep = { stepIdx -> onToggleRepairStep(session, stepIdx) },
                    onMarkResolved = { onMarkResolved(session) },
                    onAddVoiceObservation = { stepTag, catTag, text ->
                        onAddVoiceObservation(session, stepTag, catTag, text)
                    },
                    onDeleteVoiceObservation = { obsId ->
                        onDeleteVoiceObservation(session, obsId)
                    },
                    onSavePdf = {
                        pendingPdfSaveSession = session
                        createPdfDocumentLauncher.launch(
                            DiagnosticPdfExporter.buildSuggestedFileName(session)
                        )
                    },
                    onEmailPdf = {
                        onEmailPdfReport(session)
                    },
                    onSaveCsv = {
                        pendingCsvSessionIds = setOf(session.id)
                        createCsvDocumentLauncher.launch(
                            DiagnosticCsvExporter.buildSuggestedSingleFileName(session)
                        )
                    },
                    onShareCsv = {
                        onShareCsvReports(setOf(session.id))
                    },
                    onOpenInLab = { onOpenInDiagnoseLab(session.id) },
                    onDelete = { onDeleteDiagnostic(session.id) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ArchiveStatPill(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(vertical = 8.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = color,
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
private fun SavedDiagnosticArchiveCard(
    session: DiagnosticSession,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleStep: (Int) -> Unit,
    onMarkResolved: () -> Unit,
    onAddVoiceObservation: (String, String, String) -> Unit,
    onDeleteVoiceObservation: (String) -> Unit,
    onSavePdf: () -> Unit,
    onEmailPdf: () -> Unit,
    onSaveCsv: () -> Unit,
    onShareCsv: () -> Unit,
    onOpenInLab: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.US) }
    val timestampText = remember(session.updatedAt, session.createdAt) {
        val ts = session.updatedAt ?: session.createdAt
        ts?.toDate()?.let { dateFormat.format(it) } ?: "Cached Locally"
    }

    val totalSteps = session.repairSteps.size.coerceAtLeast(1)
    val completedCount = session.completedStepIndices.size
    val progress = (completedCount.toFloat() / totalSteps.toFloat()).coerceIn(0f, 1f)

    val cacheBadgeLabel = when {
        session.hasPendingWrites -> "QUEUED FOR SYNC"
        session.isFromCache -> "SERVED FROM CACHE"
        else -> "CACHED + SYNCED"
    }
    val cacheBadgeColor = when {
        session.hasPendingWrites -> SafetyAmber
        session.isFromCache -> ElectricCyan
        else -> SignalEmerald
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isExpanded) 1.5.dp else 0.5.dp,
                color = if (isExpanded) ElectricCyan else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(16.dp)
            )
            .testTag("saved_diagnostic_card_${session.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CategoryBadge(categoryLabel = session.systemCategory)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(cacheBadgeColor.copy(alpha = 0.14f))
                            .border(0.5.dp, cacheBadgeColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = cacheBadgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = cacheBadgeColor
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SeverityChip(
                        severity = session.severity,
                        confidencePercent = session.confidencePercent
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse Guide" else "Expand Guide",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = session.rootCauseTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${session.equipmentName} (${session.equipmentModel}) • ${session.environmentType}",
                    style = MaterialTheme.typography.titleSmall,
                    color = ElectricCyan
                )
                if (session.vinNumber.isNotBlank() || session.odometerMileage > 0L) {
                    val numFmt = remember { NumberFormat.getIntegerInstance(Locale.US) }
                    val vinLabel = if (session.vinNumber.isNotBlank()) "VIN: ${session.vinNumber}" else null
                    val miLabel = if (session.odometerMileage > 0L) "Odometer: ${numFmt.format(session.odometerMileage)} mi" else null
                    Text(
                        text = listOfNotNull(vinLabel, miLabel).joinToString("  •  "),
                        style = MaterialTheme.typography.labelSmall,
                        color = SignalEmerald,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = "Symptom: ${session.symptomDescription}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (isExpanded) 6 else 2
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Steps: $completedCount/${session.repairSteps.size} • ${session.status}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (session.status == "RESOLVED") SignalEmerald else SafetyAmber
                )
                Text(
                    text = timestampText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (session.status == "RESOLVED") SignalEmerald else ElectricCyan
            )

            // Expandable Full Step-by-Step Repair Guide
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                    if (session.attachedMediaSummary.isNotBlank()) {
                        Text(
                            text = "Evidence Analyzed: ${session.attachedMediaSummary}",
                            style = MaterialTheme.typography.labelMedium,
                            color = ElectricCyan
                        )
                    }

                    Text(
                        text = session.technicalAnalysis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (session.safetyWarnings.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = SafetyAmber.copy(alpha = 0.14f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Safety Warnings",
                                        tint = SafetyAmber,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "VEHICLE & SHOP SAFETY PRECAUTIONS",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = SafetyAmber,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                session.safetyWarnings.forEach { warn ->
                                    Text(
                                        text = "• $warn",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }

                    if (session.requiredTools.isNotEmpty()) {
                        Text(
                            text = "Required Tools & Parts:",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            session.requiredTools.forEach { tool ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = tool,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "Step-by-Step Repair Procedure:",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    session.repairSteps.forEachIndexed { idx, stepText ->
                        val isDone = session.completedStepIndices.contains(idx.toLong())
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isDone) SignalEmerald.copy(alpha = 0.1f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                )
                                .clickable(enabled = session.status != "RESOLVED") {
                                    onToggleStep(idx)
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Checkbox(
                                checked = isDone,
                                onCheckedChange = {
                                    if (session.status != "RESOLVED") onToggleStep(idx)
                                },
                                enabled = session.status != "RESOLVED"
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "STEP ${idx + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isDone) SignalEmerald else ElectricCyan
                                )
                                Text(
                                    text = stepText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    if (session.verificationTest.isNotBlank()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = ElectricCyan.copy(alpha = 0.1f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "POST-REPAIR VERIFICATION TEST",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ElectricCyan
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = session.verificationTest,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Microphone-Enabled Voice-to-Text Diagnostic Report Logger
                    DiagnosticReportVoiceLoggerCard(
                        session = session,
                        onLogVoiceObservation = onAddVoiceObservation,
                        onDeleteVoiceObservation = onDeleteVoiceObservation,
                        onToggleRepairStep = onToggleStep,
                        onMarkResolved = onMarkResolved,
                        testTagPrefix = "history_report_voice_logger"
                    )

                    // PDF Service Report Export & Email Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onSavePdf,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_pdf_button_${session.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = "Save PDF Service Report",
                                tint = ElectricCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save PDF")
                        }

                        Button(
                            onClick = onEmailPdf,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("email_pdf_button_${session.id}"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = ElectricCyan
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = "Email or Share PDF Service Report",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Email PDF")
                        }
                    }

                    // Shop Management System CSV Export Row (Direct from Room SQLite)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onSaveCsv,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_csv_button_${session.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.TableChart,
                                contentDescription = "Save RO as CSV File",
                                tint = SignalEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save RO CSV")
                        }

                        Button(
                            onClick = onShareCsv,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("share_csv_button_${session.id}"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = SignalEmerald
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share RO CSV with Shop Management System",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share RO CSV")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onOpenInLab,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = "Open in Diagnose Lab",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open in Lab")
                        }

                        if (session.status != "RESOLVED") {
                            Button(
                                onClick = onMarkResolved,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SignalEmerald,
                                    contentColor = MaterialTheme.colorScheme.onTertiary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Mark Resolved",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Resolve")
                            }
                        }

                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Saved Session",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
