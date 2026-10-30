package com.upisegregator.app.data.upi

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import com.upisegregator.app.util.PaiseUtils

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
 * Builds and validates UPI payment intents.
 *
 * SECURITY RULES (never break these):
 * - Never include a UPI PIN, OTP, or banking password.
 * - Never attempt to directly process a transaction.
 * - All amounts are formatted from integer paise — never Float or Double.
 * - All URI parameters are properly encoded via Uri.Builder.
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
        // Basic character whitelist: alphanumeric, dot, hyphen, underscore in identifier
        val identifierRegex = Regex("^[a-zA-Z0-9._\\-]+$")
        if (!identifierRegex.matches(identifier)) return UpiIdValidation.Invalid("UPI ID contains invalid characters")
        val handleRegex = Regex("^[a-zA-Z0-9]+$")
        if (!handleRegex.matches(handle)) return UpiIdValidation.Invalid("UPI handle must be alphanumeric (e.g. upi, ybl, paytm)")
        return UpiIdValidation.Valid
    }

    /**
     * Builds a properly-encoded UPI payment URI from validated inputs.
     *
     * URI format: upi://pay?pa=UPI_ID&pn=NAME&am=AMOUNT&cu=INR&tn=NOTE
     *
     * @param payeeUpiId  The recipient's UPI Virtual Payment Address (VPA).
     * @param payeeName   The recipient's display name.
     * @param amountPaise Payment amount in integer paise (Long). Will be divided by 100 for INR format.
     * @param note        An optional payment description / transaction note.
     */
    fun buildUpiUri(
        payeeUpiId: String,
        payeeName: String,
        amountPaise: Long,
        note: String = ""
    ): Uri {
        require(amountPaise > 0L) { "Amount must be positive" }
        val amountString = PaiseUtils.paiseToRupeesString(amountPaise)

        return Uri.Builder()
            .scheme("upi")
            .authority("pay")
            .appendQueryParameter("pa", payeeUpiId.trim())
            .appendQueryParameter("pn", payeeName.trim())
            .appendQueryParameter("am", amountString)
            .appendQueryParameter("cu", "INR")
            .appendQueryParameter("tn", note.trim().take(50)) // Most UPI apps cap note at 50 chars
            .build()
    }    /**
     * Creates an ACTION_VIEW intent for the given UPI URI targeting a specific package.
     * Uses setPackage() for maximum resilience against internal class renaming by UPI apps.
     */
    fun buildIntentForPackage(uri: Uri, packageName: String, activityName: String = ""): Intent {
        return Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Creates a chooser intent that lets the user pick any compatible UPI app.
     */
    fun buildChooserIntent(uri: Uri, title: String = "Pay with UPI"): Intent {
        val baseIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return Intent.createChooser(baseIntent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
        preferredPackage: String?
    ): UpiLaunchResult {
        val apps = getInstalledUpiApps(context)
        if (apps.isEmpty()) return UpiLaunchResult.NoAppAvailable

        // Try preferred / selected app first
        if (!preferredPackage.isNullOrBlank()) {
            val preferred = apps.firstOrNull { it.packageName == preferredPackage }
            if (preferred != null) {
                val intent = buildIntentForPackage(uri, preferred.packageName, preferred.activityName)
                return UpiLaunchResult.Success(intent)
            }
            // Preferred app is gone -> fall through
        }

        // Single app -> direct launch
        if (apps.size == 1) {
            val app = apps.first()
            val intent = buildIntentForPackage(uri, app.packageName, app.activityName)
            return UpiLaunchResult.Success(intent)
        }

        // Multiple apps -> chooser
        return UpiLaunchResult.Fallback(buildChooserIntent(uri))
    }
}
