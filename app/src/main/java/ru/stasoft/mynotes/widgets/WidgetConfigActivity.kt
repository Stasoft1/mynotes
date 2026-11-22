package ru.stasoft.mynotes.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.net.Uri
import ru.stasoft.mynotes.MainActivity

/**
 * Окно выбора виджета вызывается лаунчером при добавлении. Данные зашифрованы, поэтому сама настройка
 * проходит внутри приложения после входа: здесь только подтверждаем добавление и открываем её.
 */
class WidgetConfigActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        startActivity(
            Intent(this, MainActivity::class.java).setAction(ACTION_CONFIG).setData(Uri.parse("mynotes://w/$id/cfg"))
                .putExtra("wid", id).putExtra("tk", WidgetPrefs.token(this))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }
}
