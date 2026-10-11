package de.clopcounter.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.clopcounter.ClopApp
import de.clopcounter.data.AddResult
import de.clopcounter.data.Attachment
import de.clopcounter.data.NumberedEntry
import de.clopcounter.data.Stats
import de.clopcounter.data.Tag
import de.clopcounter.data.computeStats
import de.clopcounter.data.numberEntries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

class ClopViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as ClopApp).repository

    val entries: StateFlow<List<NumberedEntry>> = repo.observeAll()
        .map(::numberEntries)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val tags: StateFlow<List<Tag>> = repo.observeTags()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Updates itself when the date changes, so the daily counter resets at midnight. */
    val today: StateFlow<LocalDate> = flow {
        while (true) {
            emit(LocalDate.now())
            delay(30_000)
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, LocalDate.now())

    val stats: StateFlow<Stats> = combine(entries, today, ::computeStats)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, Stats())

    fun addCount(date: LocalDate? = null, onAdded: (AddResult) -> Unit = {}) {
        viewModelScope.launch { onAdded(repo.addEntry(date)) }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch { repo.deleteEntry(id) }
    }

    fun updateNote(id: Long, note: String) {
        viewModelScope.launch { repo.updateNote(id, note) }
    }

    fun updateDateTime(id: Long, dateTime: LocalDateTime) {
        viewModelScope.launch { repo.updateDateTime(id, dateTime) }
    }

    fun addTag(id: Long, name: String) {
        viewModelScope.launch { repo.addTag(id, name) }
    }

    fun removeTag(id: Long, tagId: Long) {
        viewModelScope.launch { repo.removeTag(id, tagId) }
    }

    fun addFiles(id: Long, uris: List<Uri>) {
        viewModelScope.launch { repo.addFiles(id, uris) }
    }

    fun addLink(id: Long, url: String, title: String?) {
        viewModelScope.launch { loadPreview(repo.addLink(id, url, title)) }
    }

    private val _loadingPreviews = MutableStateFlow(emptySet<Long>())

    /** Ids of link attachments whose preview is currently being fetched. */
    val loadingPreviews: StateFlow<Set<Long>> = _loadingPreviews

    fun loadPreview(attachment: Attachment) {
        if (attachment.id in _loadingPreviews.value) return
        _loadingPreviews.update { it + attachment.id }
        viewModelScope.launch {
            try {
                repo.loadPreview(attachment)
            } finally {
                _loadingPreviews.update { it - attachment.id }
            }
        }
    }

    fun removeAttachment(attachment: Attachment) {
        viewModelScope.launch { repo.removeAttachment(attachment) }
    }

    fun fileFor(attachment: Attachment): File = repo.fileFor(attachment)
}
