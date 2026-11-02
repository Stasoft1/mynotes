package ru.stasoft.mynotes.data


/** Урезанная строка записи для подсчёта сумм: без полного текста, только начало. */
data class NumRow(
    val id: Long,
    val parentId: Long?,
    val isFolder: Boolean,
    val title: String,
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
        if (line.substring(0, i).trim().trim('{', '}').trim().lowercase() == prefix) return toNumber(line.substring(i + 1))
    }
    return null
}

/** Сумма всех строк «Поле: число» в записи (если поле повторяется); null — такой строки нет. */
fun parseNoteSum(head: String, field: String): Double? {
    if (field.isEmpty()) return parseNoteNumber(head, field)
    val prefix = field.trim().lowercase()
    var sum = 0.0
    var any = false
    for (line in head.lineSequence()) {
        val i = line.indexOf(':')
        if (i <= 0) continue
        if (line.substring(0, i).trim().trim('{', '}').trim().lowercase() == prefix) {
            toNumber(line.substring(i + 1))?.let { sum += it; any = true }
        }
    }
    return if (any) sum else null
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

/** [own] — дата в названии папки (день), [date] — своя или унаследованная от папки выше (epochDay), null — без даты. */
private class Visit(val row: NumRow, val eff: List<VarDef>, val childEff: List<VarDef>, val own: Long?, val date: Long?)

/** Наибольшее показание строго меньше [x] в отсортированном массиве. */
private fun below(sorted: DoubleArray?, x: Double): Double? {
    if (sorted == null || sorted.isEmpty()) return null
    var lo = 0
    var hi = sorted.size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (sorted[mid] < x) lo = mid + 1 else hi = mid
    }
    return if (lo == 0) null else sorted[lo - 1]
}

/**
 * Значения переменных по всему дереву. Переменные, объявленные в папке, действуют на её содержимое и вложенные
 * папки (см. applyVars). Число заметки берётся по каждой действующей переменной, число папки — свод значений
 * её содержимого по правилу переменной. Записи без единого значения в результат не попадают.
 *
 * Правило «span» (прирост) нужно для пробега: показания одометра растут, поэтому прирост папки — это её
 * наибольшее показание минус ближайшее меньшее показание до неё (конец предыдущего периода). Для заметки это
 * дистанция от предыдущего показания. Порядок записей в дереве не важен.
 */
fun computeNumbers(rows: List<NumRow>): Map<Long, List<Pair<String, Double>>> {
    if (rows.none { it.isFolder && it.varsJson.isNotEmpty() }) return emptyMap()
    val kids = HashMap<Long?, MutableList<NumRow>>()
    for (r in rows) kids.getOrPut(r.parentId) { mutableListOf() }.add(r)

    val order = ArrayList<Visit>(rows.size)
    val stack = ArrayDeque<Visit>()
    fun visit(r: NumRow, eff: List<VarDef>, inherited: Long?) {
        val childEff = if (r.isFolder && r.varsJson.isNotEmpty()) applyVars(eff, parseVars(r.varsJson)) else eff
        val own = if (r.isFolder) dateInTitle(r.title)?.toEpochDay() else null
        stack.addLast(Visit(r, eff, childEff, own, own ?: inherited))
    }
    kids[null]?.forEach { visit(it, emptyList(), null) }
    while (stack.isNotEmpty()) {
        val v = stack.removeLast()
        order.add(v)
        if (v.row.isFolder) kids[v.row.id]?.forEach { visit(it, v.childEff, v.date) }
    }

    // Все показания по полям, где действует правило span.
    val readingLists = HashMap<String, MutableList<Double>>()
    for (v in order) {
        if (v.row.isFolder) continue
        for (d in v.eff) {
            if (d.agg != "span") continue
            parseNoteNumber(v.row.head, d.fieldName())?.let { readingLists.getOrPut(d.fieldName()) { mutableListOf() }.add(it) }
        }
    }
    val readings = HashMap<String, DoubleArray>()
    for ((k, l) in readingLists) readings[k] = l.sorted().toDoubleArray()

    // Накопительный остаток: вклад каждой заметки (формула по её полям, отсутствующее поле = 0) на дату её дня.
    val byId = rows.associateBy { it.id }
    val contribs = HashMap<String, MutableList<Pair<Long, Double>>>()
    val lastDate = HashMap<Long, HashMap<String, Long>>()
    val contribNotes = HashSet<Pair<Long, String>>()
    for (v in order) {
        if (v.row.isFolder) continue
        for (d in v.eff) {
            if (d.agg != "balance" || d.formula.isBlank()) continue
            var any = false
            val x = evalFormula(d.formula) { nm ->
                val field = v.eff.firstOrNull { it.name.equals(nm, ignoreCase = true) }?.fieldName() ?: nm
                parseNoteSum(v.row.head, field)?.also { any = true } ?: 0.0
            }
            if (!any || x == null) continue
            val key = v.date ?: Long.MIN_VALUE
            contribs.getOrPut(d.name) { mutableListOf() }.add(key to x)
            contribNotes.add(v.row.id to d.name)
            var p = v.row.parentId
            var guard = 0
            while (p != null && guard++ < 64) {
                lastDate.getOrPut(p) { HashMap() }.merge(d.name, key) { a, b -> maxOf(a, b) }
                p = byId[p]?.parentId
            }
        }
    }
    val balDates = HashMap<String, LongArray>()
    val balSums = HashMap<String, DoubleArray>()
    for ((name, list) in contribs) {
        val sorted = list.sortedBy { it.first }
        balDates[name] = LongArray(sorted.size) { sorted[it].first }
        var acc = 0.0
        balSums[name] = DoubleArray(sorted.size) { acc += sorted[it].second; acc }
    }
    fun balanceAt(name: String, key: Long): Double? {
        val dates = balDates[name] ?: return null
        var lo = 0
        var hi = dates.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (dates[mid] <= key) lo = mid + 1 else hi = mid
        }
        return if (lo == 0) null else balSums[name]!![lo - 1]
    }

    val result = HashMap<Long, List<Pair<String, Double>>>()
    val accs = HashMap<Long, LinkedHashMap<String, Acc>>()
    val raws = HashMap<Long, LinkedHashMap<String, Acc>>() // показания (min/max) для span, по названию переменной
    for (v in order.asReversed()) {
        val r = v.row
        val parent = r.parentId
        val values = ArrayList<Pair<String, Double>>()
        if (r.isFolder) {
            val acc = accs[r.id]
            val raw = raws[r.id]
            for (d in v.childEff) {
                when (d.agg) {
                    "none", "calc" -> {}
                    "balance" -> {
                        val key = v.own ?: lastDate[r.id]?.get(d.name)
                        if (key != null) balanceAt(d.name, key)?.let { values.add(d.name to it) }
                    }
                    "span" -> {
                        val a = raw?.get(d.name) ?: continue
                        val prev = below(readings[d.fieldName()], a.min)
                        val value = if (prev != null) a.max - prev else if (a.max > a.min) a.max - a.min else null
                        if (value != null) values.add(d.name to value)
                    }
                    else -> acc?.get(d.name)?.let { values.add(d.name to it.result(d.agg)) }
                }
            }
            // Показания для span поднимаются к родителю независимо от того, показан ли прирост здесь.
            if (parent != null && raw != null) {
                val pr = raws.getOrPut(parent) { LinkedHashMap() }
                for ((name, a) in raw) {
                    val t = pr.getOrPut(name) { Acc() }
                    t.add(a.min)
                    t.add(a.max)
                }
            }
        } else {
            for (d in v.eff) {
                if (d.agg == "balance") {
                    if ((r.id to d.name) in contribNotes) balanceAt(d.name, v.date ?: Long.MIN_VALUE)?.let { values.add(d.name to it) }
                    continue
                }
                if (d.agg == "none" || d.agg == "calc") continue
                val n = (if (d.agg == "sum") parseNoteSum(r.head, d.fieldName()) else parseNoteNumber(r.head, d.fieldName()))
                    ?: formulaValue(d, r.head, v.eff) ?: continue
                if (d.agg == "span") {
                    below(readings[d.fieldName()], n)?.let { values.add(d.name to n - it) }
                    if (parent != null) raws.getOrPut(parent) { LinkedHashMap() }.getOrPut(d.name) { Acc() }.add(n)
                } else {
                    values.add(d.name to n)
                }
            }
        }
        // «Расчёт по итогам»: формула по значениям этой же записи (на каждом уровне дерева, а не сумма формул).
        val calcDefs = (if (r.isFolder) v.childEff else v.eff).filter { it.agg == "calc" && it.formula.isNotBlank() }
        if (values.isNotEmpty()) for (d in calcDefs) {
            val snapshot = values.toList()
            evalFormula(d.formula) { nm -> snapshot.firstOrNull { it.first.equals(nm, ignoreCase = true) }?.second }
                ?.let { values.add(d.name to it) }
        }
        if (values.isEmpty()) continue
        val hideZero = (if (r.isFolder) v.childEff else v.eff).filter { it.hideZero }.map { it.name }.toSet()
        val shownValues = if (hideZero.isEmpty()) values else values.filter { !(it.first in hideZero && Math.abs(it.second) < 0.005) }
        if (shownValues.isNotEmpty()) result[r.id] = shownValues
        if (parent == null) continue
        // В свод вверх идут только обычные значения: прирост считается заново из показаний.
        val spanNames = v.let { (if (r.isFolder) it.childEff else it.eff).filter { d -> d.agg == "span" || d.agg == "calc" || d.agg == "balance" }.map { d -> d.name }.toSet() }
        val pa = accs.getOrPut(parent) { LinkedHashMap() }
        for ((name, value) in values) if (name !in spanNames) pa.getOrPut(name) { Acc() }.add(value)
    }
    return result
}

/** Название с переменными: {Сумма} заменяется текущим значением переменной (число без разделителей), нет значения — 0. */
fun resolveTitle(title: String, values: List<Pair<String, Double>>?): String {
    if (!title.contains('{')) return title
    return Regex("\\{([^{}]+)\\}").replace(title) { m ->
        val name = m.groupValues[1].trim()
        val v = values?.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second
        if (v == null) m.value.takeIf { name.lowercase() in setOf("дата", "время", "сегодня") } ?: "0" else plainNumber(v)
    }
}

fun plainNumber(v: Double): String =
    if (v == Math.rint(v)) v.toLong().toString() else String.format(java.util.Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')

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

/** То же, что formatValues, но «Сумма» подписана рублями. */
fun formatValuesMoney(values: List<Pair<String, Double>>): String =
    values.joinToString(" · ") { (name, v) ->
        when {
            name.equals("Сумма", ignoreCase = true) -> formatNumber(v) + " ₽"
            name.isEmpty() -> formatNumber(v)
            else -> "$name ${formatNumber(v)}"
        }
    }
