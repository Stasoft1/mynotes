package ru.stasoft.mynotes.data

/** Название для списка: если заголовка нет, берётся начало текста (как в MyTreeNotes, до 45 символов). */
fun NoteEntity.displayTitle(): String {
    val t = title.trim()
    if (t.isNotEmpty()) return t
    val firstLine = body.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
    return if (firstLine.length <= 45) firstLine else firstLine.take(45) + "…"
}

/**
 * Применяет режим сортировки папки. Режим 0 — ручной порядок, список остаётся как есть.
 * В режимах автосортировки папки идут первыми (так выглядит ваша база; точный флаг из MyTreeNotes ещё не расшифрован).
 */
fun sortNotes(list: List<NoteEntity>, mode: Int): List<NoteEntity> {
    if (mode <= 0) return list
    val byTitle = compareBy<NoteEntity> { it.displayTitle().lowercase() }
    val byTitleReversedText = compareBy<NoteEntity> { it.displayTitle().lowercase().reversed() }
    val byCreated = compareBy<NoteEntity> { it.createdAt }
    // Напоминания: сначала записи с напоминанием по возрастанию даты, остальные в конце.
    val byReminder = Comparator<NoteEntity> { a, b ->
        val x = a.reminderAt
        val y = b.reminderAt
        when {
            x == null && y == null -> 0
            x == null -> 1
            y == null -> -1
            else -> x.compareTo(y)
        }
    }
    val cmp: Comparator<NoteEntity> = when (mode) {
        2 -> byTitle.reversed()
        3 -> byTitleReversedText
        4 -> byTitleReversedText.reversed()
        7 -> byCreated
        8 -> byCreated.reversed()
        9 -> byReminder
        10 -> byReminder.reversed()
        else -> byTitle // 1, а также 5 и 6 (списки задач) пока как по названию
    }
    return list.sortedWith(compareByDescending<NoteEntity> { it.isFolder }.then(cmp))
}
