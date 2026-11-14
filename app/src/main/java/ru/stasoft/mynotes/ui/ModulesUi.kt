package ru.stasoft.mynotes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.stasoft.mynotes.modules.Modules

/** Раздел настроек «Модули»: включение необязательных функций. */
@Composable
fun ModulesSection(vm: AppViewModel) {
    Text(
        "Модули",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
    )
    Text(
        "Необязательные функции. Выключенный модуль нигде не показывается; ваши данные при этом не удаляются.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    Modules.all.forEach { m ->
        val on = Modules.on(m.id)
        Row(
            Modifier.fillMaxWidth().clickable { vm.setModule(m.id, !on) }.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(m.title, style = MaterialTheme.typography.bodyLarge)
                Text(m.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AppSwitch(checked = on, onCheckedChange = { vm.setModule(m.id, it) })
        }
    }
}
