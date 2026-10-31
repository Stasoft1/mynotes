package ru.stasoft.mynotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.stasoft.mynotes.data.Span
import ru.stasoft.mynotes.data.SpanKind
import ru.stasoft.mynotes.data.alignAtLine

/** Размеры шрифта в списке выбора: подпись и значение в sp (null — обычный). */
val FONT_SIZES: List<Pair<String, Int?>> = listOf(
    "Мелкий" to 12,
    "Обычный" to null,
    "Крупный" to 20,
    "Очень крупный" to 26,
    "Заголовок" to 32,
)

private fun buildStyled(text: String, spans: List<Span>): AnnotatedString {
    val len = text.length
    return AnnotatedString.Builder(text).apply {
        // Стили символов: режем текст по границам диапазонов и для каждого куска собираем итоговый стиль.
        val marks = sortedSetOf(0, len)
        spans.forEach { if (it.kind != SpanKind.ALIGN) { marks.add(it.start.coerceIn(0, len)); marks.add(it.end.coerceIn(0, len)) } }
        val pts = marks.toList()
        for (i in 0 until pts.size - 1) {
            val a = pts[i]
            val b = pts[i + 1]
            if (b <= a) continue
            var weight: FontWeight? = null
            var italic: FontStyle? = null
            val deco = ArrayList<TextDecoration>()
            var color = Color.Unspecified
            var bg = Color.Unspecified
            var size = androidx.compose.ui.unit.TextUnit.Unspecified
            for (s in spans) {
                if (s.kind == SpanKind.ALIGN || s.start > a || s.end < b) continue
                when (s.kind) {
                    SpanKind.BOLD -> weight = FontWeight.Bold
                    SpanKind.ITALIC -> italic = FontStyle.Italic
                    SpanKind.UNDERLINE -> deco.add(TextDecoration.Underline)
                    SpanKind.STRIKE -> deco.add(TextDecoration.LineThrough)
                    SpanKind.COLOR -> color = Color(s.value.toInt())
                    SpanKind.BACKGROUND -> bg = Color(s.value.toInt())
                    SpanKind.SIZE -> size = s.value.toInt().sp
                }
            }
            if (weight != null || italic != null || deco.isNotEmpty() || color != Color.Unspecified ||
                bg != Color.Unspecified || size != androidx.compose.ui.unit.TextUnit.Unspecified
            ) {
                addStyle(
                    SpanStyle(
                        color = color,
                        fontSize = size,
                        fontWeight = weight,
                        fontStyle = italic,
                        textDecoration = if (deco.isEmpty()) null else TextDecoration.combine(deco),
                        background = bg,
                    ),
                    a,
                    b,
                )
            }
        }
        // Выравнивание задаётся строкам целиком.
        var ls = 0
        while (ls <= len) {
            val nl = text.indexOf('\n', ls)
            val le = if (nl < 0) len else nl
            val align = alignAtLine(spans, ls)
            if (align != null && le >= ls && (le > ls || nl >= 0)) {
                val to = if (nl >= 0) nl + 1 else le
                addStyle(
                    ParagraphStyle(
                        textAlign = when (align) {
                            "c" -> TextAlign.Center
                            "r" -> TextAlign.End
                            "j" -> TextAlign.Justify
                            else -> TextAlign.Start
                        },
                    ),
                    ls,
                    to,
                )
            }
            if (nl < 0) break
            ls = nl + 1
        }
    }.toAnnotatedString()
}

/** Показывает оформление поверх обычного текста поля, не меняя сам текст и положение курсора. */
class SpanTransformation(private val spans: List<Span>) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (spans.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        return TransformedText(buildStyled(text.text, spans), OffsetMapping.Identity)
    }
}

@Composable
fun ColorPickerDialog(title: String, onPick: (Int?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.height(260.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(COLOR_PALETTE) { c ->
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .clickable { onPick(c) },
                        contentAlignment = Alignment.Center,
                    ) {}
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(null) }) { Text("Без цвета") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
