package ru.stasoft.mynotes.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Состояние одной работы регламента ТО. level: 0 — в норме, 1 — скоро, 2 — просрочено, 3 — нет записей о последнем выполнении. */
data class ServiceStatus(
    val carName: String,
    val itemName: String,
    val remainingKm: Int?,
    val remainingDays: Int?,
    val lastKm: Int?,
    val lastDate: LocalDate?,
    val level: Int,
) {
    val key: String get() = "$carName|$itemName"

    fun text(): String {
        if (level == 3) return "нет записей о выполнении"
        val parts = ArrayList<String>()
        remainingKm?.let { parts.add(if (it < 0) "просрочено на ${-it} км" else "через $it км") }
        remainingDays?.let { parts.add(if (it < 0) "просрочено на ${-it} дн." else "через $it дн.") }
        return parts.joinToString(", ")
    }
}

/** Запись о машине из заметки: дата берётся из папки дня. */
class CarNote(val date: LocalDate?, val head: String)

const val SERVICE_SOON_KM = 1000
const val SERVICE_SOON_DAYS = 30

/**
 * Считает состояние ТО. Текущий пробег машины — максимальный «Пробег:» среди её заметок; последнее выполнение работы —
 * самая поздняя заметка машины, где в «Работа:» (или «ТО:») есть название работы. Интервал считается и по пробегу, и по времени:
 * срабатывает то, что наступит раньше. Заметки без «Авто:» относятся к машине по умолчанию.
 */
fun computeService(cars: List<CarEntity>, items: List<ServiceItemEntity>, notes: List<CarNote>, today: LocalDate): List<ServiceStatus> {
    val dflt = (cars.firstOrNull { it.isDefault } ?: cars.firstOrNull())?.name
    val byCar = notes.groupBy { (fieldText(it.head, "Авто")?.takeIf { v -> v.isNotBlank() } ?: dflt)?.lowercase() }
    val out = ArrayList<ServiceStatus>()
    for (c in cars) {
        val mine = byCar[c.name.lowercase()].orEmpty()
        val odo = mine.mapNotNull { parseNoteNumber(it.head, "Пробег") }.maxOrNull()
        for (it in items.filter { i -> i.carId == c.id }) {
            val key = it.name.trim().lowercase()
            val done = mine.filter { n ->
                val w = (fieldText(n.head, "Работа") ?: fieldText(n.head, "ТО") ?: fieldText(n.head, "Ремонт"))?.lowercase().orEmpty()
                key.isNotEmpty() && w.contains(key)
            }
            val last = done.maxWithOrNull(compareBy({ n -> n.date ?: LocalDate.MIN }, { n -> parseNoteNumber(n.head, "Пробег") ?: 0.0 }))
            if (last == null) {
                out.add(ServiceStatus(c.name, it.name, null, null, null, null, 3))
                continue
            }
            val lastKm = parseNoteNumber(last.head, "Пробег")?.toInt()
            val remKm = if (it.everyKm > 0 && lastKm != null && odo != null) lastKm + it.everyKm - odo.toInt() else null
            val remDays = if (it.everyMonths > 0 && last.date != null) {
                ChronoUnit.DAYS.between(today, last.date.plusMonths(it.everyMonths.toLong())).toInt()
            } else null
            val over = (remKm != null && remKm < 0) || (remDays != null && remDays < 0)
            val soon = (remKm != null && remKm <= SERVICE_SOON_KM) || (remDays != null && remDays <= SERVICE_SOON_DAYS)
            out.add(ServiceStatus(c.name, it.name, remKm, remDays, lastKm, last.date, if (over) 2 else if (soon) 1 else 0))
        }
    }
    return out
}
