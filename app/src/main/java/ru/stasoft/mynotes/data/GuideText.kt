package ru.stasoft.mynotes.data

/** Заметка из текста руководства: простой текст и диапазоны оформления. */
class GuideNote(val text: String, val spans: List<Span>)

private const val CODE_BG = "579" // 0x22888888: светлая серая подложка, годится и для тёмной темы
private val INLINE_RE = Regex("\\*\\*(.+?)\\*\\*|`([^`]+)`")
private val KEY_RE = Regex("\\{\\{([a-z0-9_]+)(?:\\|([^}]*))?\\}\\}")

/**
 * Превращает разметку справки (заголовки «##», «- » списки, **жирный**, `код`, блоки ``` и таблицы) в текст заметки с оформлением.
 * Ссылки вида {{ключ}} или {{ключ|подпись}} заменяются на [[id|название]] через [resolve] (ключ → id и название записи).
 */
fun markdownToNote(md: String, resolve: (String) -> Pair<Long, String>?): GuideNote {
    val sb = StringBuilder()
    val spans = ArrayList<Span>()
    var blank = false
    /** Начало новой строки; после пустой строки исходника (или если [gap]) оставляет пустую строку. */
    fun startLine(gap: Boolean = false) {
        if (sb.isNotEmpty()) { sb.append('\n'); if (blank || gap) sb.append('\n') }
        blank = false
    }
    fun inline(src: String) {
        // ссылки на другие записи
        val linked = KEY_RE.replace(src) { m ->
            val r = resolve(m.groupValues[1])
            if (r == null) m.groupValues[2].ifEmpty { m.groupValues[1] }
            else "[[" + r.first + "|" + (m.groupValues[2].ifEmpty { r.second }) + "]]"
        }
        var i = 0
        for (m in INLINE_RE.findAll(linked)) {
            sb.append(linked, i, m.range.first)
            if (m.groups[1] != null) {
                val st = sb.length
                sb.append(m.groupValues[1])
                spans.add(Span(st, sb.length, SpanKind.BOLD))
            } else sb.append(m.groupValues[2])
            i = m.range.last + 1
        }
        sb.append(linked, i, linked.length)
    }
    val lines = md.lines()
    var k = 0
    var skippedTitle = false
    while (k < lines.size) {
        val l = lines[k]
        when {
            !skippedTitle && l.startsWith("# ") -> { skippedTitle = true }
            l.startsWith("## ") || l.startsWith("### ") -> {
                startLine(gap = true)
                val h2 = l.startsWith("## ")
                val st = sb.length
                inline(l.removePrefix(if (h2) "## " else "### "))
                spans.add(Span(st, sb.length, SpanKind.BOLD))
                spans.add(Span(st, sb.length, SpanKind.SIZE, if (h2) "20" else "17"))
            }
            l.startsWith("```") -> {
                k++
                startLine()
                val st = sb.length
                var first = true
                while (k < lines.size && !lines[k].startsWith("```")) {
                    if (!first) sb.append('\n')
                    sb.append(lines[k]); first = false; k++
                }
                if (sb.length > st) spans.add(Span(st, sb.length, SpanKind.BACKGROUND, CODE_BG))
            }
            l.startsWith("- ") -> { startLine(); sb.append("• "); inline(l.removePrefix("- ")) }
            l.startsWith("> ") -> { startLine(); val st = sb.length; inline(l.removePrefix("> ")); spans.add(Span(st, sb.length, SpanKind.ITALIC)) }
            l.startsWith("|") -> {
                val cells = l.trim().trim('|').split('|').map { it.trim() }
                if (!cells.all { c -> c.all { it == '-' || it == ':' } }) {
                    startLine()
                    sb.append("• ")
                    val st = sb.length
                    inline(cells.first())
                    spans.add(Span(st, sb.length, SpanKind.BOLD))
                    sb.append(" — ")
                    inline(cells.drop(1).joinToString(" · "))
                }
            }
            l.isBlank() -> blank = true
            else -> { startLine(); inline(l) }
        }
        k++
    }
    val trimmed = sb.toString().trimEnd('\n')
    return GuideNote(trimmed, spans.filter { it.start < trimmed.length }.map { if (it.end > trimmed.length) it.copy(end = trimmed.length) else it })
}
