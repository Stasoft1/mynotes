package ru.stasoft.mynotes.data

/** Результат приведения заявки к шаблону Ф1. */
class Converted(val title: String, val body: String)

private val DEFAULT_F1 = listOf(
    "Договор:", "Телефон:", "Работа:", "Скорость:", "Примечание:", "wf name:", "wf pass:",
    "Кабель вп:", "Кабель кокс:", "Свитч:", "Порт:", "Сумма:", "Пробег:",
)

private val MASK = "[\\dхХxX*]"
private val PHONE_RE = Regex("(?:\\+7|(?<!\\d)8)[\\s(\\-]*$MASK{3}[\\s)\\-]*$MASK{3}[\\s\\-]*$MASK{2}[\\s\\-]*$MASK{2}")
private val CONTRACT_RE = Regex("Договор\\s*№?\\s*:?\\s*([\\p{L}\\p{N}*-]+)")
private val ADDR_RE = Regex("Адрес:\\s*(.*?)(?=,?\\s*(?:Парадная|Этаж|Квартира|Заявка)|$)")
private val STREET_TYPES = Regex("(?<!\\p{L})(?:ул|ул\\.|улица|пр-кт|пр-т|пр|пр\\.|просп|проспект|бул|бульвар|наб|набережная|пер|переулок|ш|шоссе)(?!\\p{L})\\.?", RegexOption.IGNORE_CASE)

fun looksLikeRequest(text: String): Boolean =
    Regex("Заявка:\\s*\\d+").containsMatchIn(text) || (text.contains("Данные клиента") && text.contains("Договор"))

private fun normPhone(s: String): String {
    var d = s.filter { it == '+' || it.isDigit() || it in "хХxX*" }
    if (d.startsWith("8") && d.length == 11) d = "+7" + d.substring(1)
    return d
}

private fun sentenceCase(s: String): String {
    val l = s.lowercase()
    return l.replaceFirstChar { it.uppercase() }
}

private fun formatAddress(addrRaw: String, flatIn: String?): String {
    var a = addrRaw.replace(Regex(",?\\s*(?:кв\\.?|квартира)\\s*\\.?\\s*\\d+\\w*\\s*$", RegexOption.IGNORE_CASE), "")
    a = a.replace(STREET_TYPES, " ").replace(",", " ").replace(Regex("\\s+"), " ").trim()
    val m = Regex("^(.*?)\\s+(\\d+[а-яА-Яa-zA-Z]?)(?:\\s*(?:к|корп\\.?|корпус|/)\\s*(\\d+))?(?:\\s.*)?$").find(a)
    var res = if (m != null) {
        val street = m.groupValues[1].trim()
        street + " " + m.groupValues[2] + (if (m.groupValues[3].isNotEmpty()) "-" + m.groupValues[3] else "")
    } else a
    if (!flatIn.isNullOrBlank()) res += "-$flatIn"
    return res
}

private fun vpFix(s: String): String =
    Regex("(?<!\\p{L})вп(?!\\p{L})", RegexOption.IGNORE_CASE).replace(s, "ВП")

private fun norm(s0: String): String {
    var t = vpFix(s0)
    t = Regex("Подкл\\.?\\s+интерн\\p{L}*", RegexOption.IGNORE_CASE).replace(t, "Подключение интернета")
    t = Regex("(?<!\\p{L})подкл\\.?(?!\\p{L})", RegexOption.IGNORE_CASE).replace(t, "подключение")
    t = Regex("(?<!\\p{L})тв(?!\\p{L})", RegexOption.IGNORE_CASE).replace(t, "ТВ")
    t = Regex("(\\d+)\\s*мб(?:ит)?(?:/с)?(?!\\p{L})", RegexOption.IGNORE_CASE).replace(t, "$1 Мбит")
    t = Regex("(\\d+)\\s*(?:руб|р)\\.?\\s*/\\s*(?:мес|м)(?!\\p{L})", RegexOption.IGNORE_CASE).replace(t, "$1 руб/мес")
    t = Regex("(\\d+)\\s*(?:руб|р)\\.?(?![\\p{L}/])", RegexOption.IGNORE_CASE).replace(t, "$1 руб")
    t = Regex("(\\d+)\\s*мес\\.?(?!\\p{L})", RegexOption.IGNORE_CASE).replace(t, "$1 мес")
    t = Regex("(\\d+)\\s*ч(?!\\p{L})").replace(t, "$1 ч")
    t = Regex("(?<=[\\p{L}\\p{N}])\\(").replace(t, " (")
    t = Regex(",(?=\\p{L})").replace(t, ", ")
    t = Regex(", ([А-ЯЁ])(?=[а-яё])").replace(t) { ", " + it.groupValues[1].lowercase() }
    return t
}

private fun fixWords(s: String): String {
    var t = s
    t = Regex("Интернет\\s+ОТТ").replace(t, "ОТТ")
    t = Regex("гиг\\.?\\s*комм\\.?", RegexOption.IGNORE_CASE).replace(t, "гигабитном коммутаторе")
    t = Regex("настройка\\s+вай\\s*фай", RegexOption.IGNORE_CASE).replace(t, "Настройка Wi-Fi")
    return t
}

/** Разбор заявки из рабочей программы и раскладка по шаблону Ф1. skeleton — строки шаблона (если нет, порядок по умолчанию). */
fun convertRequest(rawIn: String, skeleton: List<String>?): Converted? {
    if (!looksLikeRequest(rawIn)) return null
    val raw = rawIn.replace("\r", "").replace(Regex("(?<=\\S)(?=Договор)"), "\n")
    val lines = raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

    val contract = CONTRACT_RE.find(raw)?.groupValues?.get(1) ?: ""
    val phones = PHONE_RE.findAll(raw).map { normPhone(it.value) }.distinct().toList()

    val addrLine = lines.firstOrNull { it.contains("Адрес:") }
    val flat = Regex("Квартира:\\s*(\\w+)").find(raw)?.groupValues?.get(1)
        ?: Regex("(?<!\\p{L})кв\\.?\\s*(\\d+\\w*)", RegexOption.IGNORE_CASE).find(lines.firstOrNull() ?: "")?.groupValues?.get(1)
    val addrRaw = addrLine?.let { ADDR_RE.find(it)?.groupValues?.get(1) }
        ?: (lines.firstOrNull() ?: "").replace(Regex("Договор.*"), "")
    val address = formatAddress(addrRaw.trim(), flat)

    val di = lines.indexOfFirst { it.startsWith("Данные клиента") }
    val desc = when {
        di >= 0 && di + 1 < lines.size -> lines.drop(di + 1).joinToString(" ")
        else -> lines.maxByOrNull { it.length } ?: ""
    }
    val parts = desc.split(Regex("\\s//\\s?"), limit = 2)
    var left = parts[0].trim()
    val right = parts.getOrNull(1)?.trim() ?: ""

    val rightClean = right.replace(Regex("\\([^)]*\\)"), " ").trim()
    val sw = Regex("[A-Za-z][A-Za-z0-9]*(?:-[A-Za-z0-9]+)+").find(rightClean)?.value ?: ""
    val port = Regex("(?:^|\\s)(?:[рpРP]|порт)\\s*(\\d+)\\s*$", RegexOption.IGNORE_CASE).find(rightClean)?.groupValues?.get(1) ?: ""

    // Капслок-фразы («АКЦИЯ НЕ ДОСТУПНА») → отдельные предложения
    left = Regex("(?<!\\p{L})[А-ЯЁ]{2,}(?:\\s+[А-ЯЁ]{2,})+(?!\\p{L})").replace(left) { sentenceCase(it.value) + ". " }
    left = fixWords(left)
    var speed = ""
    val sentences = ArrayList<String>()
    var freeBuf: String? = null
    fun flush() { freeBuf?.let { sentences.add(it) }; freeBuf = null }
    for (seg0 in left.split(Regex("\\s*\\.\\s+|\\s*\\.$"))) {
        val seg = seg0.trim().trim('.').trim()
        if (seg.isEmpty()) continue
        val tp = Regex("ТП\\s*(\\d+)\\s*мб?\\p{L}*\\s*/\\s*(\\d+)\\s*р\\p{L}*", RegexOption.IGNORE_CASE).find(seg)
        val conn = Regex("^подключение\\s+(\\d+)\\s*р\\p{L}*", RegexOption.IGNORE_CASE).find(seg)
        when {
            tp != null -> { flush(); speed = tp.groupValues[1] + " мбит"; sentences.add("Тариф ${tp.groupValues[1]} мбит / ${tp.groupValues[2]} руб") }
            conn != null -> { flush(); sentences.add("Подключение ${conn.groupValues[1]} руб") }
            seg.startsWith("Настройка Wi-Fi") -> { flush(); sentences.add(seg) }
            seg.first().isLowerCase() -> {
                val n = norm(seg).replace(Regex("(?<=[\\p{L}\\p{N}]) (?=ВП)"), ", ")
                val cur = freeBuf
                if (cur != null && cur.trim().split(" ").size <= 2) freeBuf = "$cur, $n"
                else { flush(); freeBuf = n.replaceFirstChar { it.uppercase() } }
            }
            else -> { flush(); sentences.add(norm(seg).replaceFirstChar { it.uppercase() }) }
        }
    }
    flush()
    if (speed.isEmpty()) Regex("(\\d+)\\s*мб", RegexOption.IGNORE_CASE).find(left)?.let { speed = it.groupValues[1] + " мбит" }
    val note = sentences.joinToString(" ") { if (it.endsWith(".")) it else "$it." }

    val vals = mapOf(
        "адрес" to address, "договор" to contract, "телефон" to phones.joinToString(", "),
        "скорость" to speed, "примечание" to note, "wf name" to sw, "свитч" to sw, "порт" to port,
    )
    val sk = skeleton?.map { it.trimEnd() }?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() } ?: DEFAULT_F1
    val out = sk.mapNotNull { line ->
        val c = line.indexOf(':')
        if (c <= 0) return@mapNotNull line
        val key = line.substring(0, c).trim()
        if (key.lowercase() == "адрес") return@mapNotNull null
        val v = vals[key.lowercase()]
        if (v.isNullOrBlank()) line.substring(0, c + 1) + line.substring(c + 1).let { if (it.isBlank()) "" else it }
        else "$key: $v"
    }
    return Converted(address, out.joinToString("\n"))
}
