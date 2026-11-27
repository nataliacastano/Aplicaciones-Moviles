package com.example.gestorempresa.ui.advanced.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

/** Colores de las series. Se distinguen bien en tema claro y oscuro. */
object ChartColors {
    val blue = Color(0xFF3B82F6)
    val orange = Color(0xFFF59E0B)
    val green = Color(0xFF10B981)
    val red = Color(0xFFEF4444)
    val purple = Color(0xFF8B5CF6)
    val teal = Color(0xFF14B8A6)
    val pink = Color(0xFFEC4899)
    val slate = Color(0xFF94A3B8)
    val palette = listOf(blue, orange, green, purple, teal, pink, red, slate)
}

data class ChartSeries(val name: String, val color: Color, val values: List<Double>)

/** Importe con signo (+/−) para resultados, que pueden ser negativos. */
fun signedEuros(value: Double): String = (if (value > 0) "+" else if (value < 0) "−" else "") + euros(kotlin.math.abs(value))

fun euros(value: Double): String = if (kotlin.math.abs(value) >= 10_000) "%.1fk €".format(value / 1000) else "%.0f €".format(value)

/** Leyenda con un punto de color por serie. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChartLegend(items: List<Pair<String, Color>>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (name, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(6.dp))
                Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Gráfico de columnas por mes. Con [stacked] las series se apilan (por ejemplo, personal + materiales);
 * si no, se dibujan una al lado de la otra.
 */
@Composable
fun ColumnChart(labels: List<String>, series: List<ChartSeries>, stacked: Boolean, format: (Double) -> String,
    modifier: Modifier = Modifier, description: String = "Gráfico de columnas") {
    val measurer = rememberTextMeasurer()
    val axis = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val totals = labels.indices.map { i -> if (stacked) series.sumOf { it.values[i] } else series.maxOfOrNull { it.values[i] } ?: 0.0 }
    val maxValue = max(totals.maxOrNull() ?: 0.0, 1.0)
    Canvas(modifier.fillMaxWidth().height(200.dp).semantics { contentDescription = description }) {
        val bottom = size.height - 22.dp.toPx()
        val top = 16.dp.toPx()
        val slot = size.width / labels.size.coerceAtLeast(1)
        drawLine(axis, Offset(0f, bottom), Offset(size.width, bottom), 1.dp.toPx())
        listOf(0.5f).forEach { f ->
            val y = bottom - (bottom - top) * f
            drawLine(axis, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        }
        labels.forEachIndexed { i, label ->
            val barWidth = if (stacked) slot * 0.55f else slot * 0.7f / series.size.coerceAtLeast(1)
            var stackBase = bottom
            series.forEachIndexed { s, serie ->
                val h = ((serie.values[i] / maxValue) * (bottom - top)).toFloat()
                if (h > 0f) {
                    val x = if (stacked) slot * i + (slot - barWidth) / 2 else slot * i + slot * 0.15f + barWidth * s
                    val yTop = if (stacked) stackBase - h else bottom - h
                    drawRoundRect(serie.color, Offset(x, yTop), Size(barWidth, h), CornerRadius(3.dp.toPx()))
                    if (stacked) stackBase -= h
                }
            }
            val labelLayout = measurer.measure(label, TextStyle(fontSize = 10.sp, color = labelColor))
            drawText(labelLayout, topLeft = Offset(slot * i + (slot - labelLayout.size.width) / 2, bottom + 5.dp.toPx()))
            if (totals[i] > 0) {
                val valueLayout = measurer.measure(format(totals[i]), TextStyle(fontSize = 9.sp, color = labelColor))
                val barTop = bottom - ((totals[i] / maxValue) * (bottom - top)).toFloat()
                drawText(valueLayout, topLeft = Offset(slot * i + (slot - valueLayout.size.width) / 2, (barTop - valueLayout.size.height - 2f).coerceAtLeast(0f)))
            }
        }
    }
}

/** Gráfico de líneas (evolución económica: ingresos frente a gastos). */
@Composable
fun LineChart(labels: List<String>, series: List<ChartSeries>, format: (Double) -> String, modifier: Modifier = Modifier,
    description: String = "Gráfico de líneas") {
    val measurer = rememberTextMeasurer()
    val axis = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val maxValue = max(series.flatMap { it.values }.maxOrNull() ?: 0.0, 1.0)
    Canvas(modifier.fillMaxWidth().height(200.dp).semantics { contentDescription = description }) {
        val bottom = size.height - 22.dp.toPx()
        val top = 18.dp.toPx()
        val left = 8.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val step = if (labels.size > 1) (right - left) / (labels.size - 1) else 0f
        drawLine(axis, Offset(0f, bottom), Offset(size.width, bottom), 1.dp.toPx())
        val maxLayout = measurer.measure(format(maxValue), TextStyle(fontSize = 9.sp, color = labelColor))
        drawText(maxLayout, topLeft = Offset(0f, 0f))
        drawLine(axis, Offset(0f, top), Offset(size.width, top), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        labels.forEachIndexed { i, label ->
            val layout = measurer.measure(label, TextStyle(fontSize = 10.sp, color = labelColor))
            drawText(layout, topLeft = Offset((left + step * i - layout.size.width / 2).coerceIn(0f, size.width - layout.size.width), bottom + 5.dp.toPx()))
        }
        series.forEach { serie ->
            val points = serie.values.mapIndexed { i, v -> Offset(left + step * i, bottom - ((v / maxValue) * (bottom - top)).toFloat()) }
            val path = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
            drawPath(path, serie.color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
            points.forEach { p ->
                drawCircle(surface, 4.5.dp.toPx(), p)
                drawCircle(serie.color, 3.dp.toPx(), p)
            }
        }
    }
}

/** Barras horizontales con el nombre a la izquierda; útiles para nombres largos (proyectos, trabajadores). */
@Composable
fun HorizontalBars(items: List<Pair<String, Double>>, color: Color, format: (Double) -> String, maxItems: Int = 8) {
    val shown = items.take(maxItems)
    val maxValue = max(shown.maxOfOrNull { it.second } ?: 0.0, 1.0)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        shown.forEach { (label, value) ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(format(value), style = MaterialTheme.typography.labelLarge)
                }
                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth((value / maxValue).toFloat().coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(color))
                }
            }
        }
    }
}

/** Barras horizontales apiladas: compara los costes (personal, materiales, otros) entre proyectos. */
@Composable
fun StackedHorizontalBars(items: List<Pair<String, List<Double>>>, colors: List<Color>, format: (Double) -> String, maxItems: Int = 8) {
    val shown = items.take(maxItems)
    val maxValue = max(shown.maxOfOrNull { it.second.sum() } ?: 0.0, 1.0)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        shown.forEach { (label, parts) ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(format(parts.sum()), style = MaterialTheme.typography.labelLarge)
                }
                Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    parts.forEachIndexed { i, part ->
                        val fraction = (part / maxValue).toFloat()
                        if (fraction > 0f) Box(Modifier.fillMaxHeight().weight(fraction).background(colors[i % colors.size]))
                    }
                    val rest = (1f - (parts.sum() / maxValue).toFloat())
                    if (rest > 0.0001f) Spacer(Modifier.weight(rest))
                }
            }
        }
    }
}

/** Gráfico circular (anillo) con el total en el centro y la leyenda al lado. */
@Composable
fun DonutChart(slices: List<Pair<String, Int>>, description: String = "Gráfico circular") {
    val total = slices.sumOf { it.second }.coerceAtLeast(1)
    val colors = slices.indices.map { ChartColors.palette[it % ChartColors.palette.size] }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().semantics { contentDescription = description }) {
                val stroke = 22.dp.toPx()
                var start = -90f
                slices.forEachIndexed { i, (_, count) ->
                    val sweep = 360f * count / total
                    drawArc(colors[i], start, (sweep - 1.5f).coerceAtLeast(0.5f), false,
                        topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${slices.sumOf { it.second }}", style = MaterialTheme.typography.headlineSmall)
                Text("personas", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            slices.take(8).forEachIndexed { i, (name, count) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(colors[i]))
                    Spacer(Modifier.width(6.dp))
                    Text("$name · $count", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/**
 * Barras de resultado alrededor del cero: hacia arriba y en verde si es positivo, hacia abajo y en
 * rojo si es negativo. Así un mes con pérdidas se distingue de un vistazo.
 */
@Composable
fun ResultBars(labels: List<String>, values: List<Double>, format: (Double) -> String, modifier: Modifier = Modifier,
    description: String = "Resultado") {
    val measurer = rememberTextMeasurer()
    val axis = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val maxAbs = max(values.maxOfOrNull { kotlin.math.abs(it) } ?: 0.0, 1.0)
    Canvas(modifier.fillMaxWidth().height(200.dp).semantics { contentDescription = description }) {
        val top = 16.dp.toPx()
        val bottom = size.height - 22.dp.toPx()
        val zero = (top + bottom) / 2
        val half = zero - top
        val slot = size.width / labels.size.coerceAtLeast(1)
        drawLine(axis, Offset(0f, zero), Offset(size.width, zero), 1.dp.toPx())
        values.forEachIndexed { i, v ->
            val h = (kotlin.math.abs(v) / maxAbs * half).toFloat()
            val x = slot * i + slot * 0.2f
            val w = slot * 0.6f
            if (h > 0f) drawRoundRect(if (v >= 0) ChartColors.green else ChartColors.red,
                Offset(x, if (v >= 0) zero - h else zero), Size(w, h), CornerRadius(3.dp.toPx()))
            val label = measurer.measure(labels[i], TextStyle(fontSize = 10.sp, color = labelColor))
            drawText(label, topLeft = Offset(slot * i + (slot - label.size.width) / 2, bottom + 5.dp.toPx()))
            if (v != 0.0) {
                val text = measurer.measure(format(v), TextStyle(fontSize = 9.sp, color = labelColor))
                val y = if (v >= 0) zero - h - text.size.height - 2f else zero + h + 2f
                drawText(text, topLeft = Offset(slot * i + (slot - text.size.width) / 2, y.coerceIn(0f, size.height - text.size.height)))
            }
        }
    }
}
