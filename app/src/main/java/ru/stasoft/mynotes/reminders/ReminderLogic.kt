package ru.stasoft.mynotes.reminders

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Правила напоминаний. Дата в заметке — это ТЕКУЩЕЕ срабатывание. Когда его отметили выполненным,
 * повторяющееся напоминание само переходит на следующую дату после «сейчас».
 * Периоды: 1 — неделя, 2 — месяц, 3 — квартал, 4 — полгода, 5 — год (смысл взят из кода MyTreeNotes).
 */
object ReminderLogic {
    private val NINE = LocalTime.of(9, 0)
    const val DAY_MS = 24L * 60 * 60 * 1000

    private fun zdt(ms: Long, zone: ZoneId = ZoneId.systemDefault()): ZonedDateTime =
        Instant.ofEpochMilli(ms).atZone(zone)

    // Свой интервал хранится в reminderPeriod: 1_000_000 + единица * 10_000 + число (единицы: 1 минуты, 2 часы, 3 дни, 4 недели, 5 месяцы, 6 годы).
    const val CUSTOM_BASE = 1_000_000
    fun custom(unit: Int, n: Int): Int = CUSTOM_BASE + unit * 10_000 + n.coerceIn(1, 9999)
    fun isCustom(p: Int): Boolean = p >= CUSTOM_BASE
    fun customUnit(p: Int): Int = (p - CUSTOM_BASE) / 10_000
    fun customCount(p: Int): Int = (p - CUSTOM_BASE) % 10_000
    fun isRepeating(p: Int): Boolean = p in 1..5 || (isCustom(p) && customUnit(p) in 1..6)

    private val LEGACY = listOf("Не повторять", "Каждую неделю", "Каждый месяц", "Раз в 3 месяца", "Раз в полгода", "Раз в год")
    private val FORMS = listOf(
        Triple("минуту", "минуты", "минут"), Triple("час", "часа", "часов"), Triple("день", "дня", "дней"),
        Triple("неделю", "недели", "недель"), Triple("месяц", "месяца", "месяцев"), Triple("год", "года", "лет"),
    )

    /** Подпись повтора: «Каждую неделю», «Каждые 14 дней», «Каждые 3 часа». */
    fun periodLabel(p: Int): String {
        if (!isCustom(p)) return LEGACY.getOrElse(p) { LEGACY[0] }
        val u = customUnit(p)
        val n = customCount(p)
        if (u !in 1..6) return LEGACY[0]
        val f = FORMS[u - 1]
        if (n == 1) return "Каждую ${f.first}".replace("Каждую час", "Каждый час").replace("Каждую день", "Каждый день")
            .replace("Каждую месяц", "Каждый месяц").replace("Каждую год", "Каждый год")
        val m100 = n % 100
        val m10 = n % 10
        val word = when {
            m100 in 11..14 -> f.third
            m10 == 1 -> f.first
            m10 in 2..4 -> f.second
            else -> f.third
        }
        return "Каждые $n $word"
    }

    private fun occurrence(base: ZonedDateTime, period: Int, k: Long): ZonedDateTime = when (period) {
        1 -> base.plusWeeks(k)
        2 -> base.plusMonths(k)
        3 -> base.plusMonths(3 * k)
        4 -> base.plusMonths(6 * k)
        5 -> base.plusYears(k)
        else -> {
            val n = customCount(period).toLong() * k
            when (customUnit(period)) {
                1 -> base.plusMinutes(n)
                2 -> base.plusHours(n)
                3 -> base.plusDays(n)
                4 -> base.plusWeeks(n)
                5 -> base.plusMonths(n)
                else -> base.plusYears(n)
            }
        }
    }

    /** Следующее срабатывание строго после [now] или null, если напоминание не повторяется. */
    fun advance(dueAt: Long, period: Int, now: Long): Long? {
        if (!isRepeating(period)) return null
        val base = zdt(dueAt)
        var k = 1L
        // Минуты и часы — точными шагами, чтобы не перебирать тысячи значений.
        if (isCustom(period) && customUnit(period) in 1..2) {
            val step = customCount(period) * (if (customUnit(period) == 1) 60_000L else 3_600_000L)
            return if (dueAt > now) dueAt + step else dueAt + ((now - dueAt) / step + 1) * step
        }
        while (k < 100_000) {
            val t = occurrence(base, period, k).toInstant().toEpochMilli()
            if (t > now) return t
            k++
        }
        return null
    }

    /** Момент срабатывания: если время не задано (полночь), то 9:00. */
    fun effectiveDue(dueAt: Long): Long {
        val d = zdt(dueAt)
        return if (d.toLocalTime() == LocalTime.MIDNIGHT) d.with(NINE).toInstant().toEpochMilli() else dueAt
    }

    /** Моменты уведомлений: за [leadDays] дней (в 9:00) и в момент срабатывания. */
    fun alertTimes(dueAt: Long, leadDays: Int): List<Long> {
        val eff = zdt(effectiveDue(dueAt))
        val list = mutableListOf<Long>()
        if (leadDays > 0) list.add(eff.minusDays(leadDays.toLong()).with(NINE).toInstant().toEpochMilli())
        list.add(eff.toInstant().toEpochMilli())
        return list
    }

    /** Разница в календарных днях от сегодняшней даты до даты срабатывания (отрицательная — пропущено). */
    fun daysUntil(dueAt: Long, now: Long): Long =
        ChronoUnit.DAYS.between(zdt(now).toLocalDate(), zdt(effectiveDue(dueAt)).toLocalDate())

    fun tomorrowNine(now: Long): Long =
        zdt(now).toLocalDate().plusDays(1).atTime(NINE).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun ruDays(n: Long): String {
        val a = Math.abs(n)
        val m100 = a % 100
        val m10 = a % 10
        val word = when {
            m100 in 11..14 -> "дней"
            m10 == 1L -> "день"
            m10 in 2..4 -> "дня"
            else -> "дней"
        }
        return "$a $word"
    }
}
