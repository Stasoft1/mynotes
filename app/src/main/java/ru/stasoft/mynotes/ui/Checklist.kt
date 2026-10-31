package ru.stasoft.mynotes.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

private const val DONE = "[x] "

class CheckItem(val id: Long, text: String, checked: Boolean) {
    var text by mutableStateOf(text)
    var checked by mutableStateOf(checked)
}

/**
 * Список задач поверх обычного текста заметки: каждая строка — пункт. Отмеченный пункт хранится в тексте
 * с приставкой «[x] » (неотмеченный — просто строка), так что текст читается и ищется как обычно.
 */
class ChecklistState(body: String) {
    private var nextId = 1L
    val items = mutableStateListOf<CheckItem>().apply {
        val lines = if (body.isEmpty()) emptyList() else body.split("\n").let { l -> if (l.lastOrNull() == "") l.dropLast(1) else l }
        lines.forEach { line ->
            val done = line.startsWith(DONE) || line.startsWith("[X] ")
            add(CheckItem(nextId++, if (done) line.drop(DONE.length) else line, done))
        }
    }
    var focusId by mutableLongStateOf(-1L)

    val serialized: String
        get() = items.joinToString("\n") { (if (it.checked) DONE else "") + it.text }

    fun addAfter(index: Int, text: String = ""): CheckItem {
        val it = CheckItem(nextId++, text, false)
        items.add((index + 1).coerceIn(0, items.size), it)
        focusId = it.id
        return it
    }

    fun addLines(text: String) {
        text.split("\n").filter { it.isNotBlank() }.forEach { addAfter(items.size - 1, it) }
    }

    fun removeChecked() {
        items.removeAll { it.checked }
    }
}

@Composable
fun ChecklistBody(state: ChecklistState, readOnly: Boolean, modifier: Modifier = Modifier) {
    val items = state.items
    LazyColumn(modifier) {
        items(items, key = { it.id }) { item ->
            val index = items.indexOf(item)
            val focus = remember { FocusRequester() }
            LaunchedEffect(state.focusId) {
                if (state.focusId == item.id) {
                    try {
                        focus.requestFocus()
                    } catch (_: Exception) {
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = item.checked,
                    enabled = !readOnly,
                    onCheckedChange = { item.checked = it },
                )
                TextField(
                    value = item.text,
                    onValueChange = { item.text = it.replace("\n", " ") },
                    readOnly = readOnly,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                        color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { if (!readOnly) state.addAfter(items.indexOf(item)) }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
                if (!readOnly) {
                    IconButton(onClick = { items.remove(item) }) { Icon(Icons.Filled.Close, "Удалить пункт") }
                }
            }
        }
        if (!readOnly) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    TextButton(onClick = { state.addAfter(items.size - 1) }) { Text("+ Добавить пункт") }
                    if (items.any { it.checked }) {
                        TextButton(onClick = { state.removeChecked() }) { Text("Удалить отмеченные") }
                    }
                }
            }
        }
    }
}
