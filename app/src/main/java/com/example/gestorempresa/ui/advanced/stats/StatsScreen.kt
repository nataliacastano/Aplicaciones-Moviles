package com.example.gestorempresa.ui.advanced.stats

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.gestorempresa.ui.home.HomeWorkspaceState

/**
 * Estadísticas de la empresa: horas, costes de personal y materiales, gastos,
 * ingresos por servicio, evolución económica, trabajadores por oficio y comparación entre proyectos.
 */
@Composable
fun StatsScreen(state: HomeWorkspaceState, modifier: Modifier = Modifier) {
    var months by rememberSaveable { mutableIntStateOf(6) }
    val monthly = remember(state, months) { state.monthlyValues(months) }
    val labels = monthly.map { it.month.shortLabel() }
    val projects = remember(state) { state.allProjectCosts().filter { it.total > 0 || it.income > 0 } }
    val trades = remember(state) { state.workersByTrade() }
    val income = remember(state) { state.incomeByProject() }
    val workers = remember(state) { state.hoursByWorker() }
    val current = monthly.lastOrNull() ?: MonthValues(java.time.YearMonth.now())

    LazyColumn(modifier.fillMaxSize().testTag("stats-screen"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Estadísticas", style = MaterialTheme.typography.headlineMedium)
                SingleChoiceSegmentedButtonRow {
                    listOf(3, 6, 12).forEachIndexed { i, option ->
                        SegmentedButton(selected = months == option, onClick = { months = option },
                            shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text("$option meses") }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Horas este mes", "%.1f h".format(current.hours), Modifier.weight(1f))
                StatTile("Coste de personal", euros(current.personnel), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Coste de materiales", euros(current.materials), Modifier.weight(1f))
                StatTile("Balance del mes", euros(current.income - current.expenses - current.personnel - current.materials), Modifier.weight(1f))
            }
        }
        item {
            ChartCard("Evolución económica", "Ingresos y gastos registrados por mes.") {
                if (monthly.all { it.income == 0.0 && it.expenses == 0.0 }) EmptyChart("Registra ingresos o gastos para ver su evolución.")
                else {
                    ChartLegend(listOf("Ingresos" to ChartColors.green, "Gastos" to ChartColors.red))
                    LineChart(labels, listOf(ChartSeries("Ingresos", ChartColors.green, monthly.map { it.income }),
                        ChartSeries("Gastos", ChartColors.red, monthly.map { it.expenses })), ::euros, description = "Evolución de ingresos y gastos")
                }
            }
        }
        item {
            ChartCard("Horas trabajadas", "Horas registradas en los partes diarios.") {
                if (monthly.all { it.hours == 0.0 }) EmptyChart("Todavía no hay horas registradas.")
                else ColumnChart(labels, listOf(ChartSeries("Horas", ChartColors.blue, monthly.map { it.hours })), false,
                    { "%.0f".format(it) }, description = "Horas trabajadas por mes")
            }
        }
        item {
            ChartCard("Coste de personal y materiales", "Coste por mes de las horas trabajadas y de los materiales usados.") {
                if (monthly.all { it.personnel == 0.0 && it.materials == 0.0 }) EmptyChart("Sin costes en este periodo.")
                else {
                    ChartLegend(listOf("Personal" to ChartColors.blue, "Materiales" to ChartColors.orange))
                    ColumnChart(labels, listOf(ChartSeries("Personal", ChartColors.blue, monthly.map { it.personnel }),
                        ChartSeries("Materiales", ChartColors.orange, monthly.map { it.materials })), true, ::euros,
                        description = "Coste de personal y materiales por mes")
                }
            }
        }
        item {
            ChartCard("Comparación de costes entre proyectos", "Coste total acumulado de cada servicio o proyecto.") {
                if (projects.isEmpty()) EmptyChart("Ningún servicio tiene costes todavía.")
                else {
                    ChartLegend(listOf("Personal" to ChartColors.blue, "Materiales" to ChartColors.orange, "Otros gastos" to ChartColors.red))
                    StackedHorizontalBars(projects.map { it.title to listOf(it.personnel, it.materials, it.otherExpenses) },
                        listOf(ChartColors.blue, ChartColors.orange, ChartColors.red), ::euros)
                }
            }
        }
        item {
            ChartCard("Ingresos por servicio", "Ingresos acumulados de cada servicio o proyecto.") {
                if (income.isEmpty()) EmptyChart("No hay ingresos registrados.")
                else HorizontalBars(income, ChartColors.green, ::euros)
            }
        }
        item {
            ChartCard("Trabajadores por oficio", "Empleados y subcontratados activos.") {
                if (trades.isEmpty()) EmptyChart("No hay trabajadores activos.")
                else DonutChart(trades, "Trabajadores por oficio")
            }
        }
        item {
            ChartCard("Horas por trabajador", "Total de horas registradas por cada persona.") {
                if (workers.isEmpty()) EmptyChart("Todavía no hay horas registradas.")
                else HorizontalBars(workers, ChartColors.purple, { "%.1f h".format(it) })
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun ChartCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun EmptyChart(message: String) = Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
