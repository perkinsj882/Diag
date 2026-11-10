package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.SystemCategory
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HazardCrimson
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SignalEmerald

enum class RepairGuideSectionTarget(val label: String, val badgeCode: String) {
    SAFETY_PRECAUTIONS("Shop & Lift Safety Precautions", "SAFETY"),
    REQUIRED_TOOLS("Required Tools & OEM Parts", "TOOLS"),
    REPAIR_STEPS("Step-by-Step Repair Procedure", "STEPS"),
    VERIFICATION_TEST("Post-Repair Verification Test", "VERIFY");

    companion object {
        fun fromString(raw: String): RepairGuideSectionTarget {
            return entries.firstOrNull {
                it.name.equals(raw.trim(), ignoreCase = true) ||
                    it.badgeCode.equals(raw.trim(), ignoreCase = true)
            } ?: REPAIR_STEPS
        }
    }
}

data class IdentifiedAutomotivePart(
    val partName: String,
    val oemPartNumberPattern: String,
    val subsystemCategory: SystemCategory,
    val confidencePercent: Long,
    val partFunctionSummary: String,
    val inspectionSpec: String,
    val commonFailureSymptoms: List<String>,
    val primarySectionTarget: RepairGuideSectionTarget,
    val matchedRepairStepNumber: Int,
    val matchedRepairStepSummary: String,
    val recommendedDiagnosticPrompt: String
)

sealed interface VisualPartIdState {
    data object Idle : VisualPartIdState
    data class Identifying(val statusMessage: String) : VisualPartIdState
    data class Success(
        val part: IdentifiedAutomotivePart,
        val capturedBitmap: Bitmap?
    ) : VisualPartIdState
    data class Error(val message: String) : VisualPartIdState
}

data class CommonPartVisualPreset(
    val shortLabel: String,
    val fullPartName: String,
    val category: SystemCategory,
    val schematicCode: String
)

val commonAutomotivePartPresets: List<CommonPartVisualPreset> = listOf(
    CommonPartVisualPreset(
        shortLabel = "MAF & IAT Sensor",
        fullPartName = "Hot-Wire Mass Air Flow (MAF) & Intake Air Temp Sensor",
        category = SystemCategory.COMPUTER_CONTROLLER,
        schematicCode = "MAF-5V-CAN"
    ),
    CommonPartVisualPreset(
        shortLabel = "VVT Cam Solenoid",
        fullPartName = "Variable Valve Timing (VVT) Oil Control Solenoid Actuator",
        category = SystemCategory.MECHANICAL,
        schematicCode = "VVT-OCV-12V"
    ),
    CommonPartVisualPreset(
        shortLabel = "12V Alternator",
        fullPartName = "Brushless 12V Charging Alternator & LIN-Bus Voltage Regulator",
        category = SystemCategory.ELECTRICAL,
        schematicCode = "ALT-14V4-LIN"
    ),
    CommonPartVisualPreset(
        shortLabel = "iBooster / ABS Unit",
        fullPartName = "Electronic Brake Booster (iBooster) & ABS/ESC Module",
        category = SystemCategory.HYDRAULIC,
        schematicCode = "BRK-IBOOST-ESC"
    ),
    CommonPartVisualPreset(
        shortLabel = "EPAS Steering Rack",
        fullPartName = "Electric Power Assisted Steering (EPAS) Rack & Pinion Assembly",
        category = SystemCategory.PNEUMATIC,
        schematicCode = "EPAS-RACK-48V"
    ),
    CommonPartVisualPreset(
        shortLabel = "Turbo Wastegate",
        fullPartName = "Electronic Turbocharger Wastegate Actuator & Boost Control Solenoid",
        category = SystemCategory.MECHANICAL,
        schematicCode = "TC-EWGA-PWM"
    )
)

/**
 * AI-Powered Visual Automotive Part Identifier Card.
 * Uses CameraX (`CameraCaptureDialog`), Gallery Photo Picker, or 1-tap Optical Part Presets
 * to identify automotive components via Firebase AI Logic (`gemini-3.8-flash`) and provides
 * direct deep-link navigation buttons to the relevant repair guide section.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisualPartIdentifierCard(
    partIdState: VisualPartIdState,
    hasActiveRepairGuide: Boolean,
    activeHighlightedSection: RepairGuideSectionTarget?,
    onIdentifyPartBitmap: (Bitmap, String) -> Unit,
    onJumpToRepairGuideSection: (RepairGuideSectionTarget, Int, IdentifiedAutomotivePart) -> Unit,
    onGenerateRepairGuideForPart: (IdentifiedAutomotivePart, Bitmap?) -> Unit,
    onClearPartIdState: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showPartCameraDialog by remember { mutableStateOf(false) }
    var selectedPresetLabel by remember { mutableStateOf<String?>(null) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }

    val partCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionMessage = null
            showPartCameraDialog = true
        } else {
            permissionMessage =
                "Camera permission denied. You can still upload a part photo from Gallery or tap a sample automotive part below."
        }
    }

    val partPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val bmp = decodePartBitmapFromUri(context, uri)
            if (bmp != null) {
                selectedPresetLabel = "Gallery Part Photo"
                onIdentifyPartBitmap(bmp, "Uploaded automotive component photo from gallery")
            }
        }
    }

    if (showPartCameraDialog) {
        CameraCaptureDialog(
            initialMode = CameraCaptureMode.PHOTO,
            onDismiss = { showPartCameraDialog = false },
            onMediaCaptured = { bmp, _, _, summary ->
                showPartCameraDialog = false
                if (bmp != null) {
                    selectedPresetLabel = "CameraX Live Part Scan"
                    onIdentifyPartBitmap(bmp, "CameraX visual part scan ($summary)")
                }
            }
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = ElectricCyan.copy(alpha = 0.55f),
                shape = RoundedCornerShape(14.dp)
            )
            .testTag("visual_part_identifier_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
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
                            .background(ElectricCyan.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CenterFocusStrong,
                            contentDescription = "AI Visual Part Identification",
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "AI VISUAL PART IDENTIFIER & REPAIR GUIDE LINKER",
                            style = MaterialTheme.typography.titleSmall,
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Point CameraX at an automotive component to identify OEM specs & jump to its repair guide section",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // CameraX Scan & Gallery Photo Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val hasCam = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasCam) {
                            permissionMessage = null
                            showPartCameraDialog = true
                        } else {
                            partCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("scan_part_camerax_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Scan Automotive Part with Camera",
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Scan Part (Camera)", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        partPhotoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pick_part_gallery_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Pick Part Photo from Gallery",
                        tint = ElectricCyan,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Part Photo")
                }
            }

            if (permissionMessage != null) {
                Text(
                    text = permissionMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = SafetyAmber
                )
            }

            // Quick-Scan Common Automotive Parts Bar (for instant testing or bench lookup)
            Text(
                text = "Or Tap a Common Automotive Component for Instant Optical AI Identification:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                commonAutomotivePartPresets.forEach { preset ->
                    FilterChip(
                        selected = selectedPresetLabel == preset.shortLabel,
                        onClick = {
                            selectedPresetLabel = preset.shortLabel
                            val syntheticBmp = renderPartInspectionBitmap(preset)
                            onIdentifyPartBitmap(syntheticBmp, preset.fullPartName)
                        },
                        label = {
                            Text(
                                text = preset.shortLabel,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.testTag(
                            "part_preset_chip_${preset.schematicCode.lowercase()}"
                        )
                    )
                }
            }

            // State Feedback: Loading / Error / Identified Part Result with Direct Repair Guide Links
            when (partIdState) {
                is VisualPartIdState.Idle -> {}

                is VisualPartIdState.Identifying -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = ElectricCyan,
                                strokeWidth = 2.5.dp
                            )
                            Text(
                                text = partIdState.statusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                is VisualPartIdState.Error -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = partIdState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onClearPartIdState) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss Part ID Error"
                                )
                            }
                        }
                    }
                }

                is VisualPartIdState.Success -> {
                    val part = partIdState.part
                    val bmp = partIdState.capturedBitmap
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.5.dp,
                                color = SignalEmerald.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .testTag("identified_part_result_card"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = part.partName,
                                        modifier = Modifier
                                            .size(68.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(
                                                1.dp,
                                                ElectricCyan.copy(alpha = 0.6f),
                                                RoundedCornerShape(8.dp)
                                            ),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        CategoryBadge(categoryLabel = part.subsystemCategory.label)
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(SignalEmerald.copy(alpha = 0.16f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "${part.confidencePercent}% MATCH • ${part.oemPartNumberPattern}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = SignalEmerald,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Text(
                                        text = part.partName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                IconButton(onClick = onClearPartIdState) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear Identified Part"
                                    )
                                }
                            }

                            Text(
                                text = part.partFunctionSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = ElectricCyan.copy(alpha = 0.1f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "OEM BENCH & MULTIMETER SPECIFICATION:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ElectricCyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = part.inspectionSpec,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            if (part.commonFailureSymptoms.isNotEmpty()) {
                                Text(
                                    text = "Associated Fault Codes & Failure Modes: ${part.commonFailureSymptoms.joinToString(" • ")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SafetyAmber
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                            // Direct Deep-Links to Relevant Repair Guide Section
                            Text(
                                text = "DIRECT LINKS TO RELEVANT REPAIR GUIDE SECTION:",
                                style = MaterialTheme.typography.labelSmall,
                                color = SignalEmerald,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Recommended Match: Step ${part.matchedRepairStepNumber} (${part.primarySectionTarget.label}) — ${part.matchedRepairStepSummary}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                RepairGuideSectionTarget.entries.forEach { section ->
                                    val isRecommended = section == part.primarySectionTarget
                                    val isCurrentlyHighlighted = activeHighlightedSection == section
                                    val buttonLabel = when (section) {
                                        RepairGuideSectionTarget.SAFETY_PRECAUTIONS -> "1. Shop Safety"
                                        RepairGuideSectionTarget.REQUIRED_TOOLS -> "2. Tools & Parts"
                                        RepairGuideSectionTarget.REPAIR_STEPS -> "3. Repair Step #${part.matchedRepairStepNumber}"
                                        RepairGuideSectionTarget.VERIFICATION_TEST -> "4. Verification Test"
                                    }

                                    if (isRecommended || isCurrentlyHighlighted) {
                                        Button(
                                            onClick = {
                                                onJumpToRepairGuideSection(
                                                    section,
                                                    part.matchedRepairStepNumber,
                                                    part
                                                )
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = SignalEmerald,
                                                contentColor = MaterialTheme.colorScheme.surface
                                            ),
                                            modifier = Modifier.testTag("jump_repair_section_${section.badgeCode.lowercase()}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDownward,
                                                contentDescription = "Jump to ${section.label}",
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(buttonLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = {
                                                onJumpToRepairGuideSection(
                                                    section,
                                                    part.matchedRepairStepNumber,
                                                    part
                                                )
                                            },
                                            modifier = Modifier.testTag("jump_repair_section_${section.badgeCode.lowercase()}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDownward,
                                                contentDescription = "Jump to ${section.label}",
                                                tint = ElectricCyan,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(buttonLabel, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    onGenerateRepairGuideForPart(part, bmp)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("generate_part_repair_guide_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Generate Tailored Repair Guide for Identified Part",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (hasActiveRepairGuide) {
                                        "Inject Part Specs & Run Tailored Repair Guide for ${part.partName.take(28)}"
                                    } else {
                                        "Generate Tailored Repair Guide for ${part.partName.take(30)}"
                                    },
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun decodePartBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.isMutableRequired = true
                val maxDim = maxOf(info.size.width, info.size.height)
                if (maxDim > 1024) {
                    decoder.setTargetSampleSize(maxDim / 800)
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

/**
 * Generates a clean, high-contrast automotive optical inspection frame for the selected preset part
 * so mechanics can test AI visual part identification even when running on a virtual camera.
 */
private fun renderPartInspectionBitmap(preset: CommonPartVisualPreset): Bitmap {
    val width = 480
    val height = 320
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(14, 22, 34)
        style = Paint.Style.FILL
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

    val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(28, 48, 72)
        strokeWidth = 1.5f
    }
    for (x in 0..width step 40) {
        canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), gridPaint)
    }
    for (y in 0..height step 40) {
        canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), gridPaint)
    }

    val reticlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 210, 255)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    val rect = RectF(44f, 44f, width - 44f, height - 44f)
    canvas.drawRoundRect(rect, 18f, 18f, reticlePaint)

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 22f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(72, 235, 180)
        textSize = 16f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    canvas.drawText("OPTICAL PART SCAN: ${preset.schematicCode}", 64f, 110f, subPaint)
    canvas.drawText(preset.shortLabel.uppercase(), 64f, 155f, titlePaint)
    canvas.drawText("DOMAIN: ${preset.category.label.uppercase()}", 64f, 195f, subPaint)
    canvas.drawText("CAMERAX TELEMETRY FRAME • 1080P", 64f, 235f, subPaint)

    return bmp
}
