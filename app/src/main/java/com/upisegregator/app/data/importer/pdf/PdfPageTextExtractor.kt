package com.upisegregator.app.data.importer.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.IOException
import java.util.TreeMap
import kotlin.math.roundToInt

/**
 * Text token extracted from a PDF page, with its X/Y position on the page.
 *
 * @param text   The text string of this run.
 * @param x      X position (points from left margin), coordinate-adjusted.
 * @param y      Y position (points from top of page), coordinate-adjusted.
 */
data class TextToken(val text: String, val x: Float, val y: Float)

/**
 * A single logical line of text, reconstructed by grouping tokens with similar Y positions.
 *
 * @param y      Rounded Y coordinate of the line.
 * @param tokens Tokens in this line, sorted left-to-right by X position.
 */
data class TextLine(
    val y: Float,
    val tokens: List<TextToken>
) {
    /** Convenience: full text of the line, joining tokens with single spaces. */
    val fullText: String get() = tokens.joinToString(" ") { it.text }

    /** Total character count of this line. */
    val charCount: Int get() = tokens.sumOf { it.text.length }
}

/**
 * Result of extracting positional text from a single page.
 *
 * @param pageNum  1-based page number.
 * @param lines    Logical lines, top-to-bottom.
 * @param rawCharCount  Total characters extracted from this page.
 */
data class PageTextResult(
    val pageNum: Int,
    val lines: List<TextLine>,
    val rawCharCount: Int
)

/**
 * PDFBox [PDFTextStripper] subclass that captures the X/Y position of every text run
 * in addition to its content.
 *
 * [writeString] is overridden to record each text run's first-character coordinate.
 * The parent's output writer is intentionally not written to — callers consume
 * [tokensByY] directly instead of the stripper's string output.
 *
 * Y coordinates are rounded to the nearest 2-point grid to absorb sub-pixel variation
 * within the same visual line.
 */
internal class PositionalPdfTextStripper : PDFTextStripper() {

    /** Y (rounded) → sorted list of (X, text) pairs accumulated during getText(). */
    private val _tokensByY = TreeMap<Float, MutableList<Pair<Float, String>>>()
    val tokensByY: TreeMap<Float, MutableList<Pair<Float, String>>> get() = _tokensByY

    @Throws(IOException::class)
    override fun writeString(text: String, textPositions: List<TextPosition>) {
        if (text.isBlank() || textPositions.isEmpty()) return
        val first = textPositions.first()
        val x = first.xDirAdj
        val y = first.yDirAdj
        // Round Y to 2-point grid so tokens on the same visual line group together
        val roundedY = (y / 2f).roundToInt() * 2f
        _tokensByY.getOrPut(roundedY) { mutableListOf() }.add(Pair(x, text))
        // Do NOT call super — we don't use the string output
    }

    fun reset() = _tokensByY.clear()
}

/**
 * Extracts positional text from a [PDDocument], page by page.
 *
 * Returns a [PageTextResult] per page, each containing [TextLine]s whose tokens are
 * sorted left-to-right, with lines ordered top-to-bottom.
 */
object PdfPageTextExtractor {

    /**
     * Extracts lines from every page of [document].
     *
     * @param document  An already-opened (and optionally decrypted) [PDDocument].
     * @return One [PageTextResult] per page, in page order.
     */
    fun extractAllPages(document: PDDocument): List<PageTextResult> {
        val results = mutableListOf<PageTextResult>()
        val stripper = PositionalPdfTextStripper()
        stripper.sortByPosition = true

        for (pageNum in 1..document.numberOfPages) {
            stripper.reset()
            stripper.startPage = pageNum
            stripper.endPage = pageNum

            // Trigger page processing — output is captured via writeString() override
            stripper.getText(document)

            val lines = stripper.tokensByY
                .entries
                // Sort top-to-bottom: smaller Y = higher on page in coordinate-adjusted space
                .sortedBy { it.key }
                .map { (y, pairs) ->
                    val sortedTokens = pairs
                        .sortedBy { it.first }
                        .map { (x, text) -> TextToken(text, x, y) }
                    TextLine(y = y, tokens = sortedTokens)
                }

            val charCount = lines.sumOf { it.charCount }
            results.add(PageTextResult(pageNum = pageNum, lines = lines, rawCharCount = charCount))
        }

        return results
    }

    /** Counts total characters across all pages of [document]. */
    fun totalCharsInDocument(document: PDDocument): Int {
        return extractAllPages(document).sumOf { it.rawCharCount }
    }
}
