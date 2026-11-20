package ru.stasoft.mynotes.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File

/** Блок для PDF: заголовок (с уровнем вложенности) или текст заметки. */
class PdfBlock(val text: String, val level: Int, val table: PdfTable? = null)

/** Таблица внутри заметки: ячейки в «сыром» виде (с метками оформления) и выравнивание столбцов. */
class PdfTable(val rows: List<List<String>>, val aligns: List<Int>)

/** Рисует блоки в многостраничный A4 PDF: заголовки папок и заметок выделены, длинные тексты переносятся по страницам. */
object PdfExport {
    private const val W = 595
    private const val H = 842
    private const val M = 42

    fun write(ctx: Context, name: String, blocks: List<PdfBlock>): File {
        val doc = PdfDocument()
        val width = W - 2 * M
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = Color.rgb(30, 30, 30) }
        val h1 = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(20, 70, 140) }
        val h2 = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13.5f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(20, 20, 20) }
        val foot = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = Color.GRAY }
        val line = Paint().apply { color = Color.rgb(200, 210, 225); strokeWidth = 0.8f }
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var canvas: android.graphics.Canvas? = null
        var y = 0f
        fun newPage() {
            page?.let { p ->
                canvas!!.drawText("${name} · стр. $pageNo", M.toFloat(), H - 20f, foot)
                doc.finishPage(p)
            }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            canvas = page!!.canvas
            y = M.toFloat()
        }
        newPage()
        fun layout(t: String, p: TextPaint, w: Int) =
            StaticLayout.Builder.obtain(t, 0, t.length, p, w).setLineSpacing(2f, 1f).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
        val grid = Paint().apply { color = Color.rgb(170, 175, 185); strokeWidth = 0.6f; style = Paint.Style.STROKE }
        val fill = Paint().apply { style = Paint.Style.FILL }
        fun cellPaint(st: ru.stasoft.mynotes.data.CellSt, header: Boolean): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f * floatArrayOf(1f, 0.85f, 1.3f, 1.6f)[st.size]
            val fam = when (st.font) { 1 -> Typeface.SERIF; 2 -> Typeface.MONOSPACE; else -> Typeface.DEFAULT }
            val sty = (if (st.b || header) Typeface.BOLD else 0) or (if (st.i) Typeface.ITALIC else 0)
            typeface = Typeface.create(fam, sty)
            isUnderlineText = st.u
            color = if (st.color >= 0) Color.rgb((st.color shr 16) and 255, (st.color shr 8) and 255, st.color and 255) else Color.rgb(30, 30, 30)
        }
        fun num(v: Double): String =
            if (v == Math.floor(v) && Math.abs(v) < 1e12) String.format(java.util.Locale("ru"), "%,.0f", v).replace('\u00A0', ' ')
            else String.format(java.util.Locale("ru"), "%,.2f", v).replace('\u00A0', ' ')
        fun drawTable(t: PdfTable) {
            if (t.rows.isEmpty()) return
            val ncol = t.rows[0].size
            val pad = 4f
            val parsed = t.rows.map { r -> r.map { ru.stasoft.mynotes.data.parseCell(it) } }
            val sums = ru.stasoft.mynotes.data.tableSums(t.rows)
            val numeric = sums.map { it != null }
            // Строка итогов добавляется, если есть числовые столбцы.
            val all = ArrayList(parsed)
            if (sums.any { it != null }) all.add(
                List(ncol) { c -> ru.stasoft.mynotes.data.CellSt(b = true) to (sums.getOrNull(c)?.let { num(it) } ?: if (c == 0) "Итого" else "") },
            )
            val nat = FloatArray(ncol) { c ->
                all.mapIndexed { ri, r -> r.getOrNull(c)?.let { (st, tx) -> cellPaint(st, ri == 0).measureText(tx) } ?: 0f }.max() + 2 * pad + 2f
            }
            val total = nat.sum()
            val k = minOf(width / total, 1.5f)
            val cw = FloatArray(ncol) { nat[it] * k }
            fun align(c: Int): Layout.Alignment = when (t.aligns.getOrElse(c) { 0 }) {
                1 -> Layout.Alignment.ALIGN_NORMAL
                2 -> Layout.Alignment.ALIGN_CENTER
                3 -> Layout.Alignment.ALIGN_OPPOSITE
                else -> if (numeric.getOrElse(c) { false }) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL
            }
            fun layouts(ri: Int): List<StaticLayout> = all[ri].mapIndexed { c, (st, tx) ->
                val p = cellPaint(st, ri == 0)
                val w = maxOf(8, (cw[c] - 2 * pad).toInt())
                StaticLayout.Builder.obtain(tx.ifEmpty { " " }, 0, tx.length.coerceAtLeast(1).coerceAtMost(tx.ifEmpty { " " }.length), p, w)
                    .setAlignment(align(c)).build()
            }
            fun drawRow(ri: Int, ls: List<StaticLayout>, top: Float) {
                val h = ls.maxOf { it.height } + 2 * pad
                var x = M.toFloat()
                val isSum = ri == all.size - 1 && all.size > parsed.size
                for (c in 0 until ncol) {
                    val st = all[ri][c].first
                    fill.color = when {
                        st.bg >= 0 -> Color.rgb((st.bg shr 16) and 255, (st.bg shr 8) and 255, st.bg and 255)
                        ri == 0 -> Color.rgb(228, 233, 243)
                        isSum -> Color.rgb(222, 230, 246)
                        ri % 2 == 0 -> Color.rgb(246, 247, 250)
                        else -> Color.WHITE
                    }
                    canvas!!.drawRect(x, top, x + cw[c], top + h, fill)
                    canvas!!.drawRect(x, top, x + cw[c], top + h, grid)
                    canvas!!.save(); canvas!!.translate(x + pad, top + pad); ls[c].draw(canvas!!); canvas!!.restore()
                    x += cw[c]
                }
            }
            y += 4f
            val headLs = layouts(0)
            for (ri in all.indices) {
                val ls = if (ri == 0) headLs else layouts(ri)
                val h = ls.maxOf { it.height } + 2 * pad
                if (y + h > H - M) {
                    newPage()
                    if (ri > 0) { // на новой странице повторяем шапку
                        drawRow(0, headLs, y)
                        y += headLs.maxOf { it.height } + 2 * pad
                    }
                }
                drawRow(ri, ls, y)
                y += h
            }
            y += 10f
        }
        for (b in blocks) {
            if (b.table != null) {
                if (y + 60 > H - M) newPage()
                drawTable(b.table)
                continue
            }
            val heading = b.level > 0
            val indent = if (heading) 0 else 0
            val paint = if (!heading) body else if (b.level == 1) h1 else h2
            val l = layout(b.text.ifEmpty { " " }, paint, width - indent)
            if (heading) {
                if (y + l.height + 40 > H - M) newPage()
                else if (y > M) y += if (b.level == 1) 14f else 8f
                canvas!!.save(); canvas!!.translate(M.toFloat(), y); l.draw(canvas!!); canvas!!.restore()
                y += l.height + 3f
                canvas!!.drawLine(M.toFloat(), y, (W - M).toFloat(), y, line)
                y += 6f
            } else {
                // Длинный текст режем по строкам, чтобы он переходил на следующую страницу.
                var from = 0
                while (from < l.lineCount) {
                    val room = H - M - y
                    var to = from
                    while (to < l.lineCount && l.getLineBottom(to) - l.getLineTop(from) <= room) to++
                    if (to == from) { newPage(); continue }
                    val top = l.getLineTop(from).toFloat()
                    val bottom = l.getLineBottom(to - 1).toFloat()
                    canvas!!.save()
                    canvas!!.translate(M.toFloat(), y - top)
                    canvas!!.clipRect(0f, top, width.toFloat(), bottom)
                    l.draw(canvas!!)
                    canvas!!.restore()
                    y += bottom - top
                    from = to
                    if (from < l.lineCount) newPage()
                }
                y += 4f
            }
        }
        page?.let { p ->
            canvas!!.drawText("${name} · стр. $pageNo", M.toFloat(), H - 20f, foot)
            doc.finishPage(p)
        }
        val dir = File(ctx.cacheDir, "pdf").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val safe = name.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").trim().ifEmpty { "notes" }
        val f = File(dir, "$safe.pdf")
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        return f
    }

    fun share(ctx: Context, f: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, "PDF").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
