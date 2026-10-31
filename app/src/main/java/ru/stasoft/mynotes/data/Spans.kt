package ru.stasoft.mynotes.data

import org.json.JSONArray

/**
 * Оформление текста заметки. Сам текст остаётся простым (поля «Сумма:», суммы и поиск работают как раньше),
 * а оформление хранится отдельно как список диапазонов [start, end) с видом и значением.
 */
data class Span(val start: Int, val end: Int, val kind: String, val value: String = "")

object SpanKind {
    const val BOLD = "b"
    const val ITALIC = "i"
    const val UNDERLINE = "u"
    const val STRIKE = "s"
    const val COLOR = "c"
    const val BACKGROUND = "bg"
    const val SIZE = "sz"
    const val ALIGN = "al"

    /** Виды, которые наследует вводимый текст: выравнивание относится к строке, а не к символам. */
    val CHAR_KINDS = listOf(BOLD, ITALIC, UNDERLINE, STRIKE, COLOR, BACKGROUND, SIZE)
}

fun parseSpans(json: String, textLength: Int): List<Span> {
    if (json.isBlank()) return emptyList()
    return try {
        val arr = JSONArray(json)
        val out = ArrayList<Span>()
        for (i in 0 until arr.length()) {
            val a = arr.optJSONArray(i) ?: continue
            val s = a.optInt(0).coerceIn(0, textLength)
            val e = a.optInt(1).coerceIn(0, textLength)
            if (e > s) out.add(Span(s, e, a.optString(2), a.optString(3, "")))
        }
        normalize(out)
    } catch (_: Exception) {
        emptyList()
    }
}

fun spansToJson(spans: List<Span>): String {
    if (spans.isEmpty()) return ""
    val arr = JSONArray()
    spans.forEach { arr.put(JSONArray().put(it.start).put(it.end).put(it.kind).put(it.value)) }
    return arr.toString()
}

/** Убирает пустые диапазоны и склеивает соседние и пересекающиеся диапазоны одного вида и значения. */
fun normalize(spans: List<Span>): List<Span> {
    val out = ArrayList<Span>()
    spans.filter { it.end > it.start }
        .groupBy { it.kind to it.value }
        .forEach { (_, group) ->
            var cur: Span? = null
            for (s in group.sortedBy { it.start }) {
                val c = cur
                if (c != null && s.start <= c.end) cur = c.copy(end = maxOf(c.end, s.end))
                else {
                    if (c != null) out.add(c)
                    cur = s
                }
            }
            cur?.let { out.add(it) }
        }
    return out.sortedWith(compareBy({ it.start }, { it.kind }))
}

/** Задаёт вид оформления на диапазоне; [value] = null снимает этот вид с диапазона. */
fun applyStyle(spans: List<Span>, start: Int, end: Int, kind: String, value: String?): List<Span> {
    if (start >= end) return spans
    val out = ArrayList<Span>()
    for (s in spans) {
        if (s.kind != kind || s.end <= start || s.start >= end) {
            out.add(s)
            continue
        }
        if (s.start < start) out.add(s.copy(end = start))
        if (s.end > end) out.add(s.copy(start = end))
    }
    if (value != null) out.add(Span(start, end, kind, value))
    return normalize(out)
}

/** Значение вида, если оно покрывает весь диапазон [start, end) целиком; иначе null. */
fun coveredValue(spans: List<Span>, kind: String, start: Int, end: Int): String? {
    if (end <= start) return null
    return spans.firstOrNull { it.kind == kind && it.start <= start && it.end >= end }?.value
}

/** Оформление символа перед курсором (в начале текста — следующего): его наследует вводимый текст. */
fun styleAt(spans: List<Span>, pos: Int): Map<String, String> {
    val i = if (pos > 0) pos - 1 else 0
    val out = HashMap<String, String>()
    for (s in spans) {
        if (s.kind == SpanKind.ALIGN) continue
        if (s.start <= i && s.end > i) out[s.kind] = s.value
    }
    return out
}

/**
 * Пересчитывает диапазоны после правки текста: в позиции [prefix] убрано [removed] символов и вставлено [inserted].
 * [typing] — оформление для вставленного текста (вид -> значение, null — без этого вида).
 */
fun mapSpansForEdit(
    spans: List<Span>,
    prefix: Int,
    removed: Int,
    inserted: Int,
    typing: Map<String, String?>,
): List<Span> {
    val oldEnd = prefix + removed
    val delta = inserted - removed
    fun pos(p: Int, isEnd: Boolean): Int = when {
        p < prefix -> p
        p == prefix && removed == 0 -> if (isEnd) p else p + delta
        p >= oldEnd -> p + delta
        else -> prefix
    }
    var out = spans.mapNotNull { s ->
        // Выравнивание строки продолжается на вставленные строки.
        val extendAlign = s.kind == SpanKind.ALIGN && removed == 0 && inserted > 0 && s.end == prefix
        val ns = pos(s.start, false)
        val ne = if (extendAlign) s.end + inserted else pos(s.end, true)
        if (ne > ns) s.copy(start = ns, end = ne) else null
    }
    if (inserted > 0) {
        val range = prefix to prefix + inserted
        for (kind in SpanKind.CHAR_KINDS) {
            out = applyStyle(out, range.first, range.second, kind, typing[kind])
        }
    }
    return normalize(out)
}

/** Выравнивание строки, начинающейся в [lineStart]. */
fun alignAtLine(spans: List<Span>, lineStart: Int): String? =
    spans.firstOrNull { it.kind == SpanKind.ALIGN && it.start <= lineStart && it.end > lineStart }?.value
