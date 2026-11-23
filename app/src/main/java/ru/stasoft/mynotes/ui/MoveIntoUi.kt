package ru.stasoft.mynotes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.stasoft.mynotes.data.NoteEntity
import ru.stasoft.mynotes.data.displayTitle

/**
 * «Поместить внутрь…»: выбор любой записи (папки или обычной заметки), в которую переносится [note].
 * Обычная заметка при этом сама становится папкой.
 */
@Composable
fun MoveIntoDialog(vm: AppViewModel, note: NoteEntity, onDismiss: () -> Unit) {
    var all by remember { mutableStateOf<List<NoteEntity>?>(null) }
    var q by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { all = vm.allNotesOnce() }
    val options = remember(all) {
        val list = all ?: return@remember emptyList()
        val by = list.associateBy { it.id }
        // сама запись и всё, что лежит внутри неё, в список не попадает
        val bad = HashSet<Long>().apply { add(note.id) }
        var grew = true
        while (grew) {
            grew = false
            for (n in list) if (n.id !in bad && n.parentId in bad) { bad.add(n.id); grew = true }
        }
        list.filter { it.id !in bad }.map { it to pathOf(it, by) }.sortedBy { it.second.lowercase() }
    }
    val shown = remember(q, options) { options.filter { q.isBlank() || it.second.contains(q, ignoreCase = true) }.take(80) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Поместить «${note.displayTitle().ifBlank { "Без названия" }}» внутрь…") },
        text = {
            Column {
                Text("Выберите папку или заметку. Заметка станет папкой, если в неё что-то помещено.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = q, onValueChange = { q = it }, label = { Text("Поиск") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                if (all == null) Text("Загрузка…", Modifier.padding(top = 8.dp))
                LazyColumn(Modifier.heightIn(max = 320.dp).padding(top = 8.dp)) {
                    if (q.isBlank()) item {
                        Text("Главный экран (корень)", Modifier.fillMaxWidth().clickable { vm.moveInto(listOf(note.id), null); onDismiss() }.padding(vertical = 10.dp))
                        HorizontalDivider()
                    }
                    items(shown) { (n, label) ->
                        Text(
                            (if (n.isFolder) "📁 " else "📝 ") + label,
                            Modifier.fillMaxWidth().clickable { vm.moveInto(listOf(note.id), n.id); onDismiss() }.padding(vertical = 10.dp),
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

internal fun pathOf(n: NoteEntity, by: Map<Long, NoteEntity>): String {
    val parts = ArrayList<String>()
    var c: NoteEntity? = n
    var guard = 0
    while (c != null && guard++ < 30) {
        parts.add(c.displayTitle().ifBlank { "Без названия" })
        c = c.parentId?.let { by[it] }
    }
    return parts.reversed().joinToString(" / ")
}

