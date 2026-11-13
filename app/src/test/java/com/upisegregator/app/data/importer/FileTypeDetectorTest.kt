package com.upisegregator.app.data.importer

import org.junit.Assert.assertEquals
import org.junit.Test

class FileTypeDetectorTest {

    @Test
    fun detect_pdfMagicBytesAtZero_returnsPdf() {
        val bytes = "%PDF-1.5\n%Header".toByteArray(Charsets.ISO_8859_1)
        val result = FileTypeDetector.detect(bytes, fileName = "unknown_file", mimeType = null)
        assertEquals(FileTypeDetector.FileType.PDF, result)
    }

    @Test
    fun detect_pdfMagicBytesWithLeadingWhitespaceOrBom_returnsPdf() {
        // Construct bytes with 200 bytes of padding before %PDF
        val padding = " ".repeat(200)
        val bytes = "$padding%PDF-1.4\n1 0 obj".toByteArray(Charsets.ISO_8859_1)
        val result = FileTypeDetector.detect(bytes, fileName = "statement", mimeType = null)
        assertEquals(FileTypeDetector.FileType.PDF, result)
    }

    @Test
    fun detect_pdfMagicBytesWithinFirst1Kb_returnsPdf() {
        // Construct bytes with 800 bytes of zeros/spaces, then %PDF
        val prefix = ByteArray(800) { 0x20 }
        val pdfHeader = "%PDF-1.7".toByteArray(Charsets.ISO_8859_1)
        val bytes = prefix + pdfHeader
        val result = FileTypeDetector.detect(bytes, fileName = "file.bin", mimeType = "application/octet-stream")
        assertEquals(FileTypeDetector.FileType.PDF, result)
    }

    @Test
    fun detect_pdfMagicBytesBeyond1Kb_fallbackToExtension() {
        // When %PDF appears after 1024 bytes, it should fall back to extension
        val prefix = ByteArray(1200) { 0x20 }
        val pdfHeader = "%PDF-1.7".toByteArray(Charsets.ISO_8859_1)
        val bytes = prefix + pdfHeader
        val result = FileTypeDetector.detect(bytes, fileName = "statement.pdf", mimeType = null)
        assertEquals(FileTypeDetector.FileType.PDF, result)
    }

    @Test
    fun detect_mimeTypePdf_returnsPdf() {
        val bytes = "No signature here".toByteArray(Charsets.ISO_8859_1)
        val result = FileTypeDetector.detect(bytes, fileName = "doc", mimeType = "application/pdf")
        assertEquals(FileTypeDetector.FileType.PDF, result)
    }

    @Test
    fun detect_extensionPdf_returnsPdf() {
        val bytes = "No signature here".toByteArray(Charsets.ISO_8859_1)
        val result = FileTypeDetector.detect(bytes, fileName = "my_statement.PDF", mimeType = null)
        assertEquals(FileTypeDetector.FileType.PDF, result)
    }

    @Test
    fun detect_csvMimeType_returnsCsv() {
        val bytes = "Date,Narration,Amount\n01/01/2026,Swiggy,200.00".toByteArray(Charsets.ISO_8859_1)
        val result = FileTypeDetector.detect(bytes, fileName = "data", mimeType = "text/csv")
        assertEquals(FileTypeDetector.FileType.CSV, result)
    }

    @Test
    fun detect_csvExtension_returnsCsv() {
        val bytes = "Date,Narration,Amount\n01/01/2026,Swiggy,200.00".toByteArray(Charsets.ISO_8859_1)
        val result = FileTypeDetector.detect(bytes, fileName = "hdfc_statement.csv", mimeType = null)
        assertEquals(FileTypeDetector.FileType.CSV, result)
    }

    @Test
    fun detect_txtExtension_returnsCsv() {
        val bytes = "Date,Narration,Amount\n01/01/2026,Swiggy,200.00".toByteArray(Charsets.ISO_8859_1)
        val result = FileTypeDetector.detect(bytes, fileName = "statement.txt", mimeType = null)
        assertEquals(FileTypeDetector.FileType.CSV, result)
    }

    @Test
    fun detect_unknownBinary_returnsUnknown() {
        val bytes = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04)
        val result = FileTypeDetector.detect(bytes, fileName = "archive.zip", mimeType = "application/zip")
        assertEquals(FileTypeDetector.FileType.UNKNOWN, result)
    }
}
