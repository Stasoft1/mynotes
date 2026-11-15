package ru.stasoft.mynotes.data

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Подставляет в текст вставки переменные: {сегодня} 22.10.26, {дата} 22.10.2026, {время} 09:05. */
fun expandSnippet(text: String, now: LocalDateTime = LocalDateTime.now()): String =
    text
        .replace("{сегодня}", now.format(DateTimeFormatter.ofPattern("dd.MM.yy")))
        .replace("{дата}", now.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")))
        .replace("{время}", now.format(DateTimeFormatter.ofPattern("HH:mm")))
