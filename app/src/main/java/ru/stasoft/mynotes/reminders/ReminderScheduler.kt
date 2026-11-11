package ru.stasoft.mynotes.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object ReminderScheduler {
    private fun floorFor(e: IndexEntry, now: Long): Long = maxOf(e.lastFired, now - ReminderLogic.DAY_MS)

    private fun alertsFor(e: IndexEntry): List<Long> {
        val times = ReminderLogic.alertTimes(e.dueAt, e.leadDays).toMutableList()
        if (e.snoozeUntil > 0) times.add(e.snoozeUntil)
        return times
    }

    private fun pendingIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 0, Intent(ctx, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Ставит один точный будильник на ближайшее уведомление из индекса. */
    fun rescheduleAll(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()
        val next = ReminderStore.load(ctx).entries
            .mapNotNull { e -> alertsFor(e).filter { it > floorFor(e, now) }.minOrNull() }
            .minOrNull()
        val pi = pendingIntent(ctx)
        if (next == null) {
            am.cancel(pi)
            return
        }
        val at = maxOf(next, now + 500)
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /** Срабатывает по будильнику: показывает всё, что подошло, и ставит следующий будильник. */
    fun fireDue(ctx: Context) {
        val now = System.currentTimeMillis()
        val toNotify = mutableListOf<IndexEntry>()
        ReminderStore.update(ctx) { data ->
            val updated = data.entries.map { e ->
                val due = alertsFor(e).filter { it > floorFor(e, now) && it <= now }
                if (due.isEmpty()) e else {
                    toNotify.add(e)
                    e.copy(lastFired = now, snoozeUntil = if (e.snoozeUntil in 1..now) 0 else e.snoozeUntil)
                }
            }
            // Свой интервал («каждые 3 часа») переходит на следующее срабатывание сам, без нажатия «Готово».
            val autoDone = ArrayList<PendingDone>()
            val rolled = updated.map { e ->
                if (ReminderLogic.isCustom(e.period) && toNotify.any { it.id == e.id } && e.dueAt <= now) {
                    val next = ReminderLogic.advance(e.dueAt, e.period, now)
                    if (next != null) {
                        autoDone.add(PendingDone(e.id, now))
                        e.copy(dueAt = next, lastFired = 0, snoozeUntil = 0)
                    } else e
                } else e
            }
            data.copy(entries = rolled, pending = data.pending.filter { p -> autoDone.none { it.id == p.id } } + autoDone)
        }
        toNotify.forEach { ReminderNotifier.show(ctx, it, now) }
        rescheduleAll(ctx)
    }
}
