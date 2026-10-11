package de.clopcounter.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.clopcounter.ui.ClopViewModel
import de.clopcounter.ui.GermanLocale
import de.clopcounter.ui.SectionTitle
import de.clopcounter.ui.ShortDateFormat
import de.clopcounter.ui.weekdayShort
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

private fun YearMonth.toIndex() = year * 12 + monthValue - 1
private fun yearMonthOf(index: Int): YearMonth = YearMonth.of(index / 12, index % 12 + 1)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(vm: ClopViewModel, onOpenDay: (LocalDate) -> Unit) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    var monthIndex by rememberSaveable { mutableIntStateOf(YearMonth.now().toIndex()) }
    val month = yearMonthOf(monthIndex)

    val counts = remember(entries) { entries.groupingBy { it.date }.eachCount() }
    val monthCounts = counts.filterKeys { YearMonth.from(it) == month }
    val maxInMonth = monthCounts.values.maxOrNull() ?: 0

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Kalender") },
                actions = {
                    TextButton(onClick = { monthIndex = YearMonth.from(today).toIndex() }) { Text("Heute") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { monthIndex-- }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Vorheriger Monat")
                }
                Text(
                    "${month.month.getDisplayName(TextStyle.FULL_STANDALONE, GermanLocale)} ${month.year}",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                )
                IconButton(onClick = { monthIndex++ }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Nächster Monat")
                }
            }

            Row {
                DayOfWeek.values().forEach {
                    Text(
                        it.getDisplayName(TextStyle.SHORT, GermanLocale),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val offset = month.atDay(1).dayOfWeek.value - 1
            val days = month.lengthOfMonth()
            val rows = (offset + days + 6) / 7
            for (week in 0 until rows) {
                Row {
                    for (col in 0 until 7) {
                        val dayNumber = week * 7 + col - offset + 1
                        if (dayNumber in 1..days) {
                            val date = month.atDay(dayNumber)
                            DayCell(
                                date = date,
                                count = counts[date] ?: 0,
                                max = maxInMonth,
                                isToday = date == today,
                                onClick = { onOpenDay(date) },
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            Spacer(Modifier.weight(1f).aspectRatio(1f))
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionTitle("Dieser Monat")
                    Text("${monthCounts.values.sum()} Clops an ${monthCounts.size} Tagen")
                    monthCounts.maxByOrNull { it.value }?.let { (date, count) ->
                        Text("Bester Tag: ${date.format(ShortDateFormat)} mit $count")
                    }
                }
            }

            if (monthCounts.isNotEmpty()) {
                SectionTitle("Tage", Modifier.padding(top = 8.dp))
                monthCounts.toSortedMap(compareByDescending { it.toEpochDay() }).forEach { (date, count) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenDay(date) }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                    ) {
                        Text("${date.weekdayShort()}, ${date.format(ShortDateFormat)}", Modifier.weight(1f))
                        Text("$count×", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    count: Int,
    max: Int,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val intensity = if (count == 0 || max == 0) 0f else 0.3f + 0.7f * count / max
    val background = if (count == 0) colors.surfaceVariant.copy(alpha = 0.5f) else colors.primary.copy(alpha = intensity)
    val textColor = if (intensity > 0.6f) colors.onPrimary else colors.onSurface
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(shape)
            .background(background)
            .then(if (isToday) Modifier.border(BorderStroke(2.dp, colors.tertiary), shape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${date.dayOfMonth}", color = textColor, style = MaterialTheme.typography.bodyMedium)
            if (count > 0) {
                Text("$count", color = textColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}
