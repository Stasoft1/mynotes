package ru.stasoft.mynotes.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONArray
import org.json.JSONObject
import ru.stasoft.mynotes.data.AppDatabase
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Резервные копии. Копия — zip (.mnbak) с зашифрованной базой, связкой ключей (без отпечатка) и описанием.
 * Данные остаются зашифрованными: открыть копию можно только тем же паролем, поэтому копию безопасно класть
 * куда угодно, в том числе в папку Google Диска (через приложение «Диск» и системный выбор папки: само приложение
 * в сеть не выходит).
 */
object BackupManager {
    private const val PREFS = "backup"
    const val EXT = ".mnbak"
    private const val DB_NAME = "notes.db"

    class Item(val name: String, val size: Long, val time: Long, val local: Boolean, val uri: Uri?, val file: File?)

    fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun localDir(ctx: Context): File = File(ctx.filesDir, "backups").also { it.mkdirs() }

    fun treeUri(ctx: Context): Uri? = prefs(ctx).getString("tree", null)?.let { Uri.parse(it) }

    fun setTree(ctx: Context, uri: Uri?) {
        prefs(ctx).edit().apply { if (uri == null) remove("tree") else putString("tree", uri.toString()) }.apply()
    }

    fun keepCount(ctx: Context) = prefs(ctx).getInt("keep", 10)

    private fun fileName(): String =
        "MyNotes-" + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date()) + EXT

    private fun treeDoc(tree: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    // ---------- создание ----------

    private fun writeZip(ctx: Context, out: OutputStream) {
        // Если база открыта, сбрасываем журнал в основной файл, чтобы копия была согласованной.
        try {
            AppDatabase.current?.takeIf { it.isOpen }?.openHelper?.writableDatabase
                ?.query("PRAGMA wal_checkpoint(TRUNCATE)")?.close()
        } catch (_: Exception) {
        }
        val zip = ZipOutputStream(out)
        val version = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (_: Exception) { "" }
        zip.putNextEntry(ZipEntry("meta.json"))
        zip.write(
            JSONObject().put("app", "MyNotes").put("dbVersion", AppDatabase.VERSION)
                .put("created", System.currentTimeMillis()).put("appVersion", version).toString().toByteArray(),
        )
        zip.closeEntry()
        // Связка ключей: всё, кроме данных отпечатка (он привязан к этому телефону).
        val arr = JSONArray()
        for ((k, v) in ctx.getSharedPreferences("vault", Context.MODE_PRIVATE).all) {
            if (k.startsWith("bio_") || v == null) continue
            val t = when (v) {
                is String -> "s"; is Int -> "i"; is Long -> "l"; is Boolean -> "b"; is Float -> "f"; else -> continue
            }
            arr.put(JSONObject().put("k", k).put("t", t).put("v", v.toString()))
        }
        zip.putNextEntry(ZipEntry("vault.json"))
        zip.write(arr.toString().toByteArray())
        zip.closeEntry()
        val dbFile = ctx.getDatabasePath(DB_NAME)
        for (f in listOf(dbFile, File(dbFile.path + "-wal"))) {
            if (!f.exists() || f.length() == 0L) continue
            zip.putNextEntry(ZipEntry(f.name))
            f.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
        zip.finish()
    }

    /** Создаёт копию во внутренней папке и, если выбрана, в папке пользователя. Возвращает описание результата. */
    @Synchronized
    fun backupNow(ctx: Context, auto: Boolean): String {
        val name = fileName()
        val dir = localDir(ctx)
        val tmp = File(dir, "$name.tmp")
        try {
            FileOutputStream(tmp).use { writeZip(ctx, it) }
            tmp.renameTo(File(dir, name))
        } finally {
            tmp.delete()
        }
        var result = "Копия $name создана"
        val tree = treeUri(ctx)
        if (tree != null) {
            try {
                val doc = DocumentsContract.createDocument(ctx.contentResolver, treeDoc(tree), "application/octet-stream", name)
                    ?: throw IllegalStateException("не удалось создать файл в выбранной папке")
                ctx.contentResolver.openOutputStream(doc, "w")!!.use { out ->
                    File(dir, name).inputStream().use { it.copyTo(out) }
                }
                result += " и записана в папку"
            } catch (e: Exception) {
                result += ", но в выбранную папку записать не удалось: ${e.message}"
                prefs(ctx).edit().putBoolean("last_ok", false).putString("last_msg", result).putLong("last_time", System.currentTimeMillis()).apply()
                prune(ctx)
                throw FolderWriteFailed(result)
            }
        }
        prune(ctx)
        prefs(ctx).edit().putBoolean("last_ok", true).putString("last_msg", result)
            .putLong("last_time", System.currentTimeMillis()).putBoolean("last_auto", auto).apply()
        return result
    }

    class FolderWriteFailed(msg: String) : Exception(msg)

    // ---------- список и удаление ----------

    fun list(ctx: Context): List<Item> {
        val out = mutableListOf<Item>()
        localDir(ctx).listFiles { f -> f.name.endsWith(EXT) }?.forEach {
            out.add(Item(it.name, it.length(), it.lastModified(), true, null, it))
        }
        treeUri(ctx)?.let { tree ->
            try {
                val kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                ctx.contentResolver.query(
                    kids,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_SIZE,
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    ),
                    null, null, null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        val n = c.getString(1) ?: continue
                        if (!n.endsWith(EXT)) continue
                        out.add(Item(n, c.getLong(2), c.getLong(3), false, DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)), null))
                    }
                }
            } catch (_: Exception) {
            }
        }
        return out.sortedByDescending { it.time }
    }

    fun delete(ctx: Context, item: Item) {
        if (item.local) item.file?.delete()
        else item.uri?.let { try { DocumentsContract.deleteDocument(ctx.contentResolver, it) } catch (_: Exception) {} }
    }

    /** Оставляет [keepCount] последних копий в каждом из мест хранения. */
    private fun prune(ctx: Context) {
        val keep = keepCount(ctx)
        val all = list(ctx)
        all.filter { it.local }.drop(keep).forEach { delete(ctx, it) }
        all.filter { !it.local }.drop(keep).forEach { delete(ctx, it) }
    }

    fun openStream(ctx: Context, item: Item): InputStream? =
        if (item.local) item.file?.inputStream() else item.uri?.let { ctx.contentResolver.openInputStream(it) }

    // ---------- восстановление ----------

    /**
     * Восстанавливает базу и связку ключей из копии. Перед этим делает страховочную копию текущих данных.
     * После успеха процесс нужно перезапустить (вызывающий это делает). Бросает исключение с понятным текстом.
     */
    @Synchronized
    fun restore(ctx: Context, input: InputStream) {
        val tmpDir = File(ctx.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        try {
            var meta: JSONObject? = null
            var vault: JSONArray? = null
            ZipInputStream(input).use { zin ->
                while (true) {
                    val e = zin.nextEntry ?: break
                    val target = File(tmpDir, e.name.substringAfterLast('/'))
                    when (e.name) {
                        "meta.json" -> meta = JSONObject(zin.readBytes().decodeToString())
                        "vault.json" -> vault = JSONArray(zin.readBytes().decodeToString())
                        DB_NAME, "$DB_NAME-wal" -> FileOutputStream(target).use { zin.copyTo(it) }
                    }
                }
            }
            if (meta == null || vault == null || !File(tmpDir, DB_NAME).exists()) throw IllegalStateException("это не копия МоиЗаметки или файл повреждён")
            val dbVersion = meta!!.optInt("dbVersion", 0)
            if (dbVersion > AppDatabase.VERSION) throw IllegalStateException("копия сделана более новой версией приложения")
            // Страховка: текущее состояние сохраняется как обычная копия.
            try { backupNow(ctx, auto = false) } catch (_: Exception) {}
            AppDatabase.current?.let { try { if (it.isOpen) it.close() } catch (_: Exception) {} }
            val dbFile = ctx.getDatabasePath(DB_NAME)
            for (suffix in listOf("", "-wal", "-shm", "-journal")) File(dbFile.path + suffix).delete()
            dbFile.parentFile?.mkdirs()
            File(tmpDir, DB_NAME).copyTo(dbFile, overwrite = true)
            val wal = File(tmpDir, "$DB_NAME-wal")
            if (wal.exists()) wal.copyTo(File(dbFile.path + "-wal"), overwrite = true)
            val p = ctx.getSharedPreferences("vault", Context.MODE_PRIVATE)
            val ed = p.edit().clear()
            val arr = vault!!
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val k = o.getString("k")
                val v = o.getString("v")
                when (o.getString("t")) {
                    "s" -> ed.putString(k, v)
                    "i" -> ed.putInt(k, v.toInt())
                    "l" -> ed.putLong(k, v.toLong())
                    "b" -> ed.putBoolean(k, v.toBoolean())
                    "f" -> ed.putFloat(k, v.toFloat())
                }
            }
            if (!ed.commit()) throw IllegalStateException("не удалось записать ключи")
        } finally {
            tmpDir.deleteRecursively()
        }
    }
}
