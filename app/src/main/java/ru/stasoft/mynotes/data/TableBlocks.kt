package ru.stasoft.mynotes.data

/**
 * Таблица внутри текста заметки: подряд идущие строки вида «| a | b |».
 * Остаётся обычным текстом, поэтому поиск, копирование и резервные копии работают как раньше.
 */
class TableBlock(val start: Int, val end: Int, val rows: List<List<String>>, val aligns: List<Int> = emptyList())

private val SEP = Regex("^:?-+:?$")
private fun isSepRow(cells: List<String>) = cells.isNotEmpty() && cells.all { SEP.matches(it) }

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
            var aligns = emptyList<Int>()
            if (rows.size >= 2 && isSepRow(rows[1])) {
                aligns = rows[1].map { c -> when { c.startsWith(":") && c.endsWith(":") -> 2; c.startsWith(":") -> 1; c.endsWith(":") -> 3; else -> 0 } }
                rows.removeAt(1)
            }
            val w = maxOf(rows.maxOf { it.size }, aligns.size)
            out.add(TableBlock(start, end, rows.map { r -> r + List(w - r.size) { "" } }, aligns + List(w - aligns.size) { 0 }))
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

/** Выравнивание столбцов хранится строкой-разделителем как в Markdown: «| :--- | :---: | ---: |». */
fun serializeTable(rows: List<List<String>>, aligns: List<Int> = emptyList()): String {
    val lines = rows.map { r -> "| " + r.joinToString(" | ") { it.replace('|', '/').replace('\n', ' ').trim() } + " |" }.toMutableList()
    if (aligns.any { it != 0 } && lines.isNotEmpty()) {
        val w = rows[0].size
        lines.add(1, "| " + (0 until w).joinToString(" | ") { when (aligns.getOrElse(it) { 0 }) { 1 -> ":---"; 2 -> ":---:"; 3 -> "---:"; else -> "---" } } + " |")
    }
    return lines.joinToString("\n")
}

/** Оформление ячейки. Цвета — RGB, -1 — не задан. Размер: 0 обычный, 1 мелкий, 2 крупный, 3 очень крупный. Шрифт: 0 обычный, 1 с засечками, 2 моноширинный. */
data class CellSt(val b: Boolean = false, val i: Boolean = false, val u: Boolean = false, val color: Int = -1, val bg: Int = -1, val size: Int = 0, val font: Int = 0)

/** Оформление лежит в начале текста ячейки: «{{b;i;c=FF0000;g=FFEEAA;s=2;f=1}}текст». */
fun parseCell(raw: String): Pair<CellSt, String> {
    if (!raw.startsWith("{{")) return CellSt() to raw
    val e = raw.indexOf("}}")
    if (e < 0 || e > 80) return CellSt() to raw
    var st = CellSt()
    raw.substring(2, e).split(';').forEach { t ->
        when {
            t == "b" -> st = st.copy(b = true)
            t == "i" -> st = st.copy(i = true)
            t == "u" -> st = st.copy(u = true)
            t.startsWith("c=") -> t.substring(2).toIntOrNull(16)?.let { st = st.copy(color = it) }
            t.startsWith("g=") -> t.substring(2).toIntOrNull(16)?.let { st = st.copy(bg = it) }
            t.startsWith("s=") -> t.substring(2).toIntOrNull()?.let { st = st.copy(size = it.coerceIn(0, 3)) }
            t.startsWith("f=") -> t.substring(2).toIntOrNull()?.let { st = st.copy(font = it.coerceIn(0, 2)) }
        }
    }
    return st to raw.substring(e + 2)
}

fun encodeCell(st: CellSt, text: String): String {
    val t = ArrayList<String>()
    if (st.b) t.add("b")
    if (st.i) t.add("i")
    if (st.u) t.add("u")
    if (st.color >= 0) t.add("c=" + "%06X".format(st.color))
    if (st.bg >= 0) t.add("g=" + "%06X".format(st.bg))
    if (st.size != 0) t.add("s=${st.size}")
    if (st.font != 0) t.add("f=${st.font}")
    return if (t.isEmpty()) text else "{{" + t.joinToString(";") + "}}" + text
}

fun parseCellNumber(s: String): Double? =
    s.replace(" ", "").replace(' ', ' ').replace(" ", "").replace(',', '.').toDoubleOrNull()

/** Суммы по столбцам: считаются только там, где все заполненные ячейки (кроме шапки) — числа. */
fun tableSums(rows: List<List<String>>): List<Double?> {
    if (rows.size < 2) return emptyList()
    val body = rows.drop(1)
    return rows[0].indices.map { c ->
        val cells = body.map { parseCell(it.getOrElse(c) { "" }).second }.filter { it.isNotBlank() }
        val nums = cells.map { parseCellNumber(it) }
        if (nums.isNotEmpty() && nums.all { it != null }) nums.sumOf { it!! } else null
    }
}
