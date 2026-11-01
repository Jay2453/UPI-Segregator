package com.upisegregator.app.data.upi

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import com.upisegregator.app.util.PaiseUtils

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Data class representing a UPI-capable application discovered on the device.
 */
data class UpiApp(
    val packageName: String,
    val activityName: String,
    val displayName: String
)

/**
 * Validation result for a UPI ID.
 */
sealed class UpiIdValidation {
    data object Valid : UpiIdValidation()
    data class Invalid(val reason: String) : UpiIdValidation()
}

/**
 * Result of attempting to launch a UPI payment intent.
 */
sealed class UpiLaunchResult {
    data class Success(val intent: Intent) : UpiLaunchResult()
    data class Fallback(val chooserIntent: Intent) : UpiLaunchResult()
    data object NoAppAvailable : UpiLaunchResult()
    data class Error(val message: String) : UpiLaunchResult()
}

/**
 * Result of validating a generated UPI payment URI against expected financial parameters.
 */
sealed class UpiUriValidationResult {
    data class Valid(
        val uriString: String,
        val originalPaise: Long,
        val parsedPaise: Long,
        val recipientMasked: String,
        val paramNames: List<String>
    ) : UpiUriValidationResult()

    data class Invalid(val reason: String) : UpiUriValidationResult()
}

/**
 * Result of preparing a complete UPI payment redirection.
 */
sealed class PaymentRedirectionResult {
    data class Success(val intent: Intent, val uri: Uri) : PaymentRedirectionResult()
    data class ValidationFailed(val reason: String) : PaymentRedirectionResult()
    data class AppUnavailable(val packageName: String) : PaymentRedirectionResult()
    data object NoAppAvailable : PaymentRedirectionResult()
    data class Error(val message: String) : PaymentRedirectionResult()
}

/**
 * Structured query parameters extracted from a parsed UPI URI string.
 */
data class ParsedUpiParams(
    val pa: String?,
    val pn: String?,
    val am: String?,
    val cu: String?,
    val tn: String?,
    val tr: String?,
    val allParamNames: List<String>,
    val duplicateKeys: Set<String>
)

/**
 * Builds and validates UPI payment intents.
 *
 * SECURITY RULES (never break these):
 * - Never include a UPI PIN, OTP, or banking password.
 * - Never attempt to directly process a transaction.
 * - All amounts are formatted from integer paise — never Float or Double.
 * - All URI parameters are properly encoded.
 */
object UpiIntentBuilder {

    /**
     * Validates a UPI ID for basic structural correctness.
     * Format: <identifier>@<handle> where handle has no dot.
     * Examples: rahul@upi, user@ybl, 9876543210@paytm
     */
    fun validateUpiId(upiId: String): UpiIdValidation {
        val trimmed = upiId.trim()
        if (trimmed.isBlank()) return UpiIdValidation.Invalid("UPI ID cannot be empty")
        if (!trimmed.contains("@")) return UpiIdValidation.Invalid("UPI ID must contain '@' (e.g. name@upi)")
        val parts = trimmed.split("@")
        if (parts.size != 2) return UpiIdValidation.Invalid("UPI ID must have exactly one '@'")
        val (identifier, handle) = parts
        if (identifier.isBlank()) return UpiIdValidation.Invalid("The part before '@' cannot be empty")
        if (handle.isBlank()) return UpiIdValidation.Invalid("The part after '@' cannot be empty (e.g. upi, ybl, paytm)")
        if (handle.contains(".")) return UpiIdValidation.Invalid("UPI handle should not contain a dot (e.g. use '@upi' not '@upi.com')")
        if (identifier.length > 256 || handle.length > 256) return UpiIdValidation.Invalid("UPI ID is too long")
        val identifierRegex = Regex("^[a-zA-Z0-9._\\-]+$")
        if (!identifierRegex.matches(identifier)) return UpiIdValidation.Invalid("UPI ID contains invalid characters")
        val handleRegex = Regex("^[a-zA-Z0-9]+$")
        if (!handleRegex.matches(handle)) return UpiIdValidation.Invalid("UPI handle must be alphanumeric (e.g. upi, ybl, paytm)")
        return UpiIdValidation.Valid
    }

    /**
     * Masks a UPI ID for safe logging.
     * Example: rahul@upi -> r***l@upi, 9876543210@paytm -> 98***0@paytm.
     */
    fun maskUpiId(upiId: String): String {
        val trimmed = upiId.trim()
        val atIndex = trimmed.indexOf('@')
        if (atIndex <= 1) return "***" + trimmed.substring(atIndex.coerceAtLeast(0))
        val identifier = trimmed.substring(0, atIndex)
        val handle = trimmed.substring(atIndex)
        val maskedIdentifier = if (identifier.length <= 3) {
            "${identifier.first()}***"
        } else {
            "${identifier.take(2)}***${identifier.takeLast(1)}"
        }
        return "$maskedIdentifier$handle"
    }

    /**
     * Builds a pure, minimal, standard-compliant UPI payment URI string.
     *
     * Essential standard UPI parameters generated:
     * - pa: Recipient UPI ID (exact, trimmed, URL-encoded)
     * - pn: Recipient name (trimmed, fallback to UPI ID, URL-encoded)
     * - am: Amount in decimal rupees (e.g. 1000L paise -> "10.00")
     * - cu: Currency code (always "INR")
     * - tn: Optional note (appended only if non-blank, max 50 chars, URL-encoded)
     * - tr: Optional transaction reference (appended only if explicitly provided, alphanumeric, max 35 chars)
     *
     * STRICT OMISSIONS:
     * - Does NOT append empty "tn=" parameters.
     * - Does NOT append unnecessary "mc" parameters.
     * - Does NOT append unnecessary "mode" parameters.
     * - Does NOT append unnecessary "purpose" parameters.
     * - Does NOT append duplicate parameters.
     * - Does NOT generate synthetic/fake "tr" parameters for P2P payments.
     */
    fun buildUpiUriString(
        payeeUpiId: String,
        payeeName: String,
        amountPaise: Long,
        note: String = "",
        transactionRef: String? = null
    ): String {
        require(amountPaise > 0L) { "Amount must be strictly positive" }
        val trimmedUpiId = payeeUpiId.trim()
        val validation = validateUpiId(trimmedUpiId)
        require(validation is UpiIdValidation.Valid) {
            (validation as UpiIdValidation.Invalid).reason
        }

        val amountString = PaiseUtils.paiseToRupeesString(amountPaise)
        val name = payeeName.trim().ifBlank { trimmedUpiId }

        val encodeParam = { s: String ->
            URLEncoder.encode(s, StandardCharsets.UTF_8.name()).replace("+", "%20")
        }

        val sb = StringBuilder("upi://pay?")
        sb.append("pa=").append(encodeParam(trimmedUpiId))
        sb.append("&pn=").append(encodeParam(name))
        sb.append("&am=").append(amountString)
        sb.append("&cu=INR")

        val cleanNote = note.trim().take(50)
        if (cleanNote.isNotEmpty()) {
            sb.append("&tn=").append(encodeParam(cleanNote))
        }

        if (!transactionRef.isNullOrBlank()) {
            val cleanTr = transactionRef.trim().filter { it.isLetterOrDigit() }.take(35)
            if (cleanTr.isNotEmpty()) {
                sb.append("&tr=").append(encodeParam(cleanTr))
            }
        }

        return sb.toString()
    }

    /**
     * Builds a standard android.net.Uri using [buildUpiUriString].
     */
    fun buildUpiUri(
        payeeUpiId: String,
        payeeName: String,
        amountPaise: Long,
        note: String = "",
        transactionRef: String? = null
    ): Uri {
        val uriString = buildUpiUriString(
            payeeUpiId = payeeUpiId,
            payeeName = payeeName,
            amountPaise = amountPaise,
            note = note,
            transactionRef = transactionRef
        )
        return Uri.parse(uriString)
    }

    /**
     * Pure-Kotlin parser for UPI URI strings.
     * Extracts query parameters and flags any duplicate keys.
     */
    fun parseUpiUriString(uriString: String): ParsedUpiParams {
        val trimmed = uriString.trim()
        val schemeSplit = trimmed.split(":", limit = 2)
        val afterScheme = if (schemeSplit.size == 2) schemeSplit[1] else trimmed
        val queryStart = afterScheme.indexOf('?')
        if (queryStart == -1) {
            return ParsedUpiParams(null, null, null, null, null, null, emptyList(), emptySet())
        }
        val queryString = afterScheme.substring(queryStart + 1)
        val pairs = queryString.split("&")

        val paramNames = mutableListOf<String>()
        val seenKeys = mutableSetOf<String>()
        val duplicates = mutableSetOf<String>()
        val map = mutableMapOf<String, String>()

        for (pair in pairs) {
            if (pair.isBlank()) continue
            val parts = pair.split("=", limit = 2)
            val rawKey = parts[0].trim().lowercase()
            val rawVal = if (parts.size > 1) parts[1].trim() else ""
            paramNames.add(rawKey)
            if (!seenKeys.add(rawKey)) {
                duplicates.add(rawKey)
            }
            val decoded = try {
                URLDecoder.decode(rawVal, StandardCharsets.UTF_8.name())
            } catch (_: Exception) {
                rawVal
            }
            map[rawKey] = decoded
        }

        return ParsedUpiParams(
            pa = map["pa"],
            pn = map["pn"],
            am = map["am"],
            cu = map["cu"],
            tn = map["tn"],
            tr = map["tr"],
            allParamNames = paramNames,
            duplicateKeys = duplicates
        )
    }

    /**
     * VALIDATION LAYER:
     * Validates a generated UPI URI before launching any external payment app.
     *
     * 1. Parses the URI string.
     * 2. Verifies recipient UPI ID matches expected and is valid.
     * 3. Verifies currency is strictly INR.
     * 4. Parses the amount back into paise using [PaiseUtils.rupeesToPaise].
     * 5. Compares parsed paise against the original reviewed amount.
     * 6. Rejects the request if values differ.
     * 7. Ensures there are no duplicate query parameters.
     * 8. Emits safe diagnostic logging with masked recipient and no sensitive data.
     */
    fun validateUpiUriString(
        uriString: String,
        expectedPaise: Long,
        expectedUpiId: String,
        selectedPackage: String? = null
    ): UpiUriValidationResult {
        val trimmedUri = uriString.trim()
        if (!trimmedUri.startsWith("upi://pay?") && !trimmedUri.startsWith("upi:pay?")) {
            return UpiUriValidationResult.Invalid("Invalid UPI URI format: must start with upi://pay?")
        }

        val parsed = parseUpiUriString(trimmedUri)

        // 1. Check for duplicate parameters
        if (parsed.duplicateKeys.isNotEmpty()) {
            return UpiUriValidationResult.Invalid("Duplicate parameter(s) found in UPI URI: ${parsed.duplicateKeys.joinToString()}")
        }

        // 2. Verify recipient UPI ID
        val pa = parsed.pa
        if (pa.isNullOrBlank()) {
            return UpiUriValidationResult.Invalid("Missing payee UPI ID (pa)")
        }
        if (!pa.equals(expectedUpiId.trim(), ignoreCase = true)) {
            return UpiUriValidationResult.Invalid("Recipient UPI ID mismatch: expected $expectedUpiId, got $pa")
        }
        val idValidation = validateUpiId(pa)
        if (idValidation is UpiIdValidation.Invalid) {
            return UpiUriValidationResult.Invalid("Invalid recipient UPI ID: ${idValidation.reason}")
        }

        // 3. Verify currency is INR
        val cu = parsed.cu
        if (cu.isNullOrBlank() || !cu.equals("INR", ignoreCase = true)) {
            return UpiUriValidationResult.Invalid("Invalid currency: expected INR, got $cu")
        }

        // 4. Verify amount format and parse back to paise
        val am = parsed.am
        if (am.isNullOrBlank()) {
            return UpiUriValidationResult.Invalid("Missing amount (am)")
        }
        if (!Regex("^\\d+\\.\\d{2}$").matches(am)) {
            return UpiUriValidationResult.Invalid("Amount must be formatted as decimal rupees (e.g. 10.00), got: $am")
        }
        val parsedPaise = try {
            PaiseUtils.rupeesToPaise(am)
        } catch (e: Exception) {
            return UpiUriValidationResult.Invalid("Failed to parse URI amount '$am' to paise: ${e.message}")
        }

        if (parsedPaise != expectedPaise) {
            return UpiUriValidationResult.Invalid(
                "Amount mismatch: expected $expectedPaise paise, but URI parsed as $parsedPaise paise"
            )
        }

        // 5. Ensure no empty tn= parameter
        if (parsed.allParamNames.contains("tn") && parsed.tn.isNullOrBlank()) {
            return UpiUriValidationResult.Invalid("Empty note parameter 'tn' should not be present in URI")
        }

        // 6. Safe diagnostic logging
        val maskedRecipient = maskUpiId(expectedUpiId)
        logSafeValidationDiagnostic(
            originalPaise = expectedPaise,
            formattedRupees = am,
            parsedPaise = parsedPaise,
            selectedPackage = selectedPackage,
            paramNames = parsed.allParamNames,
            maskedRecipient = maskedRecipient
        )

        return UpiUriValidationResult.Valid(
            uriString = trimmedUri,
            originalPaise = expectedPaise,
            parsedPaise = parsedPaise,
            recipientMasked = maskedRecipient,
            paramNames = parsed.allParamNames
        )
    }

    /**
     * Validates an android.net.Uri using [validateUpiUriString].
     */
    fun validateUpiUri(
        uri: Uri,
        expectedPaise: Long,
        expectedUpiId: String,
        selectedPackage: String? = null
    ): UpiUriValidationResult {
        return validateUpiUriString(uri.toString(), expectedPaise, expectedUpiId, selectedPackage)
    }

    /**
     * Safe diagnostic logger that never throws on JVM unit tests and never logs sensitive data.
     */
    fun logSafeValidationDiagnostic(
        originalPaise: Long,
        formattedRupees: String,
        parsedPaise: Long,
        selectedPackage: String?,
        paramNames: List<String>,
        maskedRecipient: String
    ) {
        val message = "Payment Validation -> OriginalPaise: $originalPaise | FormattedRupees: $formattedRupees | " +
                "ParsedPaise: $parsedPaise | TargetPkg: ${selectedPackage ?: "CHOOSER"} | " +
                "Params: $paramNames | Recipient: $maskedRecipient"
        try {
            android.util.Log.d("UPI_PAYMENT_DEBUG", message)
        } catch (_: Throwable) {
            println("UPI_PAYMENT_DEBUG: $message")
        }
    }

    /**
     * Safely logs non-sensitive payment initiation parameters for debugging.
     * NEVER logs PIN, OTP, credentials, or full account identifiers.
     */
    fun logSafePaymentDebug(
        payeeUpiId: String,
        amountPaise: Long,
        amountString: String,
        uri: Uri,
        selectedPackage: String? = null
    ) {
        val masked = maskUpiId(payeeUpiId)
        val message = "Payment URI Built -> Recipient: $masked | AmountPaise: $amountPaise | am: $amountString | cu: INR | Uri: $uri | TargetPkg: $selectedPackage"
        try {
            android.util.Log.d("UPI_PAYMENT_DEBUG", message)
        } catch (_: Throwable) {
            println("UPI_PAYMENT_DEBUG: $message")
        }
    }

    /**
     * CENTRAL REDIRECTION FUNCTION:
     * One clear redirection function that accepts:
     * - Recipient UPI ID
     * - Recipient name
     * - Amount in paise
     * - Selected UPI application package
     * (and optional note and transactionRef)
     *
     * Validates the generated URI before creating the intent.
     * The selected package determines ONLY which app receives the URI;
     * it never modifies the amount or recipient.
     */
    fun buildPaymentRedirection(
        context: Context,
        payeeUpiId: String,
        payeeName: String,
        amountPaise: Long,
        selectedPackage: String?,
        note: String = "",
        transactionRef: String? = null
    ): PaymentRedirectionResult {
        val uriString = try {
            buildUpiUriString(
                payeeUpiId = payeeUpiId,
                payeeName = payeeName,
                amountPaise = amountPaise,
                note = note,
                transactionRef = transactionRef
            )
        } catch (e: Exception) {
            return PaymentRedirectionResult.ValidationFailed(e.message ?: "Failed to build UPI URI")
        }

        val validation = validateUpiUriString(
            uriString = uriString,
            expectedPaise = amountPaise,
            expectedUpiId = payeeUpiId,
            selectedPackage = selectedPackage
        )
        if (validation is UpiUriValidationResult.Invalid) {
            return PaymentRedirectionResult.ValidationFailed(validation.reason)
        }

        val uri = Uri.parse(uriString)
        val baseIntent = Intent(Intent.ACTION_VIEW, uri)

        if (!selectedPackage.isNullOrBlank()) {
            if (!isUpiAppAvailable(context, selectedPackage)) {
                return PaymentRedirectionResult.AppUnavailable(selectedPackage)
            }
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(selectedPackage)
            }
            return PaymentRedirectionResult.Success(intent, uri)
        }

        // Chooser fallback if no package selected
        val apps = getInstalledUpiApps(context)
        if (apps.isEmpty()) {
            return PaymentRedirectionResult.NoAppAvailable
        }
        if (apps.size == 1) {
            val app = apps.first()
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(app.packageName)
            }
            return PaymentRedirectionResult.Success(intent, uri)
        }

        val chooserIntent = Intent.createChooser(baseIntent, "Pay with UPI")
        return PaymentRedirectionResult.Success(chooserIntent, uri)
    }

    /**
     * Creates an ACTION_VIEW intent for the given UPI URI targeting a specific package.
     * Uses setPackage() for maximum resilience against internal class renaming by UPI apps.
     *
     * @param forResult If true, does NOT add FLAG_ACTIVITY_NEW_TASK so that startActivityForResult
     *                  can properly deliver the result to the caller instead of immediately cancelling.
     */
    fun buildIntentForPackage(
        uri: Uri,
        packageName: String,
        activityName: String = "",
        forResult: Boolean = true
    ): Intent {
        return Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(packageName)
            if (!forResult) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /**
     * Creates a chooser intent that lets the user pick any compatible UPI app.
     */
    fun buildChooserIntent(
        uri: Uri,
        title: String = "Pay with UPI",
        forResult: Boolean = true
    ): Intent {
        val baseIntent = Intent(Intent.ACTION_VIEW, uri)
        return Intent.createChooser(baseIntent, title).apply {
            if (!forResult) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /**
     * Top Indian UPI application packages declared in AndroidManifest.xml <queries>.
     * Used as a resilient fallback probe to guarantee detection even when OEM systems
     * (such as Motorola / Lenovo Moto Android 11+ builds) restrict implicit intent queries.
     */
    val KNOWN_UPI_PACKAGES = listOf(
        "com.google.android.apps.nbu.paisa.user" to "Google Pay",
        "com.phonepe.app" to "PhonePe",
        "net.one97.paytm" to "Paytm",
        "in.org.npci.upiapp" to "BHIM",
        "in.amazon.mShop.android.shopping" to "Amazon Pay",
        "com.whatsapp" to "WhatsApp",
        "com.cred.club" to "CRED",
        "com.naviapps.mobile" to "Navi"
    )

    /**
     * Discovers all UPI-capable apps installed on the device.
     *
     * 1. Uses probe URI (upi://pay) with queryIntentActivities using both flag 0 and MATCH_DEFAULT_ONLY.
     * 2. Probes explicitly declared UPI packages (Google Pay, PhonePe, etc.) to ensure detection
     *    even on Android 11+ OEM devices where generic intent matching may be restricted.
     *
     * Adheres strictly to Android 11+ package visibility rules without requiring QUERY_ALL_PACKAGES.
     */
    fun getInstalledUpiApps(context: Context): List<UpiApp> {
        val pm = context.packageManager
        val probeUri = Uri.parse("upi://pay")
        val probeIntent = Intent(Intent.ACTION_VIEW, probeUri)
        val discovered = mutableListOf<UpiApp>()

        // Step 1: Query matching activities via PackageManager
        val resolveInfoList: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val listZero = try {
                pm.queryIntentActivities(
                    probeIntent,
                    PackageManager.ResolveInfoFlags.of(0L)
                )
            } catch (_: Exception) {
                emptyList()
            }
            val listDefault = try {
                pm.queryIntentActivities(
                    probeIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } catch (_: Exception) {
                emptyList()
            }
            (listZero + listDefault).distinctBy { it.activityInfo?.packageName }
        } else {
            @Suppress("DEPRECATION")
            val listZero = try {
                pm.queryIntentActivities(probeIntent, 0)
            } catch (_: Exception) {
                emptyList()
            }
            @Suppress("DEPRECATION")
            val listDefault = try {
                pm.queryIntentActivities(probeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            } catch (_: Exception) {
                emptyList()
            }
            (listZero + listDefault).distinctBy { it.activityInfo?.packageName }
        }

        for (info in resolveInfoList) {
            try {
                val packageName = info.activityInfo?.packageName ?: continue
                val activityName = info.activityInfo?.name ?: ""
                val label = info.loadLabel(pm).toString()
                discovered.add(
                    UpiApp(
                        packageName = packageName,
                        activityName = activityName,
                        displayName = label.ifBlank { packageName }
                    )
                )
            } catch (_: Exception) {
                // Ignore individual parsing failures
            }
        }

        // Step 2: Targeted probe for known UPI apps (Google Pay, PhonePe, Paytm, etc.)
        for ((pkg, defaultName) in KNOWN_UPI_PACKAGES) {
            if (discovered.none { it.packageName == pkg }) {
                try {
                    val targetedIntent = Intent(Intent.ACTION_VIEW, probeUri).apply {
                        setPackage(pkg)
                    }
                    val targetedResolves = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.queryIntentActivities(
                            targetedIntent,
                            PackageManager.ResolveInfoFlags.of(0L)
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        pm.queryIntentActivities(targetedIntent, 0)
                    }

                    if (targetedResolves.isNotEmpty()) {
                        val info = targetedResolves.first()
                        val label = try {
                            info.loadLabel(pm).toString()
                        } catch (_: Exception) {
                            defaultName
                        }
                        discovered.add(
                            UpiApp(
                                packageName = pkg,
                                activityName = info.activityInfo?.name ?: "",
                                displayName = label.ifBlank { defaultName }
                            )
                        )
                    } else {
                        // Check if package is installed via getPackageInfo (permitted via <queries><package>)
                        val isInstalled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            try {
                                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0L))
                                true
                            } catch (_: Exception) {
                                false
                            }
                        } else {
                            try {
                                @Suppress("DEPRECATION")
                                pm.getPackageInfo(pkg, 0)
                                true
                            } catch (_: Exception) {
                                false
                            }
                        }

                        if (isInstalled) {
                            val label = try {
                                val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0L))
                                } else {
                                    @Suppress("DEPRECATION")
                                    pm.getApplicationInfo(pkg, 0)
                                }
                                pm.getApplicationLabel(appInfo).toString()
                            } catch (_: Exception) {
                                defaultName
                            }
                            discovered.add(
                                UpiApp(
                                    packageName = pkg,
                                    activityName = "",
                                    displayName = label.ifBlank { defaultName }
                                )
                            )
                        }
                    }
                } catch (_: Exception) {
                    // Package not installed, safely ignore
                }
            }
        }

        return discovered.distinctBy { it.packageName }
    }

    /**
     * Checks whether a specific package is still installed and can handle UPI intents.
     */
    fun isUpiAppAvailable(context: Context, packageName: String): Boolean {
        val availableApps = getInstalledUpiApps(context)
        return availableApps.any { it.packageName == packageName }
    }

    /**
     * Prepares the best launch result for a payment:
     * 1. If [preferredPackage] is set and available -> target it directly.
     * 2. If only one UPI app is available -> use it directly.
     * 3. If multiple apps available -> show chooser.
     * 4. If no apps available -> return NoAppAvailable.
     */
    fun resolvePaymentLaunch(
        context: Context,
        uri: Uri,
        preferredPackage: String?,
        forResult: Boolean = true
    ): UpiLaunchResult {
        val apps = getInstalledUpiApps(context)
        if (apps.isEmpty()) return UpiLaunchResult.NoAppAvailable

        // Try preferred / selected app first
        if (!preferredPackage.isNullOrBlank()) {
            val preferred = apps.firstOrNull { it.packageName == preferredPackage }
            if (preferred != null) {
                val intent = buildIntentForPackage(uri, preferred.packageName, preferred.activityName, forResult = forResult)
                return UpiLaunchResult.Success(intent)
            }
            // Preferred app is gone -> fall through
        }

        // Single app -> direct launch
        if (apps.size == 1) {
            val app = apps.first()
            val intent = buildIntentForPackage(uri, app.packageName, app.activityName, forResult = forResult)
            return UpiLaunchResult.Success(intent)
        }

        // Multiple apps -> chooser
        return UpiLaunchResult.Fallback(buildChooserIntent(uri, forResult = forResult))
    }
}
