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
