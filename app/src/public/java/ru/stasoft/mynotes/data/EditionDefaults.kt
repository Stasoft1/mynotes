package ru.stasoft.mynotes.data

/** Набор шаблонов публичной редакции: остальные пользователь создаёт сам. */
val DEFAULT_SNIPPETS: List<Pair<String, String>> = listOf(
    "Дата" to "{сегодня}",
    "Расход" to "Магазин: \nСумма: \nКатегория: \nПримечание: ",
)

/** Руководство в виде заметок создаётся при первом запуске на пустой базе. */
const val GUIDE_ENABLED = true

/** В публичной редакции разовых шаблонов нет. */
val EXTRA_TEMPLATES: List<Triple<String, String, String>> = emptyList()

/** Готовые наборы переменных в свойствах папки. */
val VAR_PRESETS: List<Pair<String, List<VarDef>>> = listOf(
    "Деньги: Сумма" to listOf(VarDef("Сумма", "sum")),
    "Счётчик: показание и расход" to listOf(VarDef("Показание", "max"), VarDef("Расход", "span", field = "Показание")),
    "Остаток: приход, расход, остаток" to listOf(
        VarDef("Приход", "sum"), VarDef("Расход", "sum"), VarDef("Остаток", "balance", formula = "{Приход} - {Расход}"),
    ),
)

/** Дополнительных модулей в публичной редакции нет. */
val EXTRA_MODULES: List<ru.stasoft.mynotes.modules.ModuleInfo> = emptyList()
