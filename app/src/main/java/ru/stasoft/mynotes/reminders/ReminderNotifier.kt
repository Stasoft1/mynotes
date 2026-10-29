package ru.stasoft.mynotes.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import ru.stasoft.mynotes.MainActivity
import ru.stasoft.mynotes.R

object ReminderNotifier {
    private const val CHANNEL = "reminders"

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) == null) {
            val ch = NotificationChannel(CHANNEL, "Напоминания", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "Напоминания о заметках и днях рождения"
            nm.createNotificationChannel(ch)
        }
    }

    private fun actionIntent(ctx: Context, id: Long, action: String): PendingIntent {
        val i = Intent(ctx, ReminderActionReceiver::class.java)
            .setData(Uri.parse("mynotes://reminder/$id/$action"))
            .putExtra("id", id)
            .putExtra("action", action)
        return PendingIntent.getBroadcast(ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun show(ctx: Context, e: IndexEntry, now: Long) {
        ensureChannel(ctx)
        val days = ReminderLogic.daysUntil(e.dueAt, now)
        val text = when {
            days == 0L -> "Сегодня"
            days > 0 -> "Через ${ReminderLogic.ruDays(days)}"
            else -> "Пропущено ${ReminderLogic.ruDays(days)}"
        }
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // На заблокированном экране название скрыто.
        val publicVersion = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle("Напоминание")
            .build()
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(e.title.ifBlank { "Напоминание" })
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "Готово", actionIntent(ctx, e.id, "done"))
            .addAction(0, "Через час", actionIntent(ctx, e.id, "hour"))
            .addAction(0, "Завтра", actionIntent(ctx, e.id, "tomorrow"))
            .build()
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            nm.notify(e.id.toInt(), n)
        } catch (_: SecurityException) {
            // Нет разрешения на уведомления.
        }
    }
}
