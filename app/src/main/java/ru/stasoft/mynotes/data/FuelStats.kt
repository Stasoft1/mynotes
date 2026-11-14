package ru.stasoft.mynotes.data

import java.time.LocalDate

/** Одна заправка из заметки: Литры, ЦенаБ, СуммаБ, Пробег. */
data class Fill(val date: LocalDate?, val litres: Double, val price: Double, val sum: Double, val odo: Double?)

/** Участок между двумя заправками по пробегу (метод «до полного бака»: литры берутся с более поздней заправки). */
data class FuelLeg(val date: LocalDate?, val km: Double, val per100: Double, val costKm: Double)

data class FuelSummary(
    val fills: List<Fill>,
    val legs: List<FuelLeg>,
    val litres: Double,
    val money: Double,
    val avgPer100: Double?,
    val avgCostKm: Double?,
    val avgPrice: Double?,
)

fun parseFill(head: String, date: LocalDate?): Fill? {
    val litres = parseNoteNumber(head, "Литры") ?: return null
    if (litres <= 0) return null
    val sum0 = parseNoteSum(head, "СуммаБ") ?: parseNoteSum(head, "Сумма")
    val price0 = parseNoteNumber(head, "ЦенаБ") ?: parseNoteNumber(head, "Цена")
    val price = price0 ?: sum0?.let { it / litres } ?: return null
    val sum = sum0 ?: (price * litres)
    return Fill(date, litres, price, sum, parseNoteNumber(head, "Пробег"))
}

fun fuelSummary(all: List<Fill>): FuelSummary {
    val fills = all.sortedWith(compareBy({ it.date ?: LocalDate.MIN }, { it.odo ?: 0.0 }))
    val withOdo = fills.filter { it.odo != null }.sortedBy { it.odo }
    val legs = ArrayList<FuelLeg>()
    for (i in 1 until withOdo.size) {
        val km = withOdo[i].odo!! - withOdo[i - 1].odo!!
        if (km <= 0 || km > 3000) continue
        legs.add(FuelLeg(withOdo[i].date, km, withOdo[i].litres / km * 100, withOdo[i].sum / km))
    }
    val km = legs.sumOf { it.km }
    val lit = legs.sumOf { it.per100 * it.km / 100 }
    val cost = legs.sumOf { it.costKm * it.km }
    val litres = fills.sumOf { it.litres }
    val money = fills.sumOf { it.sum }
    return FuelSummary(
        fills, legs, litres, money,
        if (km > 0) lit / km * 100 else null,
        if (km > 0) cost / km else null,
        if (litres > 0) money / litres else null,
    )
}
