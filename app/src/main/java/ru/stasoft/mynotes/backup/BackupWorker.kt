package ru.stasoft.mynotes.backup

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Периодическая копия по расписанию. Базу открывать не нужно: копируются зашифрованные файлы. */
class BackupWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result = try {
        BackupManager.backupNow(applicationContext, auto = true)
        Result.success()
    } catch (e: BackupManager.FolderWriteFailed) {
        Result.success() // локальная копия есть, ошибка папки записана в статус
    } catch (e: Exception) {
        BackupManager.prefs(applicationContext).edit()
            .putBoolean("last_ok", false).putString("last_msg", "Ошибка: ${e.message}")
            .putLong("last_time", System.currentTimeMillis()).apply()
        Result.retry()
    }
}

object BackupScheduler {
    private const val NAME = "auto-backup"

    fun enabled(ctx: Context) = BackupManager.prefs(ctx).getBoolean("auto", false)
    fun intervalHours(ctx: Context) = BackupManager.prefs(ctx).getInt("hours", 24)
    fun hour(ctx: Context) = BackupManager.prefs(ctx).getInt("hour", 3)

    /** Ставит или снимает расписание по сохранённым настройкам. */
    fun apply(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        if (!enabled(ctx)) {
            wm.cancelUniqueWork(NAME)
            return
        }
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour(ctx)); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
        }
        val req = PeriodicWorkRequestBuilder<BackupWorker>(intervalHours(ctx).toLong(), TimeUnit.HOURS)
            .setInitialDelay(next.timeInMillis - now.timeInMillis, TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
    }
}
