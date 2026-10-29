package ru.stasoft.mynotes.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Управление быстрыми вставками текущей папки (в корне — общие вставки для всех папок). */
@Composable
fun SnippetsDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val all by vm.allSnippets.collectAsState()
    val folderId by vm.folderId.collectAsState()
    val mine = all.filter { it.folderId == folderId }
    var editId by remember { mutableStateOf<Long?>(null) }
    var label by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    val where = vm.path.lastOrNull()?.title?.ifBlank { "эта папка" } ?: "общие, для всех папок"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Вставки: $where") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Кнопки над клавиатурой в редакторе. Вставки папки работают и во всех вложенных папках. " +
                        "В тексте можно использовать {сегодня}, {дата}, {время}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (mine.isEmpty()) {
                    Text("Здесь своих вставок пока нет.", modifier = Modifier.padding(vertical = 8.dp))
                }
                mine.forEach { s ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(
                            s.label,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TextButton(onClick = {
                            editId = s.id
                            label = s.label
                            text = s.text
                        }) { Text("Изменить") }
                        TextButton(onClick = {
                            vm.deleteSnippet(s)
                            if (editId == s.id) {
                                editId = null
                                label = ""
                                text = ""
                            }
                        }) { Text("Удалить") }
                    }
                    HorizontalDivider()
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Название кнопки") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Текст вставки") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                TextButton(
                    onClick = {
                        if (label.isNotBlank() && text.isNotEmpty()) {
                            vm.saveSnippet(editId, folderId, label.trim(), text)
                            editId = null
                            label = ""
                            text = ""
                        }
                    },
                ) { Text(if (editId == null) "Добавить вставку" else "Сохранить изменения") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}
