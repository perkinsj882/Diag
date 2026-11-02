package com.example.ui.screens

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
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.EnvironmentPreset
import com.example.data.model.FlowchartNode
import com.example.data.model.SystemCategory
import com.example.data.model.TroubleshootingFlowchart
import com.example.ui.components.CategoryBadge
import com.example.ui.components.HandsFreeSpeechRecognizerCard
import com.example.ui.components.categoryAccentColor
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HazardCrimson
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SignalEmerald
import com.example.viewmodel.AiTaskState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowchartsScreen(
    flowcharts: List<TroubleshootingFlowchart>,
    activeFlowchartId: String?,
    aiTaskState: AiTaskState,
    onSelectFlowchart: (String) -> Unit,
    onAnswerNode: (TroubleshootingFlowchart, String, String) -> Unit,
    onResetWalkthrough: (TroubleshootingFlowchart) -> Unit,
    onDeleteFlowchart: (String) -> Unit,
    onGenerateAiFlowchart: (
        equipmentModel: String,
        systemCategory: SystemCategory,
        environmentType: String,
        failureModePrompt: String
    ) -> Unit,
    onSeedReferenceLibrary: () -> Unit
) {
    var filterCategory by remember { mutableStateOf<SystemCategory?>(null) }
    var showGeneratorSheet by remember { mutableStateOf(false) }

    var genCategory by remember { mutableStateOf(SystemCategory.COMPUTER_CONTROLLER) }
    var genEquipmentModel by remember { mutableStateOf("Siemens S7-1500 / ControlLogix") }
    var genEnvironment by remember { mutableStateOf(EnvironmentPreset.MANUFACTURING.label) }
    var genFailurePrompt by remember {
        mutableStateOf(SystemCategory.COMPUTER_CONTROLLER.commonFailureModes.first())
    }

    val filteredFlowcharts = remember(flowcharts, filterCategory) {
        if (filterCategory == null) {
            flowcharts
        } else {
            flowcharts.filter {
                it.systemCategory.equals(filterCategory!!.label, ignoreCase = true)
            }
        }
    }

    val activeFlowchart = filteredFlowcharts.firstOrNull { it.id == activeFlowchartId }
        ?: flowcharts.firstOrNull { it.id == activeFlowchartId }
        ?: filteredFlowcharts.firstOrNull()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Interactive Decision Flowcharts",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Guided YES/NO conditional steps to narrow down failure causes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { showGeneratorSheet = !showGeneratorSheet },
                    modifier = Modifier.testTag("toggle_flowchart_generator_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "New AI Flowchart",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (showGeneratorSheet) "Hide Builder" else "New AI Tree")
                }
            }
        }

        // AI Custom Flowchart Builder Panel
        if (showGeneratorSheet) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            ElectricCyan.copy(alpha = 0.55f),
                            RoundedCornerShape(16.dp)
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Generate Custom Interactive Troubleshooting Flowchart",
                            style = MaterialTheme.typography.titleMedium,
                            color = ElectricCyan
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SystemCategory.entries.forEach { cat ->
                                FilterChip(
                                    selected = genCategory == cat,
                                    onClick = {
                                        genCategory = cat
                                        genFailurePrompt = cat.commonFailureModes.firstOrNull().orEmpty()
                                    },
                                    label = { Text("${cat.badgeCode}: ${cat.label}") }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = genEquipmentModel,
                            onValueChange = { genEquipmentModel = it },
                            label = { Text("Target Equipment / Controller / Computer Model") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("flowchart_model_input")
                        )

                        Text(
                            text = "Common Failure Presets for ${genCategory.label}:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            genCategory.commonFailureModes.forEach { mode ->
                                FilterChip(
                                    selected = genFailurePrompt == mode,
                                    onClick = { genFailurePrompt = mode },
                                    label = { Text(mode, maxLines = 1) }
                                )
                            }
                        }

                        HandsFreeSpeechRecognizerCard(
                            onTextRecognized = { spokenText ->
                                genFailurePrompt = if (genFailurePrompt.isBlank()) {
                                    spokenText
                                } else {
                                    "${genFailurePrompt.trimEnd()}. $spokenText"
                                }
                            },
                            title = "Dictate Failure Symptom (SpeechRecognizer)",
                            hintText = "Speak the failure symptom or fault code hands-free to generate a custom Yes/No decision tree.",
                            testTagPrefix = "flowchart_builder_speech"
                        )

                        OutlinedTextField(
                            value = genFailurePrompt,
                            onValueChange = { genFailurePrompt = it },
                            label = { Text("Failure Mode / Symptom to Isolate") },
                            minLines = 2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("flowchart_symptom_input")
                        )

                        Button(
                            onClick = {
                                onGenerateAiFlowchart(
                                    genEquipmentModel,
                                    genCategory,
                                    genEnvironment,
                                    genFailurePrompt
                                )
                            },
                            enabled = aiTaskState !is AiTaskState.Running,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("generate_flowchart_submit_button")
                        ) {
                            if (aiTaskState is AiTaskState.Running) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Building Decision Tree...")
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AccountTree,
                                    contentDescription = "Generate Flowchart"
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Generate Interactive Yes/No Flowchart")
                            }
                        }
                    }
                }
            }
        }

        // Category Filter Chips
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = filterCategory == null,
                    onClick = { filterCategory = null },
                    label = { Text("All Systems (${flowcharts.size})") }
                )
                SystemCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = filterCategory == cat,
                        onClick = {
                            filterCategory = if (filterCategory == cat) null else cat
                        },
                        label = { Text(cat.label) }
                    )
                }
            }
        }

        if (flowcharts.isEmpty()) {
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
                            imageVector = Icons.Default.AccountTree,
                            contentDescription = "No flowcharts yet",
                            tint = ElectricCyan,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "No Interactive Flowcharts Loaded Yet",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Load our pre-engineered decision trees covering PLC Controllers, Rack Servers/Computers, Hydraulics, Pneumatics, 3-Phase Electrical, and CNC Spindles, or generate a custom AI flowchart above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onSeedReferenceLibrary,
                            modifier = Modifier.testTag("empty_flowcharts_seed_button")
                        ) {
                            Text("Load 6 Reference Decision Trees")
                        }
                    }
                }
            }
        }

        // Interactive Decision Tree Player for Selected Flowchart
        if (activeFlowchart != null) {
            item {
                InteractiveFlowchartPlayerCard(
                    flowchart = activeFlowchart,
                    onAnswer = { ansLabel, targetId ->
                        onAnswerNode(activeFlowchart, ansLabel, targetId)
                    },
                    onReset = { onResetWalkthrough(activeFlowchart) }
                )
            }
        }

        // Flowchart Library List
        if (filteredFlowcharts.isNotEmpty()) {
            item {
                Text(
                    text = "Available Troubleshooting Trees (${filteredFlowcharts.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            items(filteredFlowcharts, key = { it.id }) { item ->
                val isSelected = item.id == activeFlowchart?.id
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (isSelected) 1.5.dp else 0.5.dp,
                            color = if (isSelected) ElectricCyan else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onSelectFlowchart(item.id) }
                        .testTag("flowchart_item_${item.id}"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CategoryBadge(categoryLabel = item.systemCategory)
                                Text(
                                    text = if (item.isResolved) "ROOT CAUSE ISOLATED" else "STEP: ${item.currentNodeId}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (item.isResolved) SignalEmerald else SafetyAmber
                                )
                            }
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${item.equipmentModel} • ${item.failureMode}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                        IconButton(onClick = { onDeleteFlowchart(item.id) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Flowchart",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
private fun InteractiveFlowchartPlayerCard(
    flowchart: TroubleshootingFlowchart,
    onAnswer: (answerLabel: String, targetNodeId: String) -> Unit,
    onReset: () -> Unit
) {
    val nodes = remember(flowchart.nodesList) { flowchart.parsedNodes() }
    val currentNode: FlowchartNode = nodes.firstOrNull {
        it.nodeId.equals(flowchart.currentNodeId, ignoreCase = true)
    } ?: nodes.firstOrNull() ?: FlowchartNode(
        nodeId = "N1",
        nodeType = "CONCLUSION",
        prompt = "Inspect primary system connections.",
        recommendedAction = "Reset walkthrough"
    )

    val isConclusion = currentNode.nodeType.equals("CONCLUSION", ignoreCase = true) ||
        (currentNode.yesNodeId.isBlank() && currentNode.noNodeId.isBlank())

    val accent = categoryAccentColor(flowchart.systemCategory)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.5.dp,
                color = if (isConclusion) SignalEmerald else accent,
                shape = RoundedCornerShape(18.dp)
            )
            .testTag("active_flowchart_player"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryBadge(categoryLabel = flowchart.systemCategory)
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.testTag("reset_flowchart_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Restart Flowchart",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Restart at N1")
                }
            }

            Text(
                text = flowchart.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Model: ${flowchart.equipmentModel} • Env: ${flowchart.environmentType}",
                style = MaterialTheme.typography.titleSmall,
                color = ElectricCyan
            )

            // Visual Node Map Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                nodes.forEach { node ->
                    val isCurrent = node.nodeId == currentNode.nodeId
                    val isVisited = flowchart.visitedPathSummary.contains(node.nodeId)
                    val nodeColor = when {
                        isCurrent && isConclusion -> SignalEmerald
                        isCurrent -> SafetyAmber
                        isVisited -> ElectricCyan
                        else -> MaterialTheme.colorScheme.outline
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(nodeColor.copy(alpha = if (isCurrent) 0.25f else 0.1f))
                            .border(1.dp, nodeColor, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = node.nodeId,
                            style = MaterialTheme.typography.labelSmall,
                            color = nodeColor,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // Breadcrumb Trail
            Text(
                text = "Decision Trail: ${flowchart.visitedPathSummary}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            // Active Question or Root Cause Conclusion Box
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isConclusion) {
                        SignalEmerald.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isConclusion) SignalEmerald.copy(alpha = 0.25f)
                                    else SafetyAmber.copy(alpha = 0.25f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isConclusion) Icons.Default.CheckCircle else Icons.AutoMirrored.Filled.HelpOutline,
                                contentDescription = currentNode.nodeType,
                                tint = if (isConclusion) SignalEmerald else SafetyAmber,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = if (isConclusion) {
                                "NODE ${currentNode.nodeId} • ROOT CAUSE ISOLATED"
                            } else {
                                "NODE ${currentNode.nodeId} • DIAGNOSTIC CHECKPOINT"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isConclusion) SignalEmerald else SafetyAmber,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = currentNode.prompt,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (currentNode.observationHint.isNotBlank()) {
                        Text(
                            text = "How to Observe / Measure: ${currentNode.observationHint}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (currentNode.recommendedAction.isNotBlank()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = if (isConclusion) "REQUIRED CORRECTIVE REPAIR ACTION:" else "CHECKPOINT FOCUS:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isConclusion) SignalEmerald else ElectricCyan
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentNode.recommendedAction,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // YES / NO Decision Buttons + Hands-Free Voice Checkpoint Control for QUESTION nodes
            if (!isConclusion) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (currentNode.yesNodeId.isNotBlank()) {
                        Button(
                            onClick = { onAnswer("YES", currentNode.yesNodeId) },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("flowchart_yes_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SignalEmerald,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Yes"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "YES (-> ${currentNode.yesNodeId})",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (currentNode.noNodeId.isNotBlank()) {
                        Button(
                            onClick = { onAnswer("NO", currentNode.noNodeId) },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("flowchart_no_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HazardCrimson,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "No"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "NO (-> ${currentNode.noNodeId})",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                HandsFreeSpeechRecognizerCard(
                    onTextRecognized = { spoken ->
                        val normalized = spoken.trim().lowercase()
                        when {
                            (normalized.contains("yes") || normalized.contains("affirmative") || normalized.contains("pass")) &&
                                currentNode.yesNodeId.isNotBlank() -> {
                                onAnswer("YES", currentNode.yesNodeId)
                            }
                            (normalized.contains("no") || normalized.contains("negative") || normalized.contains("fail")) &&
                                currentNode.noNodeId.isNotBlank() -> {
                                onAnswer("NO", currentNode.noNodeId)
                            }
                            normalized.contains("restart") || normalized.contains("reset") -> {
                                onReset()
                            }
                        }
                    },
                    title = "Hands-Free Checkpoint Voice Control",
                    hintText = "Say \"YES\" (-> ${currentNode.yesNodeId}), \"NO\" (-> ${currentNode.noNodeId}), or \"RESTART\" while keeping both hands on your test probes.",
                    testTagPrefix = "flowchart_voice_checkpoint"
                )
            }
        }
    }
}
