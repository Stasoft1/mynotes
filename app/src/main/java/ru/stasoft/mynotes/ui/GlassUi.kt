package ru.stasoft.mynotes.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Стиль кнопок: 0 — объёмные (тёмные), 1 — «стеклянные» пастельные. */
val LocalButtonStyle = compositionLocalOf { 0 }

private val LAVENDER = Color(0xFFC9C2F5)
private val MINT = Color(0xFFBFE9D2)
private val PEACH = Color(0xFFF9CFAA)
private val PINK = Color(0xFFF4BBD6)
private val SKY = Color(0xFFBBD7F5)
private val FROST = Color(0xFFD9DEEC)

private val PASTELS = listOf(LAVENDER, MINT, PEACH, PINK, SKY)

fun pastel(index: Int): Color = PASTELS[Math.floorMod(index, PASTELS.size)]

fun pastelFor(key: String): Color = pastel(key.hashCode())

@Composable
private fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

@Composable
private fun glassFg(): Color = if (isDarkTheme()) Color.White else Color(0xFF1F2430)

/**
 * «Гелевое стекло»: матовая подложка, цвет насыщеннее к краям и светлее в центре, блик-капля сверху,
 * мягкий свет снизу, светлая кромка и цветная мягкая тень. [strength] — насыщенность цвета (для полей меньше).
 */
fun Modifier.gel(shape: Shape, tint: Color, dark: Boolean, strength: Float = 1f, elevation: Dp = 9.dp): Modifier =
    this
        .shadow(if (dark) elevation / 2 else elevation, shape, ambientColor = tint, spotColor = tint)
        .clip(shape)
        .background(if (dark) Color(0xFF2A3042) else Color(0xFFFBFBFE))
        .drawBehind {
            val w = size.width
            val h = size.height
            val r = maxOf(w, h)
            val centerA = (if (dark) 0.10f else 0.50f)
            val midA = (if (dark) 0.30f else 0.42f) * strength
            val edgeA = (if (dark) 0.62f else 0.92f) * strength
            drawRect(
                Brush.radialGradient(
                    0f to Color.White.copy(alpha = centerA),
                    0.55f to tint.copy(alpha = midA),
                    1f to tint.copy(alpha = edgeA),
                    center = Offset(w / 2f, h * 0.42f),
                    radius = r * 0.72f,
                ),
            )
            // Нижний тон: цвет чуть собирается к нижней кромке.
            drawRect(
                Brush.verticalGradient(
                    0.55f to Color.Transparent,
                    1f to tint.copy(alpha = (if (dark) 0.35f else 0.45f) * strength),
                ),
            )
            val inset = minOf(w, h) * 0.09f
            val bandH = h * 0.40f
            drawRoundRect(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = if (dark) 0.34f else 0.95f), Color.White.copy(alpha = 0.04f)),
                    startY = inset,
                    endY = inset + bandH,
                ),
                topLeft = Offset(inset * 1.7f, inset),
                size = Size((w - inset * 3.4f).coerceAtLeast(1f), bandH),
                cornerRadius = CornerRadius(bandH / 2f, bandH / 2f),
            )
            drawRoundRect(
                Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = if (dark) 0.18f else 0.55f))),
                topLeft = Offset(inset * 2.4f, h * 0.70f),
                size = Size((w - inset * 4.8f).coerceAtLeast(1f), h * 0.22f),
                cornerRadius = CornerRadius(h * 0.11f, h * 0.11f),
            )
        }
        .border(
            1.2.dp,
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = if (dark) 0.50f else 1f), tint.copy(alpha = 0.55f), Color.White.copy(alpha = if (dark) 0.25f else 0.70f)),
            ),
            shape,
        )

/** Стеклянная кнопка-контейнер любой формы. */
@Composable
fun GlassBox(
    modifier: Modifier,
    shape: Shape,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.45f)
            .gel(shape, tint, isDarkTheme())
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
        val fg = glassFg()
        GlassBox(modifier.height(54.dp), RoundedCornerShape(27.dp), pastel(tone), onClick, enabled) {
            Row(
                Modifier.padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
                    Box(Modifier.size(10.dp))
                }
                Text(text, color = fg, style = MaterialTheme.typography.titleMedium)
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

// ---------- переключатель, флажок, радиокнопка ----------

@Composable
fun GlassSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val dark = isDarkTheme()
    val knobX by animateDpAsState(if (checked) 27.dp else 3.dp, label = "knob")
    Box(
        modifier
            .padding(2.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .size(width = 58.dp, height = 34.dp)
            .then(
                if (onCheckedChange != null) Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                else Modifier,
            ),
    ) {
        Box(Modifier.matchParentSize().gel(RoundedCornerShape(17.dp), if (checked) PEACH else FROST, dark, strength = 0.9f, elevation = 6.dp))
        Box(Modifier.offset(x = knobX, y = 3.dp).size(28.dp).gel(CircleShape, if (checked) PINK else Color.White, dark, elevation = 5.dp))
    }
}

@Composable
fun GlassCheckbox(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val dark = isDarkTheme()
    Box(
        modifier
            .padding(4.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .size(26.dp)
            .then(
                if (onCheckedChange != null) Modifier.toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
                else Modifier,
            )
            .gel(RoundedCornerShape(8.dp), if (checked) MINT else FROST, dark, strength = if (checked) 1f else 0.6f, elevation = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Filled.Check, contentDescription = null, tint = glassFg(), modifier = Modifier.size(18.dp))
    }
}

@Composable
fun GlassRadio(selected: Boolean, onClick: (() -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val dark = isDarkTheme()
    Box(
        modifier
            .padding(4.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .size(26.dp)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick) else Modifier)
            .gel(CircleShape, if (selected) LAVENDER else FROST, dark, strength = if (selected) 1f else 0.6f, elevation = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(glassFg()))
    }
}

/** Переключатель/флажок/радиокнопка: в стеклянном стиле свои, иначе обычные. */
@Composable
fun AppSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    if (LocalButtonStyle.current == 1) GlassSwitch(checked, onCheckedChange, modifier, enabled)
    else Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled)
}

@Composable
fun AppCheckbox(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    if (LocalButtonStyle.current == 1) GlassCheckbox(checked, onCheckedChange, modifier, enabled)
    else Checkbox(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled)
}

@Composable
fun AppRadio(selected: Boolean, onClick: (() -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    if (LocalButtonStyle.current == 1) GlassRadio(selected, onClick, modifier, enabled)
    else RadioButton(selected = selected, onClick = onClick, modifier = modifier, enabled = enabled)
}

// ---------- поле ввода ----------

/** Поле в виде матовой капсулы (поиск, ввод заметки). */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leading: ImageVector? = null,
    singleLine: Boolean = true,
) {
    val dark = isDarkTheme()
    val fg = glassFg()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = TextStyle(color = fg, fontSize = 17.sp),
        cursorBrush = SolidColor(fg),
        modifier = modifier,
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .gel(RoundedCornerShape(27.dp), FROST, dark, strength = 0.55f, elevation = 6.dp)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading != null) {
                    Icon(leading, contentDescription = null, tint = fg, modifier = Modifier.size(24.dp))
                    Box(Modifier.width(12.dp))
                }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, color = fg.copy(alpha = 0.5f), fontSize = 17.sp)
                    inner()
                }
            }
        },
    )
}

// ---------- вкладки и панель выбора ----------

/** Нижняя/верхняя панель вкладок: выбранная вкладка — сиреневая капсула. */
@Composable
fun GlassTabs(items: List<Pair<ImageVector, String>>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val dark = isDarkTheme()
    val fg = glassFg()
    Row(
        modifier
            .fillMaxWidth()
            .gel(RoundedCornerShape(32.dp), FROST, dark, strength = 0.5f, elevation = 8.dp)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { i, (icon, label) ->
            val sel = i == selected
            Column(
                Modifier
                    .weight(1f)
                    .height(60.dp)
                    .then(if (sel) Modifier.gel(RoundedCornerShape(27.dp), LAVENDER, dark, elevation = 6.dp) else Modifier)
                    .clickable(role = Role.Tab) { onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(24.dp))
                Text(label, color = fg, fontSize = 12.sp)
            }
        }
    }
}

/** Панель-список выбора («Сегодня / Работа / Личное»). */
@Composable
fun GlassPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .gel(RoundedCornerShape(26.dp), FROST, isDarkTheme(), strength = 0.45f, elevation = 8.dp)
            .padding(10.dp),
        content = content,
    )
}

@Composable
fun GlassMenuRow(icon: ImageVector, text: String, selected: Boolean, onClick: () -> Unit) {
    val dark = isDarkTheme()
    val fg = glassFg()
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.gel(RoundedCornerShape(22.dp), LAVENDER, dark, strength = 0.8f, elevation = 3.dp) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
        Text(text, color = fg, modifier = Modifier.weight(1f).padding(start = 14.dp), fontSize = 16.sp)
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
    }
}
