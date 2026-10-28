package ru.stasoft.mynotes.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import ru.stasoft.mynotes.data.NoteEntity

/** Строка статуса напоминания для списка: текст, дата и признак «пропущено». */
data class ReminderStatus(val headline: String, val date: String, val missed: Boolean, val done: Boolean)

private val dateFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM yyyy 'г.' (EEE)", Locale("ru"))

private fun plural(n: Long, one: String, few: String, many: String): String {
    val m100 = n % 100
    val m10 = n % 10
    return when {
        m100 in 11..14 -> many
        m10 == 1L -> one
        m10 in 2..4 -> few
        else -> many
    }
}

fun reminderStatus(note: NoteEntity, zone: ZoneId = ZoneId.systemDefault()): ReminderStatus? {
    val at = note.reminderAt ?: return null
    val day = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
    val date = dateFormat.format(day)
    val done = note.reminderDoneAt != null && note.reminderDoneAt >= at
    if (done) return ReminderStatus("Выполнено", date, missed = false, done = true)
    val days = ChronoUnit.DAYS.between(LocalDate.now(zone), day)
    return when {
        days < 0 -> ReminderStatus("Пропущено ${-days} ${plural(-days, "день", "дня", "дней")}", date, true, false)
        days == 0L -> ReminderStatus("Сегодня", date, false, false)
        else -> ReminderStatus("Через $days ${plural(days, "день", "дня", "дней")}", date, false, false)
    }
}
