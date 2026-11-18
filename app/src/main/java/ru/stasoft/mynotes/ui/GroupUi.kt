package ru.stasoft.mynotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.stasoft.mynotes.data.NoteEntity

/** Верхняя панель режима группового выбора. */
@Composable
fun MultiStrip(vm: AppViewModel, items: List<NoteEntity>) {
    var askDelete by remember { mutableStateOf(false) }
    var showStyle by remember { mutableStateOf(false) }
    val chosen = vm.multi.orEmpty()
    val ordered = items.filter { it.id in chosen }.map { it.id }
    TopStrip {
        StripButton(Icons.Filled.Close, "Отменить выбор") { vm.endMulti() }
        StripButton(Icons.Filled.SelectAll, "Выбрать все") { vm.selectAllMulti(items.map { it.id }) }
        StripButton(Icons.Filled.DriveFileMove, "Переместить") { if (ordered.isEmpty()) vm.message = "Сначала выберите записи" else vm.startClipMulti(ordered, true) }
        StripButton(Icons.Filled.ContentCopy, "Копировать") { if (ordered.isEmpty()) vm.message = "Сначала выберите записи" else vm.startClipMulti(ordered, false) }
        StripButton(Icons.Filled.Palette, "Цвет и значок") { if (ordered.isEmpty()) vm.message = "Сначала выберите записи" else showStyle = true }
        StripButton(Icons.Filled.Delete, "Удалить") { if (ordered.isEmpty()) vm.message = "Сначала выберите записи" else askDelete = true }
    }
    if (askDelete) AlertDialog(
        onDismissRequest = { askDelete = false },
        title = { Text("Удалить выбранное?") },
        text = { Text("Записей: ${ordered.size}. Папки будут удалены вместе со всем содержимым.") },
        confirmButton = { TextButton(onClick = { askDelete = false; vm.deleteMulti() }) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = { askDelete = false }) { Text("Отмена") } },
    )
    if (showStyle) GroupStyleDialog(vm, onDismiss = { showStyle = false })
}

/** Строка со счётчиком выбранных записей. */
@Composable
fun MultiBar(vm: AppViewModel) {
    val m = vm.multi ?: return
    Text(
        if (m.isEmpty()) "Коснитесь записей, чтобы выбрать" else "Выбрано: ${m.size}",
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun GroupStyleDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var mode by remember { mutableStateOf(0) } // 0 — меню, 1 — цвет строки, 2 — цвет текста, 3 — цвет значка, 4 — значок
    when (mode) {
        1 -> ColorPickerDialog("Цвет строки", onDismiss = { mode = 0 }, onPick = { c -> vm.styleMulti { it.copy(colorArgb = c) }; onDismiss() })
        2 -> ColorPickerDialog("Цвет текста", onDismiss = { mode = 0 }, onPick = { c -> vm.styleMulti { it.copy(textColorArgb = c) }; onDismiss() })
        3 -> ColorPickerDialog("Цвет значка", onDismiss = { mode = 0 }, onPick = { c -> vm.styleMulti { it.copy(iconColorArgb = c) }; onDismiss() })
        4 -> IconPickDialog(onDismiss = { mode = 0 }, onPick = { key -> vm.styleMulti { it.copy(iconKey = key) }; onDismiss() })
        else -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Выбранные записи") },
            text = {
                Column {
                    listOf("Цвет строки…" to 1, "Цвет текста…" to 2, "Цвет значка…" to 3, "Значок…" to 4).forEach { (t, m) ->
                        Text(t, Modifier.fillMaxWidth().clickable { mode = m }.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                    Text(
                        "Сбросить оформление",
                        Modifier.fillMaxWidth().clickable {
                            vm.styleMulti { it.copy(colorArgb = null, textColorArgb = null, iconColorArgb = null, iconKey = null) }
                            onDismiss()
                        }.padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
        )
    }
}

@Composable
private fun IconPickDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    var search by remember { mutableStateOf("") }
    val all = remember { Mdi.names(ctx) }
    val list = remember(search, all) {
        val q = search.trim().lowercase()
        if (q.isEmpty()) MDI_POPULAR.filter { all.contains(it) } else all.filter { it.contains(q) }.take(300)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Значок") },
        text = {
            Column {
                TextField(value = search, onValueChange = { search = it }, placeholder = { Text("Поиск (англ.), например car") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(52.dp),
                    modifier = Modifier.padding(top = 8.dp).height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(list) { key ->
                        Box(Modifier.size(52.dp).clip(CircleShape).clickable { onPick(key) }, contentAlignment = Alignment.Center) {
                            MdiIcon(key, MaterialTheme.colorScheme.primary, 30.dp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick("") }) { Text("Без значка") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
