package ru.stasoft.mynotes.data


/** Урезанная строка записи для подсчёта сумм: без полного текста, только начало. */
data class NumRow(
    val id: Long,
    val parentId: Long?,
    val isFolder: Boolean,
    val numeric: Boolean,
    val numericField: String,
    val head: String,
)

private val NUMBER = Regex("^[+-]?\\d+(?:[.,]\\d+)?$")

private fun toNumber(raw: String): Double? {
    val s = raw.trim().replace(" ", "").replace(" ", "")
    return if (NUMBER.matches(s)) s.replace(',', '.').toDoubleOrNull() else null
}

/**
 * Число записи. [field] пустое — число в первой строке (как в MyTreeNotes);
 * иначе число в строке «Поле: значение» с этим названием.
 */
fun parseNoteNumber(head: String, field: String): Double? {
    if (field.isEmpty()) return toNumber(head.lineSequence().firstOrNull().orEmpty())
    val prefix = field.trim().lowercase()
    for (line in head.lineSequence()) {
        val i = line.indexOf(':')
        if (i <= 0) continue
        if (line.substring(0, i).trim().lowercase() == prefix) return toNumber(line.substring(i + 1))
    }
    return null
}

/**
 * Суммы по всему дереву. Флажок «Числовые значения» у папки действует на всё её содержимое, пока ниже
 * не встретится другая папка со своим флажком. Число заметки берётся по настройке ближайшей такой папки,
 * число папки — сумма чисел её содержимого. Папки без единого числа в результат не попадают.
 */
fun computeNumbers(rows: List<NumRow>): Map<Long, Double> {
    if (rows.none { it.numeric }) return emptyMap()
    val byId = HashMap<Long, NumRow>(rows.size * 2)
    val kids = HashMap<Long?, MutableList<NumRow>>()
    for (r in rows) {
        byId[r.id] = r
        kids.getOrPut(r.parentId) { mutableListOf() }.add(r)
    }
    val result = HashMap<Long, Double>()

    // Обходим от корня вниз без рекурсии по глубине записей: стек вместо вызовов.
    // Сначала порядок посещения с настройкой, затем суммирование в обратном порядке.
    class Visit(val row: NumRow, val field: String?)
    val order = ArrayList<Visit>(rows.size)
    val stack = ArrayDeque<Visit>()
    kids[null]?.forEach { stack.addLast(Visit(it, null)) }
    while (stack.isNotEmpty()) {
        val v = stack.removeLast()
        order.add(v)
        if (v.row.isFolder) {
            val childField = if (v.row.numeric) v.row.numericField else v.field
            kids[v.row.id]?.forEach { stack.addLast(Visit(it, childField)) }
        }
    }
    val sums = HashMap<Long, Double>()
    for (v in order.asReversed()) {
        val r = v.row
        if (r.isFolder) {
            val s = sums[r.id]
            if (s != null) result[r.id] = s
        } else {
            val f = v.field ?: continue
            val n = parseNoteNumber(r.head, f) ?: continue
            result[r.id] = n
        }
        val value = result[r.id] ?: continue
        val p = r.parentId
        if (p != null) sums[p] = (sums[p] ?: 0.0) + value
    }
    return result
}

/** «66261», «1 234,5»: целые без дробной части, тысячи через пробел. */
fun formatNumber(v: Double): String {
    val whole = v == Math.rint(v)
    val text = if (whole) v.toLong().toString() else String.format(java.util.Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
    val neg = text.startsWith("-")
    val digits = text.removePrefix("-").removePrefix("+")
    val parts = digits.split('.')
    val grouped = parts[0].reversed().chunked(3).joinToString(" ").reversed()
    val out = grouped + if (parts.size > 1) "," + parts[1] else ""
    return if (neg) "-$out" else out
}
