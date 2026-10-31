package ru.stasoft.mynotes.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.foundation.lazy.rememberLazyListState
import ru.stasoft.mynotes.data.formatNumber
import androidx.compose.foundation.layout.heightIn
import ru.stasoft.mynotes.data.ReadingIssue
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import ru.stasoft.mynotes.crypto.BiometricVault
import ru.stasoft.mynotes.data.NoteEntity
import ru.stasoft.mynotes.data.displayTitle
import ru.stasoft.mynotes.data.formatValues
import ru.stasoft.mynotes.data.resolveTitle

private val BarColor = Color(0xFF1B2330)

/** Тёмная панель кнопок сверху, листается по горизонтали (как в MyTreeNotes). */
@Composable
fun TopStrip(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(BarColor)
            .statusBarsPadding()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun StripButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = description, tint = Color.White) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TreeScreen(vm: AppViewModel) {
    val items by vm.items.collectAsState()
    val numbers by vm.numbers.collectAsState()
    val query by vm.query.collectAsState()
    val folderId by vm.folderId.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<NoteEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<NoteEntity?>(null) }
    var showBioSetup by remember { mutableStateOf(false) }
    var showSnippets by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var showCheck by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importMtnt(uri)
    }

    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopStrip {
                if (folderId != null) StripButton(Icons.AutoMirrored.Filled.ArrowBack, "Назад") { vm.goUp() }
                StripButton(Icons.Filled.Add, "Создать заметку") { vm.newNote() }
                StripButton(Icons.Filled.CreateNewFolder, "Создать папку") { vm.newChild() }
                StripButton(Icons.Filled.Edit, "Редактировать выбранное") { vm.editSelected() }
                StripButton(if (searching) Icons.Filled.Close else Icons.Filled.Search, "Поиск") {
                    searching = !searching
                    if (!searching) vm.query.value = ""
                }
                StripButton(Icons.Filled.Bookmarks, "Шаблоны") { showSnippets = true }
                StripButton(Icons.Filled.CalendarMonth, "Напоминания") { vm.showReminders = true }
                StripButton(Icons.Filled.Delete, "Удалить выбранное") {
                    val sel = items.firstOrNull { it.id == vm.selectedId }
                    if (sel == null) vm.message = "Сначала выберите запись: коснитесь её" else deleteTarget = sel
                }
                Box {
                    StripButton(Icons.Filled.MoreVert, "Меню") { menuOpen = true }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Сортировка") },
                            onClick = {
                                menuOpen = false
                                showSort = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Проверка показаний…") },
                            onClick = {
                                menuOpen = false
                                showCheck = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Планировщик (напоминания)") },
                            onClick = {
                                menuOpen = false
                                vm.showReminders = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Импорт из .mtnt") },
                            onClick = {
                                menuOpen = false
                                picker.launch(arrayOf("*/*"))
                            },
                        )
                        if (vm.biometricAvailable()) {
                            DropdownMenuItem(
                                text = { Text(if (vm.biometricEnabled) "Выключить вход по отпечатку" else "Включить вход по отпечатку") },
                                onClick = {
                                    menuOpen = false
                                    if (vm.biometricEnabled) vm.disableBiometric() else showBioSetup = true
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Заблокировать") },
                            onClick = {
                                menuOpen = false
                                vm.lock()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Выход") },
                            onClick = {
                                menuOpen = false
                                vm.lock()
                                (ctx as? android.app.Activity)?.finish()
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (searching) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { vm.query.value = it },
                    placeholder = { Text("Поиск по всей базе") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            if (vm.path.isNotEmpty() && query.isBlank()) {
                Text(
                    text = (listOf("Главная") + vm.path.map { it.title.ifBlank { "…" } }).joinToString(" › "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.goToPathIndex(-1) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (vm.path.isNotEmpty() && query.isBlank()) {
                val cur = vm.path.last()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (vm.selectedId == null) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .combinedClickable(onClick = { vm.goUp() }, onDoubleClick = { vm.editNote(cur) })
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(
                            resolveTitle(cur.displayTitle(), numbers[cur.id]).ifBlank { "Без названия" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        numbers[cur.id]?.let {
                            Text(
                                formatValues(it),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (vm.busy) CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            if (items.isEmpty() && !vm.busy) {
                Text(
                    if (query.isBlank()) "Здесь пока пусто. Нажмите «+», чтобы добавить заметку." else "Ничего не найдено",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val listState = rememberLazyListState()
            val target = vm.selectedId
            val hasTarget = target != null && items.any { it.id == target }
            LaunchedEffect(vm.scrollTick, hasTarget) {
                val i = items.indexOfFirst { it.id == target }
                if (hasTarget && i >= 0) {
                    val visible = listState.layoutInfo.visibleItemsInfo.any { it.index == i }
                    if (!visible) listState.scrollToItem(i)
                }
            }
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(items, key = { it.id }) { note ->
                    NoteRow(
                        note = note,
                        number = numbers[note.id],
                        selected = vm.selectedId == note.id,
                        onClick = { if (note.isFolder) vm.openFolder(note) else vm.select(note) },
                        onDoubleClick = { vm.editNote(note) },
                        onLongClick = {
                            vm.select(note)
                            actionTarget = note
                        },
                    )
                }
            }
        }
    }

    if (showSnippets) SnippetsDialog(vm, onDismiss = { showSnippets = false })
    if (showSort) SortDialog(vm, onDismiss = { showSort = false })
    if (showCheck) ReadingCheckDialog(vm, onDismiss = { showCheck = false })
    if (showBioSetup) {
        BioSetupDialog(
            onDismiss = { showBioSetup = false },
            onConfirm = { password ->
                showBioSetup = false
                scope.launch {
                    val master = vm.verifyPassword(password)
                    val activity = ctx as? FragmentActivity
                    if (master == null) {
                        if (vm.message == null) vm.message = "Неверный пароль"
                    } else if (activity != null) {
                        try {
                            showBiometricPrompt(
                                activity = activity,
                                title = "Подтвердите отпечаток",
                                negativeText = "Отмена",
                                cipher = BiometricVault.encryptCipher(),
                                onSuccess = { vm.storeBiometricKey(it, master) },
                                onFailure = {
                                    master.fill(0)
                                    vm.message = it
                                },
                            )
                        } catch (e: Exception) {
                            master.fill(0)
                            vm.message = "Не удалось включить вход по отпечатку: ${e.message}"
                        }
                    }
                }
            },
        )
    }
    actionTarget?.let { note ->
        ActionDialog(
            note = note,
            onDismiss = { actionTarget = null },
            onRename = { vm.rename(note, it) },
            onDelete = { vm.delete(note) },
            onFlatten = { vm.flattenFolder(note) },
        )
    }
    deleteTarget?.let { note ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Удалить?") },
            text = {
                Text(
                    if (note.isFolder) "«${note.displayTitle()}» будет удалена вместе со всем содержимым."
                    else "«${note.displayTitle()}» будет удалена.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(note)
                    deleteTarget = null
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Отмена") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteRow(
    note: NoteEntity,
    number: List<Pair<String, Double>>?,
    selected: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val status = reminderStatus(note)
    val textColor = note.textColorArgb?.let { Color(it) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else rowBackground(note) ?: Color.Transparent,
            )
            .combinedClickable(onClick = onClick, onDoubleClick = onDoubleClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Слева всегда папка или заметка, чтобы их было видно сразу; своя иконка — справа, как в MyTreeNotes.
        Text(if (note.isFolder) "📁" else "📄", style = MaterialTheme.typography.titleLarge)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                resolveTitle(note.displayTitle(), number).ifBlank { "Без названия" },
                style = MaterialTheme.typography.titleMedium,
                color = textColor ?: Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (status != null && !status.done) {
                Text(
                    status.headline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (status.missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                Text(status.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (number != null) {
                Text(
                    formatValues(number),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (!note.isFolder && note.body.isNotBlank()) {
                Text(
                    note.body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        NoteIcon(effectiveIcon(note), legacyIconColor(note) ?: textColor ?: MaterialTheme.colorScheme.primary, 28.dp)
    }
}

@Composable
private fun BioSetupDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Вход по отпечатку") },
        text = {
            Column {
                Text("Введите пароль, чтобы подтвердить, что это вы.")
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Пароль") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(password) }) { Text("Далее") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun ActionDialog(
    note: NoteEntity,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onFlatten: () -> Unit,
) {
    var title by remember { mutableStateOf(note.title) }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Удалить?") },
            text = {
                Text(
                    if (note.isFolder) "Папка «${note.title}» будет удалена вместе со всем содержимым."
                    else "Заметка «${note.title}» будет удалена.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    onDismiss()
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Действия") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Название") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onRename(title)
                onDismiss()
            }) { Text("Переименовать") }
        },
        dismissButton = {
            Row {
                if (note.isFolder) {
                    TextButton(onClick = {
                        onFlatten()
                        onDismiss()
                    }) { Text("Вынести наверх") }
                }
                TextButton(onClick = { confirmDelete = true }) { Text("Удалить") }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}

private val SORT_MODES = listOf(
    0 to "Без сортировки",
    1 to "По возрастанию",
    2 to "По убыванию",
    7 to "По возр. даты изменения",
    8 to "По убыв. даты изменения",
    9 to "По возр. дата события",
    10 to "По убыв. дата события",
    3 to "По возр., с конца строки",
    4 to "По убыв., с конца строки",
)

@Composable
private fun SortDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val currentMode by vm.sortMode.collectAsState()
    val currentFolders by vm.foldersFirst.collectAsState()
    var mode by remember { mutableStateOf(currentMode) }
    var folders by remember { mutableStateOf(currentFolders) }
    val auto = mode != 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Сортировка") },
        text = {
            Column {
                Text("Только текущая папка", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                Row(Modifier.fillMaxWidth().clickable { mode = if (auto) 0 else 1 }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = auto, onCheckedChange = { mode = if (it) 1 else 0 })
                    Text("Автоматическая")
                }
                Row(Modifier.fillMaxWidth().clickable { folders = !folders }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = folders, onCheckedChange = { folders = it })
                    Text("Папки сверху")
                }
                SORT_MODES.forEach { (m, label) ->
                    Row(Modifier.fillMaxWidth().clickable { mode = m }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = mode == m, onClick = { mode = m })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.setSortMode(mode, folders)
                onDismiss()
            }) { Text("ОК") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun ReadingCheckDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var field by remember { mutableStateOf("Пробег") }
    var issues by remember { mutableStateOf<List<ReadingIssue>?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Проверка показаний") },
        text = {
            Column {
                Text(
                    "Ищет показания, которые не вписываются в рост по датам в названиях папок: опечатки и пробег за день вместо показания. Проверяется текущая папка и вложенные.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = field,
                    onValueChange = { field = it },
                    label = { Text("Поле") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                val list = issues
                if (busy) CircularProgressIndicator(Modifier.padding(top = 12.dp))
                if (list != null) {
                    Text(
                        if (list.isEmpty()) "Всё в порядке: показания только растут." else "Подозрительных: ${list.size}",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(list, key = { it.noteId }) { i ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onDismiss()
                                        vm.openById(i.noteId)
                                    }
                                    .padding(vertical = 6.dp),
                            ) {
                                Text("${i.folderTitle}: ${formatNumber(i.value)}", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    i.noteTitle.ifBlank { "Без названия" } + (if (i.message.isNotEmpty()) " — ${i.message}" else ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && field.isNotBlank(),
                onClick = {
                    busy = true
                    scope.launch {
                        issues = vm.checkReadings(field.trim().trimEnd(':'))
                        busy = false
                    }
                },
            ) { Text("Проверить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}
