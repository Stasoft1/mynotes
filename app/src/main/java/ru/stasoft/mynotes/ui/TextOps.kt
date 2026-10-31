package ru.stasoft.mynotes.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

fun TextFieldValue.selectedText(): String = text.substring(selection.min, selection.max)

fun TextFieldValue.replaceSelection(s: String): TextFieldValue =
    TextFieldValue(text.replaceRange(selection.min, selection.max, s), TextRange(selection.min + s.length))

fun TextFieldValue.selectAll(): TextFieldValue = copy(selection = TextRange(0, text.length))

fun TextFieldValue.cursorTo(pos: Int): TextFieldValue = copy(selection = TextRange(pos.coerceIn(0, text.length)))

private fun TextFieldValue.lineStart(pos: Int): Int = if (pos <= 0) 0 else text.lastIndexOf('\n', pos - 1) + 1

private fun TextFieldValue.lineEnd(pos: Int): Int {
    val i = text.indexOf('\n', pos)
    return if (i < 0) text.length else i
}

/** Удаляет строку, в которой стоит курсор (вместе с переводом строки). */
fun TextFieldValue.deleteLine(): TextFieldValue {
    val pos = selection.min
    val start = lineStart(pos)
    val end = lineEnd(pos)
    val removeFrom = if (end >= text.length && start > 0) start - 1 else start
    val removeTo = if (end < text.length) end + 1 else end
    val newText = text.removeRange(removeFrom, removeTo)
    return TextFieldValue(newText, TextRange(removeFrom.coerceAtMost(newText.length)))
}

/** Дублирует строку, в которой стоит курсор. */
fun TextFieldValue.duplicateLine(): TextFieldValue {
    val pos = selection.min
    val start = lineStart(pos)
    val end = lineEnd(pos)
    val line = text.substring(start, end)
    val newText = text.substring(0, end) + "\n" + line + text.substring(end)
    return TextFieldValue(newText, TextRange(end + 1 + line.length))
}

/** История правок для «Отменить» и «Повторить». Снимки добавляются после паузы в наборе. */
class TextHistory(initial: TextFieldValue) {
    private val undoStack = mutableListOf<TextFieldValue>()
    private val redoStack = mutableListOf<TextFieldValue>()
    private var base: TextFieldValue = initial

    fun commit(v: TextFieldValue) {
        if (v.text == base.text) return
        undoStack.add(base)
        if (undoStack.size > 300) undoStack.removeAt(0)
        redoStack.clear()
        base = v
    }

    fun undo(current: TextFieldValue): TextFieldValue? {
        commit(current)
        if (undoStack.isEmpty()) return null
        redoStack.add(base)
        val prev = undoStack.removeAt(undoStack.size - 1)
        base = prev
        return prev
    }

    fun redo(): TextFieldValue? {
        if (redoStack.isEmpty()) return null
        undoStack.add(base)
        val next = redoStack.removeAt(redoStack.size - 1)
        base = next
        return next
    }
}

private val NUMBERED = Regex("^\\d+\\. ")
private const val BULLET = "• "

private fun stripListPrefix(line: String): String =
    if (line.startsWith(BULLET)) line.removePrefix(BULLET) else NUMBERED.replaceFirst(line, "")

private fun TextFieldValue.mapLines(transform: (List<String>) -> List<String>): TextFieldValue {
    val start = lineStart(selection.min)
    val end = lineEnd(selection.max)
    val block = transform(text.substring(start, end).split('\n')).joinToString("\n")
    val newText = text.substring(0, start) + block + text.substring(end)
    val sel = if (selection.collapsed) TextRange(start + block.length) else TextRange(start, start + block.length)
    return TextFieldValue(newText, sel)
}

/** Маркированный список: ставит или снимает «• » у строк выделения. */
fun TextFieldValue.toggleBullet(): TextFieldValue = mapLines { lines ->
    if (lines.all { it.startsWith(BULLET) }) lines.map { it.removePrefix(BULLET) }
    else lines.map { BULLET + stripListPrefix(it) }
}

/** Нумерованный список: ставит «1. », «2. » … или снимает нумерацию. */
fun TextFieldValue.toggleNumbered(): TextFieldValue = mapLines { lines ->
    if (lines.all { NUMBERED.containsMatchIn(it) }) lines.map { stripListPrefix(it) }
    else lines.mapIndexed { i, l -> "${i + 1}. " + stripListPrefix(l) }
}

/**
 * Если нажат Enter в строке списка, возвращает текст с продолжением списка (следующий маркер или номер),
 * а на пустом пункте — с убранным маркером. Иначе null. [old] — текст до нажатия, [p] — позиция вставленного '\n'.
 */
fun continueList(old: String, p: Int): TextFieldValue? {
    val ls = if (p <= 0) 0 else old.lastIndexOf('\n', p - 1) + 1
    val line = old.substring(ls, p)
    val bullet = line.startsWith(BULLET)
    val num = NUMBERED.find(line)
    if (!bullet && num == null) return null
    val prefix = if (bullet) BULLET else num!!.value
    if (line.length == prefix.length) {
        // Пустой пункт: Enter завершает список.
        val t = old.removeRange(ls, p) + ""
        return TextFieldValue(t, TextRange(ls))
    }
    val next = if (bullet) BULLET else "${num!!.value.trim().trimEnd('.').toInt() + 1}. "
    val newText = old.substring(0, p) + "\n" + next + old.substring(p)
    return TextFieldValue(newText, TextRange(p + 1 + next.length))
}
