package ru.stasoft.mynotes.data

import java.time.LocalDate

/** Результат поиска папки дня: [existingId] — найденная папка; иначе в [parentId] нужно создать цепочку папок [toCreate] (последняя — сам день). */
class DayPlan(val existingId: Long?, val parentId: Long?, val toCreate: List<String>)

private val MONTHS_NOM = listOf("январь", "февраль", "март", "апрель", "май", "июнь", "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь")
private val MONTHS_GEN = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")
private val YEAR_ONLY = Regex("^\\s*(\\d{4})\\s*(г\\.?|год)?\\s*$", RegexOption.IGNORE_CASE)
private val YEAR_ANY = Regex("(?<!\\d)(20\\d{2})(?!\\d)")
private val MONTH_NUM = Regex("^\\s*(\\d{1,2})(?:\\.(\\d{4}))?\\s*$")

fun yearOfTitle(t: String): Int? = YEAR_ONLY.find(t)?.groupValues?.get(1)?.toIntOrNull()

/** Номер месяца по названию: «Ноябрь», «ноября 2026», «11», «11.2026». Если в названии есть год, он не учитывается здесь. */
fun monthOfTitle(t: String): Int? {
    val l = t.lowercase()
    for (i in 0 until 12) if (Regex("(?<![а-яё])" + MONTHS_NOM[i] + "(?![а-яё])").containsMatchIn(l) || Regex("(?<![а-яё])" + MONTHS_GEN[i] + "(?![а-яё])").containsMatchIn(l)) return i + 1
    val m = MONTH_NUM.find(t) ?: return null
    return m.groupValues[1].toIntOrNull()?.takeIf { it in 1..12 }
}

private fun kindOf(t: String): Int = when { yearOfTitle(t) != null -> 1; monthOfTitle(t) != null -> 2; else -> 0 }

private fun monthTitleLike(sample: String?, d: LocalDate): String {
    val name = MONTHS_NOM[d.monthValue - 1]
    if (sample == null) return name.replaceFirstChar { it.uppercase() }
    val s = sample.trim()
    val num = MONTH_NUM.find(s)
    if (num != null) {
        val w = num.groupValues[1].length
        val mm = if (w >= 2) "%02d".format(d.monthValue) else d.monthValue.toString()
        return if (num.groupValues[2].isNotEmpty()) "$mm.${d.year}" else mm
    }
    val gen = MONTHS_GEN.any { Regex("(?<![а-яё])$it(?![а-яё])", RegexOption.IGNORE_CASE).containsMatchIn(s) }
    var base = if (gen) MONTHS_GEN[d.monthValue - 1] else name
    val letters = s.filter { it.isLetter() }
    base = when {
        letters.length > 1 && letters.all { it.isUpperCase() } -> base.uppercase()
        letters.firstOrNull()?.isUpperCase() == true -> base.replaceFirstChar { it.uppercase() }
        else -> base
    }
    val y = YEAR_ANY.find(s)
    return if (y != null) "$base ${d.year}" else base
}

private fun dayTitleLike(sample: String, d: LocalDate): String {
    val m = Regex("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4}|\\d{2})").find(sample)
        ?: return "%02d.%02d.%02d".format(d.dayOfMonth, d.monthValue, d.year % 100)
    fun p(v: Int, w: Int) = if (w >= 2) "%02d".format(v) else v.toString()
    val y = if (m.groupValues[3].length == 4) d.year.toString() else "%02d".format(d.year % 100)
    return p(d.dayOfMonth, m.groupValues[1].length) + "." + p(d.monthValue, m.groupValues[2].length) + "." + y
}

/**
 * Ищет внутри папки [rootId] (null — весь главный экран) папку дня [day]. Если её нет, смотрит, как устроены уже
 * существующие папки дней (год → месяц → день, только месяцы и т. п.), и сообщает, что и где создать: недостающие год и месяц тоже.
 */
fun planDayPath(folders: List<FolderTitle>, rootId: Long?, day: LocalDate): DayPlan {
    val byId = folders.associateBy { it.id }
    val kids = folders.groupBy { it.parentId }
    // Все потомки корня.
    val sub = ArrayList<FolderTitle>()
    val stack = ArrayDeque<Long?>().apply { addLast(rootId) }
    while (stack.isNotEmpty()) for (f in kids[stack.removeLast()].orEmpty()) { sub.add(f); stack.addLast(f.id) }
    sub.firstOrNull { dateInTitle(it.title) == day }?.let { return DayPlan(it.id, null, emptyList()) }

    val days = sub.filter { dateInTitle(it.title) != null }
    val sample = days.maxByOrNull { dateInTitle(it.title)!! }
    // Уровни между корнем и образцом дня: год, месяц; прочие названия пропускаем.
    val layout = ArrayList<Pair<Int, String>>() // вид, название-образец
    if (sample == null) {
        layout.add(1 to day.year.toString()); layout.add(2 to monthTitleLike(null, day))
    } else {
        val chain = ArrayList<FolderTitle>()
        var c = byId[sample.parentId]
        var g = 0
        while (c != null && c.id != rootId && g++ < 20) { chain.add(0, c); c = byId[c.parentId] }
        chain.forEach { val k = kindOf(it.title); if (k != 0) layout.add(k to it.title) }
    }
    var cur: Long? = rootId
    var i = 0
    while (i < layout.size) {
        val (k, _) = layout[i]
        val hit = kids[cur].orEmpty().firstOrNull {
            when (k) {
                1 -> yearOfTitle(it.title) == day.year
                else -> monthOfTitle(it.title) == day.monthValue && (YEAR_ANY.find(it.title)?.groupValues?.get(1)?.toIntOrNull() ?: day.year) == day.year
            }
        } ?: break
        cur = hit.id
        i++
    }
    val create = ArrayList<String>()
    for (j in i until layout.size) {
        val (k, s) = layout[j]
        create.add(if (k == 1) day.year.toString() else monthTitleLike(s, day))
    }
    create.add(dayTitleLike(sample?.title ?: "", day))
    return DayPlan(null, cur, create)
}
