package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.SystemCategory
import com.example.ui.theme.ControllerViolet
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HazardCrimson
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.SlateNavy800
import com.example.ui.theme.SlateNavy900
import kotlin.math.cos
import kotlin.math.sin

fun categoryIcon(categoryLabel: String): ImageVector {
    return when (SystemCategory.fromLabel(categoryLabel)) {
        SystemCategory.MECHANICAL -> Icons.Default.Settings
        SystemCategory.ELECTRICAL -> Icons.Default.Bolt
        SystemCategory.HYDRAULIC -> Icons.Default.Album
        SystemCategory.PNEUMATIC -> Icons.Default.DirectionsCar
        SystemCategory.COMPUTER_CONTROLLER -> Icons.Default.DeveloperBoard
    }
}

fun categoryAccentColor(categoryLabel: String): Color {
    return when (SystemCategory.fromLabel(categoryLabel)) {
        SystemCategory.MECHANICAL -> SafetyAmber
        SystemCategory.ELECTRICAL -> Color(0xFFFFD740)
        SystemCategory.HYDRAULIC -> Color(0xFF40C4FF)
        SystemCategory.PNEUMATIC -> SignalEmerald
        SystemCategory.COMPUTER_CONTROLLER -> ControllerViolet
    }
}

fun severityColor(severity: String): Color {
    return when (severity.uppercase()) {
        "CRITICAL" -> HazardCrimson
        "HIGH" -> SafetyAmber
        "MODERATE" -> ElectricCyan
        else -> SignalEmerald
    }
}

@Composable
fun CategoryBadge(
    categoryLabel: String,
    modifier: Modifier = Modifier
) {
    val category = SystemCategory.fromLabel(categoryLabel)
    val accent = categoryAccentColor(categoryLabel)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = categoryIcon(categoryLabel),
            contentDescription = category.label,
            tint = accent,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = "${category.badgeCode} • ${category.label}",
            style = MaterialTheme.typography.labelMedium,
            color = accent
        )
    }
}

@Composable
fun SeverityChip(
    severity: String,
    confidencePercent: Long,
    modifier: Modifier = Modifier
) {
    val color = severityColor(severity)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${severity.uppercase()} • ${confidencePercent}% CONF",
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SchematicHeroBanner(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "hud_pulse")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveform_phase"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(148.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(SlateNavy900, SlateNavy800)
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(ElectricCyan.copy(alpha = 0.6f), SafetyAmber.copy(alpha = 0.5f))
                ),
                shape = RoundedCornerShape(18.dp)
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val gridStep = 28.dp.toPx()

            // Technical Blueprint Grid
            var x = 0f
            while (x < w) {
                drawLine(
                    color = ElectricCyan.copy(alpha = 0.06f),
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = 1f
                )
                x += gridStep
            }
            var y = 0f
            while (y < h) {
                drawLine(
                    color = ElectricCyan.copy(alpha = 0.06f),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f
                )
                y += gridStep
            }

            // Animated Telemetry Sine/Harmonic Waveform
            val wavePath = Path()
            val samples = 60
            for (i in 0..samples) {
                val px = (i.toFloat() / samples) * w
                val norm = (i.toFloat() / samples) * 6f * Math.PI.toFloat()
                val py = h * 0.72f + sin(norm + phase) * (h * 0.11f) + cos(norm * 2.2f - phase) * (h * 0.05f)
                if (i == 0) wavePath.moveTo(px, py) else wavePath.lineTo(px, py)
            }
            drawPath(
                path = wavePath,
                color = ElectricCyan.copy(alpha = 0.42f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // Automotive Tachometer / Rotor Schematic in top-right
            val cx = w * 0.86f
            val cy = h * 0.42f
            val rOuter = h * 0.28f
            drawCircle(
                color = SafetyAmber.copy(alpha = 0.25f),
                radius = rOuter,
                center = Offset(cx, cy),
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = ElectricCyan.copy(alpha = 0.35f),
                radius = rOuter * 0.55f,
                center = Offset(cx, cy),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(ElectricCyan.copy(alpha = 0.18f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Engine & Powertrain • Brakes • Steering & Suspension • Battery & Charging • ECU & CAN-Bus",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
