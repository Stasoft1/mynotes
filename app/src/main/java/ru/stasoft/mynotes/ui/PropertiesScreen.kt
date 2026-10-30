package ru.stasoft.mynotes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import ru.stasoft.mynotes.data.NoteEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Фон строки списка: выбранный цвет, смягчённый, чтобы текст читался. */
fun rowBackground(note: NoteEntity): Color? = note.colorArgb?.let { Color(it).copy(alpha = 0.45f) }

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
    HorizontalDivider()
}

@Composable
private fun PropRow(title: String, subtitle: String? = null, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing()
    }
}

@Composable
fun PropertiesScreen(vm: AppViewModel, note: NoteEntity) {
    var iconKey by remember(note.id) { mutableStateOf(note.iconKey) }
    var bg by remember(note.id) { mutableStateOf(note.colorArgb) }
    var fg by remember(note.id) { mutableStateOf(note.textColorArgb) }
    var checklist by remember(note.id) { mutableStateOf(note.checklist) }
    var readOnly by remember(note.id) { mutableStateOf(note.readOnly) }
    var tags by remember(note.id) { mutableStateOf(note.keyword) }
    var showIcons by remember { mutableStateOf(false) }
    var showColors by remember { mutableStateOf(false) }
    var showReminder by remember { mutableStateOf(false) }
    var raw by remember { mutableStateOf(false) }

    fun close() {
        val ic = iconKey
        val b = bg
        val f = fg
        val cl = checklist
        val ro = readOnly
        val tg = tags
        vm.closeProps { n ->
            n.copy(iconKey = ic, colorArgb = b, textColorArgb = f, checklist = cl, readOnly = ro, keyword = tg.trim())
        }
    }
    BackHandler { close() }

    Scaffold(
        topBar = {
            TopStrip {
                StripButton(Icons.AutoMirrored.Filled.ArrowBack, "Назад") { close() }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                note.title.ifBlank { "Без названия" },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp),
            )

            SectionTitle("ИЗОБРАЖЕНИЕ ИЛИ ИКОНКА")
            val shownKey = if (iconKey == null) effectiveIcon(note) else iconKey?.ifEmpty { null }
            PropRow(
                "Название иконки",
                shownKey ?: "Не выбрана",
                onClick = { showIcons = true },
            ) {
                if (shownKey != null) {
                    NoteIcon(shownKey, MaterialTheme.colorScheme.primary, 32.dp)
                    IconButton(onClick = { iconKey = "" }) { Icon(Icons.Filled.Delete, "Убрать иконку") }
                }
            }

            SectionTitle("СОБЫТИЕ")
            PropRow(
                "Дата события",
                note.reminderAt?.let { SimpleDateFormat("d MMMM yyyy 'г.' HH:mm", Locale("ru")).format(Date(it)) }
                    ?: "Дата не установлена",
                onClick = { showReminder = true },
            )

            SectionTitle("ОПЦИИ")
            PropRow("Выбрать цвет", "Выбрать цвет фона и цвет текста", onClick = { showColors = true }) {
                Box(
                    Modifier
                        .size(width = 72.dp, height = 36.dp)
                        .background(bg?.let { Color(it) } ?: Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Aa", color = fg?.let { Color(it) } ?: MaterialTheme.colorScheme.onSurface)
                }
                if (bg != null || fg != null) {
                    IconButton(onClick = { bg = null; fg = null }) { Icon(Icons.Filled.Delete, "Сбросить цвет") }
                }
            }
            HorizontalDivider()
            PropRow("Текст в виде списка", "Отобразить текст как список задач", onClick = { checklist = !checklist }) {
                Checkbox(checked = checklist, onCheckedChange = { checklist = it })
            }
            HorizontalDivider()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Теги", style = MaterialTheme.typography.titleMedium)
                TextField(
                    value = tags,
                    onValueChange = { tags = it },
                    placeholder = { Text("Теги (для поиска)") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Разделитель — пробел", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
            PropRow("Запретить редактирование", "Запретить редактирование данных в заметке", onClick = { readOnly = !readOnly }) {
                Checkbox(checked = readOnly, onCheckedChange = { readOnly = it })
            }

            SectionTitle("ИНФОРМАЦИЯ")
            PropRow(
                "Дата последнего изменения",
                SimpleDateFormat("d MMMM yyyy 'г.' (E) HH:mm", Locale("ru")).format(Date(note.updatedAt)),
            )
            HorizontalDivider()
            PropRow("Идентификатор заметки", note.legacyId ?: note.id.toString())
            HorizontalDivider()
            PropRow("Служебные данные импорта", if (raw) null else "Нажмите, чтобы показать", onClick = { raw = !raw })
            if (raw) {
                Text(
                    "flags = ${note.flags}\nmap = ${note.mapJson.ifBlank { "(пусто)" }}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            Box(Modifier.height(24.dp))
        }
    }

    if (showIcons) {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        var search by remember { mutableStateOf("") }
        val all = remember { Mdi.names(ctx) }
        val list = remember(search, all) {
            val q = search.trim().lowercase()
            if (q.isEmpty()) MDI_POPULAR.filter { all.contains(it) }
            else all.filter { it.contains(q) }.take(300)
        }
        AlertDialog(
            onDismissRequest = { showIcons = false },
            title = { Text("Из набора иконок") },
            text = {
                Column {
                    TextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = { Text("Поиск по названию (англ.), например car") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(52.dp),
                        modifier = Modifier.padding(top = 8.dp).height(320.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(list) { key ->
                            Box(
                                Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(if (key == shownKey) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                    .clickable {
                                        iconKey = key
                                        showIcons = false
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                MdiIcon(key, MaterialTheme.colorScheme.primary, 30.dp)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showIcons = false }) { Text("Закрыть") } },
        )
    }

    if (showColors) {
        var tab by remember { mutableIntStateOf(0) }
        AlertDialog(
            onDismissRequest = { showColors = false },
            title = { Text("Цвет") },
            text = {
                Column {
                    TabRow(selectedTabIndex = tab) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Фон") })
                        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Текст") })
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(7),
                        modifier = Modifier.padding(top = 12.dp).height(260.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(COLOR_PALETTE) { c ->
                            val chosen = if (tab == 0) bg == c else fg == c
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(c))
                                    .clickable { if (tab == 0) bg = c else fg = c },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (chosen) {
                                    Icon(
                                        Icons.Filled.Check,
                                        null,
                                        tint = if (c == 0xFFFFFFFF.toInt() || Color(c).luminance() > 0.6f) Color.Black else Color.White,
                                    )
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.weight(1f).height(40.dp).background(bg?.let { Color(it) } ?: Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Пример текста", color = fg?.let { Color(it) } ?: MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showColors = false }) { Text("Готово") } },
            dismissButton = {
                TextButton(onClick = {
                    if (tab == 0) bg = null else fg = null
                }) { Text("Без цвета") }
            },
        )
    }

    if (showReminder) {
        ReminderDialog(
            note = note,
            onDismiss = { showReminder = false },
            onSave = { due, period, lead ->
                showReminder = false
                vm.setReminder(note.id, due, period, lead)
            },
            onClear = {
                showReminder = false
                vm.setReminder(note.id, null, 0, 0)
            },
        )
    }
}
