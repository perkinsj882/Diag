package com.example.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HazardCrimson
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SignalEmerald
import java.util.Locale

/**
 * Hands-Free Speech-to-Text Narration bar powered by Android's [SpeechRecognizer] API.
 * Allows mechanics to narrate equipment symptoms, gauge readings, fault codes, or
 * voice commands ("run diagnosis", "clear prompt") hands-free while working on machinery.
 */
@Composable
fun HandsFreeSpeechRecognizerCard(
    onTextRecognized: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Hands-Free Voice Narration (SpeechRecognizer)",
    hintText: String = "Narrate observations hands-free (e.g., \"Bearing housing 86°C, 24V rail sagging to 18.4V\" or say \"Run diagnosis\")",
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
    var statusMessage by remember { mutableStateOf("Ready for hands-free voice dictation") }
    var rmsLevelNormalized by remember { mutableFloatStateOf(0f) }

    val speechRecognizerRef = remember { mutableStateOf<SpeechRecognizer?>(null) }

    // Fallback system recognizer intent launcher if direct SpeechRecognizer service binding isn't supported on a custom ROM
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
            // Try fallback activity intent
            try {
                val intent = createSpeechRecognizerIntent(preferOfflineSpeech)
                fallbackIntentLauncher.launch(intent)
                isListening = true
                statusMessage = "Listening via system voice recognizer..."
            } catch (_: Exception) {
                isListening = false
                statusMessage = "Speech recognition service is not installed on this device."
            }
            return
        }

        try {
            val intent = createSpeechRecognizerIntent(preferOfflineSpeech)
            partialTranscript = ""
            rmsLevelNormalized = 0.08f
            isListening = true
            statusMessage = if (continuousHandsFree) {
                "Listening (Hands-Free Continuous Mode) — speak observations..."
            } else {
                "Listening — narrate equipment symptoms & measurements..."
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
                // Convert typical SpeechRecognizer rmsdB (-2..12) to 0f..1f progress
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
                    // Automatically restart listening in continuous hands-free mode
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

            // Option chips: Continuous Hands-Free Mode & Offline Speech Pack Mode
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
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
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error — check microphone connection."
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
