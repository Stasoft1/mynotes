package ru.stasoft.mynotes.importer

import android.util.Xml
import androidx.room.withTransaction
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.GZIPInputStream
import org.xmlpull.v1.XmlPullParser
import ru.stasoft.mynotes.data.AppDatabase
import ru.stasoft.mynotes.data.NoteEntity

/**
 * Импорт открытого (не защищённого паролем) экспорта MyTreeNotes: XML вида
 * <DB ver="8" ...><T id= date= title= value= ...> с вложенными <T>.
 * Атрибуты, смысл которых мы ещё не подтвердили на реальном файле, читаем осторожно.
 */
object MtntImporter {
    class ImportException(message: String) : Exception(message)

    class Node(val attrs: Map<String, String>) {
        val children = mutableListOf<Node>()
    }

    fun parse(raw: ByteArray): List<Node> {
        var bytes = raw
        if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
            bytes = GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
        }
        var start = 0
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) start = 3
        while (start < bytes.size && bytes[start].toInt().toChar().isWhitespace()) start++
        if (start >= bytes.size || bytes[start] != '<'.code.toByte()) {
            throw ImportException(
                "Файл не похож на открытый экспорт .mtnt. Если он защищён паролем, такие файлы пока не поддерживаются: " +
                    "сделайте экспорт без пароля.",
            )
        }

        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), null)
        val roots = mutableListOf<Node>()
        val stack = ArrayDeque<Node>()
        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> if (parser.name == "T") {
                        val map = HashMap<String, String>()
                        for (i in 0 until parser.attributeCount) map[parser.getAttributeName(i)] = parser.getAttributeValue(i)
                        val node = Node(map)
                        if (stack.isEmpty()) roots.add(node) else stack.last().children.add(node)
                        stack.addLast(node)
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "T" && stack.isNotEmpty()) stack.removeLast()
                }
                event = parser.next()
            }
        } catch (e: org.xmlpull.v1.XmlPullParserException) {
            throw ImportException("Не удалось разобрать файл: ${e.message}")
        }
        if (roots.isEmpty()) throw ImportException("В файле не найдено ни одной заметки.")
        return roots
    }

    /** Кладёт всё дерево в новую папку «Импорт …» в корне. Возвращает число импортированных записей. */
    suspend fun importInto(db: AppDatabase, roots: List<Node>, folderTitle: String): Int {
        var count = 0
        val now = System.currentTimeMillis()
        db.withTransaction {
            val dao = db.notes()
            val rootId = dao.insert(
                NoteEntity(title = folderTitle, isFolder = true, createdAt = now, updatedAt = now),
            )
            suspend fun add(node: Node, parentId: Long) {
                val a = node.attrs
                val created = parseTime(a["date"]) ?: now
                val id = dao.insert(
                    NoteEntity(
                        parentId = parentId,
                        title = a["title"].orEmpty(),
                        body = a["value"].orEmpty(),
                        isFolder = node.children.isNotEmpty(),
                        sortIndex = a["sort"]?.toIntOrNull() ?: 0,
                        createdAt = created,
                        updatedAt = created,
                        reminderAt = parseTime(a["reminderDate"]),
                        keyword = a["keyword"].orEmpty(),
                        legacyId = a["id"],
                    ),
                )
                count++
                for (child in node.children) add(child, id)
            }
            for (r in roots) add(r, rootId)
        }
        return count
    }

    private val patterns = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd", "dd.MM.yyyy HH:mm", "dd.MM.yyyy")

    private fun parseTime(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        s.trim().toLongOrNull()?.let { n ->
            if (n <= 0) return null
            return if (n < 100_000_000_000L) n * 1000 else n
        }
        for (p in patterns) {
            try {
                return SimpleDateFormat(p, Locale.US).parse(s.trim())?.time ?: continue
            } catch (_: Exception) {
            }
        }
        return null
    }
}
