package ru.stasoft.mynotes.reminders

import java.util.Calendar

/** Временное окно и дни недели для напоминания по месту. Минуты считаются от полуночи, дни — битами пн = 1 … вс = 64. */
object GeoWindow {
    fun hasWindow(from: Int, to: Int, days: Int) = from >= 0 || to >= 0 || days != 0

    private fun dayIndex(cal: Calendar) = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // пн = 0

    private fun dayOk(days: Int, d: Int) = days == 0 || (days shr d) and 1 == 1

    /** Открыто ли окно в момент [now]. Окно через полночь («22–02») относится ко дню, в который началось. */
    fun isOpen(from: Int, to: Int, days: Int, now: Long): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val t = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val d = dayIndex(cal)
        return when {
            from < 0 && to < 0 -> dayOk(days, d)
            from < 0 -> t <= to && dayOk(days, d)
            to < 0 -> t >= from && dayOk(days, d)
            from <= to -> t in from..to && dayOk(days, d)
            else -> (t >= from && dayOk(days, d)) || (t <= to && dayOk(days, (d + 6) % 7))
        }
    }

    /** Ближайшее открытие окна после [now] или null, если окно нет смысла отслеживать. */
    fun nextOpen(from: Int, to: Int, days: Int, now: Long): Long? {
        if (!hasWindow(from, to, days)) return null
        val start = if (from >= 0) from else 0
        for (add in 0..7) {
            val cal = Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, add)
                set(Calendar.HOUR_OF_DAY, start / 60)
                set(Calendar.MINUTE, start % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis > now && dayOk(days, dayIndex(cal))) return cal.timeInMillis
        }
        return null
    }

    fun hhmm(min: Int) = String.format("%02d:%02d", min / 60, min % 60)

    private val DAY_NAMES = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")

    fun daysText(days: Int): String = when {
        days == 0 || days == 127 -> ""
        days == 31 -> "по будням"
        days == 96 -> "по выходным"
        else -> DAY_NAMES.filterIndexed { i, _ -> (days shr i) and 1 == 1 }.joinToString(", ")
    }

    /** Краткое описание условий для строки в заметке и окне настройки. */
    fun summary(on: Int, from: Int, to: Int, days: Int): String {
        val parts = ArrayList<String>()
        parts.add(if (on == 1) "при уходе" else "при приходе")
        when {
            from >= 0 && to >= 0 -> parts.add("с ${hhmm(from)} до ${hhmm(to)}")
            from >= 0 -> parts.add("после ${hhmm(from)}")
            to >= 0 -> parts.add("до ${hhmm(to)}")
        }
        daysText(days).takeIf { it.isNotEmpty() }?.let { parts.add(it) }
        return parts.joinToString(" · ")
    }
}
