package com.upisegregator.app.domain.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [CategoryNormalizer].
 */
class CategoryNormalizerTest {

    // ─── normalizeKey ──────────────────────────────────────────────────────

    @Test
    fun normalizeKey_normalString_returnsLowercaseTrimmed() {
        assertEquals("food", CategoryNormalizer.normalizeKey("Food"))
        assertEquals("food", CategoryNormalizer.normalizeKey("  Food  "))
        assertEquals("food", CategoryNormalizer.normalizeKey("FOOD"))
        assertEquals("food", CategoryNormalizer.normalizeKey("food"))
    }

    @Test
    fun normalizeKey_nullInput_returnsOtherKey() {
        assertEquals("other", CategoryNormalizer.normalizeKey(null))
    }

    @Test
    fun normalizeKey_blankInput_returnsOtherKey() {
        assertEquals("other", CategoryNormalizer.normalizeKey(""))
        assertEquals("other", CategoryNormalizer.normalizeKey("   "))
    }

    @Test
    fun normalizeKey_multiWordCategory_preservesSpaces() {
        assertEquals("dining out", CategoryNormalizer.normalizeKey("Dining Out"))
        assertEquals("dining out", CategoryNormalizer.normalizeKey("  Dining Out  "))
    }

    // ─── displayName ───────────────────────────────────────────────────────

    @Test
    fun displayName_nullInput_returnsOther() {
        assertEquals("Other", CategoryNormalizer.displayName(null))
    }

    @Test
    fun displayName_blankInput_returnsOther() {
        assertEquals("Other", CategoryNormalizer.displayName(""))
        assertEquals("Other", CategoryNormalizer.displayName("   "))
    }

    @Test
    fun displayName_allUpperCase_titleCases() {
        assertEquals("Food", CategoryNormalizer.displayName("FOOD"))
        assertEquals("Dining Out", CategoryNormalizer.displayName("DINING OUT"))
    }

    @Test
    fun displayName_allLowerCase_titleCases() {
        assertEquals("Food", CategoryNormalizer.displayName("food"))
        assertEquals("Dining Out", CategoryNormalizer.displayName("dining out"))
    }

    @Test
    fun displayName_mixedCase_preservedAsIs() {
        assertEquals("Food", CategoryNormalizer.displayName("Food"))
        assertEquals("Dining Out", CategoryNormalizer.displayName("Dining Out"))
        assertEquals("eBay", CategoryNormalizer.displayName("eBay"))
    }

    @Test
    fun displayName_withWhitespace_trimmedAndProcessed() {
        assertEquals("Food", CategoryNormalizer.displayName("  Food  "))
        // "FOOD" after trim is all upper → title-cased to "Food"
        assertEquals("Food", CategoryNormalizer.displayName("  FOOD  "))
    }

    @Test
    fun displayName_singleCharacter_works() {
        assertEquals("A", CategoryNormalizer.displayName("a"))
        assertEquals("A", CategoryNormalizer.displayName("A"))
    }

    // ─── bestDisplayName ───────────────────────────────────────────────────

    @Test
    fun bestDisplayName_prefersMixedCaseValue() {
        val values = listOf("FOOD", "Food", "food")
        assertEquals("Food", CategoryNormalizer.bestDisplayName(values))
    }

    @Test
    fun bestDisplayName_allUpperCase_titleCasesFirst() {
        val values = listOf("FOOD", "FOOD")
        assertEquals("Food", CategoryNormalizer.bestDisplayName(values))
    }

    @Test
    fun bestDisplayName_allNullOrBlank_returnsOther() {
        assertEquals("Other", CategoryNormalizer.bestDisplayName(listOf(null, "", "  ")))
    }

    @Test
    fun bestDisplayName_emptyList_returnsOther() {
        assertEquals("Other", CategoryNormalizer.bestDisplayName(emptyList()))
    }

    @Test
    fun bestDisplayName_mixOfNullsAndValues_selectsBestNonNull() {
        val values = listOf(null, "", "Groceries", "groceries")
        assertEquals("Groceries", CategoryNormalizer.bestDisplayName(values))
    }
}
