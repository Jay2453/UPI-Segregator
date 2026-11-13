package com.upisegregator.app.data.importer.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.upisegregator.app.data.importer.model.RowValidationStatus
import java.io.ByteArrayInputStream

/**
 * High-level extractor for bank statement PDFs.
 *
 * Implements:
 * - Local selectable text extraction via PDFBox Android.
 * - Password detection and handling (passwords are never logged or stored).
 * - Distinguishing document types:
 *     1. [PdfDocumentType.ImageOnly]: Character count <= NEAR_ZERO_CHARS_THRESHOLD
 *     2. [PdfDocumentType.InsufficientText]: Chars/page < MIN_TABLE_CHARS_PER_PAGE
 *     3. [PdfDocumentType.UnsupportedLayout]: Text exists but table headers not recognized
 *     4. [PdfDocumentType.PasswordProtected]: File is encrypted and requires a password
 *     5. [PdfDocumentType.Digital]: Successfully parsed table
 */
object PdfStatementExtractor {

    /**
     * Extracts transactions from raw PDF bytes.
     * Accepting [pdfBytes] allows safe reopening without stream reuse issues when a password retry occurs.
     *
     * @param pdfBytes Raw bytes of the PDF file.
     * @param userId Current user ID (for deterministic fingerprinting and data isolation).
     * @param password Optional password if the PDF is password-protected. Never logged.
     */
    fun extract(
        pdfBytes: ByteArray,
        userId: String,
        password: String? = null
    ): PdfParseResult {
        var document: PDDocument? = null
        try {
            document = if (password.isNullOrEmpty()) {
                PDDocument.load(ByteArrayInputStream(pdfBytes))
            } else {
                PDDocument.load(ByteArrayInputStream(pdfBytes), password)
            }

            val pageCount = document.numberOfPages
            if (pageCount == 0) {
                return PdfParseResult(
                    isSuccess = false,
                    errorMessage = "The PDF contains no pages.",
                    documentType = PdfDocumentType.ImageOnly
                )
            }

            // Extract positional text from all pages
            val pages = PdfPageTextExtractor.extractAllPages(document)
            val totalChars = pages.sumOf { it.rawCharCount }

            // 1. Scanned / Image-only PDF detection
            if (totalChars <= PdfDocumentType.NEAR_ZERO_CHARS_THRESHOLD) {
                return PdfParseResult(
                    isSuccess = false,
                    errorMessage = "This PDF appears to be a scanned image with no selectable text layer.",
                    documentType = PdfDocumentType.ImageOnly,
                    pagesProcessed = pageCount
                )
            }

            // 2. Insufficient text detection
            val avgCharsPerPage = totalChars / pageCount
            if (avgCharsPerPage < PdfDocumentType.MIN_TABLE_CHARS_PER_PAGE) {
                return PdfParseResult(
                    isSuccess = false,
                    errorMessage = "Found only $totalChars characters across $pageCount page(s). Insufficient text to extract bank transactions.",
                    documentType = PdfDocumentType.InsufficientText(totalChars, pageCount),
                    pagesProcessed = pageCount
                )
            }

            // 3. Layout and bank detection
            val detectedBank = PdfBankLayoutDetector.detectBank(pages)
            val mapping = PdfBankLayoutDetector.detectLayout(pages)

            if (mapping == null || !mapping.isValid) {
                return PdfParseResult(
                    isSuccess = false,
                    errorMessage = "Could not identify standard bank statement table columns (Date, Description, Debit/Credit/Amount).",
                    detectedBank = detectedBank,
                    documentType = PdfDocumentType.UnsupportedLayout,
                    pagesProcessed = pageCount
                )
            }

            // 4. Parse transactions
            val transactions = PdfTransactionParser.parseTransactions(pages, mapping, userId)
            val warnings = mutableListOf<String>()

            val reviewCount = transactions.count { it.validationStatus == RowValidationStatus.NEEDS_REVIEW }
            if (reviewCount > 0) {
                warnings.add("$reviewCount transaction(s) require manual review due to ambiguous direction or date.")
            }
            val invalidCount = transactions.count { it.validationStatus == RowValidationStatus.INVALID }
            if (invalidCount > 0) {
                warnings.add("$invalidCount row(s) had invalid or zero amounts.")
            }

            return PdfParseResult(
                isSuccess = true,
                transactions = transactions,
                totalRowsInFile = transactions.size,
                pagesProcessed = pageCount,
                detectedBank = detectedBank ?: mapping.detectedBank,
                documentType = PdfDocumentType.Digital,
                extractionWarnings = warnings,
                requiresPassword = false
            )

        } catch (e: InvalidPasswordException) {
            return PdfParseResult(
                isSuccess = false,
                errorMessage = "This PDF is password-protected. Please enter the password to unlock.",
                documentType = PdfDocumentType.PasswordProtected(),
                requiresPassword = true
            )
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            if (msg.contains("password", ignoreCase = true) || msg.contains("encrypted", ignoreCase = true)) {
                return PdfParseResult(
                    isSuccess = false,
                    errorMessage = "This PDF is password-protected. Please enter the password to unlock.",
                    documentType = PdfDocumentType.PasswordProtected(),
                    requiresPassword = true
                )
            }
            return PdfParseResult(
                isSuccess = false,
                errorMessage = "Failed to parse PDF: ${e.localizedMessage ?: "Unknown error"}",
                extractionWarnings = listOf(e.localizedMessage.orEmpty())
            )
        } finally {
            try {
                document?.close()
            } catch (_: Exception) {}
        }
    }
}
