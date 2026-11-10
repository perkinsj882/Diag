package com.example.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DiagnosticSession
import com.example.data.model.SystemCategory
import com.example.ui.components.CategoryBadge
import com.example.ui.components.ObdRealtimeRechartsDashboardCard
import com.example.ui.components.SeverityChip
import com.example.ui.components.categoryAccentColor
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HazardCrimson
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SignalEmerald
import kotlin.math.roundToInt

private data class DomainAnalyticsRow(
    val category: SystemCategory,
    val faultCount: Int,
    val avgRepairMinutes: Int,
    val resolvedCount: Int,
    val topFaultTitle: String,
    val accentColor: Color
)

private data class CommonFaultSummary(
    val rootCauseTitle: String,
    val systemCategory: String,
    val equipmentModel: String,
    val count: Int,
    val avgMinutes: Int,
    val severity: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalyticsDashboardScreen(
    diagnosticsList: List<DiagnosticSession>,
    onAdjustRepairDuration: (DiagnosticSession, Int) -> Unit,
    onOpenDiagnosticInHistory: (String) -> Unit,
    onSeedReferenceLibrary: () -> Unit
) {
    var selectedCategoryFilter by remember { mutableStateOf<SystemCategory?>(null) }
    var highlightedDomain by remember { mutableStateOf<SystemCategory?>(null) }

    val filteredList = remember(diagnosticsList, selectedCategoryFilter) {
        if (selectedCategoryFilter == null) {
            diagnosticsList
        } else {
            diagnosticsList.filter {
                it.systemCategory.equals(selectedCategoryFilter!!.label, ignoreCase = true)
            }
        }
    }

    val totalFaults = filteredList.size
    val resolvedFaults = filteredList.count { it.status == "RESOLVED" }
    val resolutionRatePercent = if (totalFaults > 0) {
        ((resolvedFaults.toFloat() / totalFaults.toFloat()) * 100f).roundToInt()
    } else {
        0
    }
    val avgRepairMinutes = if (totalFaults > 0) {
        filteredList.map { it.effectiveRepairMinutes() }.average().roundToInt()
    } else {
        0
    }
    val criticalOrHighCount = filteredList.count {
        it.severity.equals("CRITICAL", ignoreCase = true) ||
            it.severity.equals("HIGH", ignoreCase = true)
    }

    val domainStats = remember(diagnosticsList) {
        SystemCategory.entries.map { cat ->
            val matching = diagnosticsList.filter {
                it.systemCategory.equals(cat.label, ignoreCase = true)
            }
            val avgMin = if (matching.isNotEmpty()) {
                matching.map { it.effectiveRepairMinutes() }.average().roundToInt()
            } else {
                0
            }
            val topCause = matching
                .groupingBy { it.rootCauseTitle }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key
                ?: cat.commonFailureModes.first()

            DomainAnalyticsRow(
                category = cat,
                faultCount = matching.size,
                avgRepairMinutes = avgMin,
                resolvedCount = matching.count { it.status == "RESOLVED" },
                topFaultTitle = topCause,
                accentColor = categoryAccentColor(cat.label)
            )
        }
    }

    val commonFaults = remember(filteredList) {
        filteredList
            .groupBy { it.rootCauseTitle.trim().ifBlank { "General Component Fault" } }
            .map { (title, sessions) ->
                val first = sessions.first()
                CommonFaultSummary(
                    rootCauseTitle = title,
                    systemCategory = first.systemCategory,
                    equipmentModel = first.equipmentModel,
                    count = sessions.size,
                    avgMinutes = sessions.map { it.effectiveRepairMinutes() }.average().roundToInt(),
                    severity = first.severity
                )
            }
            .sortedWith(compareByDescending<CommonFaultSummary> { it.count }.thenByDescending { it.avgMinutes })
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("analytics_dashboard_list"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Real-Time OBD-II Engine Telemetry Recharts Dashboard (RPM, Coolant Temp, STFT/LTFT Fuel Trims)
        item {
            Spacer(modifier = Modifier.height(4.dp))
            ObdRealtimeRechartsDashboardCard()
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        ElectricCyan.copy(alpha = 0.45f),
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
                                imageVector = Icons.Default.BarChart,
                                contentDescription = "Recharts Telemetry Dashboard",
                                tint = ElectricCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "ROOM SQLITE • RELIABILITY & MTTR ANALYTICS",
                                style = MaterialTheme.typography.labelMedium,
                                color = ElectricCyan
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = "Room Database Active",
                                tint = SignalEmerald,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "LOCAL ROOM DB",
                                style = MaterialTheme.typography.labelSmall,
                                color = SignalEmerald
                            )
                        }
                    }

                    Text(
                        text = "Automotive Fault Frequency & Repair Time Dashboard",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "Interactive Recharts telemetry computed directly from your local Room automotive service history, tracking fault distribution across vehicle subsystems and Mean Time To Repair (MTTR).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 4-Metric KPI Strip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KpiMetricCard(
                            label = "TOTAL FAULTS",
                            value = "$totalFaults",
                            unit = "logged",
                            accent = ElectricCyan,
                            modifier = Modifier.weight(1f)
                        )
                        KpiMetricCard(
                            label = "AVG REPAIR",
                            value = "$avgRepairMinutes",
                            unit = "min MTTR",
                            accent = SafetyAmber,
                            modifier = Modifier.weight(1f)
                        )
                        KpiMetricCard(
                            label = "FIX RATE",
                            value = "$resolutionRatePercent%",
                            unit = "$resolvedFaults resolved",
                            accent = SignalEmerald,
                            modifier = Modifier.weight(1f)
                        )
                        KpiMetricCard(
                            label = "HIGH/CRIT",
                            value = "$criticalOrHighCount",
                            unit = "alerts",
                            accent = HazardCrimson,
                            modifier = Modifier.weight(1f)
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
                            imageVector = Icons.Default.BarChart,
                            contentDescription = "Empty Analytics",
                            tint = ElectricCyan,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "No Diagnostic History in Local Room Database",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Seed the local Room SQLite database with 6 multi-domain diagnostic reports to visualize machine fault frequencies and average repair times.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onSeedReferenceLibrary,
                            modifier = Modifier.testTag("analytics_seed_button")
                        ) {
                            Text("Load Reference Diagnostic History")
                        }
                    }
                }
            }
        } else {
            // Chart 1: Recharts-Style Cartesian Bar Chart — Frequency of Machine Faults by Domain
            item {
                RechartsFaultFrequencyCard(
                    domainStats = domainStats,
                    highlightedDomain = highlightedDomain,
                    onSelectDomain = { clicked ->
                        highlightedDomain = if (highlightedDomain == clicked) null else clicked
                    }
                )
            }

            // Chart 2: Recharts-Style Composed Area-Spline & Bar Chart — Average Repair Time (MTTR)
            item {
                RechartsRepairTimeChartCard(
                    domainStats = domainStats,
                    overallAvgMinutes = avgRepairMinutes
                )
            }

            // Domain Filter Chips for Detailed Fault Frequency & Repair Time Log
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Filter Fault Frequency & MTTR Breakdown:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedCategoryFilter == null,
                            onClick = { selectedCategoryFilter = null },
                            label = { Text("All Domains (${diagnosticsList.size})") }
                        )
                        SystemCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = selectedCategoryFilter == cat,
                                onClick = {
                                    selectedCategoryFilter = if (selectedCategoryFilter == cat) null else cat
                                },
                                label = { Text(cat.badgeCode) }
                            )
                        }
                    }
                }
            }

            // Top Recurring Machine Faults Summary Card
            if (commonFaults.isNotEmpty()) {
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
                                text = "Most Frequent Machine Fault Modes",
                                style = MaterialTheme.typography.titleMedium,
                                color = ElectricCyan
                            )

                            val maxCount = commonFaults.maxOf { it.count }.coerceAtLeast(1)
                            commonFaults.take(6).forEach { fault ->
                                val ratio = (fault.count.toFloat() / maxCount.toFloat()).coerceIn(0.12f, 1f)
                                val accent = categoryAccentColor(fault.systemCategory)
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxWidth()
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
                                            CategoryBadge(categoryLabel = fault.systemCategory)
                                            Text(
                                                text = fault.rootCauseTitle,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "${fault.count}x • ${fault.avgMinutes}m avg",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = accent,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { ratio },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = accent
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Individual Session Repair Time Calibration Cards (Backed by Room DB)
            item {
                Text(
                    text = "Logged Service Records & Repair Time Calibration (${filteredList.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            items(filteredList, key = { it.id }) { session ->
                val minutes = session.effectiveRepairMinutes()
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDiagnosticInHistory(session.id) }
                        .testTag("analytics_session_row_${session.id}"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${session.equipmentName} (${session.equipmentModel}) • ${session.environmentType}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (session.status == "RESOLVED") Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = session.status,
                                    tint = if (session.status == "RESOLVED") SignalEmerald else SafetyAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "${session.status} (${session.completedStepIndices.size}/${session.repairSteps.size} steps)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (session.status == "RESOLVED") SignalEmerald else SafetyAmber
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        onAdjustRepairDuration(session, (minutes - 5).coerceAtLeast(5))
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        contentDescription = "Decrease Repair Minutes",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ElectricCyan.copy(alpha = 0.14f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = "Repair Time",
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${minutes}m",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = ElectricCyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        onAdjustRepairDuration(session, (minutes + 5).coerceAtMost(600))
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Increase Repair Minutes",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun KpiMetricCard(
    label: String,
    value: String,
    unit: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.11f))
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = accent,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = unit,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Recharts-inspired interactive Cartesian BarChart showing Machine Fault Frequency by Domain.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RechartsFaultFrequencyCard(
    domainStats: List<DomainAnalyticsRow>,
    highlightedDomain: SystemCategory?,
    onSelectDomain: (SystemCategory) -> Unit
) {
    val maxFaults = domainStats.maxOfOrNull { it.faultCount }?.coerceAtLeast(1) ?: 1
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val selectedStat = domainStats.firstOrNull { it.category == highlightedDomain }
        ?: domainStats.maxByOrNull { it.faultCount }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("fault_frequency_chart_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Frequency of Common Vehicle Faults",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Recharts Cartesian Bar Distribution by Automotive Subsystem",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = "Fault Frequency Bar Chart",
                    tint = ElectricCyan
                )
            }

            // Recharts-style Canvas BarChart with dashed CartesianGrid
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(185.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(horizontal = 12.dp, vertical = 14.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val chartHeight = size.height - 24.dp.toPx()
                    val chartWidth = size.width
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

                    // Draw 4 horizontal Recharts CartesianGrid lines
                    for (i in 0..3) {
                        val y = chartHeight * (i / 3f)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(chartWidth, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = if (i < 3) dashEffect else null
                        )
                    }

                    val slotWidth = chartWidth / domainStats.size.coerceAtLeast(1)
                    val barWidth = (slotWidth * 0.54f).coerceAtMost(42.dp.toPx())

                    domainStats.forEachIndexed { index, row ->
                        val ratio = (row.faultCount.toFloat() / maxFaults.toFloat()).coerceIn(0.06f, 1f)
                        val barHeight = chartHeight * ratio
                        val left = (index * slotWidth) + ((slotWidth - barWidth) / 2f)
                        val top = chartHeight - barHeight
                        val isHighlighted = highlightedDomain == null || highlightedDomain == row.category

                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    row.accentColor.copy(alpha = if (isHighlighted) 0.95f else 0.35f),
                                    row.accentColor.copy(alpha = if (isHighlighted) 0.45f else 0.15f)
                                ),
                                startY = top,
                                endY = chartHeight
                            ),
                            topLeft = Offset(left, top),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )
                    }
                }

                // XAxis labels & counts aligned at the bottom
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    domainStats.forEach { row ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelectDomain(row.category) }
                        ) {
                            Text(
                                text = "${row.faultCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = row.accentColor,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = row.category.badgeCode,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Recharts Interactive Tooltip / Legend Card
            if (selectedStat != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = selectedStat.accentColor.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${selectedStat.category.label} (${selectedStat.category.badgeCode})",
                                style = MaterialTheme.typography.titleSmall,
                                color = selectedStat.accentColor,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${selectedStat.faultCount} Faults • ${selectedStat.resolvedCount} Resolved",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "Primary Fault Mode: ${selectedStat.topFaultTitle}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Recharts-inspired Composed AreaChart + Horizontal Comparison Bars for Average Repair Times (MTTR).
 */
@Composable
private fun RechartsRepairTimeChartCard(
    domainStats: List<DomainAnalyticsRow>,
    overallAvgMinutes: Int
) {
    val maxMinutes = (domainStats.maxOfOrNull { it.avgRepairMinutes } ?: 60).coerceAtLeast(30)
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("average_repair_time_chart_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Average Repair Time (MTTR) by System",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Recharts Area-Spline & Domain Repair Duration (Minutes)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.Timeline,
                    contentDescription = "Average Repair Time Chart",
                    tint = SafetyAmber
                )
            }

            // Recharts-style Smooth Area + Line Chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(14.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

                    for (i in 0..2) {
                        val y = h * (i / 2f)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = if (i < 2) dashEffect else null
                        )
                    }

                    if (domainStats.size >= 2) {
                        val stepX = w / (domainStats.size - 1)
                        val points = domainStats.mapIndexed { idx, stat ->
                            val normalized = (stat.avgRepairMinutes.toFloat() / maxMinutes.toFloat()).coerceIn(0.05f, 0.96f)
                            Offset(idx * stepX, h * (1f - normalized))
                        }

                        val linePath = Path().apply {
                            moveTo(points.first().x, points.first().y)
                            for (i in 1 until points.size) {
                                val prev = points[i - 1]
                                val curr = points[i]
                                val midX = (prev.x + curr.x) / 2f
                                cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                            }
                        }

                        val areaPath = Path().apply {
                            addPath(linePath)
                            lineTo(points.last().x, h)
                            lineTo(points.first().x, h)
                            close()
                        }

                        drawPath(
                            path = areaPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    SafetyAmber.copy(alpha = 0.38f),
                                    ElectricCyan.copy(alpha = 0.05f)
                                )
                            )
                        )

                        drawPath(
                            path = linePath,
                            color = SafetyAmber,
                            style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
                        )

                        points.forEachIndexed { idx, pt ->
                            drawCircle(
                                color = domainStats[idx].accentColor,
                                radius = 5.dp.toPx(),
                                center = pt
                            )
                        }
                    }
                }
            }

            // Domain-by-Domain Horizontal MTTR Bars
            domainStats.forEach { stat ->
                val progress = (stat.avgRepairMinutes.toFloat() / maxMinutes.toFloat()).coerceIn(0.05f, 1f)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.width(120.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(stat.accentColor)
                        )
                        Text(
                            text = stat.category.badgeCode,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = stat.accentColor
                    )
                    Text(
                        text = if (stat.avgRepairMinutes > 0) "${stat.avgRepairMinutes} min" else "—",
                        style = MaterialTheme.typography.labelMedium,
                        color = stat.accentColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(56.dp)
                    )
                }
            }

            Text(
                text = "Fleet Mean Time To Repair (MTTR): $overallAvgMinutes minutes across all logged equipment.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
