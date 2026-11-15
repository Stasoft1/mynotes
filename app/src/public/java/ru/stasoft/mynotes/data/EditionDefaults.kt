package ru.stasoft.mynotes.data

/** Нейтральный набор шаблонов для публичной редакции. */
val DEFAULT_SNIPPETS: List<Pair<String, String>> = listOf(
    "Дата" to "{сегодня}",
    "Дата –" to "{сегодня} - ",
    "Примечание" to "Примечание: ",
    "Сумма" to "Сумма: ",
    "Разделитель" to "-------------------------------------\n",
    "Расходы" to "Магазин: \nСумма: \nПримечание: ",
)

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
