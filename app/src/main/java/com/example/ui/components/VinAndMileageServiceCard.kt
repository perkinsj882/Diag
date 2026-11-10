package com.example.ui.components

import android.Manifest
import android.os.Build
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HazardCrimson
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SignalEmerald
import com.example.util.DecodedVinInfo
import com.example.util.ScheduledCalendarServiceEvent
import com.example.util.ServiceIntervalCalendarSyncManager
import com.example.util.ServiceIntervalEvaluation
import com.example.util.ServiceIntervalNotificationManager
import com.example.util.VinDecoderService
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/**
 * Combined VIN Decoder (NHTSA vPIC REST API) & Vehicle Mileage Service Interval Alert System Card.
 * Placed in Step 1 of DiagnoseLabScreen so mechanics can:
 * 1. Decode a 17-character VIN via the NHTSA vPIC API to auto-populate Year, Make, Model, Engine & Environment.
 * 2. Enter current vehicle mileage to evaluate upcoming OEM service intervals, dispatch local Android notifications,
 *    and sync service intervals (Oil Changes, Brake Inspections, Scheduled Maintenance) with the System Calendar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VinAndMileageServicePanel(
    vinInput: String,
    onVinInputChange: (String) -> Unit,
    mileageInput: String,
    onMileageInputChange: (String) -> Unit,
    vehicleName: String,
    vehicleModel: String,
    autoAlertOnDiagnosis: Boolean,
    onAutoAlertOnDiagnosisChange: (Boolean) -> Unit,
    onVinDecoded: (DecodedVinInfo) -> Unit,
    onAppendServiceNoteToSymptom: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isDecodingVin by remember { mutableStateOf(false) }
    var decodedInfo by remember { mutableStateOf<DecodedVinInfo?>(null) }
    var vinStatusMessage by remember {
        mutableStateOf("Enter a 17-character VIN or tap a sample VIN below to query the NHTSA vPIC API")
    }
    var vinError by remember { mutableStateOf<String?>(null) }
    var notificationStatusText by remember { mutableStateOf<String?>(null) }
    var calendarSyncStatusText by remember { mutableStateOf<String?>(null) }
    var monthlyDrivingMiles by remember { mutableStateOf(1000) }
    var showAllScheduleItems by remember { mutableStateOf(false) }

    val parsedMileage = remember(mileageInput) {
        mileageInput.filter { it.isDigit() }.toLongOrNull() ?: 58_900L
    }

    val serviceEvaluation: ServiceIntervalEvaluation = remember(vehicleName, vehicleModel, parsedMileage) {
        ServiceIntervalNotificationManager.evaluateServiceSchedule(
            vehicleName = vehicleName,
            vehicleModel = vehicleModel,
            mileageMiles = parsedMileage
        )
    }

    val projectedCalendarEvents: List<ScheduledCalendarServiceEvent> = remember(
        serviceEvaluation,
        vehicleName,
        vehicleModel,
        vinInput,
        monthlyDrivingMiles
    ) {
        ServiceIntervalCalendarSyncManager.buildProjectedCalendarSchedule(
            evaluation = serviceEvaluation,
            vehicleName = vehicleName,
            vehicleModel = vehicleModel,
            vin = vinInput,
            monthlyDrivingMiles = monthlyDrivingMiles
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val posted = ServiceIntervalNotificationManager.postServiceIntervalNotification(
                context = context,
                evaluation = serviceEvaluation,
                vehicleName = vehicleName,
                vehicleModel = vehicleModel,
                vin = vinInput
            )
            notificationStatusText = if (posted) {
                "Local system notification sent: ${serviceEvaluation.headlineSummary}"
            } else {
                "Unable to post system notification — check device notification settings."
            }
        } else {
            notificationStatusText = "Notification permission denied. Service interval alerts remain visible on-screen below."
        }
    }

    val calendarSyncPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val summary = ServiceIntervalCalendarSyncManager.syncServiceIntervalsToSystemCalendar(
            context = context,
            evaluation = serviceEvaluation,
            vehicleName = vehicleName,
            vehicleModel = vehicleModel,
            vin = vinInput,
            monthlyDrivingMiles = monthlyDrivingMiles
        )
        calendarSyncStatusText = summary.statusMessage
    }

    fun triggerLocalServiceNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !ServiceIntervalNotificationManager.hasNotificationPermission(context)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            val posted = ServiceIntervalNotificationManager.postServiceIntervalNotification(
                context = context,
                evaluation = serviceEvaluation,
                vehicleName = vehicleName,
                vehicleModel = vehicleModel,
                vin = vinInput
            )
            notificationStatusText = if (posted) {
                "Local system notification sent: ${serviceEvaluation.headlineSummary}"
            } else {
                "Service interval alert evaluated: ${serviceEvaluation.headlineSummary}"
            }
        }
    }

    fun triggerSystemCalendarSync() {
        val hasCalPerm = ServiceIntervalCalendarSyncManager.hasCalendarPermissions(context)
        val hasNotifPerm = ServiceIntervalNotificationManager.hasNotificationPermission(context)
        if (!hasCalPerm || !hasNotifPerm) {
            val perms = buildList {
                add(Manifest.permission.READ_CALENDAR)
                add(Manifest.permission.WRITE_CALENDAR)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }.toTypedArray()
            calendarSyncPermissionLauncher.launch(perms)
        } else {
            val summary = ServiceIntervalCalendarSyncManager.syncServiceIntervalsToSystemCalendar(
                context = context,
                evaluation = serviceEvaluation,
                vehicleName = vehicleName,
                vehicleModel = vehicleModel,
                vin = vinInput,
                monthlyDrivingMiles = monthlyDrivingMiles
            )
            calendarSyncStatusText = summary.statusMessage
        }
    }

    fun executeVinLookup(targetVin: String) {
        val clean = VinDecoderService.sanitizeVin(targetVin)
        onVinInputChange(clean)
        if (clean.length < 11) {
            vinError = "VIN must be 11 to 17 characters (excluding I, O, Q)."
            return
        }
        vinError = null
        isDecodingVin = true
        vinStatusMessage = "Querying NHTSA vPIC API for VIN $clean..."
        scope.launch {
            val result = VinDecoderService.decodeVin(clean)
            isDecodingVin = false
            result.onSuccess { info ->
                decodedInfo = info
                vinError = null
                vinStatusMessage = "Decoded via ${info.sourceLabel}: ${info.year} ${info.make} ${info.model} (${info.engineSummary})"
                onVinDecoded(info)
            }.onFailure { err ->
                vinError = err.localizedMessage ?: "Failed to decode VIN"
                vinStatusMessage = "VIN lookup failed."
            }
        }
    }

    val urgencyColor = when (serviceEvaluation.overallUrgency) {
        "DUE_NOW" -> HazardCrimson
        "UPCOMING_SOON" -> SafetyAmber
        else -> SignalEmerald
    }

    val numFmt = remember { NumberFormat.getIntegerInstance(Locale.US) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. NHTSA vPIC API VIN Decoder Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = ElectricCyan.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(14.dp)
                )
                .testTag("vin_decoder_card"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(ElectricCyan.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "VIN Decoder API",
                                tint = ElectricCyan,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "VIN DECODER (NHTSA vPIC API)",
                                style = MaterialTheme.typography.titleSmall,
                                color = ElectricCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = vinStatusMessage,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ElectricCyan.copy(alpha = 0.14f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${vinInput.length}/17 CHARS",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = vinInput,
                        onValueChange = { raw ->
                            onVinInputChange(VinDecoderService.sanitizeVin(raw))
                        },
                        label = { Text("17-Character Vehicle Identification Number (VIN)") },
                        placeholder = { Text("e.g. 1FTFW1E85MFA10492") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            keyboardType = KeyboardType.Ascii
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("vin_input_field")
                    )

                    Button(
                        onClick = { executeVinLookup(vinInput) },
                        enabled = !isDecodingVin && vinInput.length >= 11,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("decode_vin_button")
                    ) {
                        if (isDecodingVin) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = "Decode VIN",
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Decode VIN", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Quick-Select Real Sample VINs for 1-Tap API Testing
                Text(
                    text = "Quick-Decode Sample Vehicle VINs:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    VinDecoderService.sampleVinPresets.forEach { preset ->
                        FilterChip(
                            selected = vinInput == preset.vin,
                            onClick = { executeVinLookup(preset.vin) },
                            label = {
                                Text(
                                    text = preset.shortLabel,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.testTag("sample_vin_chip_${preset.vin.take(6)}")
                        )
                    }
                }

                if (vinError != null) {
                    Text(
                        text = vinError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = HazardCrimson
                    )
                }

                AnimatedVisibility(visible = decodedInfo != null) {
                    val info = decodedInfo
                    if (info != null) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = SignalEmerald.copy(alpha = 0.12f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    0.5.dp,
                                    SignalEmerald.copy(alpha = 0.45f),
                                    RoundedCornerShape(10.dp)
                                )
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
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
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "VIN Auto-Populated",
                                            tint = SignalEmerald,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "${info.year} ${info.make} ${info.model} ${info.trim}".trim(),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = SignalEmerald,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = info.sourceLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = ElectricCyan
                                    )
                                }
                                Text(
                                    text = "Engine: ${info.engineSummary} • Fuel: ${info.fuelType} • Drive: ${info.driveType} • Body: ${info.bodyClass}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Auto-populated Year/Make/Model, Engine/ECU Code (${info.formattedModelCode}), and Service Environment (${info.recommendedEnvironment}).",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Odometer Mileage & Local Service Interval Notification System Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = urgencyColor.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(14.dp)
                )
                .testTag("mileage_service_interval_card"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(urgencyColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "Service Interval Notifications",
                                tint = urgencyColor,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "MILEAGE & SERVICE INTERVAL ALERT SYSTEM",
                                style = MaterialTheme.typography.titleSmall,
                                color = urgencyColor,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = serviceEvaluation.headlineSummary,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Button(
                        onClick = { triggerLocalServiceNotification() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = urgencyColor,
                            contentColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.testTag("send_service_notification_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "Send Local Service Notification",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Notify Now", fontWeight = FontWeight.Bold)
                    }
                }

                // Odometer Mileage Input & Quick Odometer Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = mileageInput,
                        onValueChange = { raw ->
                            onMileageInputChange(raw.filter { it.isDigit() }.take(7))
                        },
                        label = { Text("Vehicle Odometer Mileage (Miles)") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Odometer Mileage",
                                tint = ElectricCyan
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("diagnostic_mileage_input")
                    )

                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "Auto-Alert on Diagnosis",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Switch(
                            checked = autoAlertOnDiagnosis,
                            onCheckedChange = onAutoAlertOnDiagnosisChange,
                            modifier = Modifier.testTag("auto_service_alert_switch")
                        )
                    }
                }

                // Quick Mileage Milestone Presets
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val mileagePresets = listOf(
                        29_450L to "29,450 mi (Near 30k)",
                        58_900L to "58,900 mi (Near 60k)",
                        89_200L to "89,200 mi (Near 90k)",
                        100_120L to "100,120 mi (100k Due)"
                    )
                    mileagePresets.forEach { (milesVal, label) ->
                        FilterChip(
                            selected = parsedMileage == milesVal,
                            onClick = { onMileageInputChange(milesVal.toString()) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // System Calendar Sync & Push Reminder Bar
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = ElectricCyan.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .testTag("calendar_service_sync_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = "System Calendar Sync",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = "SYSTEM CALENDAR & PUSH REMINDER SYNC",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = ElectricCyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Syncs upcoming Oil Changes, Brake Inspections & Scheduled Maintenance based on driving pace",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val drivingRateOptions = listOf(
                                750 to "750 mi/mo (City)",
                                1000 to "1,000 mi/mo (Avg)",
                                1500 to "1,500 mi/mo (Commuter)",
                                2500 to "2,500 mi/mo (Fleet)"
                            )
                            drivingRateOptions.forEach { (rate, rateLabel) ->
                                FilterChip(
                                    selected = monthlyDrivingMiles == rate,
                                    onClick = { monthlyDrivingMiles = rate },
                                    label = { Text(rateLabel, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }

                        Button(
                            onClick = { triggerSystemCalendarSync() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sync_service_calendar_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.EventAvailable,
                                contentDescription = "Sync Service Intervals to System Calendar",
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sync Oil, Brakes & Maintenance to System Calendar + Push",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (calendarSyncStatusText != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = SignalEmerald.copy(alpha = 0.14f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EventAvailable,
                                contentDescription = "Calendar Synced",
                                tint = SignalEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = calendarSyncStatusText!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = SignalEmerald,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                if (notificationStatusText != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = ElectricCyan.copy(alpha = 0.14f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "Notification Dispatched",
                                tint = ElectricCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = notificationStatusText!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = ElectricCyan,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                // Upcoming & Due Recommended Service Intervals List
                val eventsToDisplay = if (showAllScheduleItems) {
                    projectedCalendarEvents
                } else {
                    projectedCalendarEvents.take(3)
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    eventsToDisplay.forEach { scheduledEvent ->
                        val item = scheduledEvent.item
                        val itemColor = when (item.urgency) {
                            "DUE_NOW" -> HazardCrimson
                            "UPCOMING_SOON" -> SafetyAmber
                            else -> SignalEmerald
                        }
                        val badgeLabel = when {
                            item.milesRemaining < 0 -> "OVERDUE BY ${numFmt.format(-item.milesRemaining)} MI"
                            item.urgency == "DUE_NOW" -> "DUE NOW (${numFmt.format(item.nextDueAtMiles)} MI)"
                            item.urgency == "UPCOMING_SOON" -> "IN ${numFmt.format(item.milesRemaining)} MI (AT ${numFmt.format(item.nextDueAtMiles)} MI)"
                            else -> "NEXT AT ${numFmt.format(item.nextDueAtMiles)} MI"
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                                .border(0.5.dp, itemColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
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
                                    Icon(
                                        imageVector = when (item.urgency) {
                                            "DUE_NOW" -> Icons.Default.Warning
                                            "UPCOMING_SOON" -> Icons.Default.Schedule
                                            else -> Icons.Default.Build
                                        },
                                        contentDescription = item.urgency,
                                        tint = itemColor,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = item.serviceTitle,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(itemColor.copy(alpha = 0.15f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "$badgeLabel • EVERY ${numFmt.format(item.intervalEveryMiles)} MI",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = itemColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(ElectricCyan.copy(alpha = 0.14f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "CALENDAR: ${scheduledEvent.formattedDateLabel.uppercase()} (~${scheduledEvent.estimatedDaysFromNow}D)",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = ElectricCyan,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                Text(
                                    text = item.checklistDetails,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        ServiceIntervalCalendarSyncManager.launchSingleIntervalCalendarIntent(
                                            context = context,
                                            scheduledEvent = scheduledEvent
                                        )
                                        calendarSyncStatusText =
                                            "Opened System Calendar for ${item.serviceTitle} on ${scheduledEvent.formattedDateLabel}."
                                    },
                                    modifier = Modifier.testTag("add_interval_calendar_button_${item.intervalEveryMiles}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = "Add Interval to System Calendar",
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Calendar", style = MaterialTheme.typography.labelSmall)
                                }

                                if (onAppendServiceNoteToSymptom != null) {
                                    OutlinedButton(
                                        onClick = {
                                            onAppendServiceNoteToSymptom(
                                                "[Service Interval Alert @ ${numFmt.format(parsedMileage)} mi]: ${item.serviceTitle} ($badgeLabel, Est. ${scheduledEvent.formattedDateLabel}) — ${item.checklistDetails}"
                                            )
                                        }
                                    ) {
                                        Text("+ Log", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }

                if (serviceEvaluation.recommendedItems.size > 3) {
                    OutlinedButton(
                        onClick = { showAllScheduleItems = !showAllScheduleItems },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (showAllScheduleItems) {
                                "Show Top Priority Intervals Only"
                            } else {
                                "View All ${serviceEvaluation.recommendedItems.size} OEM Mileage Service Intervals"
                            }
                        )
                    }
                }
            }
        }
    }
}
