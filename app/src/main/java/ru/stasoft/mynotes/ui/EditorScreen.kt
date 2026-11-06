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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.CheckBox
import ru.stasoft.mynotes.data.mapSpansForEdit
import ru.stasoft.mynotes.data.applyFormulas
import ru.stasoft.mynotes.data.applyStyle
import ru.stasoft.mynotes.data.coveredValue
import ru.stasoft.mynotes.data.styleAt
import ru.stasoft.mynotes.data.spansToJson
import ru.stasoft.mynotes.data.parseSpans
import ru.stasoft.mynotes.data.SpanKind
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.StrikethroughS
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatBold
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
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

@Composable
private fun FmtButton(icon: ImageVector, description: String, active: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun singleLine(v: TextFieldValue): TextFieldValue =
    if (v.text.contains('\n') || v.text.contains('\r')) {
        v.copy(text = v.text.replace("\r", "").replace('\n', ' '))
    } else v

@Composable
fun EditorScreen(vm: AppViewModel, note: NoteEntity) {
    // Несохранённые правки живут в черновике модели: переживают открытие свойств и блокировку.
    val draft0 = remember(note.id) { vm.draft?.takeIf { it.id == note.id } }
    var titleField by remember(note.id) { mutableStateOf(TextFieldValue(draft0?.title ?: note.title)) }
    var bodyField by remember(note.id) { mutableStateOf(TextFieldValue(draft0?.body ?: note.body)) }
    var spans by remember(note.id) { mutableStateOf(parseSpans(draft0?.spans ?: note.spansJson, (draft0?.body ?: note.body).length)) }
    var askExit by remember { mutableStateOf(false) }
    // Переключатели оформления при курсоре без выделения: действуют на следующий набранный текст.
    var pending by remember(note.id) { mutableStateOf<Map<String, String?>>(emptyMap()) }
    // Позиция только что вставленного Enter: продолжение списка делается чуть позже, когда клавиатура закончила правку.
    var enterAt by remember(note.id) { mutableStateOf(-1 to 0) }
    var pasteAt by remember(note.id) { mutableStateOf(0) }
    var undoReq by remember(note.id) { mutableStateOf<Pair<String, String>?>(null) }
    val skipEnter = remember(note.id) { java.util.concurrent.atomic.AtomicBoolean(false) }
    var showTextColor by remember { mutableStateOf(false) }
    var showBgColor by remember { mutableStateOf(false) }
    var showSizes by remember { mutableStateOf(false) }
    val spansJson = spansToJson(spans)
    // Режим списка задач: пункты строятся из текущего текста при включении режима.
    val checklist = remember(note.id, note.checklist) { ChecklistState(bodyField.text) }
    if (note.checklist) {
        val serialized = checklist.serialized
        LaunchedEffect(serialized) {
            if (serialized != bodyField.text) bodyField = TextFieldValue(serialized)
            if (spans.isNotEmpty()) spans = emptyList()
        }
    }
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

    // Есть ли несохранённые правки: сравниваем с тем, что лежит в базе.
    val dirty = title != note.title || body != note.body || (!note.checklist && spansJson != note.spansJson)
    LaunchedEffect(note.id, title, body, spansJson, dirty) {
        vm.draft = if (dirty) AppViewModel.Draft(note.id, title, body, spansJson) else null
    }
    // Снимки для «Отменить» после паузы в наборе.
    LaunchedEffect(title) {
        delay(600)
        titleHistory.commit(titleField)
    }
    LaunchedEffect(body) {
        delay(600)
        bodyHistory.commit(bodyField)
    }

    val ctxToast = androidx.compose.ui.platform.LocalContext.current
    fun saveNow() {
        vm.save(note.id, title, body, spansJson)
        android.widget.Toast.makeText(ctxToast, "Сохранено", android.widget.Toast.LENGTH_SHORT).show()
    }
    fun discard() {
        vm.draft = null
        vm.closeEditor(note.title, note.body)
    }
    fun close() {
        if (dirty && !note.readOnly) askExit = true else discard()
    }
    BackHandler { close() }
    if (askExit) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { askExit = false },
            title = { Text("Сохранить изменения?") },
            confirmButton = {
                TextButton(onClick = {
                    askExit = false
                    vm.save(note.id, title, body, spansJson)
                    vm.draft = null
                    vm.closeEditor(title, body)
                }) { Text("Сохранить") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { askExit = false }) { Text("Отмена") }
                    TextButton(onClick = { askExit = false; discard() }) { Text("Не сохранять") }
                }
            },
        )
    }

    // Действия над тем полем, в котором сейчас курсор.
    fun current(): TextFieldValue = if (titleFocused) titleField else bodyField
    // Любая правка текста тела проходит здесь: диапазоны оформления сдвигаются вслед за текстом.
    fun updateBody(v: TextFieldValue) {
        val old = bodyField
        if (v.text == old.text) {
            if (v.selection != old.selection) pending = emptyMap()
            bodyField = v
            return
        }
        val a = old.text
        val b = v.text
        val max = minOf(a.length, b.length)
        var p = 0
        while (p < max && a[p] == b[p]) p++
        var sfx = 0
        while (sfx < max - p && a[a.length - 1 - sfx] == b[b.length - 1 - sfx]) sfx++
        var removed = a.length - p - sfx
        var inserted = b.length - p - sfx
        // Enter в конце строки перед другим переводом строки по тексту неоднозначен (куда вставлен '\n'),
        // поэтому место берём по курсору: он стоит сразу после вставленного перевода строки.
        val q = v.selection.start
        // Enter: перевод строки принимаем как обычный текст, а продолжение списка делаем отложенно (см. enterAt).
        val enterBase = if (v.selection.collapsed && q >= 1 && b[q - 1] == '\n') b.removeRange(q - 1, q) else null
        if (enterBase != null && !skipEnter.get() && !titleFocused && !note.readOnly && Math.abs(enterBase.length - a.length) <= 6) {
            enterAt = (q - 1) to (enterAt.second + 1)
            if (b.length == a.length + 1 && enterBase == a) {
                p = q - 1
                removed = 0
                inserted = 1
            }
        }
        val base = styleAt(spans, if (removed > 0) p + 1 else p)
        val typing = HashMap<String, String?>()
        for (k in SpanKind.CHAR_KINDS) typing[k] = base[k]
        for ((k, value) in pending) typing[k] = value
        spans = mapSpansForEdit(spans, p, removed, inserted, typing)
        pending = emptyMap()
        bodyField = v
        if (a.isEmpty() && b.length >= 40 && !titleFocused && !note.readOnly && !note.checklist && note.id >= 0 &&
            ru.stasoft.mynotes.data.looksLikeRequest(b)) pasteAt++
    }

    LaunchedEffect(enterAt) {
        val (p, _) = enterAt
        if (p < 0) return@LaunchedEffect
        delay(40)
        val f = bodyField
        val t = f.text
        if (p < t.length && t[p] == '\n' && f.selection.collapsed && f.selection.start == p + 1) {
            continueList(t.removeRange(p, p + 1), p)?.let {
                skipEnter.set(true)
                try { updateBody(it) } finally { skipEnter.set(false) }
            }
        }
    }

    // Формульные переменные папки: результат сам вписывается в текст после паузы в наборе.
    var formulaBase by remember(note.id) { mutableStateOf(note.body) }
    LaunchedEffect(note.id, body) {
        if (note.checklist) return@LaunchedEffect
        delay(900)
        val vars = vm.inheritedVars(note).map { it.first }.filter { it.formula.isNotBlank() }
        if (vars.isEmpty()) return@LaunchedEffect
        val next = applyFormulas(formulaBase, bodyField.text, vars)
        formulaBase = next
        if (next != bodyField.text) {
            val sel = bodyField.selection
            val max = next.length
            updateBody(TextFieldValue(next, androidx.compose.ui.text.TextRange(minOf(sel.start, max), minOf(sel.end, max))))
        }
    }
    fun setCurrent(v: TextFieldValue) {
        if (titleFocused) titleField = singleLine(v) else updateBody(v)
    }

    // Оформление: на выделение, а без выделения — на следующий набранный текст.
    fun isActive(kind: String): Boolean {
        val sel = bodyField.selection
        return if (sel.collapsed) {
            if (pending.containsKey(kind)) pending[kind] != null else styleAt(spans, sel.min)[kind] != null
        } else coveredValue(spans, kind, sel.min, sel.max) != null
    }
    fun setStyle(kind: String, value: String?) {
        val sel = bodyField.selection
        if (sel.collapsed) pending = pending + (kind to value)
        else spans = applyStyle(spans, sel.min, sel.max, kind, value)
    }
    fun toggleStyle(kind: String) = setStyle(kind, if (isActive(kind)) null else "1")
    fun alignLines(value: String?) {
        val t = bodyField.text
        val sel = bodyField.selection
        val ls = if (sel.min <= 0) 0 else t.lastIndexOf('\n', sel.min - 1) + 1
        val nl = t.indexOf('\n', sel.max)
        val end = if (nl < 0) t.length else nl + 1
        if (end > ls) spans = applyStyle(spans, ls, end, SpanKind.ALIGN, value)
    }
    fun clearStyle() {
        val sel = bodyField.selection
        if (sel.collapsed) pending = SpanKind.CHAR_KINDS.associateWith { null }
        else {
            var out = spans
            for (k in SpanKind.CHAR_KINDS) out = applyStyle(out, sel.min, sel.max, k, null)
            spans = out
        }
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

    suspend fun convertRequestNow(): Boolean {
        val raw = bodyField.text
        val sn = snippets.firstOrNull { val l = it.label.trim().lowercase(); l.startsWith("ф1") || l.startsWith("f1") }
        val skeleton = sn?.let { vm.expandTemplate(it.text, it.spansJson, note.parentId).first.lines() }
        val r = ru.stasoft.mynotes.data.convertRequest(raw, skeleton) ?: return false
        undoReq = raw to titleField.text
        updateBody(TextFieldValue(r.body, androidx.compose.ui.text.TextRange(r.body.length)))
        spans = parseSpans("", r.body.length)
        if (titleField.text.isBlank()) titleField = TextFieldValue(r.title)
        return true
    }
    LaunchedEffect(pasteAt) {
        if (pasteAt > 0) {
            delay(80)
            if (convertRequestNow()) android.widget.Toast.makeText(ctxToast, "Заявка приведена к шаблону Ф1", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopStrip {
                StripButton(Icons.AutoMirrored.Filled.ArrowBack, "Назад") { close() }
                StripButton(Icons.Filled.Save, if (dirty) "Сохранить" else "Сохранено", accent = dirty) { if (!note.readOnly) saveNow() }
                Box {
                    StripButton(Icons.Filled.Menu, "Шаблоны") { showTemplates = true }
                    DropdownMenu(expanded = showTemplates, onDismissRequest = { showTemplates = false }) {
                        if (snippets.isEmpty()) {
                            DropdownMenuItem(text = { Text("Шаблонов нет") }, onClick = { showTemplates = false }, enabled = false)
                        }
                        DropdownMenuItem(
                            text = { Text("Привести заявку к Ф1") },
                            enabled = !note.readOnly && !note.checklist,
                            onClick = {
                                showTemplates = false
                                scope.launch {
                                    if (!convertRequestNow()) android.widget.Toast.makeText(ctxToast, "Текст не похож на заявку", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                        snippets.forEach { sn ->
                            DropdownMenuItem(
                                text = { Text(sn.label, maxLines = 2) },
                                enabled = !note.readOnly,
                                onClick = {
                                    showTemplates = false
                                    // Вставляем туда, где сейчас курсор: в название или в текст.
                                    scope.launch {
                                        val (text, spansJ) = vm.expandTemplate(sn.text, sn.spansJson, note.parentId)
                                        // Шаблон без заголовка вставляется туда, где курсор (как раньше). Шаблон с заголовком:
                                        // заголовок — в название (если оно пустое), текст — всегда в тело заметки.
                                        val intoTitle = titleFocused && sn.title.isBlank()
                                        if (sn.title.isNotBlank() && titleField.text.isBlank()) {
                                            titleField = TextFieldValue(ru.stasoft.mynotes.data.expandSnippet(sn.title))
                                        }
                                        if (note.checklist) checklist.addLines(text)
                                        else if (intoTitle) setCurrent(current().replaceSelection(text))
                                        else {
                                            val start = bodyField.selection.min
                                            updateBody(bodyField.replaceSelection(text))
                                            if (spansJ.isNotEmpty()) {
                                                val add = parseSpans(spansJ, text.length).map { it.copy(start = it.start + start, end = it.end + start) }
                                                spans = ru.stasoft.mynotes.data.normalize(spans + add)
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
                if (note.id >= 0) StripButton(Icons.Filled.Notifications, "Напоминание") { showReminderDialog = true }
                if (note.id >= 0) StripButton(Icons.Filled.Tune, "Свойства") {
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
            undoReq?.let { u ->
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(start = 16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Заявка приведена к шаблону Ф1", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        updateBody(TextFieldValue(u.first))
                        spans = parseSpans("", u.first.length)
                        titleField = TextFieldValue(u.second)
                        undoReq = null
                    }) { Text("Вернуть") }
                    TextButton(onClick = { undoReq = null }) { Text("Ок") }
                }
            }
            if (note.checklist) {
                ChecklistBody(checklist, note.readOnly, Modifier.fillMaxWidth().weight(1f))
            } else TextField(
                value = bodyField,
                onValueChange = { updateBody(it) },
                visualTransformation = remember(spans) { SpanTransformation(spans) },
                placeholder = { Text("Текст") },
                readOnly = note.readOnly,
                colors = flatFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onFocusChanged { if (it.isFocused) titleFocused = false }
            )
            if (!note.checklist) Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                if (!titleFocused && !note.readOnly) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        FmtButton(Icons.Filled.FormatBold, "Жирный", isActive(SpanKind.BOLD)) { toggleStyle(SpanKind.BOLD) }
                        FmtButton(Icons.Filled.FormatItalic, "Курсив", isActive(SpanKind.ITALIC)) { toggleStyle(SpanKind.ITALIC) }
                        FmtButton(Icons.Filled.FormatUnderlined, "Подчёркнутый", isActive(SpanKind.UNDERLINE)) { toggleStyle(SpanKind.UNDERLINE) }
                        FmtButton(Icons.Filled.StrikethroughS, "Зачёркнутый", isActive(SpanKind.STRIKE)) { toggleStyle(SpanKind.STRIKE) }
                        FmtButton(Icons.Filled.FormatColorText, "Цвет текста", isActive(SpanKind.COLOR)) { showTextColor = true }
                        FmtButton(Icons.Filled.FormatColorFill, "Цвет фона", isActive(SpanKind.BACKGROUND)) { showBgColor = true }
                        Box {
                            FmtButton(Icons.Filled.FormatSize, "Размер шрифта", isActive(SpanKind.SIZE)) { showSizes = true }
                            DropdownMenu(expanded = showSizes, onDismissRequest = { showSizes = false }) {
                                FONT_SIZES.forEach { (label, size) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            showSizes = false
                                            setStyle(SpanKind.SIZE, size?.toString())
                                        },
                                    )
                                }
                            }
                        }
                        FmtButton(Icons.Filled.FormatAlignLeft, "По левому краю", false) { alignLines(null) }
                        FmtButton(Icons.Filled.FormatAlignCenter, "По центру", false) { alignLines("c") }
                        FmtButton(Icons.Filled.FormatAlignRight, "По правому краю", false) { alignLines("r") }
                        FmtButton(Icons.Filled.FormatListBulleted, "Маркированный список", false) { updateBody(bodyField.toggleBullet()) }
                        FmtButton(Icons.Filled.FormatListNumbered, "Нумерованный список", false) { updateBody(bodyField.toggleNumbered()) }
                        FmtButton(Icons.Filled.FormatClear, "Сбросить оформление", false) { clearStyle() }
                    }
                }
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

    if (showTextColor) {
        ColorPickerDialog(
            title = "Цвет текста",
            onDismiss = { showTextColor = false },
            onPick = { c ->
                showTextColor = false
                setStyle(SpanKind.COLOR, c?.toString())
            },
        )
    }
    if (showBgColor) {
        ColorPickerDialog(
            title = "Цвет фона",
            onDismiss = { showBgColor = false },
            onPick = { c ->
                showBgColor = false
                setStyle(SpanKind.BACKGROUND, c?.toString())
            },
        )
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
