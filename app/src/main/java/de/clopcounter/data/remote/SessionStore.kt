package de.clopcounter.data.remote

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Keeps the login tokens and the signed-in account in private app storage. */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    private val _account = MutableStateFlow(readAccount())
    val account: StateFlow<Account?> = _account

    val accessToken: String? get() = prefs.getString("access_token", null)
    val refreshToken: String? get() = prefs.getString("refresh_token", null)
    val expiresAt: Long get() = prefs.getLong("expires_at", 0)

    /** The last account that synced on this device; survives sign-out. */
    val lastUserId: String? get() = prefs.getString("last_user_id", null)

    fun saveTokens(accessToken: String, refreshToken: String, expiresInSeconds: Long) {
        prefs.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .putLong("expires_at", System.currentTimeMillis() + expiresInSeconds * 1000)
            .apply()
    }

    fun saveAccount(account: Account) {
        prefs.edit()
            .putString("user_id", account.userId)
            .putString("username", account.username)
            .putLong("user_number", account.userNumber)
            .putString("last_user_id", account.userId)
            .apply()
        _account.value = account
    }

    fun forgetLastUser() {
        prefs.edit().remove("last_user_id").apply()
    }

    fun clear() {
        prefs.edit()
            .remove("access_token").remove("refresh_token").remove("expires_at")
            .remove("user_id").remove("username").remove("user_number")
            .apply()
        _account.value = null
    }

    private fun readAccount(): Account? {
        val id = prefs.getString("user_id", null) ?: return null
        val name = prefs.getString("username", null) ?: return null
        if (prefs.getString("refresh_token", null) == null) return null
        return Account(id, name, prefs.getLong("user_number", 0))
    }
}
