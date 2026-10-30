package com.upisegregator.app.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.upisegregator.app.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_session_prefs")

data class UserSession(
    val isLoggedIn: Boolean = false,
    val userId: String? = null,
    val email: String? = null,
    val fullName: String? = null,
    val isDemoMode: Boolean = false
) {
    val isGuestOrLoggedOut: Boolean get() = !isLoggedIn && !isDemoMode
}

interface SessionManager {
    val sessionFlow: Flow<UserSession>
    suspend fun startUserSession(user: UserEntity)
    suspend fun startDemoSession()
    suspend fun clearSession()
    suspend fun setActivePaymentTransactionId(transactionId: String)
    suspend fun getActivePaymentTransactionId(): String?
    suspend fun clearActivePaymentTransactionId()
}

class SessionManagerImpl(
    private val context: Context
) : SessionManager {

    companion object {
        val KEY_IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val KEY_USER_ID = stringPreferencesKey("user_id")
        val KEY_EMAIL = stringPreferencesKey("user_email")
        val KEY_FULL_NAME = stringPreferencesKey("user_full_name")
        val KEY_IS_DEMO = booleanPreferencesKey("is_demo_mode")
        val KEY_ACTIVE_PAYMENT_TXN_ID = stringPreferencesKey("active_payment_txn_id")
    }

    override val sessionFlow: Flow<UserSession> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val isLoggedIn = prefs[KEY_IS_LOGGED_IN] ?: false
            val isDemo = prefs[KEY_IS_DEMO] ?: false
            val userId = prefs[KEY_USER_ID]
            val email = prefs[KEY_EMAIL]
            val fullName = prefs[KEY_FULL_NAME]

            UserSession(
                isLoggedIn = isLoggedIn,
                userId = userId,
                email = email,
                fullName = fullName,
                isDemoMode = isDemo
            )
        }

    override suspend fun startUserSession(user: UserEntity) {
        context.dataStore.edit { prefs ->
            prefs[KEY_IS_LOGGED_IN] = true
            prefs[KEY_IS_DEMO] = false
            prefs[KEY_USER_ID] = user.userId
            prefs[KEY_EMAIL] = user.email
            prefs[KEY_FULL_NAME] = user.fullName
        }
    }

    override suspend fun startDemoSession() {
        context.dataStore.edit { prefs ->
            prefs[KEY_IS_LOGGED_IN] = false
            prefs[KEY_IS_DEMO] = true
            prefs[KEY_USER_ID] = "demo_user"
            prefs[KEY_EMAIL] = "demo@upisegregator.local"
            prefs[KEY_FULL_NAME] = "Demo User"
        }
    }

    override suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }

    override suspend fun setActivePaymentTransactionId(transactionId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACTIVE_PAYMENT_TXN_ID] = transactionId
        }
    }

    override suspend fun getActivePaymentTransactionId(): String? {
        return context.dataStore.data
            .catch { emit(emptyPreferences()) }
            .first()[KEY_ACTIVE_PAYMENT_TXN_ID]
    }

    override suspend fun clearActivePaymentTransactionId() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_ACTIVE_PAYMENT_TXN_ID)
        }
    }
}
