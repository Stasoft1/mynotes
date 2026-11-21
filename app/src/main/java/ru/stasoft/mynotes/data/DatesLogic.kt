package ru.stasoft.mynotes.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Событие из заметки: подписка, день рождения, гарантия или долг. [next] — ближайшая дата (у долга без срока — null). */
class DueItem(
    val kind: String,
    val name: String,
    val next: LocalDate?,
    val amount: Double?,
    val noteId: Long,
    val extra: String = "",
    /** Для подписки: сколько раз в год. */
    val perYear: Double = 12.0,
)

private val DAY = Regex("""(\d{1,2})\.(\d{1,2})(?:\.(\d{2,4}))?""")

/** «15.03.2026», «15.03.26» или «15.03» (год не указан — null вместо года). */
class DayParts(val d: Int, val m: Int, val y: Int?)

fun parseDayParts(s: String?): DayParts? {
    val x = DAY.find(s ?: return null) ?: return null
    val y = x.groupValues[3].let { if (it.isEmpty()) null else if (it.length == 2) 2000 + it.toInt() else it.toInt() }
    return DayParts(x.groupValues[1].toInt(), x.groupValues[2].toInt(), y)
}

private fun safeDate(y: Int, m: Int, d: Int): LocalDate? = try {
    LocalDate.of(y, m, d)
} catch (_: Exception) {
    try { LocalDate.of(y, m, 1).let { it.withDayOfMonth(minOf(d, it.lengthOfMonth())) } } catch (_: Exception) { null }
}

/** Ближайшее наступление даты «день.месяц» не раньше [today]. */
fun nextYearly(p: DayParts, today: LocalDate): LocalDate? {
    var y = today.year
    var d = safeDate(y, p.m, p.d) ?: return null
    if (d.isBefore(today)) { y++; d = safeDate(y, p.m, p.d) ?: return null }
    return d
}

private fun nextAfter(start: LocalDate, period: String, today: LocalDate): LocalDate {
    var d = start
    var guard = 0
    while (d.isBefore(today) && guard++ < 5000) {
        d = when {
            period.startsWith("нед") -> d.plusWeeks(1)
            period.startsWith("год") -> d.plusYears(1)
            period.startsWith("кварт") -> d.plusMonths(3)
            else -> d.plusMonths(1)
        }
    }
    return d
}

/** Все события из заметок для включённых видов [on] (subs, bday, warr, debt). */
fun parseDueItems(notes: List<NoteEntity>, today: LocalDate, on: Set<String>): List<DueItem> {
    val out = ArrayList<DueItem>()
    for (n in notes) {
        if (n.isFolder) continue
        val b = n.body
        if ("subs" in on) fieldText(b, "Подписка")?.takeIf { it.isNotBlank() }?.let { name ->
            val period = (fieldText(b, "Период") ?: "месяц").lowercase()
            val amount = fieldText(b, "Сумма")?.let(::leadingNumber)
            val p = parseDayParts(fieldText(b, "Дата") ?: fieldText(b, "Списание"))
            val next = when {
                p?.y != null -> safeDate(p.y, p.m, p.d)?.let { nextAfter(it, period, today) }
                p != null -> nextYearly(p, today)
                else -> fieldText(b, "Списание")?.trim()?.toIntOrNull()?.let { dd ->
                    safeDate(today.year, today.monthValue, dd)?.let { d -> if (d.isBefore(today)) safeDate(today.year, today.monthValue, 1)?.plusMonths(1)?.let { safeDate(it.year, it.monthValue, dd) } else d }
                }
            }
            val per = when { period.startsWith("нед") -> 52.0; period.startsWith("год") -> 1.0; period.startsWith("кварт") -> 4.0; else -> 12.0 }
            out.add(DueItem("subs", name, next, amount, n.id, period, per))
        }
        if ("bday" in on) {
            val name = fieldText(b, "ДР") ?: fieldText(b, "День рождения") ?: fieldText(b, "Событие")
            if (!name.isNullOrBlank()) parseDayParts(fieldText(b, "Дата"))?.let { p ->
                nextYearly(p, today)?.let { nx ->
                    val age = p.y?.let { nx.year - it }
                    out.add(DueItem("bday", name, nx, null, n.id, if (age != null && age in 1..130 && fieldText(b, "Событие") == null) "исполнится $age" else ""))
                }
            }
        }
        if ("warr" in on) fieldText(b, "Гарантия")?.takeIf { it.isNotBlank() }?.let { name ->
            val p = parseDayParts(fieldText(b, "До") ?: fieldText(b, "Срок"))
            val d = if (p?.y != null) safeDate(p.y, p.m, p.d) else null
            if (d != null) out.add(DueItem("warr", name, d, null, n.id))
        }
        if ("debt" in on) {
            val theirs = fieldText(b, "Долг")?.takeIf { it.isNotBlank() }
            val mine = fieldText(b, "Я должен")?.takeIf { it.isNotBlank() }
            val name = theirs ?: mine
            if (name != null) {
                val sum = fieldText(b, "Сумма")?.let(::leadingNumber) ?: 0.0
                val p = parseDayParts(fieldText(b, "Вернуть до"))
                val d = if (p?.y != null) safeDate(p.y, p.m, p.d) else null
                out.add(DueItem("debt", name, d, if (theirs != null) sum else -sum, n.id))
            }
        }
    }
    return out
}

fun daysText(next: LocalDate?, today: LocalDate): String {
    if (next == null) return ""
    val d = ChronoUnit.DAYS.between(today, next).toInt()
    return when {
        d == 0 -> "сегодня"
        d == 1 -> "завтра"
        d > 1 -> "через $d дн."
        d == -1 -> "вчера"
        else -> "${-d} дн. назад"
    }
}
