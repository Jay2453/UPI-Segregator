package com.upisegregator.app.data.importer

import java.util.Locale

/**
 * Detects whether a file is a CSV or PDF by inspecting the first 1 KB of its content.
 *
 * Detection priority:
 * 1. Magic-byte scan: first 1 024 bytes searched for the `%PDF` signature (may appear after
 *    a BOM or leading whitespace, so the scan is not restricted to byte zero).
 * 2. MIME type: "pdf" substring → PDF; "csv"/"text" substring → CSV.
 * 3. File extension: `.pdf` → PDF; `.csv` / `.txt` → CSV.
 */
object FileTypeDetector {

    enum class FileType { CSV, PDF, UNKNOWN }

    private const val INSPECT_BYTES = 1024
    private const val PDF_SIGNATURE = "%PDF"

    /**
     * Detects the file type from the leading bytes of the file, with MIME type and
     * file extension as ordered fallbacks.
     *
     * @param headerBytes  First bytes of the file (up to [INSPECT_BYTES] bytes inspected).
     * @param fileName     Original file name, used for extension fallback.
     * @param mimeType     MIME type reported by the system, used as secondary indicator.
     */
    fun detect(
        headerBytes: ByteArray,
        fileName: String = "",
        mimeType: String? = null
    ): FileType {
        // 1. Primary: scan first 1 KB for the %PDF signature
        val inspectLen = headerBytes.size.coerceAtMost(INSPECT_BYTES)
        val headerText = headerBytes.copyOfRange(0, inspectLen).toString(Charsets.ISO_8859_1)
        if (PDF_SIGNATURE in headerText) return FileType.PDF

        // 2. Secondary: MIME type
        if (mimeType != null) {
            val lowerMime = mimeType.lowercase(Locale.US)
            if ("pdf" in lowerMime) return FileType.PDF
            if ("csv" in lowerMime || "text" in lowerMime) return FileType.CSV
        }

        // 3. Tertiary: file extension
        val lowerName = fileName.lowercase(Locale.US)
        if (lowerName.endsWith(".pdf")) return FileType.PDF
        if (lowerName.endsWith(".csv") || lowerName.endsWith(".txt")) return FileType.CSV

        return FileType.UNKNOWN
    }
}
