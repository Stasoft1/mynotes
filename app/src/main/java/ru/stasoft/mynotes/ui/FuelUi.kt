package ru.stasoft.mynotes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import ru.stasoft.mynotes.data.FuelSummary
import ru.stasoft.mynotes.data.formatNumber

private fun f2(v: Double) = String.format(java.util.Locale("ru"), "%.2f", v)

@Composable
fun FuelDialog(vm: AppViewModel, folderId: Long?, onDismiss: () -> Unit) {
    var st by remember { mutableStateOf<FuelSummary?>(null) }
    var loaded by remember { mutableStateOf(false) }
    val cars by vm.cars.collectAsState()
    var car by remember { mutableStateOf<String?>(vm.defaultCar()?.name?.takeIf { vm.cars.value.size > 1 }) }
    LaunchedEffect(folderId, car) {
        st = vm.fuelStats(folderId, car)
        loaded = true
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Статистика заправок") },
        text = {
            val s = st
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (cars.size > 1) androidx.compose.foundation.layout.Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                ) {
                    androidx.compose.material3.FilterChip(selected = car == null, onClick = { car = null }, label = { Text("Все") })
                    cars.forEach { c -> androidx.compose.material3.FilterChip(selected = car == c.name, onClick = { car = c.name }, label = { Text(c.name) }) }
                }
                if (!loaded) Text("Считаю…")
                else if (s == null || s.fills.isEmpty()) Text("В этой папке нет заметок с полями «Литры:», «ЦенаБ:» или «СуммаБ:». Откройте нужную папку (например, год) и повторите.")
                else {
                    Text("Заправок: ${s.fills.size}, литров: ${formatNumber(s.litres)}, потрачено: ${formatNumber(s.money)} ₽")
                    s.avgPrice?.let { Text("Средняя цена литра: ${f2(it)} ₽") }
                    s.avgPer100?.let { Text("Средний расход: ${f2(it)} л на 100 км", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp)) }
                    s.avgCostKm?.let { Text("Стоимость километра: ${f2(it)} ₽", style = MaterialTheme.typography.titleSmall) }
                    if (s.legs.isEmpty()) Text("Для расхода нужны хотя бы две заправки с полем «Пробег:».", modifier = Modifier.padding(top = 6.dp))
                    Text("Цена литра, ₽", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                    LineChart(s.fills.map { it.price })
                    if (s.legs.size > 1) {
                        Text("Расход, л/100 км", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                        LineChart(s.legs.map { it.per100 })
                    }
                    if (s.legs.isNotEmpty()) {
                        Text("Последние участки", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                        s.legs.takeLast(8).reversed().forEach {
                            Text(
                                (it.date?.let { d -> "%02d.%02d.%d".format(d.dayOfMonth, d.monthValue, d.year) } ?: "—") +
                                    " · ${formatNumber(it.km)} км · ${f2(it.per100)} л/100 · ${f2(it.costKm)} ₽/км",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

@Composable
private fun LineChart(values: List<Double>) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val lo = values.min()
    val hi = values.max()
    Text("${f2(lo)} … ${f2(hi)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Canvas(Modifier.fillMaxWidth().height(110.dp).padding(top = 4.dp)) {
        drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height))
        drawLine(grid, Offset(0f, 0f), Offset(0f, size.height))
        val span = (hi - lo).takeIf { it > 1e-9 } ?: 1.0
        fun pt(i: Int): Offset {
            val x = if (values.size == 1) size.width / 2 else size.width * i / (values.size - 1)
            val y = size.height - ((values[i] - lo) / span * (size.height - 12.dp.toPx())).toFloat() - 6.dp.toPx()
            return Offset(x, y)
        }
        val path = Path()
        values.indices.forEach { i -> pt(i).let { if (i == 0) path.moveTo(it.x, it.y) else path.lineTo(it.x, it.y) } }
        drawPath(path, line, style = Stroke(width = 2.dp.toPx()))
        if (values.size <= 40) values.indices.forEach { drawCircle(line, 3.dp.toPx(), pt(it)) }
    }
}
