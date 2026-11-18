package ru.stasoft.mynotes.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import ru.stasoft.mynotes.R

/** Вариант значка приложения: псевдоним активности + ресурс значка. */
private class AppIcon(val alias: String, val title: String, val res: Int)

private val ICONS = listOf(
    AppIcon("IconClassic", "Классика", R.mipmap.ic_launcher),
    AppIcon("IconNotebook", "Блокнот", R.mipmap.ic_launcher_notebook),
    AppIcon("IconTree", "Дерево", R.mipmap.ic_launcher_tree),
    AppIcon("IconCheck", "Галочка", R.mipmap.ic_launcher_check),
    AppIcon("IconMono", "Минимал", R.mipmap.ic_launcher_mono),
    AppIcon("IconNeon", "Неон", R.mipmap.ic_launcher_neon),
    AppIcon("IconBookmark", "Закладка", R.mipmap.ic_launcher_bookmark),
    AppIcon("IconLock", "Замок", R.mipmap.ic_launcher_lock),
)

private fun cn(ctx: Context, alias: String) = ComponentName(ctx.packageName, "ru.stasoft.mynotes.$alias")

private fun currentIcon(ctx: Context): String =
    ICONS.firstOrNull {
        val st = ctx.packageManager.getComponentEnabledSetting(cn(ctx, it.alias))
        st == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
            (st == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && it.alias == "IconClassic")
    }?.alias ?: "IconClassic"

private fun applyIcon(ctx: Context, alias: String) {
    val pm = ctx.packageManager
    // Сначала включаем новый, потом выключаем остальные, чтобы значок не пропадал.
    pm.setComponentEnabledSetting(cn(ctx, alias), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
    ICONS.filter { it.alias != alias }.forEach {
        pm.setComponentEnabledSetting(cn(ctx, it.alias), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
    }
}

@Composable
fun IconSettingsSection() {
    val ctx = LocalContext.current
    var selected by remember { mutableStateOf(currentIcon(ctx)) }
    Text(
        "Выберите значок. Он сменится на экране приложений; на некоторых лаунчерах это занимает несколько секунд.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    ICONS.chunked(4).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            row.forEach { ic ->
                val bmp = remember(ic.res) { ctx.getDrawable(ic.res)?.toBitmap(192, 192)?.asImageBitmap() }
                Column(
                    Modifier.weight(1f).clickable { applyIcon(ctx, ic.alias); selected = ic.alias },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (bmp != null) {
                        Image(
                            bmp, ic.title,
                            Modifier.size(64.dp).clip(CircleShape).border(
                                if (selected == ic.alias) 3.dp else 1.dp,
                                if (selected == ic.alias) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape,
                            ),
                        )
                    }
                    Text(ic.title, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                }
            }
            repeat(4 - row.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
        }
    }
}
