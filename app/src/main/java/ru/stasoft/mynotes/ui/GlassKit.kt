package ru.stasoft.mynotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Предпросмотр всего стеклянного набора: кнопки, круглые кнопки, поля, переключатели, вкладки, список выбора. */
@Composable
fun GlassKitDialog(onDismiss: () -> Unit) {
    CompositionLocalProvider(LocalButtonStyle provides 1) {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                var query by remember { mutableStateOf("") }
                var note by remember { mutableStateOf("") }
                var sw1 by remember { mutableStateOf(false) }
                var sw2 by remember { mutableStateOf(true) }
                var c1 by remember { mutableStateOf(false) }
                var c2 by remember { mutableStateOf(true) }
                var tab by remember { mutableIntStateOf(0) }
                var pick by remember { mutableIntStateOf(0) }
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                        Text("Стеклянный набор", style = MaterialTheme.typography.titleLarge)
                    }
                    Column(
                        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        AppButton("Создать заметку", {}, Modifier.fillMaxWidth(), icon = Icons.Filled.Create, tone = 0)
                        AppButton("Добавить", {}, Modifier.fillMaxWidth(), icon = Icons.Filled.Add, tone = 1)
                        AppButton("Календарь", {}, Modifier.fillMaxWidth(), icon = Icons.Filled.CalendarMonth, tone = 2)
                        AppButton("Напоминание", {}, Modifier.fillMaxWidth(), icon = Icons.Filled.Notifications, tone = 3)
                        GlassTextField(query, { query = it }, Modifier.fillMaxWidth(), "Поиск заметок", Icons.Filled.Search)
                        GlassTextField(note, { note = it }, Modifier.fillMaxWidth(), "Новая заметка", Icons.Filled.Create)
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            listOf(Icons.Filled.Add to 4, Icons.Filled.Notifications to 3, Icons.Filled.Settings to 1, Icons.Filled.Work to 2).forEach { (ic, t) ->
                                GlassBox(Modifier.size(58.dp), CircleShape, pastel(t), {}) {
                                    Icon(ic, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(26.dp))
                                }
                            }
                        }
                        GlassTabs(
                            listOf(
                                Icons.AutoMirrored.Filled.List to "Заметки",
                                Icons.Filled.CheckBox to "Задачи",
                                Icons.Filled.CalendarMonth to "Календарь",
                                Icons.Filled.Settings to "Настройки",
                            ),
                            tab,
                            { tab = it },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            GlassSwitch(sw1, { sw1 = it })
                            GlassSwitch(sw2, { sw2 = it })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlassCheckbox(c1, { c1 = it })
                            Text("Задача", modifier = Modifier.padding(start = 10.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlassCheckbox(c2, { c2 = it })
                            Text("Выполнено", modifier = Modifier.padding(start = 10.dp))
                        }
                        GlassPanel(Modifier.fillMaxWidth()) {
                            GlassMenuRow(Icons.Filled.CalendarMonth, "Сегодня", pick == 0) { pick = 0 }
                            GlassMenuRow(Icons.Filled.Work, "Работа", pick == 1) { pick = 1 }
                            GlassMenuRow(Icons.Filled.Person, "Личное", pick == 2) { pick = 2 }
                        }
                        Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.Center) {
                            GlassBox(Modifier.size(72.dp), CircleShape, pastel(2), {}) {
                                Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(34.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
