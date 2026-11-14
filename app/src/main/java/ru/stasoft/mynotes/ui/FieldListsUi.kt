package ru.stasoft.mynotes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Раздел настроек «Подсказки значений»: когда в заметке набираешь «Поле:», над клавиатурой появляются значения из списка. */
@Composable
fun FieldListsSection(vm: AppViewModel) {
    // null — окно закрыто; Pair(старое имя или null, …) — правка существующего или новый список.
    var edit by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    Text(
        "Подсказки значений",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
    )
    Text(
        "Когда в заметке набираете «Магазин:», над клавиатурой появятся значения из списка: нажмите, и оно подставится.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    vm.fieldLists.forEach { (k, v) ->
        Column(Modifier.fillMaxWidth().clickable { edit = k }.padding(horizontal = 16.dp, vertical = 6.dp)) {
            Text(k, style = MaterialTheme.typography.bodyLarge)
            Text(v.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    TextButton(onClick = { adding = true }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Добавить список…") }
    if (adding || edit != null) {
        val old = edit
        var name by remember(old) { mutableStateOf(old ?: "") }
        var values by remember(old) { mutableStateOf(old?.let { vm.fieldLists[it]?.joinToString("\n") } ?: "") }
        val close = { adding = false; edit = null }
        AlertDialog(
            onDismissRequest = close,
            title = { Text(if (old == null) "Новый список" else "Список значений") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Поле (например, Магазин)") }, singleLine = true)
                    OutlinedTextField(
                        value = values, onValueChange = { values = it }, label = { Text("Значения, по одному в строке") },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.saveFieldList(name, values.split("\n", ","), old); close() }) { Text("Сохранить") }
            },
            dismissButton = {
                Row {
                    if (old != null) TextButton(onClick = { vm.deleteFieldList(old); close() }) { Text("Удалить") }
                    TextButton(onClick = close) { Text("Отмена") }
                }
            },
        )
    }
}
