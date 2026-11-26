package ru.stasoft.mynotes.modules

import android.content.Context
import androidx.compose.runtime.getValue
import ru.stasoft.mynotes.BuildConfig
import ru.stasoft.mynotes.data.EXTRA_MODULES
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Описание подключаемого модуля: необязательной функции, которую пользователь включает в Настройках. */
class ModuleInfo(val id: String, val title: String, val description: String, val category: String = "Прочее")

/** Разделы каталога модулей в порядке показа. Пустые разделы не показываются. */
val MODULE_CATEGORIES = listOf("Деньги и быт", "Дом и семья", "Здоровье и привычки", "Транспорт", "Время и место", "Голос и ИИ", "Прочее")

/**
 * Реестр модулей. Ядро (дерево, поиск, переменные, голосовой ввод по правилам, фото, копии, PDF, напоминания по времени)
 * работает всегда, а всё специальное включается здесь. Выключенный модуль нигде не виден: ни в меню, ни в настройках,
 * ни в редакторе. Состояние читается из Compose, поэтому интерфейс обновляется сразу.
 */
object Modules {
    private val everything = EXTRA_MODULES + listOf(
        ModuleInfo("budget", "Бюджет и расходы", "Заметки с полями «Сумма» и «Категория» собираются в отчёт по месяцам и категориям, с лимитами и уведомлением о перерасходе", "Деньги и быт"),
        ModuleInfo("subs", "Подписки и платежи", "Регулярные списания: когда следующее, сколько в месяц, уведомление за 2 дня", "Деньги и быт"),
        ModuleInfo("debt", "Долги", "Кто вам должен и кому должны вы, сроки возврата, итоги по людям", "Деньги и быт"),
        ModuleInfo("bday", "Дни рождения и даты", "Ежегодные даты из заметок: сколько дней осталось, возраст, уведомление за день и в день события", "Дом и семья"),
        ModuleInfo("util", "Коммунальные услуги", "Показания счётчиков, тарифы, расход и сумма по месяцам, напоминание передать показания", "Дом и семья"),
        ModuleInfo("warr", "Гарантии", "Сроки гарантии на покупки, уведомление за 30 и 7 дней до конца", "Дом и семья"),
        ModuleInfo("health", "Показатели здоровья", "Вес, давление, пульс, сахар и другие замеры из заметок: динамика, минимум и максимум, график; быстрый ввод замера", "Здоровье и привычки"),
        ModuleInfo("habits", "Привычки", "Ежедневные отметки, серии подряд и процент выполнения за 30 дней", "Здоровье и привычки"),
        ModuleInfo("auto", "Авто", "Машины, заправки, расход, ТО и ремонты с напоминаниями по пробегу и времени; все расходы на машину, ОСАГО, техосмотр и шины", "Транспорт"),
        ModuleInfo("day", "Папка дня", "Каждый новый день сама создаёт папку с датой и заметки из шаблонов", "Время и место"),
        ModuleInfo("geo", "Напоминания по месту", "Уведомление, когда вы приходите в выбранное место или уходите, в нужные часы и дни; списки покупок по магазину", "Время и место"),
        ModuleInfo("route", "Маршрут к адресу", "Кнопка «Маршрут» для заметок с адресом", "Время и место"),
        ModuleInfo("giga", "ИИ-разбор голоса (GigaChat)", "Непонятные голосовые фразы разбирает GigaChat; нужен свой ключ", "Голос и ИИ"),
    )

    /** Модули этой редакции: «Заявки Ф1» есть только в personal. */
    val all: List<ModuleInfo> = everything

    private const val PREFS = "modules"
    private var enabled by mutableStateOf<Set<String>>(emptySet())
    private var ready = false

    /** personal: включено всё, чтобы ничего не пропало; public: чистая установка без модулей, пользователь включает нужные сам. */
    private val DEFAULT_ON: Set<String> = if (BuildConfig.PERSONAL) all.map { it.id }.toSet() else emptySet()

    fun init(ctx: Context) {
        if (ready) return
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        enabled = if (p.contains("on")) p.getStringSet("on", emptySet())!!.toSet() else DEFAULT_ON
        // Папка дня стала модулем позже: в личной редакции у владельца она должна остаться включённой (один раз).
        if (BuildConfig.PERSONAL && !p.getBoolean("day_migrated", false)) {
            enabled = enabled + "day"
            p.edit().putStringSet("on", enabled).putBoolean("day_migrated", true).apply()
        }
        ready = true
    }

    fun on(id: String): Boolean = id in enabled && (id != "f1" || BuildConfig.PERSONAL)

    fun set(ctx: Context, id: String, on: Boolean) {
        enabled = if (on) enabled + id else enabled - id
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet("on", enabled).apply()
    }
}
