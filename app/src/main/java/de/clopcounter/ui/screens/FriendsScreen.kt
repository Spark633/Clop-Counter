@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package de.clopcounter.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.clopcounter.data.SyncState
import de.clopcounter.data.remote.Account
import de.clopcounter.data.remote.Friend
import de.clopcounter.data.remote.FriendStatus
import de.clopcounter.ui.SectionTitle
import de.clopcounter.ui.SocialViewModel
import de.clopcounter.ui.TimeFormat
import java.time.Instant
import java.time.ZoneId

@Composable
fun FriendsScreen(
    social: SocialViewModel,
    onSignIn: (register: Boolean) -> Unit,
    onOpenFriend: (Friend) -> Unit,
) {
    val account by social.account.collectAsStateWithLifecycle()
    val friends by social.friends.collectAsStateWithLifecycle()
    val busy by social.busy.collectAsStateWithLifecycle()
    val syncState by social.syncState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { social.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(account?.userId) { if (account != null) social.refreshFriends() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Freunde") },
                actions = {
                    if (account != null) {
                        IconButton(onClick = { social.refreshFriends(); social.syncNow() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Aktualisieren")
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Mehr")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(text = { Text("Abmelden") }, onClick = { menuOpen = false; confirmSignOut = true })
                                DropdownMenuItem(text = { Text("Konto löschen") }, onClick = { menuOpen = false; confirmDelete = true })
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            val current = account
            when {
                !social.isConfigured -> NotConfigured()
                current == null -> SignedOut(onSignIn)
                else -> SignedIn(current, friends, syncState, social, onOpenFriend)
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Abmelden?") },
            text = { Text("Deine Einträge bleiben auf diesem Handy. Wenn du dich wieder anmeldest, werden sie erneut abgeglichen.") },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; social.signOut() }) { Text("Abmelden") } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Abbrechen") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Konto endgültig löschen?") },
            text = {
                Text(
                    "Dein Konto, deine User-Nr., Freundschaften und alle Daten auf dem Server werden gelöscht. " +
                        "Die Einträge auf diesem Handy bleiben erhalten.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; social.deleteAccount() }) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun NotConfigured() {
    Text(
        "Die Online-Funktionen sind in dieser Version noch nicht eingerichtet (Supabase-Zugangsdaten fehlen).",
        modifier = Modifier.padding(16.dp),
    )
}

@Composable
private fun SignedOut(onSignIn: (register: Boolean) -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Mit einem Konto kannst du", style = MaterialTheme.typography.titleMedium)
        Text("• Freunde per Nutzername oder User-Nr. hinzufügen\n• ihre Statistiken und geteilten Links sehen\n• deine Einträge sichern und auf ein neues Handy übertragen")
        Text(
            "Nur Nutzername und Passwort, keine E-Mail. Bilder bleiben auf deinem Handy; geteilt werden Zähler, Tags und Links. " +
                "Notizen sind privat.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = { onSignIn(true) }, modifier = Modifier.fillMaxWidth()) { Text("Konto erstellen") }
        OutlinedButton(onClick = { onSignIn(false) }, modifier = Modifier.fillMaxWidth()) { Text("Anmelden") }
    }
}

@Composable
private fun SignedIn(
    account: Account,
    friends: List<Friend>,
    syncState: SyncState,
    social: SocialViewModel,
    onOpenFriend: (Friend) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var target by remember { mutableStateOf("") }
    val incoming = friends.filter { it.status == FriendStatus.INCOMING }
    val accepted = friends.filter { it.status == FriendStatus.FRIEND }
    val outgoing = friends.filter { it.status == FriendStatus.OUTGOING }
    val send = {
        if (target.isNotBlank()) social.sendFriendRequest(target) { target = "" }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(account.username, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("User-Nr. #${account.userNumber}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            syncText(syncState),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (syncState is SyncState.Failed) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { clipboard.setText(AnnotatedString("#${account.userNumber}")) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "User-Nr. kopieren")
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = target,
                onValueChange = { target = it },
                label = { Text("Freund hinzufügen (Nutzername oder #Nr.)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                trailingIcon = {
                    IconButton(onClick = send, enabled = target.isNotBlank()) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Anfrage senden")
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }

        if (incoming.isNotEmpty()) {
            item { SectionTitle("Anfragen", Modifier.padding(top = 12.dp)) }
            items(incoming, key = { "in-" + it.userId }) { f ->
                FriendRow(f) {
                    IconButton(onClick = { social.respond(f, accept = true) }) {
                        Icon(Icons.Filled.Check, contentDescription = "Annehmen", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { social.respond(f, accept = false) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Ablehnen")
                    }
                }
            }
        }

        item { SectionTitle("Freunde (${accepted.size})", Modifier.padding(top = 12.dp)) }
        if (accepted.isEmpty()) {
            item {
                Text(
                    "Noch keine Freunde. Gib oben einen Nutzernamen oder eine User-Nr. ein, oder teile deine Nummer #${account.userNumber}.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(accepted, key = { "f-" + it.userId }) { f -> FriendRow(f, onClick = { onOpenFriend(f) }) }

        if (outgoing.isNotEmpty()) {
            item { SectionTitle("Gesendete Anfragen", Modifier.padding(top = 12.dp)) }
            items(outgoing, key = { "out-" + it.userId }) { f ->
                FriendRow(f) {
                    TextButton(onClick = { social.removeFriend(f) }) { Text("Zurückziehen") }
                }
            }
        }
    }
}

@Composable
private fun FriendRow(friend: Friend, onClick: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    val content = @Composable {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(friend.username.take(1).uppercase(), style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(friend.username, style = MaterialTheme.typography.titleMedium)
                Text("#${friend.userNumber}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            actions()
        }
    }
    if (onClick != null) Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) { content() }
    else Card(Modifier.fillMaxWidth()) { content() }
}

private fun syncText(state: SyncState): String = when (state) {
    SyncState.Idle -> "Synchronisierung bereit"
    SyncState.Running -> "Synchronisiere …"
    is SyncState.Done -> "Gesichert um " + Instant.ofEpochMilli(state.at).atZone(ZoneId.systemDefault()).format(TimeFormat) + " Uhr"
    is SyncState.Failed -> "Nicht synchronisiert: ${state.message}"
}
