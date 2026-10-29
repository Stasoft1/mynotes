package ru.stasoft.mynotes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import ru.stasoft.mynotes.data.NoteEntity
import ru.stasoft.mynotes.data.displayTitle
import ru.stasoft.mynotes.reminders.ReminderLogic

val PERIOD_LABELS = listOf(
    "Не повторять",
    "Каждую неделю",
    "Каждый месяц",
    "Раз в 3 месяца",
    "Раз в полгода",
    "Раз в год",
)

// ------------------------------------------------------------------ список напоминаний

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderListScreen(vm: AppViewModel) {
    val items by vm.reminderItems.collectAsState()
    val now = System.currentTimeMillis()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Напоминания") },
                navigationIcon = { TextButton(onClick = { vm.showReminders = false }) { Text("←") } },
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            Text(
                "Напоминаний нет. Задать его можно в самой заметке.",
                modifier = Modifier.padding(padding).padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            items(items, key = { it.id }) { note ->
                val due = note.reminderAt ?: 0L
                val missed = ReminderLogic.daysUntil(due, now) < 0
                val st = reminderStatus(note)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.editNote(note) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(note.displayTitle().ifBlank { "Без названия" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (st != null) {
                        Text(
                            st.headline,
                            color = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(st.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row {
                        TextButton(onClick = { vm.acknowledge(note) }) { Text("Готово") }
                        TextButton(onClick = { vm.editNote(note) }) { Text("Открыть") }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

// ------------------------------------------------------------------ строка и диалог в редакторе

@Composable
fun ReminderRow(note: NoteEntity, onEdit: () -> Unit) {
    val st = reminderStatus(note)
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            if (st == null) {
                Text("Напоминание не задано", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    "${st.headline} · ${st.date}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (st.missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                val period = PERIOD_LABELS.getOrNull(note.reminderPeriod)
                if (period != null && note.reminderPeriod > 0) {
                    Text(period, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        TextButton(onClick = onEdit) { Text(if (st == null) "Задать" else "Изменить") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDialog(
    note: NoteEntity,
    onDismiss: () -> Unit,
    onSave: (dueAt: Long, period: Int, leadDays: Int) -> Unit,
    onClear: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val existing = note.reminderAt?.let { Instant.ofEpochMilli(it).atZone(zone) }
    var date by remember { mutableStateOf(existing?.toLocalDate() ?: LocalDate.now(zone).plusDays(1)) }
    var time by remember {
        mutableStateOf(
            existing?.toLocalTime()?.takeIf { it != LocalTime.MIDNIGHT } ?: LocalTime.of(9, 0),
        )
    }
    var period by remember { mutableStateOf(note.reminderPeriod.coerceIn(0, 5)) }
    var lead by remember { mutableStateOf(if (note.reminderCheckDays > 0) note.reminderCheckDays.toString() else "") }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var periodMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Напоминание") },
        text = {
            Column {
                Row {
                    TextButton(onClick = { showDate = true }) { Text("Дата: ${date.dayOfMonth}.${"%02d".format(date.monthValue)}.${date.year}") }
                    TextButton(onClick = { showTime = true }) { Text("Время: ${"%02d:%02d".format(time.hour, time.minute)}") }
                }
                Box {
                    TextButton(onClick = { periodMenu = true }) { Text("Повтор: ${PERIOD_LABELS[period]}") }
                    DropdownMenu(expanded = periodMenu, onDismissRequest = { periodMenu = false }) {
                        PERIOD_LABELS.forEachIndexed { i, label ->
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                period = i
                                periodMenu = false
                            })
                        }
                    }
                }
                OutlinedTextField(
                    value = lead,
                    onValueChange = { v -> lead = v.filter { it.isDigit() }.take(3) },
                    label = { Text("Напомнить заранее, дней") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val ms = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
                onSave(ms, period, lead.toIntOrNull() ?: 0)
            }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (note.reminderAt != null) TextButton(onClick = onClear) { Text("Убрать") }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )

    if (showDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Отмена") } },
        ) { DatePicker(state = state) }
    }
    if (showTime) {
        val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    time = LocalTime.of(state.hour, state.minute)
                    showTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Отмена") } },
        )
    }
}
