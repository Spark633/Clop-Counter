@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package de.clopcounter.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.clopcounter.ui.SocialViewModel

private enum class AuthMode(val label: String) { SignIn("Anmelden"), Register("Registrieren"), Reset("Passwort vergessen") }

@Composable
fun AuthScreen(social: SocialViewModel, startWithRegister: Boolean, onDone: () -> Unit, onBack: () -> Unit) {
    val busy by social.busy.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableIntStateOf(if (startWithRegister) AuthMode.Register.ordinal else AuthMode.SignIn.ordinal) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }
    var recoveryInput by remember { mutableStateOf("") }
    var recoveryCode by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { social.messages.collect { snackbar.showSnackbar(it) } }

    val current = AuthMode.entries[mode]
    val usernameValid = Regex("^[A-Za-z0-9_.-]{3,20}$").matches(username.trim())
    val canSubmit = !busy && when (current) {
        AuthMode.SignIn -> username.isNotBlank() && password.isNotEmpty()
        AuthMode.Register -> usernameValid && password.length >= 6 && password == password2
        AuthMode.Reset -> username.isNotBlank() && recoveryInput.isNotBlank() && password.length >= 6
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Konto") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (current != AuthMode.Reset) {
                TabRow(selectedTabIndex = mode) {
                    listOf(AuthMode.SignIn, AuthMode.Register).forEach { m ->
                        Tab(selected = mode == m.ordinal, onClick = { mode = m.ordinal }, text = { Text(m.label) })
                    }
                }
            }
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (current) {
                    AuthMode.SignIn -> Text("Melde dich mit deinem Nutzernamen und Passwort an. Eine E-Mail-Adresse wird nicht benötigt.")
                    AuthMode.Register -> Text(
                        "Wähle einen Nutzernamen (3–20 Zeichen: Buchstaben, Zahlen, _ . -). " +
                            "Du bekommst eine User-Nr., über die dich Freunde finden. Bilder bleiben nur auf deinem Handy.",
                    )
                    AuthMode.Reset -> Text("Gib deinen Wiederherstellungscode ein, den du bei der Registrierung bekommen hast.")
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Nutzername") },
                    singleLine = true,
                    isError = current == AuthMode.Register && username.isNotEmpty() && !usernameValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (current == AuthMode.Reset) {
                    OutlinedTextField(
                        value = recoveryInput,
                        onValueChange = { recoveryInput = it },
                        label = { Text("Wiederherstellungscode") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = if (current == AuthMode.Reset) "Neues Passwort" else "Passwort",
                )
                if (current == AuthMode.Register) {
                    PasswordField(
                        value = password2,
                        onValueChange = { password2 = it },
                        label = "Passwort wiederholen",
                        isError = password2.isNotEmpty() && password != password2,
                    )
                }
                if (current != AuthMode.SignIn && password.isNotEmpty() && password.length < 6) {
                    Text("Mindestens 6 Zeichen", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Button(
                    enabled = canSubmit,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        when (current) {
                            AuthMode.SignIn -> social.signIn(username, password, onDone)
                            AuthMode.Register -> social.register(username, password) { recoveryCode = it }
                            AuthMode.Reset -> social.resetPassword(username, recoveryInput, password) {
                                password = ""
                                mode = AuthMode.SignIn.ordinal
                            }
                        }
                    },
                ) { Text(current.label) }

                when (current) {
                    AuthMode.SignIn -> TextButton(onClick = { mode = AuthMode.Reset.ordinal }) { Text("Passwort vergessen?") }
                    AuthMode.Reset -> TextButton(onClick = { mode = AuthMode.SignIn.ordinal }) { Text("Zurück zur Anmeldung") }
                    AuthMode.Register -> Unit
                }
            }
        }
    }

    recoveryCode?.let { code ->
        RecoveryCodeDialog(code) {
            recoveryCode = null
            onDone()
        }
    }
}

@Composable
private fun PasswordField(value: String, onValueChange: (String) -> Unit, label: String, isError: Boolean = false) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "Passwort verbergen" else "Passwort anzeigen",
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RecoveryCodeDialog(code: String, onConfirm: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var saved by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Dein Wiederherstellungscode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Da es keine E-Mail gibt, ist das die einzige Möglichkeit, ein vergessenes Passwort zurückzusetzen. " +
                        "Schreib ihn auf oder mach einen Screenshot. Er wird nur dieses eine Mal angezeigt.",
                )
                SelectionContainer {
                    Text(
                        code,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(code)) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Text("  Kopieren")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = saved, onCheckedChange = { saved = it })
                    Text("Ich habe den Code gespeichert")
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = saved) { Text("Weiter") } },
    )
}
