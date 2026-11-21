package com.example.gestorempresa.ui.advanced.stats

import com.example.gestorempresa.ui.home.HomeWorkspaceState
import java.time.LocalDate
import java.time.YearMonth

/** Costes e ingresos de un servicio o proyecto. */
data class ProjectCosts(
    val projectId: Long,
    val title: String,
    val personnel: Double,
    val materials: Double,
    val otherExpenses: Double,
    val income: Double,
    val hours: Double
) {
    val total: Double get() = personnel + materials + otherExpenses
    val result: Double get() = income - total
}

/** Valores de un mes para los gráficos de evolución. */
data class MonthValues(
    val month: YearMonth,
    val hours: Double = 0.0,
    val personnel: Double = 0.0,
    val materials: Double = 0.0,
    val expenses: Double = 0.0,
    val income: Double = 0.0
)

/** Coste de personal (horas × tarifa), materiales consumidos, gastos registrados e ingresos del proyecto. */
fun HomeWorkspaceState.projectCosts(projectId: Long): ProjectCosts {
    val hoursOf = hours.filter { it.unidadTrabajoId == projectId }
    val moves = movements.filter { it.projectId == projectId }
    return ProjectCosts(
        projectId = projectId,
        title = projects.firstOrNull { it.id == projectId }?.titulo ?: "Servicio",
        personnel = hoursOf.sumOf { it.horasTrabajadas * it.costeHoraAplicado },
        materials = materialUses.filter { it.unidadTrabajoId == projectId }.sumOf { it.cantidad * it.precioUnitarioAplicado },
        otherExpenses = moves.filter { !it.income }.sumOf { it.amountCents } / 100.0,
        income = moves.filter { it.income }.sumOf { it.amountCents } / 100.0,
        hours = hoursOf.sumOf { it.horasTrabajadas }
    )
}

/** Comparación de costes de todos los proyectos, de mayor a menor coste total. */
fun HomeWorkspaceState.allProjectCosts(): List<ProjectCosts> =
    projects.map { projectCosts(it.id) }.sortedByDescending { it.total }

/** Totales por mes de los últimos [months] meses (el último es el mes de [today]). */
fun HomeWorkspaceState.monthlyValues(months: Int, today: LocalDate = LocalDate.now()): List<MonthValues> {
    val last = YearMonth.from(today)
    val range = (months - 1 downTo 0).map { last.minusMonths(it.toLong()) }
    fun monthOf(date: String): YearMonth? = try { YearMonth.parse(date.take(7)) } catch (_: Exception) { null }
    return range.map { month ->
        val h = hours.filter { monthOf(it.fecha) == month }
        val m = movements.filter { monthOf(it.date) == month }
        MonthValues(
            month = month,
            hours = h.sumOf { it.horasTrabajadas },
            personnel = h.sumOf { it.horasTrabajadas * it.costeHoraAplicado },
            materials = materialUses.filter { monthOf(it.fecha) == month }.sumOf { it.cantidad * it.precioUnitarioAplicado },
            expenses = m.filter { !it.income }.sumOf { it.amountCents } / 100.0,
            income = m.filter { it.income }.sumOf { it.amountCents } / 100.0
        )
    }
}

/** Número de trabajadores activos (empleados y subcontratados) por oficio, de más a menos. */
fun HomeWorkspaceState.workersByTrade(): List<Pair<String, Int>> =
    (employees.filter { it.activo }.map { it.oficio } + subcontractors.filter { it.activo }.map { it.oficio })
        .map { it.trim().ifBlank { "Sin oficio" }.replaceFirstChar(Char::uppercase) }
        .groupingBy { it }.eachCount()
        .toList().sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })

/** Ingresos de cada servicio o proyecto, solo los que tienen ingresos. */
fun HomeWorkspaceState.incomeByProject(): List<Pair<String, Double>> =
    projects.map { p -> p.titulo to movements.filter { it.projectId == p.id && it.income }.sumOf { it.amountCents } / 100.0 }
        .filter { it.second > 0 }.sortedByDescending { it.second }

/** Horas trabajadas por cada trabajador en total, de más a menos. */
fun HomeWorkspaceState.hoursByWorker(): List<Pair<String, Double>> =
    hours.groupBy { if (it.empleadoId != null) "EMPLEADO" to it.empleadoId else "SUBCONTRATADO" to (it.subcontratadoId ?: 0) }
        .map { (key, list) -> workerName(key.first, key.second) to list.sumOf { it.horasTrabajadas } }
        .sortedByDescending { it.second }

private val monthNames = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

/** Nombre corto del mes, por ejemplo «nov». */
fun YearMonth.shortLabel(): String = monthNames[monthValue - 1]
