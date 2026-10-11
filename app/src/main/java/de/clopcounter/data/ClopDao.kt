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
}
