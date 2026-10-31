package ru.stasoft.mynotes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.stasoft.mynotes.data.NoteEntity
import ru.stasoft.mynotes.data.SnippetEntity
import ru.stasoft.mynotes.data.expandWithVars
import kotlinx.coroutines.launch

@Composable
private fun flatFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    errorContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    errorIndicatorColor = Color.Transparent,
)

@Composable
private fun ToolButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = description) }
}

private fun singleLine(v: TextFieldValue): TextFieldValue =
    if (v.text.contains('\n') || v.text.contains('\r')) {
        v.copy(text = v.text.replace("\r", "").replace('\n', ' '))
    } else v

@Composable
fun EditorScreen(vm: AppViewModel, note: NoteEntity) {
    var titleField by remember(note.id) { mutableStateOf(TextFieldValue(note.title)) }
    var bodyField by remember(note.id) { mutableStateOf(TextFieldValue(note.body)) }
    val title = titleField.text
    val body = bodyField.text
    var titleFocused by remember(note.id) { mutableStateOf(false) }
    val titleFocus = remember { FocusRequester() }
    val titleHistory = remember(note.id) { TextHistory(titleField) }
    val bodyHistory = remember(note.id) { TextHistory(bodyField) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(note.id) {
        // Новая пустая заметка: сразу ставим курсор в название.
        if (note.title.isBlank() && note.body.isBlank()) {
            titleFocused = true
            try {
                titleFocus.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    // Автосохранение и снимки для «Отменить» после паузы в наборе.
    LaunchedEffect(note.id, title, body) {
        delay(600)
        vm.save(note.id, title, body)
    }
    LaunchedEffect(title) {
        delay(600)
        titleHistory.commit(titleField)
    }
    LaunchedEffect(body) {
        delay(600)
        bodyHistory.commit(bodyField)
    }

    fun close() {
        vm.save(note.id, title, body)
        vm.closeEditor(title, body)
    }
    BackHandler { close() }

    // Действия над тем полем, в котором сейчас курсор.
    fun current(): TextFieldValue = if (titleFocused) titleField else bodyField
    fun setCurrent(v: TextFieldValue) {
        if (titleFocused) titleField = singleLine(v) else bodyField = v
    }
    fun history(): TextHistory = if (titleFocused) titleHistory else bodyHistory

    val allSnippets by vm.allSnippets.collectAsState()
    val chain = vm.editingChain
    val snippets = remember(allSnippets, chain) {
        val rank = chain.withIndex().associate { (i, id) -> id to i }
        fun depth(sn: SnippetEntity): Int = sn.folderId?.let { rank[it] } ?: Int.MAX_VALUE
        allSnippets
            .filter { sn -> sn.folderId == null || rank.containsKey(sn.folderId) }
            .sortedWith(compareBy<SnippetEntity>({ depth(it) }, { it.sortIndex }, { it.id }))
    }

    Scaffold(
        topBar = {
            TopStrip {
                StripButton(Icons.AutoMirrored.Filled.ArrowBack, "Назад") { close() }
                Box {
                    StripButton(Icons.Filled.Bookmarks, "Шаблоны") { showTemplates = true }
                    DropdownMenu(expanded = showTemplates, onDismissRequest = { showTemplates = false }) {
                        if (snippets.isEmpty()) {
                            DropdownMenuItem(text = { Text("Шаблонов нет") }, onClick = { showTemplates = false }, enabled = false)
                        }
                        snippets.forEach { sn ->
                            DropdownMenuItem(
                                text = { Text(sn.label, maxLines = 2) },
                                enabled = !note.readOnly,
                                onClick = {
                                    showTemplates = false
                                    // Вставляем туда, где сейчас курсор: в название или в текст.
                                    scope.launch {
                                        val vars = vm.inheritedVars(note).map { it.first }
                                        val values = vm.numbers.value
                                        val text = expandWithVars(sn.text, vars) { name ->
                                            chain.firstNotNullOfOrNull { id ->
                                                values[id]?.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second
                                            }
                                        }
                                        setCurrent(current().replaceSelection(text))
                                    }
                                },
                            )
                        }
                    }
                }
                StripButton(Icons.Filled.Notifications, "Напоминание") { showReminderDialog = true }
                StripButton(Icons.Filled.Tune, "Свойства") {
                    vm.save(note.id, title, body)
                    vm.openProps(note)
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .fillMaxSize(),
        ) {
            TextField(
                value = titleField,
                onValueChange = { titleField = singleLine(it) },
                placeholder = { Text("Название", style = MaterialTheme.typography.titleLarge) },
                textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                singleLine = true,
                readOnly = note.readOnly,
                colors = flatFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(titleFocus)
                    .onFocusChanged { if (it.isFocused) titleFocused = true },
            )
            if (note.reminderAt != null) {
                ReminderRow(note = note, onEdit = { showReminderDialog = true })
            }
            TextField(
                value = bodyField,
                onValueChange = { bodyField = it },
                placeholder = { Text("Текст") },
                readOnly = note.readOnly,
                colors = flatFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onFocusChanged { if (it.isFocused) titleFocused = false },
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    ToolButton(Icons.AutoMirrored.Filled.Undo, "Отменить") {
                        history().undo(current())?.let { setCurrent(it) }
                    }
                    ToolButton(Icons.AutoMirrored.Filled.Redo, "Повторить") {
                        history().redo()?.let { setCurrent(it) }
                    }
                    ToolButton(Icons.Filled.ContentCut, "Вырезать") {
                        val c = current()
                        if (c.selection.length > 0) {
                            clipboard.setText(AnnotatedString(c.selectedText()))
                            setCurrent(c.replaceSelection(""))
                        }
                    }
                    ToolButton(Icons.Filled.ContentCopy, "Копировать") {
                        val c = current()
                        if (c.selection.length > 0) clipboard.setText(AnnotatedString(c.selectedText()))
                    }
                    ToolButton(Icons.Filled.ContentPaste, "Вставить") {
                        val t = clipboard.getText()?.text
                        if (!t.isNullOrEmpty()) setCurrent(current().replaceSelection(t))
                    }
                    ToolButton(Icons.Filled.SelectAll, "Выделить всё") { setCurrent(current().selectAll()) }
                    TextButton(onClick = { setCurrent(current().cursorTo(0)) }) { Text("В начало") }
                    TextButton(onClick = { setCurrent(current().let { it.cursorTo(it.text.length) }) }) { Text("В конец") }
                    if (!titleFocused) {
                        TextButton(onClick = { setCurrent(current().deleteLine()) }) { Text("Удалить строку") }
                        TextButton(onClick = { setCurrent(current().duplicateLine()) }) { Text("Дублировать строку") }
                    }
                }
            }
        }
    }

    if (showReminderDialog) {
        ReminderDialog(
            note = note,
            onDismiss = { showReminderDialog = false },
            onSave = { due, period, lead ->
                showReminderDialog = false
                vm.setReminder(note.id, due, period, lead)
            },
            onClear = {
                showReminderDialog = false
                vm.setReminder(note.id, null, 0, 0)
            },
        )
    }
}
