package de.clopcounter.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** An entry together with its number for the day (#1, #2, ...). */
data class NumberedEntry(
    val details: EntryWithDetails,
    val number: Int,
) {
    val id: Long get() = details.entry.id
    val note: String get() = details.entry.note
    val tags: List<Tag> get() = details.tags
    val attachments: List<Attachment> get() = details.attachments
    val date: LocalDate get() = LocalDate.ofEpochDay(details.entry.epochDay)
    val dateTime: LocalDateTime
        get() = Instant.ofEpochMilli(details.entry.timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime()
}

fun numberEntries(entries: List<EntryWithDetails>): List<NumberedEntry> =
    entries.groupBy { it.entry.epochDay }
        .toSortedMap()
        .flatMap { (_, day) ->
            day.sortedWith(compareBy<EntryWithDetails>({ it.entry.timestamp }, { it.entry.id }))
                .mapIndexed { index, e -> NumberedEntry(e, index + 1) }
        }

data class Stats(
    val total: Int = 0,
    val activeDays: Int = 0,
    val averagePerActiveDay: Double = 0.0,
    val recordCount: Int = 0,
    val recordDate: LocalDate? = null,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val firstDate: LocalDate? = null,
    /** How often each daily number was reached: #1 -> n, #2 -> n, ... */
    val perNumber: List<Pair<Int, Int>> = emptyList(),
    /** How many days had exactly n entries. */
    val daysWithExactly: List<Pair<Int, Int>> = emptyList(),
    /** Entries per weekday, Monday first. */
    val perWeekday: List<Pair<DayOfWeek, Int>> = DayOfWeek.values().toList().map { it to 0 },
    val topTags: List<Pair<String, Int>> = emptyList(),
    val last30Days: List<Pair<LocalDate, Int>> = emptyList(),
)

fun computeStats(entries: List<NumberedEntry>, today: LocalDate): Stats {
    val byDay = entries.groupingBy { it.date }.eachCount()
    val days = byDay.keys.sortedBy { it.toEpochDay() }

    var longest = 0
    var run = 0
    var previous: LocalDate? = null
    for (day in days) {
        run = if (previous != null && previous.plusDays(1) == day) run + 1 else 1
        longest = maxOf(longest, run)
        previous = day
    }

    // A streak is only broken once a whole day passes without an entry.
    var current = 0
    var cursor = if (today in byDay) today else today.minusDays(1)
    while (cursor in byDay) {
        current++
        cursor = cursor.minusDays(1)
    }

    val record = byDay.entries.maxWithOrNull(compareBy<Map.Entry<LocalDate, Int>>({ it.value }, { -it.key.toEpochDay() }))

    return Stats(
        total = entries.size,
        activeDays = byDay.size,
        averagePerActiveDay = if (byDay.isEmpty()) 0.0 else entries.size.toDouble() / byDay.size,
        recordCount = record?.value ?: 0,
        recordDate = record?.key,
        currentStreak = current,
        longestStreak = longest,
        firstDate = days.firstOrNull(),
        perNumber = entries.groupingBy { it.number }.eachCount().toSortedMap().toList(),
        daysWithExactly = byDay.values.groupingBy { it }.eachCount().toSortedMap().toList(),
        perWeekday = DayOfWeek.values().toList().map { dow -> dow to entries.count { it.date.dayOfWeek == dow } },
        topTags = entries.flatMap { it.tags }
            .groupingBy { it.name }.eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(10),
        last30Days = (29 downTo 0).map { offset ->
            val d = today.minusDays(offset.toLong())
            d to (byDay[d] ?: 0)
        },
    )
}
