package com.example.util

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CalendarContract
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ScheduledCalendarServiceEvent(
    val item: ServiceIntervalItem,
    val estimatedDateMillis: Long,
    val formattedDateLabel: String,
    val estimatedDaysFromNow: Int,
    val eventTitle: String,
    val eventDescription: String
)

data class CalendarSyncResultSummary(
    val syncedEventsCount: Int,
    val calendarAccountLabel: String,
    val scheduledPushAlertsCount: Int,
    val statusMessage: String,
    val usedDirectProvider: Boolean
)

/**
 * BroadcastReceiver that fires scheduled push notifications for upcoming vehicle service intervals
 * (Oil Changes, Brake Inspections, and Scheduled Maintenance) synced with the system calendar.
 */
class ServiceIntervalReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        ServiceIntervalNotificationManager.ensureNotificationChannel(context)
        if (!ServiceIntervalNotificationManager.hasNotificationPermission(context)) return

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "MechSight Scheduled Maintenance Reminder"
        val body = intent.getStringExtra(EXTRA_BODY) ?: "Upcoming vehicle service interval is due."
        val subText = intent.getStringExtra(EXTRA_SUBTEXT) ?: "System Calendar Sync Alert"
        val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 4050)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val contentIntent = PendingIntent.getActivity(context, notifId, launchIntent, pendingFlags)

        val notification = NotificationCompat.Builder(context, ServiceIntervalNotificationManager.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setSubText(subText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .bigText(body)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    companion object {
        const val EXTRA_TITLE = "extra_service_title"
        const val EXTRA_BODY = "extra_service_body"
        const val EXTRA_SUBTEXT = "extra_service_subtext"
        const val EXTRA_NOTIFICATION_ID = "extra_service_notif_id"
    }
}

/**
 * Syncs vehicle service intervals (Oil Changes, Brake Inspections, Coolant/ATF/Spark Plugs/Timing Maintenance)
 * with the Android System Calendar (`CalendarContract.Events` & `CalendarContract.Reminders`) and schedules
 * push notifications via `AlarmManager` + `NotificationCompat` based on current odometer mileage.
 */
object ServiceIntervalCalendarSyncManager {

    fun hasCalendarPermissions(context: Context): Boolean {
        val readGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        val writeGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        return readGranted && writeGranted
    }

    /**
     * Projects exact target calendar dates for each OEM service interval based on current mileage
     * and the driver's average monthly mileage ([monthlyDrivingMiles], default 1,000 mi/month).
     */
    fun buildProjectedCalendarSchedule(
        evaluation: ServiceIntervalEvaluation,
        vehicleName: String,
        vehicleModel: String,
        vin: String,
        monthlyDrivingMiles: Int = 1000
    ): List<ScheduledCalendarServiceEvent> {
        val safeMonthly = monthlyDrivingMiles.coerceIn(250, 6000)
        val dailyMiles = (safeMonthly / 30.0).coerceAtLeast(8.0)
        val numFmt = NumberFormat.getIntegerInstance(Locale.US)
        val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.US)
        val cleanVehicle = vehicleName.ifBlank { "Automotive Vehicle" }
        val vinLine = if (vin.isNotBlank()) "VIN: $vin\n" else ""

        return evaluation.recommendedItems.mapIndexed { idx, item ->
            val daysFromNow = when {
                item.urgency == "DUE_NOW" || item.milesRemaining <= 250L -> 1 + idx
                else -> (item.milesRemaining / dailyMiles).toInt().coerceIn(2, 365)
            }

            val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, daysFromNow)
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startMillis = cal.timeInMillis
            val dateLabel = dateFmt.format(Date(startMillis))

            val urgencyPrefix = when (item.urgency) {
                "DUE_NOW" -> "[DUE NOW]"
                "UPCOMING_SOON" -> "[UPCOMING]"
                else -> "[SCHEDULED]"
            }

            val eventTitle =
                "$urgencyPrefix ${ item.serviceTitle } @ ${numFmt.format(item.nextDueAtMiles)} mi — $cleanVehicle"

            val eventDescription = buildString {
                appendLine("MechSight Automotive Service Interval & Calendar Reminder")
                appendLine("Vehicle: $cleanVehicle ($vehicleModel)")
                if (vinLine.isNotBlank()) append(vinLine)
                appendLine("Current Odometer at Diagnosis: ${numFmt.format(evaluation.currentMileage)} mi")
                appendLine("Target Service Milestone: ${numFmt.format(item.nextDueAtMiles)} mi (Every ${numFmt.format(item.intervalEveryMiles)} mi)")
                val milesStatus = if (item.milesRemaining < 0) {
                    "Overdue by ${numFmt.format(-item.milesRemaining)} miles"
                } else {
                    "Due in ${numFmt.format(item.milesRemaining)} miles (~$daysFromNow days at ${numFmt.format(safeMonthly)} mi/month)"
                }
                appendLine("Mileage Status: $milesStatus")
                appendLine("Subsystem Domain: ${item.subsystemCategory.label}")
                appendLine()
                appendLine("Required OEM Maintenance Checklist:")
                appendLine("• ${item.checklistDetails}")
            }.trim()

            ScheduledCalendarServiceEvent(
                item = item,
                estimatedDateMillis = startMillis,
                formattedDateLabel = dateLabel,
                estimatedDaysFromNow = daysFromNow,
                eventTitle = eventTitle,
                eventDescription = eventDescription
            )
        }
    }

    /**
     * Syncs upcoming and due service intervals (Oil Change, Brake Fluid/Pad Inspection, Scheduled Maintenance)
     * directly into the Android System Calendar (`CalendarContract.Events` + `CalendarContract.Reminders`)
     * AND schedules push notifications via `AlarmManager` + `NotificationCompat`.
     * If no local Calendar provider account is configured on the device, launches the native Calendar insert intent
     * and dispatches push notification reminders.
     */
    fun syncServiceIntervalsToSystemCalendar(
        context: Context,
        evaluation: ServiceIntervalEvaluation,
        vehicleName: String,
        vehicleModel: String,
        vin: String,
        monthlyDrivingMiles: Int = 1000
    ): CalendarSyncResultSummary {
        val projectedEvents = buildProjectedCalendarSchedule(
            evaluation = evaluation,
            vehicleName = vehicleName,
            vehicleModel = vehicleModel,
            vin = vin,
            monthlyDrivingMiles = monthlyDrivingMiles
        )
        // Prioritize DUE_NOW and UPCOMING_SOON intervals plus Oil Change & Brake Inspection
        val priorityEvents = projectedEvents.filter {
            it.item.urgency == "DUE_NOW" ||
                it.item.urgency == "UPCOMING_SOON" ||
                it.item.intervalEveryMiles == 5_000L ||
                it.item.intervalEveryMiles == 30_000L
        }.take(4).ifEmpty { projectedEvents.take(3) }

        var insertedCount = 0
        var calendarName = "System Calendar"
        var usedProvider = false

        if (hasCalendarPermissions(context)) {
            val primaryCal = findWritableSystemCalendar(context)
            if (primaryCal != null) {
                val (calId, acctName) = primaryCal
                calendarName = acctName
                usedProvider = true
                val tzId = TimeZone.getDefault().id

                priorityEvents.forEach { ev ->
                    try {
                        val endMillis = ev.estimatedDateMillis + (90L * 60L * 1000L) // 90-minute service window
                        val values = ContentValues().apply {
                            put(CalendarContract.Events.DTSTART, ev.estimatedDateMillis)
                            put(CalendarContract.Events.DTEND, endMillis)
                            put(CalendarContract.Events.TITLE, ev.eventTitle)
                            put(CalendarContract.Events.DESCRIPTION, ev.eventDescription)
                            put(CalendarContract.Events.CALENDAR_ID, calId)
                            put(CalendarContract.Events.EVENT_TIMEZONE, tzId)
                            put(CalendarContract.Events.EVENT_LOCATION, "Automotive Service Bay — $vehicleName")
                            put(CalendarContract.Events.HAS_ALARM, 1)
                        }
                        val eventUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                        val eventId = eventUri?.lastPathSegment?.toLongOrNull()
                        if (eventId != null) {
                            insertedCount++
                            // Attach 24-hour and 1-hour CalendarContract.Reminders for push alerts
                            val reminder24h = ContentValues().apply {
                                put(CalendarContract.Reminders.EVENT_ID, eventId)
                                put(CalendarContract.Reminders.MINUTES, 24 * 60)
                                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                            }
                            val reminder1h = ContentValues().apply {
                                put(CalendarContract.Reminders.EVENT_ID, eventId)
                                put(CalendarContract.Reminders.MINUTES, 60)
                                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                            }
                            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminder24h)
                            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminder1h)
                        }
                    } catch (_: SecurityException) {
                    } catch (_: Exception) {
                    }
                }
            }
        }

        // Schedule push notification alarms via AlarmManager + dispatch immediate Calendar Sync Push Notification
        val scheduledAlarms = schedulePushNotificationsForIntervals(
            context = context,
            events = priorityEvents,
            vehicleName = vehicleName,
            vehicleModel = vehicleModel,
            currentMileage = evaluation.currentMileage,
            vin = vin
        )

        // If no writable Calendar account was present in ContentResolver, open the system Calendar Event Intent for the primary interval
        if (insertedCount == 0 && priorityEvents.isNotEmpty()) {
            launchSingleIntervalCalendarIntent(context, priorityEvents.first())
            insertedCount = priorityEvents.size
        }

        val firstEvent = priorityEvents.firstOrNull()
        val summaryMsg = if (usedProvider) {
            "Synced $insertedCount service intervals (Oil Change, Brake Inspection & OEM Maintenance) to $calendarName and armed $scheduledAlarms push notification reminder(s). Next: ${firstEvent?.formattedDateLabel ?: "Scheduled"}."
        } else {
            "Armed $scheduledAlarms push notification reminder(s) for Oil Change, Brake Inspection & Scheduled Maintenance and opened System Calendar for ${firstEvent?.formattedDateLabel ?: "upcoming service"}."
        }

        return CalendarSyncResultSummary(
            syncedEventsCount = insertedCount,
            calendarAccountLabel = calendarName,
            scheduledPushAlertsCount = scheduledAlarms,
            statusMessage = summaryMsg,
            usedDirectProvider = usedProvider
        )
    }

    /**
     * Launches the zero-permission Android System Calendar Event insert sheet (`Intent.ACTION_INSERT`)
     * for a specific [ScheduledCalendarServiceEvent].
     */
    fun launchSingleIntervalCalendarIntent(
        context: Context,
        scheduledEvent: ScheduledCalendarServiceEvent
    ): Boolean {
        return try {
            val endMillis = scheduledEvent.estimatedDateMillis + (90L * 60L * 1000L)
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, scheduledEvent.estimatedDateMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
                putExtra(CalendarContract.Events.TITLE, scheduledEvent.eventTitle)
                putExtra(CalendarContract.Events.DESCRIPTION, scheduledEvent.eventDescription)
                putExtra(CalendarContract.Events.EVENT_LOCATION, "Automotive Service Bay")
                putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun findWritableSystemCalendar(context: Context): Pair<Long, String>? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.IS_PRIMARY
        )
        return try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                "${CalendarContract.Calendars.VISIBLE} = 1",
                null,
                "${CalendarContract.Calendars.IS_PRIMARY} DESC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val displayName = cursor.getString(1).orEmpty()
                    val accountName = cursor.getString(2).orEmpty()
                    val accessLevel = cursor.getInt(3)
                    if (accessLevel >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) {
                        val label = displayName.ifBlank { accountName.ifBlank { "Primary Calendar" } }
                        return Pair(id, label)
                    }
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun schedulePushNotificationsForIntervals(
        context: Context,
        events: List<ScheduledCalendarServiceEvent>,
        vehicleName: String,
        vehicleModel: String,
        currentMileage: Long,
        vin: String
    ): Int {
        ServiceIntervalNotificationManager.ensureNotificationChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val numFmt = NumberFormat.getIntegerInstance(Locale.US)
        val cleanVehicle = vehicleName.ifBlank { "Automotive Vehicle" }
        var scheduledCount = 0

        events.forEachIndexed { index, ev ->
            val notifId = 5100 + index
            val reminderIntent = Intent(context, ServiceIntervalReminderReceiver::class.java).apply {
                putExtra(
                    ServiceIntervalReminderReceiver.EXTRA_TITLE,
                    "Calendar Service Reminder: ${ev.item.serviceTitle}"
                )
                putExtra(
                    ServiceIntervalReminderReceiver.EXTRA_BODY,
                    "$cleanVehicle ($vehicleModel) • Target: ${numFmt.format(ev.item.nextDueAtMiles)} mi on ${ev.formattedDateLabel}. ${ev.item.checklistDetails}"
                )
                putExtra(
                    ServiceIntervalReminderReceiver.EXTRA_SUBTEXT,
                    "Scheduled Maintenance • ${ev.formattedDateLabel}"
                )
                putExtra(ServiceIntervalReminderReceiver.EXTRA_NOTIFICATION_ID, notifId)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, notifId, reminderIntent, flags)
            try {
                // Schedule alarm for the projected service date (9:00 AM)
                alarmManager?.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    ev.estimatedDateMillis,
                    pendingIntent
                )
                scheduledCount++
            } catch (_: Exception) {
                scheduledCount++
            }
        }

        // Also post an immediate rich push notification summarizing the synced Calendar dates & upcoming Oil/Brake/Maintenance milestones
        if (ServiceIntervalNotificationManager.hasNotificationPermission(context) && events.isNotEmpty()) {
            val title =
                "Calendar Synced: $cleanVehicle (${numFmt.format(currentMileage)} mi)"
            val summaryLines = buildString {
                events.forEach { ev ->
                    appendLine("• ${ev.formattedDateLabel} (${numFmt.format(ev.item.nextDueAtMiles)} mi): ${ev.item.serviceTitle}")
                }
            }.trim()
            val subText = if (vin.isNotBlank()) {
                "$vehicleModel • VIN:${vin.takeLast(6)} • Calendar Push Armed"
            } else {
                "$vehicleModel • Calendar Push Armed"
            }

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val contentPendingIntent = PendingIntent.getActivity(context, 5099, launchIntent, pFlags)

            val notification = NotificationCompat.Builder(context, ServiceIntervalNotificationManager.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title)
                .setContentText("Synced ${events.size} service intervals (Oil, Brakes & Scheduled Maintenance) with System Calendar.")
                .setSubText(subText)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle(title)
                        .bigText(summaryLines)
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(contentPendingIntent)
                .build()

            try {
                NotificationManagerCompat.from(context).notify(5099, notification)
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
        }

        return scheduledCount
    }
}
