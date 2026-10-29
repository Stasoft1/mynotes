package ru.stasoft.mynotes.data

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Подставляет в текст вставки переменные: {сегодня} 22.10.26, {дата} 22.10.2026, {время} 09:05. */
fun expandSnippet(text: String, now: LocalDateTime = LocalDateTime.now()): String =
    text
        .replace("{сегодня}", now.format(DateTimeFormatter.ofPattern("dd.MM.yy")))
        .replace("{дата}", now.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")))
        .replace("{время}", now.format(DateTimeFormatter.ofPattern("HH:mm")))

/** Начальный набор вставок (общий для всех папок): по образцу меню старого приложения. */
val DEFAULT_SNIPPETS: List<Pair<String, String>> = listOf(
    "Заявка" to "Договор: \nТелефон: +7\nРабота: \nСкорость мб: \nПримечание: \nwf name: \nwf pass: \n" +
        "Кабель вп: \nКабель кокс: \nСвитч: \nПорт: \nСумма: \nПробег: ",
    "Примечание" to "Примечание: ",
    "Сумма" to "Сумма: ",
    "Оклад" to "Оклад (2800)",
    "Вдвоём с" to "Примечание: вдвоем с ",
    "Разделитель" to "-------------------------------------\n",
    "Дата" to "{сегодня}",
    "Дата –" to "{сегодня} - ",
    "Заправка" to "Заправка",
)
