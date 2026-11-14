package ru.stasoft.mynotes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.stasoft.mynotes.data.CarEntity
import ru.stasoft.mynotes.data.dateInTitle

/** Раздел настроек модуля «Авто»: справочник машин и привязка старых заметок. */
@Composable
fun AutoSettingsSection(vm: AppViewModel) {
    val cars by vm.cars.collectAsState()
    var editing by remember { mutableStateOf<CarEntity?>(null) }
    var assign by remember { mutableStateOf(false) }
    Text(
        "Авто",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
    )
    Text(
        "Если машин больше одной, в новые заметки про заправку и ТО добавляется строка «Авто:», а при вводе «Авто:» появляется выбор из этого списка. " +
            "В голосе можно назвать машину: «заправка октавия, 40 литров…».",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    cars.forEach { c ->
        Column(Modifier.fillMaxWidth().clickable { editing = c }.padding(horizontal = 16.dp, vertical = 6.dp)) {
            Text(c.name + if (c.isDefault) "  · по умолчанию" else "", style = MaterialTheme.typography.bodyLarge)
            if (c.aliases.isNotBlank()) Text("Голосом: ${c.aliases}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Row(Modifier.padding(horizontal = 8.dp)) {
        TextButton(onClick = { editing = CarEntity(name = "") }) { Text("Добавить машину…") }
        if (cars.isNotEmpty()) TextButton(onClick = { assign = true }) { Text("Привязать старые заметки…") }
    }
    editing?.let { c -> CarDialog(vm, c, onDismiss = { editing = null }) }
    if (assign) AssignCarDialog(vm, cars, onDismiss = { assign = false })
}

@Composable
private fun CarDialog(vm: AppViewModel, car: CarEntity, onDismiss: () -> Unit) {
    var name by remember(car.id) { mutableStateOf(car.name) }
    var aliases by remember(car.id) { mutableStateOf(car.aliases) }
    var dflt by remember(car.id) { mutableStateOf(car.isDefault) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (car.id == 0L) "Новая машина" else "Машина") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название (Octavia)") }, singleLine = true)
                OutlinedTextField(
                    value = aliases, onValueChange = { aliases = it }, label = { Text("Как называть голосом, через запятую") },
                    singleLine = true, modifier = Modifier.padding(top = 8.dp),
                )
                Row(Modifier.fillMaxWidth().clickable { dflt = !dflt }.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Машина по умолчанию", Modifier.weight(1f))
                    AppSwitch(checked = dflt, onCheckedChange = { dflt = it })
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { vm.saveCar(car.copy(name = name.trim(), aliases = aliases.trim(), isDefault = dflt)); onDismiss() }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (car.id != 0L) TextButton(onClick = { vm.deleteCar(car); onDismiss() }) { Text("Удалить") }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}

@Composable
private fun AssignCarDialog(vm: AppViewModel, cars: List<CarEntity>, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var pick by remember { mutableStateOf(vm.defaultCar()?.name ?: cars.first().name) }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Привязать старые заметки") },
        text = {
            Column {
                Text(
                    "Заметки с заправками и ТО, где ещё нет строки «Авто:», получат её в конце текста. Даты берутся из названий папок дней; пустые поля — без ограничения.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("Машина:", Modifier.padding(top = 8.dp))
                cars.forEach { c ->
                    Row(Modifier.fillMaxWidth().clickable { pick = c.name }, verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(selected = pick == c.name, onClick = { pick = c.name })
                        Text(c.name)
                    }
                }
                OutlinedTextField(value = from, onValueChange = { from = it }, label = { Text("С даты (дд.мм.гг)") }, singleLine = true, modifier = Modifier.padding(top = 8.dp))
                OutlinedTextField(value = to, onValueChange = { to = it }, label = { Text("По дату (дд.мм.гг)") }, singleLine = true, modifier = Modifier.padding(top = 8.dp))
                result?.let { Text(it, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.primary) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    val n = vm.assignCar(pick, dateInTitle(from), dateInTitle(to))
                    result = "Привязано заметок: $n"
                }
            }) { Text("Привязать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}
