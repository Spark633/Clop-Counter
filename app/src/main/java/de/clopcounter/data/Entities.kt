package de.clopcounter.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

/** One counted "Clop". The daily number (#1, #2, ...) is derived from the order within [epochDay]. */
@Entity(tableName = "entries", indices = [Index("epochDay")])
data class CountEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val epochDay: Long,
    val note: String = "",
)

enum class AttachmentType { IMAGE, VIDEO, FILE, LINK }

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = CountEntry::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("entryId")],
)
data class Attachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val type: AttachmentType,
    /** File name inside the app's attachment folder, or the URL for links. */
    val value: String,
    val mimeType: String? = null,
    val title: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "entry_tags",
    primaryKeys = ["entryId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = CountEntry::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tagId")],
)
data class EntryTagCrossRef(
    val entryId: Long,
    val tagId: Long,
)

data class EntryWithDetails(
    @Embedded val entry: CountEntry,
    @Relation(parentColumn = "id", entityColumn = "entryId")
    val attachments: List<Attachment>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(EntryTagCrossRef::class, parentColumn = "entryId", entityColumn = "tagId"),
    )
    val tags: List<Tag>,
)
