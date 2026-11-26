package ru.stasoft.mynotes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
        "Когда вы откроете приложение в новый день, в выбранной папке (или рядом с последней папкой дня) появится папка с сегодняшним числом, в том же формате даты, а в ней заметки из выбранных ниже шаблонов.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    var all by remember { mutableStateOf<List<ru.stasoft.mynotes.data.NoteEntity>>(emptyList()) }
    var pickRoot by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(vm.autoDayRoot, pickRoot) { all = vm.allNotesOnce() }
    val byId = remember(all) { all.associateBy { it.id } }
    Column(Modifier.fillMaxWidth().clickable { pickRoot = true }.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Где создавать", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text("📁 " + (byId[vm.autoDayRoot]?.let { pathOf(it, byId) } ?: "Вся база: по уже имеющимся папкам дней"))
        Text(
            "Выберите папку, внутри которой лежат папки дней (например «Работа»). Недостающие год и месяц создаются сами: Работа / 2026 / Ноябрь / 26.11.26.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (pickRoot) FolderTreeDialog(
        all, vm.autoDayRoot, onDismiss = { pickRoot = false },
        specials = listOf(-1L to "Вся база (по уже имеющимся папкам дней)"), title = "Где создавать папки дней",
    ) { vm.saveAutoDayRoot(it); pickRoot = false }
    Text("В какие дни создавать:", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Каждый день", "По дням недели", "График «через»").forEachIndexed { i, l ->
            FilterChip(selected = vm.dayMode == i, onClick = { vm.saveDaySchedule(mode = i) }, label = { Text(l) })
        }
    }
    if (vm.dayMode == 1) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEachIndexed { i, l ->
            FilterChip(selected = (vm.dayMask shr i) and 1 == 1, onClick = { vm.saveDaySchedule(mask = vm.dayMask xor (1 shl i)) }, label = { Text(l) })
        }
    }
    if (vm.dayMode == 2) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            var w by remember(vm.dayWork) { mutableStateOf(vm.dayWork.toString()) }
            var o by remember(vm.dayOff) { mutableStateOf(vm.dayOff.toString()) }
            val kb = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            OutlinedTextField(value = w, onValueChange = { w = it.filter(Char::isDigit).take(2); w.toIntOrNull()?.let { n -> if (n > 0) vm.saveDaySchedule(work = n) } }, label = { Text("Рабочих") }, singleLine = true, keyboardOptions = kb, modifier = Modifier.weight(1f))
            Text("через")
            OutlinedTextField(value = o, onValueChange = { o = it.filter(Char::isDigit).take(2); o.toIntOrNull()?.let { n -> if (n > 0) vm.saveDaySchedule(off = n) } }, label = { Text("Выходных") }, singleLine = true, keyboardOptions = kb, modifier = Modifier.weight(1f))
        }
        val anchor = java.time.LocalDate.ofEpochDay(vm.dayAnchor)
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Первый рабочий день: %02d.%02d.%d".format(anchor.dayOfMonth, anchor.monthValue, anchor.year), Modifier.padding(start = 8.dp).weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { vm.saveDaySchedule(anchor = java.time.LocalDate.now().toEpochDay()) }) { Text("Сегодня") }
        }
        Text(
            "Начало графика — первый из рабочих дней цикла. Если сегодня первый рабочий день, нажмите «Сегодня».",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
    val next = (0..60).map { java.time.LocalDate.now().plusDays(it.toLong()) }.filter { vm.isDayScheduled(it) }.take(6)
    Text(
        "Ближайшие дни: " + next.joinToString(", ") { "%02d.%02d".format(it.dayOfMonth, it.monthValue) },
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 4.dp),
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
