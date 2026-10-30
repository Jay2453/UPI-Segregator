package com.upisegregator.app.data.upi

/**
 * Result of attempting to verify payment settlement with an authoritative banking source.
 */
sealed class PaymentVerificationResult {
    /**
     * The payment has been verified by an authoritative backend provider.
     */
    data class Verified(
        val isSettled: Boolean,
        val providerReference: String,
        val message: String
    ) : PaymentVerificationResult()

    /**
     * Independent verification is not supported in the current environment (e.g. Local-first V1).
     */
    data class NotSupported(
        val reason: String
    ) : PaymentVerificationResult()

    /**
     * Verification encountered an error.
     */
    data class Error(
        val message: String
    ) : PaymentVerificationResult()
}

/**
 * Interface defining contract for payment status verification.
 *
 * ARCHITECTURAL SPECIFICATION:
 * In UPI Segregator V1, the application is local-first without a paid server or direct
 * NPCI/bank PSP integration. Client-side intent responses from apps cannot be independently
 * verified against bank ledger settlement without a server-to-server webhook or PSP API.
 *
 * This interface defines the contract so that future versions can plug in a real PSP
 * verification provider (e.g. Razorpay, Cashfree, or bank open APIs) without altering
 * the UI or Room persistence architecture.
 */
interface PaymentVerificationProvider {
    suspend fun verifyPayment(
        transactionId: String,
        externalReference: String?
    ): PaymentVerificationResult
}

/**
 * Default local-first verification provider.
 * Transparently indicates that local intent handoff is not bank-verified.
 */
class LocalPaymentVerificationProvider : PaymentVerificationProvider {
    override suspend fun verifyPayment(
        transactionId: String,
        externalReference: String?
    ): PaymentVerificationResult {
        return PaymentVerificationResult.NotSupported(
            reason = "Local-first mode cannot independently verify bank settlement. " +
                    "Bank verification requires a server-to-server PSP gateway or bank webhook integration."
        )
    }
}
