package ru.stasoft.mynotes.data

/**
 * Подстановка в текст шаблона при вставке в заметку:
 *  - {переменные} — строки «Поле: » для всех переменных папки (включая унаследованные);
 *  - {Имя} — текущее значение переменной с таким названием (например {Пробег} — последнее известное показание);
 *  - {сегодня}, {дата}, {время} — как раньше.
 */
fun expandWithVars(text: String, vars: List<VarDef>, lookup: (String) -> Double?): String {
    var out = expandSnippet(text)
    if (out.contains("{переменные}")) {
        val lines = vars
            .filter { it.name.isNotEmpty() && !it.off }
            .map { it.fieldName() }
            .distinct()
            .joinToString("\n") { "$it: " }
        out = out.replace("{переменные}", lines)
    }
    return Regex("\\{([^{}]+)\\}").replace(out) { m ->
        val n = m.groupValues[1].trim()
        val d = vars.firstOrNull { it.name.equals(n, ignoreCase = true) }
        if (d == null) m.value else lookup(d.name)?.let { plainNumber(it) }.orEmpty()
    }
}
