package ru.stasoft.mynotes.data

/** Сводная таблица по заметкам вида «2026 / Октябрь 7793,81 / Сентябрь 6476,12…». */
class PayCell(val value: Double)

class PayRow(val year: Int, val month: Int, val values: Map<String, Double>) {
    val total: Double get() = values.values.sum()
}

class PayTable(val cols: List<String>, val rows: List<PayRow>, val skipped: List<String>) {
    val years: List<Int> get() = rows.map { it.year }.distinct().sortedDescending()

    fun yearSum(year: Int, col: String): Double = rows.filter { it.year == year }.sumOf { it.values[col] ?: 0.0 }
    fun yearTotal(year: Int): Double = rows.filter { it.year == year }.sumOf { it.total }
    fun monthsIn(year: Int): Int = rows.count { it.year == year }

    /** Сумма за те же месяцы прошлого года, чтобы сравнение было честным при неполном текущем годе. */
    fun prevYearSameMonths(year: Int, col: String?): Double? {
        val months = rows.filter { it.year == year }.map { it.month }.toSet()
        val prev = rows.filter { it.year == year - 1 && it.month in months }
        if (prev.size != months.size || prev.isEmpty()) return null
        return prev.sumOf { if (col == null) it.total else (it.values[col] ?: 0.0) }
    }
}

val MONTH_NAMES = listOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь")
private val MONTH_PREFIX = listOf("янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")

private val YEAR_LINE = Regex("""^\s*(\d{4})\s*$""")
private val MONTH_LINE = Regex("""^\s*([А-Яа-яЁё]+)\.?\s+(-?\d[\d\s]*(?:[.,]\d+)?)\s*(?:₽|р\.?|руб\.?)?\s*$""")

/** Разбирает текст одной заметки. Возвращает найденные (год, месяц)→сумма и непонятные строки. */
fun parsePayNote(body: String): Pair<Map<Pair<Int, Int>, Double>, List<String>> {
    val out = LinkedHashMap<Pair<Int, Int>, Double>()
    val bad = ArrayList<String>()
    var year: Int? = null
    for (raw in body.lines()) {
        val line = raw.trim()
        if (line.isEmpty() || line.all { it == '-' || it == '—' || it == '_' || it == '=' }) continue
        val ym = YEAR_LINE.matchEntire(line)
        if (ym != null) { year = ym.groupValues[1].toInt(); continue }
        val m = MONTH_LINE.matchEntire(line)
        if (m == null) { if (line.any { it.isDigit() }) bad.add(line); continue }
        val name = m.groupValues[1].lowercase()
        val idx = MONTH_PREFIX.indexOfFirst { name.startsWith(it) }
        val num = m.groupValues[2].replace(" ", "").replace(',', '.').toDoubleOrNull()
        val y = year
        if (idx < 0 || num == null || y == null) { bad.add(line); continue }
        out[y to idx + 1] = num
    }
    return out to bad
}

fun buildPayTable(notes: List<Pair<String, String>>): PayTable {
    val cols = ArrayList<String>()
    val cells = HashMap<Pair<Int, Int>, MutableMap<String, Double>>()
    val skipped = ArrayList<String>()
    for ((title, body) in notes) {
        val (vals, bad) = parsePayNote(body)
        if (vals.size < 3) continue // не похоже на ряд «месяц — сумма»
        val name = title.ifBlank { "Без названия" }
        cols.add(name)
        vals.forEach { (k, v) -> cells.getOrPut(k) { LinkedHashMap() }[name] = v }
        bad.forEach { skipped.add("$name: $it") }
    }
    val rows = cells.map { (k, v) -> PayRow(k.first, k.second, v) }
        .sortedWith(compareByDescending<PayRow> { it.year }.thenByDescending { it.month })
    return PayTable(cols, rows, skipped)
}
