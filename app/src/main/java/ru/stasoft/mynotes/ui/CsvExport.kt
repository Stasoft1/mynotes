package ru.stasoft.mynotes.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Экспорт таблицы в CSV: разделитель «;» и кодировка UTF-8 с меткой BOM, чтобы русский Excel открывал файл без настройки. */
object CsvExport {
    private fun field(s: String): String =
        if (s.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    fun build(rows: List<List<String>>): String = rows.joinToString("\r\n") { r -> r.joinToString(";") { field(it) } } + "\r\n"

    fun share(ctx: Context, name: String, rows: List<List<String>>) {
        val dir = File(ctx.cacheDir, "pdf").apply { mkdirs() }
        val safe = name.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").trim().ifEmpty { "table" }
        val f = File(dir, "$safe.csv")
        f.writeBytes(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + build(rows).toByteArray(Charsets.UTF_8))
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, "CSV").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
