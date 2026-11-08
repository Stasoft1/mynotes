package ru.stasoft.mynotes.data

/** Совпадение продиктованной фразы с шаблоном: сам шаблон и остаток речи после его названия. */
class VoiceMatch(val template: SnippetEntity, val rest: String)

private val LEAD = Regex(
    "^\\s*(?:(?:создай|создать|добавь|добавить|сделай|новая|новую)\\s+)*(?:заметк\\p{L}*|запис\\p{L}*)?\\s*[,:;.\\-–—]*\\s*",
    RegexOption.IGNORE_CASE,
)
private val WORD = Regex("[\\p{L}\\p{N}-]+")
private val FIELD_LINE = Regex("^(\\s*)([^:\\n]{1,40}):(.*)$")

/** Убирает вводные слова «новая заметка» и т.п. в начале фразы. */
fun stripVoiceLead(s: String): String = s.replaceFirst(LEAD, "").trim()

private fun commonPrefix(a: String, b: String): Int {
    var i = 0
    while (i < a.length && i < b.length && a[i] == b[i]) i++
    return i
}

private fun wordMatches(spoken: String, name: String): Boolean {
    if (spoken == name) return true
    val p = commonPrefix(spoken, name)
    return p >= maxOf(3, name.length - 2) && p >= spoken.length - 3
}

/** Ищет в начале фразы название шаблона (по названию вставки или по заголовку заметки); падежи окончаний допускаются. */
fun matchVoiceTemplate(spoken: String, templates: List<SnippetEntity>): VoiceMatch? {
    val s = stripVoiceLead(spoken)
    val sw = WORD.findAll(s).toList()
    var best: SnippetEntity? = null
    var bestN = 0
    var bestEnd = 0
    for (t in templates) {
        val names = listOf(t.label, expandSnippet(t.title)).map { it.trim() }.filter { it.length >= 3 }.distinct()
        for (name in names) {
            val nw = WORD.findAll(name.lowercase()).map { it.value }.toList()
            if (nw.isEmpty() || sw.size < nw.size) continue
            if (nw.indices.all { wordMatches(sw[it].value.lowercase(), nw[it]) } && nw.size > bestN) {
                best = t
                bestN = nw.size
                bestEnd = sw[nw.size - 1].range.last + 1
            }
        }
    }
    val t = best ?: return null
    val rest = s.substring(bestEnd).trimStart(',', '.', ':', ';', '-', '–', ' ', '\n')
    return VoiceMatch(t, rest)
}

/** Названия полей шаблона: строки вида «Поле:». */
fun templateFieldKeys(text: String): List<String> =
    text.lines().mapNotNull { FIELD_LINE.find(it)?.groupValues?.get(2)?.trim()?.takeIf { k -> k.isNotEmpty() } }

private class Num(val start: Int, val end: Int, val value: String)

private val NUM_RE = Regex("(\\d+)\\s*(?:руб\\p{L}*|р\\.?)\\s*(\\d{1,2})\\s*коп\\p{L}*|(\\d+(?:[.,]\\d+)?)", RegexOption.IGNORE_CASE)

private fun extractNumbers(s: String): List<Num> = NUM_RE.findAll(s).map { m ->
    val v = if (m.groupValues[3].isNotEmpty()) m.groupValues[3].replace('.', ',')
    else m.groupValues[1] + "," + m.groupValues[2].padStart(2, '0')
    Num(m.range.first, m.range.last + 1, v)
}.toList()

private class Hint(val before: Regex?, val after: Regex?)

private fun hintFor(key: String): Hint {
    val k = key.lowercase()
    return when {
        k.contains("литр") -> Hint(Regex("^литр\\p{L}*$"), Regex("^\\s*(?:литр|л(?!\\p{L}))"))
        k.contains("цен") -> Hint(Regex("^(?:цен\\p{L}*|по)$"), Regex("^\\s*(?:руб\\p{L}*\\.?\\s*)?за\\s"))
        k.contains("сумм") || k.contains("итог") -> Hint(Regex("^(?:сумм\\p{L}*|итого|всего)$"), null)
        k.contains("пробег") || k.contains("одометр") -> Hint(Regex("^(?:пробег|одометр)\\p{L}*$"), Regex("^\\s*(?:км|километр)"))
        else -> {
            val stem = k.filter { it.isLetter() }.let { it.take(maxOf(4, it.length - 2)) }
            if (stem.isEmpty()) Hint(null, null) else Hint(Regex("^" + Regex.escape(stem) + "\\p{L}*$"), null)
        }
    }
}

/**
 * Раскладывает числа из речи по полям шаблона «Поле:» (только пустые). Возвращает новый текст и число заполненных полей.
 * Поле находит своё число по слову перед ним («пробег 54500») или после него («40 литров»).
 */
fun fillTemplateFromSpeech(templateText: String, rest: String): Pair<String, Int> {
    val speech = rest.replace(Regex("(?<=\\d)[\\s\\u00A0](?=\\d{3}(?!\\d))"), "")
    val nums = extractNumbers(speech)
    val used = BooleanArray(nums.size)
    var filled = 0
    val lines = templateText.split("\n").toMutableList()
    for (li in lines.indices) {
        val m = FIELD_LINE.find(lines[li]) ?: continue
        if (m.groupValues[3].isNotBlank()) continue
        val hint = hintFor(m.groupValues[2].trim())
        for (i in nums.indices) {
            if (used[i]) continue
            val before = speech.substring(if (i == 0) 0 else nums[i - 1].end, nums[i].start)
            val after = speech.substring(nums[i].end, if (i + 1 < nums.size) nums[i + 1].start else speech.length).lowercase()
            val lastWord = Regex("\\p{L}+").findAll(before).lastOrNull()?.value?.lowercase().orEmpty()
            val ok = (hint.before != null && lastWord.isNotEmpty() && hint.before.containsMatchIn(lastWord)) ||
                (hint.after != null && hint.after.containsMatchIn(after))
            if (ok) {
                used[i] = true
                lines[li] = m.groupValues[1] + m.groupValues[2] + ": " + nums[i].value
                filled++
                break
            }
        }
    }
    return lines.joinToString("\n") to filled
}

/** Подставляет значения из карты «поле → значение» (ответ ИИ) в пустые поля шаблона. */
fun fillTemplateFromMap(templateText: String, values: Map<String, String>): Pair<String, Int> {
    val low = values.mapKeys { it.key.trim().lowercase() }
    var filled = 0
    val lines = templateText.split("\n").toMutableList()
    for (li in lines.indices) {
        val m = FIELD_LINE.find(lines[li]) ?: continue
        if (m.groupValues[3].isNotBlank()) continue
        val v = low[m.groupValues[2].trim().lowercase()]?.trim().orEmpty()
        if (v.isNotEmpty() && v != "null") {
            lines[li] = m.groupValues[1] + m.groupValues[2] + ": " + v
            filled++
        }
    }
    return lines.joinToString("\n") to filled
}

/** Обычная заметка без шаблона: заголовок — первая фраза, остальное — текст. */
fun plainVoiceNote(spoken: String): Pair<String, String> {
    val s = stripVoiceLead(spoken).ifBlank { spoken.trim() }
    val cut = Regex("[,.:;!?]").find(s)?.range?.first ?: -1
    val title: String
    val body: String
    if (cut in 1..60) {
        title = s.substring(0, cut).trim()
        body = s.substring(cut + 1).trim()
    } else if (s.length <= 60) {
        title = s
        body = ""
    } else {
        val sp = s.lastIndexOf(' ', 60).takeIf { it > 10 } ?: 60
        title = s.substring(0, sp).trim()
        body = s.substring(sp).trim()
    }
    return title.replaceFirstChar { it.uppercase() } to body.replaceFirstChar { it.uppercase() }
}
