package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.DiagnosticSession
import com.example.data.model.EnvironmentPreset
import com.example.data.model.EquipmentProfile
import com.example.data.model.SystemCategory
import com.example.ui.components.CategoryBadge
import com.example.ui.components.SeverityChip
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.SignalEmerald

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EquipmentFleetScreen(
    equipmentList: List<EquipmentProfile>,
    diagnosticsList: List<DiagnosticSession>,
    onAddEquipment: (
        name: String,
        modelNumber: String,
        systemCategory: SystemCategory,
        environmentType: String,
        operatingHours: Long,
        notes: String
    ) -> Unit,
    onDeleteEquipment: (String) -> Unit,
    onDiagnoseEquipment: (EquipmentProfile) -> Unit,
    onOpenDiagnosticSession: (String) -> Unit,
    onDeleteDiagnosticSession: (String) -> Unit,
    onSeedReferenceLibrary: () -> Unit
) {
    var showAddForm by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var modelNumber by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(SystemCategory.COMPUTER_CONTROLLER) }
    var environment by remember { mutableStateOf(EnvironmentPreset.MANUFACTURING.label) }
    var hoursText by remember { mutableStateOf("1200") }
    var notes by remember { mutableStateOf("") }

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
                        text = "Equipment Registry & Repair Logs",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Manage models across all 6 domains & review historical repair guides.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { showAddForm = !showAddForm },
                    modifier = Modifier.testTag("toggle_add_equipment_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Register Equipment",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (showAddForm) "Close" else "Add Model")
                }
            }
        }

        if (showAddForm) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Register Equipment, Controller, or Computer Asset",
                            style = MaterialTheme.typography.titleMedium,
                            color = ElectricCyan
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SystemCategory.entries.forEach { cat ->
                                FilterChip(
                                    selected = category == cat,
                                    onClick = { category = cat },
                                    label = { Text("${cat.badgeCode}: ${cat.label}") }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Asset Name") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("new_eq_name_input")
                            )
                            OutlinedTextField(
                                value = modelNumber,
                                onValueChange = { modelNumber = it },
                                label = { Text("Model / Part #") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("new_eq_model_input")
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            EnvironmentPreset.entries.forEach { env ->
                                FilterChip(
                                    selected = environment == env.label,
                                    onClick = { environment = env.label },
                                    label = { Text(env.label) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = hoursText,
                                onValueChange = { hoursText = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Operating Hours") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(0.4f)
                            )
                            OutlinedTextField(
                                value = notes,
                                onValueChange = { notes = it },
                                label = { Text("Firmware / Fluid / Spec Notes") },
                                singleLine = true,
                                modifier = Modifier.weight(0.6f)
                            )
                        }

                        Button(
                            onClick = {
                                onAddEquipment(
                                    name,
                                    modelNumber,
                                    category,
                                    environment,
                                    hoursText.toLongOrNull() ?: 0L,
                                    notes
                                )
                                name = ""
                                modelNumber = ""
                                notes = ""
                                showAddForm = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_equipment_button")
                        ) {
                            Text("Save Equipment Profile")
                        }
                    }
                }
            }
        }

        if (equipmentList.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "No Equipment Profiles Registered",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Load reference equipment across Mechanical, Electrical, Hydraulic, Pneumatic, Computer Controller, and Computer categories.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = onSeedReferenceLibrary) {
                            Text("Load Reference Fleet")
                        }
                    }
                }
            }
        } else {
            item {
                Text(
                    text = "Registered Fleet (${equipmentList.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(equipmentList, key = { it.id }) { eq ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
                            CategoryBadge(categoryLabel = eq.systemCategory)
                            Text(
                                text = "${eq.operatingHours} hrs • ${eq.environmentType}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = eq.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Model: ${eq.modelNumber}",
                            style = MaterialTheme.typography.titleSmall,
                            color = ElectricCyan
                        )
                        if (eq.notes.isNotBlank()) {
                            Text(
                                text = eq.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { onDiagnoseEquipment(eq) },
                                modifier = Modifier.testTag("diagnose_eq_${eq.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Diagnose",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Diagnose This Model")
                            }
                            IconButton(onClick = { onDeleteEquipment(eq.id) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Equipment",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Saved Diagnostic Repair Sessions
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Saved Repair Guides & Diagnostic History (${diagnosticsList.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        if (diagnosticsList.isEmpty()) {
            item {
                Text(
                    text = "No diagnostic repair sessions saved yet. Run a multimodal diagnosis in the Diagnose tab.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(diagnosticsList, key = { it.id }) { diag ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDiagnosticSession(diag.id) },
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
                                CategoryBadge(categoryLabel = diag.systemCategory)
                                SeverityChip(
                                    severity = diag.severity,
                                    confidencePercent = diag.confidencePercent
                                )
                            }
                            Text(
                                text = diag.rootCauseTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${diag.equipmentName} (${diag.equipmentModel}) • ${diag.completedStepIndices.size}/${diag.repairSteps.size} steps • ${diag.status}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (diag.status == "RESOLVED") SignalEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onDeleteDiagnosticSession(diag.id) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Diagnostic Session",
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
