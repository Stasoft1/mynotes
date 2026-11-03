package ru.stasoft.mynotes.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.stasoft.mynotes.data.SnippetEntity

/**
 * Шаблоны заметок: название, заголовок будущей заметки и текст с оформлением. Правятся в обычном редакторе.
 * В корне создаются общие шаблоны для всех папок; в папке — свои, а общие и шаблоны папок выше по дереву тоже
 * доступны (наследуются) и показаны здесь.
 */
@Composable
fun SnippetsDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val all by vm.allSnippets.collectAsState()
    val pathIds = vm.path.map { it.id }
    val titleById = vm.path.associate { it.id to it.title.ifBlank { "папка" } }
    val visible = all
        .filter { sn -> sn.folderId == null || pathIds.contains(sn.folderId) }
        .sortedWith(compareBy<SnippetEntity>({ sn -> sn.folderId?.let { pathIds.indexOf(it) } ?: -1 }, { it.sortIndex }, { it.id }))
    // null — окно имени закрыто; пара (шаблон или null для нового, текст имени).
    var nameDialog by remember { mutableStateOf<Pair<SnippetEntity?, String>?>(null) }
    val where = vm.path.lastOrNull()?.title?.ifBlank { "эта папка" } ?: "общие, для всех папок"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Шаблоны: $where") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Нажмите на шаблон, чтобы создать из него заметку здесь. Карандаш открывает шаблон в обычном " +
                        "редакторе: название, текст и оформление. В тексте работают {переменные}, {сегодня}, {дата}, {время}. " +
                        "В заметке шаблон вставляется кнопкой в верхней панели.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (visible.isEmpty()) {
                    Text("Шаблонов пока нет.", modifier = Modifier.padding(vertical = 8.dp))
                }
                visible.forEach { s ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                onDismiss()
                                vm.newFromSnippet(s)
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(s.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                                val scope = if (s.folderId == null) "общий" else "из «${titleById[s.folderId] ?: "папки"}»"
                                val head = s.title.ifBlank { s.text.lineSequence().firstOrNull().orEmpty() }
                                Text(
                                    (if (head.isBlank()) scope else "$scope · $head"),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(onClick = { nameDialog = s to s.label }) { Icon(Icons.Filled.DriveFileRenameOutline, "Название") }
                        IconButton(onClick = {
                            onDismiss()
                            vm.editSnippet(s)
                        }) { Icon(Icons.Filled.Edit, "Править") }
                        IconButton(onClick = { vm.deleteSnippet(s) }) { Icon(Icons.Filled.Delete, "Удалить") }
                    }
                    HorizontalDivider()
                }
                TextButton(onClick = { nameDialog = null to "" }) { Text("+ Новый шаблон") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )

    nameDialog?.let { (sn, text) ->
        var value by remember(sn?.id) { mutableStateOf(text) }
        AlertDialog(
            onDismissRequest = { nameDialog = null },
            title = { Text(if (sn == null) "Новый шаблон" else "Название шаблона") },
            text = {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Название в списке") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = value.isNotBlank(),
                    onClick = {
                        nameDialog = null
                        if (sn == null) {
                            onDismiss()
                            vm.newSnippet(value.trim())
                        } else {
                            vm.renameSnippet(sn, value.trim())
                        }
                    },
                ) { Text(if (sn == null) "Создать" else "Сохранить") }
            },
            dismissButton = { TextButton(onClick = { nameDialog = null }) { Text("Отмена") } },
        )
    }
}
