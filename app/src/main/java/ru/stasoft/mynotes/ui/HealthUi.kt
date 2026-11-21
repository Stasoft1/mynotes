package ru.stasoft.mynotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import ru.stasoft.mynotes.data.Habit
import ru.stasoft.mynotes.data.NoteEntity
import ru.stasoft.mynotes.data.Measure
import ru.stasoft.mynotes.data.fmtDate
import ru.stasoft.mynotes.data.parseHabits
import ru.stasoft.mynotes.data.parseMetrics
import ru.stasoft.mynotes.data.parseMeasures
import java.time.LocalDate

private fun num(v: Double): String =
    if (v == Math.rint(v)) v.toLong().toString() else String.format(java.util.Locale("ru"), "%.1f", v)

private fun valueText(r: Measure) = num(r.v) + (r.v2?.let { "/" + num(it) } ?: "")

/** Показатели здоровья: динамика, минимум, среднее и максимум за период, график, быстрый ввод замера. */
@Composable
fun HealthDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val metrics = remember(vm.healthText) { parseMetrics(vm.healthText) }
    var notes by remember { mutableStateOf<List<NoteEntity>?>(null) }
    var metric by remember { mutableStateOf(metrics.firstOrNull()?.name) }
    var days by remember { mutableStateOf(90) }
    var adding by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(reload) { notes = vm.allNotesOnce() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                    Text("Показатели", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { adding = true }) { Text("+ Замер") }
                }
                val all = notes
                val m = metric
                if (all == null) Text("Считаю…", Modifier.padding(16.dp))
                else if (metrics.isEmpty() || m == null) Text("Добавьте показатели в Настройках, раздел «Показатели».", Modifier.padding(16.dp))
                else Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        metrics.forEach { FilterChip(selected = it.name == m, onClick = { metric = it.name }, label = { Text(it.name) }) }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp, 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(30 to "30 дней", 90 to "90 дней", 365 to "Год", 100000 to "Всё").forEach { (d, t) ->
                            FilterChip(selected = days == d, onClick = { days = d }, label = { Text(t) })
                        }
                    }
                    val unit = metrics.firstOrNull { it.name == m }?.unit.orEmpty()
                    val from = LocalDate.now().minusDays(days.toLong())
                    val rs = remember(all, m) { parseMeasures(all, m) }
                    val inPeriod = rs.filter { !it.date.isBefore(from) }
                    if (inPeriod.isEmpty()) Text(
                        "Нет замеров за период. Нажмите «+ Замер» или напишите в заметке строку «$m: значение» (при необходимости «Дата: дд.мм.гггг»).",
                        Modifier.padding(16.dp),
                    ) else {
                        val last = inPeriod.last()
                        val first = inPeriod.first()
                        Column(Modifier.padding(16.dp)) {
                            Text("Последнее: " + valueText(last) + " " + unit, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(fmtDate(last.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (inPeriod.size > 1) {
                                val d = last.v - first.v
                                Text(
                                    "За период: " + (if (d > 0) "+" else "") + num(d) + " " + unit + " (с " + fmtDate(first.date) + ")",
                                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            val vs = inPeriod.map { it.v }
                            Text(
                                "Мин " + num(vs.min()) + " · среднее " + num(vs.average()) + " · макс " + num(vs.max()) + "  (замеров: ${vs.size})",
                                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        if (inPeriod.size > 1) {
                            Text(
                                if (last.v2 != null) "Верхнее значение" else "График",
                                style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(16.dp, 4.dp, 16.dp, 0.dp),
                            )
                            Column(Modifier.padding(horizontal = 16.dp)) { LineChart(inPeriod.map { it.v }) }
                        }
                        HorizontalDivider(Modifier.padding(16.dp, 12.dp))
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Замеры", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                fun n(v: Double) = String.format(java.util.Locale.US, "%.2f", v).replace('.', ',')
                                CsvExport.share(
                                    ctx, m,
                                    listOf(listOf("Дата", m, if (last.v2 != null) "Нижнее" else "")) +
                                        inPeriod.map { listOf(fmtDate(it.date), n(it.v), it.v2?.let(::n) ?: "") },
                                )
                            }) { Text("CSV") }
                        }
                        inPeriod.asReversed().take(40).forEach {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
                                Text(fmtDate(it.date), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text(valueText(it) + " " + unit, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
    if (adding) {
        var mname by remember { mutableStateOf(metric ?: metrics.firstOrNull()?.name.orEmpty()) }
        var value by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Новый замер") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        metrics.forEach { FilterChip(selected = it.name == mname, onClick = { mname = it.name }, label = { Text(it.name) }) }
                    }
                    OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("Значение, например 82,5 или 120/80") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = value.isNotBlank() && mname.isNotBlank(), onClick = {
                    val today = fmtDate(LocalDate.now())
                    scope.launch {
                        vm.addNoteIn("Показатели", "$mname $today", "$mname: ${value.trim()}\nДата: $today")
                        metric = mname
                        reload++
                        adding = false
                    }
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Отмена") } },
        )
    }
}

/** Привычки: отметки по дням, серия подряд, процент выполнения. */
@Composable
fun HabitsDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var notes by remember { mutableStateOf<List<NoteEntity>?>(null) }
    var reload by remember { mutableStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(reload) { notes = vm.allNotesOnce() }
    val today = LocalDate.now()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                    Text("Привычки", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { adding = true }) { Text("+ Привычка") }
                }
                val habits = notes?.let { parseHabits(it) }
                if (habits == null) Text("Считаю…", Modifier.padding(16.dp))
                else if (habits.isEmpty()) Text(
                    "Привычек пока нет. Нажмите «+ Привычка», например «Зарядка» или «Вода». Каждая привычка — обычная заметка с полем «Привычка: …» и датами отметок, её можно искать и копировать как любую другую.",
                    Modifier.padding(16.dp),
                ) else Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                    habits.forEach { h: Habit ->
                        Column(Modifier.fillMaxWidth().padding(16.dp, 10.dp)) {
                            Text(h.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Серия: ${h.currentStreak(today)} дн. · лучшая: ${h.bestStreak()} · за 30 дней: ${h.share(today, 30)}%",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                (6 downTo 0).forEach { back ->
                                    val d = today.minusDays(back.toLong())
                                    val done = d in h.days
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            Modifier.size(38.dp).clip(CircleShape)
                                                .background(if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
                                                .clickable { scope.launch { vm.toggleHabit(h.noteId, d); reload++ } },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                "${d.dayOfMonth}",
                                                color = if (done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                style = MaterialTheme.typography.labelLarge,
                                            )
                                        }
                                        Text(
                                            listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")[d.dayOfWeek.value - 1],
                                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    }
                    Text(
                        "Коснитесь кружка, чтобы отметить или снять отметку за день.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
    if (adding) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Новая привычка") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название") }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    scope.launch {
                        vm.addNoteIn("Привычки", name.trim(), "Привычка: ${name.trim()}\n")
                        reload++
                        adding = false
                    }
                }) { Text("Добавить") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Отмена") } },
        )
    }
}

/** Настройки модуля «Показатели здоровья»: список показателей с единицами. */
@Composable
fun HealthSettingsSection(vm: AppViewModel) {
    Text(
        "По строке на показатель, единицы через «=»: Вес = кг. Замеры берутся из заметок со строкой «Вес: 82,5»; давление пишется как «Давление: 120/80». Дата — из поля «Дата:», иначе день создания заметки.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    OutlinedTextField(
        value = vm.healthText,
        onValueChange = { vm.updateHealthText(it) },
        label = { Text("Показатели") },
        minLines = 5,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
}
