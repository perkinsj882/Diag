package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.model.SystemCategory
import java.text.NumberFormat
import java.util.Locale

data class ServiceIntervalItem(
    val intervalEveryMiles: Long,
    val nextDueAtMiles: Long,
    val milesRemaining: Long,
    val urgency: String, // "DUE_NOW", "UPCOMING_SOON", "SCHEDULED"
    val serviceTitle: String,
    val subsystemCategory: SystemCategory,
    val checklistDetails: String
)

data class ServiceIntervalEvaluation(
    val currentMileage: Long,
    val nextMilestoneMiles: Long,
    val milesUntilNextMilestone: Long,
    val overallUrgency: String, // "DUE_NOW", "UPCOMING_SOON", "ON_SCHEDULE"
    val headlineSummary: String,
    val notificationBody: String,
    val recommendedItems: List<ServiceIntervalItem>
)

object ServiceIntervalNotificationManager {

    const val CHANNEL_ID = "mechsight_service_intervals"
    private const val CHANNEL_NAME = "Vehicle Service Interval Alerts"
    private const val CHANNEL_DESCRIPTION =
        "Local alerts for upcoming and due automotive maintenance intervals based on diagnostic odometer mileage."

    private data class OemScheduleTemplate(
        val intervalMiles: Long,
        val upcomingWindowMiles: Long,
        val overdueGraceMiles: Long,
        val title: String,
        val category: SystemCategory,
        val details: String
    )

    private val oemSchedules = listOf(
        OemScheduleTemplate(
            intervalMiles = 5_000L,
            upcomingWindowMiles = 600L,
            overdueGraceMiles = 350L,
            title = "Engine Oil & Filter + Tire Rotation",
            category = SystemCategory.MECHANICAL,
            details = "Replace full-synthetic OEM viscosity engine oil & filter, rotate tires, and measure brake pad lining thickness."
        ),
        OemScheduleTemplate(
            intervalMiles = 15_000L,
            upcomingWindowMiles = 1_200L,
            overdueGraceMiles = 500L,
            title = "Engine Air Filter, Cabin Filter & Battery Load Test",
            category = SystemCategory.ELECTRICAL,
            details = "Replace engine intake air element and HVAC cabin microfilter; perform 12V battery conductance & charging ripple test."
        ),
        OemScheduleTemplate(
            intervalMiles = 30_000L,
            upcomingWindowMiles = 2_000L,
            overdueGraceMiles = 750L,
            title = "Brake Pads/Rotors, Electronic Brake Booster & DOT 4 Fluid Service",
            category = SystemCategory.HYDRAULIC,
            details = "Measure pad & rotor thickness, run scan-tool bleed for electronic brake booster / ABS module, and lubricate caliper slide pins & EPB actuators."
        ),
        OemScheduleTemplate(
            intervalMiles = 50_000L,
            upcomingWindowMiles = 2_500L,
            overdueGraceMiles = 900L,
            title = "Engine / Inverter Coolant Flush & PCV Valve Service",
            category = SystemCategory.MECHANICAL,
            details = "Flush radiator/heater core coolant loop, test thermostat & pressure cap, and replace positive crankcase ventilation (PCV) valve."
        ),
        OemScheduleTemplate(
            intervalMiles = 60_000L,
            upcomingWindowMiles = 2_500L,
            overdueGraceMiles = 1_000L,
            title = "Transmission Fluid (ATF), Transfer Case & Differential Service",
            category = SystemCategory.MECHANICAL,
            details = "Drain/refill automatic transmission fluid & filter, service 4WD/AWD transfer case and axle differentials, and reset TCM adaptive shift tables."
        ),
        OemScheduleTemplate(
            intervalMiles = 90_000L,
            upcomingWindowMiles = 3_000L,
            overdueGraceMiles = 1_200L,
            title = "Spark Plugs, Ignition Coils & Serpentine Accessory Belt",
            category = SystemCategory.ELECTRICAL,
            details = "Replace iridium spark plugs and coil boots, inspect direct-injection high-pressure pump tappet, and replace serpentine belt & tensioner."
        ),
        OemScheduleTemplate(
            intervalMiles = 100_000L,
            upcomingWindowMiles = 3_500L,
            overdueGraceMiles = 1_500L,
            title = "EPAS Electric Steering Rack, Tie Rods & Suspension Inspection",
            category = SystemCategory.PNEUMATIC,
            details = "Inspect EPAS electric rack & pinion bellows, inner/outer tie rod play, steering torque/angle calibration, struts, wheel bearings & control arm bushings."
        )
    )

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHANNEL_DESCRIPTION
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Evaluates upcoming and due service intervals for the given [mileageMiles].
     */
    fun evaluateServiceSchedule(
        vehicleName: String,
        vehicleModel: String,
        mileageMiles: Long
    ): ServiceIntervalEvaluation {
        val safeMileage = mileageMiles.coerceIn(0L, 1_500_000L)
        val numFmt = NumberFormat.getIntegerInstance(Locale.US)

        val evaluatedItems = oemSchedules.map { spec ->
            if (safeMileage == 0L) {
                ServiceIntervalItem(
                    intervalEveryMiles = spec.intervalMiles,
                    nextDueAtMiles = spec.intervalMiles,
                    milesRemaining = spec.intervalMiles,
                    urgency = "SCHEDULED",
                    serviceTitle = spec.title,
                    subsystemCategory = spec.category,
                    checklistDetails = spec.details
                )
            } else {
                val completedCycles = safeMileage / spec.intervalMiles
                val lastMilestone = completedCycles * spec.intervalMiles
                val milesSinceLast = safeMileage - lastMilestone
                val nextMilestone = (completedCycles + 1) * spec.intervalMiles
                val milesUntilNext = nextMilestone - safeMileage

                // If right at or slightly past a milestone (within overdueGraceMiles), mark DUE_NOW
                val isJustPastMilestone = completedCycles >= 1 && milesSinceLast <= spec.overdueGraceMiles
                val isApproachingMilestone = milesUntilNext <= spec.upcomingWindowMiles

                when {
                    isJustPastMilestone -> ServiceIntervalItem(
                        intervalEveryMiles = spec.intervalMiles,
                        nextDueAtMiles = lastMilestone,
                        milesRemaining = -milesSinceLast,
                        urgency = "DUE_NOW",
                        serviceTitle = spec.title,
                        subsystemCategory = spec.category,
                        checklistDetails = spec.details
                    )
                    milesUntilNext <= 250L -> ServiceIntervalItem(
                        intervalEveryMiles = spec.intervalMiles,
                        nextDueAtMiles = nextMilestone,
                        milesRemaining = milesUntilNext,
                        urgency = "DUE_NOW",
                        serviceTitle = spec.title,
                        subsystemCategory = spec.category,
                        checklistDetails = spec.details
                    )
                    isApproachingMilestone -> ServiceIntervalItem(
                        intervalEveryMiles = spec.intervalMiles,
                        nextDueAtMiles = nextMilestone,
                        milesRemaining = milesUntilNext,
                        urgency = "UPCOMING_SOON",
                        serviceTitle = spec.title,
                        subsystemCategory = spec.category,
                        checklistDetails = spec.details
                    )
                    else -> ServiceIntervalItem(
                        intervalEveryMiles = spec.intervalMiles,
                        nextDueAtMiles = nextMilestone,
                        milesRemaining = milesUntilNext,
                        urgency = "SCHEDULED",
                        serviceTitle = spec.title,
                        subsystemCategory = spec.category,
                        checklistDetails = spec.details
                    )
                }
            }
        }.sortedWith(
            compareBy<ServiceIntervalItem> {
                when (it.urgency) {
                    "DUE_NOW" -> 0
                    "UPCOMING_SOON" -> 1
                    else -> 2
                }
            }.thenBy { it.milesRemaining }
        )

        val dueNowItems = evaluatedItems.filter { it.urgency == "DUE_NOW" }
        val upcomingSoonItems = evaluatedItems.filter { it.urgency == "UPCOMING_SOON" }
        val primaryItem = evaluatedItems.first()

        val overallUrgency = when {
            dueNowItems.isNotEmpty() -> "DUE_NOW"
            upcomingSoonItems.isNotEmpty() -> "UPCOMING_SOON"
            else -> "ON_SCHEDULE"
        }

        val cleanVehicle = vehicleName.ifBlank { "Vehicle" }
        val formattedCurrent = "${numFmt.format(safeMileage)} mi"
        val formattedTarget = "${numFmt.format(primaryItem.nextDueAtMiles)} mi"

        val headline = when (overallUrgency) {
            "DUE_NOW" -> "SERVICE DUE NOW at $formattedTarget (${dueNowItems.size} OEM Interval${if (dueNowItems.size > 1) "s" else ""})"
            "UPCOMING_SOON" -> "UPCOMING SERVICE in ${numFmt.format(primaryItem.milesRemaining.coerceAtLeast(0L))} mi (at $formattedTarget)"
            else -> "Next Scheduled Service at $formattedTarget (in ${numFmt.format(primaryItem.milesRemaining.coerceAtLeast(0L))} mi)"
        }

        val priorityList = (dueNowItems + upcomingSoonItems).ifEmpty { evaluatedItems.take(2) }
        val notificationBody = buildString {
            appendLine("$cleanVehicle ($vehicleModel) • Odometer: $formattedCurrent")
            priorityList.take(3).forEach { item ->
                val badge = when {
                    item.milesRemaining < 0 -> "OVERDUE by ${numFmt.format(-item.milesRemaining)} mi (${numFmt.format(item.nextDueAtMiles)} mi interval)"
                    item.milesRemaining == 0L -> "DUE AT ${numFmt.format(item.nextDueAtMiles)} mi"
                    else -> "Due in ${numFmt.format(item.milesRemaining)} mi (at ${numFmt.format(item.nextDueAtMiles)} mi)"
                }
                appendLine("• ${item.serviceTitle}: $badge")
            }
        }.trim()

        return ServiceIntervalEvaluation(
            currentMileage = safeMileage,
            nextMilestoneMiles = primaryItem.nextDueAtMiles,
            milesUntilNextMilestone = primaryItem.milesRemaining,
            overallUrgency = overallUrgency,
            headlineSummary = headline,
            notificationBody = notificationBody,
            recommendedItems = evaluatedItems
        )
    }

    /**
     * Posts a local system notification alerting the mechanic/owner of upcoming or due
     * recommended service intervals based on the mileage entered during diagnostics.
     */
    fun postServiceIntervalNotification(
        context: Context,
        evaluation: ServiceIntervalEvaluation,
        vehicleName: String,
        vehicleModel: String,
        vin: String = ""
    ): Boolean {
        ensureNotificationChannel(context)
        if (!hasNotificationPermission(context)) {
            return false
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            2048,
            launchIntent,
            pendingIntentFlags
        )

        val numFmt = NumberFormat.getIntegerInstance(Locale.US)
        val cleanName = vehicleName.ifBlank { "Automotive Vehicle" }
        val vinSuffix = if (vin.isNotBlank()) " • VIN:${vin.takeLast(6)}" else ""
        val title = "MechSight Service Alert: $cleanName (${numFmt.format(evaluation.currentMileage)} mi)"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(evaluation.headlineSummary)
            .setSubText("${vehicleModel.ifBlank { "OBD-II" }}$vinSuffix")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .bigText("${evaluation.headlineSummary}\n${evaluation.notificationBody}")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        return try {
            val notificationId = (cleanName.hashCode() and 0x7FFFFFFF).coerceAtLeast(1001)
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Posts a high-priority local notification warning the mechanic that the vehicle's 12V battery
     * is critically low during an active OBD-II diagnostic session.
     */
    fun postObdLowBatteryWarningNotification(
        context: Context,
        adapterName: String,
        voltage: Double,
        sessionMinutes: Int,
        estimatedRemainingMinutes: Int,
        warningMessage: String
    ): Boolean {
        ensureNotificationChannel(context)
        if (!hasNotificationPermission(context)) {
            return false
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            4096,
            launchIntent,
            pendingIntentFlags
        )

        val voltFormatted = String.format(Locale.US, "%.2fV", voltage)
        val title = "LOW VEHICLE BATTERY ($voltFormatted) • OBD-II Session Alert"
        val details = buildString {
            appendLine("Adapter: ${adapterName.ifBlank { "ELM327 BLE" }} • Session Active: ${sessionMinutes}m")
            appendLine("Safe KOEO Time Left: ~${estimatedRemainingMinutes} min before 11.5V ECU brownout cutoff.")
            appendLine(warningMessage)
        }.trim()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(warningMessage)
            .setSubText("OBD-II 12V Bus Monitor")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .bigText(details)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(8842, notification)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }
}
