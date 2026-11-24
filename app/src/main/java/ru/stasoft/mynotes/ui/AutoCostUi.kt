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
import androidx.compose.runtime.collectAsState
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
import ru.stasoft.mynotes.data.CAR_DOC_WORKS
import ru.stasoft.mynotes.data.CAR_EXTRA_FIELDS
import ru.stasoft.mynotes.data.NoteEntity
import ru.stasoft.mynotes.data.carCosts
import ru.stasoft.mynotes.data.fmtDate
import ru.stasoft.mynotes.data.formatNumber
import java.time.LocalDate
import java.time.YearMonth

private fun rub(v: Double) = formatNumber(Math.round(v).toDouble()) + " ₽"

/** Все расходы на машину: топливо, ТО и ремонт, страховка, налоги; цена километра и пробег в месяц. */
@Composable
fun CarCostDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val cars by vm.cars.collectAsState()
    var notes by remember { mutableStateOf<List<NoteEntity>?>(null) }
    var car by remember { mutableStateOf<String?>(vm.defaultCar()?.name?.takeIf { vm.cars.value.size > 1 }) }
    var year by remember { mutableStateOf<Int?>(LocalDate.now().year) }
    var reload by remember { mutableStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(reload) { notes = vm.allNotesOnce() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                    Text("Расходы на авто", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    HelpButton("auto")
                    TextButton(onClick = { adding = true }) { Text("+ Расход") }
                }
                val all = notes
                if (all == null) Text("Считаю…", Modifier.padding(16.dp))
                else {
                    val allYears = remember(all, car) { carCosts(all, cars, car, null).years }
                    val s = remember(all, car, year, cars) { carCosts(all, cars, car, year) }
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                        if (cars.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = car == null, onClick = { car = null }, label = { Text("Все машины") })
                            cars.forEach { c -> FilterChip(selected = car == c.name, onClick = { car = c.name }, label = { Text(c.name) }) }
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp, 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = year == null, onClick = { year = null }, label = { Text("Всё время") })
                            allYears.reversed().forEach { y -> FilterChip(selected = year == y, onClick = { year = y }, label = { Text(y.toString()) }) }
                        }
                        if (s.items.isEmpty()) Text(
                            "Расходов за период нет. Нажмите «+ Расход» или записывайте в заметках: заправки — «Литры:», «СуммаБ:»; ТО и ремонт — «Работа:» и «Сумма:»; страховка, налог, штраф, мойка — «Страховка: 12000».",
                            Modifier.padding(16.dp),
                        ) else {
                            Column(Modifier.padding(16.dp)) {
                                Text("Всего: " + rub(s.total), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                s.perMonth?.let { Text("В среднем в месяц: " + rub(it), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
                                s.perKm?.let { Text("Стоимость километра со всеми расходами: " + formatNumber(Math.round(it * 100) / 100.0) + " ₽", style = MaterialTheme.typography.bodyMedium) }
                                s.km?.let { Text("Пройдено: " + formatNumber(it) + " км" + (s.kmPerMonth?.let { m -> " (≈ " + formatNumber(Math.round(m).toDouble()) + " км в месяц)" } ?: ""), style = MaterialTheme.typography.bodyMedium) }
                                s.lastOdo?.let { Text("Последний пробег: " + formatNumber(it) + " км", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            Text("По статьям", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp, 4.dp))
                            s.byCat.forEach { (k, v) ->
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
                                    Text(k, Modifier.weight(1f))
                                    Text(rub(v) + "  ·  " + (if (s.total > 0) Math.round(v * 100 / s.total) else 0) + "%")
                                }
                            }
                            HorizontalDivider(Modifier.padding(16.dp, 12.dp))
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("По месяцам", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            }
                            val loc = java.util.Locale("ru")
                            s.items.groupBy { YearMonth.from(it.date) }.toSortedMap(compareByDescending { it }).forEach { (ym, list) ->
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
                                    Text(
                                        ym.month.getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, loc).replaceFirstChar { it.uppercase() } + " " + ym.year,
                                        Modifier.weight(1f),
                                    )
                                    Text(rub(list.sumOf { it.amount }))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (adding) {
        var cat by remember { mutableStateOf(CAR_EXTRA_FIELDS.first()) }
        var amount by remember { mutableStateOf("") }
        var odo by remember { mutableStateOf("") }
        var carName by remember { mutableStateOf(car ?: vm.defaultCar()?.name) }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Новый расход") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (CAR_DOC_WORKS + CAR_EXTRA_FIELDS).forEach { FilterChip(selected = it == cat, onClick = { cat = it }, label = { Text(it) }) }
                    }
                    if (cars.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        cars.forEach { c -> FilterChip(selected = carName == c.name, onClick = { carName = c.name }, label = { Text(c.name) }) }
                    }
                    OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Сумма, ₽") }, singleLine = true)
                    OutlinedTextField(value = odo, onValueChange = { odo = it }, label = { Text("Пробег, км (необязательно)") }, singleLine = true)
                    Text(
                        "Заправки и обычное ТО лучше вводить как раньше — голосом или шаблоном. Здесь — страховка, налог, техосмотр, шины и прочее. ОСАГО, техосмотр и смену шин можно добавить в регламент ТО машины («Документы и сезон»), и приложение напомнит о сроке.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = amount.isNotBlank(), onClick = {
                    val today = fmtDate(LocalDate.now())
                    val isWork = cat in CAR_DOC_WORKS
                    val body = buildString {
                        if (isWork) append("Работа: $cat\nСумма: ${amount.trim()}") else append("$cat: ${amount.trim()}")
                        if (odo.isNotBlank()) append("\nПробег: ${odo.trim()}")
                        append("\nДата: $today")
                        if (cars.size > 1 && carName != null) append("\nАвто: $carName")
                    }
                    scope.launch {
                        vm.addNoteIn("Авто", "$cat $today", body)
                        reload++
                        adding = false
                    }
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Отмена") } },
        )
    }
}
