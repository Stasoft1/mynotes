package ru.stasoft.mynotes.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Стиль кнопок: 0 — объёмные (тёмные), 1 — «стеклянные» пастельные. */
val LocalButtonStyle = compositionLocalOf { 0 }

private val PASTELS = listOf(
    Color(0xFFCFC9F7), // сирень
    Color(0xFFC4EBD6), // мята
    Color(0xFFFAD3B4), // персик
    Color(0xFFF6C6DC), // розовый
    Color(0xFFC2DBF7), // небесный
)

fun pastel(index: Int): Color = PASTELS[Math.floorMod(index, PASTELS.size)]

fun pastelFor(key: String): Color = pastel(key.hashCode())

/** Матовое стекло: цветная подложка, блик сверху, светлая кромка, цветная мягкая тень. */
@Composable
fun GlassBox(
    modifier: Modifier,
    shape: Shape,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val a1 = if (dark) 0.38f else 0.75f
    val a2 = if (dark) 0.16f else 0.35f
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.45f)
            .shadow(if (dark) 4.dp else 9.dp, shape, ambientColor = tint, spotColor = tint)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .background(Brush.linearGradient(listOf(tint.copy(alpha = a1), tint.copy(alpha = a2))))
            .background(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = if (dark) 0.22f else 0.60f),
                    0.45f to Color.White.copy(alpha = if (dark) 0.05f else 0.14f),
                    1f to Color.Transparent,
                ),
            )
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.45f else 0.95f), tint.copy(alpha = 0.55f))), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Кнопка с подписью: в стеклянном стиле — широкая «капсула» как в наборе компонентов, иначе обычная. */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: Int = 0,
    enabled: Boolean = true,
) {
    if (LocalButtonStyle.current == 1) {
        val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        val fg = if (dark) Color.White else Color(0xFF1F2430)
        GlassBox(modifier.height(52.dp), RoundedCornerShape(26.dp), pastel(tone), onClick, enabled) {
            Row(
                Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
                    Box(Modifier.size(10.dp))
                }
                Text(text, color = fg, style = MaterialTheme.typography.titleSmall)
            }
        }
    } else {
        Button(onClick = onClick, modifier = modifier, enabled = enabled) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Box(Modifier.size(8.dp))
            }
            Text(text)
        }
    }
}
