package ru.stasoft.mynotes.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.stasoft.mynotes.data.MONTH_NAMES
import ru.stasoft.mynotes.data.PayTable

private fun money(v: Double) = String.format(java.util.Locale("ru"), "%,.2f", v).replace(' ', ' ')
private fun pct(now: Double, was: Double): String {
    if (was == 0.0) return ""
    val d = (now - was) / was * 100
    return (if (d >= 0) "+" else "") + String.format(java.util.Locale("ru"), "%.1f", d) + "%"
}

@Composable
fun PayTableDialog(vm: AppViewModel, folderId: Long?, onDismiss: () -> Unit) {
    var table by remember { mutableStateOf<PayTable?>(null) }
    var year by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(folderId) { table = vm.payTable(folderId); year = table?.years?.firstOrNull() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                    Text("Таблица по папке", style = MaterialTheme.typography.titleLarge)
                }
                val t = table
                if (t == null) Text("Считаю…", Modifier.padding(16.dp))
                else if (t.cols.isEmpty()) Text(
                    "В этой папке нет заметок вида «год — месяцы с суммами», например:\n2026\nОктябрь 7793,81\nСентябрь 6476,12\n\nОткройте папку, где лежат такие заметки (каждая услуга — отдельная заметка), и повторите.",
                    Modifier.padding(16.dp),
                ) else Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = year == null, onClick = { year = null }, label = { Text("Все годы") })
                        t.years.forEach { y -> FilterChip(selected = year == y, onClick = { year = y }, label = { Text("$y") }) }
                    }
                    val shown = t.rows.filter { year == null || it.year == year }
                    val cw = 92.dp
                    Column(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp)) {
                        Row {
                            Text("Месяц", Modifier.width(110.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            t.cols.forEach { Text(it, Modifier.width(cw), fontWeight = FontWeight.Bold, textAlign = TextAlign.End, maxLines = 2, style = MaterialTheme.typography.labelMedium) }
                            Text("Итого", Modifier.width(cw), fontWeight = FontWeight.Bold, textAlign = TextAlign.End, style = MaterialTheme.typography.labelMedium)
                        }
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        shown.forEach { r ->
                            Row(Modifier.padding(vertical = 2.dp)) {
                                Text(MONTH_NAMES[r.month - 1] + if (year == null) " ${r.year}" else "", Modifier.width(110.dp), style = MaterialTheme.typography.bodyMedium)
                                t.cols.forEach { c -> Text(r.values[c]?.let(::money) ?: "—", Modifier.width(cw), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium) }
                                Text(money(r.total), Modifier.width(cw), textAlign = TextAlign.End, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    val y = year
                    HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                    if (y != null) {
                        val n = t.monthsIn(y)
                        Text("Итоги за $y (месяцев: $n)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(12.dp, 12.dp, 12.dp, 4.dp))
                        t.cols.forEach { c ->
                            val sum = t.yearSum(y, c)
                            val prev = t.prevYearSameMonths(y, c)
                            Text("$c: ${money(sum)} ₽, в среднем ${money(sum / n)} в месяц" + (prev?.let { " · к $y−1: ${pct(sum, it)}" } ?: ""), Modifier.padding(horizontal = 12.dp, vertical = 2.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                        val tot = t.yearTotal(y)
                        Text("Всего: ${money(tot)} ₽, в среднем ${money(tot / n)} в месяц" + (t.prevYearSameMonths(y, null)?.let { " · к прошлому году: ${pct(tot, it)}" } ?: ""), Modifier.padding(12.dp, 6.dp), fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("Итоги по годам", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(12.dp, 12.dp, 12.dp, 4.dp))
                        t.years.forEach { yy -> Text("$yy: ${money(t.yearTotal(yy))} ₽ (месяцев: ${t.monthsIn(yy)})", Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) }
                    }
                    val chron = shown.reversed().map { it.total }
                    if (chron.size > 1) {
                        Text("Итого по месяцам, ₽", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(12.dp, 14.dp, 12.dp, 0.dp))
                        Column(Modifier.padding(horizontal = 12.dp)) { LineChart(chron) }
                    }
                    if (t.skipped.isNotEmpty()) {
                        Text("Не распознано (${t.skipped.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(12.dp, 14.dp, 12.dp, 4.dp))
                        t.skipped.take(30).forEach { Text(it, Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}
