package ru.stasoft.mynotes.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

class CarCost(val date: LocalDate, val category: String, val amount: Double)

class CarCostSummary(
    val items: List<CarCost>,
    val total: Double,
    val byCat: List<Pair<String, Double>>,
    val km: Double?,
    val lastOdo: Double?,
    val kmPerMonth: Double?,
    val perKm: Double?,
    val perMonth: Double?,
    val years: List<Int>,
)

/** Поля заметки о разовых расходах на машину: «Страховка: 12000». */
val CAR_EXTRA_FIELDS = listOf("Страховка", "Налог", "Штраф", "Парковка", "Мойка", "Прочее")

/** Работы, которые записываются как «Работа: …» и считаются в регламенте (с напоминанием по сроку). */
val CAR_DOC_WORKS = listOf("ОСАГО", "Техосмотр", "Смена шин")

fun workCategory(work: String): String {
    val s = work.lowercase()
    return when {
        "осаго" in s || "каско" in s || "страх" in s -> "Страховка"
        "техосмотр" in s || "диагностическ" in s -> "Техосмотр"
        "шин" in s || "колёс" in s || "колес" in s -> "Шины"
        else -> "ТО и ремонт"
    }
}

/** Все расходы на машину [car] (null — на все) за [year] (null — за всё время): топливо, ТО, ремонт, страховка, налоги и прочее. */
fun carCosts(notes: List<NoteEntity>, cars: List<CarEntity>, car: String?, year: Int?): CarCostSummary {
    val dflt = (cars.firstOrNull { it.isDefault } ?: cars.firstOrNull())?.name
    val items = ArrayList<CarCost>()
    val odos = ArrayList<Pair<LocalDate, Double>>()
    val years = sortedSetOf<Int>()
    for (n in notes) {
        if (n.isFolder) continue
        val body = n.body
        if (!isAutoNote(body) && CAR_EXTRA_FIELDS.none { fieldText(body, it) != null }) continue
        val name = fieldText(body, "Авто")?.takeIf { it.isNotBlank() } ?: dflt
        if (car != null && !name.equals(car, ignoreCase = true)) continue
        val date = noteLocalDate(n)
        years.add(date.year)
        if (year != null && date.year != year) continue
        parseNoteNumber(body, "Пробег")?.let { odos.add(date to it) }
        parseFill(body, date)?.let { items.add(CarCost(date, "Топливо", it.sum)) }
        val work = fieldText(body, "Работа") ?: fieldText(body, "ТО") ?: fieldText(body, "Ремонт")
        if (work != null) parseNoteSum(body, "Сумма")?.let { items.add(CarCost(date, workCategory(work), it)) }
        for (f in CAR_EXTRA_FIELDS) {
            val raw = fieldText(body, f) ?: continue
            (leadingNumber(raw) ?: parseNoteSum(body, "Сумма"))?.let { items.add(CarCost(date, f, it)) }
        }
    }
    val total = items.sumOf { it.amount }
    val byCat = items.groupBy { it.category }.map { (k, v) -> k to v.sumOf { it.amount } }.sortedByDescending { it.second }
    val first = odos.minByOrNull { it.second }
    val last = odos.maxByOrNull { it.second }
    val km = if (first != null && last != null && last.second > first.second) last.second - first.second else null
    val months = if (first != null && last != null) maxOf(1.0, ChronoUnit.DAYS.between(minOf(first.first, last.first), maxOf(first.first, last.first)) / 30.4) else null
    val span = if (items.isEmpty()) null else maxOf(1.0, ChronoUnit.DAYS.between(items.minOf { it.date }, items.maxOf { it.date }) / 30.4)
    return CarCostSummary(
        items, total, byCat, km, last?.second,
        if (km != null && months != null) km / months else null,
        if (km != null && km > 0 && total > 0) total / km else null,
        if (span != null && total > 0) total / span else null,
        years.toList(),
    )
}
