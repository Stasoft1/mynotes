package ru.stasoft.mynotes.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import ru.stasoft.mynotes.MainActivity
import ru.stasoft.mynotes.R
import ru.stasoft.mynotes.data.daysText
import java.time.LocalDate

const val ACTION_OPEN = "ru.stasoft.mynotes.widget.OPEN"
const val ACTION_NEW = "ru.stasoft.mynotes.widget.NEW"
const val ACTION_CONFIG = "ru.stasoft.mynotes.widget.CONFIG"

class NotesWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, m: AppWidgetManager, ids: IntArray) = ids.forEach { WidgetRender.update(ctx, m, it) }
    override fun onDeleted(ctx: Context, ids: IntArray) = ids.forEach { WidgetPrefs.remove(ctx, it) }
}

class QuickWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, m: AppWidgetManager, ids: IntArray) = ids.forEach { WidgetRender.update(ctx, m, it) }
    override fun onDeleted(ctx: Context, ids: IntArray) = ids.forEach { WidgetPrefs.remove(ctx, it) }
}

object WidgetRender {
    private fun pi(ctx: Context, i: Intent): PendingIntent =
        PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun base(ctx: Context, action: String, uri: String) = Intent(ctx, MainActivity::class.java)
        .setAction(action).setData(Uri.parse(uri)).putExtra("tk", WidgetPrefs.token(ctx))

    fun openNote(ctx: Context, wid: Int, id: Long) = pi(ctx, base(ctx, ACTION_OPEN, "mynotes://w/$wid/open/$id").putExtra("note", id))
    fun newNote(ctx: Context, wid: Int, slot: Int, folder: Long?, template: String, title: String = "", daily: Boolean = false, snippet: Long? = null) =
        pi(ctx, base(ctx, ACTION_NEW, "mynotes://w/$wid/new/$slot").putExtra("folder", folder ?: -1L).putExtra("tpl", template).putExtra("title", title).putExtra("daily", daily).putExtra("snip", snippet ?: -1L))
    fun configure(ctx: Context, wid: Int) = pi(ctx, base(ctx, ACTION_CONFIG, "mynotes://w/$wid/cfg").putExtra("wid", wid))

    fun updateAll(ctx: Context) {
        val m = AppWidgetManager.getInstance(ctx)
        WidgetSync.ids(ctx).forEach { update(ctx, m, it) }
    }

    private class Colors(val bg: Int, val text: Int, val sub: Int)

    private fun colors(ctx: Context, theme: Int): Colors {
        val night = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val dark = when (theme) { 1 -> false; 2, 3 -> true; else -> night }
        val bg = when { theme == 3 -> R.drawable.widget_bg_glass; dark -> R.drawable.widget_bg_dark; else -> R.drawable.widget_bg_light }
        return if (dark) Colors(bg, 0xFFF2F2F2.toInt(), 0xFFB0B3B8.toInt()) else Colors(bg, 0xFF1B1B1F.toInt(), 0xFF5F6368.toInt())
    }

    fun update(ctx: Context, m: AppWidgetManager, wid: Int) {
        val quickProvider = m.getAppWidgetInfo(wid)?.provider?.className?.endsWith("QuickWidget") == true
        val cfg = WidgetPrefs.get(ctx, wid)
        m.updateAppWidget(wid, if (quickProvider) quick(ctx, wid, cfg) else main(ctx, wid, cfg))
    }

    private fun quick(ctx: Context, wid: Int, cfg: WidgetCfg?): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_quick)
        val c = colors(ctx, cfg?.theme ?: 0)
        rv.setInt(R.id.root, "setBackgroundResource", c.bg)
        val btns = cfg?.quick?.filter { it.label.isNotBlank() }.orEmpty().take(3)
        val ids = intArrayOf(R.id.q0, R.id.q1, R.id.q2)
        ids.forEach { rv.setViewVisibility(it, View.GONE) }
        if (btns.isEmpty()) {
            rv.setViewVisibility(R.id.empty, View.VISIBLE)
            rv.setTextViewText(R.id.empty, "Нажмите, чтобы настроить кнопки")
            rv.setTextColor(R.id.empty, c.text)
            rv.setOnClickPendingIntent(R.id.root, configure(ctx, wid))
            return rv
        }
        rv.setViewVisibility(R.id.empty, View.GONE)
        btns.forEachIndexed { i, b ->
            rv.setViewVisibility(ids[i], View.VISIBLE)
            rv.setTextViewText(ids[i], b.label)
            rv.setTextColor(ids[i], c.text)
            rv.setOnClickPendingIntent(ids[i], newNote(ctx, wid, i, b.folderId, b.template, b.label, b.daily, b.snippetId))
        }
        return rv
    }

    private fun main(ctx: Context, wid: Int, cfg: WidgetCfg?): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_main)
        val c = colors(ctx, cfg?.theme ?: 0)
        rv.setInt(R.id.root, "setBackgroundResource", c.bg)
        rv.setTextColor(R.id.title, c.text)
        rv.setTextColor(R.id.empty, c.sub)
        rv.setTextColor(R.id.text, c.text)
        rv.setInt(R.id.add, "setColorFilter", c.text)
        val rowIds = listOf(R.id.row0, R.id.row1, R.id.row2, R.id.row3, R.id.row4, R.id.row5)
        val tIds = listOf(R.id.rt0, R.id.rt1, R.id.rt2, R.id.rt3, R.id.rt4, R.id.rt5)
        val sIds = listOf(R.id.rs0, R.id.rs1, R.id.rs2, R.id.rs3, R.id.rs4, R.id.rs5)
        rowIds.forEach { rv.setViewVisibility(it, View.GONE) }
        rv.setViewVisibility(R.id.text, View.GONE)
        rv.setViewVisibility(R.id.empty, View.GONE)
        rv.setViewVisibility(R.id.add, View.GONE)
        if (cfg == null) {
            rv.setTextViewText(R.id.title, "МоиЗаметки")
            rv.setViewVisibility(R.id.empty, View.VISIBLE)
            rv.setTextViewText(R.id.empty, "Нажмите, чтобы настроить виджет")
            rv.setOnClickPendingIntent(R.id.root, configure(ctx, wid))
            return rv
        }
        val k = when (cfg.size) { 0 -> 0.85f; 2 -> 1.2f; else -> 1f }
        val snap = if (WidgetPrefs.contentOn(ctx)) WidgetStore.load(ctx)[wid] else null
        rv.setTextViewText(R.id.title, snap?.title ?: cfg.title.ifBlank { "МоиЗаметки" })
        if (cfg.kind == "folder" && cfg.add) {
            rv.setViewVisibility(R.id.add, View.VISIBLE)
            rv.setOnClickPendingIntent(R.id.add, newNote(ctx, wid, 9, cfg.folderId, cfg.template, "", cfg.daily, cfg.snippetId))
        }
        rv.setOnClickPendingIntent(R.id.title, configure(ctx, wid))
        if (snap == null) {
            rv.setViewVisibility(R.id.empty, View.VISIBLE)
            rv.setTextViewText(R.id.empty, if (WidgetPrefs.contentOn(ctx)) "Откройте приложение — виджет обновится" else "Показ заметок на рабочем столе выключен")
            rv.setOnClickPendingIntent(R.id.empty, pi(ctx, Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_MAIN)))
            return rv
        }
        if (cfg.kind == "note") {
            rv.setViewVisibility(R.id.text, View.VISIBLE)
            rv.setTextViewText(R.id.text, snap.text)
            rv.setTextViewTextSize(R.id.text, TypedValue.COMPLEX_UNIT_SP, 13f * k)
            snap.rows.firstOrNull()?.let { rv.setOnClickPendingIntent(R.id.text, openNote(ctx, wid, it.id)) }
            return rv
        }
        if (snap.rows.isEmpty()) {
            rv.setViewVisibility(R.id.empty, View.VISIBLE)
            rv.setTextViewText(R.id.empty, if (cfg.kind == "dates") "Ближайших сроков нет" else if (cfg.daily) "За сегодня записей пока нет" else "В папке пусто")
            return rv
        }
        val today = LocalDate.now()
        if (cfg.daily && snap.day != today.toEpochDay()) {
            rv.setViewVisibility(R.id.empty, View.VISIBLE)
            rv.setTextViewText(R.id.empty, "Новый день — откройте приложение, виджет обновится")
            return rv
        }
        snap.rows.take(cfg.count).forEachIndexed { i, r ->
            rv.setViewVisibility(rowIds[i], View.VISIBLE)
            rv.setTextViewText(tIds[i], r.title)
            rv.setTextColor(tIds[i], c.text)
            rv.setTextViewTextSize(tIds[i], TypedValue.COMPLEX_UNIT_SP, 14f * k)
            val sub = if (r.day != null) daysText(LocalDate.ofEpochDay(r.day), today) + (if (r.sub.isNotEmpty()) " · " + r.sub else "") else r.sub
            rv.setTextViewText(sIds[i], sub)
            rv.setTextColor(sIds[i], c.sub)
            rv.setTextViewTextSize(sIds[i], TypedValue.COMPLEX_UNIT_SP, 11f * k)
            rv.setViewVisibility(sIds[i], if (sub.isBlank()) View.GONE else View.VISIBLE)
            rv.setOnClickPendingIntent(rowIds[i], openNote(ctx, wid, r.id))
        }
        return rv
    }
}
