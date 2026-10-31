package ru.stasoft.mynotes.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Переменная папки: поле «Имя: число» в тексте заметок. Объявляется в свойствах папки и действует на неё и все
 * вложенные папки. Пустое имя — число в первой строке заметки (как в MyTreeNotes).
 * [agg] — как сводить значения вверх по дереву: sum, max, min или none (объявлена, но в списке не показывается).
 * [off] — в этой папке унаследованная переменная отключена.
 */
data class VarDef(
    val name: String,
    val agg: String = "sum",
    val off: Boolean = false,
    /** Поле в тексте заметки, если оно называется иначе, чем переменная (пусто — как название). */
    val field: String = "",
)

fun VarDef.fieldName(): String = field.ifEmpty { name }

val AGG_LABELS = linkedMapOf(
    "sum" to "Сумма",
    "max" to "Максимум",
    "min" to "Минимум",
    "span" to "Прирост (пробег за период)",
    "none" to "Не показывать",
)

fun varLabel(name: String): String = name.ifEmpty { "Первая строка" }

fun parseVars(json: String): List<VarDef> {
    if (json.isBlank()) return emptyList()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            VarDef(
                o.optString("n", ""),
                o.optString("a", "sum").ifEmpty { "sum" },
                o.optBoolean("off", false),
                o.optString("f", ""),
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

fun varsToJson(list: List<VarDef>): String {
    if (list.isEmpty()) return ""
    val arr = JSONArray()
    list.forEach {
        arr.put(JSONObject().put("n", it.name).put("a", it.agg).apply {
            if (it.off) put("off", true)
            if (it.field.isNotEmpty()) put("f", it.field)
        })
    }
    return arr.toString()
}

/** Набор переменных на уровне папки: унаследованные + собственные (собственные заменяют или отключают). */
fun applyVars(base: List<VarDef>, own: List<VarDef>): List<VarDef> {
    val out = base.toMutableList()
    for (d in own) {
        val i = out.indexOfFirst { it.name == d.name }
        if (d.off) {
            if (i >= 0) out.removeAt(i)
        } else if (i >= 0) {
            out[i] = d
        } else {
            out.add(d)
        }
    }
    return out
}
