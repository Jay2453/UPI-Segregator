package com.upisegregator.app.data.importer.pdf

import com.upisegregator.app.data.importer.model.ImportableTransaction

/**
 * Parsing result returned by [PdfStatementExtractor].
 *
 * Mirrors [com.upisegregator.app.data.importer.csv.CsvParseResult] in structure so that
 * the shared import pipeline can consume either result through [StatementParseResult].
 */
data class PdfParseResult(
    val isSuccess: Boolean,
    val errorMessage: String? = null,
    val transactions: List<ImportableTransaction> = emptyList(),
    val totalRowsInFile: Int = 0,
    val pagesProcessed: Int = 0,
    val detectedBank: String? = null,
    val documentType: PdfDocumentType? = null,
    val extractionWarnings: List<String> = emptyList(),
    /** True when [documentType] is [PdfDocumentType.PasswordProtected]. */
    val requiresPassword: Boolean = false
)
