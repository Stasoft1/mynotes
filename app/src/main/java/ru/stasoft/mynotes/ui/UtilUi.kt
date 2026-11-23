package ru.stasoft.mynotes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import ru.stasoft.mynotes.data.NoteEntity
import ru.stasoft.mynotes.data.fmtDate
import ru.stasoft.mynotes.data.formatNumber
import ru.stasoft.mynotes.data.parseUtilMeters
import ru.stasoft.mynotes.data.utilMonths
import ru.stasoft.mynotes.data.utilUses
import java.time.LocalDate

private fun rub(v: Double) = formatNumber(Math.round(v).toDouble()) + " ₽"

/** Коммунальные услуги: последние показания, расход и сумма по месяцам, быстрый ввод показаний. */
@Composable
fun UtilDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val meters = remember(vm.utilText) { parseUtilMeters(vm.utilText) }
    var notes by remember { mutableStateOf<List<NoteEntity>?>(null) }
    var reload by remember { mutableStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(reload) { notes = vm.allNotesOnce() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                    Text("Коммунальные услуги", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { adding = true }) { Text("+ Показания") }
                }
                val all = notes
                if (all == null) Text("Считаю…", Modifier.padding(16.dp))
                else if (meters.isEmpty()) Text("Добавьте счётчики в Настройках, раздел «Коммунальные услуги».", Modifier.padding(16.dp))
                else {
                    val months = remember(all, meters, vm.utilFixed) { utilMonths(all, meters, vm.utilFixed) }
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                        Text("Последние показания", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 4.dp))
                        meters.forEach { m ->
                            val uses = utilUses(all, m)
                            val last = uses.lastOrNull()
                            Column(Modifier.fillMaxWidth().padding(16.dp, 4.dp)) {
                                if (last == null) Text(m.name + ": показаний нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                else {
                                    Text(m.name + ": " + formatNumber(last.reading) + " " + m.unit, fontWeight = FontWeight.Bold)
                                    Text(
                                        fmtDate(last.date) + if (uses.size > 1) " · с прошлого раза " + formatNumber(last.used) + " " + m.unit + (if (m.tariff > 0) " ≈ " + rub(last.cost) else "") else "",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        HorizontalDivider(Modifier.padding(16.dp, 12.dp))
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("По месяцам", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            if (months.isNotEmpty()) TextButton(onClick = {
                                CsvExport.share(
                                    ctx, "Коммунальные",
                                    listOf(listOf("Месяц", "Счётчик", "Расход", "Сумма")) +
                                        months.flatMap { mo -> mo.perMeter.map { (m, u) -> listOf("%02d.%d".format(mo.month.monthValue, mo.month.year), m.name, formatNumber(u.used), formatNumber(Math.round(u.cost).toDouble())) } },
                                )
                            }) { Text("CSV") }
                        }
                        if (months.isEmpty()) Text(
                            "Нужно минимум два показания одного счётчика. Нажмите «+ Показания» или напишите в заметке строку «Электричество: 12345» (дата — из поля «Дата:», иначе день создания заметки).",
                            Modifier.padding(16.dp),
                        )
                        val loc = java.util.Locale("ru")
                        months.reversed().take(24).forEachIndexed { idx, mo ->
                            val prev = months.getOrNull(months.size - 2 - idx)
                            Column(Modifier.fillMaxWidth().padding(16.dp, 6.dp)) {
                                Row {
                                    Text(
                                        mo.month.month.getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, loc).replaceFirstChar { it.uppercase() } + " " + mo.month.year,
                                        Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(rub(mo.total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                mo.perMeter.forEach { (m, u) ->
                                    Text(
                                        m.name + ": " + formatNumber(u.used) + " " + m.unit + (if (m.tariff > 0) " · " + rub(u.cost) else ""),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (vm.utilFixed > 0) Text("Постоянные платежи: " + rub(vm.utilFixed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (prev != null && prev.total > 0) {
                                    val d = Math.round((mo.total - prev.total) * 100 / prev.total)
                                    Text("К прошлому месяцу: " + (if (d > 0) "+" else "") + d + "%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (adding) {
        val vals = remember { mutableStateMapOf<String, String>() }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Показания счётчиков") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    val all = notes.orEmpty()
                    meters.forEach { m ->
                        val last = utilUses(all, m).lastOrNull()
                        OutlinedTextField(
                            value = vals[m.name].orEmpty(),
                            onValueChange = { vals[m.name] = it },
                            label = { Text(m.name + ", " + m.unit) },
                            supportingText = { if (last != null) Text("прошлый раз: " + formatNumber(last.reading)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                    Text("Пустые поля пропускаются.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(enabled = vals.values.any { it.isNotBlank() }, onClick = {
                    val today = fmtDate(LocalDate.now())
                    val body = meters.filter { !vals[it.name].isNullOrBlank() }.joinToString("\n") { it.name + ": " + vals[it.name]!!.trim() } + "\nДата: $today"
                    scope.launch {
                        vm.addNoteIn("Коммунальные", "Показания $today", body)
                        reload++
                        adding = false
                    }
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Отмена") } },
        )
    }
}

@Composable
fun UtilSettingsSection(vm: AppViewModel) {
    Text(
        "По строке на счётчик: Название = единицы = тариф за единицу, например «Электричество = кВт·ч = 6,5». Показания берутся из заметок со строкой «Электричество: 12345» (можно несколько счётчиков в одной заметке). Тариф можно оставить пустым — тогда считается только расход.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    OutlinedTextField(
        value = vm.utilText,
        onValueChange = { vm.updateUtilText(it) },
        label = { Text("Счётчики и тарифы") },
        minLines = 4,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
    var fixed by remember { mutableStateOf(if (vm.utilFixed > 0) formatNumber(vm.utilFixed) else "") }
    OutlinedTextField(
        value = fixed,
        onValueChange = { fixed = it; vm.updateUtilFixed(ru.stasoft.mynotes.data.leadingNumber(it) ?: 0.0) },
        label = { Text("Постоянные платежи в месяц, ₽ (содержание жилья, отопление…)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 0.dp),
    )
    var day by remember { mutableStateOf(if (vm.utilDay > 0) vm.utilDay.toString() else "") }
    OutlinedTextField(
        value = day,
        onValueChange = { s -> day = s.filter { it.isDigit() }.take(2); vm.updateUtilDay(day.toIntOrNull() ?: 0) },
        label = { Text("Напомнить передать показания до числа (пусто — не напоминать)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 0.dp),
    )
}
