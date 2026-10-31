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
import ru.stasoft.mynotes.data.varLabel
import ru.stasoft.mynotes.data.varsToJson
import ru.stasoft.mynotes.data.parseVars
import ru.stasoft.mynotes.data.AGG_LABELS
import ru.stasoft.mynotes.data.VarDef
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
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
    var own by remember(note.id) { mutableStateOf(parseVars(note.varsJson)) }
    var inherited by remember(note.id) { mutableStateOf<List<Pair<VarDef, String>>>(emptyList()) }
    var showAddVar by remember { mutableStateOf(false) }
    LaunchedEffect(note.id) { inherited = vm.inheritedVars(note) }
    val legacy = effectiveIcon(note)
    val shownKey: String? = if (iconKey == null) legacy else iconKey?.ifEmpty { null }

    fun close() {
        val ic = iconKey
        val b = bg
        val f = fg
        val cl = checklist
        val ro = readOnly
        val tg = tags
        val vj = varsToJson(own)
        vm.closeProps { n ->
            n.copy(iconKey = ic, colorArgb = b, textColorArgb = f, checklist = cl, readOnly = ro, keyword = tg.trim(), varsJson = vj)
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
            PropRow(
                "Название иконки",
                shownKey ?: "Не выбрана",
                onClick = { showIcons = true },
            ) {
                if (shownKey != null) {
                    NoteIcon(shownKey, legacyIconColor(note)?.takeIf { iconKey == null } ?: MaterialTheme.colorScheme.primary, 32.dp)
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
            if (note.isFolder) {
                VarsSection(
                    inherited = inherited,
                    own = own,
                    onChange = { own = it },
                    onAdd = { showAddVar = true },
                )
                HorizontalDivider()
            }
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

    if (showAddVar) {
        AddVarDialog(
            onDismiss = { showAddVar = false },
            onAdd = { def ->
                showAddVar = false
                // Если такая переменная была отключена в этой папке — просто возвращаем её.
                own = own.filterNot { it.name == def.name } +
                    if (inherited.any { it.first.name == def.name && it.first.agg == def.agg } &&
                        own.any { it.name == def.name && it.off }
                    ) emptyList() else listOf(def)
            },
        )
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

@Composable
private fun AggPicker(agg: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text(AGG_LABELS[agg] ?: agg) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            AGG_LABELS.forEach { (k, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; onPick(k) })
            }
        }
    }
}

@Composable
private fun VarsSection(
    inherited: List<Pair<VarDef, String>>,
    own: List<VarDef>,
    onChange: (List<VarDef>) -> Unit,
    onAdd: () -> Unit,
) {
    SectionTitle("ПЕРЕМЕННЫЕ")
    Text(
        "Поле «Имя: число» в тексте заметок. Действует в этой папке и во всех вложенных; значения сводятся вверх " +
            "по дереву и показываются под названием.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
    val inheritedNames = inherited.map { it.first.name }.toSet()
    inherited.forEach { (def, from) ->
        val override = own.firstOrNull { it.name == def.name }
        val disabled = override?.off == true
        val agg = if (override != null && !override.off) override.agg else def.agg
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    varLabel(def.name),
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (disabled) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                )
                Text("из папки «$from»", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (disabled) {
                TextButton(onClick = { onChange(own.filterNot { it.name == def.name }) }) { Text("Вернуть") }
            } else {
                AggPicker(agg) { picked ->
                    val rest = own.filterNot { it.name == def.name }
                    onChange(if (picked == def.agg) rest else rest + VarDef(def.name, picked))
                }
                IconButton(onClick = { onChange(own.filterNot { it.name == def.name } + VarDef(def.name, def.agg, off = true)) }) {
                    Icon(Icons.Filled.Delete, "Отключить здесь")
                }
            }
        }
    }
    own.filter { !it.off && it.name !in inheritedNames }.forEach { def ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(varLabel(def.name), style = MaterialTheme.typography.titleMedium)
                Text("задана здесь", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AggPicker(def.agg) { picked -> onChange(own.map { if (it.name == def.name) it.copy(agg = picked) else it }) }
            IconButton(onClick = { onChange(own.filterNot { it.name == def.name }) }) { Icon(Icons.Filled.Delete, "Удалить") }
        }
    }
    TextButton(onClick = onAdd, modifier = Modifier.padding(horizontal = 8.dp)) { Text("+ Добавить переменную") }
}

@Composable
private fun AddVarDialog(onDismiss: () -> Unit, onAdd: (VarDef) -> Unit) {
    var name by remember { mutableStateOf("") }
    var agg by remember { mutableStateOf("sum") }
    var firstLine by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая переменная") },
        text = {
            Column {
                Row(Modifier.fillMaxWidth().clickable { firstLine = !firstLine }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = firstLine, onCheckedChange = { firstLine = it })
                    Text("Число в первой строке заметки")
                }
                if (!firstLine) {
                    TextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Название поля, например Сумма") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text("Как сводить вверх по дереву", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                AggPicker(agg) { agg = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = firstLine || name.isNotBlank(),
                onClick = { onAdd(VarDef(if (firstLine) "" else name.trim().trimEnd(':'), agg)) },
            ) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
