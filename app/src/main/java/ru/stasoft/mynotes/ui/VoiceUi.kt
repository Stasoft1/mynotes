package ru.stasoft.mynotes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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

/** Окно проверки голосовой заметки: что услышано, куда и с каким содержимым будет создана. */
@Composable
fun VoiceDialog(vm: AppViewModel, onRetry: () -> Unit) {
    if (vm.voiceBusy) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Разбираю…") },
            text = { CircularProgressIndicator() },
            confirmButton = {},
        )
        return
    }
    val d = vm.voiceDraft ?: return
    var title by remember(d) { mutableStateOf(d.title) }
    var body by remember(d) { mutableStateOf(d.body) }
    var asList by remember(d) { mutableStateOf(d.checklist) }
    AlertDialog(
        onDismissRequest = { vm.voiceDraft = null },
        title = { Text("Голосовая заметка") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Папка: ${d.folderName}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Услышал: ${d.spoken}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                d.iconKey?.let { key ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        MdiIcon(key, d.iconColor?.let { androidx.compose.ui.graphics.Color(it) } ?: MaterialTheme.colorScheme.primary, 24.dp)
                        Text("  Иконка: $key", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (d.iconKey == null && d.iconColor != null) Text("Цвет иконки задан", style = MaterialTheme.typography.bodySmall)
                d.remindAt?.let {
                    val f = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it))
                    Text("Напоминание: $f", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                if (d.viaAi) Text("Разобрано ИИ (GigaChat)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(
                    value = title, onValueChange = { title = it }, label = { Text("Название") },
                    singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = body, onValueChange = { body = it }, label = { Text("Текст") },
                    minLines = 3, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Row(
                    Modifier.fillMaxWidth().clickable { asList = !asList },
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    AppCheckbox(checked = asList, onCheckedChange = { asList = it })
                    Text("Список задач (каждая строка — пункт)", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.voiceCommit(d, title, body, asList) }) { Text("Создать") } },
        dismissButton = {
            Row {
                TextButton(onClick = { vm.voiceDraft = null; onRetry() }) { Text("Заново") }
                if (vm.gigaActive && !d.viaAi) {
                    TextButton(onClick = { vm.voiceDraft = null; vm.voiceInput(d.spoken, forceAi = true) }) { Text("ИИ") }
                }
                TextButton(onClick = { vm.voiceDraft = null }) { Text("Отмена") }
            }
        },
    )
}

/** Раздел настроек: ключ GigaChat и доверие к сертификату Минцифры. */
@Composable
fun VoiceSettingsSection(vm: AppViewModel) {
    var editKey by remember { mutableStateOf(false) }
    var keyText by remember { mutableStateOf("") }
    if (!ru.stasoft.mynotes.modules.Modules.on("giga")) return
    Text(
        "Голосовой ввод: ИИ-разбор (GigaChat)",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
    )
    Text(
        "Без ключа фразы разбираются по правилам прямо на телефоне, в сеть ничего не уходит. " +
            "С ключом непонятные фразы отправляются в GigaChat (Сбер): только текст продиктованной фразы и названия ваших шаблонов.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        TextButton(onClick = { keyText = ""; editKey = true }) {
            Text(if (vm.gigaKey.isBlank()) "Ввести ключ…" else "Изменить ключ…")
        }
        if (vm.gigaKey.isNotBlank()) TextButton(onClick = { vm.gigaCheck() }) { Text("Проверить") }
    }
    if (vm.gigaKey.isNotBlank()) {
        Text(
            "Ключ сохранён.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        TextButton(onClick = { vm.updateGigaKey("") }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Удалить ключ") }
    }
    if (editKey) {
        AlertDialog(
            onDismissRequest = { editKey = false },
            title = { Text("Ключ GigaChat") },
            text = {
                Column {
                    Text("Вставьте «Ключ авторизации» из личного кабинета developers.sber.ru.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = keyText, onValueChange = { keyText = it }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { vm.updateGigaKey(keyText); editKey = false; if (keyText.isNotBlank()) vm.gigaCheck() }) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { editKey = false }) { Text("Отмена") } },
        )
    }
    vm.gigaTrustOffer?.let { o ->
        AlertDialog(
            onDismissRequest = { vm.gigaTrustOffer = null },
            title = { Text("Доверять сертификату?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Сервер GigaChat использует сертификат российского удостоверяющего центра (Минцифры), которого нет на телефоне. " +
                            "Если вы согласны, приложение будет доверять ему только при обращении к GigaChat.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text("Выдан: ${o.subject}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    Text("SHA-256: ${o.sha256}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
            },
            confirmButton = { TextButton(onClick = { vm.gigaTrust(o) }) { Text("Доверять") } },
            dismissButton = { TextButton(onClick = { vm.gigaTrustOffer = null }) { Text("Отмена") } },
        )
    }
}
