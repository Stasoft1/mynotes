package ru.stasoft.mynotes.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.stasoft.mynotes.data.NoteEntity
import ru.stasoft.mynotes.data.displayTitle

/** Ссылка на другую заметку или папку внутри текста: [[123]] или [[123|Название]]. */
class NoteLink(val id: Long, val title: String?)

private val NOTE_LINK_RE = Regex("\\[\\[\\s*(\\d{1,18})\\s*(?:\\|([^\\]\\n]*))?\\]\\]")

fun findNoteLinks(body: String): List<NoteLink> {
    if (!body.contains("[[")) return emptyList()
    val seen = LinkedHashMap<Long, NoteLink>()
    for (m in NOTE_LINK_RE.findAll(body)) {
        val id = m.groupValues[1].toLongOrNull() ?: continue
        seen.putIfAbsent(id, NoteLink(id, m.groupValues[2].trim().ifEmpty { null }))
    }
    return seen.values.toList().take(12)
}

/** Номер записи из того, что вставил пользователь: «123» или «[[123|Название]]». */
fun parseNoteRef(s: String): Long? {
    val t = s.trim()
    t.toLongOrNull()?.let { return it }
    return NOTE_LINK_RE.find(t)?.groupValues?.get(1)?.toLongOrNull()
}

/** Текст ссылки на запись, который копируется в буфер из свойств. */
fun noteLinkText(id: Long, title: String): String =
    "[[" + id + "|" + title.replace(Regex("[\\[\\]|\\r\\n]+"), " ").trim().take(60) + "]]"

/** Кнопки переходов под названием заметки: ссылки на другие заметки, внешние ссылки и телефоны из текста. */
@Composable
fun NoteLinksRow(vm: AppViewModel, body: String) {
    val ctx = LocalContext.current
    val notes = remember(body) { findNoteLinks(body) }
    val hits = remember(body) { findLinks(body) }
    if (notes.isEmpty() && hits.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        notes.forEach { l ->
            val shown by produceState(l.title, l.id) {
                if (value == null) value = vm.noteById(l.id)?.let { it.displayTitle().ifBlank { "Без названия" } } ?: "заметка не найдена"
            }
            AssistChip(
                onClick = { vm.followNoteLink(l.id) { Toast.makeText(ctx, "Заметка не найдена: возможно, она удалена", Toast.LENGTH_SHORT).show() } },
                label = { Text("→ " + (shown ?: ("№" + l.id)), maxLines = 1) },
                leadingIcon = { Icon(Icons.Filled.Description, null, Modifier.padding(0.dp)) },
            )
        }
        hits.forEach { h ->
            AssistChip(
                onClick = { openHit(ctx, h) },
                label = { Text(h.shown, maxLines = 1) },
                leadingIcon = { Icon(if (h.kind == 0) Icons.Filled.Link else Icons.Filled.Phone, null) },
            )
        }
    }
}

/** Кнопка «Ссылка» в редакторе: адрес в интернете или другая заметка (ID из свойств либо выбор по поиску). */
@Composable
fun LinkInsertDialog(vm: AppViewModel, current: NoteEntity, onDismiss: () -> Unit, onInsert: (String) -> Unit) {
    val clip = LocalClipboardManager.current
    var mode by remember { mutableStateOf(0) }
    var url by remember { mutableStateOf("https://") }
    val clipRef = remember { clip.getText()?.text?.let { parseNoteRef(it) } }
    var idText by remember { mutableStateOf(clipRef?.toString() ?: "") }
    var q by remember { mutableStateOf("") }
    var all by remember { mutableStateOf<List<NoteEntity>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { all = vm.allNotesOnce() }
    val options = remember(all, q) {
        val list = all ?: return@remember emptyList()
        if (q.isBlank()) return@remember emptyList()
        val by = list.associateBy { it.id }
        list.filter { it.id != current.id }.map { it to pathOf(it, by) }
            .filter { it.second.contains(q, ignoreCase = true) }.sortedBy { it.second.lowercase() }.take(40)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Вставить ссылку") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("На заметку") })
                    FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("На сайт") })
                }
                if (mode == 1) {
                    OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Адрес") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                } else {
                    Text(
                        "Вставьте ID заметки (в её свойствах есть кнопка «Копировать ссылку») или найдите её поиском.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = idText, onValueChange = { idText = it; error = null },
                        label = { Text("ID заметки") }, singleLine = true, isError = error != null,
                        supportingText = { error?.let { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(value = q, onValueChange = { q = it }, label = { Text("Поиск по названию") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    LazyColumn(Modifier.heightIn(max = 220.dp)) {
                        items(options) { (n, label) ->
                            Text(
                                label,
                                Modifier.fillMaxWidth().clickable { idText = n.id.toString(); error = null }.padding(vertical = 10.dp),
                                color = if (idText.trim() == n.id.toString()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (mode == 1) {
                    val u = url.trim()
                    if (u.isNotEmpty() && u != "https://") { onInsert(if (u.contains("://")) u else "https://$u"); onDismiss() }
                } else {
                    val id = parseNoteRef(idText)
                    val n = id?.let { i -> all?.firstOrNull { it.id == i } }
                    if (n == null) error = "Записи с таким ID нет"
                    else { onInsert(noteLinkText(n.id, n.displayTitle().ifBlank { "Без названия" })); onDismiss() }
                }
            }) { Text("Вставить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
