@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package de.clopcounter.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.clopcounter.data.Stats
import de.clopcounter.ui.BarRow
import de.clopcounter.ui.ClopViewModel
import de.clopcounter.ui.GermanLocale
import de.clopcounter.ui.SectionTitle
import de.clopcounter.ui.ShortDateFormat
import de.clopcounter.ui.StatCard
import java.time.LocalDate
import java.time.format.TextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: ClopViewModel, onOpenDay: (LocalDate) -> Unit) {
    val stats by vm.stats.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Statistik") }) },
    ) { padding ->
        StatsContent(
            stats,
            onOpenDay,
            Modifier.fillMaxSize().padding(padding),
            emptyText = "Noch keine Daten. Zähle deinen ersten Clop auf dem Heute-Tab!",
        )
    }
}

/** The full statistics view; also used for friends' profiles. */
@Composable
fun StatsContent(stats: Stats, onOpenDay: (LocalDate) -> Unit, modifier: Modifier = Modifier, emptyText: String) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (stats.total == 0) {
            Text(emptyText)
            return@Column
        }
        Overview(stats, onOpenDay)

        StatSection("Wie oft wurde welche Nummer erreicht?") {
            val max = stats.perNumber.maxOfOrNull { it.second } ?: 0
            stats.perNumber.forEach { (number, count) ->
                val percent = 100 * count / stats.activeDays
                BarRow("#$number", count, max, trailing = "$count ($percent%)")
            }
            Text(
                "Prozent = Anteil der aktiven Tage, an denen diese Nummer erreicht wurde.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        StatSection("Tage nach Anzahl") {
            val max = stats.daysWithExactly.maxOfOrNull { it.second } ?: 0
            stats.daysWithExactly.forEach { (perDay, days) ->
                BarRow("${perDay}×", days, max, trailing = if (days == 1) "1 Tag" else "$days Tage")
            }
        }

        StatSection("Letzte 30 Tage") {
            Last30Chart(stats.last30Days.map { it.second })
            Text(
                "${stats.last30Days.sumOf { it.second }} Clops in den letzten 30 Tagen",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        StatSection("Wochentage") {
            val max = stats.perWeekday.maxOfOrNull { it.second } ?: 0
            stats.perWeekday.forEach { (day, count) ->
                BarRow(day.getDisplayName(TextStyle.SHORT, GermanLocale), count, max)
            }
        }

        if (stats.topTags.isNotEmpty()) {
            StatSection("Top-Tags") {
                val max = stats.topTags.maxOf { it.second }
                stats.topTags.forEach { (name, count) -> BarRow(name, count, max) }
            }
        }
    }
}

@Composable
private fun Overview(stats: Stats, onOpenDay: (LocalDate) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard(
            "Insgesamt",
            "${stats.total}",
            Modifier.weight(1f),
            sub = stats.firstDate?.let { "seit ${it.format(ShortDateFormat)}" },
        )
        StatCard("Aktive Tage", "${stats.activeDays}", Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard(
            "Ø pro Tag",
            String.format(GermanLocale, "%.2f", stats.averagePerActiveDay),
            Modifier.weight(1f),
            sub = "an aktiven Tagen",
        )
        val recordDate = stats.recordDate
        Card(
            onClick = { recordDate?.let(onOpenDay) },
            modifier = Modifier.weight(1f),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("Rekord", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("#${stats.recordCount}", style = MaterialTheme.typography.headlineSmall)
                if (recordDate != null) {
                    Text(
                        "am ${recordDate.format(ShortDateFormat)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard("Aktuelle Serie", "${stats.currentStreak} Tage", Modifier.weight(1f))
        StatCard("Längste Serie", "${stats.longestStreak} Tage", Modifier.weight(1f))
    }
}

@Composable
private fun StatSection(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionTitle(title, Modifier.padding(bottom = 4.dp))
            content()
        }
    }
}

@Composable
private fun Last30Chart(values: List<Int>) {
    val barColor = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(100.dp)) {
        if (values.isEmpty()) return@Canvas
        val slot = size.width / values.size
        val barWidth = slot * 0.7f
        values.forEachIndexed { i, v ->
            val h = if (v == 0) 3.dp.toPx() else size.height * v / max
            drawRoundRect(
                color = if (v == 0) emptyColor else barColor,
                topLeft = Offset(i * slot + (slot - barWidth) / 2, size.height - h),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
        }
    }
}
