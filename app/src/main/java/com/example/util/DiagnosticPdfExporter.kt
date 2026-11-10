package com.example.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.DiagnosticSession
import com.example.data.model.TroubleshootingFlowchart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticPdfExporter {

    private const val PAGE_WIDTH = 595 // Standard A4 width in PostScript points (72 dpi)
    private const val PAGE_HEIGHT = 842 // Standard A4 height in PostScript points
    private const val MARGIN_HORIZONTAL = 36f
    private const val MARGIN_TOP = 36f
    private const val MARGIN_BOTTOM = 44f
    private const val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN_HORIZONTAL * 2)

    fun buildSuggestedFileName(session: DiagnosticSession): String {
        val cleanModel = session.equipmentModel
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
            .take(24)
            .ifBlank { "Equipment" }
        val shortId = session.id.take(6).ifBlank { "Report" }
        return "MechSight_Service_Report_${cleanModel}_$shortId.pdf"
    }

    suspend fun generatePdfToCacheFile(
        context: Context,
        session: DiagnosticSession,
        companionFlowchart: TroubleshootingFlowchart? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
            val outFile = File(reportsDir, buildSuggestedFileName(session))
            FileOutputStream(outFile).use { fos ->
                renderDiagnosticPdf(session, companionFlowchart, fos)
            }
            outFile
        }
    }

    suspend fun writePdfToUri(
        context: Context,
        targetUri: Uri,
        session: DiagnosticSession,
        companionFlowchart: TroubleshootingFlowchart? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val outStream = context.contentResolver.openOutputStream(targetUri)
                ?: throw IllegalStateException("Unable to open destination URI for writing PDF.")
            outStream.use { stream ->
                renderDiagnosticPdf(session, companionFlowchart, stream)
            }
        }
    }

    fun launchEmailOrSharePdf(
        context: Context,
        pdfFile: File,
        session: DiagnosticSession
    ): Result<Unit> = runCatching {
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, pdfFile)

        val completedSteps = session.completedStepIndices.size
        val totalSteps = session.repairSteps.size
        val emailSubject =
            "Service Record [${session.status}]: ${session.equipmentName} (${session.equipmentModel}) - ${session.rootCauseTitle}"

        val emailBody = buildString {
            appendLine("MECHSIGHT AUTOMOTIVE DIAGNOSTIC & MAINTENANCE SERVICE RECORD")
            appendLine("============================================================")
            appendLine("Session ID: ${session.id.ifBlank { "LOCAL-RECORD" }}")
            appendLine("Status: ${session.status} ($completedSteps of $totalSteps repair steps completed)")
            appendLine("Vehicle / Subsystem: ${session.equipmentName} (Model: ${session.equipmentModel})")
            if (session.vinNumber.isNotBlank() || session.odometerMileage > 0L) {
                appendLine("VIN: ${session.vinNumber.ifBlank { "N/A" }} | Odometer Mileage: ${if (session.odometerMileage > 0L) "${session.odometerMileage} mi" else "N/A"}")
            }
            appendLine("Subsystem Domain: ${session.systemCategory} | Environment: ${session.environmentType}")
            appendLine("Primary Root Cause: ${session.rootCauseTitle} (${session.severity} - ${session.confidencePercent}% Confidence)")
            appendLine()
            appendLine("Reported Symptom / OBD-II DTC:")
            appendLine(session.symptomDescription)
            appendLine()
            appendLine("Technical Failure Analysis:")
            appendLine(session.technicalAnalysis)
            appendLine()
            appendLine("Post-Repair Verification Test:")
            appendLine(session.verificationTest)
            val voiceNotes = session.parsedVoiceObservations()
            if (voiceNotes.isNotEmpty()) {
                appendLine()
                appendLine("Mechanic Voice-to-Text Field Observations (${voiceNotes.size}):")
                voiceNotes.forEach { obs ->
                    appendLine("• [${obs.timestampLabel}] [${obs.stepTag} | ${obs.categoryTag}] ${obs.transcript}")
                }
            }
            appendLine()
            appendLine("Please find the complete formatted PDF Automotive Service Report attached for the vehicle maintenance log.")
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, emailSubject)
            putExtra(Intent.EXTRA_TEXT, emailBody)
            putExtra(Intent.EXTRA_STREAM, contentUri)
            clipData = ClipData.newRawUri(pdfFile.name, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, "Email or Save PDF Service Report").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun renderDiagnosticPdf(
        session: DiagnosticSession,
        companionFlowchart: TroubleshootingFlowchart?,
        outputStream: OutputStream
    ) {
        val pdfDocument = PdfDocument()
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
            val sessionDate = (session.updatedAt ?: session.createdAt)?.toDate() ?: Date()
            val formattedTimestamp = dateFormat.format(sessionDate)

            val headerFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(13, 19, 30)
                style = Paint.Style.FILL
            }
            val accentCyanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(0, 145, 190)
                style = Paint.Style.FILL
            }
            val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(244, 247, 251)
                style = Paint.Style.FILL
            }
            val warningBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(255, 246, 229)
                style = Paint.Style.FILL
            }
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(200, 212, 226)
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            val warningBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(230, 145, 0)
                style = Paint.Style.STROKE
                strokeWidth = 1.2f
            }

            val titleWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 15f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val subtitleCyanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(79, 216, 255)
                textSize = 8.5f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }
            val sectionHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(13, 45, 82)
                textSize = 11.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val bodyBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(20, 28, 40)
                textSize = 9.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val bodyRegularPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(38, 48, 64)
                textSize = 9.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            val monoSmallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(75, 88, 108)
                textSize = 8f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            }
            val resolvedBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (session.status == "RESOLVED") Color.rgb(16, 150, 92) else Color.rgb(218, 130, 0)
                textSize = 9f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }

            var pageNumber = 1
            var currentPage = pdfDocument.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            )
            var canvas = currentPage.canvas
            var cursorY = MARGIN_TOP

            fun drawPageFooter(targetCanvas: Canvas, pageNum: Int) {
                val footerY = PAGE_HEIGHT - 18f
                targetCanvas.drawLine(
                    MARGIN_HORIZONTAL,
                    footerY - 12f,
                    PAGE_WIDTH - MARGIN_HORIZONTAL,
                    footerY - 12f,
                    borderPaint
                )
                targetCanvas.drawText(
                    "MechSight Maintenance & Diagnostic Service Log • Session ID: ${session.id.ifBlank { "LOCAL" }}",
                    MARGIN_HORIZONTAL,
                    footerY,
                    monoSmallPaint
                )
                val pageLabel = "Page $pageNum"
                val width = monoSmallPaint.measureText(pageLabel)
                targetCanvas.drawText(
                    pageLabel,
                    PAGE_WIDTH - MARGIN_HORIZONTAL - width,
                    footerY,
                    monoSmallPaint
                )
            }

            fun ensureSpace(requiredHeight: Float) {
                if (cursorY + requiredHeight > PAGE_HEIGHT - MARGIN_BOTTOM) {
                    drawPageFooter(canvas, pageNumber)
                    pdfDocument.finishPage(currentPage)
                    pageNumber += 1
                    currentPage = pdfDocument.startPage(
                        PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    )
                    canvas = currentPage.canvas
                    cursorY = MARGIN_TOP
                }
            }

            // 1. Top Industrial Header Banner
            val bannerHeight = 64f
            val bannerRect = RectF(
                MARGIN_HORIZONTAL,
                cursorY,
                PAGE_WIDTH - MARGIN_HORIZONTAL,
                cursorY + bannerHeight
            )
            canvas.drawRoundRect(bannerRect, 8f, 8f, headerFillPaint)
            canvas.drawText(
                "MECHSIGHT AUTOMOTIVE DIAGNOSTIC & SERVICE REPORT",
                MARGIN_HORIZONTAL + 14f,
                cursorY + 24f,
                titleWhitePaint
            )
            val cacheOrigin = if (session.isFromCache) "FIRESTORE ON-DEVICE CACHE" else "FIRESTORE CLOUD + LOCAL ARCHIVE"
            canvas.drawText(
                "DOC ID: ${session.id.ifBlank { "FIELD-REPORT" }}  |  SOURCE: $cacheOrigin",
                MARGIN_HORIZONTAL + 14f,
                cursorY + 41f,
                subtitleCyanPaint
            )
            canvas.drawText(
                "GENERATED: $formattedTimestamp  |  STATUS: ${session.status}",
                MARGIN_HORIZONTAL + 14f,
                cursorY + 54f,
                subtitleCyanPaint
            )
            cursorY += bannerHeight + 14f

            // 2. Equipment & Operating Environment Metadata Card
            val metaBoxHeight = 76f
            ensureSpace(metaBoxHeight + 10f)
            val metaRect = RectF(
                MARGIN_HORIZONTAL,
                cursorY,
                PAGE_WIDTH - MARGIN_HORIZONTAL,
                cursorY + metaBoxHeight
            )
            canvas.drawRoundRect(metaRect, 6f, 6f, cardBgPaint)
            canvas.drawRoundRect(metaRect, 6f, 6f, borderPaint)

            canvas.drawText(
                "EQUIPMENT: ${session.equipmentName}",
                MARGIN_HORIZONTAL + 12f,
                cursorY + 18f,
                bodyBoldPaint
            )
            canvas.drawText(
                "MODEL / SERIAL: ${session.equipmentModel}",
                MARGIN_HORIZONTAL + 290f,
                cursorY + 18f,
                bodyBoldPaint
            )
            canvas.drawText(
                "SYSTEM DOMAIN: ${session.systemCategory}",
                MARGIN_HORIZONTAL + 12f,
                cursorY + 36f,
                bodyRegularPaint
            )
            canvas.drawText(
                "ENVIRONMENT: ${session.environmentType}",
                MARGIN_HORIZONTAL + 290f,
                cursorY + 36f,
                bodyRegularPaint
            )
            val completedCount = session.completedStepIndices.size
            val totalSteps = session.repairSteps.size
            canvas.drawText(
                "SEVERITY: ${session.severity} (${session.confidencePercent}% CONFIDENCE)",
                MARGIN_HORIZONTAL + 12f,
                cursorY + 54f,
                bodyBoldPaint
            )
            canvas.drawText(
                "REPAIR PROGRESS: $completedCount / $totalSteps STEPS (${session.status})",
                MARGIN_HORIZONTAL + 290f,
                cursorY + 54f,
                resolvedBadgePaint
            )
            if (session.vinNumber.isNotBlank() || session.odometerMileage > 0L || session.attachedMediaSummary.isNotBlank()) {
                val extraMeta = buildList {
                    if (session.vinNumber.isNotBlank()) add("VIN: ${session.vinNumber}")
                    if (session.odometerMileage > 0L) add("ODOMETER: ${session.odometerMileage} MI")
                    if (session.attachedMediaSummary.isNotBlank()) add("EVIDENCE: ${session.attachedMediaSummary.take(45)}")
                }.joinToString("  |  ")
                canvas.drawText(
                    extraMeta.take(95),
                    MARGIN_HORIZONTAL + 12f,
                    cursorY + 69f,
                    monoSmallPaint
                )
            }
            cursorY += metaBoxHeight + 14f

            // Helper for wrapped text sections
            fun drawWrappedParagraph(
                text: String,
                paint: Paint,
                indentX: Float = 0f,
                lineSpacing: Float = 13.5f
            ) {
                val maxWidth = CONTENT_WIDTH - indentX
                val lines = wrapTextLines(text, paint, maxWidth)
                for (line in lines) {
                    ensureSpace(lineSpacing + 4f)
                    canvas.drawText(line, MARGIN_HORIZONTAL + indentX, cursorY + lineSpacing, paint)
                    cursorY += lineSpacing
                }
            }

            fun drawSectionHeader(title: String) {
                ensureSpace(26f)
                canvas.drawRect(
                    MARGIN_HORIZONTAL,
                    cursorY + 4f,
                    MARGIN_HORIZONTAL + 4f,
                    cursorY + 18f,
                    accentCyanPaint
                )
                canvas.drawText(title, MARGIN_HORIZONTAL + 10f, cursorY + 15f, sectionHeaderPaint)
                cursorY += 22f
            }

            // 3. Reported Symptom & Primary Root Cause
            drawSectionHeader("1. REPORTED SYMPTOM & PRIMARY ROOT CAUSE DIAGNOSIS")
            drawWrappedParagraph("Primary Root Cause: ${session.rootCauseTitle}", bodyBoldPaint)
            cursorY += 3f
            drawWrappedParagraph("Reported Symptoms / Fault Telemetry: ${session.symptomDescription}", bodyRegularPaint)
            cursorY += 6f
            drawWrappedParagraph("Engineering Failure Analysis:", bodyBoldPaint)
            drawWrappedParagraph(session.technicalAnalysis, bodyRegularPaint)
            cursorY += 10f

            // 4. Vehicle Lift, Battery & Shop Safety Warnings
            if (session.safetyWarnings.isNotEmpty()) {
                drawSectionHeader("2. VEHICLE LIFT, BATTERY & SHOP SAFETY PRECAUTIONS")
                for (warning in session.safetyWarnings) {
                    val lines = wrapTextLines("• $warning", bodyBoldPaint, CONTENT_WIDTH - 20f)
                    val boxH = (lines.size * 13.5f) + 12f
                    ensureSpace(boxH + 6f)
                    val rect = RectF(
                        MARGIN_HORIZONTAL,
                        cursorY,
                        PAGE_WIDTH - MARGIN_HORIZONTAL,
                        cursorY + boxH
                    )
                    canvas.drawRoundRect(rect, 5f, 5f, warningBgPaint)
                    canvas.drawRoundRect(rect, 5f, 5f, warningBorderPaint)
                    var innerY = cursorY + 14f
                    for (line in lines) {
                        canvas.drawText(line, MARGIN_HORIZONTAL + 10f, innerY, bodyBoldPaint)
                        innerY += 13.5f
                    }
                    cursorY += boxH + 6f
                }
                cursorY += 6f
            }

            // 5. Required Tools, Gauges & Replacement Parts
            if (session.requiredTools.isNotEmpty()) {
                drawSectionHeader("3. REQUIRED INSTRUMENTS, TOOLS & REPLACEMENT PARTS")
                val toolsLine = session.requiredTools.joinToString("   •   ")
                drawWrappedParagraph("• $toolsLine", bodyRegularPaint, indentX = 6f)
                cursorY += 10f
            }

            // 6. Step-by-Step Repair Guide & Checklist
            drawSectionHeader("4. STEP-BY-STEP REPAIR PROCEDURE & EXECUTION LOG")
            session.repairSteps.forEachIndexed { index, stepText ->
                val isCompleted = session.completedStepIndices.contains(index.toLong())
                val statusPrefix = if (isCompleted) "[✓ COMPLETED]" else "[  PENDING  ]"
                val stepHeader = "STEP ${index + 1} $statusPrefix"
                ensureSpace(28f)
                canvas.drawText(
                    stepHeader,
                    MARGIN_HORIZONTAL + 4f,
                    cursorY + 13f,
                    if (isCompleted) resolvedBadgePaint else bodyBoldPaint
                )
                cursorY += 15f
                drawWrappedParagraph(stepText, bodyRegularPaint, indentX = 12f)
                cursorY += 6f
            }
            cursorY += 6f

            // 7. Post-Repair Verification Test
            if (session.verificationTest.isNotBlank()) {
                drawSectionHeader("5. POST-REPAIR VERIFICATION & CALIBRATION TEST")
                drawWrappedParagraph(session.verificationTest, bodyRegularPaint, indentX = 4f)
                cursorY += 10f
            }

            // 8. Mechanic Voice-to-Text Observations & Bench Log
            val voiceObservations = session.parsedVoiceObservations()
            if (voiceObservations.isNotEmpty()) {
                drawSectionHeader("6. MECHANIC VOICE-TO-TEXT OBSERVATIONS & BENCH LOG")
                voiceObservations.forEach { entry ->
                    drawWrappedParagraph(
                        "[${entry.timestampLabel}] [${entry.stepTag} • ${entry.categoryTag}]",
                        bodyBoldPaint,
                        indentX = 4f
                    )
                    drawWrappedParagraph(
                        entry.transcript,
                        bodyRegularPaint,
                        indentX = 12f
                    )
                    cursorY += 4f
                }
                cursorY += 6f
            }

            // 9. Companion Flowchart Decision Trail (if available)
            if (companionFlowchart != null) {
                drawSectionHeader("7. COMPANION TROUBLESHOOTING DECISION TRAIL")
                drawWrappedParagraph(
                    "Flowchart: ${companionFlowchart.title} (Status: ${if (companionFlowchart.isResolved) "RESOLVED" else "IN PROGRESS"})",
                    bodyBoldPaint,
                    indentX = 4f
                )
                drawWrappedParagraph(
                    "Recorded Decision Path: ${companionFlowchart.visitedPathSummary}",
                    monoSmallPaint,
                    indentX = 4f
                )
                cursorY += 10f
            }

            // 9. Maintenance Log Sign-Off Block
            ensureSpace(74f)
            val signRect = RectF(
                MARGIN_HORIZONTAL,
                cursorY + 4f,
                PAGE_WIDTH - MARGIN_HORIZONTAL,
                cursorY + 64f
            )
            canvas.drawRoundRect(signRect, 6f, 6f, cardBgPaint)
            canvas.drawRoundRect(signRect, 6f, 6f, borderPaint)
            canvas.drawText(
                "MAINTENANCE LOG SIGN-OFF & COMPLIANCE VERIFICATION",
                MARGIN_HORIZONTAL + 12f,
                cursorY + 20f,
                bodyBoldPaint
            )
            canvas.drawText(
                "Technician Signature: ___________________________    Date: _______________",
                MARGIN_HORIZONTAL + 12f,
                cursorY + 42f,
                bodyRegularPaint
            )
            canvas.drawText(
                "Supervisor / QA Approval: _______________________    Work Order #: _________",
                MARGIN_HORIZONTAL + 12f,
                cursorY + 56f,
                bodyRegularPaint
            )
            cursorY += 70f

            drawPageFooter(canvas, pageNumber)
            pdfDocument.finishPage(currentPage)
            pdfDocument.writeTo(outputStream)
        } finally {
            pdfDocument.close()
        }
    }

    private fun wrapTextLines(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank()) return emptyList()
        val result = mutableListOf<String>()
        val paragraphs = text.split("\n")
        for (paragraph in paragraphs) {
            val words = paragraph.trim().split(Regex("\\s+"))
            if (words.isEmpty() || (words.size == 1 && words[0].isEmpty())) {
                continue
            }
            var currentLine = StringBuilder()
            for (word in words) {
                val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
                if (paint.measureText(candidate) <= maxWidth) {
                    currentLine = StringBuilder(candidate)
                } else {
                    if (currentLine.isNotEmpty()) {
                        result.add(currentLine.toString())
                    }
                    currentLine = StringBuilder(word)
                }
            }
            if (currentLine.isNotEmpty()) {
                result.add(currentLine.toString())
            }
        }
        return result
    }
}
