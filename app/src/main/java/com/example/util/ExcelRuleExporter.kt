package com.example.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.model.AutoReplyRule
import com.example.model.MatchType
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStream

object ExcelRuleExporter {

    private const val TAG = "ExcelRuleExporter"
    const val DEFAULT_FILE_NAME = "custom_reply_rules.xls"

    /**
     * Automatically syncs the list of custom reply rules to an XLS file in app storage.
     */
    fun autoSyncXls(context: Context, rules: List<AutoReplyRule>): File? {
        return try {
            val file = File(context.filesDir, DEFAULT_FILE_NAME)
            val content = generateXlsXmlContent(rules)
            FileOutputStream(file).use { os ->
                os.write(content.toByteArray(Charsets.UTF_8))
            }
            Log.d(TAG, "Successfully auto-synced rules to ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Error during autoSyncXls: ${e.message}", e)
            null
        }
    }

    /**
     * Exports/saves a copy of the rules XLS file to the device Downloads folder or OutputStream.
     */
    fun exportToDownloads(context: Context, rules: List<AutoReplyRule>): String? {
        val content = generateXlsXmlContent(rules)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, DEFAULT_FILE_NAME)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.ms-excel")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { os ->
                        os.write(content.toByteArray(Charsets.UTF_8))
                    }
                    "Saved to Downloads/$DEFAULT_FILE_NAME"
                } else {
                    null
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val targetFile = File(downloadsDir, DEFAULT_FILE_NAME)
                FileOutputStream(targetFile).use { os ->
                    os.write(content.toByteArray(Charsets.UTF_8))
                }
                targetFile.absolutePath
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting XLS to Downloads: ${e.message}", e)
            // Fallback: save to internal files directory
            val file = autoSyncXls(context, rules)
            file?.absolutePath
        }
    }

    /**
     * Writes XLS content to a custom destination Uri (SAF).
     */
    fun exportToUri(context: Context, uri: Uri, rules: List<AutoReplyRule>): Boolean {
        return try {
            val content = generateXlsXmlContent(rules)
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(content.toByteArray(Charsets.UTF_8))
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting to Uri: ${e.message}", e)
            false
        }
    }

    /**
     * Reads and parses an XLS/CSV file from Uri into a list of AutoReplyRules.
     */
    fun importRulesFromUri(context: Context, uri: Uri): List<AutoReplyRule> {
        val rules = mutableListOf<AutoReplyRule>()
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return emptyList()
            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            val fullText = reader.readText()
            inputStream.close()

            if (fullText.contains("<Workbook") || fullText.contains("<Table")) {
                // Parse XML Spreadsheet format
                parseXmlSpreadsheet(fullText, rules)
            } else {
                // Parse CSV / TSV format
                parseCsvFormat(fullText, rules)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error importing rules from Uri: ${e.message}", e)
        }
        return rules
    }

    /**
     * Deletes all local XLS backup files.
     */
    fun clearAllXlsFiles(context: Context) {
        try {
            val internalFile = File(context.filesDir, DEFAULT_FILE_NAME)
            if (internalFile.exists()) internalFile.delete()

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val downloadFile = File(downloadsDir, DEFAULT_FILE_NAME)
            if (downloadFile.exists()) downloadFile.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing XLS files: ${e.message}", e)
        }
    }

    private fun generateXlsXmlContent(rules: List<AutoReplyRule>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<?mso-application progid=\"Excel.Sheet\"?>\n")
        sb.append("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\"\n")
        sb.append(" xmlns:o=\"urn:schemas-microsoft-com:office:office\"\n")
        sb.append(" xmlns:x=\"urn:schemas-microsoft-com:office:excel\"\n")
        sb.append(" xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\">\n")
        sb.append(" <Worksheet ss:Name=\"CustomReplies\">\n")
        sb.append("  <Table>\n")
        
        // Header Row
        sb.append("   <Row>\n")
        sb.append("    <Cell><Data ss:Type=\"String\">Keyword</Data></Cell>\n")
        sb.append("    <Cell><Data ss:Type=\"String\">Reply message</Data></Cell>\n")
        sb.append("    <Cell><Data ss:Type=\"String\">Match type</Data></Cell>\n")
        sb.append("    <Cell><Data ss:Type=\"String\">Is enabled</Data></Cell>\n")
        sb.append("   </Row>\n")

        // Data Rows
        for (rule in rules) {
            sb.append("   <Row>\n")
            sb.append("    <Cell><Data ss:Type=\"String\">${escapeXml(rule.keyword)}</Data></Cell>\n")
            sb.append("    <Cell><Data ss:Type=\"String\">${escapeXml(rule.replyText)}</Data></Cell>\n")
            sb.append("    <Cell><Data ss:Type=\"String\">${rule.matchType.name}</Data></Cell>\n")
            sb.append("    <Cell><Data ss:Type=\"String\">${rule.isEnabled}</Data></Cell>\n")
            sb.append("   </Row>\n")
        }

        sb.append("  </Table>\n")
        sb.append(" </Worksheet>\n")
        sb.append("</Workbook>")
        return sb.toString()
    }

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun parseXmlSpreadsheet(fullText: String, rules: MutableList<AutoReplyRule>) {
        val rowRegex = Regex("<Row>(.*?)</Row>", RegexOption.DOT_MATCHES_ALL)
        val cellDataRegex = Regex("<Data[^>]*>(.*?)</Data>", RegexOption.DOT_MATCHES_ALL)

        val rows = rowRegex.findAll(fullText).map { it.groupValues[1] }.toList()
        var isFirstRow = true

        for (rowXml in rows) {
            val cellValues = cellDataRegex.findAll(rowXml).map {
                it.groupValues[1]
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .replace("&apos;", "'")
                    .trim()
            }.toList()

            if (cellValues.isEmpty()) continue

            // Skip header if first row has "Keyword"
            if (isFirstRow && cellValues.firstOrNull()?.equals("Keyword", ignoreCase = true) == true) {
                isFirstRow = false
                continue
            }
            isFirstRow = false

            if (cellValues.size >= 2) {
                val keyword = cellValues[0]
                val reply = cellValues[1]
                val isEnabled = if (cellValues.size >= 4) cellValues[3].lowercase() == "true" else true

                if (keyword.isNotBlank() && reply.isNotBlank()) {
                    rules.add(
                        AutoReplyRule(
                            name = "Custom Reply: $keyword",
                            keyword = keyword,
                            replyText = reply,
                            matchType = MatchType.CONTAINS,
                            isEnabled = isEnabled
                        )
                    )
                }
            }
        }
    }

    private fun parseCsvFormat(fullText: String, rules: MutableList<AutoReplyRule>) {
        val lines = fullText.lines()
        var isFirstRow = true

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val tokens = if (trimmed.contains("\t")) {
                trimmed.split("\t")
            } else {
                trimmed.split(",")
            }.map { it.trim().trim('"', '\'') }

            if (tokens.isEmpty()) continue

            if (isFirstRow && tokens.firstOrNull()?.equals("Keyword", ignoreCase = true) == true) {
                isFirstRow = false
                continue
            }
            isFirstRow = false

            if (tokens.size >= 2) {
                val keyword = tokens[0]
                val reply = tokens[1]
                val isEnabled = if (tokens.size >= 4) tokens[3].lowercase() == "true" else true

                if (keyword.isNotBlank() && reply.isNotBlank()) {
                    rules.add(
                        AutoReplyRule(
                            name = "Custom Reply: $keyword",
                            keyword = keyword,
                            replyText = reply,
                            matchType = MatchType.CONTAINS,
                            isEnabled = isEnabled
                        )
                    )
                }
            }
        }
    }
}
