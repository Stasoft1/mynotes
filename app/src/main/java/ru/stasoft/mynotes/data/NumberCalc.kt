package ru.stasoft.mynotes.data


/** Урезанная строка записи для подсчёта сумм: без полного текста, только начало. */
data class NumRow(
    val id: Long,
    val parentId: Long?,
    val isFolder: Boolean,
    val varsJson: String,
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

private class Acc {
    var sum = 0.0
    var max = Double.NEGATIVE_INFINITY
    var min = Double.POSITIVE_INFINITY
    fun add(v: Double) {
        sum += v
        if (v > max) max = v
        if (v < min) min = v
    }
    fun result(agg: String): Double = when (agg) {
        "max" -> max
        "min" -> min
        else -> sum
    }
}

private class Visit(val row: NumRow, val eff: List<VarDef>, val childEff: List<VarDef>)

/**
 * Значения переменных по всему дереву. Переменные, объявленные в папке, действуют на её содержимое и вложенные
 * папки (см. applyVars). Число заметки берётся по каждой действующей переменной, число папки — свод значений
 * её содержимого по правилу переменной. Записи без единого значения в результат не попадают.
 */
fun computeNumbers(rows: List<NumRow>): Map<Long, List<Pair<String, Double>>> {
    if (rows.none { it.isFolder && it.varsJson.isNotEmpty() }) return emptyMap()
    val kids = HashMap<Long?, MutableList<NumRow>>()
    for (r in rows) kids.getOrPut(r.parentId) { mutableListOf() }.add(r)

    val order = ArrayList<Visit>(rows.size)
    val stack = ArrayDeque<Visit>()
    fun visit(r: NumRow, eff: List<VarDef>) {
        val childEff = if (r.isFolder && r.varsJson.isNotEmpty()) applyVars(eff, parseVars(r.varsJson)) else eff
        stack.addLast(Visit(r, eff, childEff))
    }
    kids[null]?.forEach { visit(it, emptyList()) }
    while (stack.isNotEmpty()) {
        val v = stack.removeLast()
        order.add(v)
        if (v.row.isFolder) kids[v.row.id]?.forEach { visit(it, v.childEff) }
    }

    val result = HashMap<Long, List<Pair<String, Double>>>()
    val accs = HashMap<Long, LinkedHashMap<String, Acc>>()
    for (v in order.asReversed()) {
        val r = v.row
        val values: List<Pair<String, Double>> = if (r.isFolder) {
            val acc = accs[r.id]
            if (acc == null) emptyList()
            else v.childEff.filter { it.agg != "none" }.mapNotNull { d -> acc[d.name]?.let { d.name to it.result(d.agg) } }
        } else {
            v.eff.filter { it.agg != "none" }.mapNotNull { d -> parseNoteNumber(r.head, d.name)?.let { d.name to it } }
        }
        if (values.isEmpty()) continue
        result[r.id] = values
        val p = r.parentId ?: continue
        val pa = accs.getOrPut(p) { LinkedHashMap() }
        for ((name, value) in values) pa.getOrPut(name) { Acc() }.add(value)
    }
    return result
}

/** Подпись под названием: одно число первой строки — просто число, иначе «Имя число · Имя число». */
fun formatValues(values: List<Pair<String, Double>>): String =
    values.joinToString(" · ") { (name, v) -> if (name.isEmpty()) formatNumber(v) else "$name ${formatNumber(v)}" }

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
