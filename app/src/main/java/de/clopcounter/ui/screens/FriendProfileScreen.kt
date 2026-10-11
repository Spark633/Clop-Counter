@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package de.clopcounter.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.clopcounter.data.AttachmentType
import de.clopcounter.data.NumberedEntry
import de.clopcounter.data.computeStats
import de.clopcounter.data.remote.Friend
import de.clopcounter.data.remote.FriendStatus
import de.clopcounter.ui.EntryRow
import de.clopcounter.ui.LongDateFormat
import de.clopcounter.ui.MediaViewer
import de.clopcounter.ui.SocialViewModel
import de.clopcounter.ui.ViewerItem
import de.clopcounter.ui.toUserMessage
import java.time.LocalDate

private sealed interface LoadState {
    data object Loading : LoadState
    data class Loaded(val entries: List<NumberedEntry>) : LoadState
    data class Failed(val message: String) : LoadState
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FriendProfileScreen(
    social: SocialViewModel,
    userId: String,
    username: String,
    userNumber: Long,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var reload by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<LoadState>(LoadState.Loading) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var viewer by remember { mutableStateOf<Pair<List<ViewerItem>, Int>?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }

    LaunchedEffect(userId, reload) {
        state = LoadState.Loading
        state = try {
            LoadState.Loaded(social.loadFriendEntries(userId))
        } catch (e: Exception) {
            LoadState.Failed(e.toUserMessage())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(username)
                        Text("#$userNumber", style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmRemove = true }) {
                        Icon(Icons.Filled.PersonRemove, contentDescription = "Freund entfernen")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                LoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is LoadState.Failed -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(s.message, textAlign = TextAlign.Center)
                    Button(onClick = { reload++ }, modifier = Modifier.padding(top = 12.dp)) { Text("Erneut versuchen") }
                }
                is LoadState.Loaded -> {
                    val entries = s.entries
                    val gallery = remember(entries) { galleryItems(entries) }
                    TabRow(selectedTabIndex = tab) {
                        listOf("Statistik", "Verlauf", "Galerie").forEachIndexed { i, label ->
                            Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                        }
                    }
                    when (tab) {
                        0 -> {
                            val stats = remember(entries) { computeStats(entries, LocalDate.now()) }
                            StatsContent(stats, onOpenDay = {}, modifier = Modifier.fillMaxSize(), emptyText = "$username hat noch keine Einträge.")
                        }
                        1 -> FriendHistory(entries) { entry ->
                            val items = galleryItems(listOf(entry))
                            when {
                                items.isNotEmpty() -> viewer = items to 0
                                entry.attachments.isNotEmpty() -> openUrl(context, entry.attachments.first().value)
                                else -> {}
                            }
                        }
                        else -> FriendGallery(gallery) { index -> viewer = gallery to index }
                    }
                }
            }
        }
    }

    viewer?.let { (items, start) ->
        MediaViewer(
            items = items,
            startIndex = start,
            onDismiss = { viewer = null },
            onOpenExternal = { item -> openUrl(context, item.key.substringAfter('|')) },
        )
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("$username entfernen?") },
            text = { Text("Ihr seht danach gegenseitig keine Einträge mehr.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    social.removeFriend(Friend(userId, username, userNumber, FriendStatus.FRIEND), onDone = onBack)
                }) { Text("Entfernen") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Abbrechen") } },
        )
    }
}

/** Link preview images of the given entries, newest first. The key carries the link URL after '|'. */
private fun galleryItems(entries: List<NumberedEntry>): List<ViewerItem> =
    entries.asReversed().flatMap { entry ->
        entry.attachments
            .filter { it.type == AttachmentType.LINK && !it.previewUrl.isNullOrEmpty() }
            .map { a ->
                ViewerItem(
                    key = "${a.id}|${a.value}",
                    model = a.previewUrl!!,
                    caption = "#${entry.number} · ${entry.date.format(LongDateFormat)}",
                )
            }
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FriendHistory(entries: List<NumberedEntry>, onOpen: (NumberedEntry) -> Unit) {
    val grouped = remember(entries) { entries.asReversed().groupBy { it.date } }
    if (grouped.isEmpty()) {
        Text("Noch keine Einträge.", modifier = Modifier.padding(16.dp))
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        grouped.forEach { (date, dayEntries) ->
            stickyHeader(key = "h-$date") {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "${date.format(LongDateFormat)} · ${dayEntries.size}×",
                        modifier = Modifier.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            items(dayEntries, key = { it.details.entry.uuid }) { entry -> EntryRow(entry, onClick = { onOpen(entry) }) }
        }
    }
}

@Composable
private fun FriendGallery(items: List<ViewerItem>, onOpen: (Int) -> Unit) {
    if (items.isEmpty()) {
        Text("Noch keine geteilten Bilder.", modifier = Modifier.padding(16.dp))
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(110.dp),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
            AsyncImage(
                model = item.model,
                contentDescription = item.caption,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onOpen(index) },
            )
        }
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Keine App zum Öffnen gefunden", Toast.LENGTH_SHORT).show()
    }
}
