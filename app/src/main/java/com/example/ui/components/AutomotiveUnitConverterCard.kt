package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SignalEmerald
import com.example.util.AutomotiveMeasurementPreset
import com.example.util.AutomotiveUnitConverter
import com.example.util.AutomotiveUnitSystem
import com.example.util.MeasurementCategory
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutomotiveUnitConverterCard(
    unitSystem: AutomotiveUnitSystem,
    onUnitSystemChange: (AutomotiveUnitSystem) -> Unit,
    onAppendConvertedNoteToPrompt: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(true) }
    var selectedCategory by remember { mutableStateOf(MeasurementCategory.TORQUE) }
    var selectedPreset by remember {
        mutableStateOf<AutomotiveMeasurementPreset?>(
            AutomotiveUnitConverter.PRESETS.first { it.category == MeasurementCategory.TORQUE }
        )
    }

    // Track input value string in the currently active unit system
    var inputValueText by remember { mutableStateOf("135.0") }

    // Helper to switch unit system and convert the currently entered number automatically
    fun toggleOrSetUnitSystem(targetSystem: AutomotiveUnitSystem) {
        if (targetSystem == unitSystem) return
        val currentVal = inputValueText.toDoubleOrNull()
        if (currentVal != null) {
            val convertedVal = when (selectedCategory) {
                MeasurementCategory.TORQUE -> {
                    if (targetSystem == AutomotiveUnitSystem.IMPERIAL) {
                        AutomotiveUnitConverter.nmToLbFt(currentVal)
                    } else {
                        AutomotiveUnitConverter.lbFtToNm(currentVal)
                    }
                }
                MeasurementCategory.PRESSURE -> {
                    if (targetSystem == AutomotiveUnitSystem.IMPERIAL) {
                        AutomotiveUnitConverter.barToPsi(currentVal)
                    } else {
                        AutomotiveUnitConverter.psiToBar(currentVal)
                    }
                }
                MeasurementCategory.TEMPERATURE -> {
                    if (targetSystem == AutomotiveUnitSystem.IMPERIAL) {
                        AutomotiveUnitConverter.celsiusToFahrenheit(currentVal)
                    } else {
                        AutomotiveUnitConverter.fahrenheitToCelsius(currentVal)
                    }
                }
            }
            inputValueText = String.format(Locale.US, "%.1f", convertedVal)
        }
        onUnitSystemChange(targetSystem)
    }

    val numericInput = inputValueText.toDoubleOrNull() ?: 0.0
    val conversionResult = remember(selectedCategory, numericInput, unitSystem) {
        AutomotiveUnitConverter.convert(
            category = selectedCategory,
            inputValue = numericInput,
            inputSystem = unitSystem
        )
    }

    val categoryPresets = remember(selectedCategory) {
        AutomotiveUnitConverter.PRESETS.filter { it.category == selectedCategory }
    }

    val activeInputUnitLabel = when (selectedCategory) {
        MeasurementCategory.TORQUE ->
            if (unitSystem == AutomotiveUnitSystem.METRIC) "Nm (Newton-meters)" else "lb-ft (Foot-pounds)"
        MeasurementCategory.PRESSURE ->
            if (unitSystem == AutomotiveUnitSystem.METRIC) "bar (100 kPa)" else "PSI (lb/in²)"
        MeasurementCategory.TEMPERATURE ->
            if (unitSystem == AutomotiveUnitSystem.METRIC) "°C (Celsius)" else "°F (Fahrenheit)"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = ElectricCyan.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp)
            )
            .testTag("automotive_unit_converter_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row + Global Metric / Imperial Toggle Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { isExpanded = !isExpanded }
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ElectricCyan.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Automotive Unit Converter",
                            tint = ElectricCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Automotive Unit Converter Utility",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (unitSystem == AutomotiveUnitSystem.METRIC) {
                                            ElectricCyan.copy(alpha = 0.2f)
                                        } else {
                                            SafetyAmber.copy(alpha = 0.2f)
                                        }
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = unitSystem.label.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (unitSystem == AutomotiveUnitSystem.METRIC) ElectricCyan else SafetyAmber,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = "Torque (Nm ↔ lb-ft/lb-in) • Pressure (bar/kPa ↔ PSI) • Temp (°C ↔ °F)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        val next = if (unitSystem == AutomotiveUnitSystem.METRIC) {
                            AutomotiveUnitSystem.IMPERIAL
                        } else {
                            AutomotiveUnitSystem.METRIC
                        }
                        toggleOrSetUnitSystem(next)
                    },
                    modifier = Modifier.testTag("unit_converter_swap_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = "Toggle Metric or Imperial",
                        tint = if (unitSystem == AutomotiveUnitSystem.METRIC) ElectricCyan else SafetyAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (unitSystem == AutomotiveUnitSystem.METRIC) "To SAE" else "To Metric",
                        color = if (unitSystem == AutomotiveUnitSystem.METRIC) ElectricCyan else SafetyAmber,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Metric vs Imperial Segmented Toggle Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = unitSystem == AutomotiveUnitSystem.METRIC,
                    onClick = { toggleOrSetUnitSystem(AutomotiveUnitSystem.METRIC) },
                    label = {
                        Text(
                            text = "Metric (Nm • bar/kPa • °C)",
                            fontWeight = if (unitSystem == AutomotiveUnitSystem.METRIC) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricCyan.copy(alpha = 0.22f),
                        selectedLabelColor = ElectricCyan
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("unit_system_toggle_metric")
                )
                FilterChip(
                    selected = unitSystem == AutomotiveUnitSystem.IMPERIAL,
                    onClick = { toggleOrSetUnitSystem(AutomotiveUnitSystem.IMPERIAL) },
                    label = {
                        Text(
                            text = "Imperial (lb-ft • PSI • °F)",
                            fontWeight = if (unitSystem == AutomotiveUnitSystem.IMPERIAL) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SafetyAmber.copy(alpha = 0.22f),
                        selectedLabelColor = SafetyAmber
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("unit_system_toggle_imperial")
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { isExpanded = !isExpanded }
                        .padding(6.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse Converter" else "Expand Converter",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                    // Category Selection Chips: Torque, Pressure, Temperature
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MeasurementCategory.entries.forEach { cat ->
                            val isSelected = selectedCategory == cat
                            val catIcon = when (cat) {
                                MeasurementCategory.TORQUE -> Icons.Default.Build
                                MeasurementCategory.PRESSURE -> Icons.Default.Speed
                                MeasurementCategory.TEMPERATURE -> Icons.Default.Thermostat
                            }
                            val catColor = when (cat) {
                                MeasurementCategory.TORQUE -> ElectricCyan
                                MeasurementCategory.PRESSURE -> SafetyAmber
                                MeasurementCategory.TEMPERATURE -> SignalEmerald
                            }
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedCategory = cat
                                    val defaultPreset = AutomotiveUnitConverter.PRESETS.first { it.category == cat }
                                    selectedPreset = defaultPreset
                                    val defaultVal = if (unitSystem == AutomotiveUnitSystem.METRIC) {
                                        defaultPreset.metricValue
                                    } else {
                                        defaultPreset.imperialValue
                                    }
                                    inputValueText = String.format(Locale.US, "%.1f", defaultVal)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = catIcon,
                                        contentDescription = cat.title,
                                        tint = if (isSelected) catColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text(cat.title, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = catColor.copy(alpha = 0.2f),
                                    selectedLabelColor = catColor
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("unit_category_chip_${cat.name.lowercase()}")
                            )
                        }
                    }

                    // Input Field + Live Dual-System Readout Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inputValueText,
                            onValueChange = { newVal ->
                                if (newVal.length <= 10 && newVal.all { it.isDigit() || it == '.' || it == '-' }) {
                                    inputValueText = newVal
                                }
                            },
                            label = { Text("Enter ${selectedCategory.title} ($activeInputUnitLabel)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("unit_converter_value_input")
                        )
                    }

                    // Side-by-Side Metric (SI) and Imperial (SAE) Readout Tiles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Metric Tile
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (unitSystem == AutomotiveUnitSystem.METRIC) {
                                        ElectricCyan.copy(alpha = 0.14f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                )
                                .border(
                                    width = if (unitSystem == AutomotiveUnitSystem.METRIC) 1.5.dp else 1.dp,
                                    color = if (unitSystem == AutomotiveUnitSystem.METRIC) {
                                        ElectricCyan
                                    } else {
                                        ElectricCyan.copy(alpha = 0.35f)
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .padding(10.dp)
                                .testTag("unit_converter_metric_readout")
                        ) {
                            Text(
                                text = "METRIC (SI)",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = conversionResult.metricPrimaryFormatted,
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = conversionResult.metricSecondaryFormatted,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Imperial Tile
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (unitSystem == AutomotiveUnitSystem.IMPERIAL) {
                                        SafetyAmber.copy(alpha = 0.14f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                )
                                .border(
                                    width = if (unitSystem == AutomotiveUnitSystem.IMPERIAL) 1.5.dp else 1.dp,
                                    color = if (unitSystem == AutomotiveUnitSystem.IMPERIAL) {
                                        SafetyAmber
                                    } else {
                                        SafetyAmber.copy(alpha = 0.35f)
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .padding(10.dp)
                                .testTag("unit_converter_imperial_readout")
                        ) {
                            Text(
                                text = "IMPERIAL (SAE)",
                                style = MaterialTheme.typography.labelSmall,
                                color = SafetyAmber,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = conversionResult.imperialPrimaryFormatted,
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = conversionResult.imperialSecondaryFormatted,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // OEM Automotive Fastener / Fluid / Thermal Reference Presets
                    Text(
                        text = "Quick OEM ${selectedCategory.title} Reference Specs:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categoryPresets.forEach { preset ->
                            val isPresetSelected = selectedPreset?.id == preset.id
                            val chipValueStr = if (unitSystem == AutomotiveUnitSystem.METRIC) {
                                "${preset.metricValue} ${selectedCategory.metricPrimaryUnit}"
                            } else {
                                "${preset.imperialValue} ${selectedCategory.imperialPrimaryUnit}"
                            }
                            FilterChip(
                                selected = isPresetSelected,
                                onClick = {
                                    selectedPreset = preset
                                    val targetVal = if (unitSystem == AutomotiveUnitSystem.METRIC) {
                                        preset.metricValue
                                    } else {
                                        preset.imperialValue
                                    }
                                    inputValueText = String.format(Locale.US, "%.1f", targetVal)
                                },
                                label = {
                                    Text(
                                        text = "${preset.componentLabel}: $chipValueStr",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier.testTag("unit_preset_chip_${preset.id}")
                            )
                        }
                    }

                    // Active OEM Preset Note + 1-Tap Log to Diagnostic Report Button
                    selectedPreset?.let { preset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SignalEmerald.copy(alpha = 0.1f))
                                .border(0.5.dp, SignalEmerald.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${preset.componentLabel} — ${conversionResult.metricPrimaryFormatted} / ${conversionResult.imperialPrimaryFormatted}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SignalEmerald,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = preset.oemApplicationNote,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val presetHeader = selectedPreset?.let { "${it.componentLabel} — " } ?: ""
                            val note = "[Unit Spec] $presetHeader${conversionResult.shopNoteSummary}"
                            onAppendConvertedNoteToPrompt(note)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("insert_converted_unit_spec_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = ElectricCyan
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircleOutline,
                            contentDescription = "Insert Converted Spec",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Insert Converted ${selectedCategory.title} Spec into Diagnostic Notes",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
