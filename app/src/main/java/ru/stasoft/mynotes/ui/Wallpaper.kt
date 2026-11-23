package ru.stasoft.mynotes.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ru.stasoft.mynotes.R

class WallpaperItem(val title: String, val res: Int)

val WALLPAPERS = listOf(
    WallpaperItem("Яркий", R.drawable.wp_pop),
    WallpaperItem("Пиксели", R.drawable.wp_pixel),
    WallpaperItem("Акварель", R.drawable.wp_water),
    WallpaperItem("Японский", R.drawable.wp_japan),
    WallpaperItem("Неон", R.drawable.wp_neon),
    WallpaperItem("Зелёный", R.drawable.wp_green),
)

/** Включены ли обои: тогда экраны списка и редактора рисуются прозрачными поверх картинки. */
val LocalWallpaper = compositionLocalOf { false }

@Composable
fun WallpaperSettingsSection(vm: AppViewModel) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Картинка станет фоном списка и редактора заметок.", style = MaterialTheme.typography.bodyMedium)
        val items = listOf<WallpaperItem?>(null) + WALLPAPERS
        items.chunked(3).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowItems.forEach { item ->
                    val idx = if (item == null) 0 else WALLPAPERS.indexOf(item) + 1
                    val sel = vm.wallpaper == idx
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.6f)
                                .clip(RoundedCornerShape(12.dp))
                                .border(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                .clickable { vm.chooseWallpaper(idx) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (item != null) Image(
                                painterResource(item.res), null,
                                Modifier.fillMaxWidth().aspectRatio(0.6f),
                                contentScale = ContentScale.Crop,
                                alpha = vm.wallpaperAlpha / 100f,
                            ) else Text("Нет", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(item?.title ?: "Без обоев", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                repeat(3 - rowItems.size) { Box(Modifier.weight(1f)) }
            }
        }
        var v by remember { mutableFloatStateOf(vm.wallpaperAlpha.toFloat()) }
        Text("Прозрачность обоев: ${v.toInt()}%  (чем меньше, тем бледнее картинка)", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = v,
            onValueChange = { v = it; vm.setWallpaperAlpha(it.toInt()) },
            valueRange = 10f..100f,
        )
        Text("По умолчанию — 40%.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
