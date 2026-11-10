package com.example.ui.components

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.DiagnosticSession
import com.example.data.model.MechanicVoiceObservation
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HazardCrimson
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SignalEmerald
import java.util.Locale

/**
 * Hands-Free Speech-to-Text Narration bar powered by Android's [SpeechRecognizer] API.
 * Allows mechanics to narrate automotive symptoms, gauge readings, OBD-II fault codes, or
 * voice commands ("run diagnosis", "clear prompt") hands-free while inspecting a vehicle.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HandsFreeSpeechRecognizerCard(
    onTextRecognized: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Hands-Free Voice Narration (SpeechRecognizer)",
    hintText: String = "Narrate vehicle observations hands-free (e.g., \"Fuel rail pressure 58 PSI at idle, Cylinder 3 misfire on cold start\" or say \"Run diagnosis\")",
    onVoiceCommand: ((String) -> Boolean)? = null,
    testTagPrefix: String = "speech_recognizer"
) {
    val context = LocalContext.current
    val currentOnTextRecognized by rememberUpdatedState(onTextRecognized)
    val currentOnVoiceCommand by rememberUpdatedState(onVoiceCommand)

    var isListening by remember { mutableStateOf(false) }
    var continuousHandsFree by remember { mutableStateOf(false) }
    var preferOfflineSpeech by remember { mutableStateOf(false) }
    var partialTranscript by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("Ready for hands-free automotive voice dictation") }
    var rmsLevelNormalized by remember { mutableFloatStateOf(0f) }
    var samplePhraseIndex by remember { mutableIntStateOf(0) }

    val speechRecognizerRef = remember { mutableStateOf<SpeechRecognizer?>(null) }

    val sampleAutomotiveDictations = remember {
        listOf(
            "Fuel rail pressure holding 58 PSI at hot idle; long-term fuel trim Bank 1 elevated at +14.8% under load.",
            "Measured 0.42V voltage drop across front left chassis ground strap G102 during starter cranking.",
            "Cold start metallic rattle from front timing cover for 3 seconds with active code P0016 cam-crank correlation.",
            "Master cylinder pedal slowly sinks 40mm under steady foot pressure with zero external caliper leaks."
        )
    }

    val fallbackIntentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val bestText = matches?.firstOrNull()?.trim().orEmpty()
            if (bestText.isNotEmpty()) {
                val handledAsCommand = currentOnVoiceCommand?.invoke(bestText) == true
                if (!handledAsCommand) {
                    currentOnTextRecognized(bestText)
                    statusMessage = "Transcribed: \"$bestText\""
                } else {
                    statusMessage = "Executed voice command: \"$bestText\""
                }
            }
        } else {
            statusMessage = "Voice dictation cancelled."
        }
    }

    fun startSpeechRecognitionSession() {
        val recognizer = speechRecognizerRef.value
        if (recognizer == null) {
            try {
                val intent = createSpeechRecognizerIntent(preferOfflineSpeech)
                fallbackIntentLauncher.launch(intent)
                isListening = true
                statusMessage = "Listening via system voice recognizer..."
            } catch (_: Exception) {
                isListening = false
                statusMessage = "Speech service unavailable — use Simulate Mic Dictation below."
            }
            return
        }

        try {
            val intent = createSpeechRecognizerIntent(preferOfflineSpeech)
            partialTranscript = ""
            rmsLevelNormalized = 0.08f
            isListening = true
            statusMessage = if (continuousHandsFree) {
                "Listening (Hands-Free Continuous Loop) — speak vehicle observations..."
            } else {
                "Listening — narrate vehicle symptoms & measurements..."
            }
            recognizer.startListening(intent)
        } catch (e: Exception) {
            isListening = false
            statusMessage = "Unable to start SpeechRecognizer: ${e.localizedMessage ?: "error"}"
        }
    }

    fun stopSpeechRecognitionSession() {
        continuousHandsFree = false
        isListening = false
        rmsLevelNormalized = 0f
        partialTranscript = ""
        try {
            speechRecognizerRef.value?.stopListening()
            speechRecognizerRef.value?.cancel()
        } catch (_: Exception) {
        }
        statusMessage = "Hands-free narration paused."
    }

    DisposableEffect(context) {
        val isAvailable = try {
            SpeechRecognizer.isRecognitionAvailable(context)
        } catch (_: Exception) {
            false
        }

        val recognizer = try {
            if (isAvailable) SpeechRecognizer.createSpeechRecognizer(context) else null
        } catch (_: Exception) {
            null
        }

        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                statusMessage = if (continuousHandsFree) {
                    "Microphone active (Hands-Free Loop) — speak now..."
                } else {
                    "Microphone active — speak your diagnostic observations..."
                }
            }

            override fun onBeginningOfSpeech() {
                statusMessage = "Capturing mechanic voice narration..."
            }

            override fun onRmsChanged(rmsdB: Float) {
                rmsLevelNormalized = ((rmsdB + 2f) / 14f).coerceIn(0.05f, 1f)
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                rmsLevelNormalized = 0f
                if (!continuousHandsFree) {
                    isListening = false
                }
            }

            override fun onError(error: Int) {
                rmsLevelNormalized = 0f
                val readableError = mapSpeechErrorToMessage(error)
                if (continuousHandsFree && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                    startSpeechRecognitionSession()
                } else {
                    isListening = false
                    continuousHandsFree = false
                    statusMessage = readableError
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val bestText = matches?.firstOrNull()?.trim().orEmpty()
                partialTranscript = ""
                rmsLevelNormalized = 0f

                if (bestText.isNotEmpty()) {
                    val handledAsCommand = currentOnVoiceCommand?.invoke(bestText) == true
                    if (!handledAsCommand) {
                        currentOnTextRecognized(bestText)
                        statusMessage = "Added narration: \"$bestText\""
                    } else {
                        statusMessage = "Voice command executed: \"$bestText\""
                    }
                } else {
                    statusMessage = "No speech detected. Tap Dictate to try again."
                }

                if (continuousHandsFree) {
                    startSpeechRecognitionSession()
                } else {
                    isListening = false
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partialList = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = partialList?.firstOrNull()?.trim().orEmpty()
                if (text.isNotEmpty()) {
                    partialTranscript = text
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        speechRecognizerRef.value = recognizer

        onDispose {
            try {
                recognizer?.stopListening()
                recognizer?.cancel()
                recognizer?.destroy()
            } catch (_: Exception) {
            }
            speechRecognizerRef.value = null
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startSpeechRecognitionSession()
        } else {
            isListening = false
            statusMessage = "Microphone permission is required for hands-free SpeechRecognizer narration."
        }
    }

    fun requestAndStartListening() {
        val hasAudioPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasAudioPermission) {
            startSpeechRecognitionSession()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (isListening) 1.5.dp else 1.dp,
                color = if (isListening) SafetyAmber else ElectricCyan.copy(alpha = 0.45f),
                shape = RoundedCornerShape(14.dp)
            )
            .testTag("${testTagPrefix}_card"),
        colors = CardDefaults.cardColors(
            containerColor = if (isListening) {
                SafetyAmber.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            }
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (isListening) SafetyAmber.copy(alpha = 0.22f)
                                else ElectricCyan.copy(alpha = 0.18f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.GraphicEq else Icons.Default.RecordVoiceOver,
                            contentDescription = "SpeechRecognizer Status",
                            tint = if (isListening) SafetyAmber else ElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isListening) SafetyAmber else ElectricCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        if (isListening) {
                            stopSpeechRecognitionSession()
                        } else {
                            requestAndStartListening()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isListening) HazardCrimson else ElectricCyan,
                        contentColor = if (isListening) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("${testTagPrefix}_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isListening) "Stop Dictation" else "Start Hands-Free Dictation",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isListening) "Stop" else "Dictate",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = continuousHandsFree,
                    onClick = {
                        continuousHandsFree = !continuousHandsFree
                        if (continuousHandsFree && !isListening) {
                            requestAndStartListening()
                        }
                    },
                    label = { Text("Continuous Hands-Free Loop") },
                    leadingIcon = {
                        Icon(
                            imageVector = if (continuousHandsFree) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = "Continuous Hands-Free Mode",
                            modifier = Modifier.size(15.dp),
                            tint = if (continuousHandsFree) SignalEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.testTag("${testTagPrefix}_continuous_chip")
                )

                FilterChip(
                    selected = preferOfflineSpeech,
                    onClick = { preferOfflineSpeech = !preferOfflineSpeech },
                    label = { Text("On-Device Speech") },
                    modifier = Modifier.testTag("${testTagPrefix}_offline_chip")
                )

                FilterChip(
                    selected = false,
                    onClick = {
                        val phrase = sampleAutomotiveDictations[samplePhraseIndex % sampleAutomotiveDictations.size]
                        samplePhraseIndex++
                        currentOnTextRecognized(phrase)
                        statusMessage = "Transcribed voice observation: \"$phrase\""
                    },
                    label = { Text("Simulate Mic Dictation") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Simulate Voice Dictation",
                            modifier = Modifier.size(15.dp),
                            tint = SafetyAmber
                        )
                    },
                    modifier = Modifier.testTag("${testTagPrefix}_simulate_chip")
                )
            }

            AnimatedVisibility(visible = isListening || partialTranscript.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(
                        progress = { rmsLevelNormalized.coerceIn(0.08f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = SafetyAmber
                    )
                    if (partialTranscript.isNotBlank()) {
                        Text(
                            text = "Live Transcript: \"$partialTranscript...\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = ElectricCyan,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (!isListening && partialTranscript.isBlank()) {
                Text(
                    text = hintText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Microphone-Enabled Voice-to-Text Diagnostic Report Logger.
 * Embedded directly inside an active or saved [DiagnosticSession] report so mechanics can dictate
 * step-specific measurements, part wear observations, and verification notes hands-free while working on a vehicle.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiagnosticReportVoiceLoggerCard(
    session: DiagnosticSession,
    onLogVoiceObservation: (stepTag: String, categoryTag: String, spokenText: String) -> Unit,
    onDeleteVoiceObservation: (observationId: String) -> Unit,
    onToggleRepairStep: ((Int) -> Unit)? = null,
    onMarkResolved: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "report_voice_logger"
) {
    val context = LocalContext.current
    val currentOnLogVoiceObservation by rememberUpdatedState(onLogVoiceObservation)
    val currentOnToggleRepairStep by rememberUpdatedState(onToggleRepairStep)
    val currentOnMarkResolved by rememberUpdatedState(onMarkResolved)

    val stepOptions = remember(session.repairSteps.size) {
        buildList {
            add("GENERAL")
            for (i in session.repairSteps.indices) {
                add("STEP ${i + 1}")
            }
        }
    }
    val categoryOptions = remember {
        listOf("MEASUREMENT", "PART / WEAR", "OBD-II / TEST", "ROAD TEST")
    }

    var selectedStepTag by remember(session.id) { mutableStateOf("GENERAL") }
    var selectedCategoryTag by remember(session.id) { mutableStateOf("MEASUREMENT") }
    var draftNoteText by remember(session.id) { mutableStateOf("") }

    var isListening by remember { mutableStateOf(false) }
    var continuousHandsFree by remember { mutableStateOf(false) }
    var preferOfflineSpeech by remember { mutableStateOf(false) }
    var partialTranscript by remember { mutableStateOf("") }
    var statusMessage by remember {
        mutableStateOf("Tap 'Dictate Note' to log spoken observations directly into this diagnostic report")
    }
    var rmsLevelNormalized by remember { mutableFloatStateOf(0f) }
    var benchPresetIndex by remember { mutableIntStateOf(0) }

    val speechRecognizerRef = remember { mutableStateOf<SpeechRecognizer?>(null) }

    val benchDictationSamples = remember(session.equipmentModel, session.systemCategory) {
        listOf(
            "Measured 14.18V DC charging voltage at battery posts with 28mV AC ripple under full load.",
            "Back-probed sensor connector pin 2; 5.01V reference stable after cleaning weather-pack terminal.",
            "Torqued replacement assembly bolts to OEM specification and verified zero fluid or boost leaks.",
            "Post-repair 15-minute road test completed; fuel trims within 2% and zero pending OBD-II DTCs."
        )
    }

    fun handleRecognizedReportSpeech(spokenText: String) {
        val clean = spokenText.trim()
        if (clean.isEmpty()) return
        val lower = clean.lowercase(Locale.US)

        // Check for hands-free step completion commands e.g. "complete step 1", "check step 2"
        val stepMatch = Regex("(?:complete|check|finish|mark)\\s+step\\s+(\\d+)").find(lower)
        if (stepMatch != null && currentOnToggleRepairStep != null) {
            val stepNum = stepMatch.groupValues[1].toIntOrNull()
            if (stepNum != null && stepNum in 1..session.repairSteps.size) {
                currentOnToggleRepairStep?.invoke(stepNum - 1)
                currentOnLogVoiceObservation(
                    "STEP $stepNum",
                    "OBD-II / TEST",
                    "Voice command executed: Completed Step $stepNum during vehicle service."
                )
                statusMessage = "Hands-free command: Toggled Step $stepNum & logged to report."
                return
            }
        }

        if ((lower == "mark resolved" || lower == "repair complete" || lower == "resolve report") &&
            currentOnMarkResolved != null
        ) {
            currentOnMarkResolved?.invoke()
            currentOnLogVoiceObservation(
                selectedStepTag,
                "ROAD TEST",
                "Mechanic voice sign-off: Repair verified and marked RESOLVED."
            )
            statusMessage = "Hands-free command: Marked report RESOLVED."
            return
        }

        // Log the spoken observation directly into the diagnostic report
        currentOnLogVoiceObservation(selectedStepTag, selectedCategoryTag, clean)
        statusMessage = "Logged to report [$selectedStepTag • $selectedCategoryTag]: \"$clean\""
    }

    val fallbackIntentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val bestText = matches?.firstOrNull()?.trim().orEmpty()
            if (bestText.isNotEmpty()) {
                handleRecognizedReportSpeech(bestText)
            }
        } else {
            statusMessage = "Voice report dictation cancelled."
        }
    }

    fun startReportVoiceDictation() {
        val recognizer = speechRecognizerRef.value
        if (recognizer == null) {
            try {
                val intent = createSpeechRecognizerIntent(preferOfflineSpeech)
                fallbackIntentLauncher.launch(intent)
                isListening = true
                statusMessage = "Listening via system voice recognizer for report log..."
            } catch (_: Exception) {
                isListening = false
                statusMessage = "System speech recognizer unavailable — use Simulate Voice Log or type below."
            }
            return
        }

        try {
            val intent = createSpeechRecognizerIntent(preferOfflineSpeech)
            partialTranscript = ""
            rmsLevelNormalized = 0.1f
            isListening = true
            statusMessage = if (continuousHandsFree) {
                "Mic active (Continuous Loop) — dictating into [$selectedStepTag • $selectedCategoryTag]..."
            } else {
                "Mic active — dictate observation for [$selectedStepTag • $selectedCategoryTag]..."
            }
            recognizer.startListening(intent)
        } catch (e: Exception) {
            isListening = false
            statusMessage = "Unable to start SpeechRecognizer: ${e.localizedMessage ?: "error"}"
        }
    }

    fun stopReportVoiceDictation() {
        continuousHandsFree = false
        isListening = false
        rmsLevelNormalized = 0f
        partialTranscript = ""
        try {
            speechRecognizerRef.value?.stopListening()
            speechRecognizerRef.value?.cancel()
        } catch (_: Exception) {
        }
        statusMessage = "Microphone dictation paused."
    }

    DisposableEffect(context, session.id) {
        val isAvailable = try {
            SpeechRecognizer.isRecognitionAvailable(context)
        } catch (_: Exception) {
            false
        }

        val recognizer = try {
            if (isAvailable) SpeechRecognizer.createSpeechRecognizer(context) else null
        } catch (_: Exception) {
            null
        }

        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                statusMessage = "Microphone live — dictate notes for [$selectedStepTag • $selectedCategoryTag]..."
            }

            override fun onBeginningOfSpeech() {
                statusMessage = "Transcribing mechanic voice observation..."
            }

            override fun onRmsChanged(rmsdB: Float) {
                rmsLevelNormalized = ((rmsdB + 2f) / 14f).coerceIn(0.05f, 1f)
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                rmsLevelNormalized = 0f
                if (!continuousHandsFree) {
                    isListening = false
                }
            }

            override fun onError(error: Int) {
                rmsLevelNormalized = 0f
                val readableError = mapSpeechErrorToMessage(error)
                if (continuousHandsFree && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                    startReportVoiceDictation()
                } else {
                    isListening = false
                    continuousHandsFree = false
                    statusMessage = readableError
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val bestText = matches?.firstOrNull()?.trim().orEmpty()
                partialTranscript = ""
                rmsLevelNormalized = 0f

                if (bestText.isNotEmpty()) {
                    handleRecognizedReportSpeech(bestText)
                } else {
                    statusMessage = "No speech detected. Tap Dictate Note to try again."
                }

                if (continuousHandsFree) {
                    startReportVoiceDictation()
                } else {
                    isListening = false
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partialList = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = partialList?.firstOrNull()?.trim().orEmpty()
                if (text.isNotEmpty()) {
                    partialTranscript = text
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        speechRecognizerRef.value = recognizer

        onDispose {
            try {
                recognizer?.stopListening()
                recognizer?.cancel()
                recognizer?.destroy()
            } catch (_: Exception) {
            }
            speechRecognizerRef.value = null
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startReportVoiceDictation()
        } else {
            isListening = false
            statusMessage = "Microphone permission is required to dictate notes into the diagnostic report."
        }
    }

    fun requestAndStartReportDictation() {
        val hasAudioPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasAudioPermission) {
            startReportVoiceDictation()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val voiceObservations: List<MechanicVoiceObservation> = remember(session.mechanicVoiceLogs) {
        session.parsedVoiceObservations()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (isListening) 1.5.dp else 1.dp,
                color = if (isListening) SafetyAmber else ElectricCyan.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp)
            )
            .testTag("${testTagPrefix}_card_${session.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isListening) {
                SafetyAmber.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row with Mic Status & Primary Dictate Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (isListening) SafetyAmber.copy(alpha = 0.24f)
                                else ElectricCyan.copy(alpha = 0.18f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.GraphicEq else Icons.Default.Mic,
                            contentDescription = "Voice-to-Text Report Logger",
                            tint = if (isListening) SafetyAmber else ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "VOICE-TO-TEXT REPORT OBSERVATION LOG (${voiceObservations.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isListening) SafetyAmber else ElectricCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        if (isListening) {
                            stopReportVoiceDictation()
                        } else {
                            requestAndStartReportDictation()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isListening) HazardCrimson else ElectricCyan,
                        contentColor = if (isListening) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("${testTagPrefix}_dictate_button_${session.id}")
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isListening) "Stop Dictation" else "Dictate Note to Report",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isListening) "Stop Mic" else "Dictate Note",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Live RMS Decibel Meter & Partial Speech Preview
            AnimatedVisibility(visible = isListening || partialTranscript.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(
                        progress = { rmsLevelNormalized.coerceIn(0.08f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = SafetyAmber
                    )
                    if (partialTranscript.isNotBlank()) {
                        Text(
                            text = "Live Speech-to-Text: \"$partialTranscript...\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = ElectricCyan,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Target Repair Step Tag Selector
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Attach Dictated Note to Repair Step & Category:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    stepOptions.forEach { stepLabel ->
                        FilterChip(
                            selected = selectedStepTag == stepLabel,
                            onClick = { selectedStepTag = stepLabel },
                            label = { Text(stepLabel, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    categoryOptions.forEach { catLabel ->
                        FilterChip(
                            selected = selectedCategoryTag == catLabel,
                            onClick = { selectedCategoryTag = catLabel },
                            label = { Text(catLabel, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            // Hands-Free Mode Controls + Bench Voice Dictation Simulator Chip
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = continuousHandsFree,
                    onClick = {
                        continuousHandsFree = !continuousHandsFree
                        if (continuousHandsFree && !isListening) {
                            requestAndStartReportDictation()
                        }
                    },
                    label = { Text("Hands-Free Loop") },
                    leadingIcon = {
                        Icon(
                            imageVector = if (continuousHandsFree) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = "Continuous Hands-Free Report Logging",
                            modifier = Modifier.size(14.dp),
                            tint = if (continuousHandsFree) SignalEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )

                FilterChip(
                    selected = preferOfflineSpeech,
                    onClick = { preferOfflineSpeech = !preferOfflineSpeech },
                    label = { Text("On-Device Speech") }
                )

                OutlinedButton(
                    onClick = {
                        val sample = benchDictationSamples[benchPresetIndex % benchDictationSamples.size]
                        benchPresetIndex++
                        handleRecognizedReportSpeech(sample)
                    },
                    modifier = Modifier.testTag("${testTagPrefix}_simulate_button_${session.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = "Simulate Voice Log Entry",
                        tint = SafetyAmber,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Simulate Voice Log", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Inline Text Field for Reviewing/Editing or Adding Observation Directly
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = draftNoteText,
                    onValueChange = { draftNoteText = it },
                    label = {
                        Text("Dictate with Mic or type mechanic observation for $selectedStepTag...")
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (isListening) stopReportVoiceDictation() else requestAndStartReportDictation()
                            }
                        ) {
                            Icon(
                                imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = "Microphone Voice Input",
                                tint = if (isListening) SafetyAmber else ElectricCyan
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("${testTagPrefix}_input_${session.id}")
                )

                Button(
                    onClick = {
                        if (draftNoteText.isNotBlank()) {
                            handleRecognizedReportSpeech(draftNoteText)
                            draftNoteText = ""
                        }
                    },
                    enabled = draftNoteText.isNotBlank(),
                    modifier = Modifier.testTag("${testTagPrefix}_add_button_${session.id}")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Log Note to Report",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Logged Mechanic Voice Observations List inside the Report
            if (voiceObservations.isEmpty()) {
                Text(
                    text = "No mechanic voice observations logged yet. Tap 'Dictate Note' (or say \"Complete step 1\") while working on the vehicle to append timestamped notes directly to this report and its PDF export.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    voiceObservations.forEach { entry ->
                        val catAccent = when (entry.categoryTag) {
                            "MEASUREMENT" -> ElectricCyan
                            "PART / WEAR" -> SafetyAmber
                            "OBD-II / TEST" -> SignalEmerald
                            else -> MaterialTheme.colorScheme.primary
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                                .border(0.5.dp, catAccent.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(ElectricCyan.copy(alpha = 0.16f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = entry.stepTag,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ElectricCyan,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(catAccent.copy(alpha = 0.16f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = entry.categoryTag,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = catAccent,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = entry.timestampLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = entry.transcript,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(
                                onClick = { onDeleteVoiceObservation(entry.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Delete Voice Observation",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun createSpeechRecognizerIntent(preferOffline: Boolean): Intent {
    return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        if (preferOffline) {
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
    }
}

private fun mapSpeechErrorToMessage(errorCode: Int): String {
    return when (errorCode) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error — check microphone connection or tap Simulate Voice Log."
        SpeechRecognizer.ERROR_CLIENT -> "Hands-free dictation ready. Tap Dictate to speak."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required for hands-free narration."
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network unavailable for cloud speech — enable 'On-Device Speech' chip for offline recognition."
        SpeechRecognizer.ERROR_NO_MATCH -> "No clear speech recognized. Speak closer to the mic or tap Dictate again."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "SpeechRecognizer busy — wait a moment and tap Dictate."
        SpeechRecognizer.ERROR_SERVER -> "Speech server unreachable — try enabling 'On-Device Speech'."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Silence timeout — tap Dictate when ready to narrate."
        else -> "Speech recognition paused (code $errorCode). Tap Dictate to resume."
    }
}
