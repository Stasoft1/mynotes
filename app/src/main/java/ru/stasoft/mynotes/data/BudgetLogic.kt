package ru.stasoft.mynotes.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Расход или доход, найденный в заметке по полям «Сумма:» / «Доход:». */
class Expense(val date: Long, val amount: Double, val category: String, val income: Boolean, val place: String, val noteId: Long)

private val NUM = Regex("""-?\d[\d\s\u00A0]*(?:[.,]\d+)?""")

/** Число в начале текста: «1 200,50 р» → 1200.5. */
fun leadingNumber(s: String): Double? =
    NUM.find(s.trim())?.value?.replace(Regex("[\\s\u00A0]"), "")?.replace(',', '.')?.toDoubleOrNull()

private val DATE = Regex("""(\d{1,2})\.(\d{1,2})(?:\.(\d{2,4}))?""")

/** Дата траты: поле «Дата:» (дд.мм.гггг, дд.мм.гг или дд.мм), иначе день создания заметки. */
fun expenseDate(n: NoteEntity): Long {
    val zone = ZoneId.systemDefault()
    val m = fieldText(n.body, "Дата")?.let { DATE.find(it) }
    if (m != null) {
        try {
            val created = Instant.ofEpochMilli(n.createdAt).atZone(zone).toLocalDate()
            val y = m.groupValues[3].let { if (it.isEmpty()) created.year else if (it.length == 2) 2000 + it.toInt() else it.toInt() }
            return LocalDate.of(y, m.groupValues[2].toInt(), m.groupValues[1].toInt()).atStartOfDay(zone).toInstant().toEpochMilli()
        } catch (_: Exception) {
        }
    }
    return n.createdAt
}

fun parseExpenses(notes: List<NoteEntity>): List<Expense> {
    val out = ArrayList<Expense>()
    for (n in notes) {
        if (n.isFolder) continue
        // Расход — поле «Расход:» или «Сумма:» вместе с «Категория:» / «Магазин:». Одинокая «Сумма:» (оклад, счёт и т.п.) не считается тратой.
        val explicit = fieldText(n.body, "Расход")?.let(::leadingNumber)
        val hasContext = !fieldText(n.body, "Категория").isNullOrBlank() || !fieldText(n.body, "Магазин").isNullOrBlank()
        val sum = explicit ?: if (hasContext) fieldText(n.body, "Сумма")?.let(::leadingNumber) else null
        val inc = fieldText(n.body, "Доход")?.let(::leadingNumber)
        if (sum == null && inc == null) continue
        val cat = fieldText(n.body, "Категория")?.takeIf { it.isNotBlank() } ?: "Без категории"
        val place = fieldText(n.body, "Магазин")?.takeIf { it.isNotBlank() } ?: n.title
        val d = expenseDate(n)
        if (sum != null) out.add(Expense(d, sum, cat, false, place, n.id))
        if (inc != null) out.add(Expense(d, inc, cat, true, place, n.id))
    }
    return out
}

class BudgetConfig(val categories: List<String>, val limits: Map<String, Double>)

/** Настройка: по строке на категорию, лимит на месяц через «=»: «Продукты = 20000». */
fun parseBudgetConfig(text: String): BudgetConfig {
    val cats = ArrayList<String>()
    val limits = LinkedHashMap<String, Double>()
    for (line in text.lines()) {
        val name = line.substringBefore('=').trim()
        if (name.isEmpty()) continue
        cats.add(name)
        if (line.contains('=')) leadingNumber(line.substringAfter('='))?.let { limits[name] = it }
    }
    return BudgetConfig(cats, limits)
}

fun monthKey(ms: Long): Int {
    val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    return d.year * 12 + (d.monthValue - 1)
}
