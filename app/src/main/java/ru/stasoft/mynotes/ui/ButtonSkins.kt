package ru.stasoft.mynotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Названия наборов кнопок: 0 и 1 — прежние (объёмные, стеклянные), 2–6 — новые; у каждого есть светлый и тёмный вид. */
val BUTTON_SKINS = listOf("Объёмные", "Стеклянные", "Плоские", "Мягкие", "Контурные", "Градиентные", "Неон")

@Composable
private fun skinDark(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

private fun c(v: Long) = Color(v)

// Пять тонов: сирень, мята, персик, роза, небо. Акцент (оранжевый) — персиковый.
private val FLAT_L = listOf(c(0xFFD8D2FA), c(0xFFC9EBD8), c(0xFFFAD9BC), c(0xFFF6C9DE), c(0xFFC9DFF7))
private val FLAT_D = listOf(c(0xFF433F6E), c(0xFF2F5A47), c(0xFF6B4B33), c(0xFF6A3B53), c(0xFF31536F))
private val INK_L = listOf(c(0xFF5B4FD6), c(0xFF1F9D63), c(0xFFE0792B), c(0xFFD2478A), c(0xFF2D7FD6))
private val INK_D = listOf(c(0xFFB9B0FF), c(0xFF80E4B2), c(0xFFFFB27A), c(0xFFFF9EC8), c(0xFF8EC4FF))
private val GRAD_L = listOf(
    c(0xFF8A78FF) to c(0xFF4E3FD0), c(0xFF3CCB8B) to c(0xFF12935A), c(0xFFFFA24D) to c(0xFFE8651A),
    c(0xFFFF6FAE) to c(0xFFD13C86), c(0xFF4FA8FF) to c(0xFF2166D8),
)
private val GRAD_D = listOf(
    c(0xFF5A4BC9) to c(0xFF372C94), c(0xFF22835B) to c(0xFF175A3D), c(0xFFC9702A) to c(0xFF8F4512),
    c(0xFFB8457F) to c(0xFF7C2656), c(0xFF2F78C4) to c(0xFF1B4C8C),
)
private val NEON = listOf(c(0xFFB69CFF), c(0xFF4DFFB0), c(0xFFFFA54D), c(0xFFFF5CAA), c(0xFF4DC8FF))

/** Основа мягкого стиля: фон панели и кнопок одного цвета, объём даёт пара теней. */
fun neuBase(dark: Boolean): Color = if (dark) c(0xFF262B38) else c(0xFFE9EDF4)

private fun Modifier.neu(dark: Boolean, cornerDp: Float): Modifier = drawBehind {
    val base = neuBase(dark)
    val light = if (dark) c(0xFF343B4C) else Color.White
    val shade = if (dark) c(0xFF14171F) else c(0xFFBFC7D6)
    val r = 7.dp.toPx()
    val d = 4.dp.toPx()
    val cr = cornerDp.dp.toPx()
    drawIntoCanvas { cv ->
        val p = androidx.compose.ui.graphics.Paint().asFrameworkPaint().apply { isAntiAlias = true; color = base.toArgb() }
        p.setShadowLayer(r, -d, -d, light.toArgb())
        cv.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, cr, cr, p)
        p.setShadowLayer(r, d, d, shade.toArgb())
        cv.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, cr, cr, p)
    }
}

/**
 * Контейнер кнопки в одном из новых стилей (2–6). Цвет значка и текста приходит в content как fg.
 * [tone] выбирает один из пяти тонов, [accent] — оранжевый акцент.
 */
@Composable
fun SkinBox(
    style: Int,
    modifier: Modifier,
    shape: Shape,
    tone: Int,
    accent: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    cornerDp: Float = 23f,
    content: @Composable BoxScope.(Color) -> Unit,
) {
    val dark = skinDark()
    val t = if (accent) 2 else Math.floorMod(tone, 5)
    val click = Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    val base = modifier.alpha(if (enabled) 1f else 0.45f)
    when (style) {
        2 -> { // плоские: тонированная заливка без теней
            val fg = if (dark) Color.White.copy(alpha = 0.92f) else c(0xFF1F2430)
            Box(base.clip(shape).background(if (dark) FLAT_D[t] else FLAT_L[t]).then(click), Alignment.Center) { content(fg) }
        }
        3 -> { // мягкие: выпуклые, цвет фона панели
            val fg = if (dark) INK_D[t] else INK_L[t]
            Box(base.neu(dark, cornerDp).clip(shape).then(click), Alignment.Center) { content(fg) }
        }
        4 -> { // контурные: прозрачные, цветной ободок
            val ink = if (dark) INK_D[t] else INK_L[t]
            Box(base.clip(shape).border(1.8.dp, ink, shape).background(ink.copy(alpha = if (dark) 0.10f else 0.07f)).then(click), Alignment.Center) { content(ink) }
        }
        5 -> { // градиентные: насыщенный диагональный градиент
            val (a, b) = if (dark) GRAD_D[t] else GRAD_L[t]
            Box(
                base.shadow(if (dark) 3.dp else 6.dp, shape, ambientColor = b, spotColor = b).clip(shape)
                    .background(Brush.linearGradient(listOf(a, b))).then(click),
                Alignment.Center,
            ) { content(Color.White) }
        }
        else -> { // неон: тёмная заливка и светящийся ободок (на светлой теме — белая с цветным свечением)
            val ink = if (dark) NEON[t] else INK_L[t]
            Box(
                base.shadow(if (dark) 9.dp else 5.dp, shape, ambientColor = ink, spotColor = ink).clip(shape)
                    .background(if (dark) c(0xFF10121A) else Color.White).border(1.6.dp, ink, shape).then(click),
                Alignment.Center,
            ) { content(ink) }
        }
    }
}

/** Образец набора в двух темах рядом: пять круглых кнопок на светлой и на тёмной панели. */
@Composable
fun SkinPreview(style: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(false, true).forEach { dark ->
            MaterialTheme(colorScheme = if (dark) darkColorScheme(surface = c(0xFF14161C)) else lightColorScheme(surface = c(0xFFF7F7FB))) {
                val panel = when {
                    style == 0 -> c(0xFF1B2330)
                    style == 3 -> neuBase(dark)
                    dark -> c(0xFF1D1F27)
                    else -> c(0xFFF1F2F8)
                }
                CompositionLocalProvider(LocalButtonStyle provides style) {
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(panel).padding(vertical = 6.dp)) {
                        Text(
                            if (dark) "тёмная" else "светлая",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (style == 0 || dark) Color.White.copy(alpha = 0.7f) else c(0xFF5A6070),
                            modifier = Modifier.padding(start = 10.dp),
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            StripButton(Icons.Filled.Add, "Добавить", onClick = {})
                            StripButton(Icons.Filled.Search, "Поиск", onClick = {})
                            StripButton(Icons.Filled.Mic, "Голос", accent = true, onClick = {})
                            StripButton(Icons.Filled.Edit, "Правка", onClick = {})
                            StripButton(Icons.Filled.Settings, "Настройки", onClick = {})
                        }
                    }
                }
            }
        }
    }
}
