package ru.stasoft.mynotes.reminders

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.fireDue(context)
    }
}

/** Кнопки «Готово», «Через час», «Завтра» в уведомлении. */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("id", -1)
        val action = intent.getStringExtra("action") ?: return
        if (id < 0) return
        val now = System.currentTimeMillis()
        ReminderStore.update(context) { data ->
            val entry = data.entries.firstOrNull { it.id == id } ?: return@update data
            when (action) {
                "done" -> {
                    val next = ReminderLogic.advance(entry.dueAt, entry.period, now)
                    val entries = data.entries.filter { it.id != id } +
                        listOfNotNull(next?.let { entry.copy(dueAt = it, lastFired = 0, snoozeUntil = 0) })
                    data.copy(entries = entries, pending = data.pending + PendingDone(id, now))
                }
                "hour" -> data.copy(entries = data.entries.map {
                    if (it.id == id) it.copy(snoozeUntil = now + 60 * 60 * 1000) else it
                })
                "tomorrow" -> data.copy(entries = data.entries.map {
                    if (it.id == id) it.copy(snoozeUntil = ReminderLogic.tomorrowNine(now)) else it
                })
                else -> data
            }
        }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(id.toInt())
        ReminderScheduler.rescheduleAll(context)
    }
}

/** После перезагрузки, смены времени или обновления приложения будильник нужно поставить заново. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.rescheduleAll(context)
    }
}
