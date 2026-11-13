package com.upisegregator.app.data.importer.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.ByteArrayOutputStream

/**
 * Creates programmatic synthetic PDF bank statements for automated unit tests.
 * Never uses personal or external bank statements.
 */
object SyntheticPdfFactory {

    /**
     * Generates a 2-page synthetic HDFC Bank statement PDF.
     * Includes:
     * - Bank header on Page 1
     * - Repeated column headers on Page 2
     * - Multi-line / wrapped transaction narrations
     * - Split Debit / Credit columns
     * - Varied date formats (dd/MM/yyyy and dd-MMM-yyyy)
     * - Closing balance and footer strings
     */
    fun createMultiPageStatementPdf(): ByteArray {
        val document = PDDocument()
        val font = PDType1Font.HELVETICA
        val boldFont = PDType1Font.HELVETICA_BOLD

        // ---------------- PAGE 1 ----------------
        val page1 = PDPage()
        document.addPage(page1)
        val cs1 = PDPageContentStream(document, page1)

        // Bank Title & Account Info
        cs1.beginText()
        cs1.setFont(boldFont, 14f)
        cs1.newLineAtOffset(50f, 750f)
        cs1.showText("HDFC BANK STATEMENT OF ACCOUNT")
        cs1.endText()

        cs1.beginText()
        cs1.setFont(font, 10f)
        cs1.newLineAtOffset(50f, 730f)
        cs1.showText("Account Number: 50100234567890 | Customer ID: 98765432")
        cs1.endText()

        // Page 1 Table Header at Y = 680
        writeRow(cs1, boldFont, 10f, 680f,
            date = "Date",
            desc = "Narration / Details",
            ref = "Chq/Ref No",
            debit = "Debit",
            credit = "Credit",
            bal = "Balance"
        )

        // Row 1: Normal Debit (Swiggy)
        writeRow(cs1, font, 9f, 650f,
            date = "01/10/2026",
            desc = "UPI/DR/123456789012/Swiggy/swiggy@icici",
            ref = "123456789012",
            debit = "450.00",
            credit = "",
            bal = "14,550.00"
        )

        // Row 2: Wrapped Debit (Amazon)
        writeRow(cs1, font, 9f, 620f,
            date = "02/10/2026",
            desc = "UPI-Amazon Retail India Private Limited-amazon@apl",
            ref = "987654321098",
            debit = "1,299.00",
            credit = "",
            bal = "13,251.00"
        )
        // Wrapped continuation line
        cs1.beginText()
        cs1.setFont(font, 9f)
        cs1.newLineAtOffset(130f, 608f)
        cs1.showText("Order #402-9871234-9871221 Bangalore KA")
        cs1.endText()

        // Row 3: Credit (Salary)
        writeRow(cs1, font, 9f, 580f,
            date = "03/10/2026",
            desc = "NEFT-ACME CORP-OCTOBER SALARY",
            ref = "ACME98765432",
            debit = "",
            credit = "50,000.00",
            bal = "63,251.00"
        )

        // Page 1 Footer
        cs1.beginText()
        cs1.setFont(font, 8f)
        cs1.newLineAtOffset(50f, 40f)
        cs1.showText("Page 1 of 2 - Generated on 08/11/2026")
        cs1.endText()
        cs1.close()

        // ---------------- PAGE 2 ----------------
        val page2 = PDPage()
        document.addPage(page2)
        val cs2 = PDPageContentStream(document, page2)

        // Page 2 Header info
        cs2.beginText()
        cs2.setFont(font, 10f)
        cs2.newLineAtOffset(50f, 750f)
        cs2.showText("HDFC Bank Account Statement (Continued)")
        cs2.endText()

        // Repeated Table Header on Page 2
        writeRow(cs2, boldFont, 10f, 710f,
            date = "Date",
            desc = "Narration / Details",
            ref = "Chq/Ref No",
            debit = "Debit",
            credit = "Credit",
            bal = "Balance"
        )

        // Row 4 on Page 2: Debit (Uber) with different date format
        writeRow(cs2, font, 9f, 680f,
            date = "04-Oct-2026",
            desc = "UPI/DR/554433221100/Uber/uber@axis",
            ref = "554433221100",
            debit = "320.00",
            credit = "",
            bal = "62,931.00"
        )

        // Row 5 on Page 2: Credit (Refund)
        writeRow(cs2, font, 9f, 650f,
            date = "05-Oct-2026",
            desc = "UPI/CR/998877665544/Zomato/refund",
            ref = "998877665544",
            debit = "",
            credit = "150.00",
            bal = "63,081.00"
        )

        // Closing summary / footer
        cs2.beginText()
        cs2.setFont(boldFont, 9f)
        cs2.newLineAtOffset(50f, 600f)
        cs2.showText("Statement Summary: Closing Balance: INR 63,081.00")
        cs2.endText()

        cs2.beginText()
        cs2.setFont(font, 8f)
        cs2.newLineAtOffset(50f, 40f)
        cs2.showText("Page 2 of 2 - End of Statement")
        cs2.endText()
        cs2.close()

        val baos = ByteArrayOutputStream()
        document.save(baos)
        document.close()
        return baos.toByteArray()
    }

    /**
     * Generates an encrypted (password-protected) PDF.
     */
    fun createPasswordProtectedPdf(password: String): ByteArray {
        val document = PDDocument()
        val page = PDPage()
        document.addPage(page)

        val cs = PDPageContentStream(document, page)
        cs.beginText()
        cs.setFont(PDType1Font.HELVETICA_BOLD, 12f)
        cs.newLineAtOffset(50f, 700f)
        cs.showText("HDFC Bank Confidentially Protected Statement")
        cs.endText()

        writeRow(cs, PDType1Font.HELVETICA_BOLD, 10f, 660f,
            date = "Date", desc = "Description", ref = "Ref", debit = "Debit", credit = "Credit", bal = "Balance"
        )
        writeRow(cs, PDType1Font.HELVETICA, 9f, 630f,
            date = "10/10/2026", desc = "UPI/DR/111222333444/CafeCoffeeDay", ref = "111222333444", debit = "220.00", credit = "", bal = "9,780.00"
        )
        cs.close()

        val ap = AccessPermission()
        val spp = StandardProtectionPolicy("ownerSecretPass", password, ap)
        spp.encryptionKeyLength = 128
        spp.permissions = ap
        document.protect(spp)

        val baos = ByteArrayOutputStream()
        document.save(baos)
        document.close()
        return baos.toByteArray()
    }

    /**
     * Generates a PDF with 0 text characters (simulating a scanned image PDF).
     */
    fun createImageOnlyScannedPdf(): ByteArray {
        val document = PDDocument()
        val page = PDPage()
        document.addPage(page)
        // Add no text content
        val baos = ByteArrayOutputStream()
        document.save(baos)
        document.close()
        return baos.toByteArray()
    }

    /**
     * Generates a PDF with very few characters (under 100 chars / page threshold).
     */
    fun createInsufficientTextPdf(): ByteArray {
        val document = PDDocument()
        val page = PDPage()
        document.addPage(page)

        val cs = PDPageContentStream(document, page)
        cs.beginText()
        cs.setFont(PDType1Font.HELVETICA, 10f)
        cs.newLineAtOffset(50f, 700f)
        cs.showText("Account Notice - Page 1") // ~23 characters total
        cs.endText()
        cs.close()

        val baos = ByteArrayOutputStream()
        document.save(baos)
        document.close()
        return baos.toByteArray()
    }

    /**
     * Generates a PDF with sufficient text, but completely unsupported layout (no table headers).
     */
    fun createUnsupportedLayoutPdf(): ByteArray {
        val document = PDDocument()
        val page = PDPage()
        document.addPage(page)

        val cs = PDPageContentStream(document, page)
        cs.beginText()
        cs.setFont(PDType1Font.HELVETICA, 10f)
        cs.newLineAtOffset(50f, 700f)
        cs.showText("Dear Customer, thank you for banking with us.")
        cs.endText()

        cs.beginText()
        cs.setFont(PDType1Font.HELVETICA, 10f)
        cs.newLineAtOffset(50f, 670f)
        cs.showText("Please review your terms and conditions for credit card insurance services.")
        cs.endText()

        cs.beginText()
        cs.setFont(PDType1Font.HELVETICA, 10f)
        cs.newLineAtOffset(50f, 640f)
        cs.showText("For support, call 1800-123-4567 or visit your nearest branch during normal hours.")
        cs.endText()
        cs.close()

        val baos = ByteArrayOutputStream()
        document.save(baos)
        document.close()
        return baos.toByteArray()
    }

    private fun writeRow(
        cs: PDPageContentStream,
        font: PDType1Font,
        size: Float,
        y: Float,
        date: String,
        desc: String,
        ref: String,
        debit: String,
        credit: String,
        bal: String
    ) {
        // Date col at X = 50
        cs.beginText()
        cs.setFont(font, size)
        cs.newLineAtOffset(50f, y)
        cs.showText(date)
        cs.endText()

        // Narration col at X = 130
        cs.beginText()
        cs.setFont(font, size)
        cs.newLineAtOffset(130f, y)
        cs.showText(desc)
        cs.endText()

        // Reference col at X = 330
        cs.beginText()
        cs.setFont(font, size)
        cs.newLineAtOffset(330f, y)
        cs.showText(ref)
        cs.endText()

        // Debit col at X = 420
        cs.beginText()
        cs.setFont(font, size)
        cs.newLineAtOffset(420f, y)
        cs.showText(debit)
        cs.endText()

        // Credit col at X = 475
        cs.beginText()
        cs.setFont(font, size)
        cs.newLineAtOffset(475f, y)
        cs.showText(credit)
        cs.endText()

        // Balance col at X = 530
        cs.beginText()
        cs.setFont(font, size)
        cs.newLineAtOffset(530f, y)
        cs.showText(bal)
        cs.endText()
    }
}
