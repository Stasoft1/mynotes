package ru.stasoft.mynotes

import android.app.Application

class MyNotesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Нативная библиотека SQLCipher должна быть загружена до первого открытия базы.
        System.loadLibrary("sqlcipher")
    }
}
