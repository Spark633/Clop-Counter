package de.clopcounter.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ClopDao {
    @Transaction
    @Query("SELECT * FROM entries ORDER BY timestamp ASC, id ASC")
    fun observeAll(): Flow<List<EntryWithDetails>>

    @Insert
    suspend fun insertEntry(entry: CountEntry): Long

    @Query("SELECT COUNT(*) FROM entries WHERE epochDay = :epochDay")
    suspend fun countForDay(epochDay: Long): Int

    @Query("SELECT MAX(timestamp) FROM entries WHERE epochDay = :epochDay")
    suspend fun lastTimestampForDay(epochDay: Long): Long?

    @Query("UPDATE entries SET note = :note WHERE id = :id")
    suspend fun updateNote(id: Long, note: String)

    @Query("UPDATE entries SET timestamp = :timestamp, epochDay = :epochDay WHERE id = :id")
    suspend fun updateTime(id: Long, timestamp: Long, epochDay: Long)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Insert
    suspend fun insertAttachment(attachment: Attachment): Long

    @Query("UPDATE attachments SET previewUrl = :previewUrl, title = COALESCE(title, :title) WHERE id = :id")
    suspend fun updatePreview(id: Long, previewUrl: String, title: String?)

    @Delete
    suspend fun deleteAttachment(attachment: Attachment)

    @Query("SELECT * FROM attachments WHERE entryId = :entryId")
    suspend fun attachmentsFor(entryId: Long): List<Attachment>

    @Query("DELETE FROM attachments WHERE entryId = :entryId")
    suspend fun deleteAttachmentsFor(entryId: Long)

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    fun observeTags(): Flow<List<Tag>>

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findTag(name: String): Tag?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: Tag): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(ref: EntryTagCrossRef)

    @Query("DELETE FROM entry_tags WHERE entryId = :entryId AND tagId = :tagId")
    suspend fun removeTag(entryId: Long, tagId: Long)

    @Query("DELETE FROM entry_tags WHERE entryId = :entryId")
    suspend fun removeAllTags(entryId: Long)

    @Query("DELETE FROM tags WHERE id NOT IN (SELECT tagId FROM entry_tags)")
    suspend fun deleteUnusedTags()

    // ---------------------------------------------------------------- sync

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getEntry(id: Long): CountEntry?

    @Query("SELECT * FROM entries WHERE uuid = :uuid")
    suspend fun findByUuid(uuid: String): CountEntry?

    /** Marks an entry as changed so it gets uploaded. */
    @Query("UPDATE entries SET dirty = 1, updatedAt = MAX(updatedAt + 1, :now) WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("SELECT COUNT(*) FROM entries WHERE dirty = 1")
    fun observeDirtyCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM pending_deletes")
    fun observePendingDeleteCount(): Flow<Int>

    @Transaction
    @Query("SELECT * FROM entries WHERE dirty = 1")
    suspend fun dirtyEntries(): List<EntryWithDetails>

    /** Only clears the flag if the entry was not changed again during the upload. */
    @Query("UPDATE entries SET dirty = 0, synced = 1 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markClean(id: Long, updatedAt: Long)

    @Query("SELECT * FROM entries WHERE synced = 1 AND dirty = 0")
    suspend fun syncedCleanEntries(): List<CountEntry>

    /** Treat everything as new local data, e.g. after switching accounts. */
    @Query("UPDATE entries SET synced = 0, dirty = 1")
    suspend fun markAllUnsynced()

    @Query(
        "UPDATE entries SET timestamp = :timestamp, epochDay = :epochDay, note = :note, " +
            "updatedAt = :updatedAt, dirty = 0, synced = 1 WHERE id = :id",
    )
    suspend fun applyRemote(id: Long, timestamp: Long, epochDay: Long, note: String, updatedAt: Long)

    @Query("DELETE FROM attachments WHERE entryId = :entryId AND type = 'LINK'")
    suspend fun deleteLinksFor(entryId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPendingDelete(pending: PendingDelete)

    @Query("SELECT uuid FROM pending_deletes")
    suspend fun pendingDeletes(): List<String>

    @Query("DELETE FROM pending_deletes WHERE uuid IN (:uuids)")
    suspend fun clearPendingDeletes(uuids: List<String>)

    @Query("DELETE FROM pending_deletes")
    suspend fun clearAllPendingDeletes()
}
