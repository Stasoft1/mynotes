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

    private fun occurrence(base: ZonedDateTime, period: Int, k: Long): ZonedDateTime = when (period) {
        1 -> base.plusWeeks(k)
        2 -> base.plusMonths(k)
        3 -> base.plusMonths(3 * k)
        4 -> base.plusMonths(6 * k)
        else -> base.plusYears(k)
    }

    /** Следующее срабатывание строго после [now] или null, если напоминание не повторяется. */
    fun advance(dueAt: Long, period: Int, now: Long): Long? {
        if (period !in 1..5) return null
        val base = zdt(dueAt)
        var k = 1L
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
