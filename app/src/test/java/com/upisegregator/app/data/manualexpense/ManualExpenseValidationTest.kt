package com.upisegregator.app.data.manualexpense

import com.upisegregator.app.data.local.entity.PaymentMethod
import com.upisegregator.app.ui.screens.manualexpense.MANUAL_EXPENSE_CATEGORIES
import com.upisegregator.app.util.PaiseUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualExpenseValidationTest {

    @Test
    fun amountValidation_validAmounts_parseCorrectly() {
        assertEquals(10000L, PaiseUtils.rupeesToPaise("100"))
        assertEquals(2550L, PaiseUtils.rupeesToPaise("25.50"))
        assertEquals(50L, PaiseUtils.rupeesToPaise("0.50"))
        assertEquals(999900L, PaiseUtils.rupeesToPaise("9999"))
    }

    @Test
    fun amountValidation_invalidOrZeroAmounts() {
        assertNull(PaiseUtils.rupeesToPaiseOrNull("0"))
        assertNull(PaiseUtils.rupeesToPaiseOrNull("-50"))
        assertNull(PaiseUtils.rupeesToPaiseOrNull("abc"))
        assertNull(PaiseUtils.rupeesToPaiseOrNull(""))
        assertNull(PaiseUtils.rupeesToPaiseOrNull(null))
    }

    @Test
    fun recipientNameValidation_rules() {
        fun isValidRecipient(name: String): Boolean = name.trim().length >= 2

        assertTrue(isValidRecipient("Shopkeeper"))
        assertTrue(isValidRecipient("AB"))
        assertFalse(isValidRecipient(""))
        assertFalse(isValidRecipient("   "))
        assertFalse(isValidRecipient("A"))
    }

    @Test
    fun categoryList_containsAllRequiredCategories() {
        val requiredCategories = listOf(
            "Food", "Transport", "Shopping", "Education",
            "Entertainment", "Groceries", "Bills", "Health", "Other"
        )
        // Verify all 9 specified categories are present in MANUAL_EXPENSE_CATEGORIES
        assertEquals(9, MANUAL_EXPENSE_CATEGORIES.size)
        requiredCategories.forEach { category ->
            assertTrue("Expected category $category to be present", MANUAL_EXPENSE_CATEGORIES.contains(category))
        }
    }

    @Test
    fun paymentMethod_supportsCashAndOther() {
        val cash = PaymentMethod.CASH
        val other = PaymentMethod.OTHER
        assertEquals("CASH", cash.name)
        assertEquals("OTHER", other.name)
    }
}
