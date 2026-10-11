@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package de.clopcounter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import de.clopcounter.data.Attachment
import de.clopcounter.data.AttachmentType
import de.clopcounter.data.NumberedEntry
import de.clopcounter.data.Tag
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

val GermanLocale: Locale = Locale.GERMAN
val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", GermanLocale)
val ShortDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", GermanLocale)
val LongDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", GermanLocale)

fun LocalDate.weekdayShort(): String = dayOfWeek.getDisplayName(TextStyle.SHORT, GermanLocale)

@Composable
fun NumberBadge(number: Int, size: Dp = 44.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "#$number",
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.36f).sp,
        )
    }
}

@Composable
fun TagLabel(tag: Tag) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            tag.name,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** First local image/video, otherwise the first link preview image. */
private fun thumbnailFor(filesDir: File, attachments: List<Attachment>): Any? {
    val media = attachments.firstOrNull { it.type == AttachmentType.IMAGE || it.type == AttachmentType.VIDEO }
    if (media != null) return File(File(filesDir, "attachments"), media.value)
    return attachments.firstOrNull { it.type == AttachmentType.LINK && !it.previewUrl.isNullOrEmpty() }?.previewUrl
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryRow(entry: NumberedEntry, onClick: () -> Unit, showDate: Boolean = false) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            NumberBadge(entry.number)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val time = entry.dateTime.format(TimeFormat)
                Text(
                    if (showDate) "${entry.date.format(ShortDateFormat)} · $time Uhr" else "$time Uhr",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (entry.note.isNotBlank()) {
                    Text(
                        entry.note,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (entry.tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        entry.tags.forEach { TagLabel(it) }
                    }
                }
            }
            if (entry.attachments.isNotEmpty()) {
                val thumbnail = thumbnailFor(LocalContext.current.filesDir, entry.attachments)
                Spacer(Modifier.width(8.dp))
                if (thumbnail != null) {
                    Box {
                        AsyncImage(
                            model = thumbnail,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                        if (entry.attachments.size > 1) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.Black.copy(alpha = 0.6f),
                                modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp),
                            ) {
                                Text(
                                    "${entry.attachments.size}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                            }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AttachFile, contentDescription = "Anhänge", modifier = Modifier.size(18.dp))
                        Text("${entry.attachments.size}", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (sub != null) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun BarRow(label: String, value: Int, max: Int, trailing: String = value.toString()) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(64.dp), style = MaterialTheme.typography.bodyMedium)
        Box(
            Modifier
                .weight(1f)
                .height(18.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (max > 0 && value > 0) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(value.toFloat() / max)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        Text(
            trailing,
            modifier = Modifier.width(72.dp).padding(start = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}
