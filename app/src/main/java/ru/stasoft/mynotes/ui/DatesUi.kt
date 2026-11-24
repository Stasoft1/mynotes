package ru.stasoft.mynotes.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import ru.stasoft.mynotes.data.DueItem
import ru.stasoft.mynotes.data.daysText
import ru.stasoft.mynotes.data.fmtDate
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private fun rub(v: Double) = String.format(java.util.Locale("ru"), "%,.0f", v).replace(' ', ' ') + " ₽"

private val TITLES = mapOf("soon" to "Ближайшее", "subs" to "Подписки", "bday" to "Даты", "warr" to "Гарантии", "debt" to "Долги")

/** Сроки и даты: ближайшие события, подписки, даты, гарантии и долги; в одном окне по вкладкам включённых модулей. */
@Composable
fun DatesDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var items by remember { mutableStateOf<List<DueItem>?>(null) }
    var reload by remember { mutableStateOf(0) }
    var tab by remember { mutableStateOf("soon") }
    var adding by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    LaunchedEffect(reload) { items = vm.dueItems() }
    val kinds = listOf("subs", "bday", "warr", "debt").filter { vm.dateKindsOn().contains(it) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                    Text("Сроки и даты", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    HelpButton(if (tab in kinds) tab else kinds.firstOrNull() ?: "subs")
                    if (tab != "soon") TextButton(onClick = { adding = tab }) { Text("+ Добавить") }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (listOf("soon") + kinds).forEach { k -> FilterChip(selected = tab == k, onClick = { tab = k }, label = { Text(TITLES[k] ?: k) }) }
                }
                val all = items
                if (all == null) Text("Считаю…", Modifier.padding(16.dp))
                else Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                    when (tab) {
                        "soon" -> {
                            val soon = all.filter { it.kind != "debt" || it.next != null }
                                .filter { it.next != null && ChronoUnit.DAYS.between(today, it.next).let { d -> d in -7..30 } }
                                .sortedBy { it.next }
                            if (soon.isEmpty()) Text("В ближайшие 30 дней событий нет. Добавьте их на вкладках или напишите поля в заметках; формат описан в руководстве.", Modifier.padding(16.dp))
                            soon.forEach { Row0(it, today, true) }
                        }
                        "subs" -> {
                            val l = all.filter { it.kind == "subs" }.sortedBy { it.next ?: LocalDate.MAX }
                            val month = l.sumOf { (it.amount ?: 0.0) * it.perYear / 12 }
                            if (l.isNotEmpty()) Text("В месяц: ${rub(month)} · в год: ${rub(month * 12)}", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                            if (l.isEmpty()) Text("Подписок нет. Заметка: «Подписка: Название», «Сумма: 599», «Дата: 15.12.2026» (ближайшее списание), «Период: месяц / год / неделя».", Modifier.padding(16.dp))
                            l.forEach { Row0(it, today, false) }
                        }
                        "bday" -> {
                            val l = all.filter { it.kind == "bday" }.sortedBy { it.next }
                            if (l.isEmpty()) Text("Дат нет. Заметка: «ДР: Мама» и «Дата: 12.05.1965» (год необязателен). Для других событий — «Событие: …».", Modifier.padding(16.dp))
                            l.forEach { Row0(it, today, false) }
                        }
                        "warr" -> {
                            val l = all.filter { it.kind == "warr" }.sortedBy { it.next }
                            if (l.isEmpty()) Text("Гарантий нет. Заметка: «Гарантия: Холодильник» и «До: 30.06.2028».", Modifier.padding(16.dp))
                            l.forEach { Row0(it, today, false) }
                        }
                        "debt" -> {
                            val l = all.filter { it.kind == "debt" }
                            val owedToMe = l.filter { (it.amount ?: 0.0) > 0 }.sumOf { it.amount ?: 0.0 }
                            val iOwe = l.filter { (it.amount ?: 0.0) < 0 }.sumOf { -(it.amount ?: 0.0) }
                            if (l.isNotEmpty()) Column(Modifier.padding(16.dp)) {
                                Text("Мне должны: ${rub(owedToMe)}", style = MaterialTheme.typography.titleMedium)
                                Text("Я должен: ${rub(iOwe)}", style = MaterialTheme.typography.titleMedium)
                            }
                            if (l.isEmpty()) Text("Долгов нет. Заметка: «Долг: Иван» (должен вам) или «Я должен: Иван», «Сумма: 5000», «Вернуть до: 01.12.2026» (необязательно).", Modifier.padding(16.dp))
                            l.groupBy { it.name.lowercase() }.values.sortedByDescending { g -> Math.abs(g.sumOf { it.amount ?: 0.0 }) }.forEach { g ->
                                val bal = g.sumOf { it.amount ?: 0.0 }
                                Column(Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
                                    Row {
                                        Text(g.first().name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                        Text((if (bal >= 0) "должен вам " else "вы должны ") + rub(Math.abs(bal)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    }
                                    g.forEach {
                                        val late = it.next != null && it.next.isBefore(today)
                                        Text(
                                            rub(Math.abs(it.amount ?: 0.0)) + (if ((it.amount ?: 0.0) < 0) " (я должен)" else "") + (it.next?.let { d -> " · до " + fmtDate(d) + (if (late) " — просрочено" else "") } ?: ""),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    adding?.let { kind ->
        var name by remember { mutableStateOf("") }
        var date by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        var period by remember { mutableStateOf("месяц") }
        var mine by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { adding = null },
            title = { Text("Новая запись: " + (TITLES[kind] ?: "")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(if (kind == "debt") "Кто" else "Название") }, singleLine = true)
                    OutlinedTextField(
                        value = date, onValueChange = { date = it }, singleLine = true,
                        label = { Text(when (kind) { "subs" -> "Ближайшее списание (дд.мм.гггг)"; "bday" -> "Дата (дд.мм или дд.мм.гггг)"; "warr" -> "Гарантия до (дд.мм.гггг)"; else -> "Вернуть до (необязательно)" }) },
                    )
                    if (kind == "subs" || kind == "debt") OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Сумма") }, singleLine = true)
                    if (kind == "subs") Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("неделя", "месяц", "квартал", "год").forEach { p -> FilterChip(selected = period == p, onClick = { period = p }, label = { Text(p) }) }
                    }
                    if (kind == "debt") Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = !mine, onClick = { mine = false }, label = { Text("Мне должны") })
                        FilterChip(selected = mine, onClick = { mine = true }, label = { Text("Я должен") })
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    val n = name.trim()
                    val (folder, body) = when (kind) {
                        "subs" -> "Подписки" to "Подписка: $n\nСумма: ${amount.trim()}\nДата: ${date.trim()}\nПериод: $period"
                        "bday" -> "Даты" to "ДР: $n\nДата: ${date.trim()}"
                        "warr" -> "Гарантии" to "Гарантия: $n\nДо: ${date.trim()}"
                        else -> "Долги" to ((if (mine) "Я должен: $n" else "Долг: $n") + "\nСумма: ${amount.trim()}" + if (date.isNotBlank()) "\nВернуть до: ${date.trim()}" else "")
                    }
                    scope.launch {
                        vm.addNoteIn(folder, n, body)
                        reload++
                        adding = null
                    }
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { adding = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun Row0(it: DueItem, today: LocalDate, withKind: Boolean) {
    val late = it.next != null && it.next.isBefore(today) && it.kind != "bday"
    Row(Modifier.fillMaxWidth().padding(16.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                (if (withKind) (TITLES[it.kind] ?: "") + ": " else "") + it.name,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                listOfNotNull(
                    it.next?.let { d -> fmtDate(d) + " · " + daysText(d, today) },
                    it.extra.takeIf { e -> e.isNotBlank() && it.kind != "subs" },
                    if (it.kind == "subs") it.extra else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        it.amount?.let { a -> Text(rub(Math.abs(a)), style = MaterialTheme.typography.bodyMedium) }
    }
}
