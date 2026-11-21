package com.example.gestorempresa.ui.advanced.dates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.gestorempresa.ui.home.CatalogSelection
import com.example.gestorempresa.ui.home.HomeWorkspaceState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

private val monthNames = listOf("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")

@Composable
fun statusColor(status: DueStatus): Color = when (status) {
    DueStatus.VENCIDO -> MaterialTheme.colorScheme.error
    DueStatus.PROXIMO -> Color(0xFFF59E0B)
    DueStatus.PROGRAMADO -> MaterialTheme.colorScheme.primary
    DueStatus.HECHO -> MaterialTheme.colorScheme.outline
}

/**
 * Calendario de fechas y vencimientos: reconocimientos médicos, cursos, contratos, documentación,
 * fechas de servicios y proyectos y tareas. Muestra el mes con un punto por día y la lista de fechas.
 */
@Composable
fun CalendarScreen(state: HomeWorkspaceState, warnDays: Int, onOpen: (CatalogSelection) -> Unit, onNotifyNow: () -> Unit,
    modifier: Modifier = Modifier, today: LocalDate = LocalDate.now()) {
    val all = remember(state) { state.dueItems() }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val month = YearMonth.parse(monthText)
    var selectedDay by rememberSaveable { mutableStateOf<String?>(null) }
    var filter by rememberSaveable { mutableStateOf("MES") }
    val overdue = all.filter { it.status(today, warnDays) == DueStatus.VENCIDO }
    val upcoming = all.filter { it.status(today, warnDays) == DueStatus.PROXIMO }
    val shown = when (filter) {
        "VENCIDOS" -> overdue
        "PROXIMOS" -> upcoming
        else -> selectedDay?.let { day -> all.filter { it.date.toString() == day } } ?: all.filter { YearMonth.from(it.date) == month }
    }

    LazyColumn(modifier.fillMaxSize().testTag("calendar-screen"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Fechas y vencimientos", style = MaterialTheme.typography.headlineMedium)
                Text("Se considera «próximo» lo que vence en los próximos $warnDays días (puedes cambiarlo en Ajustes).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CounterTile("Vencidos", overdue.size, MaterialTheme.colorScheme.error, Modifier.weight(1f)) { filter = "VENCIDOS" }
                CounterTile("Próximos", upcoming.size, Color(0xFFF59E0B), Modifier.weight(1f)) { filter = "PROXIMOS" }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { monthText = month.minusMonths(1).toString(); selectedDay = null; filter = "MES" }) { Text("‹") }
                        Text("${monthNames[month.monthValue - 1]} ${month.year}", Modifier.weight(1f), textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { monthText = month.plusMonths(1).toString(); selectedDay = null; filter = "MES" }) { Text("›") }
                    }
                    MonthGrid(month, today, all, warnDays, selectedDay) { day ->
                        filter = "MES"
                        selectedDay = if (selectedDay == day.toString()) null else day.toString()
                    }
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("MES" to (selectedDay?.let { "Día $it" } ?: "Este mes"), "VENCIDOS" to "Vencidos", "PROXIMOS" to "Próximos")) { (code, label) ->
                    FilterChip(selected = filter == code, onClick = { filter = code }, label = { Text(label) })
                }
            }
        }
        if (shown.isEmpty()) item { Text("No hay fechas para mostrar.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(shown.size) { index ->
            val item = shown[index]
            DueRow(item, item.status(today, warnDays)) { item.target?.let(onOpen) }
        }
        item {
            OutlinedButton(onClick = onNotifyNow, modifier = Modifier.fillMaxWidth()) { Text("🔔 Enviar ahora el aviso de vencimientos") }
        }
    }
}

@Composable
private fun CounterTile(label: String, count: Int, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$count", style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MonthGrid(month: YearMonth, today: LocalDate, items: List<DueItem>, warnDays: Int, selectedDay: String?, onDay: (LocalDate) -> Unit) {
    val byDay = items.filter { YearMonth.from(it.date) == month }.groupBy { it.date }
    val offset = month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value
    val cells = (0 until offset).map<Int, LocalDate?> { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row { listOf("L", "M", "X", "J", "V", "S", "D").forEach {
            Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        cells.chunked(7).forEach { week ->
            Row {
                (0 until 7).forEach { i ->
                    val day = week.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (day != null) {
                            val dayItems = byDay[day].orEmpty()
                            val selected = selectedDay == day.toString()
                            Column(
                                Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .then(if (day == today) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier)
                                    .clickable { onDay(day) },
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                            ) {
                                Text("${day.dayOfMonth}", style = MaterialTheme.typography.bodyMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    dayItems.map { it.status(today, warnDays) }.distinct().take(3).forEach { status ->
                                        Box(Modifier.size(5.dp).clip(CircleShape).background(statusColor(status)))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DueRow(item: DueItem, status: DueStatus, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(enabled = item.target != null, onClick = onClick), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(4.dp).height(40.dp).clip(RoundedCornerShape(2.dp)).background(statusColor(status)))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall)
                Text("${item.kind.label} · ${item.detail}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(item.date.toString(), style = MaterialTheme.typography.labelLarge)
                Text(status.label, style = MaterialTheme.typography.labelSmall, color = statusColor(status))
            }
        }
    }
}
