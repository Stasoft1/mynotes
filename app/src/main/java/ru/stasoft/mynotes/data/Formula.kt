package ru.stasoft.mynotes.data

/**
 * Формулы переменных: «Литры * ЦенаБ». Операции + − * / (а также × ÷), скобки, числа, имена переменных
 * (буквы, цифры, _) и произвольные названия в фигурных скобках: {Цена за литр}.
 * [lookup] возвращает число переменной в текущей заметке или null, если его нет. Любое отсутствующее значение,
 * ошибка в записи или деление на ноль дают null.
 */
fun evalFormula(expr: String, lookup: (String) -> Double?): Double? {
    val p = FormulaParser(expr, lookup)
    return try {
        val v = p.expr()
        p.skip()
        if (!p.atEnd()) null else v.takeIf { it.isFinite() }
    } catch (_: FormulaError) {
        null
    }
}

private class FormulaError : Exception()

private class FormulaParser(val s: String, val lookup: (String) -> Double?) {
    var i = 0
    fun atEnd() = i >= s.length
    fun skip() { while (i < s.length && s[i].isWhitespace()) i++ }

    fun expr(): Double {
        var v = term()
        while (true) {
            skip()
            if (atEnd()) return v
            when (s[i]) {
                '+' -> { i++; v += term() }
                '-', '−' -> { i++; v -= term() }
                else -> return v
            }
        }
    }

    fun term(): Double {
        var v = factor()
        while (true) {
            skip()
            if (atEnd()) return v
            when (s[i]) {
                '*', '×', '·' -> { i++; v *= factor() }
                '/', '÷' -> {
                    i++
                    val d = factor()
                    if (d == 0.0) throw FormulaError()
                    v /= d
                }
                else -> return v
            }
        }
    }

    fun factor(): Double {
        skip()
        if (atEnd()) throw FormulaError()
        val c = s[i]
        when {
            c == '-' || c == '−' -> { i++; return -factor() }
            c == '+' -> { i++; return factor() }
            c == '(' -> {
                i++
                val v = expr()
                skip()
                if (atEnd() || s[i] != ')') throw FormulaError()
                i++
                return v
            }
            c == '{' -> {
                val end = s.indexOf('}', i)
                if (end < 0) throw FormulaError()
                val name = s.substring(i + 1, end).trim()
                i = end + 1
                return lookup(name) ?: throw FormulaError()
            }
            c.isDigit() -> {
                val st = i
                while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == ',')) i++
                return s.substring(st, i).replace(',', '.').toDoubleOrNull() ?: throw FormulaError()
            }
            c.isLetter() || c == '_' -> {
                val st = i
                while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_')) i++
                return lookup(s.substring(st, i)) ?: throw FormulaError()
            }
        }
        throw FormulaError()
    }
}

/** Значение формулы переменной [d] в тексте заметки [text]; имена в формуле ищутся среди переменных [vars]. */
fun formulaValue(d: VarDef, text: String, vars: List<VarDef>): Double? {
    if (d.formula.isBlank()) return null
    return evalFormula(d.formula) { nm ->
        val field = vars.firstOrNull { it.name.equals(nm, ignoreCase = true) }?.fieldName() ?: nm
        parseNoteNumber(text, field)
    }
}

private fun sameNumber(a: Double?, b: Double?) = a != null && b != null && Math.abs(a - b) < 0.005

private fun formatForText(v: Double): String = plainNumber(Math.round(v * 100) / 100.0).replace('.', ',')

/** Заменяет значение в строке «Поле: …» (или добавляет строку в конец). */
private fun setField(text: String, field: String, value: String): String {
    val lines = text.split("\n").toMutableList()
    val key = field.trim().lowercase()
    val idx = lines.indexOfFirst { l ->
        val c = l.indexOf(':')
        c > 0 && l.substring(0, c).trim().lowercase() == key
    }
    if (idx >= 0) {
        val c = lines[idx].indexOf(':')
        lines[idx] = lines[idx].substring(0, c + 1) + " " + value
    } else {
        if (lines.last().isBlank() && lines.size > 1) lines[lines.size - 1] = "$field: $value" else lines.add("$field: $value")
    }
    return lines.joinToString("\n")
}

/**
 * Автоматический пересчёт формульных переменных в тексте заметки. [base] — текст при прошлом пересчёте.
 * Строку «Поле: …» обновляем, если её нет, она пуста или её значение совпадает с прежним результатом формулы
 * (то есть вписано автоматически). Вписанное вручную значение остаётся нетронутым.
 */
fun applyFormulas(base: String, body: String, vars: List<VarDef>): String {
    var out = body
    for (d in vars) {
        if (d.formula.isBlank() || d.off || d.agg == "calc") continue
        val now = formulaValue(d, out, vars) ?: continue
        val old = formulaValue(d, base, vars)
        val cur = parseNoteNumber(out, d.fieldName())
        val rawCur = out.lineSequence().firstOrNull { l ->
            val c = l.indexOf(':')
            c > 0 && l.substring(0, c).trim().equals(d.fieldName().trim(), ignoreCase = true)
        }?.substringAfter(':')?.trim()
        val auto = rawCur == null || rawCur.isEmpty() || sameNumber(cur, old) || sameNumber(cur, now)
        if (auto && !sameNumber(cur, now)) out = setField(out, d.fieldName(), formatForText(now))
    }
    return out
}
