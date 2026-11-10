package com.example.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.DiagnosticSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Exports locally stored diagnostic reports from the Room SQLite database (`diagnostic_reports`)
 * into an RFC-4180 compliant CSV format designed for seamless import into professional
 * Automotive Shop Management Systems (e.g., Tekmetric, Mitchell 1 Manager SE, Shopmonkey, Shop-Ware).
 */
object DiagnosticCsvExporter {

    val SHOP_SMS_CSV_HEADERS: List<String> = listOf(
        "Work_Order_ID",
        "Created_Timestamp_ISO",
        "Updated_Timestamp_ISO",
        "RO_Status",
        "VIN_17",
        "Odometer_Miles",
        "Vehicle_Year_Make_Model",
        "Engine_Trim_ECU_Code",
        "Automotive_Subsystem_Domain",
        "Service_Environment",
        "Customer_Concern_And_OBD_DTCs",
        "Primary_Root_Cause_Diagnosis",
        "Severity_Level",
        "AI_Diagnostic_Confidence_Pct",
        "Estimated_Labor_Minutes",
        "Estimated_Book_Hours",
        "Completed_Steps_Count",
        "Total_Repair_Steps_Count",
        "Completion_Rate_Pct",
        "Technical_Failure_Analysis",
        "Required_Tools_And_Parts",
        "Safety_Precautions",
        "Step_By_Step_Repair_Procedure",
        "Post_Repair_Verification_Test",
        "Mechanic_Voice_Observations_Log",
        "Attached_Telemetry_And_Media",
        "Storage_Origin"
    )

    fun buildSuggestedBatchFileName(recordCount: Int): String {
        val dateStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        return "MechSight_ShopSMS_Diagnostics_${recordCount}ROs_$dateStamp.csv"
    }

    fun buildSuggestedSingleFileName(session: DiagnosticSession): String {
        val cleanModel = session.equipmentModel
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
            .take(24)
            .ifBlank { "Vehicle" }
        val shortId = session.id.take(6).ifBlank { "RO" }
        return "MechSight_ShopSMS_RO_${cleanModel}_$shortId.csv"
    }

    /**
     * Generates an RFC-4180 compliant CSV string (prefixed with UTF-8 BOM for Excel / Shop SMS compatibility)
     * from a list of locally stored Room [DiagnosticSession] records.
     */
    fun buildCsvContent(sessions: List<DiagnosticSession>): String {
        val isoFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val sb = StringBuilder()
        // Include UTF-8 BOM so Microsoft Excel and Shop Management Systems recognize UTF-8 characters immediately
        sb.append('\uFEFF')

        // Header Row
        sb.append(SHOP_SMS_CSV_HEADERS.joinToString(",") { escapeCsvField(it) })
        sb.append("\r\n")

        // Data Rows
        sessions.forEach { session ->
            val createdDate = session.createdAt?.toDate() ?: Date()
            val updatedDate = (session.updatedAt ?: session.createdAt)?.toDate() ?: createdDate

            val totalSteps = session.repairSteps.size
            val completedSteps = session.completedStepIndices.size
            val completionPct = if (totalSteps > 0) {
                (completedSteps * 100) / totalSteps
            } else {
                0
            }

            val laborMinutes = session.effectiveRepairMinutes()
            val bookHours = String.format(Locale.US, "%.2f", laborMinutes / 60.0)

            val formattedSteps = session.repairSteps.mapIndexed { idx, step ->
                val done = session.completedStepIndices.contains(idx.toLong())
                val statusTag = if (done) "[DONE]" else "[OPEN]"
                "Step ${idx + 1} $statusTag: $step"
            }.joinToString(" | ")

            val formattedVoiceNotes = session.parsedVoiceObservations().joinToString(" | ") { obs ->
                "[${obs.timestampLabel} • ${obs.stepTag} • ${obs.categoryTag}] ${obs.transcript}"
            }

            val storageOrigin = when {
                session.hasPendingWrites -> "ROOM_SQLITE_PENDING_CLOUD_SYNC"
                session.isFromCache -> "ROOM_SQLITE_LOCAL_CACHE"
                else -> "ROOM_SQLITE_AND_FIRESTORE_SYNCED"
            }

            val rowValues = listOf(
                session.id.ifBlank { "LOCAL_RO" },
                isoFormatter.format(createdDate),
                isoFormatter.format(updatedDate),
                session.status,
                session.vinNumber.ifBlank { "N/A" },
                if (session.odometerMileage > 0L) session.odometerMileage.toString() else "0",
                session.equipmentName,
                session.equipmentModel,
                session.systemCategory,
                session.environmentType,
                session.symptomDescription,
                session.rootCauseTitle,
                session.severity,
                session.confidencePercent.toString(),
                laborMinutes.toString(),
                bookHours,
                completedSteps.toString(),
                totalSteps.toString(),
                completionPct.toString(),
                session.technicalAnalysis,
                session.requiredTools.joinToString("; "),
                session.safetyWarnings.joinToString("; "),
                formattedSteps,
                session.verificationTest,
                formattedVoiceNotes,
                session.attachedMediaSummary,
                storageOrigin
            )

            sb.append(rowValues.joinToString(",") { escapeCsvField(it) })
            sb.append("\r\n")
        }

        return sb.toString()
    }

    /**
     * Writes the CSV export to a user-selected document [targetUri] via Storage Access Framework.
     */
    suspend fun writeCsvToUri(
        context: Context,
        targetUri: Uri,
        sessions: List<DiagnosticSession>
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val csvText = buildCsvContent(sessions)
            val outputStream = context.contentResolver.openOutputStream(targetUri)
                ?: throw IllegalStateException("Unable to open destination URI for CSV export.")
            outputStream.use { stream ->
                OutputStreamWriter(stream, StandardCharsets.UTF_8).use { writer ->
                    writer.write(csvText)
                    writer.flush()
                }
            }
            sessions.size
        }
    }

    /**
     * Generates a CSV file inside the app's `cacheDir/reports` directory for sharing via [FileProvider].
     */
    suspend fun generateCsvToCacheFile(
        context: Context,
        sessions: List<DiagnosticSession>,
        fileName: String = if (sessions.size == 1) {
            buildSuggestedSingleFileName(sessions.first())
        } else {
            buildSuggestedBatchFileName(sessions.size)
        }
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
            val outFile = File(reportsDir, fileName)
            val csvText = buildCsvContent(sessions)
            FileOutputStream(outFile).use { fos ->
                OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                    writer.write(csvText)
                    writer.flush()
                }
            }
            outFile
        }
    }

    /**
     * Launches Android's Share / Email intent chooser with the generated CSV file attached
     * so shops can import work orders directly into their Shop Management System.
     */
    fun launchShareOrEmailCsv(
        context: Context,
        csvFile: File,
        sessions: List<DiagnosticSession>
    ): Result<Unit> = runCatching {
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, csvFile)

        val resolvedCount = sessions.count { it.status == "RESOLVED" }
        val inProgressCount = sessions.size - resolvedCount
        val totalBookHours = sessions.sumOf { it.effectiveRepairMinutes() } / 60.0

        val subject = if (sessions.size == 1) {
            val s = sessions.first()
            "MechSight Shop SMS CSV Export [RO ${s.id.take(6)}]: ${s.equipmentName} (${s.equipmentModel})"
        } else {
            "MechSight Shop SMS Batch CSV Export (${sessions.size} Diagnostic Work Orders)"
        }

        val body = buildString {
            appendLine("MECHSIGHT AUTOMOTIVE SHOP MANAGEMENT SYSTEM (SMS) CSV EXPORT")
            appendLine("============================================================")
            appendLine("Source Database: On-Device Room SQLite (diagnostic_reports)")
            appendLine("Exported Work Orders: ${sessions.size} ($resolvedCount Resolved, $inProgressCount In Progress)")
            appendLine("Total Estimated Labor: ${String.format(Locale.US, "%.2f", totalBookHours)} Book Hours")
            appendLine("CSV Standard: RFC-4180 UTF-8 (Compatible with Tekmetric, Mitchell 1, Shopmonkey, Shop-Ware, Excel)")
            appendLine()
            appendLine("Included Vehicles & Root Causes:")
            sessions.take(10).forEachIndexed { idx, s ->
                val vinLabel = if (s.vinNumber.isNotBlank()) " | VIN: ${s.vinNumber}" else ""
                val miLabel = if (s.odometerMileage > 0L) " | ${s.odometerMileage} mi" else ""
                appendLine(
                    "${idx + 1}. [${s.status}] ${s.equipmentName} (${s.equipmentModel})$vinLabel$miLabel — ${s.rootCauseTitle}"
                )
            }
            if (sessions.size > 10) {
                appendLine("...and ${sessions.size - 10} additional diagnostic records in the attached CSV.")
            }
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            putExtra(Intent.EXTRA_STREAM, contentUri)
            clipData = ClipData.newRawUri(csvFile.name, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, "Export CSV to Shop Management System").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    /**
     * Escapes a string field according to RFC-4180 CSV rules:
     * wraps in double quotes and doubles internal quotes if the field contains commas, quotes, or newlines.
     */
    private fun escapeCsvField(raw: String): String {
        val normalized = raw
            .replace("\r\n", " ")
            .replace('\n', ' ')
            .replace('\r', ' ')
            .trim()
        val needsQuotes = normalized.contains(',') ||
            normalized.contains('"') ||
            normalized.contains(';') ||
            normalized.contains('\n')
        return if (needsQuotes) {
            "\"${normalized.replace("\"", "\"\"")}\""
        } else {
            normalized
        }
    }
}
