package ru.stasoft.mynotes.data

/**
 * Таблица внутри текста заметки: подряд идущие строки вида «| a | b |».
 * Остаётся обычным текстом, поэтому поиск, копирование и резервные копии работают как раньше.
 */
class TableBlock(val start: Int, val end: Int, val rows: List<List<String>>)

private fun isTableLine(line: String): Boolean {
    val t = line.trim()
    return t.length >= 2 && t.startsWith("|") && t.endsWith("|")
}

fun parseTableRow(line: String): List<String> =
    line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

fun findTables(text: String): List<TableBlock> {
    val out = ArrayList<TableBlock>()
    var pos = 0
    var start = -1
    var end = 0
    val rows = ArrayList<List<String>>()
    fun flush() {
        if (start >= 0 && rows.isNotEmpty()) {
            val w = rows.maxOf { it.size }
            out.add(TableBlock(start, end, rows.map { r -> r + List(w - r.size) { "" } }))
        }
        start = -1
        rows.clear()
    }
    for (line in text.split("\n")) {
        val lineEnd = pos + line.length
        if (isTableLine(line)) {
            if (start < 0) start = pos
            end = lineEnd
            rows.add(parseTableRow(line))
        } else flush()
        pos = lineEnd + 1
    }
    flush()
    return out
}

fun tableAt(text: String, pos: Int): TableBlock? = findTables(text).firstOrNull { pos >= it.start && pos <= it.end }

fun serializeTable(rows: List<List<String>>): String =
    rows.joinToString("\n") { r -> "| " + r.joinToString(" | ") { it.replace('|', '/').replace('\n', ' ').trim() } + " |" }

fun parseCellNumber(s: String): Double? =
    s.replace(" ", "").replace(' ', ' ').replace(" ", "").replace(',', '.').toDoubleOrNull()

/** Суммы по столбцам: считаются только там, где все заполненные ячейки (кроме шапки) — числа. */
fun tableSums(rows: List<List<String>>): List<Double?> {
    if (rows.size < 2) return emptyList()
    val body = rows.drop(1)
    return rows[0].indices.map { c ->
        val cells = body.map { it.getOrElse(c) { "" } }.filter { it.isNotBlank() }
        val nums = cells.map { parseCellNumber(it) }
        if (nums.isNotEmpty() && nums.all { it != null }) nums.sumOf { it!! } else null
    }
}
