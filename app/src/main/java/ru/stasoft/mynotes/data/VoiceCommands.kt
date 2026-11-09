package ru.stasoft.mynotes.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Команды, вплетённые в диктовку: «в виде списка», «установи иконку … цвета …», «напомни завтра в 9».
 * [text] — фраза без этих команд; список: [listBefore] — что сказано до слов «в виде списка» (название), [listAfter] — после (пункты).
 */
class VoiceCommands(
    val text: String,
    val listBefore: String?,
    val listAfter: String?,
    val iconWords: List<String>?,
    val color: Int?,
    val remindAt: Long?,
) {
    val isList get() = listBefore != null
}

private val LIST_RE = Regex(
    "(?:^|\\s)(?:сделай\\s+)?(?:в\\s+виде\\s+списка|виде\\s+списка|списком|как\\s+список|чек-?лист\\p{L}*|список\\s+задач)(?=[\\s,.:;!?]|$)",
    RegexOption.IGNORE_CASE,
)
private val ICON_RE = Regex(
    "(?:(?:установи|поставь|добавь|сделай|выбери|задай)\\s+)?(?:(\\p{L}+)\\s+)?иконк\\p{L}*\\s*([^,.;:!?]*)",
    RegexOption.IGNORE_CASE,
)
private val REMIND_RE = Regex(
    "(?:напомни\\p{L}*|напоминани\\p{L}*)(?:\\s+мне)?\\s+([^,;!?]*?)(?=\\s*[,;!?]|\\s*\\.(?:\\s|$)|$)",
    RegexOption.IGNORE_CASE,
)

/** Цвета по основам слов («красн» — красный, красного, красную…). */
private val COLORS = listOf(
    "красн" to 0xFFE53935, "бордов" to 0xFF8E1A2B, "оранж" to 0xFFFB8C00, "жёлт" to 0xFFFDD835, "желт" to 0xFFFDD835,
    "золот" to 0xFFFFC107, "салат" to 0xFF9CCC65, "зелён" to 0xFF43A047, "зелен" to 0xFF43A047, "бирюз" to 0xFF00ACC1,
    "голуб" to 0xFF29B6F6, "син" to 0xFF1E88E5, "фиолет" to 0xFF8E24AA, "сирен" to 0xFFBA68C8, "розов" to 0xFFEC407A,
    "коричнев" to 0xFF6D4C41, "сер" to 0xFF757575, "чёрн" to 0xFF212121, "черн" to 0xFF212121, "бел" to 0xFFFFFFFF,
)

fun colorFromWord(word: String): Int? {
    val w = word.lowercase()
    return COLORS.firstOrNull { w.startsWith(it.first) }?.second?.toInt()
}

private val FILLER = setOf("цвета", "цвет", "цветом", "в", "со", "с", "такую", "такой", "для", "заметки", "заметке", "на", "эту", "эта")

private val MONTHS = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")
private val WEEKDAYS = listOf(
    "понедельник" to DayOfWeek.MONDAY, "вторник" to DayOfWeek.TUESDAY, "сред" to DayOfWeek.WEDNESDAY, "четверг" to DayOfWeek.THURSDAY,
    "пятниц" to DayOfWeek.FRIDAY, "суббот" to DayOfWeek.SATURDAY, "воскресень" to DayOfWeek.SUNDAY,
)

/** «завтра в 9 утра», «через 2 часа», «в понедельник в 18:30», «10 ноября» → момент времени (мс) или null. */
fun parseWhen(phrase: String, now: LocalDateTime = LocalDateTime.now()): Long? {
    var p = phrase.lowercase()
    val zone = ZoneId.systemDefault()
    fun ms(dt: LocalDateTime) = dt.atZone(zone).toInstant().toEpochMilli()

    var date: LocalDate? = null
    var explicitDate = false
    Regex("через\\s+(?:(\\d+)\\s*)?(полчаса|минут\\p{L}*|час\\p{L}*|дн\\p{L}*|день|недел\\p{L}*)").find(p)?.let { m ->
        val n = m.groupValues[1].toLongOrNull() ?: 1L
        val u = m.groupValues[2]
        p = p.removeRange(m.range)
        when {
            u.startsWith("полчаса") -> return ms(now.plusMinutes(30).withSecond(0).withNano(0))
            u.startsWith("минут") -> return ms(now.plusMinutes(n).withSecond(0).withNano(0))
            u.startsWith("час") -> return ms(now.plusHours(n).withSecond(0).withNano(0))
            u.startsWith("недел") -> { date = now.toLocalDate().plusWeeks(n); explicitDate = true }
            else -> { date = now.toLocalDate().plusDays(n); explicitDate = true }
        }
    }
    if (date == null) {
        val today = now.toLocalDate()
        when {
            p.contains("послезавтра") -> { date = today.plusDays(2); p = p.replace("послезавтра", " ") }
            p.contains("завтра") -> { date = today.plusDays(1); p = p.replace("завтра", " ") }
            p.contains("сегодня") -> { date = today; p = p.replace("сегодня", " ") }
        }
        if (date != null) explicitDate = true
    }
    if (date == null) {
        Regex("(\\d{1,2})\\s+(${MONTHS.joinToString("|")})").find(p)?.let { m ->
            val month = MONTHS.indexOf(m.groupValues[2]) + 1
            val day = m.groupValues[1].toInt()
            try {
                var d = LocalDate.of(now.year, month, day)
                if (d.isBefore(now.toLocalDate())) d = d.plusYears(1)
                date = d
                explicitDate = true
                p = p.removeRange(m.range)
            } catch (_: Exception) {
            }
        }
    }
    if (date == null) {
        for ((stem, dow) in WEEKDAYS) {
            val m = Regex("(?<!\\p{L})$stem\\p{L}*").find(p) ?: continue
            var d = now.toLocalDate().plusDays(1)
            while (d.dayOfWeek != dow) d = d.plusDays(1)
            date = d
            explicitDate = true
            p = p.removeRange(m.range)
            break
        }
    }
    var time: LocalTime? = null
    Regex("(?:(?:в|к|на|около)\\s+(\\d{1,2})(?:[:.](\\d{2}))?|(\\d{1,2})[:.](\\d{2}))\\s*(утра|вечера|дня|ночи)?").find(p)?.let { m ->
        var h = (m.groupValues[1].ifEmpty { m.groupValues[3] }).toInt()
        val min = (m.groupValues[2].ifEmpty { m.groupValues[4] }).ifEmpty { "0" }.toInt()
        when (m.groupValues[5]) {
            "вечера" -> if (h in 1..11) h += 12
            "дня" -> if (h in 1..11) h += 12
            "ночи" -> if (h == 12) h = 0
        }
        if (h in 0..23 && min in 0..59) time = LocalTime.of(h, min)
    }
    if (time == null) {
        time = when {
            p.contains("утром") -> LocalTime.of(9, 0)
            p.contains("днём") || p.contains("днем") -> LocalTime.of(13, 0)
            p.contains("вечером") -> LocalTime.of(19, 0)
            else -> null
        }
    }
    if (date == null && time == null) return null
    val t = time ?: LocalTime.of(9, 0)
    var d = date ?: now.toLocalDate()
    var dt = LocalDateTime.of(d, t)
    if (!explicitDate && !dt.isAfter(now)) dt = dt.plusDays(1)
    return ms(dt)
}

/** Вырезает из фразы команды; остальное — содержимое заметки. */
fun extractVoiceCommands(spoken: String, now: LocalDateTime = LocalDateTime.now()): VoiceCommands {
    var t = " " + spoken.trim()
    var iconWords: List<String>? = null
    var color: Int? = null
    var remind: Long? = null

    ICON_RE.find(t)?.let { m ->
        val pre = m.groupValues[1]
        val words = Regex("\\p{L}[\\p{L}-]*").findAll(m.groupValues[2]).map { it.value.lowercase() }.filter { it !in FILLER }.toMutableList()
        color = colorFromWord(pre)
        val colorIdx = words.indexOfFirst { colorFromWord(it) != null }
        if (colorIdx >= 0) {
            if (color == null) color = colorFromWord(words[colorIdx])
            words.removeAt(colorIdx)
        }
        iconWords = words
        t = t.removeRange(m.range)
    }
    REMIND_RE.find(t)?.let { m ->
        val w = parseWhen(m.groupValues[1], now)
        if (w != null) {
            remind = w
            t = t.removeRange(m.range)
        }
    }
    t = t.replace(Regex("\\s+"), " ").trim().trimStart(',', '.', ':', ';', ' ')
    var before: String? = null
    var after: String? = null
    LIST_RE.find(t)?.let { m ->
        before = t.substring(0, m.range.first).trim().trim(',', '.', ':', ';', ' ')
        after = t.substring(m.range.last + 1).trim().trimStart(',', '.', ':', ';', ' ')
        t = (before + " " + after).trim()
    }
    return VoiceCommands(t, before, after, iconWords, color, remind)
}

private val UNIT_WORDS = setOf("штук", "штуки", "штука", "шт", "кг", "килограмм", "килограмма", "г", "грамм", "грамма", "литр", "литра", "литров", "л", "пачки", "пачка", "пачек", "упаковка", "упаковки", "бутылки", "бутылка", "бутылок")

/** Пункты списка из продиктованного текста: по запятым, точкам, «потом», «и»; если разделителей нет — по словам. */
fun splitListItems(text: String): List<String> {
    val s = text.trim()
    if (s.isEmpty()) return emptyList()
    var parts = s.split(Regex("\\s*(?:\\n|[,;]|\\.(?:\\s|$)|\\s+(?:потом|затем|далее|следующий пункт|новый пункт|ещё|еще)\\s+)\\s*")).map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size == 1) parts = parts[0].split(Regex("\\s+и\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size == 1 && parts[0].count { it == ' ' } in 1..9) {
        val items = ArrayList<String>()
        for (w in parts[0].split(Regex("\\s+"))) {
            if (items.isNotEmpty() && (w.first().isDigit() || w.lowercase() in UNIT_WORDS)) items[items.lastIndex] = items.last() + " " + w
            else items.add(w)
        }
        parts = items
    }
    return parts.map { it.removePrefix("и ").trim() }.filter { it.isNotEmpty() }.map { it.replaceFirstChar { c -> c.uppercase() } }
}

/** Русские слова → имена иконок Material Design Icons (по началу слова). */
private val ICON_WORDS = listOf(
    "бензин" to "gas-station", "заправ" to "gas-station", "колонк" to "gas-station", "машин" to "car", "авто" to "car",
    "грузов" to "truck", "фур" to "truck", "автобус" to "bus", "поезд" to "train", "самол" to "airplane", "велосипед" to "bike",
    "мото" to "motorbike", "дом" to "home", "квартир" to "home", "офис" to "office-building", "магазин" to "store",
    "банк" to "bank", "деньг" to "cash", "рубл" to "currency-rub", "кошел" to "wallet", "карт" to "credit-card",
    "корзин" to "cart", "покупк" to "cart", "ключ" to "key", "замок" to "lock", "инструмент" to "toolbox", "молот" to "hammer",
    "гаечн" to "wrench", "отвёртк" to "screwdriver", "отвертк" to "screwdriver", "молни" to "flash", "электр" to "flash",
    "лампочк" to "lightbulb", "лампа" to "lightbulb", "шестерн" to "cog", "настройк" to "cog", "лестниц" to "ladder",
    "телефон" to "phone", "смартфон" to "cellphone", "ноутбук" to "laptop", "монитор" to "monitor", "телевизор" to "television",
    "роутер" to "router-wireless", "вайфай" to "wifi", "кабел" to "ethernet-cable", "сервер" to "server", "антенн" to "antenna",
    "принтер" to "printer", "камер" to "camera", "фото" to "camera", "почт" to "email", "письм" to "email",
    "сообщен" to "message-text", "колокол" to "bell", "уведомлен" to "bell", "календар" to "calendar", "часы" to "clock",
    "будильник" to "alarm", "звезд" to "star", "звёзд" to "star", "сердц" to "heart", "флаг" to "flag", "закладк" to "bookmark",
    "щит" to "shield-check", "галочк" to "check-circle", "вопрос" to "help-circle", "внимани" to "alert", "восклиц" to "alert",
    "человек" to "account", "пользовател" to "account", "люди" to "account-group", "групп" to "account-group",
    "собак" to "paw", "кошк" to "paw", "лапа" to "paw", "торт" to "cake-variant", "еда" to "food", "продукт" to "food",
    "кофе" to "coffee", "пицц" to "pizza", "лекарств" to "pill", "таблетк" to "pill", "больниц" to "hospital",
    "здоровь" to "heart-pulse", "гантел" to "dumbbell", "спорт" to "dumbbell", "солнц" to "weather-sunny", "снежин" to "snowflake",
    "дерев" to "tree", "книг" to "book-open-variant", "документ" to "file-document", "список" to "format-list-checks",
    "маркер" to "map-marker", "карта" to "map-marker", "земл" to "earth", "портфел" to "briefcase", "работ" to "briefcase",
    "калькулятор" to "calculator", "музык" to "music", "наушник" to "headphones", "игр" to "gamepad-variant",
    "подар" to "gift", "воздушный" to "airballoon", "ремонт" to "car-wrench", "аккумулятор" to "car-battery",
)

/** Подбирает имя иконки по словам: русский словарь или английское имя из набора; has проверяет, что иконка есть в наборе. */
fun resolveIconName(words: List<String>, has: (String) -> Boolean): String? {
    if (words.isEmpty()) return null
    val joined = words.joinToString("-")
    if (has(joined)) return joined
    for (w in words) {
        if (has(w)) return w
        val hit = ICON_WORDS.firstOrNull { w.startsWith(it.first) }?.second
        if (hit != null && has(hit)) return hit
    }
    return null
}
