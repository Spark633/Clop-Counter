package de.clopcounter.data

import androidx.room.withTransaction
import de.clopcounter.data.remote.NotSignedInException
import de.clopcounter.data.remote.RemoteEntry
import de.clopcounter.data.remote.RemoteLink
import de.clopcounter.data.remote.SessionStore
import de.clopcounter.data.remote.SupabaseApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

sealed interface SyncState {
    data object Idle : SyncState
    data object Running : SyncState
    data class Done(val at: Long) : SyncState
    data class Failed(val message: String) : SyncState
}

/**
 * Keeps local entries in sync with the account on the server.
 * Entries, tags, notes and links are synced; local images/videos/files never leave the device.
 */
class SyncManager(
    private val db: AppDatabase,
    private val repository: ClopRepository,
    private val api: SupabaseApi,
    private val session: SessionStore,
) {
    private val dao = db.dao()
    private val lock = Mutex()

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state

    /** Uploads local changes automatically a moment after they happen. */
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(dao.observeDirtyCount(), dao.observePendingDeleteCount(), session.account) { dirty, deletes, account ->
                account != null && dirty + deletes > 0
            }
                .debounce(2_000)
                .filter { it }
                .collect { sync(pull = false) }
        }
    }

    /** Call after signing in: data of a different previous account must not be mixed up. */
    suspend fun onSignedIn(userId: String, previousUserId: String?) {
        if (previousUserId != userId) {
            dao.markAllUnsynced()
            dao.clearAllPendingDeletes()
        }
    }

    /** Call after the account was deleted on the server. */
    suspend fun onAccountDeleted() {
        dao.markAllUnsynced()
        dao.clearAllPendingDeletes()
    }

    suspend fun sync(pull: Boolean) {
        val account = session.account.value ?: return
        if (!api.isConfigured) return
        lock.withLock {
            _state.value = SyncState.Running
            _state.value = try {
                push()
                if (pull) pull(account.userId)
                // Changes made while uploading are picked up by the next round.
                if (dao.dirtyEntries().isNotEmpty()) push()
                SyncState.Done(System.currentTimeMillis())
            } catch (e: NotSignedInException) {
                SyncState.Failed("Bitte neu anmelden")
            } catch (e: IOException) {
                SyncState.Failed("Keine Verbindung zum Server")
            } catch (e: Exception) {
                SyncState.Failed(e.message ?: "Synchronisierung fehlgeschlagen")
            }
        }
    }

    private suspend fun push() {
        val deletes = dao.pendingDeletes()
        if (deletes.isNotEmpty()) {
            api.deleteEntries(deletes)
            dao.clearPendingDeletes(deletes)
        }
        dao.dirtyEntries().chunked(500).forEach { chunk ->
            api.upsertEntries(chunk.map { it.toRemote() })
            chunk.forEach { dao.markClean(it.entry.id, it.entry.updatedAt) }
        }
    }

    private suspend fun pull(userId: String) {
        val remote = api.fetchEntries(userId, withNotes = true)
        val remoteIds = remote.mapTo(HashSet()) { it.uuid }

        db.withTransaction {
            for (r in remote) {
                val local = dao.findByUuid(r.uuid)
                when {
                    local == null -> {
                        val id = dao.insertEntry(
                            CountEntry(
                                timestamp = r.timestamp,
                                epochDay = r.epochDay,
                                note = r.note,
                                uuid = r.uuid,
                                updatedAt = r.updatedAt,
                                dirty = false,
                                synced = true,
                            ),
                        )
                        applyTagsAndLinks(id, r)
                    }
                    !local.dirty && r.updatedAt > local.updatedAt -> {
                        dao.applyRemote(local.id, r.timestamp, r.epochDay, r.note, r.updatedAt)
                        dao.deleteLinksFor(local.id)
                        dao.removeAllTags(local.id)
                        applyTagsAndLinks(local.id, r)
                    }
                }
            }
            dao.deleteUnusedTags()
        }

        // Deleted on another device: remove locally (without uploading the delete again).
        dao.syncedCleanEntries()
            .filter { it.uuid !in remoteIds }
            .forEach { repository.deleteEntry(it.id, uploadDelete = false) }
    }

    private suspend fun applyTagsAndLinks(entryId: Long, remote: RemoteEntry) {
        for (name in remote.tags) {
            val tag = dao.findTag(name) ?: Tag(id = dao.insertTag(Tag(name = name)), name = name)
            dao.insertCrossRef(EntryTagCrossRef(entryId, tag.id))
        }
        for (link in remote.links) {
            dao.insertAttachment(
                Attachment(
                    entryId = entryId,
                    type = AttachmentType.LINK,
                    value = link.url,
                    title = link.title,
                    previewUrl = link.previewUrl,
                ),
            )
        }
    }
}

fun EntryWithDetails.toRemote() = RemoteEntry(
    uuid = entry.uuid,
    timestamp = entry.timestamp,
    epochDay = entry.epochDay,
    tags = tags.map { it.name },
    links = attachments.filter { it.type == AttachmentType.LINK }
        .map { RemoteLink(it.value, it.title, it.previewUrl?.takeIf { p -> p.isNotEmpty() }) },
    updatedAt = entry.updatedAt,
    note = entry.note,
)

/** Turns a friend's server entries into the same model the local screens use. */
fun List<RemoteEntry>.toNumberedEntries(): List<NumberedEntry> {
    var nextId = 1L
    val details = map { r ->
        val entryId = nextId++
        EntryWithDetails(
            entry = CountEntry(
                id = entryId,
                timestamp = r.timestamp,
                epochDay = r.epochDay,
                uuid = r.uuid,
                updatedAt = r.updatedAt,
                dirty = false,
                synced = true,
            ),
            attachments = r.links.mapIndexed { i, link ->
                Attachment(
                    id = entryId * 1000 + i,
                    entryId = entryId,
                    type = AttachmentType.LINK,
                    value = link.url,
                    title = link.title,
                    previewUrl = link.previewUrl,
                )
            },
            tags = r.tags.map { Tag(id = it.lowercase().hashCode().toLong(), name = it) },
        )
    }
    return numberEntries(details)
}
