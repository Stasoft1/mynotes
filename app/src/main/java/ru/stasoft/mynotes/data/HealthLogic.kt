package ru.stasoft.mynotes.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Показатель из настроек: «Вес = кг». */
class Metric(val name: String, val unit: String)

fun parseMetrics(text: String): List<Metric> = text.lines().mapNotNull { l ->
    val n = l.substringBefore('=').trim()
    if (n.isEmpty()) null else Metric(n, if (l.contains('=')) l.substringAfter('=').trim() else "")
}

/** Замер: [v2] — вторая цифра, например нижнее давление в «120/80». */
class Reading(val date: LocalDate, val v: Double, val v2: Double?, val noteId: Long)

private val PAIR = Regex("""^\s*(\d+(?:[.,]\d+)?)\s*/\s*(\d+(?:[.,]\d+)?)""")

fun noteLocalDate(n: NoteEntity): LocalDate = Instant.ofEpochMilli(expenseDate(n)).atZone(ZoneId.systemDefault()).toLocalDate()

/** Все замеры показателя [metric]: заметки с полем «Показатель: значение» («Вес: 82,5», «Давление: 120/80»). */
fun parseReadings(notes: List<NoteEntity>, metric: String): List<Reading> {
    val out = ArrayList<Reading>()
    for (n in notes) {
        if (n.isFolder) continue
        val raw = fieldText(n.body, metric) ?: continue
        val pair = PAIR.find(raw)
        val v: Double?
        var v2: Double? = null
        if (pair != null) {
            v = pair.groupValues[1].replace(',', '.').toDoubleOrNull()
            v2 = pair.groupValues[2].replace(',', '.').toDoubleOrNull()
        } else v = leadingNumber(raw)
        if (v != null) out.add(Reading(noteLocalDate(n), v, v2, n.id))
    }
    return out.sortedBy { it.date }
}

/** Привычка — заметка с полем «Привычка: Название»; отметки — строки с датами «дд.мм.гггг». */
class Habit(val noteId: Long, val name: String, val days: Set<LocalDate>) {
    fun currentStreak(today: LocalDate): Int {
        var d = if (today in days) today else today.minusDays(1)
        var n = 0
        while (d in days) { n++; d = d.minusDays(1) }
        return n
    }

    fun bestStreak(): Int {
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (d in days.sorted()) {
            run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
            if (run > best) best = run
            prev = d
        }
        return best
    }

    fun share(today: LocalDate, n: Int): Int = (0 until n).count { today.minusDays(it.toLong()) in days } * 100 / n
}

private val HABIT_DATE = Regex("""^\s*(\d{1,2})\.(\d{1,2})\.(\d{4})\s*$""")

fun parseHabitDays(body: String): Set<LocalDate> = body.lines().mapNotNull { l ->
    HABIT_DATE.matchEntire(l)?.let { m ->
        try { LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()) } catch (_: Exception) { null }
    }
}.toSet()

fun parseHabits(notes: List<NoteEntity>): List<Habit> = notes.filter { !it.isFolder }.mapNotNull { n ->
    fieldText(n.body, "Привычка")?.takeIf { it.isNotBlank() }?.let { Habit(n.id, it, parseHabitDays(n.body)) }
}.sortedBy { it.name.lowercase() }

fun fmtDate(d: LocalDate) = "%02d.%02d.%04d".format(d.dayOfMonth, d.monthValue, d.year)

/** Добавляет или убирает строку с датой в тексте заметки привычки. */
fun toggleHabitBody(body: String, day: LocalDate): String {
    val line = fmtDate(day)
    val lines = body.lines()
    return if (lines.any { it.trim() == line }) lines.filter { it.trim() != line }.joinToString("\n")
    else body.trimEnd('\n') + "\n" + line
}
