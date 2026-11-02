package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.EquipmentProfile
import com.example.data.model.FirestoreCacheStatus
import com.example.data.repository.MechSightRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.screens.DiagnoseLabScreen
import com.example.ui.screens.EquipmentFleetScreen
import com.example.ui.screens.FlowchartsScreen
import com.example.ui.screens.RepairHistoryScreen
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SignalEmerald
import com.example.util.NetworkConnectivityObserver
import com.example.util.configureAppCheck
import com.example.viewmodel.DataState
import com.example.viewmodel.MechSightViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory App Check configuration with launch intent before UI or ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AppNavigation()
            }
        }
    }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(
            onAuthSuccess = { /* AuthStateListener updates currentUser automatically */ }
        )
    } else {
        MechSightAuthenticatedApp(
            currentUserId = user.uid,
            userEmail = user.email ?: "Operator"
        )
    }
}

private enum class MechSightTab {
    DIAGNOSE,
    FLOWCHARTS,
    HISTORY,
    EQUIPMENT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MechSightAuthenticatedApp(
    currentUserId: String,
    userEmail: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val viewModel: MechSightViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId).also { firestore ->
                    MechSightRepository.configurePersistentCache(firestore)
                }
                MechSightViewModel(MechSightRepository(db), currentUserId)
            }
        }
    )

    val isNetworkConnected by remember(context) {
        NetworkConnectivityObserver.observeNetworkAvailability(context)
    }.collectAsStateWithLifecycle(
        initialValue = NetworkConnectivityObserver.isCurrentlyConnected(context)
    )

    LaunchedEffect(isNetworkConnected) {
        viewModel.updateDeviceNetworkAvailability(isNetworkConnected)
    }

    val equipmentState by viewModel.equipmentState.collectAsStateWithLifecycle()
    val diagnosticsState by viewModel.diagnosticsState.collectAsStateWithLifecycle()
    val flowchartsState by viewModel.flowchartsState.collectAsStateWithLifecycle()
    val cacheStatus by viewModel.cacheStatus.collectAsStateWithLifecycle()
    val aiTaskState by viewModel.aiTaskState.collectAsStateWithLifecycle()
    val activeDiagnosticId by viewModel.activeDiagnosticId.collectAsStateWithLifecycle()
    val activeFlowchartId by viewModel.activeFlowchartId.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(MechSightTab.DIAGNOSE) }
    var preselectedEquipment by remember { mutableStateOf<EquipmentProfile?>(null) }

    // Handle back navigation from secondary tabs to return to the primary Diagnose tab
    if (currentTab != MechSightTab.DIAGNOSE) {
        BackHandler {
            currentTab = MechSightTab.DIAGNOSE
        }
    }

    LaunchedEffect(statusBanner) {
        val message = statusBanner
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearStatusBanner()
        }
    }

    val equipmentList = (equipmentState as? DataState.Success)?.data.orEmpty()
    val diagnosticsList = (diagnosticsState as? DataState.Success)?.data.orEmpty()
    val flowchartsList = (flowchartsState as? DataState.Success)?.data.orEmpty()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = userEmail.substringBefore("@"),
                            style = MaterialTheme.typography.labelMedium,
                            color = ElectricCyan
                        )
                    }
                },
                actions = {
                    CacheStatusHeaderPill(
                        cacheStatus = cacheStatus,
                        onToggleOfflineMode = {
                            viewModel.toggleOfflineFieldMode(!cacheStatus.isForcedOfflineMode)
                        }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = {
                            signOutUser(
                                context = context,
                                onSignOutComplete = {},
                                scope = scope
                            )
                        },
                        modifier = Modifier.testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = stringResource(R.string.sign_out)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = currentTab == MechSightTab.DIAGNOSE,
                    onClick = { currentTab = MechSightTab.DIAGNOSE },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = stringResource(R.string.nav_diagnose)
                        )
                    },
                    label = { Text(stringResource(R.string.nav_diagnose)) },
                    modifier = Modifier.testTag("nav_tab_diagnose")
                )
                NavigationBarItem(
                    selected = currentTab == MechSightTab.FLOWCHARTS,
                    onClick = { currentTab = MechSightTab.FLOWCHARTS },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.AccountTree,
                            contentDescription = stringResource(R.string.nav_flowcharts)
                        )
                    },
                    label = { Text(stringResource(R.string.nav_flowcharts)) },
                    modifier = Modifier.testTag("nav_tab_flowcharts")
                )
                NavigationBarItem(
                    selected = currentTab == MechSightTab.HISTORY,
                    onClick = { currentTab = MechSightTab.HISTORY },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = stringResource(R.string.nav_history)
                        )
                    },
                    label = { Text(stringResource(R.string.nav_history)) },
                    modifier = Modifier.testTag("nav_tab_history")
                )
                NavigationBarItem(
                    selected = currentTab == MechSightTab.EQUIPMENT,
                    onClick = { currentTab = MechSightTab.EQUIPMENT },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = stringResource(R.string.nav_equipment)
                        )
                    },
                    label = { Text(stringResource(R.string.nav_equipment)) },
                    modifier = Modifier.testTag("nav_tab_equipment")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(
                visible = cacheStatus.isEffectivelyOffline || cacheStatus.pendingWritesCount > 0
            ) {
                OfflineFieldCacheBanner(
                    cacheStatus = cacheStatus,
                    onGoOnlineAndSync = {
                        if (cacheStatus.isForcedOfflineMode) {
                            viewModel.toggleOfflineFieldMode(false)
                        } else {
                            viewModel.syncAndVerifyOfflineCache()
                        }
                    }
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (currentTab) {
                    MechSightTab.DIAGNOSE -> {
                        DiagnoseLabScreen(
                            equipmentList = equipmentList,
                            diagnosticsList = diagnosticsList,
                            activeDiagnosticId = activeDiagnosticId,
                            aiTaskState = aiTaskState,
                            preselectedEquipment = preselectedEquipment,
                            onRunDiagnosis = { eqName, eqModel, cat, env, symptom, bmp, bytes, mime, acoustic, label ->
                                viewModel.runMultimodalDiagnosis(
                                    equipmentName = eqName,
                                    equipmentModel = eqModel,
                                    systemCategory = cat,
                                    environmentType = env,
                                    symptomDescription = symptom,
                                    attachedBitmap = bmp,
                                    attachedMediaBytes = bytes,
                                    attachedMediaMimeType = mime,
                                    acousticSummary = acoustic,
                                    mediaSummaryLabel = label
                                )
                            },
                            onToggleRepairStep = { session, stepIdx ->
                                viewModel.toggleRepairStep(session, stepIdx)
                            },
                            onMarkResolved = { session ->
                                viewModel.markDiagnosticResolved(session)
                            },
                            onSavePdfReport = { session, targetUri ->
                                viewModel.exportDiagnosticPdfToUri(context, session.id, targetUri)
                            },
                            onEmailPdfReport = { session ->
                                viewModel.emailOrShareDiagnosticPdf(context, session.id)
                            },
                            onOpenFlowchart = { flowchartId ->
                                viewModel.selectFlowchart(flowchartId)
                                currentTab = MechSightTab.FLOWCHARTS
                            },
                            onSelectDiagnostic = { diagId ->
                                viewModel.selectDiagnostic(diagId)
                            },
                            onClearAiState = {
                                viewModel.clearAiTaskState()
                            },
                            onSeedReferenceLibrary = {
                                viewModel.seedReferenceLibrary()
                            }
                        )
                    }

                    MechSightTab.FLOWCHARTS -> {
                        FlowchartsScreen(
                            flowcharts = flowchartsList,
                            activeFlowchartId = activeFlowchartId,
                            aiTaskState = aiTaskState,
                            onSelectFlowchart = { flowId ->
                                viewModel.selectFlowchart(flowId)
                            },
                            onAnswerNode = { flowchart, answerLabel, targetNodeId ->
                                viewModel.answerFlowchartNode(flowchart, answerLabel, targetNodeId)
                            },
                            onResetWalkthrough = { flowchart ->
                                viewModel.resetFlowchartWalkthrough(flowchart)
                            },
                            onDeleteFlowchart = { flowId ->
                                viewModel.deleteFlowchart(flowId)
                            },
                            onGenerateAiFlowchart = { model, cat, env, prompt ->
                                viewModel.generateAiTroubleshootingFlowchart(
                                    equipmentModel = model,
                                    systemCategory = cat,
                                    environmentType = env,
                                    failureModePrompt = prompt
                                )
                            },
                            onSeedReferenceLibrary = {
                                viewModel.seedReferenceLibrary()
                            }
                        )
                    }

                    MechSightTab.HISTORY -> {
                        RepairHistoryScreen(
                            diagnosticsList = diagnosticsList,
                            cacheStatus = cacheStatus,
                            onToggleOfflineFieldMode = { enableOffline ->
                                viewModel.toggleOfflineFieldMode(enableOffline)
                            },
                            onSyncAndVerifyCache = {
                                viewModel.syncAndVerifyOfflineCache()
                            },
                            onToggleRepairStep = { session, stepIdx ->
                                viewModel.toggleRepairStep(session, stepIdx)
                            },
                            onMarkResolved = { session ->
                                viewModel.markDiagnosticResolved(session)
                            },
                            onSavePdfReport = { session, targetUri ->
                                viewModel.exportDiagnosticPdfToUri(context, session.id, targetUri)
                            },
                            onEmailPdfReport = { session ->
                                viewModel.emailOrShareDiagnosticPdf(context, session.id)
                            },
                            onDeleteDiagnostic = { diagId ->
                                viewModel.deleteDiagnosticSession(diagId)
                            },
                            onOpenInDiagnoseLab = { diagId ->
                                viewModel.selectDiagnostic(diagId)
                                currentTab = MechSightTab.DIAGNOSE
                            },
                            onSeedReferenceLibrary = {
                                viewModel.seedReferenceLibrary()
                            }
                        )
                    }

                    MechSightTab.EQUIPMENT -> {
                        EquipmentFleetScreen(
                            equipmentList = equipmentList,
                            diagnosticsList = diagnosticsList,
                            onAddEquipment = { name, model, cat, env, hrs, notes ->
                                viewModel.addEquipmentProfile(name, model, cat, env, hrs, notes)
                            },
                            onDeleteEquipment = { id ->
                                viewModel.deleteEquipmentProfile(id)
                            },
                            onDiagnoseEquipment = { eq ->
                                preselectedEquipment = eq
                                currentTab = MechSightTab.DIAGNOSE
                            },
                            onOpenDiagnosticSession = { diagId ->
                                viewModel.selectDiagnostic(diagId)
                                currentTab = MechSightTab.HISTORY
                            },
                            onDeleteDiagnosticSession = { diagId ->
                                viewModel.deleteDiagnosticSession(diagId)
                            },
                            onSeedReferenceLibrary = {
                                viewModel.seedReferenceLibrary()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CacheStatusHeaderPill(
    cacheStatus: FirestoreCacheStatus,
    onToggleOfflineMode: () -> Unit
) {
    val isOffline = cacheStatus.isEffectivelyOffline
    val hasPending = cacheStatus.pendingWritesCount > 0
    val accent = if (isOffline || hasPending) SafetyAmber else SignalEmerald

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .clickable { onToggleOfflineMode() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .testTag("top_bar_cache_status_pill"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = when {
                hasPending -> Icons.Default.CloudSync
                isOffline -> Icons.Default.CloudOff
                else -> Icons.Default.CloudDone
            },
            contentDescription = "Firestore Cache Status",
            tint = accent,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = when {
                cacheStatus.isForcedOfflineMode -> "FIELD OFFLINE (${cacheStatus.cachedDiagnosticsCount})"
                !cacheStatus.isNetworkAvailable -> "CACHED OFFLINE (${cacheStatus.cachedDiagnosticsCount})"
                hasPending -> "SYNCING (${cacheStatus.pendingWritesCount})"
                else -> "CACHED (${cacheStatus.cachedDiagnosticsCount})"
            },
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun OfflineFieldCacheBanner(
    cacheStatus: FirestoreCacheStatus,
    onGoOnlineAndSync: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SafetyAmber.copy(alpha = 0.15f))
            .border(0.5.dp, SafetyAmber.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("offline_field_cache_banner"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (cacheStatus.pendingWritesCount > 0) Icons.Default.CloudSync else Icons.Default.CloudOff,
                contentDescription = "Offline Cache Active",
                tint = SafetyAmber,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = buildString {
                    if (cacheStatus.isForcedOfflineMode) {
                        append("Remote Field Offline Mode: ")
                    } else if (!cacheStatus.isNetworkAvailable) {
                        append("No Signal — Serving from Firestore Cache: ")
                    } else {
                        append("Local Cache Active: ")
                    }
                    append("${cacheStatus.cachedDiagnosticsCount} guides & ${cacheStatus.cachedFlowchartsCount} flowcharts available offline")
                    if (cacheStatus.pendingWritesCount > 0) {
                        append(" • ${cacheStatus.pendingWritesCount} edits queued")
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (cacheStatus.isForcedOfflineMode || cacheStatus.pendingWritesCount > 0) {
            TextButton(onClick = onGoOnlineAndSync) {
                Text(
                    text = if (cacheStatus.isForcedOfflineMode) "GO ONLINE" else "SYNC NOW",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
