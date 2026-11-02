package com.upisegregator.app.data.importer.csv

import com.upisegregator.app.data.local.entity.PaymentMethod
import java.util.Locale

/**
 * Intelligent category and payment method classifier for imported statement transactions.
 */
object TransactionClassifier {

    private val FOOD_KEYWORDS = listOf(
        "swiggy", "zomato", "mcdonald", "kfc", "domino", "pizza", "burger", "cafe",
        "coffee", "starbucks", "restaurant", "hotel", "food", "barbeque", "dhaba",
        "bakery", "sweet", "chaat", "eats"
    )

    private val TRANSPORT_KEYWORDS = listOf(
        "uber", "ola", "rapido", "metro", "irctc", "railway", "railways", "flight",
        "indigo", "air india", "petrol", "fuel", "hpcl", "bpcl", "ioc", "shell",
        "toll", "fastag", "parking", "auto", "taxi", "bus", "redbus"
    )

    private val SHOPPING_KEYWORDS = listOf(
        "amazon", "flipkart", "myntra", "ajio", "zara", "h&m", "retail", "mart",
        "clothing", "fashion", "electronics", "croma", "reliancedigital", "ikea",
        "decathlon", "meesho", "tatacliq", "lifestyle", "shoppers stop"
    )

    private val EDUCATION_KEYWORDS = listOf(
        "school", "college", "university", "institute", "academy", "udemy",
        "coursera", "edx", "books", "tuition", "fees", "stationery", "exam"
    )

    private val ENTERTAINMENT_KEYWORDS = listOf(
        "netflix", "spotify", "prime", "hotstar", "disney", "youtube", "cinema",
        "pvr", "inox", "bookmyshow", "game", "steam", "playstation", "theatre"
    )

    private val GROCERIES_KEYWORDS = listOf(
        "blinkit", "zepto", "instamart", "bigbasket", "dmart", "reliance fresh",
        "supermarket", "groceries", "grocery", "vegetables", "fruits", "dairy",
        "milk", "nature basket", "spencer"
    )

    private val BILLS_KEYWORDS = listOf(
        "electricity", "water", "gas", "bescom", "mseb", "tneb", "jio", "airtel",
        "vi", "vodafone", "bsnl", "broadband", "act fibernet", "wifi", "bill",
        "recharge", "utility", "dth", "tata play", "insurance", "lic"
    )

    private val HEALTH_KEYWORDS = listOf(
        "hospital", "pharmacy", "apollo", "medplus", "1mg", "pharmeasy", "practo",
        "clinic", "health", "dental", "doctor", "lab", "diagnostic", "opticals"
    )

    /**
     * Determines the most appropriate category among the 9 standard categories.
     */
    fun estimateCategory(recipient: String, description: String): String {
        val combined = "$recipient $description".lowercase(Locale.US)

        return when {
            FOOD_KEYWORDS.any { combined.contains(it) } -> "Food"
            GROCERIES_KEYWORDS.any { combined.contains(it) } -> "Groceries"
            TRANSPORT_KEYWORDS.any { combined.contains(it) } -> "Transport"
            SHOPPING_KEYWORDS.any { combined.contains(it) } -> "Shopping"
            BILLS_KEYWORDS.any { combined.contains(it) } -> "Bills"
            ENTERTAINMENT_KEYWORDS.any { combined.contains(it) } -> "Entertainment"
            EDUCATION_KEYWORDS.any { combined.contains(it) } -> "Education"
            HEALTH_KEYWORDS.any { combined.contains(it) } -> "Health"
            else -> "Other"
        }
    }

    /**
     * Determines payment method based on narration patterns.
     */
    fun estimatePaymentMethod(narration: String): PaymentMethod {
        val upper = narration.uppercase(Locale.US)
        return when {
            upper.contains("UPI") -> PaymentMethod.UPI
            upper.contains("POS") || upper.contains("CARD") || upper.contains("DEBIT CARD") ||
                    upper.contains("VISA") || upper.contains("MASTERCARD") -> PaymentMethod.CARD
            upper.contains("NEFT") || upper.contains("RTGS") || upper.contains("IMPS") ||
                    upper.contains("TRANSFER") || upper.contains("INB") || upper.contains("ACH") -> PaymentMethod.BANK_TRANSFER
            else -> PaymentMethod.OTHER
        }
    }
}
