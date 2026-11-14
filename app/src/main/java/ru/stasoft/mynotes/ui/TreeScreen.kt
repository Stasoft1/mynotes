package ru.stasoft.mynotes.ui

import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Mic
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.FilterChip
import ru.stasoft.mynotes.data.searchSnippet
import ru.stasoft.mynotes.data.searchWords
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.luminance
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
import ru.stasoft.mynotes.data.formatValuesMoney
import ru.stasoft.mynotes.data.resolveTitle

private val BarColor = Color(0xFF1B2330)

/** Тёмная панель кнопок сверху, листается по горизонтали (как в MyTreeNotes). */
@Composable
fun TopStrip(content: @Composable RowScope.() -> Unit) {
    val glass = LocalButtonStyle.current == 1
    val dark = androidx.compose.material3.MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        val w = (view.context as? android.app.Activity)?.window
        if (w != null) androidx.core.view.WindowCompat.getInsetsController(w, view).isAppearanceLightStatusBars = glass && !dark
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (glass) androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow else BarColor)
            .statusBarsPadding()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun StripButton(icon: ImageVector, description: String, accent: Boolean = false, onClick: () -> Unit) {
    if (LocalButtonStyle.current == 1) {
        val dark = androidx.compose.material3.MaterialTheme.colorScheme.surface.luminance() < 0.5f
        GlassBox(
            Modifier.padding(horizontal = 4.dp, vertical = 7.dp).size(46.dp),
            androidx.compose.foundation.shape.CircleShape,
            if (accent) Color(0xFFFFB27A) else pastelFor(description),
            onClick,
        ) {
            Icon(icon, contentDescription = description, tint = if (dark) Color.White else Color(0xFF1F2430), modifier = Modifier.size(24.dp))
        }
        return
    }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
    val top = if (accent) Color(0xFFFFA85C) else Color(0xFF42536E)
    val bottom = if (accent) Color(0xFFE5701A) else Color(0xFF273448)
    Box(
        Modifier
            .padding(horizontal = 3.dp, vertical = 7.dp)
            .size(46.dp)
            .shadow(5.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(top, bottom)))
            .border(
                1.dp,
                androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0x80FFFFFF), Color(0x14FFFFFF))),
                shape,
            )
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TreeScreen(vm: AppViewModel) {
    val items by vm.items.collectAsState()
    val numbers by vm.numbers.collectAsState()
    val today by vm.today.collectAsState()
    val query by vm.query.collectAsState()
    val folderId by vm.folderId.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<NoteEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<NoteEntity?>(null) }
    var showBioSetup by remember { mutableStateOf(false) }
    var showSnippets by remember { mutableStateOf(false) }
    LaunchedEffect(vm.reopenTemplates) {
        if (vm.reopenTemplates) {
            vm.reopenTemplates = false
            showSnippets = true
        }
    }
    var showSort by remember { mutableStateOf(false) }
    var showCheck by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    var showFuel by remember { mutableStateOf(false) }
    var showService by remember { mutableStateOf(false) }
    val serviceStatus by vm.serviceStatus.collectAsState()
    val photoCounts by vm.photoCounts.collectAsState()
    var showBackup by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importMtnt(uri)
    }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val t = r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (r.resultCode == android.app.Activity.RESULT_OK && !t.isNullOrBlank()) vm.voiceInput(t)
    }
    fun startSpeech() {
        try {
            speech.launch(
                android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                    .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Диктуйте заметку"),
            )
        } catch (e: Exception) {
            vm.message = "На телефоне не найдена служба распознавания речи"
        }
    }
    VoiceDialog(vm, onRetry = { startSpeech() })

    // Новый день: при возвращении в приложение создаём папку сегодняшнего дня, если включено.
    val dayOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(dayOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) { vm.ensureToday(); vm.checkService() } }
        dayOwner.lifecycle.addObserver(obs)
        onDispose { dayOwner.lifecycle.removeObserver(obs) }
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
                StripButton(Icons.Filled.Mic, "Голосовая заметка") { startSpeech() }
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
                            text = { Text("Отчёт по папке…") },
                            onClick = {
                                menuOpen = false
                                showReport = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (vm.selectedId != null) "Экспорт выбранного в PDF" else "Экспорт папки в PDF") },
                            onClick = {
                                menuOpen = false
                                vm.exportPdf(vm.selectedId)
                            },
                        )
                        if (ru.stasoft.mynotes.modules.Modules.on("auto") && serviceStatus.isNotEmpty()) DropdownMenuItem(
                            text = { Text("Состояние ТО…") },
                            onClick = {
                                menuOpen = false
                                showService = true
                            },
                        )
                        if (ru.stasoft.mynotes.modules.Modules.on("auto")) DropdownMenuItem(
                            text = { Text("Статистика заправок…") },
                            onClick = {
                                menuOpen = false
                                showFuel = true
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
                            text = { Text("Настройки") },
                            leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                showSettings = true
                            },
                        )
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
            if (searching && LocalButtonStyle.current == 1) {
                GlassTextField(
                    value = query,
                    onValueChange = { vm.query.value = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    placeholder = "Поиск по всей базе",
                    leading = Icons.Filled.Search,
                )
            } else if (searching) {
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
            if (searching) {
                val sType by vm.searchType.collectAsState()
                val sAge by vm.searchAge.collectAsState()
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("Всё", "Заметки", "Папки").forEachIndexed { i, l ->
                        FilterChip(selected = sType == i, onClick = { vm.searchType.value = i }, label = { Text(l) })
                    }
                    listOf("Любая дата", "Неделя", "Месяц", "Год").forEachIndexed { i, l ->
                        FilterChip(selected = sAge == i, onClick = { vm.searchAge.value = i }, label = { Text(l) })
                    }
                }
            }
            val dueService = serviceStatus.filter { it.level in 1..2 }
            if (folderId == null && query.isBlank() && dueService.isNotEmpty()) Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.tertiaryContainer).clickable { showService = true }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text("Пора на ТО", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
                dueService.take(3).forEach {
                    Text(
                        (if (dueService.map { d -> d.carName }.distinct().size > 1) it.carName + ": " else "") + it.itemName + " — " + it.text(),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            today?.let { t ->
                if (folderId == null && query.isBlank()) TodayCard(t, onOpen = { vm.openFolderById(t.folder.id) })
            }
            vm.clip?.let { c ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        (if (c.move) "Перенос: " else "Копирование: ") + "«${c.title}»",
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { vm.pasteHere() }) { Text("Вставить сюда") }
                    TextButton(onClick = { vm.cancelClip() }) { Text("Отмена") }
                }
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
                    Text("📂", style = MaterialTheme.typography.titleLarge)
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
            // ---- перетаскивание: долгий тап поднимает строку, дальше её можно двигать по списку ----
            val currentSort by vm.sortMode.collectAsState()
            val canDrag = query.isBlank() && currentSort == 0
            val itemsNow by rememberUpdatedState(items)
            val haptic = LocalHapticFeedback.current
            var dragId by remember { mutableStateOf<Long?>(null) }
            var dragDy by remember { mutableFloatStateOf(0f) }
            var dragAcc by remember { mutableFloatStateOf(0f) }
            var dragMoved by remember { mutableStateOf(false) }
            var order by remember { mutableStateOf<List<NoteEntity>?>(null) }
            var askManual by remember { mutableStateOf(false) }
            var suppressUntil by remember { mutableStateOf(0L) }
            val shown = order ?: items
            LaunchedEffect(folderId) { order = null; dragId = null; dragDy = 0f }
            LaunchedEffect(items) {
                val o = order
                if (o != null && dragId == null && o.map { it.id } == items.map { it.id }) order = null
            }

            fun checkSwap() {
                val id = dragId ?: return
                val list = order ?: return
                val idx = list.indexOfFirst { it.id == id }
                val vis = listState.layoutInfo.visibleItemsInfo
                val me = vis.firstOrNull { it.key == id } ?: return
                if (me.index != idx) return // раскладка ещё не обновилась после прошлой перестановки
                val center = me.offset + me.size / 2f + dragDy
                val next = vis.firstOrNull { it.index == idx + 1 }
                val prev = vis.firstOrNull { it.index == idx - 1 }
                if (next != null && center > next.offset + next.size / 2f) {
                    order = list.toMutableList().apply { add(idx + 1, removeAt(idx)) }
                    dragDy -= next.size
                } else if (prev != null && center < prev.offset + prev.size / 2f) {
                    order = list.toMutableList().apply { add(idx - 1, removeAt(idx)) }
                    dragDy += prev.size
                }
            }

            // Автопрокрутка, когда строку подвели к верхнему или нижнему краю.
            LaunchedEffect(dragId) {
                while (dragId != null) {
                    val id = dragId
                    val info = listState.layoutInfo
                    val me = info.visibleItemsInfo.firstOrNull { it.key == id }
                    if (me != null && canDrag) {
                        val center = me.offset + me.size / 2f + dragDy
                        val zone = 140f
                        val top = info.viewportStartOffset + zone
                        val bottom = info.viewportEndOffset - zone
                        val speed = when {
                            center < top -> -((top - center) / zone).coerceIn(0f, 1f) * 28f
                            center > bottom -> ((center - bottom) / zone).coerceIn(0f, 1f) * 28f
                            else -> 0f
                        }
                        if (speed != 0f) {
                            dragDy += listState.dispatchRawDelta(speed)
                            checkSwap()
                        }
                    }
                    kotlinx.coroutines.delay(16)
                }
            }

            fun finishDrag(note: NoteEntity) {
                suppressUntil = android.os.SystemClock.uptimeMillis() + 450
                val o = order
                val moved = dragMoved
                dragId = null
                dragDy = 0f
                dragMoved = false
                when {
                    !moved -> { order = null; actionTarget = note } // отпустили без движения: меню действий
                    !canDrag -> { order = null; if (query.isBlank()) askManual = true }
                    o != null && o.map { it.id } != itemsNow.map { it.id } -> vm.reorder(o.map { it.id })
                    else -> order = null
                }
            }

            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                itemsIndexed(shown, key = { _, n -> n.id }) { index, note ->
                    val dragging = dragId == note.id
                    val rowMod = (if (dragging) {
                        Modifier
                            .zIndex(1f)
                            .graphicsLayer {
                                translationY = dragDy
                                scaleX = 1.03f
                                scaleY = 1.03f
                                shadowElevation = 24f
                                shape = RoundedCornerShape(12.dp)
                            }
                    } else Modifier.animateItem())
                    val dragMod = Modifier
                        .pointerInput(note.id, canDrag) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    suppressUntil = Long.MAX_VALUE
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    vm.select(note)
                                    dragId = note.id
                                    dragDy = 0f
                                    dragAcc = 0f
                                    dragMoved = false
                                    order = itemsNow
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragAcc += kotlin.math.abs(amount.x) + kotlin.math.abs(amount.y)
                                    if (dragAcc > 16f) dragMoved = true
                                    if (canDrag && dragMoved) {
                                        dragDy += amount.y
                                        checkSwap()
                                    }
                                },
                                onDragEnd = { finishDrag(note) },
                                onDragCancel = { if (dragId != null) finishDrag(note) },
                            )
                        }
                    NoteRow(
                        note = note,
                        modifier = rowMod,
                        dragModifier = dragMod,
                        lifted = dragging,
                        zebra = index % 2 == 1,
                        number = numbers[note.id],
                        photos = photoCounts[note.id] ?: 0,
                        selected = vm.selectedId == note.id,
                        onClick = { if (android.os.SystemClock.uptimeMillis() > suppressUntil) { if (note.isFolder) vm.openFolder(note) else vm.select(note) } },
                        onDoubleClick = { if (android.os.SystemClock.uptimeMillis() > suppressUntil) vm.editNote(note) },
                        onLongClick = null, // долгий тап обрабатывает жест перетаскивания
                        hl = query,
                    )
                }
            }
            if (askManual) AlertDialog(
                onDismissRequest = { askManual = false },
                title = { Text("Ручной порядок") },
                text = { Text("В этой папке включена автоматическая сортировка. Перейти на ручной порядок? Записи останутся в том порядке, в котором показаны сейчас, и их можно будет двигать.") },
                confirmButton = { TextButton(onClick = { askManual = false; vm.makeManualKeepOrder() }) { Text("Перейти") } },
                dismissButton = { TextButton(onClick = { askManual = false }) { Text("Не надо") } },
            )
        }
    }

    if (showSnippets) SnippetsDialog(vm, onDismiss = { showSnippets = false })
    if (showSort) SortDialog(vm, onDismiss = { showSort = false })
    if (showCheck) ReadingCheckDialog(vm, onDismiss = { showCheck = false })
    if (showService) ServiceDialog(vm, onDismiss = { showService = false })
    if (showFuel) FuelDialog(vm, folderId, onDismiss = { showFuel = false })
    if (showReport) ReportDialog(vm, folderId, onDismiss = { showReport = false })
    if (showBackup) BackupDialog(vm, onDismiss = { showBackup = false })
    if (showSettings) SettingsDialog(
        vm,
        onDismiss = { showSettings = false },
        onImport = { showSettings = false; picker.launch(arrayOf("*/*")) },
        onCheck = { showSettings = false; showCheck = true },
        onBackup = { showSettings = false; showBackup = true },
        onBioOn = { showSettings = false; showBioSetup = true },
    )
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
            onFlatten = { toRoot -> vm.flattenFolder(note, toRoot) },
            onClip = { move -> vm.startClip(note, move) },
            canReorder = query.isBlank() && vm.sortMode.value == 0,
            onEdge = { top -> vm.moveToEdge(note, top) },
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
    modifier: Modifier = Modifier,
    dragModifier: Modifier = Modifier,
    lifted: Boolean = false,
    number: List<Pair<String, Double>>?,
    photos: Int = 0,
    zebra: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    hl: String = "",
) {
    val status = reminderStatus(note)
    val textColor = note.textColorArgb?.let { Color(it) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (lifted) MaterialTheme.colorScheme.surfaceContainerHighest
                else if (selected) MaterialTheme.colorScheme.secondaryContainer
                else rowBackground(note)
                    ?: if (zebra) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f) else Color.Transparent,
            )
            .combinedClickable(onClick = onClick, onDoubleClick = onDoubleClick, onLongClick = onLongClick)
            .then(dragModifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Слева всегда папка или заметка, чтобы их было видно сразу; своя иконка — справа, как в MyTreeNotes.
        Text(if (note.isFolder) "📁" else "📄", style = MaterialTheme.typography.titleLarge)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                highlight(resolveTitle(note.displayTitle(), number).ifBlank { "Без названия" }, hl),
                style = MaterialTheme.typography.titleMedium,
                color = textColor ?: Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (number != null) {
                Text(
                    formatValues(number),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (status == null && !note.isFolder && note.body.isNotBlank()) {
                Text(
                    highlight(
                        (if (hl.isNotBlank()) searchSnippet(note.body, hl) else null)
                            ?: note.body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
                        hl,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Дата напоминания всегда подписана внизу записи, в том числе у выполненных.
            if (status != null) {
                if (status.done) {
                    Text(
                        "Выполнено · ${status.date}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        status.headline,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (status.missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                    Text(status.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (!note.isFolder && note.body.isNotEmpty()) LinkButtons(note.displayTitle(), note.body)
        if (photos > 0) Text("📷$photos", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        NoteIcon(
            effectiveIcon(note),
            note.iconColorArgb?.let { Color(it) } ?: legacyIconColor(note) ?: textColor ?: MaterialTheme.colorScheme.primary,
            28.dp,
        )
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
    onFlatten: (toRoot: Boolean) -> Unit,
    onClip: (move: Boolean) -> Unit,
    canReorder: Boolean,
    onEdge: (top: Boolean) -> Unit,
) {
    var title by remember { mutableStateOf(note.title) }
    var mode by remember { mutableStateOf(0) } // 0 — список действий, 1 — переименование, 2 — подтверждение удаления
    val name = note.displayTitle().ifBlank { "Без названия" }
    if (mode == 2) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Удалить?") },
            text = {
                Text(
                    if (note.isFolder) "Папка «$name» будет удалена вместе со всем содержимым."
                    else "Заметка «$name» будет удалена.",
                )
            },
            confirmButton = { TextButton(onClick = { onDelete(); onDismiss() }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        )
        return
    }
    if (mode == 1) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Переименовать") },
            text = { OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Название") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onRename(title); onDismiss() }) { Text("Готово") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        )
        return
    }
    @Composable
    fun Item(icon: ImageVector, text: String, danger: Boolean = false, onClick: () -> Unit) {
        val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            Text(text, color = color, modifier = Modifier.padding(start = 16.dp))
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                Item(Icons.Filled.Edit, "Переименовать") { mode = 1 }
                Item(Icons.Filled.DriveFileMove, "Переместить в другую папку…") { onClip(true); onDismiss() }
                Item(Icons.Filled.ContentCopy, if (note.isFolder) "Копировать со всем содержимым…" else "Копировать…") { onClip(false); onDismiss() }
                if (canReorder) {
                    Item(Icons.Filled.VerticalAlignTop, "В начало списка") { onEdge(true); onDismiss() }
                    Item(Icons.Filled.VerticalAlignBottom, "В конец списка") { onEdge(false); onDismiss() }
                }
                if (note.isFolder) {
                    Item(Icons.Filled.Unarchive, "Вынести содержимое наверх") { onFlatten(false); onDismiss() }
                    if (note.parentId != null) Item(Icons.Filled.Unarchive, "Вынести содержимое в корень") { onFlatten(true); onDismiss() }
                }
                Item(Icons.Filled.Delete, "Удалить", danger = true) { mode = 2 }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
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
                    AppCheckbox(checked = auto, onCheckedChange = { mode = if (it) 1 else 0 })
                    Text("Автоматическая")
                }
                Row(Modifier.fillMaxWidth().clickable { folders = !folders }, verticalAlignment = Alignment.CenterVertically) {
                    AppCheckbox(checked = folders, onCheckedChange = { folders = it })
                    Text("Папки сверху")
                }
                SORT_MODES.forEach { (m, label) ->
                    Row(Modifier.fillMaxWidth().clickable { mode = m }, verticalAlignment = Alignment.CenterVertically) {
                        AppRadio(selected = mode == m, onClick = { mode = m })
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

@Composable
private fun TodayCard(t: AppViewModel.TodayInfo, onOpen: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            "Сегодня · " + resolveTitle(t.folder.title, t.values),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (t.values.isEmpty()) "Пока пусто" else formatValuesMoney(t.values),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        if (t.parentTitle != null && t.parentValues.isNotEmpty()) {
            Text(
                "${t.parentTitle}: " + formatValuesMoney(t.parentValues),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Подсвечивает слова запроса жёлтым фоном. */
@Composable
private fun highlight(text: String, q: String): androidx.compose.ui.text.AnnotatedString {
    if (q.isBlank()) return androidx.compose.ui.text.AnnotatedString(text)
    val words = searchWords(q)
    val low = text.lowercase()
    val mark = androidx.compose.ui.text.SpanStyle(background = Color(0x99FFD54F), color = Color.Black)
    return androidx.compose.ui.text.buildAnnotatedString {
        append(text)
        if (low.length == text.length) words.forEach { w ->
            var i = low.indexOf(w)
            while (i >= 0) {
                addStyle(mark, i, i + w.length)
                i = low.indexOf(w, i + w.length)
            }
        }
    }
}
