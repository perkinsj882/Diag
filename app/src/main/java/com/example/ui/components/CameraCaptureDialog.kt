package com.example.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HazardCrimson
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SlateNavy900
import com.example.ui.theme.SlateNavy950
import kotlinx.coroutines.delay
import java.io.File

enum class CameraCaptureMode {
    PHOTO,
    VIDEO
}

@SuppressLint("MissingPermission")
@Composable
fun CameraCaptureDialog(
    initialMode: CameraCaptureMode = CameraCaptureMode.PHOTO,
    onDismiss: () -> Unit,
    onMediaCaptured: (
        bitmap: Bitmap?,
        mediaBytes: ByteArray?,
        mimeType: String,
        summaryLabel: String
    ) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var captureMode by remember { mutableStateOf(initialMode) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var isTorchEnabled by remember { mutableStateOf(false) }
    var isCapturingPhoto by remember { mutableStateOf(false) }
    var isRecordingVideo by remember { mutableStateOf(false) }
    var videoElapsedSeconds by remember { mutableIntStateOf(0) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    val videoCapture = remember {
        val qualitySelector = QualitySelector.from(
            Quality.HD,
            FallbackStrategy.higherQualityOrLowerThan(Quality.SD)
        )
        val recorder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .build()
        VideoCapture.withOutput(recorder)
    }

    var activeCamera by remember { mutableStateOf<Camera?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }

    // Bind CameraX use cases whenever lensFacing changes
    LaunchedEffect(lensFacing) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val selector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                cameraProvider.unbindAll()
                activeCamera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    imageCapture,
                    videoCapture
                )
                isTorchEnabled = false
                cameraError = null
            } catch (e: Exception) {
                cameraError = "Camera initialization failed: ${e.localizedMessage ?: "No camera hardware available"}"
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Video Recording Timer (auto-stops at 15 seconds to keep payload well under 15MB)
    LaunchedEffect(isRecordingVideo) {
        while (isRecordingVideo) {
            delay(1000L)
            videoElapsedSeconds += 1
            if (videoElapsedSeconds >= 15) {
                activeRecording?.stop()
                activeRecording = null
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                activeRecording?.stop()
            } catch (_: Exception) {
            }
            try {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            } catch (_: Exception) {
            }
        }
    }

    Dialog(
        onDismissRequest = {
            activeRecording?.stop()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(SlateNavy950),
            color = SlateNavy950
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Top CameraX HUD Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CAMERAX OPTICAL & VIDEO INSPECTOR",
                            style = MaterialTheme.typography.labelMedium,
                            color = ElectricCyan
                        )
                        Text(
                            text = if (captureMode == CameraCaptureMode.PHOTO) {
                                "Align crosshair with fault component or nameplate"
                            } else {
                                "Record equipment motion, vibration, & acoustic video"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = {
                                val nextTorch = !isTorchEnabled
                                isTorchEnabled = nextTorch
                                activeCamera?.cameraControl?.enableTorch(nextTorch)
                            },
                            modifier = Modifier.testTag("camerax_torch_button")
                        ) {
                            Icon(
                                imageVector = if (isTorchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "Toggle Inspection Torch",
                                tint = if (isTorchEnabled) SafetyAmber else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = {
                                if (!isRecordingVideo) {
                                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                        CameraSelector.LENS_FACING_FRONT
                                    } else {
                                        CameraSelector.LENS_FACING_BACK
                                    }
                                }
                            },
                            modifier = Modifier.testTag("camerax_switch_lens_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cameraswitch,
                                contentDescription = "Switch Camera Lens",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = {
                                activeRecording?.stop()
                                onDismiss()
                            },
                            modifier = Modifier.testTag("camerax_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Camera",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Live CameraX PreviewView + Industrial Reticle Overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .border(
                            width = 1.5.dp,
                            color = if (isRecordingVideo) HazardCrimson else ElectricCyan.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(18.dp)
                        )
                ) {
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("camerax_preview_view")
                    )

                    // Technical Reticle Crosshair Overlay
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val boxW = w * 0.68f
                        val boxH = h * 0.54f
                        val left = (w - boxW) / 2f
                        val top = (h - boxH) / 2f

                        drawRoundRect(
                            color = ElectricCyan.copy(alpha = 0.38f),
                            topLeft = Offset(left, top),
                            size = Size(boxW, boxH),
                            cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                            style = Stroke(width = 1.5.dp.toPx())
                        )

                        // Center Crosshair
                        val cx = w / 2f
                        val cy = h / 2f
                        val arm = 14.dp.toPx()
                        drawLine(
                            color = SafetyAmber.copy(alpha = 0.75f),
                            start = Offset(cx - arm, cy),
                            end = Offset(cx + arm, cy),
                            strokeWidth = 1.5.dp.toPx()
                        )
                        drawLine(
                            color = SafetyAmber.copy(alpha = 0.75f),
                            start = Offset(cx, cy - arm),
                            end = Offset(cx, cy + arm),
                            strokeWidth = 1.5.dp.toPx()
                        )
                    }

                    // Live Video Recording Badge
                    if (isRecordingVideo) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 14.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(HazardCrimson.copy(alpha = 0.9f))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "REC • 00:${videoElapsedSeconds.toString().padStart(2, '0')} / 00:15",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onError,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (!cameraError.isNullOrBlank()) {
                        Card(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                                .fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = cameraError!!,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Bottom Mode Selector & Capture Controls
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = SlateNavy900
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = captureMode == CameraCaptureMode.PHOTO,
                                onClick = {
                                    if (!isRecordingVideo) captureMode = CameraCaptureMode.PHOTO
                                },
                                label = { Text("PHOTO CAPTURE") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Photo Mode",
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = ElectricCyan
                                ),
                                modifier = Modifier.testTag("camerax_mode_photo")
                            )

                            FilterChip(
                                selected = captureMode == CameraCaptureMode.VIDEO,
                                onClick = {
                                    if (!isRecordingVideo) captureMode = CameraCaptureMode.VIDEO
                                },
                                label = { Text("VIDEO CAPTURE") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Videocam,
                                        contentDescription = "Video Mode",
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SafetyAmber.copy(alpha = 0.2f),
                                    selectedLabelColor = SafetyAmber
                                ),
                                modifier = Modifier.testTag("camerax_mode_video")
                            )
                        }

                        if (captureMode == CameraCaptureMode.PHOTO) {
                            Button(
                                onClick = {
                                    isCapturingPhoto = true
                                    cameraError = null
                                    val photoFile = File(
                                        context.cacheDir,
                                        "camerax_diag_${System.currentTimeMillis()}.jpg"
                                    )
                                    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                                    imageCapture.takePicture(
                                        outputOptions,
                                        ContextCompat.getMainExecutor(context),
                                        object : ImageCapture.OnImageSavedCallback {
                                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                                isCapturingPhoto = false
                                                val bmp = decodeScaledBitmapFromFile(photoFile)
                                                val bytes = try {
                                                    photoFile.readBytes()
                                                } catch (_: Exception) {
                                                    null
                                                }
                                                onMediaCaptured(
                                                    bmp,
                                                    bytes,
                                                    "image/jpeg",
                                                    "CameraX High-Res Inspection Photo (${(bytes?.size ?: 0) / 1024} KB)"
                                                )
                                            }

                                            override fun onError(exception: ImageCaptureException) {
                                                isCapturingPhoto = false
                                                // Fallback: capture previewView bitmap if virtual camera stream doesn't support still capture
                                                val fallbackBmp = previewView.bitmap
                                                if (fallbackBmp != null) {
                                                    onMediaCaptured(
                                                        fallbackBmp,
                                                        null,
                                                        "image/jpeg",
                                                        "CameraX Viewfinder Frame Captured"
                                                    )
                                                } else {
                                                    cameraError = "Photo capture failed: ${exception.localizedMessage ?: "Camera error"}"
                                                }
                                            }
                                        }
                                    )
                                },
                                enabled = !isCapturingPhoto,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("camerax_shutter_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricCyan,
                                    contentColor = SlateNavy950
                                )
                            ) {
                                if (isCapturingPhoto) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = SlateNavy950,
                                        strokeWidth = 2.5.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Capturing Optical Frame...", fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Capture Inspection Photo"
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Capture Inspection Photo", fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (isRecordingVideo) {
                                        activeRecording?.stop()
                                        activeRecording = null
                                    } else {
                                        cameraError = null
                                        val videoFile = File(
                                            context.cacheDir,
                                            "camerax_video_${System.currentTimeMillis()}.mp4"
                                        )
                                        val outputOptions = FileOutputOptions.Builder(videoFile).build()
                                        val hasAudioPerm = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED

                                        val pendingRecording = videoCapture.output
                                            .prepareRecording(context, outputOptions)
                                            .apply {
                                                if (hasAudioPerm) {
                                                    withAudioEnabled()
                                                }
                                            }

                                        videoElapsedSeconds = 0
                                        isRecordingVideo = true
                                        activeRecording = pendingRecording.start(
                                            ContextCompat.getMainExecutor(context)
                                        ) { event ->
                                            when (event) {
                                                is VideoRecordEvent.Finalize -> {
                                                    val recordedSec = videoElapsedSeconds.coerceAtLeast(1)
                                                    isRecordingVideo = false
                                                    activeRecording = null
                                                    if (!event.hasError() || videoFile.exists() && videoFile.length() > 0L) {
                                                        val frameBmp = extractKeyframeFromFile(videoFile)
                                                            ?: previewView.bitmap
                                                        val bytes = try {
                                                            val raw = videoFile.readBytes()
                                                            if (raw.size <= 14 * 1024 * 1024) raw else null
                                                        } catch (_: Exception) {
                                                            null
                                                        }
                                                        onMediaCaptured(
                                                            frameBmp,
                                                            bytes,
                                                            "video/mp4",
                                                            "CameraX Diagnostic Video (${recordedSec}s MP4 + Keyframe)"
                                                        )
                                                    } else {
                                                        cameraError = "Video capture error (${event.error})"
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("camerax_video_record_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isRecordingVideo) HazardCrimson else SafetyAmber,
                                    contentColor = if (isRecordingVideo) MaterialTheme.colorScheme.onError else SlateNavy950
                                )
                            ) {
                                Icon(
                                    imageVector = if (isRecordingVideo) Icons.Default.Stop else Icons.Default.Videocam,
                                    contentDescription = if (isRecordingVideo) "Stop Video" else "Start Video"
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isRecordingVideo) {
                                        "Stop & Attach Video (${videoElapsedSeconds}s)"
                                    } else {
                                        "Record Equipment Diagnostic Video"
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun decodeScaledBitmapFromFile(file: File): Bitmap? {
    return try {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
        var sampleSize = 1
        val maxDim = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
        while (maxDim / sampleSize > 1280) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
    } catch (_: Exception) {
        null
    }
}

private fun extractKeyframeFromFile(videoFile: File): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(videoFile.absolutePath)
        retriever.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
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
