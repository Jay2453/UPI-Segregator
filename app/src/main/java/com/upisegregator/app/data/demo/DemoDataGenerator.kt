package com.upisegregator.app.data.demo

import com.upisegregator.app.data.local.entity.CategoryEntity
import com.upisegregator.app.data.local.entity.PaymentMethod
import com.upisegregator.app.data.local.entity.RecipientEntity
import com.upisegregator.app.data.local.entity.TransactionEntity
import com.upisegregator.app.data.local.entity.TransactionSource
import com.upisegregator.app.data.local.entity.TransactionStatus

/**
 * Generator for realistic sample demo data for Milestone 2.
 *
 * Invariants:
 * - All amounts are strictly positive [Long] values representing integer paise (1 INR = 100 paise).
 * - Every demo transaction has [TransactionSource.DEMO].
 * - Statuses include SUCCESS, PENDING, FAILED, and CANCELLED to verify that
 *   only SUCCESS transactions count toward total spending.
 * - Dates span the past 30 days for rich weekly/monthly segregation in future milestones.
 */
object DemoDataGenerator {

    val demoCategories: List<CategoryEntity> = listOf(
        CategoryEntity("cat_food", "Food & Dining", "restaurant", 0xFFF97316),
        CategoryEntity("cat_groceries", "Groceries", "shopping_cart", 0xFF10B981),
        CategoryEntity("cat_shopping", "Shopping", "shopping_bag", 0xFF8B5CF6),
        CategoryEntity("cat_transport", "Transportation", "directions_car", 0xFF3B82F6),
        CategoryEntity("cat_bills", "Bills & Utilities", "receipt", 0xFFF59E0B),
        CategoryEntity("cat_transfer", "P2P Transfers", "person", 0xFF06B6D4),
        CategoryEntity("cat_entertainment", "Entertainment", "movie", 0xFFEC4899),
        CategoryEntity("cat_health", "Health & Medical", "local_hospital", 0xFF14B8A6)
    )

    val demoRecipients: List<RecipientEntity> = listOf(
        RecipientEntity("rec_rahul", "Rahul Sharma", "rahul.sharma@okaxis", "+91 98765 43210"),
        RecipientEntity("rec_priya", "Priya Verma", "priya.verma@oksbi", "+91 98765 43211"),
        RecipientEntity("rec_amit", "Amit Patel", "amit.patel@paytm", "+91 98765 43212"),
        RecipientEntity("rec_neha", "Neha Singh", "neha.singh@okicici", "+91 98765 43213"),
        RecipientEntity("rec_swiggy", "Swiggy Foods", "swiggy@icici", null),
        RecipientEntity("rec_zomato", "Zomato Online", "zomato@hdfcbank", null),
        RecipientEntity("rec_blinkit", "Blinkit Quick Mart", "blinkit@yesbank", null),
        RecipientEntity("rec_amazon", "Amazon India", "amazon@apl", null),
        RecipientEntity("rec_uber", "Uber Rides India", "uber@kotak", null),
        RecipientEntity("rec_bescom", "BESCOM Electricity", "bescom.bills@sbi", null),
        RecipientEntity("rec_airtel", "Airtel Broadband", "airtel.bill@airtel", null),
        RecipientEntity("rec_chai", "Sharmaji Chai Corner", "sharmaji.chai@upi", null),
        RecipientEntity("rec_apollo", "Apollo Pharmacy", "apollo.meds@axisbank", null),
        RecipientEntity("rec_local_mart", "Daily Needs Kirana", null, null)
    )

    /**
     * Generates 28 realistic sample transactions distributed over the past 30 days.
     */
    fun generateDemoTransactions(baseTimeMillis: Long = System.currentTimeMillis()): List<TransactionEntity> {
        val oneHour = 3_600_000L
        val oneDay = 86_400_000L

        return listOf(
            // Today
            TransactionEntity(
                transactionId = "demo_tx_01",
                amountPaise = 4000L, // ₹40.00
                recipientId = "rec_chai",
                recipientName = "Sharmaji Chai Corner",
                paymentMethod = PaymentMethod.UPI,
                category = "Food & Dining",
                note = "Morning Ginger Chai & Bun Maska",
                transactionDate = baseTimeMillis - (2 * oneHour),
                transactionStatus = TransactionStatus.SUCCESS,
                transactionSource = TransactionSource.DEMO,
                externalReference = "UPI/DEMO/2026103001"
            ),
            TransactionEntity(
                transactionId = "demo_tx_02",
                amountPaise = 24900L, // ₹249.00
                recipientId = "rec_swiggy",
                recipientName = "Swiggy Foods",
                paymentMethod = PaymentMethod.UPI,
                category = "Food & Dining",
                note = "Lunch bowl order",
                transactionDate = baseTimeMillis - (5 * oneHour),
                transactionStatus = TransactionStatus.SUCCESS,
                transactionSource = TransactionSource.DEMO,
                externalReference = "UPI/DEMO/2026103002"
            ),
            TransactionEntity(
                transactionId = "demo_tx_03",
                amountPaise = 15000L, // ₹150.00
                recipientId = null,
                recipientName = "Auto Rickshaw",
                paymentMethod = PaymentMethod.CASH,
                category = "Transportation",
                note = "Metro station to office (Cash)",
                transactionDate = baseTimeMillis - (8 * oneHour),
                transactionStatus = TransactionStatus.SUCCESS,
                transactionSource = TransactionSource.DEMO
            ),

            // Yesterday
            TransactionEntity(
                transactionId = "demo_tx_04",
                amountPaise = 125000L, // ₹1,250.00
                recipientId = "rec_rahul",
                recipientName = "Rahul Sharma",
                paymentMethod = PaymentMethod.UPI,
                category = "P2P Transfers",
                note = "Weekend dinner split repayment",
                transactionDate = baseTimeMillis - oneDay - (3 * oneHour),
                transactionStatus = TransactionStatus.SUCCESS,
                transactionSource = TransactionSource.DEMO,
                externalReference = "UPI/DEMO/2026102901"
            ),
            TransactionEntity(
                transactionId = "demo_tx_05",
                amountPaise = 52050L, // ₹520.50
                recipientId = "rec_blinkit",
                recipientName = "Blinkit Quick Mart",
                paymentMethod = PaymentMethod.UPI,
                category = "Groceries",
                note = "Milk, bread, and fruits",
                transactionDate = baseTimeMillis - oneDay - (7 * oneHour),
                transactionStatus = TransactionStatus.SUCCESS,
                transactionSource = TransactionSource.DEMO,
                externalReference = "UPI/DEMO/2026102902"
            ),
            TransactionEntity(
                transactionId = "demo_tx_06",
                amountPaise = 180000L, // ₹1,800.00 (Pending - e.g. awaiting confirmation)
                recipientId = "rec_priya",
                recipientName = "Priya Verma",
                paymentMethod = PaymentMethod.UPI,
                category = "P2P Transfers",
                note = "Concert ticket advance (Awaiting Confirmation)",
                transactionDate = baseTimeMillis - oneDay - (10 * oneHour),
                transactionStatus = TransactionStatus.PENDING,
                transactionSource = TransactionSource.DEMO,
                externalReference = "UPI/DEMO/2026102903"
            ),

            // 2 Days Ago
            TransactionEntity(
                transactionId = "demo_tx_07",
                amountPaise = 34500L, // ₹345.00
                recipientId = "rec_uber",
                recipientName = "Uber Rides India",
                paymentMethod = PaymentMethod.UPI,
                category = "Transportation",
                note = "Cab ride to Koramangala",
                transactionDate = baseTimeMillis - (2 * oneDay) - (2 * oneHour),
                transactionStatus = TransactionStatus.SUCCESS,
                transactionSource = TransactionSource.DEMO,
                externalReference = "UPI/DEMO/2026102801"
            ),
            TransactionEntity(
                transactionId = "demo_tx_08",
                amountPaise = 185000L, // ₹1,850.00
                recipientId = "rec_bescom",
                recipientName = "BESCOM Electricity",
                paymentMethod = PaymentMethod.UPI,
                category = "Bills & Utilities",
                note = "October power bill",
                transactionDate = baseTimeMillis - (2 * oneDay) - (6 * oneHour),
                transactionStatus = TransactionStatus.SUCCESS,
                transactionSource = TransactionSource.DEMO,
                externalReference = "UPI/DEMO/2026102802"
            ),

            // 3 Days Ago
            TransactionEntity(
                transactionId = "demo_tx_09",
                amountPaise = 42000L, // ₹420.00
                recipientId = "rec_apollo",
                recipientName = "Apollo Pharmacy",
                paymentMethod = PaymentMethod.CARD,
                category = "Health & Medical",
                note = "Vitamins and first aid kit",
                transactionDate = baseTimeMillis - (3 * oneDay) - (4 * oneHour),
                transactionStatus = TransactionStatus.SUCCESS,
                transactionSource = TransactionSource.DEMO,
                externalReference = "CARD/DEMO/2026102701"
            ),
            TransactionEntity(
                transactionId = "demo_tx_10",
                amountPaise = 35000L, // ₹350.00
                recipientId = "rec_amit",
                recipientName = "Amit Patel",
                paymentMethod = PaymentMethod.UPI,
                category = "P2P Transfers",
                note = "Lunch split via UPI",
                transactionDate = baseTimeMillis - (3 * oneDay) - (9 * oneHour),
                transactionStatus = TransactionStatus.SUCCESS,
                transactionSource = TransactionSource.DEMO,
                externalReference = "UPI/DEMO/2026102702"
            ),
            TransactionEntity(
                transactionId = "demo_tx_11",
                amountPaise = 50000L, // ₹500.00 (Failed transaction)
                recipientId = "rec_neha",
                recipientName = "Neha Singh",
                paymentMethod = PaymentMethod.UPI,
                category = "P2P Transfers",
                note = "Failed payment - Bank server timeout",
                transactionDate = baseTimeMillis - (3 * oneDay) - (11 * oneHour),
                transactionStatus = TransactionStatus.FAILED,
                transactionSource = TransactionSource.DEMO,
                externalReference = "UPI/DEMO/2026102703"
            ),

            // 5 Days Ago
            TransactionEntity(
                transactionId = "demo_tx_12",
                amountPaise = 249900L, // ₹2,499.00
                recipientId = "rec_amazon",
                recipientName = "Amazon India",
                paymentMethod = PaymentMethod.CARD,
                category = "Shopping",
                note = "Wireless ergonomic mouse & desk mat",
                transactionDate = baseTimeMillis - (5 * oneDay) - (1 * oneHour),
                transactionStatus = TransactionStatus.SUCCESS,
                transactionSource = TransactionSource.DEMO,
                externalReference = "CARD/DEMO/2026102501"
            ),
            TransactionEntity(
                transactionId = "demo_tx_13",
                amountPaise = 119900L, // ₹1,199.00
                recipientId = "rec_airtel",
                recipientName = "Airtel Broadband",
                paymentMethod = PaymentMethod.UPI,
                category = "Bills & Utilities",
                note = "Fiber broadband monthly recharge",
                transactionDate = baseTimeMillis - (5 * oneDay) - (5 * oneHour),
                transactionStatus = TransactionStatus.SUCCESS,
                transactionSource = TransactionSource.DEMO,
                externalReference = "UPI/DEMO/2026102502"
            ),

            // 7 Days Ago (1 Week Ago)
            TransactionEntity(
                transactionId = "demo_tx_14",
                amountPaise = 65000L, // ₹650.00
                recipientId = "rec_zomato",
                recipientName = "Zomato Online",
                paymentMethod = PaymentMethod.UPI,
                category = "Food & Dining",
                note = "Sunday Biryani celebration",
                transactionDate = baseTimeMillis - (7 * oneDay) - (3 * oneHour),
                transactionStatus = TransactionStatus.SUCCESS,
                transactionSource = TransactionSource.DEMO,
                externalReference = "UPI/DEMO/2026102301"
            ),
            TransactionEntity(
                transactionId = "demo_tx_15",
                amountPaise = 20000L, // ₹200.00
                recipientId = "rec_local_mart",
                recipientName = "Daily Needs Kirana",
                paymentMethod = PaymentMethod.CASH,
                category = "Groceries",
                note = "Vegetables & spices (Cash)",
                transactionDate = baseTimeMillis - (7 * oneDay) - (7 * oneHour),
                transactionStatus = TransactionStatus.SUCCESS,
                transactionSource = TransactionSource.DEMO
            ),

            // 10 Days Ago
            TransactionEntity(
                transactionId = "demo_tx_16",
                amountPaise = 300000L, // ₹3,000.00
                recipientId = "rec_neha",
                recipientName = "Neha Singh",
                paymentMethod = PaymentMethod.BANK_TRANSFER,
                category = "P2P Transfers",
                note = "Group trip cabin booking contribution",
                transactionDate = baseTimeMillis - (10 * oneDay) - (4 * oneHour),
                transactionStatus = TransactionStatus.SUCCESS,
                transactionSource = TransactionSource.DEMO,
                externalReference = "IMPS/DEMO/2026102001"
            ),
            TransactionEntity(
                transactionId = "demo_tx_17",
                amountPaise = 85000L, // ₹850.00
                recipientId = null,
                recipientName = "PVR Cinemas",
                paymentMethod = PaymentMethod.UPI,
                category = "Entertainment",
                note = "Weekend IMAX movie tickets",
                transactionDate = baseTimeMillis - (10 * oneDay) - (8 * oneHour),
                transactionStatus = TransactionStatus.SUCCESS,
                transactionSource = TransactionSource.DEMO,
                externalReference = "UPI/DEMO/2026102002"
            ),

            // 12 Days Ago
            TransactionEntity(
                transactionId = "demo_tx_18",
                amountPaise = 45000L, // ₹450.00 (Cancelled transaction)
                recipientId = "rec_swiggy",
                recipientName = "Swiggy Foods",
                paymentMethod = PaymentMethod.UPI,
                category = "Food & Dining",
                note = "Order cancelled by user at UPI prompt",
                transactionDate = baseTimeMillis - (12 * oneDay) - (2 * oneHour),
                transactionStatus = TransactionStatus.CANCELLED,
                transactionSource = TransactionSource.DEMO
            ),
            TransactionEntity(
                transactionId = "demo_tx_19",
                amountPaise = 82000L, // ₹820.00
                recipientId = "rec_blinkit",
                recipientName = "Blinkit Quick Mart",
                paymentMethod = PaymentMethod.UPI,
                category = "Groceries",
                note = "Snacks, juice, and cooking oil",
                transactionDate = baseTimeMillis - (12 * oneDay) - (6 * oneHour),
                transactionStatus = TransactionStatus.SUCCESS,
                transactionSource = TransactionSource.DEMO,
                externalReference = "UPI/DEMO/2026101801"
            ),

            // 15 Days Ago (2 Weeks Ago)
            TransactionEntity(
                transactionId = "demo_tx_20",
                amountPaise = 75000L, // ₹750.00
                recipientId = "rec_priya",
                recipientName = "Priya Verma",
                paymentMethod = PaymentMethod.UPI,
                category = "P2P Transfers",
                note = "Birthday gift sharing",
                transactionDate = baseTimeMillis - (15 * oneDay) - (5 * oneHour),
                transactionStatus = TransactionStatus.SUCCESS,
                transactionSource = TransactionSource.DEMO,
                externalReference = "UPI/DEMO/2026101501"
            ),
            TransactionEntity(
                transactionId = "demo_tx_21",
                amountPaise = 21000L, // ₹210.00
                recipientId = "rec_uber",
                recipientName = "Uber Rides India",
                paymentMethod = PaymentMethod.UPI,
                category = "Transportation",
                note = "Auto ride from railway station",
                transactionDate = baseTimeMillis - (15 * oneDay) - (9 * oneHour),
                transactionStatus = TransactionStatus.SUCCESS,
                transactionSource = TransactionSource.DEMO,
                externalReference = "UPI/DEMO/2026101502"
            ),

            // 18 Days Ago
            TransactionEntity(
                transactionId = "demo_tx_22",
                amountPaise = 159900L, // ₹1,599.00
                recipientId = "rec_amazon",
                recipientName = "Amazon India",
                paymentMethod = PaymentMethod.CARD,
                category = "Shopping",
                note = "Books & stationery supply",
                transactionDate = baseTimeMillis - (18 * oneDay) - (3 * oneHour),
                transactionStatus = TransactionStatus.SUCCESS,
                transactionSource = TransactionSource.DEMO,
                externalReference = "CARD/DEMO/2026101201"
            ),
            TransactionEntity(
                transactionId = "demo_tx_23",
                amountPaise = 6000L, // ₹60.00
                recipientId = "rec_chai",
                recipientName = "Sharmaji Chai Corner",
                paymentMethod = PaymentMethod.UPI,
                category = "Food & Dining",
                note = "Evening tea & samosa",
                transactionDate = baseTimeMillis - (18 * oneDay) - (8 * oneHour),
                transactionStatus = TransactionStatus.SUCCESS,
                transactionSource = TransactionSource.DEMO,
                externalReference = "UPI/DEMO/2026101202"
            ),

            // 21 Days Ago (3 Weeks Ago)
            TransactionEntity(
                transactionId = "demo_tx_24",
                amountPaise = 250000L, // ₹2,500.00
                recipientId = "rec_amit",
                recipientName = "Amit Patel",
                paymentMethod = PaymentMethod.UPI,
                category = "P2P Transfers",
                note = "Repaying advance for shared groceries",
                transactionDate = baseTimeMillis - (21 * oneDay) - (4 * oneHour),
                transactionStatus = TransactionStatus.SUCCESS,
                transactionSource = TransactionSource.DEMO,
                externalReference = "UPI/DEMO/2026100901"
            ),
            TransactionEntity(
                transactionId = "demo_tx_25",
                amountPaise = 32000L, // ₹320.00
                recipientId = null,
                recipientName = "City Cafe",
                paymentMethod = PaymentMethod.CASH,
                category = "Food & Dining",
                note = "Cappuccino & croissant (Cash)",
                transactionDate = baseTimeMillis - (21 * oneDay) - (9 * oneHour),
                transactionStatus = TransactionStatus.SUCCESS,
                transactionSource = TransactionSource.DEMO
            ),

            // 25 Days Ago
            TransactionEntity(
                transactionId = "demo_tx_26",
                amountPaise = 95000L, // ₹950.00
                recipientId = "rec_apollo",
                recipientName = "Apollo Pharmacy",
                paymentMethod = PaymentMethod.UPI,
                category = "Health & Medical",
                note = "Prescription medicines for family",
                transactionDate = baseTimeMillis - (25 * oneDay) - (2 * oneHour),
                transactionStatus = TransactionStatus.SUCCESS,
                transactionSource = TransactionSource.DEMO,
                externalReference = "UPI/DEMO/2026100501"
            ),
            TransactionEntity(
                transactionId = "demo_tx_27",
                amountPaise = 400000L, // ₹4,000.00 (Pending - large transfer check)
                recipientId = "rec_rahul",
                recipientName = "Rahul Sharma",
                paymentMethod = PaymentMethod.UPI,
                category = "P2P Transfers",
                note = "Rent split payment (Pending UPI Confirmation)",
                transactionDate = baseTimeMillis - (25 * oneDay) - (7 * oneHour),
                transactionStatus = TransactionStatus.PENDING,
                transactionSource = TransactionSource.DEMO,
                externalReference = "UPI/DEMO/2026100502"
            ),

            // 28 Days Ago (4 Weeks Ago)
            TransactionEntity(
                transactionId = "demo_tx_28",
                amountPaise = 145000L, // ₹1,450.00
                recipientId = "rec_blinkit",
                recipientName = "Blinkit Quick Mart",
                paymentMethod = PaymentMethod.UPI,
                category = "Groceries",
                note = "Monthly staple groceries order",
                transactionDate = baseTimeMillis - (28 * oneDay) - (5 * oneHour),
                transactionStatus = TransactionStatus.SUCCESS,
                transactionSource = TransactionSource.DEMO,
                externalReference = "UPI/DEMO/2026100201"
            )
        )
    }
}
