@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package de.clopcounter.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.clopcounter.ui.ClopViewModel
import de.clopcounter.ui.EntryRow
import de.clopcounter.ui.LongDateFormat
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(vm: ClopViewModel, onOpenEntry: (Long) -> Unit, onOpenDay: (LocalDate) -> Unit) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var selectedTags by remember { mutableStateOf(emptySet<Long>()) }

    val grouped = remember(entries, query, selectedTags) {
        entries
            .filter { e ->
                (selectedTags.isEmpty() || e.tags.any { it.id in selectedTags }) &&
                    (
                        query.isBlank() ||
                            e.note.contains(query, ignoreCase = true) ||
                            e.tags.any { it.name.contains(query, ignoreCase = true) } ||
                            e.attachments.any {
                                (it.title ?: "").contains(query, ignoreCase = true) ||
                                    it.value.contains(query, ignoreCase = true)
                            }
                        )
            }
            .asReversed()
            .groupBy { it.date }
    }
    val resultCount = grouped.values.sumOf { it.size }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Verlauf") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Suchen (Notiz, Tag, Link)") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Leeren")
                            }
                        }
                    },
                )
            }
            if (tags.isNotEmpty()) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(tags, key = { it.id }) { tag ->
                            FilterChip(
                                selected = tag.id in selectedTags,
                                onClick = {
                                    selectedTags = if (tag.id in selectedTags) selectedTags - tag.id else selectedTags + tag.id
                                },
                                label = { Text(tag.name) },
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    "$resultCount Einträge",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (resultCount == 0) {
                item {
                    Text(
                        if (entries.isEmpty()) "Noch keine Einträge." else "Nichts gefunden.",
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            grouped.forEach { (date, dayEntries) ->
                stickyHeader(key = "header-$date") {
                    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier
                                .clickable { onOpenDay(date) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                date.format(LongDateFormat),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text("${dayEntries.size}×", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
                items(dayEntries, key = { it.id }) { entry ->
                    EntryRow(entry, onClick = { onOpenEntry(entry.id) })
                }
            }
        }
    }
}
