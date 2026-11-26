package ru.stasoft.mynotes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.stasoft.mynotes.data.NoteEntity
import ru.stasoft.mynotes.data.displayTitle

private fun folderLabelOf(id: Long, by: Map<Long, NoteEntity>): String = when {
    id == QUICK_CURRENT -> "Текущая папка"
    id == QUICK_ROOT -> "Главный экран"
    else -> by[id]?.let { pathOf(it, by) } ?: "папка не найдена"
}

private fun presetPlace(p: QuickPreset, by: Map<Long, NoteEntity>, snippets: Map<Long, String>): String {
    val folder = folderLabelOf(p.folderId, by) + if (p.day) " → папка дня" else ""
    val tpl = if (p.snippetId < 0) "пустая заметка" else snippets[p.snippetId] ?: "шаблон не найден"
    return "$folder · $tpl"
}

/** Список заготовок, который раскрывается по долгому нажатию на «+». */
@Composable
fun QuickMenu(vm: AppViewModel, expanded: Boolean, onDismiss: () -> Unit, onConfigure: () -> Unit) {
    val snippets by vm.allSnippets.collectAsState()
    var all by remember { mutableStateOf<List<NoteEntity>>(emptyList()) }
    LaunchedEffect(expanded) { if (expanded) all = vm.allNotesOnce() }
    val by = remember(all) { all.associateBy { it.id } }
    val sn = remember(snippets) { snippets.associate { it.id to it.label } }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (vm.quickPresets.isEmpty()) {
            Text("Заготовок пока нет. Заготовка: папка + шаблон, создаётся одним нажатием.", Modifier.padding(horizontal = 16.dp, vertical = 8.dp).widthIn(max = 260.dp), style = MaterialTheme.typography.bodySmall)
        }
        vm.quickPresets.forEach { p ->
            DropdownMenuItem(
                text = {
                    Column {
                        Text(p.name)
                        Text(presetPlace(p, by, sn), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                onClick = { onDismiss(); vm.createQuick(p) },
            )
        }
        HorizontalDivider()
        DropdownMenuItem(text = { Text("Настроить…") }, onClick = onConfigure)
    }
}

/** Настройка заготовок: добавить, изменить, удалить, поменять порядок. */
@Composable
fun QuickPresetsDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val snippets by vm.allSnippets.collectAsState()
    var all by remember { mutableStateOf<List<NoteEntity>>(emptyList()) }
    LaunchedEffect(Unit) { all = vm.allNotesOnce() }
    val by = remember(all) { all.associateBy { it.id } }
    val sn = remember(snippets) { snippets.associate { it.id to it.label } }
    var editIndex by remember { mutableStateOf<Int?>(null) } // -1 — новая
    val list = vm.quickPresets
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Быстрые заметки") },
        text = {
            Column {
                Text("Долгое нажатие на «+» раскрывает этот список. Каждая заготовка создаёт заметку в нужной папке из нужного шаблона.", style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.heightIn(max = 340.dp).padding(top = 8.dp)) {
                    items(list.size) { i ->
                        val p = list[i]
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).clickable { editIndex = i }.padding(vertical = 8.dp)) {
                                Text(p.name)
                                Text(presetPlace(p, by, sn), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(enabled = i > 0, onClick = { vm.saveQuickPresets(list.toMutableList().also { it.add(i - 1, it.removeAt(i)) }) }) { Text("↑") }
                            TextButton(enabled = i < list.size - 1, onClick = { vm.saveQuickPresets(list.toMutableList().also { it.add(i + 1, it.removeAt(i)) }) }) { Text("↓") }
                        }
                        HorizontalDivider()
                    }
                }
                if (list.isEmpty()) Text("Пока пусто.", Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = { editIndex = -1 }) { Text("Добавить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
    editIndex?.let { idx ->
        val initial = list.getOrNull(idx) ?: QuickPreset("")
        QuickPresetEditDialog(
            vm, initial, all, snippets.map { it.id to it.label },
            onDismiss = { editIndex = null },
            onDelete = if (idx >= 0) ({ vm.saveQuickPresets(list.filterIndexed { i, _ -> i != idx }); editIndex = null }) else null,
            onSave = { p ->
                vm.saveQuickPresets(if (idx >= 0) list.mapIndexed { i, o -> if (i == idx) p else o } else list + p)
                editIndex = null
            },
        )
    }
}

@Composable
private fun QuickPresetEditDialog(
    vm: AppViewModel, initial: QuickPreset, all: List<NoteEntity>, snippets: List<Pair<Long, String>>,
    onDismiss: () -> Unit, onDelete: (() -> Unit)?, onSave: (QuickPreset) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var folder by remember { mutableStateOf(initial.folderId) }
    var snippet by remember { mutableStateOf(initial.snippetId) }
    var day by remember { mutableStateOf(initial.day) }
    var pick by remember { mutableStateOf(0) } // 1 папка, 2 шаблон
    val by = remember(all) { all.associateBy { it.id } }
    val folderLabel = folderLabelOf(folder, by)
    val snippetLabel = if (snippet < 0) "Пустая заметка" else snippets.firstOrNull { it.first == snippet }?.second ?: "Шаблон не найден"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (onDelete == null) "Новая заготовка" else "Заготовка") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название в списке") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Column(Modifier.fillMaxWidth().clickable { pick = 1 }) {
                    Text(if (day) "Где искать папку дня" else "Куда создавать", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text("📁 $folderLabel")
                }
                Row(Modifier.fillMaxWidth().clickable { day = !day }, verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = day, onCheckedChange = { day = it })
                    Column {
                        Text("Папка дня")
                        Text(
                            "В выбранной папке найти папку сегодняшней даты. Если её нет, создать: год → месяц → день (по образцу уже имеющихся).",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Column(Modifier.fillMaxWidth().clickable { pick = 2 }) {
                    Text("Из какого шаблона", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text("📝 $snippetLabel")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = name.trim().ifEmpty { if (snippet >= 0) snippetLabel else if (folder >= 0) folderLabel.substringAfterLast(" / ") else "Заметка" }
                onSave(QuickPreset(n, folder, snippet, day))
            }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Удалить") }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
    if (pick == 1) FolderTreeDialog(all, folder, onDismiss = { pick = 0 }) { folder = it; pick = 0 }
    if (pick == 2) PickDialog("Из какого шаблона", listOf(-1L to "Пустая заметка") + snippets, withSearch = false, onDismiss = { pick = 0 }) { snippet = it; pick = 0 }
}

@Composable
private fun PickDialog(title: String, options: List<Pair<Long, String>>, withSearch: Boolean, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    var q by remember { mutableStateOf("") }
    val shown = remember(q, options) { options.filter { q.isBlank() || it.second.contains(q, ignoreCase = true) }.take(100) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (withSearch) OutlinedTextField(value = q, onValueChange = { q = it }, label = { Text("Поиск") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 340.dp).padding(top = 8.dp)) {
                    items(shown) { (id, label) ->
                        Text(label, Modifier.fillMaxWidth().clickable { onPick(id) }.padding(vertical = 10.dp))
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Выбор папки в раскрывающемся дереве; сверху — особые места: текущая папка, папка дня, главный экран. */
@Composable
internal fun FolderTreeDialog(
    all: List<NoteEntity>, selected: Long, onDismiss: () -> Unit,
    specials: List<Pair<Long, String>> = listOf(QUICK_CURRENT to "Текущая папка", QUICK_ROOT to "Главный экран"),
    title: String = "Куда создавать",
    onPick: (Long) -> Unit,
) {
    val folders = remember(all) { all.filter { it.isFolder } }
    val kids = remember(folders) { folders.groupBy { it.parentId }.mapValues { (_, v) -> v.sortedBy { it.displayTitle().lowercase() } } }
    val by = remember(all) { all.associateBy { it.id } }
    var open by remember(all) {
        // Путь к выбранной папке раскрыт сразу.
        val s = HashSet<Long>()
        var c = by[selected]?.parentId
        var g = 0
        while (c != null && g++ < 40) { s.add(c); c = by[c]?.parentId }
        mutableStateOf<Set<Long>>(s)
    }
    val rows = remember(kids, open) {
        val out = ArrayList<Pair<NoteEntity, Int>>()
        fun walk(parent: Long?, depth: Int) {
            for (f in kids[parent].orEmpty()) {
                out.add(f to depth)
                if (f.id in open) walk(f.id, depth + 1)
            }
        }
        walk(null, 0)
        out
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(specials) { (id, label) ->
                    Text(
                        label,
                        Modifier.fillMaxWidth().clickable { onPick(id) }.padding(vertical = 10.dp),
                        color = if (selected == id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                item { HorizontalDivider() }
                items(rows) { (f, depth) ->
                    val has = kids[f.id].orEmpty().isNotEmpty()
                    Row(Modifier.fillMaxWidth().padding(start = (depth * 18).dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (!has) "" else if (f.id in open) "▾" else "▸",
                            Modifier.width(36.dp).clickable(enabled = has) { open = if (f.id in open) open - f.id else open + f.id }.padding(vertical = 10.dp),
                        )
                        Text(
                            "📁 " + f.displayTitle().ifBlank { "Без названия" },
                            Modifier.weight(1f).clickable { onPick(f.id) }.padding(vertical = 10.dp),
                            color = if (selected == f.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
