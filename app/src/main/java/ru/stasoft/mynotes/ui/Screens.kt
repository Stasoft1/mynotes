package ru.stasoft.mynotes.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import ru.stasoft.mynotes.data.SnippetEntity
import ru.stasoft.mynotes.data.expandSnippet
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.stasoft.mynotes.crypto.BiometricVault
import ru.stasoft.mynotes.data.NoteEntity
import ru.stasoft.mynotes.data.displayTitle

@Composable
fun AppRoot(vm: AppViewModel) {
    BackHandler(enabled = vm.unlocked) { vm.goUp() }
    val appCtx = LocalContext.current
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(vm.unlocked) {
        if (vm.unlocked && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(appCtx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            !vm.unlocked -> LockScreen(vm)
            vm.editing != null -> EditorScreen(vm, vm.editing!!)
            vm.showReminders -> ReminderListScreen(vm)
            else -> TreeScreen(vm)
        }
    }
}

// ------------------------------------------------------------------ вход

@Composable
fun LockScreen(vm: AppViewModel) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    val ctx = LocalContext.current
    val activity = ctx as? FragmentActivity
    val canBio = vm.biometricEnabled && !vm.needSetup && vm.biometricAvailable()
    val promptBio: () -> Unit = {
        val cipher = BiometricVault.decryptCipher(ctx)
        if (cipher == null) {
            vm.disableBiometric()
        } else if (activity != null) {
            showBiometricPrompt(
                activity = activity,
                title = "Вход в МоиЗаметки",
                negativeText = "Ввести пароль",
                cipher = cipher,
                onSuccess = { vm.unlockWithBiometric(it) },
                onFailure = { vm.onBiometricFailure(it) },
            )
        }
    }
    // Сразу показываем запрос отпечатка при открытии и после автоблокировки.
    LaunchedEffect(canBio, vm.unlocked) {
        if (canBio && !vm.unlocked) promptBio()
    }
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
            if (canBio) {
                TextButton(onClick = promptBio, modifier = Modifier.padding(start = 8.dp)) { Text("Отпечаток") }
            }
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
    var menuOpen by remember { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<NoteEntity?>(null) }
    var showBioSetup by remember { mutableStateOf(false) }
    var showSnippets by remember { mutableStateOf(false) }
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
                    if (!searching) {
                        BarButton("＋") { vm.newNote() }
                        BarButton("📂") { vm.newChild() }
                    }
                    BarButton(if (searching) "✕" else "🔍") {
                        searching = !searching
                        if (!searching) vm.query.value = ""
                    }
                    if (!searching) BarButton("📅") { vm.showReminders = true }
                    Box {
                        BarButton("⋮") { menuOpen = true }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Вставки этой папки") },
                                onClick = {
                                    menuOpen = false
                                    showSnippets = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Напоминания") },
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
                        }
                    }
                },
            )
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
                    if (query.isBlank()) "Здесь пока пусто. Нажмите ＋, чтобы добавить заметку." else "Ничего не найдено",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.id }) { note ->
                    NoteRow(
                        note = note,
                        selected = vm.selectedId == note.id,
                        onClick = { if (note.isFolder) vm.openFolder(note) else vm.select(note) },
                        onDoubleClick = { vm.editNote(note) },
                        onLongClick = {
                            vm.select(note)
                            actionTarget = note
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showSnippets) SnippetsDialog(vm, onDismiss = { showSnippets = false })
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
        )
    }
}

@Composable
private fun BarButton(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.defaultMinSize(minWidth = 40.dp, minHeight = 40.dp),
        contentPadding = PaddingValues(horizontal = 6.dp),
    ) { Text(text, style = MaterialTheme.typography.titleLarge) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteRow(
    note: NoteEntity,
    selected: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .combinedClickable(onClick = onClick, onDoubleClick = onDoubleClick, onLongClick = onLongClick)
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
    var titleField by remember(note.id) { mutableStateOf(TextFieldValue(note.title)) }
    val title = titleField.text
    var titleFocused by remember(note.id) { mutableStateOf(false) }
    val titleFocus = remember { FocusRequester() }
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
    var bodyField by remember(note.id) { mutableStateOf(TextFieldValue(note.body)) }
    val body = bodyField.text
    var showReminderDialog by remember { mutableStateOf(false) }
    val allSnippets by vm.allSnippets.collectAsState()
    val chain = vm.editingChain
    val snippets = remember(allSnippets, chain) {
        val rank = chain.withIndex().associate { (i, id) -> id to i }
        fun depth(sn: SnippetEntity): Int = sn.folderId?.let { rank[it] } ?: Int.MAX_VALUE
        allSnippets
            .filter { sn -> sn.folderId == null || rank.containsKey(sn.folderId) }
            .sortedWith(compareBy<SnippetEntity>({ depth(it) }, { it.sortIndex }, { it.id }))
    }

    // Автосохранение с небольшой задержкой после последней правки.
    LaunchedEffect(note.id, title, body) {
        delay(600)
        vm.save(note.id, title, body)
    }
    BackHandler {
        vm.save(note.id, title, body)
        vm.closeEditor(title, body)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Заметка") },
                navigationIcon = {
                    TextButton(onClick = {
                        vm.save(note.id, title, body)
                        vm.closeEditor(title, body)
                    }) { Text("←") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .padding(horizontal = 16.dp)
                .fillMaxSize(),
        ) {
            OutlinedTextField(
                value = titleField,
                onValueChange = { titleField = it },
                label = { Text("Название") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(titleFocus)
                    .onFocusChanged { if (it.isFocused) titleFocused = true },
            )
            ReminderRow(note = note, onEdit = { showReminderDialog = true })
            OutlinedTextField(
                value = bodyField,
                onValueChange = { bodyField = it },
                label = { Text("Текст") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 8.dp, bottom = 8.dp)
                    .onFocusChanged { if (it.isFocused) titleFocused = false },
            )
            if (snippets.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                ) {
                    snippets.forEach { sn ->
                        AssistChip(
                            onClick = {
                                // Вставляем туда, где сейчас курсор: в название или в текст.
                                if (titleFocused) {
                                    val text = expandSnippet(sn.text).replace("\r", "").replace("\n", " ")
                                    val start = titleField.selection.min
                                    val end = titleField.selection.max
                                    titleField = TextFieldValue(
                                        titleField.text.replaceRange(start, end, text),
                                        TextRange(start + text.length),
                                    )
                                } else {
                                    val text = expandSnippet(sn.text)
                                    val start = bodyField.selection.min
                                    val end = bodyField.selection.max
                                    bodyField = TextFieldValue(
                                        bodyField.text.replaceRange(start, end, text),
                                        TextRange(start + text.length),
                                    )
                                }
                            },
                            label = { Text(sn.label, maxLines = 1) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
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
