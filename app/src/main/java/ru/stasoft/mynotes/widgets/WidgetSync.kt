package ru.stasoft.mynotes.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import ru.stasoft.mynotes.data.DueItem
import ru.stasoft.mynotes.data.NoteEntity
import ru.stasoft.mynotes.data.displayTitle
import java.time.LocalDate

object WidgetSync {
    fun ids(ctx: Context): List<Int> {
        val m = AppWidgetManager.getInstance(ctx)
        return listOf(NotesWidget::class.java, QuickWidget::class.java).flatMap { c -> m.getAppWidgetIds(ComponentName(ctx, c)).toList() }
    }

    private val TABLE_SEP = Regex("""^\|[\s:\-|]+\|$""")
    private val STYLE = Regex("""\{\{[^}]*\}\}""")

    /** Текст заметки для виджета: таблицы без служебных символов, не длиннее ~700 знаков. */
    private fun plain(body: String): String =
        body.lines().mapNotNull { l ->
            val t = l.trim()
            when {
                TABLE_SEP.matches(t) -> null
                t.startsWith("|") -> STYLE.replace(t, "").trim('|', ' ').split('|').joinToString(" · ") { it.trim() }
                else -> l
            }
        }.joinToString("\n").trim().take(700)

    private fun firstLine(body: String) = plain(body).lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty().take(90)

    private fun kindName(k: String) = when (k) { "subs" -> "подписка"; "bday" -> "дата"; "warr" -> "гарантия"; else -> "долг" }

    /** Пересчитывает данные всех виджетов по заметкам и обновляет их на рабочем столе. Вызывается, пока приложение открыто. */
    fun refresh(ctx: Context, notes: List<NoteEntity>, dues: List<DueItem>) {
        val all = ids(ctx)
        if (all.isEmpty()) return
        if (!WidgetPrefs.contentOn(ctx)) {
            WidgetStore.clear(ctx)
            WidgetRender.updateAll(ctx)
            return
        }
        val byId = notes.associateBy { it.id }
        val snaps = HashMap<Int, WSnap>()
        val today = LocalDate.now()
        for (id in all) {
            val c = WidgetPrefs.get(ctx, id) ?: continue
            when (c.kind) {
                "folder" -> {
                    val f = if (c.daily) ru.stasoft.mynotes.data.DayFolder.find(notes, c.folderId, today) else c.folderId?.let { byId[it] }
                    var kids = if (c.daily) (if (f == null) emptyList() else notes.filter { it.parentId == f.id }) else notes.filter { it.parentId == c.folderId }
                    kids = if (c.newestFirst) kids.sortedByDescending { it.updatedAt } else kids.sortedBy { it.sortIndex }
                    val rows = kids.take(c.count).map { n -> WRow((if (n.isFolder) "📁 " else "") + n.displayTitle().ifBlank { "Без названия" }, firstLine(n.body), n.id) }
                    snaps[id] = WSnap(c.title.ifBlank { f?.displayTitle() ?: if (c.daily) "Сегодня" else "Заметки" }, "", rows, if (c.daily) today.toEpochDay() else null)
                }
                "note" -> {
                    val n = c.noteId?.let { byId[it] }
                    snaps[id] = if (n == null) WSnap(c.title.ifBlank { "Заметка" }, "Заметка не найдена", emptyList())
                    else WSnap(c.title.ifBlank { n.displayTitle().ifBlank { "Без названия" } }, plain(n.body).ifBlank { "(пусто)" }, listOf(WRow("", "", n.id)))
                }
                "dates" -> {
                    val soon = dues.filter { it.next != null && java.time.temporal.ChronoUnit.DAYS.between(today, it.next).let { d -> d in -3..90 } }
                        .sortedBy { it.next }.take(c.count)
                    snaps[id] = WSnap(c.title.ifBlank { "Ближайшие сроки" }, "", soon.map { WRow(it.name, kindName(it.kind), it.noteId, it.next!!.toEpochDay()) })
                }
            }
        }
        WidgetStore.save(ctx, snaps)
        WidgetRender.updateAll(ctx)
    }
}
