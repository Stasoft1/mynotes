package ru.stasoft.mynotes.data

/** Текст поля «Поле: значение» из заметки (строки целиком); null — поля нет. Название без учёта регистра. */
fun fieldText(body: String, field: String): String? {
    val f = field.trim().lowercase()
    for (line in body.lineSequence()) {
        val i = line.indexOf(':')
        if (i <= 0) continue
        if (line.substring(0, i).trim().trim('{', '}').trim().lowercase() == f) return line.substring(i + 1).trim()
    }
    return null
}

private val AUTO_FIELDS = listOf("литры", "работа", "то", "ремонт", "пробег", "ценаб", "суммаб")

/** Заметка про машину: есть хотя бы одно из полей заправки или ТО. */
fun isAutoNote(body: String): Boolean = AUTO_FIELDS.any { fieldText(body, it) != null }

/** Машина по сказанному: сначала названия и короткие имена целиком, затем по началу слова (падежи: «октавии»). */
fun pickCar(spoken: String, cars: List<CarEntity>): CarEntity? {
    val words = spoken.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
    fun names(c: CarEntity) = (listOf(c.name) + c.aliases.split(',')).map { it.trim().lowercase() }.filter { it.length >= 2 }
    cars.firstOrNull { c -> names(c).any { n -> n in words || words.contains(n) } }?.let { return it }
    return cars.firstOrNull { c ->
        names(c).any { n -> val stem = n.dropLast(if (n.length > 4) 1 else 0); words.any { w -> w.startsWith(stem) && w.length <= n.length + 2 } }
    }
}

/** Добавляет в конец текста строку «Авто: …» (в конец, чтобы не сдвигать оформление) или заполняет пустую. */
fun withCarLine(body: String, car: String): String {
    val existing = fieldText(body, "Авто")
    if (existing == null) return body.trimEnd('\n') + "\nАвто: $car"
    if (existing.isNotBlank()) return body
    return body.lines().joinToString("\n") { line ->
        if (line.substringBefore(':').trim().trim('{', '}').trim().equals("Авто", true) && line.contains(':')) line.substringBefore(':') + ": $car" else line
    }
}
