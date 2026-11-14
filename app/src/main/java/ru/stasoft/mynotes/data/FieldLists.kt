package ru.stasoft.mynotes.data

import org.json.JSONObject

/** Пользовательские списки значений полей: «Магазин» → Пятёрочка, Магнит. Хранятся в настройках как JSON. */
object FieldLists {
    fun parse(json: String): LinkedHashMap<String, List<String>> {
        val out = LinkedHashMap<String, List<String>>()
        try {
            val o = JSONObject(json.ifBlank { "{}" })
            for (k in o.keys()) {
                val a = o.getJSONArray(k)
                out[k] = (0 until a.length()).map { a.getString(it) }
            }
        } catch (_: Exception) {
        }
        return out
    }

    fun toJson(m: Map<String, List<String>>): String {
        val o = JSONObject()
        for ((k, v) in m) o.put(k, org.json.JSONArray(v))
        return o.toString()
    }

    /** Значения, подходящие к уже набранному началу [typed]; точное совпадение не предлагается. */
    fun filter(values: List<String>, typed: String): List<String> {
        val t = typed.trim()
        // Значение уже выбрано: показываем остальные, чтобы его можно было заменить одним нажатием.
        if (values.any { it.equals(t, ignoreCase = true) }) return values.filter { !it.equals(t, ignoreCase = true) }
        return values.filter { t.isEmpty() || (it.startsWith(t, ignoreCase = true) && !it.equals(t, ignoreCase = true)) }
    }

    /** Строка «Поле: значение», в которой стоит курсор: границы строки, название поля и набранное значение. */
    class FieldLine(val start: Int, val end: Int, val name: String, val typed: String)

    fun lineAt(text: String, cursor: Int): FieldLine? {
        val c = cursor.coerceIn(0, text.length)
        val start = if (c == 0) 0 else text.lastIndexOf('\n', c - 1) + 1
        val end = text.indexOf('\n', c).let { if (it < 0) text.length else it }
        val line = text.substring(start, end)
        val m = Regex("^([^:\\n]{1,30}):[ ]?(.*)$").find(line) ?: return null
        if (c - start < m.groupValues[1].length + 1) return null
        return FieldLine(start, end, m.groupValues[1].trim(), m.groupValues[2])
    }
}
