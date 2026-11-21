package com.example.export

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.local.TransactionEntity
import com.example.domain.FinancialSummary
import com.example.domain.IndianCurrencyFormatter
import com.example.domain.TransactionType
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ExportFormat(val extension: String, val mimeType: String, val label: String) {
    PDF("pdf", "application/pdf", "PDF Report"),
    CSV("csv", "text/csv", "CSV Spreadsheet"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Excel (XLSX)")
}

object ReportExporter {

    private val dateFormatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
    private val shortDateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)

    fun exportToCacheUri(
        context: Context,
        format: ExportFormat,
        userName: String,
        periodLabel: String,
        summary: FinancialSummary,
        transactions: List<TransactionEntity>
    ): Uri {
        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH).format(Date())
        val outFile = File(reportsDir, "Finora_Report_$stamp.${format.extension}")
        FileOutputStream(outFile).use { stream ->
            writeReport(stream, format, userName, periodLabel, summary, transactions)
        }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outFile
        )
    }

    fun writeReport(
        outputStream: OutputStream,
        format: ExportFormat,
        userName: String,
        periodLabel: String,
        summary: FinancialSummary,
        transactions: List<TransactionEntity>
    ) {
        when (format) {
            ExportFormat.PDF -> writePdf(outputStream, userName, periodLabel, summary, transactions)
            ExportFormat.CSV -> writeCsv(outputStream, userName, periodLabel, summary, transactions)
            ExportFormat.XLSX -> writeXlsx(outputStream, userName, periodLabel, summary, transactions)
        }
    }

    private fun writeCsv(
        outputStream: OutputStream,
        userName: String,
        periodLabel: String,
        summary: FinancialSummary,
        transactions: List<TransactionEntity>
    ) {
        val sb = StringBuilder()
        // UTF-8 BOM for clean INR symbol rendering in Excel
        sb.append('\uFEFF')
        sb.appendLine("Finora Personal Finance Report")
        sb.appendLine("Account,${escapeCsv(userName)}")
        sb.appendLine("Period,${escapeCsv(periodLabel)}")
        sb.appendLine("Available Balance,${escapeCsv(IndianCurrencyFormatter.formatPaise(summary.availableBalancePaise))}")
        sb.appendLine("Period Income,${escapeCsv(IndianCurrencyFormatter.formatPaise(summary.periodIncomePaise))}")
        sb.appendLine("Period Expenses,${escapeCsv(IndianCurrencyFormatter.formatPaise(summary.periodExpensePaise))}")
        sb.appendLine("Period Credits,${escapeCsv(IndianCurrencyFormatter.formatPaise(summary.periodCreditPaise))}")
        sb.appendLine()
        sb.appendLine("Date,Type,Title,Category,Source/Sender,Payment Method,Amount (INR),Reference,Notes")

        for (tx in transactions.sortedByDescending { it.transactionDateMillis }) {
            val amountStr = String.format(Locale.US, "%.2f", tx.amountPaise / 100.0)
            sb.append(escapeCsv(dateFormatter.format(Date(tx.transactionDateMillis)))).append(',')
            sb.append(escapeCsv(tx.type.label)).append(',')
            sb.append(escapeCsv(tx.title)).append(',')
            sb.append(escapeCsv(tx.category)).append(',')
            sb.append(escapeCsv(tx.sourceOrSender)).append(',')
            sb.append(escapeCsv(tx.paymentMethod)).append(',')
            sb.append(escapeCsv(amountStr)).append(',')
            sb.append(escapeCsv(tx.referenceNumber)).append(',')
            sb.append(escapeCsv(tx.notes))
            sb.appendLine()
        }
        outputStream.write(sb.toString().toByteArray(StandardCharsets.UTF_8))
    }

    private fun escapeCsv(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }

    private fun writePdf(
        outputStream: OutputStream,
        userName: String,
        periodLabel: String,
        summary: FinancialSummary,
        transactions: List<TransactionEntity>
    ) {
        val doc = try {
            PdfDocument()
        } catch (_: Throwable) {
            writeFallbackPdf(outputStream, userName, periodLabel, summary, transactions)
            return
        }
        val pageWidth = 595
        val pageHeight = 842
        val margin = 36f

        val headerPaint = Paint().apply {
            color = Color.rgb(20, 33, 61)
            style = Paint.Style.FILL
        }
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val subTitlePaint = Paint().apply {
            color = Color.rgb(219, 234, 254)
            textSize = 11f
            isAntiAlias = true
        }
        val sectionPaint = Paint().apply {
            color = Color.rgb(20, 33, 61)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 10f
            isAntiAlias = true
        }
        val mutedPaint = Paint().apply {
            color = Color.rgb(100, 116, 139)
            textSize = 9.5f
            isAntiAlias = true
        }
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        try {
            var pageNumber = 1
            var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            var canvas = page.canvas

        // Banner
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 86f, headerPaint)
        canvas.drawText("FINORA FINANCIAL STATEMENT", margin, 38f, titlePaint)
        canvas.drawText("Account: $userName  •  Period: $periodLabel", margin, 60f, subTitlePaint)

        var y = 112f
        canvas.drawText("Summary", margin, y, sectionPaint)
        y += 18f
        canvas.drawText("Available Balance: ${IndianCurrencyFormatter.formatPaise(summary.availableBalancePaise)}", margin, y, bodyPaint)
        canvas.drawText("Opening Balance: ${IndianCurrencyFormatter.formatPaise(summary.openingBalancePaise)}", 310f, y, bodyPaint)
        y += 16f
        canvas.drawText("Total Income: ${IndianCurrencyFormatter.formatPaise(summary.periodIncomePaise)}", margin, y, bodyPaint)
        canvas.drawText("Total Expenses: ${IndianCurrencyFormatter.formatPaise(summary.periodExpensePaise)}", 310f, y, bodyPaint)
        y += 16f
        canvas.drawText("Total Credits: ${IndianCurrencyFormatter.formatPaise(summary.periodCreditPaise)}", margin, y, bodyPaint)
        canvas.drawText("Net Savings: ${IndianCurrencyFormatter.formatPaise(summary.periodNetSavingsPaise)}", 310f, y, bodyPaint)
        y += 14f
        canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
        y += 22f

        canvas.drawText("Transactions (${transactions.size})", margin, y, sectionPaint)
        y += 18f

        fun drawTableHeader() {
            canvas.drawText("Date", margin, y, mutedPaint)
            canvas.drawText("Type", 118f, y, mutedPaint)
            canvas.drawText("Title / Category", 185f, y, mutedPaint)
            canvas.drawText("Method", 390f, y, mutedPaint)
            canvas.drawText("Amount", 490f, y, mutedPaint)
            y += 8f
            canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
            y += 14f
        }

        drawTableHeader()

        for (tx in transactions.sortedByDescending { it.transactionDateMillis }) {
            if (y > pageHeight - 48f) {
                doc.finishPage(page)
                pageNumber++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                y = 48f
                drawTableHeader()
            }
            val dateStr = shortDateFormatter.format(Date(tx.transactionDateMillis))
            val titleCat = "${tx.title.take(22)} (${tx.category.take(15)})"
            val signPrefix = when (tx.type) {
                TransactionType.INCOME, TransactionType.CREDIT -> "+"
                TransactionType.EXPENSE, TransactionType.OUTGOING_TRANSFER -> "-"
            }
            val amountColor = when (tx.type) {
                TransactionType.INCOME -> Color.rgb(22, 163, 74)
                TransactionType.EXPENSE -> Color.rgb(239, 68, 68)
                TransactionType.CREDIT -> Color.rgb(59, 130, 246)
                TransactionType.OUTGOING_TRANSFER -> Color.rgb(245, 158, 11)
            }
            val amountPaint = Paint(bodyPaint).apply {
                color = amountColor
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            canvas.drawText(dateStr, margin, y, bodyPaint)
            canvas.drawText(tx.type.label, 118f, y, bodyPaint)
            canvas.drawText(titleCat, 185f, y, bodyPaint)
            canvas.drawText(tx.paymentMethod.take(14), 390f, y, bodyPaint)
            canvas.drawText("$signPrefix${IndianCurrencyFormatter.formatPaise(tx.amountPaise)}", 485f, y, amountPaint)
            y += 16f
        }

        doc.finishPage(page)
        doc.writeTo(outputStream)
        } catch (_: Throwable) {
            writeFallbackPdf(outputStream, userName, periodLabel, summary, transactions)
        } finally {
            runCatching { doc.close() }
        }
    }

    private fun writeFallbackPdf(
        outputStream: OutputStream,
        userName: String,
        periodLabel: String,
        summary: FinancialSummary,
        transactions: List<TransactionEntity>
    ) {
        val lines = mutableListOf(
            "FINORA FINANCIAL STATEMENT",
            "Account: $userName | Period: $periodLabel",
            "Available Balance: ${IndianCurrencyFormatter.formatPaise(summary.availableBalancePaise).replace("₹", "INR ")}",
            "Income: ${IndianCurrencyFormatter.formatPaise(summary.periodIncomePaise).replace("₹", "INR ")} | Expenses: ${IndianCurrencyFormatter.formatPaise(summary.periodExpensePaise).replace("₹", "INR ")}",
            "Credits: ${IndianCurrencyFormatter.formatPaise(summary.periodCreditPaise).replace("₹", "INR ")} | Net Savings: ${IndianCurrencyFormatter.formatPaise(summary.periodNetSavingsPaise).replace("₹", "INR ")}",
            "Transactions: ${transactions.size}"
        )
        transactions.sortedByDescending { it.transactionDateMillis }.take(30).forEach { tx ->
            lines.add("${shortDateFormatter.format(Date(tx.transactionDateMillis))} | ${tx.type.label} | ${tx.title} | INR ${tx.amountPaise / 100}")
        }
        val streamBody = buildString {
            append("BT\n/F1 11 Tf\n40 790 Td\n16 TL\n")
            lines.forEach { line ->
                val esc = line.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
                append("($esc) Tj T*\n")
            }
            append("ET")
        }
        val objs = listOf(
            "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n",
            "2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n",
            "3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >> endobj\n",
            "4 0 obj << /Length ${streamBody.toByteArray(StandardCharsets.US_ASCII).size} >> stream\n$streamBody\nendstream endobj\n",
            "5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n"
        )
        val out = StringBuilder("%PDF-1.4\n")
        val offsets = mutableListOf(0)
        objs.forEach { obj ->
            offsets.add(out.length)
            out.append(obj)
        }
        val xrefPos = out.length
        out.append("xref\n0 ${objs.size + 1}\n0000000000 65535 f \n")
        for (i in 1..objs.size) {
            out.append(String.format(Locale.US, "%010d 00000 n \n", offsets[i]))
        }
        out.append("trailer << /Size ${objs.size + 1} /Root 1 0 R >>\nstartxref\n$xrefPos\n%%EOF")
        outputStream.write(out.toString().toByteArray(StandardCharsets.US_ASCII))
    }

    /**
     * Generates a standards-compliant OpenXML .xlsx workbook using ZipOutputStream (no external POI dependency).
     */
    private fun writeXlsx(
        outputStream: OutputStream,
        userName: String,
        periodLabel: String,
        summary: FinancialSummary,
        transactions: List<TransactionEntity>
    ) {
        val sorted = transactions.sortedByDescending { it.transactionDateMillis }

        val summaryRows = listOf(
            listOf("Finora Personal Finance Report", ""),
            listOf("Account Holder", userName),
            listOf("Report Period", periodLabel),
            listOf("Available Balance", IndianCurrencyFormatter.formatPaise(summary.availableBalancePaise)),
            listOf("Opening Balance", IndianCurrencyFormatter.formatPaise(summary.openingBalancePaise)),
            listOf("Period Income", IndianCurrencyFormatter.formatPaise(summary.periodIncomePaise)),
            listOf("Period Expenses", IndianCurrencyFormatter.formatPaise(summary.periodExpensePaise)),
            listOf("Period Credits", IndianCurrencyFormatter.formatPaise(summary.periodCreditPaise)),
            listOf("Net Savings", IndianCurrencyFormatter.formatPaise(summary.periodNetSavingsPaise))
        )

        val txRows = mutableListOf<List<Any>>(
            listOf("Date", "Type", "Title", "Category", "Source/Sender", "Payment Method", "Amount (INR)", "Reference", "Notes")
        )
        for (tx in sorted) {
            txRows.add(
                listOf(
                    dateFormatter.format(Date(tx.transactionDateMillis)),
                    tx.type.label,
                    tx.title,
                    tx.category,
                    tx.sourceOrSender,
                    tx.paymentMethod,
                    tx.amountPaise / 100.0,
                    tx.referenceNumber,
                    tx.notes
                )
            )
        }

        ZipOutputStream(outputStream).use { zip ->
            fun addEntry(path: String, xml: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(xml.toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
            }

            addEntry(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""
            )

            addEntry(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
            )

            addEntry(
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Summary" sheetId="1" r:id="rId1"/>
    <sheet name="Transactions" sheetId="2" r:id="rId2"/>
  </sheets>
</workbook>"""
            )

            addEntry(
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""
            )

            addEntry(
                "xl/styles.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2">
    <font><sz val="11"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><name val="Calibri"/></font>
  </fonts>
  <fills count="2">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
  </fills>
  <borders count="1"><border/></borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="2">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
  </cellXfs>
</styleSheet>"""
            )

            addEntry("xl/worksheets/sheet1.xml", buildWorksheetXml(summaryRows))
            addEntry("xl/worksheets/sheet2.xml", buildWorksheetXml(txRows))
        }
    }

    private fun buildWorksheetXml(rows: List<List<Any>>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        rows.forEachIndexed { rIdx, row ->
            val rowNum = rIdx + 1
            sb.append("""<row r="$rowNum">""")
            row.forEachIndexed { cIdx, cellVal ->
                val ref = "${columnLetters(cIdx)}$rowNum"
                val styleAttr = if (rIdx == 0) """ s="1"""" else ""
                when (cellVal) {
                    is Number -> sb.append("""<c r="$ref"$styleAttr><v>${cellVal}</v></c>""")
                    else -> {
                        val safe = escapeXml(cellVal.toString())
                        sb.append("""<c r="$ref" t="inlineStr"$styleAttr><is><t>$safe</t></is></c>""")
                    }
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun columnLetters(index: Int): String {
        var n = index
        val sb = StringBuilder()
        while (n >= 0) {
            sb.append(('A' + (n % 26)))
            n = (n / 26) - 1
        }
        return sb.reverse().toString()
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
