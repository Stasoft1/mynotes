package ru.stasoft.mynotes.data

import android.content.Context

private class G(val key: String, val title: String, val src: String, val kids: List<G> = emptyList(), val folder: Boolean = false)

private fun g(key: String, title: String) = G(key, title, "guide/$key.md")
private fun h(key: String, title: String) = G(key, title, "help/$key.md")

private val GUIDE_ROOT = G(
    "root", "Руководство", "", folder = true,
    kids = listOf(
        g("start", "Оглавление"),
        g("quick", "Быстрый старт за 10 минут"),
        g("tree", "Папки и заметки"),
        g("editor", "Редактор заметки"),
        h("links", "Ссылки между заметками"),
        h("templates", "Шаблоны"),
        h("vars", "Переменные и подсчёты"),
        g("reports", "Отчёты и проверка показаний"),
        g("search", "Поиск"),
        g("reminders", "Напоминания"),
        h("voice", "Голосовой ввод"),
        g("photo", "Фото и PDF"),
        g("hints", "Подсказки значений полей"),
        g("security", "Пароль и безопасность"),
        g("backup", "Резервные копии"),
        g("look", "Внешний вид и настройки"),
        g("faq", "Частые вопросы"),
        G(
            "modules_dir", "Модули", "", folder = true,
            kids = listOf(
                g("modules", "Обзор модулей"),
                h("budget", "Бюджет и расходы"), h("subs", "Подписки и платежи"), h("debt", "Долги"), h("util", "Коммунальные услуги"),
                h("bday", "Дни рождения и даты"), h("warr", "Гарантии"), h("health", "Показатели здоровья"), h("habits", "Привычки"),
                h("auto", "Авто"), h("day", "Папка дня"), h("geo", "Напоминания по месту"), h("route", "Маршрут к адресу"),
                h("giga", "ИИ-разбор голоса (GigaChat)"),
            ),
        ),
    ),
)

/** Есть ли в корне базы папка «Руководство». */
suspend fun hasGuide(dao: NoteDao): Boolean = dao.childrenOnce(null).any { it.isFolder && it.title == GUIDE_ROOT.title }

/**
 * Создаёт в корне базы папку «Руководство»: заметки с текстом справки и ссылками между ними.
 * Возвращает id созданной папки.
 */
suspend fun seedGuide(ctx: Context, dao: NoteDao): Long {
    val now = System.currentTimeMillis()
    val ids = HashMap<String, Pair<Long, String>>()
    val todo = ArrayList<Pair<G, Long>>()
    suspend fun create(n: G, parent: Long?, index: Int): Long {
        val id = dao.insert(NoteEntity(parentId = parent, isFolder = n.folder, title = n.title, sortIndex = index, createdAt = now, updatedAt = now))
        ids[n.key] = id to n.title
        if (n.src.isNotEmpty()) todo.add(n to id)
        n.kids.forEachIndexed { i, k -> create(k, id, i) }
        return id
    }
    val rootId = create(GUIDE_ROOT, null, dao.maxSort(null) + 1)
    val startId = ids["start"]?.first
    for ((n, id) in todo) {
        val md = try { ctx.assets.open(n.src).bufferedReader(Charsets.UTF_8).use { it.readText() } } catch (_: Exception) { continue }
        val note = markdownToNote(md) { key -> ids[key] }
        var text = note.text
        if (n.key != "start" && startId != null) text += "\n\n← [[$startId|Оглавление]]"
        val cur = dao.get(id) ?: continue
        dao.update(cur.copy(body = text, spansJson = spansToJson(note.spans), updatedAt = now))
    }
    return rootId
}
