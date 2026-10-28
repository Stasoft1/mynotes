package ru.stasoft.mynotes.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.stasoft.mynotes.data.NoteEntity
import ru.stasoft.mynotes.data.displayTitle

@Composable
fun AppRoot(vm: AppViewModel) {
    BackHandler(enabled = vm.unlocked) { vm.goUp() }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            !vm.unlocked -> LockScreen(vm)
            vm.editing != null -> EditorScreen(vm, vm.editing!!)
            else -> TreeScreen(vm)
        }
    }
}

// ------------------------------------------------------------------ вход

@Composable
fun LockScreen(vm: AppViewModel) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("МоиЗаметки", style = MaterialTheme.typography.headlineLarge)
        Text(
            if (vm.needSetup) "Придумайте пароль. Им шифруется вся база. Если забыть его, восстановить данные нельзя."
            else "Введите пароль",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Пароль") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        if (vm.needSetup) {
            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it },
                label = { Text("Повторите пароль") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
        vm.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    vm.submitPassword(password, if (vm.needSetup) confirm else null)
                    password = ""
                    confirm = ""
                },
                enabled = !vm.busy,
            ) { Text(if (vm.needSetup) "Создать" else "Открыть") }
            if (vm.busy) CircularProgressIndicator(modifier = Modifier.padding(start = 16.dp))
        }
    }
}

// ------------------------------------------------------------------ дерево

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TreeScreen(vm: AppViewModel) {
    val items by vm.items.collectAsState()
    val query by vm.query.collectAsState()
    val folderId by vm.folderId.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showCreate by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<NoteEntity?>(null) }

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
            TopAppBar(
                title = {
                    if (searching) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { vm.query.value = it },
                            placeholder = { Text("Поиск") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(vm.path.lastOrNull()?.title?.ifBlank { "Без названия" } ?: "МоиЗаметки", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    if (folderId != null && !searching) TextButton(onClick = { vm.goUp() }) { Text("←") }
                },
                actions = {
                    TextButton(onClick = {
                        searching = !searching
                        if (!searching) vm.query.value = ""
                    }) { Text(if (searching) "✕" else "🔍") }
                    Box {
                        TextButton(onClick = { menuOpen = true }) { Text("⋮") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Импорт из .mtnt") },
                                onClick = {
                                    menuOpen = false
                                    picker.launch(arrayOf("*/*"))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Заблокировать") },
                                onClick = {
                                    menuOpen = false
                                    vm.lock()
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) { Text("+", style = MaterialTheme.typography.headlineSmall) }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
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
            if (vm.busy) CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            if (items.isEmpty() && !vm.busy) {
                Text(
                    if (query.isBlank()) "Здесь пока пусто. Нажмите +, чтобы добавить заметку или папку." else "Ничего не найдено",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.id }) { note ->
                    NoteRow(
                        note = note,
                        onClick = { vm.openNote(note) },
                        onLongClick = { actionTarget = note },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showCreate) {
        CreateDialog(
            onDismiss = { showCreate = false },
            onCreate = { title, folder ->
                showCreate = false
                vm.create(title, folder)
            },
        )
    }
    actionTarget?.let { note ->
        ActionDialog(
            note = note,
            onDismiss = { actionTarget = null },
            onRename = { vm.rename(note, it) },
            onDelete = { vm.delete(note) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteRow(note: NoteEntity, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (note.isFolder) "📁" else "📄", style = MaterialTheme.typography.titleLarge)
        Column(Modifier.padding(start = 12.dp)) {
            Text(
                note.displayTitle().ifBlank { "Без названия" },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val status = remember(note.reminderAt, note.reminderDoneAt) { reminderStatus(note) }
            if (status != null) {
                val color = when {
                    status.missed -> MaterialTheme.colorScheme.error
                    status.done -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.primary
                }
                Text(status.headline, style = MaterialTheme.typography.bodyMedium, color = color)
                Text(status.date, style = MaterialTheme.typography.bodySmall, color = color)
            } else if (!note.isFolder && note.body.isNotBlank() && note.title.isNotBlank()) {
                Text(
                    note.body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CreateDialog(onDismiss: () -> Unit, onCreate: (String, Boolean) -> Unit) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Создать") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Название") },
                singleLine = true,
            )
        },
        confirmButton = {
            Row {
                TextButton(onClick = { onCreate(title, true) }) { Text("📁 Папка") }
                TextButton(onClick = { onCreate(title, false) }) { Text("📄 Заметка") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun ActionDialog(note: NoteEntity, onDismiss: () -> Unit, onRename: (String) -> Unit, onDelete: () -> Unit) {
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
                TextButton(onClick = { confirmDelete = true }) { Text("Удалить") }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}

// ------------------------------------------------------------------ редактор

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: AppViewModel, note: NoteEntity) {
    var title by remember(note.id) { mutableStateOf(note.title) }
    var body by remember(note.id) { mutableStateOf(note.body) }

    // Автосохранение с небольшой задержкой после последней правки.
    LaunchedEffect(note.id, title, body) {
        delay(600)
        vm.save(note.id, title, body)
    }
    BackHandler {
        vm.save(note.id, title, body)
        vm.closeEditor()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Заметка") },
                navigationIcon = {
                    TextButton(onClick = {
                        vm.save(note.id, title, body)
                        vm.closeEditor()
                    }) { Text("←") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .fillMaxSize(),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Название") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Текст") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 8.dp, bottom = 8.dp),
            )
        }
    }
}
