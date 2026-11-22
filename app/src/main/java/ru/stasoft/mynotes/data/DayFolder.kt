package ru.stasoft.mynotes.data

import java.time.LocalDate

/** Поиск и расчёт «папки дня»: папка с сегодняшней датой в названии внутри выбранной родительской. */
object DayFolder {
    private val NOM = listOf("январь", "февраль", "март", "апрель", "май", "июнь", "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь")
    private val GEN = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")

    /** Лежит ли [n] внутри [root] на любой глубине (root == null — вся база). */
    fun isUnder(n: NoteEntity, root: Long?, byId: Map<Long, NoteEntity>): Boolean {
        if (root == null) return true
        var c = n.parentId
        var g = 0
        while (c != null && g++ < 40) {
            if (c == root) return true
            c = byId[c]?.parentId
        }
        return false
    }

    fun find(notes: List<NoteEntity>, root: Long?, today: LocalDate): NoteEntity? {
        val byId = notes.associateBy { it.id }
        return notes.firstOrNull { it.isFolder && dateInTitle(it.title) == today && isUnder(it, root, byId) }
    }

    private fun like(src: String, w: String) = when {
        src.length > 1 && src == src.uppercase() -> w.uppercase()
        src.firstOrNull()?.isUpperCase() == true -> w.replaceFirstChar { it.uppercase() }
        else -> w
    }

    /** Название папки года или месяца для новой даты: «2026» → «2027», «Ноябрь» → «Декабрь», «11» → «12». */
    fun shiftTitle(title: String, from: LocalDate, to: LocalDate): String {
        var t = title.replace(Regex("(?<!\\d)${from.year}(?!\\d)"), to.year.toString())
        fun word(list: List<String>) {
            val w = list[from.monthValue - 1]
            t = Regex("(?<![\\p{L}])${w}(?![\\p{L}])", RegexOption.IGNORE_CASE).replace(t) { like(it.value, list[to.monthValue - 1]) }
        }
        word(NOM)
        word(GEN)
        val m = Regex("^(\\s*)0?${from.monthValue}(?!\\d)").find(t)
        if (m != null) {
            val wide = t.substring(m.groupValues[1].length).startsWith("0")
            t = m.groupValues[1] + (if (wide) "%02d".format(to.monthValue) else to.monthValue.toString()) + t.substring(m.range.last + 1)
        }
        return t
    }
}
