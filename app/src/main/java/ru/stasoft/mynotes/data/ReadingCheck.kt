package ru.stasoft.mynotes.data

import java.time.LocalDate

/** Строка для проверки показаний: заголовок нужен, чтобы найти дату в названии папки дня. */
data class TitleRow(
    val id: Long,
    val parentId: Long?,
    val isFolder: Boolean,
    val title: String,
    val head: String,
)

data class ReadingIssue(
    val noteId: Long,
    val date: LocalDate,
    val folderTitle: String,
    val noteTitle: String,
    val value: Double,
    val message: String,
)

private val DATE = Regex("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4}|\\d{2})")

private fun dateIn(title: String): LocalDate? {
    val m = DATE.find(title) ?: return null
    return try {
        val y = m.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }
        LocalDate.of(y, m.groupValues[2].toInt(), m.groupValues[1].toInt())
    } catch (_: Exception) {
        null
    }
}

private class Reading(val row: TitleRow, val date: LocalDate, val folderTitle: String, val value: Double)

/**
 * Ищет показания, выбивающиеся из роста: берёт все показания поля [field] внутри [scopeId] (null — вся база),
 * ставит их по датам из названий папок (например «16.10.26 - 8107») и находит самую длинную неубывающую
 * последовательность. Всё, что в неё не вошло, — подозрительно: опечатка, пробег за день вместо показания, и т. п.
 */
fun checkReadings(rows: List<TitleRow>, scopeId: Long?, field: String): List<ReadingIssue> {
    val byId = HashMap<Long, TitleRow>(rows.size * 2)
    val kids = HashMap<Long?, MutableList<TitleRow>>()
    for (r in rows) {
        byId[r.id] = r
        kids.getOrPut(r.parentId) { mutableListOf() }.add(r)
    }
    val readings = ArrayList<Reading>()
    val stack = ArrayDeque<TitleRow>()
    if (scopeId == null) kids[null]?.forEach { stack.addLast(it) } else kids[scopeId]?.forEach { stack.addLast(it) }
    while (stack.isNotEmpty()) {
        val r = stack.removeLast()
        if (r.isFolder) {
            kids[r.id]?.forEach { stack.addLast(it) }
            continue
        }
        val v = parseNoteNumber(r.head, field) ?: continue
        // Дата — из ближайшей папки выше (или из самой заметки), где она есть в названии.
        var cur: TitleRow? = r
        var date: LocalDate? = null
        var folderTitle = ""
        while (cur != null && date == null) {
            date = dateIn(cur.title)
            if (date != null) folderTitle = cur.title
            cur = cur.parentId?.let { byId[it] }
        }
        if (date != null) readings.add(Reading(r, date, folderTitle, v))
    }
    if (readings.isEmpty()) return emptyList()
    // В один день показания идут по возрастанию, чтобы порядок заметок внутри дня не давал ложных тревог.
    readings.sortWith(compareBy<Reading>({ it.date }, { it.value }))

    // Самая длинная неубывающая последовательность.
    val n = readings.size
    val tails = ArrayList<Int>() // индексы концов подпоследовательностей
    val prev = IntArray(n) { -1 }
    for (i in 0 until n) {
        var lo = 0
        var hi = tails.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (readings[tails[mid]].value <= readings[i].value) lo = mid + 1 else hi = mid
        }
        if (lo > 0) prev[i] = tails[lo - 1]
        if (lo == tails.size) tails.add(i) else tails[lo] = i
    }
    val inRun = BooleanArray(n)
    var k = tails.lastOrNull() ?: -1
    while (k >= 0) {
        inRun[k] = true
        k = prev[k]
    }

    val issues = ArrayList<ReadingIssue>()
    for (i in 0 until n) {
        if (inRun[i]) continue
        val r = readings[i]
        val before = (i - 1 downTo 0).firstOrNull { inRun[it] }?.let { readings[it] }
        val after = (i + 1 until n).firstOrNull { inRun[it] }?.let { readings[it] }
        val msg = buildString {
            if (r.value < 1000 && (before?.value ?: 0.0) > 5000) append("похоже на пробег за день, а не показание; ")
            if (before != null) append("до этого ${plainNumber(before.value)} (${before.date.dayOfMonth}.${before.date.monthValue}); ")
            if (after != null) append("после ${plainNumber(after.value)} (${after.date.dayOfMonth}.${after.date.monthValue})")
        }.trim().trimEnd(';')
        issues.add(ReadingIssue(r.row.id, r.date, r.folderTitle, r.row.title, r.value, msg))
    }
    return issues
}
