package ru.stasoft.mynotes.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

/** «Заметка по фото»: снять или выбрать снимок, дальше его разбирает [AppViewModel.photoInput]. Возвращает функцию «открыть выбор». */
@Composable
fun rememberPhotoNote(vm: AppViewModel): () -> Unit {
    val ctx = LocalContext.current
    var ask by remember { mutableStateOf(false) }
    var camUri by remember { mutableStateOf<Uri?>(null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.photoInput(uri) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = camUri
        if (ok && u != null) vm.photoInput(u)
    }
    if (ask) AlertDialog(
        onDismissRequest = { ask = false },
        title = { Text("Заметка по фото") },
        text = { Text("Если на снимке QR-код кассового чека, сумма и дата прочитаются на телефоне. Магазин, товары и обычный текст читает GigaChat (фото уходит в облако Сбера и сразу удаляется). Снимок можно прикрепить к заметке.") },
        confirmButton = {
            TextButton(onClick = {
                ask = false
                val dir = File(ctx.cacheDir, "cam").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", File(dir, "shot_${System.currentTimeMillis()}.jpg"))
                camUri = u
                try { camera.launch(u) } catch (_: Exception) { vm.message = "Камера недоступна" }
            }) { Text("Снять") }
        },
        dismissButton = {
            TextButton(onClick = {
                ask = false
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Text("Из галереи") }
        },
    )
    return { ask = true }
}

/** Окно «Заметка по фото»: что распознано, куда сохранить, в какой шаблон подставить. */
@Composable
fun PhotoNoteDialog(vm: AppViewModel) {
    val d = vm.photoDraft ?: return
    val snippets by vm.allSnippets.collectAsState()
    var all by remember { mutableStateOf<List<ru.stasoft.mynotes.data.NoteEntity>>(emptyList()) }
    androidx.compose.runtime.LaunchedEffect(Unit) { all = vm.allNotesOnce() }
    val by = remember(all) { all.associateBy { it.id } }
    val (f0, day0) = remember(d) { vm.photoFolderChoice() }
    var folder by remember(d) { mutableStateOf(f0) }
    var day by remember(d) { mutableStateOf(day0) }
    var snippet by remember(d) { mutableStateOf(vm.defaultReceiptSnippet()) }
    var title by remember(d) { mutableStateOf("") }
    var body by remember(d) { mutableStateOf(if (d.receipt) "" else d.text) }
    var attach by remember(d) { mutableStateOf(true) }
    var pick by remember(d) { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(d, snippet, snippets) { if (d.receipt) body = vm.photoBody(d, snippet) }
    val folderLabel = when {
        folder == QUICK_CURRENT -> "Текущая папка"
        folder == QUICK_ROOT -> "Главный экран"
        else -> by[folder]?.let { pathOf(it, by) } ?: "Папка не найдена"
    }
    val snippetLabel = if (snippet < 0) "Простой формат" else snippets.firstOrNull { it.id == snippet }?.label ?: "Простой формат"
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { vm.photoDraft = null },
        title = { Text(if (d.receipt) "Чек" else "Текст с фото") },
        text = {
            androidx.compose.foundation.layout.Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Распознано: " + d.source, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().clickable { pick = 1 }.padding(top = 8.dp)) {
                    Text("Куда сохранить", style = androidx.compose.material3.MaterialTheme.typography.labelMedium, color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                    Text("📁 $folderLabel" + if (day) " → папка дня" else "")
                }
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().clickable { day = !day }, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = day, onCheckedChange = { day = it })
                    Text("Папка дня (найти или создать для сегодня)", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
                if (d.receipt) androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().clickable { pick = 2 }.padding(bottom = 4.dp)) {
                    Text("Шаблон", style = androidx.compose.material3.MaterialTheme.typography.labelMedium, color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                    Text("📝 $snippetLabel")
                }
                androidx.compose.material3.OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Название (можно пустое)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                androidx.compose.material3.OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Текст") }, minLines = 4, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().clickable { attach = !attach }, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = attach, onCheckedChange = { attach = it })
                    Text("Прикрепить снимок к заметке", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.photoCommit(d, title, body, folder, day, snippet, attach) }) { Text("Создать") } },
        dismissButton = { TextButton(onClick = { vm.photoDraft = null }) { Text("Отмена") } },
    )
    if (pick == 1) FolderTreeDialog(all, folder, onDismiss = { pick = 0 }) { folder = it; pick = 0 }
    if (pick == 2) PickDialog("Шаблон для чека", listOf(-1L to "Простой формат") + snippets.map { it.id to it.label }, withSearch = false, onDismiss = { pick = 0 }) { snippet = it; pick = 0 }
}

