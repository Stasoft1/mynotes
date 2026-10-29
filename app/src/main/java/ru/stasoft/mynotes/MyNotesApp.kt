package ru.stasoft.mynotes

import android.app.Application
import ru.stasoft.mynotes.reminders.ReminderNotifier
import ru.stasoft.mynotes.reminders.ReminderScheduler

class MyNotesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Нативная библиотека SQLCipher должна быть загружена до первого открытия базы.
        System.loadLibrary("sqlcipher")
        try {
            ReminderNotifier.ensureChannel(this)
            ReminderScheduler.rescheduleAll(this)
        } catch (e: Exception) {
            android.util.Log.e("MyNotes", "reminder init failed", e)
        }
    }
}
