package ru.stasoft.mynotes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Раздел настроек: автоматическое создание папки дня и заметок из шаблонов. */
@Composable
fun AutoDaySettingsSection(vm: AppViewModel) {
    val snippets by vm.allSnippets.collectAsState()
    Text(
        "Папка дня",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
    )
    Row(
        Modifier.fillMaxWidth().clickable { vm.updateAutoDay(!vm.autoDay) }.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Создавать папку нового дня сама", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        AppSwitch(checked = vm.autoDay, onCheckedChange = { vm.updateAutoDay(it) })
    }
    Text(
        "Когда вы откроете приложение в новый день, рядом с последней папкой дня (в том же формате даты) появится папка с сегодняшним числом, а в ней заметки из выбранных ниже шаблонов.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    if (snippets.isNotEmpty()) {
        Text("Какие заметки создавать:", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
        snippets.forEach { s ->
            Row(
                Modifier.fillMaxWidth().clickable { vm.toggleAutoDaySnippet(s.label) }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppCheckbox(checked = s.label in vm.autoDaySnips, onCheckedChange = { vm.toggleAutoDaySnippet(s.label) })
                Text(s.label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    TextButton(onClick = { vm.ensureToday(manual = true) }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Создать папку сегодняшнего дня сейчас") }
}
