package com.upisegregator.app.data.importer.pdf

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.upisegregator.app.data.importer.model.RowValidationStatus
import com.upisegregator.app.data.local.entity.TransactionDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PdfExtractionEngineTest {

    private val testUserId = "user_test_123"

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        PDFBoxResourceLoader.init(context)
    }

    @Test
    fun testPageTextExtractor_extractsPositionalLinesCorrectly() {
        val pdfBytes = SyntheticPdfFactory.createMultiPageStatementPdf()
        val document = PDDocument.load(ByteArrayInputStream(pdfBytes))

        val pages = PdfPageTextExtractor.extractAllPages(document)
        document.close()

        assertEquals(2, pages.size)
        assertEquals(1, pages[0].pageNum)
        assertEquals(2, pages[1].pageNum)
        assertTrue(pages[0].rawCharCount > 100)
        assertTrue(pages[1].rawCharCount > 100)

        // Verify tokens are ordered left-to-right by X
        for (page in pages) {
            for (line in page.lines) {
                var lastX = -1f
                for (token in line.tokens) {
                    assertTrue("Tokens should be sorted left-to-right", token.x >= lastX - 0.1f)
                    lastX = token.x
                }
            }
        }
    }

    @Test
    fun testBankLayoutDetector_identifiesHdfcBankAndLayout() {
        val pdfBytes = SyntheticPdfFactory.createMultiPageStatementPdf()
        val document = PDDocument.load(ByteArrayInputStream(pdfBytes))
        val pages = PdfPageTextExtractor.extractAllPages(document)
        document.close()

        val detectedBank = PdfBankLayoutDetector.detectBank(pages)
        assertEquals("HDFC Bank", detectedBank)

        val layout = PdfBankLayoutDetector.detectLayout(pages)
        assertNotNull(layout)
        assertTrue(layout!!.isValid)
        assertTrue(layout.hasSplitDebitCredit)
        assertNotNull(layout.dateColumn)
        assertNotNull(layout.descriptionColumn)
        assertNotNull(layout.debitColumn)
        assertNotNull(layout.creditColumn)
    }

    @Test
    fun testMultiPageStatementExtraction_extractsAllRowsAndHandlesWrappedNarration() {
        val pdfBytes = SyntheticPdfFactory.createMultiPageStatementPdf()
        val result = PdfStatementExtractor.extract(pdfBytes, testUserId)

        assertTrue(result.isSuccess)
        assertEquals("HDFC Bank", result.detectedBank)
        assertEquals(2, result.pagesProcessed)
        assertEquals(PdfDocumentType.Digital, result.documentType)
        assertFalse(result.requiresPassword)

        val txs = result.transactions
        // 3 rows on page 1, 2 rows on page 2 = 5 transactions
        assertEquals(5, txs.size)

        // Row 1: Swiggy Debit 450.00
        val row1 = txs[0]
        assertEquals("01 Oct 2026", row1.dateFormatted)
        assertEquals(45000L, row1.amountPaise)
        assertEquals(TransactionDirection.DEBIT, row1.direction)
        assertEquals("Swiggy", row1.recipientName)
        assertEquals("123456789012", row1.externalReference)
        assertEquals(RowValidationStatus.VALID, row1.validationStatus)

        // Row 2: Amazon Wrapped Narration Debit 1,299.00
        val row2 = txs[1]
        assertEquals("02 Oct 2026", row2.dateFormatted)
        assertEquals(129900L, row2.amountPaise)
        assertEquals(TransactionDirection.DEBIT, row2.direction)
        assertTrue("Wrapped text must be merged into narration",
            row2.description.contains("Order #402-9871234-9871221 Bangalore KA")
        )
        assertEquals("Amazon Retail India Private Limited", row2.recipientName)
        assertEquals(RowValidationStatus.VALID, row2.validationStatus)

        // Row 3: Salary Credit 50,000.00
        val row3 = txs[2]
        assertEquals("03 Oct 2026", row3.dateFormatted)
        assertEquals(5000000L, row3.amountPaise)
        assertEquals(TransactionDirection.CREDIT, row3.direction)
        assertEquals(RowValidationStatus.VALID, row3.validationStatus)

        // Row 4: Page 2 Uber Debit 320.00 (with different date format dd-MMM-yyyy)
        val row4 = txs[3]
        assertEquals("04 Oct 2026", row4.dateFormatted)
        assertEquals(32000L, row4.amountPaise)
        assertEquals(TransactionDirection.DEBIT, row4.direction)
        assertEquals("Uber", row4.recipientName)
        assertEquals(RowValidationStatus.VALID, row4.validationStatus)

        // Row 5: Page 2 Zomato Credit 150.00
        val row5 = txs[4]
        assertEquals("05 Oct 2026", row5.dateFormatted)
        assertEquals(15000L, row5.amountPaise)
        assertEquals(TransactionDirection.CREDIT, row5.direction)
        assertEquals(RowValidationStatus.VALID, row5.validationStatus)
    }

    @Test
    fun testImageOnlyScannedPdf_classifiedCorrectly() {
        val pdfBytes = SyntheticPdfFactory.createImageOnlyScannedPdf()
        val result = PdfStatementExtractor.extract(pdfBytes, testUserId)

        assertFalse(result.isSuccess)
        assertEquals(PdfDocumentType.ImageOnly, result.documentType)
        assertTrue(result.errorMessage!!.contains("scanned image", ignoreCase = true))
    }

    @Test
    fun testInsufficientTextPdf_classifiedCorrectly() {
        val pdfBytes = SyntheticPdfFactory.createInsufficientTextPdf()
        val result = PdfStatementExtractor.extract(pdfBytes, testUserId)

        assertFalse(result.isSuccess)
        assertTrue(result.documentType is PdfDocumentType.InsufficientText)
        val insufficient = result.documentType as PdfDocumentType.InsufficientText
        assertTrue(insufficient.charsFound < 100)
        assertEquals(1, insufficient.pageCount)
    }

    @Test
    fun testUnsupportedLayoutPdf_classifiedCorrectly() {
        val pdfBytes = SyntheticPdfFactory.createUnsupportedLayoutPdf()
        val result = PdfStatementExtractor.extract(pdfBytes, testUserId)

        assertFalse(result.isSuccess)
        assertEquals(PdfDocumentType.UnsupportedLayout, result.documentType)
        assertTrue(result.errorMessage!!.contains("standard bank statement table columns", ignoreCase = true))
    }

    @Test
    fun testPasswordProtectedPdf_requiresPasswordAndUnlocksSafely() {
        val password = "secretStatementPassword123"
        val pdfBytes = SyntheticPdfFactory.createPasswordProtectedPdf(password)

        // 1. Without password: fails with requiresPassword = true
        val lockedResult = PdfStatementExtractor.extract(pdfBytes, testUserId, password = null)
        assertFalse(lockedResult.isSuccess)
        assertTrue(lockedResult.requiresPassword)
        assertTrue(lockedResult.documentType is PdfDocumentType.PasswordProtected)

        // 2. With wrong password: fails with requiresPassword = true
        val wrongResult = PdfStatementExtractor.extract(pdfBytes, testUserId, password = "wrongPassword")
        assertFalse(wrongResult.isSuccess)
        assertTrue(wrongResult.requiresPassword)

        // 3. With correct password: successfully decrypts and extracts row
        val unlockedResult = PdfStatementExtractor.extract(pdfBytes, testUserId, password = password)
        assertTrue(unlockedResult.isSuccess)
        assertFalse(unlockedResult.requiresPassword)
        assertEquals(1, unlockedResult.transactions.size)
        assertEquals(22000L, unlockedResult.transactions[0].amountPaise)
        assertEquals(TransactionDirection.DEBIT, unlockedResult.transactions[0].direction)
    }
}
