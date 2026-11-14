package ru.stasoft.mynotes.modules

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Описание подключаемого модуля: необязательной функции, которую пользователь включает в Настройках. */
class ModuleInfo(val id: String, val title: String, val description: String)

/**
 * Реестр модулей. Ядро (дерево, поиск, переменные, голосовой ввод по правилам, фото, копии, PDF, напоминания по времени)
 * работает всегда, а всё специальное включается здесь. Выключенный модуль нигде не виден: ни в меню, ни в настройках,
 * ни в редакторе. Состояние читается из Compose, поэтому интерфейс обновляется сразу.
 */
object Modules {
    val all = listOf(
        ModuleInfo("auto", "Авто", "Машины, заправки, расход, ТО и ремонты с напоминаниями по пробегу и времени"),
        ModuleInfo("day", "Папка дня", "Каждый новый день сама создаёт папку с датой и заметки из шаблонов"),
        ModuleInfo("geo", "Напоминания по месту", "Уведомление, когда вы приходите в выбранное место на карте; списки покупок по магазину"),
        ModuleInfo("route", "Маршрут к адресу", "Кнопка «Маршрут» для заметок с адресом"),
        ModuleInfo("f1", "Заявки Ф1", "Приведение скопированной заявки к шаблону Ф1"),
        ModuleInfo("giga", "ИИ-разбор голоса (GigaChat)", "Непонятные голосовые фразы разбирает GigaChat; нужен свой ключ"),
    )

    private const val PREFS = "modules"
    private var enabled by mutableStateOf<Set<String>>(emptySet())
    private var ready = false

    /** Для новой установки по умолчанию включено всё, чтобы ничего не пропало; публичная сборка начинает с пустого набора. */
    private val DEFAULT_ON = all.map { it.id }.toSet()

    fun init(ctx: Context) {
        if (ready) return
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        enabled = if (p.contains("on")) p.getStringSet("on", emptySet())!!.toSet() else DEFAULT_ON
        ready = true
    }

    fun on(id: String): Boolean = id in enabled

    fun set(ctx: Context, id: String, on: Boolean) {
        enabled = if (on) enabled + id else enabled - id
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet("on", enabled).apply()
    }
}
