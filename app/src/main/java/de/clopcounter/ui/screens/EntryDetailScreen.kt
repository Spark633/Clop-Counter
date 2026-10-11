@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package de.clopcounter.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import de.clopcounter.data.Attachment
import de.clopcounter.data.AttachmentType
import de.clopcounter.data.NumberedEntry
import de.clopcounter.data.Tag
import de.clopcounter.ui.ClopViewModel
import de.clopcounter.ui.LongDateFormat
import de.clopcounter.ui.NumberBadge
import de.clopcounter.ui.SectionTitle
import de.clopcounter.ui.TimeFormat
import kotlinx.coroutines.delay
import java.io.File
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(vm: ClopViewModel, entryId: Long, onBack: () -> Unit) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val allTags by vm.tags.collectAsStateWithLifecycle()
    val entry = entries.find { it.id == entryId }
    val context = LocalContext.current

    var confirmDelete by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var attachmentToRemove by remember { mutableStateOf<Attachment?>(null) }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) vm.addFiles(entryId, uris)
    }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.addFiles(entryId, uris)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(entry?.let { "Clop #${it.number}" } ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }, enabled = entry != null) {
                        Icon(Icons.Filled.Delete, contentDescription = "Löschen")
                    }
                },
            )
        },
    ) { padding ->
        if (entry == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Eintrag nicht gefunden")
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeaderCard(entry) { pickDateTime(context, entry.dateTime) { vm.updateDateTime(entryId, it) } }

            NoteField(entryId, entry.note) { vm.updateNote(entryId, it) }

            SectionTitle("Tags")
            TagEditor(
                tags = entry.tags,
                suggestions = allTags.filter { t -> entry.tags.none { it.id == t.id } },
                onAdd = { vm.addTag(entryId, it) },
                onRemove = { vm.removeTag(entryId, it.id) },
            )

            SectionTitle("Medien & Links")
            AttachmentButtons(
                onPickMedia = {
                    pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                },
                onAddLink = { showLinkDialog = true },
                onPickFile = { pickFiles.launch(arrayOf("*/*")) },
            )
            Attachments(
                attachments = entry.attachments,
                fileFor = vm::fileFor,
                onOpen = { openAttachment(context, it, vm.fileFor(it)) },
                onRemove = { attachmentToRemove = it },
            )
        }
    }

    if (confirmDelete && entry != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Clop #${entry.number} löschen?") },
            text = { Text("Der Eintrag und alle Anhänge werden gelöscht. Die Nummern der späteren Einträge des Tages rücken nach.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteEntry(entryId)
                    onBack()
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }

    attachmentToRemove?.let { attachment ->
        AlertDialog(
            onDismissRequest = { attachmentToRemove = null },
            title = { Text("Anhang entfernen?") },
            text = { Text(attachment.title ?: attachment.value) },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeAttachment(attachment)
                    attachmentToRemove = null
                }) { Text("Entfernen") }
            },
            dismissButton = { TextButton(onClick = { attachmentToRemove = null }) { Text("Abbrechen") } },
        )
    }

    if (showLinkDialog) {
        LinkDialog(
            onDismiss = { showLinkDialog = false },
            onConfirm = { url, title ->
                vm.addLink(entryId, url, title)
                showLinkDialog = false
            },
        )
    }
}

@Composable
private fun HeaderCard(entry: NumberedEntry, onEditTime: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            NumberBadge(entry.number, size = 64.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.date.format(LongDateFormat), style = MaterialTheme.typography.titleMedium)
                Text("${entry.dateTime.format(TimeFormat)} Uhr", style = MaterialTheme.typography.bodyLarge)
            }
            IconButton(onClick = onEditTime) {
                Icon(Icons.Filled.Edit, contentDescription = "Datum/Uhrzeit ändern")
            }
        }
    }
}

@Composable
private fun NoteField(entryId: Long, savedNote: String, onSave: (String) -> Unit) {
    var text by remember(entryId) { mutableStateOf(savedNote) }
    val latestText by rememberUpdatedState(text)
    val latestSaved by rememberUpdatedState(savedNote)

    // Auto-save shortly after typing stops, and when leaving the screen.
    LaunchedEffect(text) {
        if (text != savedNote) {
            delay(600)
            onSave(text)
        }
    }
    DisposableEffect(entryId) {
        onDispose { if (latestText != latestSaved) onSave(latestText) }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Notiz") },
        minLines = 3,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagEditor(tags: List<Tag>, suggestions: List<Tag>, onAdd: (String) -> Unit, onRemove: (Tag) -> Unit) {
    var input by remember { mutableStateOf("") }
    val submit = {
        if (input.isNotBlank()) onAdd(input)
        input = ""
    }

    if (tags.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { tag ->
                InputChip(
                    selected = false,
                    onClick = { onRemove(tag) },
                    label = { Text(tag.name) },
                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Tag entfernen", Modifier.size(16.dp)) },
                )
            }
        }
    }
    OutlinedTextField(
        value = input,
        onValueChange = { input = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Tag hinzufügen") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        trailingIcon = {
            IconButton(onClick = submit, enabled = input.isNotBlank()) {
                Icon(Icons.Filled.Add, contentDescription = "Hinzufügen")
            }
        },
    )
    val filtered = suggestions.filter { input.isBlank() || it.name.contains(input.trim(), ignoreCase = true) }.take(12)
    if (filtered.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            filtered.forEach { tag ->
                SuggestionChip(
                    onClick = {
                        onAdd(tag.name)
                        input = ""
                    },
                    label = { Text(tag.name) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttachmentButtons(onPickMedia: () -> Unit, onAddLink: () -> Unit, onPickFile: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onPickMedia) {
            Icon(Icons.Filled.Image, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Bild/Video")
        }
        OutlinedButton(onClick = onAddLink) {
            Icon(Icons.Filled.Link, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Link")
        }
        OutlinedButton(onClick = onPickFile) {
            Icon(Icons.Filled.Description, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Datei")
        }
    }
}

@Composable
private fun Attachments(
    attachments: List<Attachment>,
    fileFor: (Attachment) -> File,
    onOpen: (Attachment) -> Unit,
    onRemove: (Attachment) -> Unit,
) {
    if (attachments.isEmpty()) {
        Text("Noch keine Anhänge.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    val media = attachments.filter { it.type == AttachmentType.IMAGE || it.type == AttachmentType.VIDEO }
    media.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { a ->
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onOpen(a) },
                ) {
                    AsyncImage(
                        model = fileFor(a),
                        contentDescription = a.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (a.type == AttachmentType.VIDEO) {
                        Icon(
                            Icons.Filled.PlayCircle,
                            contentDescription = "Video",
                            tint = Color.White,
                            modifier = Modifier.align(Alignment.Center).size(40.dp),
                        )
                    }
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable { onRemove(a) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Entfernen", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }

    attachments.filter { it.type == AttachmentType.LINK || it.type == AttachmentType.FILE }.forEach { a ->
        Card(onClick = { onOpen(a) }, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (a.type == AttachmentType.LINK) Icons.Filled.Link else Icons.Filled.Description,
                    contentDescription = null,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.title ?: a.value, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (a.type == AttachmentType.LINK && a.title != null) {
                        Text(
                            a.value,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = { onRemove(a) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Entfernen")
                }
            }
        }
    }
}

@Composable
private fun LinkDialog(onDismiss: () -> Unit, onConfirm: (String, String?) -> Unit) {
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link hinzufügen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titel (optional)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(url, title) }, enabled = url.isNotBlank()) { Text("Hinzufügen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

private fun pickDateTime(context: Context, current: LocalDateTime, onPicked: (LocalDateTime) -> Unit) {
    DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(
                context,
                { _, hour, minute -> onPicked(LocalDateTime.of(year, month + 1, day, hour, minute)) },
                current.hour,
                current.minute,
                true,
            ).show()
        },
        current.year,
        current.monthValue - 1,
        current.dayOfMonth,
    ).show()
}

private fun openAttachment(context: Context, attachment: Attachment, file: File) {
    val intent = if (attachment.type == AttachmentType.LINK) {
        Intent(Intent.ACTION_VIEW, Uri.parse(attachment.value))
    } else {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, attachment.mimeType ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Keine App zum Öffnen gefunden", Toast.LENGTH_SHORT).show()
    }
}
