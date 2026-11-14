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
class PdfBlock(val text: String, val level: Int)

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
        for (b in blocks) {
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
