package de.clopcounter.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.clopcounter.ClopApp
import de.clopcounter.data.NumberedEntry
import de.clopcounter.data.SyncState
import de.clopcounter.data.remote.Account
import de.clopcounter.data.remote.Friend
import de.clopcounter.data.remote.NotSignedInException
import de.clopcounter.data.toNumberedEntries
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.security.SecureRandom

class SocialViewModel(app: Application) : AndroidViewModel(app) {
    private val clop = app as ClopApp
    private val api = clop.api
    private val session = clop.session
    private val sync = clop.sync

    val isConfigured: Boolean get() = api.isConfigured
    val account: StateFlow<Account?> = session.account
    val syncState: StateFlow<SyncState> = sync.state

    private val _friends = MutableStateFlow<List<Friend>>(emptyList())
    val friends: StateFlow<List<Friend>> = _friends

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    // ---------------------------------------------------------------- account

    /** Registers, signs in and returns the recovery code that the user must write down. */
    fun register(username: String, password: String, onSuccess: (recoveryCode: String) -> Unit) = launchBusy {
        val code = newRecoveryCode()
        api.register(username.trim(), password, code)
        signInInternal(username.trim(), password)
        onSuccess(code)
    }

    fun signIn(username: String, password: String, onSuccess: () -> Unit) = launchBusy {
        signInInternal(username.trim(), password)
        onSuccess()
    }

    private suspend fun signInInternal(username: String, password: String) {
        val previous = session.lastUserId
        val account = api.signIn(username, password)
        sync.onSignedIn(account.userId, previous)
        session.saveAccount(account)
        sync.sync(pull = true)
        refreshFriendsInternal()
    }

    fun resetPassword(username: String, recoveryCode: String, newPassword: String, onSuccess: () -> Unit) = launchBusy {
        if (api.resetPassword(username.trim(), recoveryCode, newPassword)) {
            _messages.emit("Passwort geändert, du kannst dich jetzt anmelden")
            onSuccess()
        } else {
            _messages.emit("Nutzername oder Wiederherstellungscode falsch")
        }
    }

    fun signOut() = launchBusy {
        sync.sync(pull = false)
        api.signOut()
        session.clear()
        _friends.value = emptyList()
    }

    fun deleteAccount() = launchBusy {
        api.deleteAccount()
        sync.onAccountDeleted()
        session.forgetLastUser()
        session.clear()
        _friends.value = emptyList()
        _messages.emit("Konto gelöscht. Deine Einträge bleiben auf diesem Handy erhalten.")
    }

    fun syncNow() {
        viewModelScope.launch { sync.sync(pull = true) }
    }

    // ---------------------------------------------------------------- friends

    fun refreshFriends() = launchBusy { refreshFriendsInternal() }

    private suspend fun refreshFriendsInternal() {
        if (session.account.value != null) _friends.value = api.friends()
    }

    fun sendFriendRequest(target: String, onSent: () -> Unit) = launchBusy {
        val message = when (api.sendFriendRequest(target.trim())) {
            "sent" -> "Anfrage gesendet"
            "accepted" -> "Ihr seid jetzt befreundet"
            "already_sent" -> "Du hast schon eine Anfrage gesendet"
            "self" -> "Das bist du selbst 🙂"
            "not_found" -> "Niemand mit diesem Namen oder dieser Nummer gefunden"
            else -> "Anfrage gesendet"
        }
        _messages.emit(message)
        refreshFriendsInternal()
        onSent()
    }

    fun respond(friend: Friend, accept: Boolean) = launchBusy {
        api.respondFriendRequest(friend.userId, accept)
        refreshFriendsInternal()
    }

    fun removeFriend(friend: Friend, onDone: () -> Unit = {}) = launchBusy {
        api.removeFriend(friend.userId)
        refreshFriendsInternal()
        onDone()
    }

    /** A friend's entries, numbered like local ones. Throws on network errors. */
    suspend fun loadFriendEntries(userId: String): List<NumberedEntry> =
        api.fetchEntries(userId).toNumberedEntries()

    // ---------------------------------------------------------------- helpers

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try {
                block()
            } catch (e: Exception) {
                _messages.emit(e.toUserMessage())
            } finally {
                _busy.value = false
            }
        }
    }

    private fun newRecoveryCode(): String {
        // No 0/O/1/I to avoid confusion when copying by hand.
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        return (1..4).joinToString("-") { (1..4).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("") }
    }
}

fun Throwable.toUserMessage(): String = when (this) {
    is NotSignedInException -> "Bitte melde dich erneut an"
    is IOException -> "Keine Verbindung zum Server"
    else -> message ?: "Unbekannter Fehler"
}
