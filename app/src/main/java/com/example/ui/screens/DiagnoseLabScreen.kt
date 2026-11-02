package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.DiagnosticSession
import com.example.data.model.EnvironmentPreset
import com.example.data.model.EquipmentProfile
import com.example.data.model.SystemCategory
import com.example.ui.components.CameraCaptureDialog
import com.example.ui.components.CameraCaptureMode
import com.example.ui.components.CategoryBadge
import com.example.ui.components.HandsFreeSpeechRecognizerCard
import com.example.ui.components.SchematicHeroBanner
import com.example.ui.components.SeverityChip
import com.example.ui.components.categoryAccentColor
import com.example.ui.components.categoryIcon
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HazardCrimson
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SignalEmerald
import com.example.util.DiagnosticPdfExporter
import com.example.viewmodel.AiTaskState
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.log10

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiagnoseLabScreen(
    equipmentList: List<EquipmentProfile>,
    diagnosticsList: List<DiagnosticSession>,
    activeDiagnosticId: String?,
    aiTaskState: AiTaskState,
    preselectedEquipment: EquipmentProfile?,
    onRunDiagnosis: (
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
    ) -> Unit,
    onToggleRepairStep: (DiagnosticSession, Int) -> Unit,
    onMarkResolved: (DiagnosticSession) -> Unit,
    onSavePdfReport: (DiagnosticSession, Uri) -> Unit,
    onEmailPdfReport: (DiagnosticSession) -> Unit,
    onOpenFlowchart: (String) -> Unit,
    onSelectDiagnostic: (String?) -> Unit,
    onClearAiState: () -> Unit,
    onSeedReferenceLibrary: () -> Unit
) {
    val context = LocalContext.current
    var pendingPdfSaveSession by remember { mutableStateOf<DiagnosticSession?>(null) }

    val createPdfDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { targetUri: Uri? ->
        val sessionToSave = pendingPdfSaveSession
        if (targetUri != null && sessionToSave != null) {
            onSavePdfReport(sessionToSave, targetUri)
        }
        pendingPdfSaveSession = null
    }

    var selectedCategory by remember(preselectedEquipment) {
        mutableStateOf(
            preselectedEquipment?.let { SystemCategory.fromLabel(it.systemCategory) }
                ?: SystemCategory.COMPUTER_CONTROLLER
        )
    }
    var equipmentName by remember(preselectedEquipment) {
        mutableStateOf(preselectedEquipment?.name ?: "Siemens SIMATIC S7-1500 PLC")
    }
    var equipmentModel by remember(preselectedEquipment) {
        mutableStateOf(preselectedEquipment?.modelNumber ?: "6ES7515-2AM02-0AB0")
    }
    var selectedEnvironment by remember(preselectedEquipment) {
        mutableStateOf(preselectedEquipment?.environmentType ?: EnvironmentPreset.MANUFACTURING.label)
    }
    var symptomDescription by remember {
        mutableStateOf("")
    }

    // Multimodal Attachments State
    var attachedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var attachedMediaBytes by remember { mutableStateOf<ByteArray?>(null) }
    var attachedMediaMimeType by remember { mutableStateOf<String?>(null) }
    var mediaLabel by remember { mutableStateOf("") }

    // CameraX Dialog State
    var showCameraXDialog by remember { mutableStateOf(false) }
    var requestedCameraMode by remember { mutableStateOf(CameraCaptureMode.PHOTO) }

    // Live Acoustic Audio Recording State
    var isRecordingAudio by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var peakDecibels by remember { mutableIntStateOf(0) }
    var acousticSummary by remember { mutableStateOf("") }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordedAudioFile by remember { mutableStateOf<File?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaRecorder?.stop()
            } catch (_: Exception) {
            }
            mediaRecorder?.release()
        }
    }

    LaunchedEffect(isRecordingAudio) {
        while (isRecordingAudio) {
            delay(500L)
            val amp = try {
                mediaRecorder?.maxAmplitude ?: 0
            } catch (_: Exception) {
                0
            }
            if (amp > 0) {
                val db = (20 * log10(amp.toDouble())).toInt().coerceIn(30, 115)
                if (db > peakDecibels) peakDecibels = db
            }
            recordingSeconds += 1
            if (recordingSeconds >= 24) {
                stopAcousticRecording(
                    recorder = mediaRecorder,
                    audioFile = recordedAudioFile,
                    durationSec = recordingSeconds / 2,
                    peakDb = peakDecibels,
                    onResult = { bytes, summary ->
                        attachedMediaBytes = bytes
                        attachedMediaMimeType = "audio/mp4"
                        acousticSummary = summary
                        mediaLabel = listOfNotNull(
                            if (attachedBitmap != null) "Visual frame attached" else null,
                            summary
                        ).joinToString(" • ")
                    }
                )
                mediaRecorder = null
                isRecordingAudio = false
            }
        }
    }

    // Runtime Permission Launcher for CameraX (CAMERA + optional RECORD_AUDIO for video)
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val cameraGranted = grants[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (cameraGranted) {
            showCameraXDialog = true
        } else {
            mediaLabel = "Camera permission denied. You can still pick existing photos/videos from Gallery."
        }
    }

    // Zero-permission Android Photo & Video Picker (Gallery)
    val visualMediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            if (mime.startsWith("video/")) {
                val frame = extractVideoFrame(context, uri)
                val bytes = readBytesUnderLimit(context, uri, 12 * 1024 * 1024)
                attachedBitmap = frame
                if (bytes != null) {
                    attachedMediaBytes = bytes
                    attachedMediaMimeType = mime
                }
                mediaLabel = "Gallery Video & keyframe extracted ($mime)"
            } else {
                val bmp = decodeBitmapFromUri(context, uri)
                attachedBitmap = bmp
                mediaLabel = "Gallery inspection photo attached"
            }
        }
    }

    // Audio Document Picker
    val audioFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri) ?: "audio/mpeg"
            val bytes = readBytesUnderLimit(context, uri, 12 * 1024 * 1024)
            if (bytes != null) {
                attachedMediaBytes = bytes
                attachedMediaMimeType = mime
                acousticSummary = "Attached acoustic audio file ($mime, ${bytes.size / 1024} KB)"
                mediaLabel = acousticSummary
            }
        }
    }

    // Runtime RECORD_AUDIO Permission Launcher for Live Acoustic Capture
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startAcousticRecording(
                context = context,
                onStarted = { recorder, file ->
                    mediaRecorder = recorder
                    recordedAudioFile = file
                    recordingSeconds = 0
                    peakDecibels = 48
                    isRecordingAudio = true
                },
                onError = { err ->
                    acousticSummary = err
                }
            )
        } else {
            acousticSummary = "Microphone permission denied. You can still attach an audio/video file or describe the sound."
        }
    }

    if (showCameraXDialog) {
        CameraCaptureDialog(
            initialMode = requestedCameraMode,
            onDismiss = { showCameraXDialog = false },
            onMediaCaptured = { bmp, bytes, mime, summary ->
                attachedBitmap = bmp
                if (bytes != null) {
                    attachedMediaBytes = bytes
                    attachedMediaMimeType = mime
                }
                mediaLabel = summary
                showCameraXDialog = false
            }
        )
    }

    val activeSession = diagnosticsList.firstOrNull { it.id == activeDiagnosticId }
        ?: diagnosticsList.firstOrNull()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            SchematicHeroBanner(
                title = "Multimodal Fault Diagnostic Lab",
                subtitle = "SPEECH-TO-TEXT • CAMERAX PHOTO/VIDEO • AI REPAIR GUIDES"
            )
        }

        if (equipmentList.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "New Workspace Detected",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "Load 6 reference industrial, PLC controller, server & hydraulic units plus 6 interactive flowcharts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = onSeedReferenceLibrary,
                            modifier = Modifier.testTag("seed_reference_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            )
                        ) {
                            Text("Load Kit")
                        }
                    }
                }
            }
        }

        // Equipment & Domain Configuration Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "1. Select System Domain & Equipment Model",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SystemCategory.entries.forEach { category ->
                            val selected = selectedCategory == category
                            val accent = categoryAccentColor(category.label)
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedCategory = category
                                    val matchingEq = equipmentList.firstOrNull {
                                        it.systemCategory.equals(category.label, ignoreCase = true)
                                    }
                                    if (matchingEq != null) {
                                        equipmentName = matchingEq.name
                                        equipmentModel = matchingEq.modelNumber
                                        selectedEnvironment = matchingEq.environmentType
                                    }
                                },
                                label = { Text("${category.badgeCode}: ${category.label}") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = categoryIcon(category.label),
                                        contentDescription = category.label,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accent.copy(alpha = 0.2f),
                                    selectedLabelColor = accent
                                ),
                                modifier = Modifier.testTag("category_chip_${category.badgeCode.lowercase()}")
                            )
                        }
                    }

                    Text(
                        text = selectedCategory.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (equipmentList.isNotEmpty()) {
                        Text(
                            text = "Registered Equipment Profiles:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            equipmentList.take(6).forEach { eq ->
                                FilterChip(
                                    selected = equipmentModel == eq.modelNumber,
                                    onClick = {
                                        equipmentName = eq.name
                                        equipmentModel = eq.modelNumber
                                        selectedCategory = SystemCategory.fromLabel(eq.systemCategory)
                                        selectedEnvironment = eq.environmentType
                                    },
                                    label = { Text("${eq.name.take(22)} (${eq.modelNumber})") }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = equipmentName,
                            onValueChange = { equipmentName = it },
                            label = { Text("Equipment / Subsystem") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("equipment_name_input")
                        )
                        OutlinedTextField(
                            value = equipmentModel,
                            onValueChange = { equipmentModel = it },
                            label = { Text("Exact Model / Serial") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("equipment_model_input")
                        )
                    }

                    Text(
                        text = "Operating Environment:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        EnvironmentPreset.entries.forEach { env ->
                            FilterChip(
                                selected = selectedEnvironment == env.label,
                                onClick = { selectedEnvironment = env.label },
                                label = { Text(env.label) }
                            )
                        }
                    }
                }
            }
        }

        // Multimodal Symptom, CameraX Capture & Telemetry Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "2. Capture Visual/Audio Evidence & Symptom Prompt",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "Quick-Fill Common ${selectedCategory.label} Symptoms:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        selectedCategory.commonFailureModes.forEach { preset ->
                            FilterChip(
                                selected = symptomDescription == preset,
                                onClick = { symptomDescription = preset },
                                label = { Text(preset, maxLines = 1) }
                            )
                        }
                    }

                    HandsFreeSpeechRecognizerCard(
                        onTextRecognized = { spokenText ->
                            symptomDescription = if (symptomDescription.isBlank()) {
                                spokenText
                            } else {
                                val separator = if (symptomDescription.trimEnd().endsWith(".")) " " else ". "
                                "${symptomDescription.trimEnd()}$separator$spokenText"
                            }
                        },
                        onVoiceCommand = { rawSpoken ->
                            val normalized = rawSpoken.trim().lowercase()
                            when {
                                normalized == "run diagnosis" ||
                                    normalized == "start diagnosis" ||
                                    normalized == "diagnose now" -> {
                                    onRunDiagnosis(
                                        equipmentName,
                                        equipmentModel,
                                        selectedCategory,
                                        selectedEnvironment,
                                        symptomDescription,
                                        attachedBitmap,
                                        attachedMediaBytes,
                                        attachedMediaMimeType,
                                        acousticSummary,
                                        mediaLabel
                                    )
                                    true
                                }
                                normalized == "clear prompt" ||
                                    normalized == "clear description" ||
                                    normalized == "reset prompt" -> {
                                    symptomDescription = ""
                                    true
                                }
                                else -> false
                            }
                        },
                        testTagPrefix = "diagnose_speech_recognizer"
                    )

                    OutlinedTextField(
                        value = symptomDescription,
                        onValueChange = { symptomDescription = it },
                        label = {
                            Text("Word description or hands-free narration of symptoms, fault codes, noises & pressures")
                        },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("symptom_description_input")
                    )

                    // Row 1: CameraX Direct Hardware Capture (Photo & Video)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                requestedCameraMode = CameraCaptureMode.PHOTO
                                val hasCam = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasCam) {
                                    showCameraXDialog = true
                                } else {
                                    cameraPermissionLauncher.launch(
                                        arrayOf(Manifest.permission.CAMERA)
                                    )
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("camerax_capture_photo_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = ElectricCyan
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "CameraX Photo Capture",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("CameraX Photo")
                        }

                        Button(
                            onClick = {
                                requestedCameraMode = CameraCaptureMode.VIDEO
                                val hasCam = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                val hasMic = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasCam && hasMic) {
                                    showCameraXDialog = true
                                } else {
                                    cameraPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.CAMERA,
                                            Manifest.permission.RECORD_AUDIO
                                        )
                                    )
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("camerax_capture_video_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = SafetyAmber
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = "CameraX Video Capture",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("CameraX Video")
                        }
                    }

                    // Row 2: Gallery Picker + Live Acoustic Recorder + Audio File Picker
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                visualMediaPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("attach_image_video_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = "Upload Photo or Video from Gallery",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Gallery")
                        }

                        OutlinedButton(
                            onClick = {
                                if (isRecordingAudio) {
                                    stopAcousticRecording(
                                        recorder = mediaRecorder,
                                        audioFile = recordedAudioFile,
                                        durationSec = (recordingSeconds / 2).coerceAtLeast(1),
                                        peakDb = peakDecibels,
                                        onResult = { bytes, summary ->
                                            attachedMediaBytes = bytes
                                            attachedMediaMimeType = "audio/mp4"
                                            acousticSummary = summary
                                            mediaLabel = listOfNotNull(
                                                if (attachedBitmap != null) "Visual frame attached" else null,
                                                summary
                                            ).joinToString(" • ")
                                        }
                                    )
                                    mediaRecorder = null
                                    isRecordingAudio = false
                                } else {
                                    val hasPerm = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPerm) {
                                        startAcousticRecording(
                                            context = context,
                                            onStarted = { recorder, file ->
                                                mediaRecorder = recorder
                                                recordedAudioFile = file
                                                recordingSeconds = 0
                                                peakDecibels = 48
                                                isRecordingAudio = true
                                            },
                                            onError = { err -> acousticSummary = err }
                                        )
                                    } else {
                                        recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("record_audio_button"),
                            colors = if (isRecordingAudio) {
                                ButtonDefaults.outlinedButtonColors(contentColor = HazardCrimson)
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            }
                        ) {
                            Icon(
                                imageVector = if (isRecordingAudio) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = "Record Machine Audio",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isRecordingAudio) "Stop (${recordingSeconds / 2}s)" else "Mic Audio")
                        }

                        OutlinedButton(
                            onClick = { audioFilePicker.launch("audio/*") },
                            modifier = Modifier.testTag("attach_audio_file_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AudioFile,
                                contentDescription = "Pick Audio File",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    AnimatedVisibility(visible = isRecordingAudio) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = HazardCrimson.copy(alpha = 0.15f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = "Sampling Acoustic Spectrum",
                                        tint = HazardCrimson
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Sampling equipment acoustic signature... Peak: ~${peakDecibels} dB",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = HazardCrimson
                                    )
                                }
                            }
                        }
                    }

                    if (attachedBitmap != null || mediaLabel.isNotBlank() || acousticSummary.isNotBlank()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (attachedBitmap != null) {
                                    Image(
                                        bitmap = attachedBitmap!!.asImageBitmap(),
                                        contentDescription = "Attached inspection visual",
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.VideoFile,
                                        contentDescription = "Media attached",
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Multimodal Payload Attached",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = ElectricCyan
                                    )
                                    Text(
                                        text = mediaLabel.ifBlank { acousticSummary },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        attachedBitmap = null
                                        attachedMediaBytes = null
                                        attachedMediaMimeType = null
                                        acousticSummary = ""
                                        mediaLabel = ""
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove attachment"
                                    )
                                }
                            }
                        }
                    }

                    when (val state = aiTaskState) {
                        is AiTaskState.Running -> {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = ElectricCyan,
                                        strokeWidth = 2.5.dp
                                    )
                                    Text(
                                        text = state.statusLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        is AiTaskState.Error -> {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = onClearAiState) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Dismiss error",
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                        }
                        is AiTaskState.DiagnosisReady -> {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = SignalEmerald.copy(alpha = 0.16f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Diagnosis & tailored repair guide generated below.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = SignalEmerald,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (state.companionFlowchartId != null) {
                                        OutlinedButton(
                                            onClick = { onOpenFlowchart(state.companionFlowchartId) }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AccountTree,
                                                contentDescription = "Open Flowchart",
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Open Flowchart")
                                        }
                                    }
                                }
                            }
                        }
                        else -> {}
                    }

                    Button(
                        onClick = {
                            onRunDiagnosis(
                                equipmentName,
                                equipmentModel,
                                selectedCategory,
                                selectedEnvironment,
                                symptomDescription,
                                attachedBitmap,
                                attachedMediaBytes,
                                attachedMediaMimeType,
                                acousticSummary,
                                mediaLabel
                            )
                        },
                        enabled = aiTaskState !is AiTaskState.Running,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("run_diagnosis_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Run AI Diagnosis"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Diagnose & Generate Tailored Repair Guide",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        if (activeSession != null) {
            item {
                ActiveRepairGuideCard(
                    session = activeSession,
                    allSessions = diagnosticsList,
                    onSelectSession = onSelectDiagnostic,
                    onToggleStep = { idx -> onToggleRepairStep(activeSession, idx) },
                    onMarkResolved = { onMarkResolved(activeSession) },
                    onSavePdf = {
                        pendingPdfSaveSession = activeSession
                        createPdfDocumentLauncher.launch(
                            DiagnosticPdfExporter.buildSuggestedFileName(activeSession)
                        )
                    },
                    onEmailPdf = {
                        onEmailPdfReport(activeSession)
                    }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveRepairGuideCard(
    session: DiagnosticSession,
    allSessions: List<DiagnosticSession>,
    onSelectSession: (String) -> Unit,
    onToggleStep: (Int) -> Unit,
    onMarkResolved: () -> Unit,
    onSavePdf: () -> Unit,
    onEmailPdf: () -> Unit
) {
    val totalSteps = session.repairSteps.size.coerceAtLeast(1)
    val completedCount = session.completedStepIndices.size
    val progress = (completedCount.toFloat() / totalSteps.toFloat()).coerceIn(0f, 1f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                shape = RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (allSessions.size > 1) {
                Text(
                    text = "Recent Diagnostic Reports:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allSessions.take(5).forEach { item ->
                        FilterChip(
                            selected = item.id == session.id,
                            onClick = { onSelectSession(item.id) },
                            label = { Text("${item.equipmentModel}: ${item.rootCauseTitle.take(20)}") }
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryBadge(categoryLabel = session.systemCategory)
                SeverityChip(
                    severity = session.severity,
                    confidencePercent = session.confidencePercent
                )
            }

            Text(
                text = session.rootCauseTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Model: ${session.equipmentName} (${session.equipmentModel}) • Env: ${session.environmentType}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = session.technicalAnalysis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                contentDescription = "Safety Precautions",
                                tint = SafetyAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "LOCKOUT / TAGOUT & SAFETY PRECAUTIONS",
                                style = MaterialTheme.typography.labelMedium,
                                color = SafetyAmber,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        session.safetyWarnings.forEach { warn ->
                            Text(
                                text = "• $warn",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            if (session.requiredTools.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Required Tools, Test Instruments & Parts:",
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
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tailored Step-by-Step Repair Guide ($completedCount/${session.repairSteps.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = session.status,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (session.status == "RESOLVED") SignalEmerald else ElectricCyan
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (session.status == "RESOLVED") SignalEmerald else ElectricCyan
            )

            session.repairSteps.forEachIndexed { index, stepText ->
                val isDone = session.completedStepIndices.contains(index.toLong())
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isDone) SignalEmerald.copy(alpha = 0.1f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        )
                        .clickable(enabled = session.status != "RESOLVED") {
                            onToggleStep(index)
                        }
                        .padding(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Checkbox(
                        checked = isDone,
                        onCheckedChange = {
                            if (session.status != "RESOLVED") onToggleStep(index)
                        },
                        enabled = session.status != "RESOLVED"
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "STEP ${index + 1}",
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

            if (session.status != "RESOLVED") {
                Button(
                    onClick = onMarkResolved,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mark_resolved_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SignalEmerald,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Mark Resolved"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verify & Lock Repair as RESOLVED", fontWeight = FontWeight.Bold)
                }
            }

            // Export Completed/Active Diagnostic Report to PDF or Email
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onSavePdf,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("active_report_save_pdf_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = "Save PDF Report",
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save PDF Report")
                }

                Button(
                    onClick = onEmailPdf,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("active_report_email_pdf_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = ElectricCyan
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = "Email PDF Report",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Email Service PDF")
                }
            }
        }
    }
}

private fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.isMutableRequired = true
                val maxDim = maxOf(info.size.width, info.size.height)
                if (maxDim > 1280) {
                    decoder.setTargetSampleSize(maxDim / 1024)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (_: Exception) {
        null
    }
}

private fun extractVideoFrame(context: Context, uri: Uri): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.frameAtTime
    } catch (_: Exception) {
        null
    } finally {
        try {
            retriever.release()
        } catch (_: Exception) {
        }
    }
}

private fun readBytesUnderLimit(context: Context, uri: Uri, maxBytes: Int): ByteArray? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytes()
            if (bytes.size <= maxBytes) bytes else bytes.copyOf(maxBytes)
        }
    } catch (_: Exception) {
        null
    }
}

private fun startAcousticRecording(
    context: Context,
    onStarted: (MediaRecorder, File) -> Unit,
    onError: (String) -> Unit
) {
    try {
        val outputFile = File(context.cacheDir, "acoustic_sample_${System.currentTimeMillis()}.m4a")
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        recorder.setAudioSamplingRate(44100)
        recorder.setAudioEncodingBitRate(96000)
        recorder.setOutputFile(outputFile.absolutePath)
        recorder.prepare()
        recorder.start()
        onStarted(recorder, outputFile)
    } catch (e: Exception) {
        onError("Audio capture unavailable on this device: ${e.localizedMessage ?: "error"}")
    }
}

private fun stopAcousticRecording(
    recorder: MediaRecorder?,
    audioFile: File?,
    durationSec: Int,
    peakDb: Int,
    onResult: (ByteArray?, String) -> Unit
) {
    try {
        recorder?.stop()
    } catch (_: Exception) {
    }
    try {
        recorder?.release()
    } catch (_: Exception) {
    }
    val bytes = try {
        if (audioFile != null && audioFile.exists() && audioFile.length() > 0L) {
            audioFile.readBytes()
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
    val summary = "Live Acoustic Capture (${durationSec}s, Peak ~${peakDb} dB)"
    onResult(bytes, summary)
}
