package de.clopcounter.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

data class AddResult(val id: Long, val number: Int)

class ClopRepository(private val context: Context, private val db: AppDatabase) {
    private val dao = db.dao()

    private val attachmentDir: File
        get() = File(context.filesDir, "attachments").apply { mkdirs() }

    fun observeAll() = dao.observeAll()
    fun observeTags() = dao.observeTags()

    fun fileFor(attachment: Attachment) = File(attachmentDir, attachment.value)

    /** Adds a count for today (now) or, for a past [date], right after that day's last entry. */
    suspend fun addEntry(date: LocalDate? = null): AddResult = db.withTransaction {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val day = date ?: now.toLocalDate()
        val timestamp = if (day == now.toLocalDate()) {
            now.toInstant().toEpochMilli()
        } else {
            val last = dao.lastTimestampForDay(day.toEpochDay())
            val endOfDay = day.atTime(23, 59).atZone(zone).toInstant().toEpochMilli()
            if (last != null) minOf(last + 60_000, endOfDay)
            else day.atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
        }
        val number = dao.countForDay(day.toEpochDay()) + 1
        val id = dao.insertEntry(CountEntry(timestamp = timestamp, epochDay = day.toEpochDay()))
        AddResult(id, number)
    }

    suspend fun deleteEntry(id: Long) {
        val files = db.withTransaction {
            val attachments = dao.attachmentsFor(id)
            dao.deleteAttachmentsFor(id)
            dao.removeAllTags(id)
            dao.deleteEntry(id)
            dao.deleteUnusedTags()
            attachments.filter { it.type != AttachmentType.LINK }.map { fileFor(it) }
        }
        withContext(Dispatchers.IO) { files.forEach { it.delete() } }
    }

    suspend fun updateNote(id: Long, note: String) = dao.updateNote(id, note)

    suspend fun updateDateTime(id: Long, dateTime: LocalDateTime) {
        val ts = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        dao.updateTime(id, ts, dateTime.toLocalDate().toEpochDay())
    }

    suspend fun addTag(entryId: Long, rawName: String) {
        val name = rawName.trim().removePrefix("#").trim()
        if (name.isEmpty()) return
        db.withTransaction {
            val tag = dao.findTag(name) ?: Tag(id = dao.insertTag(Tag(name = name)), name = name)
            dao.insertCrossRef(EntryTagCrossRef(entryId, tag.id))
        }
    }

    suspend fun removeTag(entryId: Long, tagId: Long) = db.withTransaction {
        dao.removeTag(entryId, tagId)
        dao.deleteUnusedTags()
    }

    suspend fun addLink(entryId: Long, rawUrl: String, title: String?): Attachment {
        val url = rawUrl.trim().let { if (it.contains("://")) it else "https://$it" }
        val attachment = Attachment(
            entryId = entryId,
            type = AttachmentType.LINK,
            value = url,
            title = title?.trim()?.takeIf { it.isNotEmpty() },
        )
        return attachment.copy(id = dao.insertAttachment(attachment))
    }

    /** Fetches the link's preview image (og:image, or the URL itself if it is an image) and stores it. */
    suspend fun loadPreview(attachment: Attachment) {
        if (attachment.type != AttachmentType.LINK) return
        val preview = withContext(Dispatchers.IO) { LinkPreviewFetcher.fetch(attachment.value) }
        dao.updatePreview(attachment.id, preview?.imageUrl.orEmpty(), preview?.title)
    }

    /** Copies picked media/files into app storage so they stay available. */
    suspend fun addFiles(entryId: Long, uris: List<Uri>) = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        for (uri in uris) {
            val mime = resolver.getType(uri)
            val ext = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: "bin"
            val file = File(attachmentDir, "${UUID.randomUUID()}.$ext")
            val copied = runCatching {
                resolver.openInputStream(uri)!!.use { input ->
                    file.outputStream().use { input.copyTo(it) }
                }
            }.isSuccess
            if (!copied) {
                file.delete()
                continue
            }
            val type = when {
                mime?.startsWith("image/") == true -> AttachmentType.IMAGE
                mime?.startsWith("video/") == true -> AttachmentType.VIDEO
                else -> AttachmentType.FILE
            }
            dao.insertAttachment(
                Attachment(
                    entryId = entryId,
                    type = type,
                    value = file.name,
                    mimeType = mime,
                    title = displayName(uri),
                ),
            )
        }
    }

    suspend fun removeAttachment(attachment: Attachment) {
        dao.deleteAttachment(attachment)
        if (attachment.type != AttachmentType.LINK) {
            withContext(Dispatchers.IO) { fileFor(attachment).delete() }
        }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()
}
