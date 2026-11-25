package ru.stasoft.mynotes.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.style.TextDecoration

/** Ссылка на заметку в тексте: [[id|Название]] занимает [s, e), название — [ts, te) (если оно есть). */
class LinkRange(val s: Int, val e: Int, val ts: Int, val te: Int, val id: Long, val title: String?) {
    val shown: String get() = title ?: ("заметка " + id)
    val len: Int get() = shown.length
}

private val LINK_RE = Regex("\\[\\[\\s*(\\d{1,18})\\s*(?:\\|([^\\]\\n]*))?\\]\\]")

fun noteLinkRanges(text: String): List<LinkRange> {
    if (!text.contains("[[")) return emptyList()
    val out = ArrayList<LinkRange>()
    for (m in LINK_RE.findAll(text)) {
        val id = m.groupValues[1].toLongOrNull() ?: continue
        val tg = m.groups[2]
        val raw = tg?.value ?: ""
        val title = raw.trim()
        if (tg != null && title.isNotEmpty()) {
            // Границы названия внутри ссылки: пробелы по краям оставляем в скрытой части.
            val lead = raw.length - raw.trimStart().length
            val ts = tg.range.first + lead
            out.add(LinkRange(m.range.first, m.range.last + 1, ts, ts + title.length, id, title))
        } else out.add(LinkRange(m.range.first, m.range.last + 1, m.range.last + 1, m.range.last + 1, id, null))
    }
    return out
}

/** Ссылка, к которой прилегает курсор или внутри которой он стоит. */
fun linkAtCursor(text: String, pos: Int): LinkRange? = noteLinkRanges(text).firstOrNull { pos in it.s..it.e }

/** Пересчёт позиций между исходным текстом («[[1|Имя]]») и показанным («Имя»). */
class LinkMap(val links: List<LinkRange>) {
    val identity get() = links.isEmpty()

    fun o2t(o: Int): Int {
        var shift = 0
        for (l in links) {
            val ds = l.s - shift
            if (o <= l.s) return o - shift
            if (o < l.e) {
                return if (l.title != null) {
                    when {
                        o <= l.ts -> ds
                        o < l.te -> ds + (o - l.ts)
                        else -> ds + l.len
                    }
                } else ds + l.len
            }
            if (o == l.e) return ds + l.len
            shift += (l.e - l.s) - l.len
        }
        return o - shift
    }

    fun t2o(t: Int): Int {
        var shift = 0
        for (l in links) {
            val ds = l.s - shift
            if (t <= ds) return t + shift
            val de = ds + l.len
            if (t < de) return if (l.title != null) l.ts + (t - ds) else l.e
            if (t == de) return l.e
            shift += (l.e - l.s) - l.len
        }
        return t + shift
    }

    /** Ссылка, на которую попадает позиция [t] показанного текста. */
    fun linkAtShown(t: Int): LinkRange? {
        var shift = 0
        for (l in links) {
            val ds = l.s - shift
            if (t in ds until ds + l.len) return l
            shift += (l.e - l.s) - l.len
        }
        return null
    }

    val offsets = object : OffsetMapping {
        override fun originalToTransformed(offset: Int) = o2t(offset)
        override fun transformedToOriginal(offset: Int) = t2o(offset)
    }

    /** Заменяет ссылки их названиями, сохраняя оформление (диапазоны переносятся) и выделяя ссылки цветом. */
    fun apply(src: AnnotatedString, linkColor: Color): AnnotatedString {
        if (links.isEmpty()) return src
        val sb = StringBuilder()
        var pos = 0
        for (l in links) {
            sb.append(src.text, pos, l.s)
            sb.append(l.shown)
            pos = l.e
        }
        sb.append(src.text, pos, src.text.length)
        val b = AnnotatedString.Builder(sb.toString())
        src.spanStyles.forEach { b.addStyle(it.item, o2t(it.start), o2t(it.end)) }
        src.paragraphStyles.forEach { b.addStyle(it.item, o2t(it.start), o2t(it.end)) }
        var shift = 0
        for (l in links) {
            val ds = l.s - shift
            b.addStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline), ds, ds + l.len)
            shift += (l.e - l.s) - l.len
        }
        return b.toAnnotatedString()
    }
}
