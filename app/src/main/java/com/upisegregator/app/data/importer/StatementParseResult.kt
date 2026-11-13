package com.upisegregator.app.data.importer

import com.upisegregator.app.data.importer.pdf.PdfDocumentType
import com.upisegregator.app.data.importer.model.ImportableTransaction

/** Identifies which file format was successfully detected and processed. */
enum class DetectedFileFormat { CSV, PDF, UNKNOWN }

/**
 * Unified parse result produced by [StatementFileParser] for any supported file format.
 *
 * Both the CSV and PDF paths produce a [StatementParseResult] before handing off to the
 * shared duplicate-detection and save pipeline.
 */
data class StatementParseResult(
    val isSuccess: Boolean,
    val errorMessage: String? = null,
    val transactions: List<ImportableTransaction> = emptyList(),
    val totalRowsInFile: Int = 0,
    val detectedFormat: DetectedFileFormat = DetectedFileFormat.UNKNOWN,
    val detectedBank: String? = null,
    val extractionWarnings: List<String> = emptyList(),
    /** True when a PDF requires a password before extraction can proceed. */
    val requiresPassword: Boolean = false,
    /** Non-null for PDF files; null for CSV files. */
    val pdfDocumentType: PdfDocumentType? = null
)
