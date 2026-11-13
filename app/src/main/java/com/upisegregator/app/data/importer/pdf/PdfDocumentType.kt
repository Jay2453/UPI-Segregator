package com.upisegregator.app.data.importer.pdf

/**
 * Describes the structural type of a PDF document encountered by the extraction engine.
 *
 * These variants are mutually exclusive and control which code path is taken and which
 * user message is shown when extraction cannot proceed normally.
 */
sealed class PdfDocumentType {

    /** The PDF contains selectable text and a recognizable transaction table. */
    object Digital : PdfDocumentType()

    /**
     * Every page in the document consists entirely of rasterized images.
     * No text layer exists whatsoever (char count across the whole document is 0
     * or fewer than [NEAR_ZERO_CHARS_THRESHOLD] characters with no font objects found).
     */
    object ImageOnly : PdfDocumentType()

    /**
     * The PDF has some text (e.g. a header or page numbers) but not enough to reconstruct
     * a transaction table. This may indicate a partially scanned document or an unusual layout
     * that is neither fully digital nor fully image-based.
     *
     * @param charsFound  Total characters extracted across the document.
     * @param pageCount   Total page count.
     */
    data class InsufficientText(val charsFound: Int, val pageCount: Int) : PdfDocumentType()

    /**
     * Sufficient text was found, but the column layout does not match any recognised
     * bank statement format. The text exists but cannot be parsed into transactions.
     */
    object UnsupportedLayout : PdfDocumentType()

    /**
     * The PDF is encrypted and requires a password to open.
     * The [hint] is optional and comes from the PDF's encryption dictionary if present.
     */
    data class PasswordProtected(val hint: String? = null) : PdfDocumentType()

    companion object {
        /**
         * A document with fewer than this many characters in total is classified as
         * [ImageOnly] rather than [InsufficientText], because such a small count most
         * likely reflects only PDF metadata strings, not real content.
         */
        const val NEAR_ZERO_CHARS_THRESHOLD = 10

        /**
         * Average characters per page below this value (but above [NEAR_ZERO_CHARS_THRESHOLD]
         * per page total) triggers [InsufficientText] rather than [Digital].
         *
         * A single transaction row in a standard bank statement typically contains ~80 characters.
         * Three rows = ~240 chars. We set the bar at 100 chars/page to avoid false positives for
         * short statements.
         */
        const val MIN_TABLE_CHARS_PER_PAGE = 100
    }
}
