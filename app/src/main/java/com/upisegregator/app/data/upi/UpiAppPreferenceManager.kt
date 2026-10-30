package com.upisegregator.app.data.upi

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.upiPrefsDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "upi_app_prefs")

/**
 * Persists the user's preferred UPI application across app restarts.
 *
 * The preferred app is stored as its package name string.
 * On every use, it is validated against the currently installed UPI apps
 * before being applied — so a stale preference is gracefully ignored.
 */
class UpiAppPreferenceManager(private val context: Context) {

    companion object {
        private val KEY_PREFERRED_UPI_PACKAGE = stringPreferencesKey("preferred_upi_package")
    }

    /**
     * A [Flow] that emits the saved preferred UPI app package name,
     * or null if none has been saved yet.
     */
    val preferredUpiPackageFlow: Flow<String?> = context.upiPrefsDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs -> prefs[KEY_PREFERRED_UPI_PACKAGE] }

    /**
     * Saves the package name of the user's chosen preferred UPI application.
     */
    suspend fun savePreferredUpiApp(packageName: String) {
        context.upiPrefsDataStore.edit { prefs ->
            prefs[KEY_PREFERRED_UPI_PACKAGE] = packageName
        }
    }

    /**
     * Clears the stored preference (e.g., when the user explicitly unsets it).
     */
    suspend fun clearPreferredUpiApp() {
        context.upiPrefsDataStore.edit { prefs ->
            prefs.remove(KEY_PREFERRED_UPI_PACKAGE)
        }
    }

    /**
     * Returns the preferred UPI package only if it is still installed and UPI-capable.
     * Returns null if the app is not installed or no preference is saved.
     */
    suspend fun getValidatedPreferredPackage(): String? {
        val saved = context.upiPrefsDataStore.data
            .catch { emit(emptyPreferences()) }
            .first()[KEY_PREFERRED_UPI_PACKAGE]
        // Validate that the saved package is still installed and UPI-capable
        return if (saved != null && UpiIntentBuilder.isUpiAppAvailable(context, saved)) {
            saved
        } else {
            null
        }
    }
}
