package ru.stasoft.mynotes.reminders

import android.content.Context
import ru.stasoft.mynotes.data.NoteDao
import ru.stasoft.mynotes.data.NoteEntity
import ru.stasoft.mynotes.data.displayTitle

/** Связь базы и индекса уведомлений. */
object ReminderSync {
    /** Применяет к базе отметки «выполнено», сделанные из уведомлений, пока база была закрыта. */
    suspend fun applyPending(ctx: Context, dao: NoteDao) {
        val pending = ReminderStore.load(ctx).pending
        if (pending.isEmpty()) return
        for (p in pending) {
            val note = dao.get(p.id) ?: continue
            val due = note.reminderAt ?: continue
            val next = ReminderLogic.advance(due, note.reminderPeriod, p.doneAt)
            dao.update(note.copy(reminderAt = next ?: due, reminderDoneAt = p.doneAt))
        }
        ReminderStore.update(ctx) { it.copy(pending = emptyList()) }
    }

    /** Пересобирает индекс из заметок с напоминаниями, сохраняя, что уже показывалось. */
    fun rebuild(ctx: Context, notes: List<NoteEntity>) {
        ReminderStore.update(ctx) { data ->
            val old = data.entries.associateBy { it.id }
            val entries = notes.mapNotNull { n ->
                val due = n.reminderAt ?: return@mapNotNull null
                val prev = old[n.id]
                val same = prev != null && prev.dueAt == due
                IndexEntry(
                    id = n.id,
                    title = n.displayTitle(),
                    dueAt = due,
                    leadDays = minOf(n.reminderCheckDays, 364),
                    period = n.reminderPeriod,
                    lastFired = if (same) prev!!.lastFired else 0,
                    snoozeUntil = if (same) prev!!.snoozeUntil else 0,
                )
            }
            data.copy(entries = entries)
        }
        ReminderScheduler.rescheduleAll(ctx)
    }
}
