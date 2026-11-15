package ru.stasoft.mynotes.data

/** Публичная редакция: приведение заявок (модуль «Заявки Ф1») не входит, код заглушен. */
class Converted(val title: String, val body: String)

fun looksLikeRequest(text: String): Boolean = false

fun convertRequest(rawIn: String, skeleton: List<String>?): Converted? = null
