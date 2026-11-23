package ru.stasoft.mynotes.data

import java.time.LocalDate
import java.time.YearMonth

/** Счётчик из настроек: «Электричество = кВт·ч = 6,5» (название = единицы = тариф за единицу). */
class UtilMeter(val name: String, val unit: String, val tariff: Double)

fun parseUtilMeters(text: String): List<UtilMeter> = text.lines().mapNotNull { l ->
    val parts = l.split('=').map { it.trim() }
    if (parts[0].isEmpty()) null
    else UtilMeter(parts[0], parts.getOrNull(1).orEmpty(), parts.getOrNull(2)?.let { leadingNumber(it) } ?: 0.0)
}

/** Расход за период между двумя показаниями. */
class UtilUse(val date: LocalDate, val reading: Double, val used: Double, val cost: Double)

class UtilMonth(val month: YearMonth, val perMeter: List<Pair<UtilMeter, UtilUse>>, val total: Double)

/** Показания счётчика в хронологии и расход по каждому следующему показанию; уменьшение значения (замена счётчика) пропускается. */
fun utilUses(notes: List<NoteEntity>, m: UtilMeter): List<UtilUse> {
    val rs = parseMeasures(notes, m.name)
    val out = ArrayList<UtilUse>()
    for (i in rs.indices) {
        val used = if (i == 0) 0.0 else rs[i].v - rs[i - 1].v
        out.add(UtilUse(rs[i].date, rs[i].v, if (used < 0) 0.0 else used, if (used < 0) 0.0 else used * m.tariff))
    }
    return out
}

/** Помесячная сводка: расход относится к месяцу, в котором сняты более поздние показания. Первое показание расхода не даёт. */
fun utilMonths(notes: List<NoteEntity>, meters: List<UtilMeter>, fixed: Double): List<UtilMonth> {
    val byMonth = sortedMapOf<YearMonth, MutableList<Pair<UtilMeter, UtilUse>>>()
    for (m in meters) {
        val uses = utilUses(notes, m)
        for (i in 1 until uses.size) byMonth.getOrPut(YearMonth.from(uses[i].date)) { ArrayList() }.add(m to uses[i])
    }
    return byMonth.map { (k, v) -> UtilMonth(k, v, v.sumOf { it.second.cost } + fixed) }
}
