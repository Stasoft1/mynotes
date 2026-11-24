package ru.stasoft.mynotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.stasoft.mynotes.data.Expense
import ru.stasoft.mynotes.data.MONTH_NAMES
import ru.stasoft.mynotes.data.monthKey
import ru.stasoft.mynotes.data.parseBudgetConfig

private fun money(v: Double) = String.format(java.util.Locale("ru"), "%,.0f", v).replace(' ', ' ')
private fun monthTitle(k: Int) = MONTH_NAMES[k % 12] + " " + (k / 12)
private fun pct(now: Double, was: Double): String {
    if (was == 0.0) return ""
    val d = (now - was) / was * 100
    return (if (d >= 0) "+" else "") + String.format(java.util.Locale("ru"), "%.0f", d) + "%"
}

/** Отчёт по бюджету за месяц: расходы, доходы, категории с лимитами, динамика по дням, крупные траты. */
@Composable
fun BudgetDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var all by remember { mutableStateOf<List<Expense>?>(null) }
    var month by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        val list = vm.expenses()
        all = list
        val now = java.time.LocalDate.now().let { it.year * 12 + it.monthValue - 1 }
        month = if (list.any { monthKey(it.date) == now } || list.isEmpty()) now else list.maxOf { monthKey(it.date) }
    }
    val cfg = remember(vm.budgetText) { parseBudgetConfig(vm.budgetText) }
    val ctx = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                    Text("Бюджет", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    HelpButton("budget")
                    val list = all
                    if (!list.isNullOrEmpty()) TextButton(onClick = {
                        val fmt = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
                        fun n(v: Double) = String.format(java.util.Locale.US, "%.2f", v).replace('.', ',')
                        CsvExport.share(
                            ctx, "Бюджет",
                            listOf(listOf("Дата", "Тип", "Категория", "Где", "Сумма")) +
                                list.sortedByDescending { it.date }.map { listOf(fmt.format(java.util.Date(it.date)), if (it.income) "Доход" else "Расход", it.category, it.place, n(it.amount)) },
                        )
                    }) { Text("CSV") }
                }
                val list = all
                val m = month
                if (list == null || m == null) Text("Считаю…", Modifier.padding(16.dp))
                else if (list.isEmpty()) Text(
                    "Пока нет данных. Добавьте в заметки строки:\nСумма: 450\nКатегория: Продукты\nМагазин: Пятёрочка\nДата: 21.11.2026 (необязательно, иначе берётся день создания)\n\nТратой считается заметка, где есть «Сумма:» вместе с «Категория:» или «Магазин:», либо поле «Расход: 450». Доходы записываются полем «Доход: 50000». Подсказки категорий появятся при вводе «Категория:». Лимиты на месяц задаются в Настройках, в разделе «Бюджет».",
                    Modifier.padding(16.dp),
                ) else Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                    val months = list.map { monthKey(it.date) }.toSet().plus(m).sortedDescending()
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        months.forEach { k -> FilterChip(selected = k == m, onClick = { month = k }, label = { Text(monthTitle(k)) }) }
                    }
                    val cur = list.filter { monthKey(it.date) == m }
                    val prev = list.filter { monthKey(it.date) == m - 1 }
                    val spent = cur.filter { !it.income }.sumOf { it.amount }
                    val earned = cur.filter { it.income }.sumOf { it.amount }
                    val prevSpent = prev.filter { !it.income }.sumOf { it.amount }
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Расходы", style = MaterialTheme.typography.labelMedium)
                            Text(money(spent) + " ₽", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            if (prevSpent > 0) Text("к прошлому месяцу: " + pct(spent, prevSpent), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (earned > 0) Column(horizontalAlignment = Alignment.End) {
                            Text("Доходы", style = MaterialTheme.typography.labelMedium)
                            Text(money(earned) + " ₽", style = MaterialTheme.typography.titleLarge)
                            Text("баланс: " + money(earned - spent) + " ₽", style = MaterialTheme.typography.bodySmall, color = if (earned - spent < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    Text("По категориям", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp))
                    val byCat = cur.filter { !it.income }.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
                    val rows = (byCat.keys + cfg.limits.keys).distinct().sortedByDescending { byCat[it] ?: 0.0 }
                    rows.forEach { c ->
                        val v = byCat[c] ?: 0.0
                        val lim = cfg.limits[c]
                        val over = lim != null && v >= lim
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Row {
                                Text(c, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    money(v) + (lim?.let { " из " + money(it) } ?: "") + " ₽" + if (spent > 0 && lim == null) " · " + String.format(java.util.Locale("ru"), "%.0f", v / spent * 100) + "%" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (over) FontWeight.Bold else null,
                                )
                            }
                            val frac = (if (lim != null) v / lim else if (spent > 0) v / spent else 0.0).toFloat().coerceIn(0f, 1f)
                            Box(Modifier.fillMaxWidth().height(6.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                                Box(Modifier.fillMaxWidth(frac).height(6.dp).background(if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary))
                            }
                        }
                    }
                    if (byCat.containsKey("Без категории")) Text(
                        "Часть трат без категории: добавьте в заметку строку «Категория: …».",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp, 6.dp),
                    )
                    // нарастающий итог по дням месяца
                    val days = java.time.YearMonth.of(m / 12, m % 12 + 1).lengthOfMonth()
                    val perDay = DoubleArray(days)
                    cur.filter { !it.income }.forEach { e ->
                        val d = java.time.Instant.ofEpochMilli(e.date).atZone(java.time.ZoneId.systemDefault()).dayOfMonth
                        perDay[(d - 1).coerceIn(0, days - 1)] += e.amount
                    }
                    var acc = 0.0
                    val cum = perDay.map { acc += it; acc }
                    if (spent > 0) {
                        Text("Расходы нарастающим итогом, ₽", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(16.dp, 14.dp, 16.dp, 0.dp))
                        Column(Modifier.padding(horizontal = 16.dp)) { LineChart(cum) }
                    }
                    val top = cur.filter { !it.income }.sortedByDescending { it.amount }.take(5)
                    if (top.isNotEmpty()) {
                        Text("Самые крупные траты", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp, 14.dp, 16.dp, 4.dp))
                        val f = java.text.SimpleDateFormat("dd.MM", java.util.Locale.getDefault())
                        top.forEach {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
                                Text(f.format(java.util.Date(it.date)) + "  " + it.place.ifBlank { it.category }, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                Text(money(it.amount) + " ₽", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Настройки модуля «Бюджет»: категории и месячные лимиты. */
@Composable
fun BudgetSettingsSection(vm: AppViewModel) {
    Text(
        "По строке на категорию. Лимит на месяц — через «=», например: Продукты = 20000. Когда траты категории достигнут лимита, придёт уведомление (один раз в месяц).",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    OutlinedTextField(
        value = vm.budgetText,
        onValueChange = { vm.updateBudgetText(it) },
        label = { Text("Категории и лимиты") },
        minLines = 6,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
    Text(
        "Как записывать траты: «Сумма:» вместе с «Категория:» или «Магазин:» (либо одно поле «Расход:»), «Дата:» необязательно. Заметки только с «Сумма:» (оклад, счета) не учитываются. Доходы — поле «Доход:». Отчёт открывается из меню «Бюджет…».",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
}
