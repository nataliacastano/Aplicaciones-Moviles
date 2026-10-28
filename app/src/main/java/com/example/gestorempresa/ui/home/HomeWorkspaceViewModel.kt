package com.example.gestorempresa.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gestorempresa.data.local.*
import com.example.gestorempresa.data.local.entities.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class HomeWorkspaceState(
    val employees: List<EmpleadoEntity> = emptyList(),
    val projects: List<UnidadTrabajoEntity> = emptyList(),
    val hours: List<RegistroHorasEntity> = emptyList(),
    val movements: List<HomeMovement> = emptyList(),
    val loading: Boolean = true, val error: String? = null,
    val subcontractors: List<SubcontratadoEntity> = emptyList(),
    val suppliers: List<ProveedorEntity> = emptyList(), val materials: List<MaterialEntity> = emptyList(),
    val workerMeta: List<HomeWorkerMeta> = emptyList(), val assignments: List<HomeAssignment> = emptyList(),
    val obligations: List<HomeObligation> = emptyList(), val tasks: List<HomeTask> = emptyList()
) {
    fun workerName(kind: String, id: Long): String = if (kind == "EMPLEADO")
        employees.firstOrNull { it.id == id }?.let { "${it.nombre} ${it.apellidos}" } ?: "Empleado #$id"
        else subcontractors.firstOrNull { it.id == id }?.nombreComercial ?: "Subcontratado #$id"

    fun summary(name: String, company: String, today: LocalDate = LocalDate.now()): HomeUiState {
        val monthly = movements.filter { it.date.startsWith(YearMonth.from(today).toString()) }
        val projectAlerts = projects.filter {
            it.estado == "ACTIVO" && it.fechaFin != null && it.fechaFin <= today.plusDays(15).toString()
        }.map { project -> HomeAlert(0, project.titulo,
            if (project.fechaFin!! <= today.toString()) "Finalización prevista vencida: ${project.fechaFin}" else "Finalización prevista: ${project.fechaFin}",
            AlertType.GENERAL, project.fechaFin <= today.toString(), "PROJECTS", project.id) }
        val due = obligations.filter { !it.resolved && (it.kind == "DOCUMENTO" || (it.dueDate != null && it.dueDate <= today.plusDays(30).toString())) }
        val obligationAlerts = due.map { record -> HomeAlert(0, record.title,
            "${workerName(record.workerKind, record.workerId)} · ${record.dueDate?.let { if (it <= today.toString()) "Vencido: $it" else "Vence: $it" } ?: "Documentación pendiente"}",
            AlertType.DOCUMENTACION, record.dueDate != null && record.dueDate <= today.toString(), "obligations", record.id) }
        val pending = tasks.filter { !it.completed }.sortedBy { it.dueDate }
        val taskAlerts = pending.filter { it.dueDate <= today.toString() }.map { HomeAlert(0, it.title,
            "Tarea pendiente: ${it.dueDate}", AlertType.GENERAL, true, "tasks", it.id) }
        val warnings = (projectAlerts + obligationAlerts + taskAlerts).sortedByDescending { it.urgente }.mapIndexed { i, alert -> alert.copy(id = i) }
        return HomeUiState(nombreAdministrador = name, cargoAdministrador = company,
            empleados = employees.count { it.activo }, subcontratados = subcontractors.count { it.activo },
            totalObras = projects.size, serviciosActivos = projects.count { it.estado == "ACTIVO" }, obrasActivas = projects.count { it.estado == "ACTIVO" },
            trabajadoresEnCampo = hours.filter { it.fecha == today.toString() }.mapNotNull { it.empleadoId }.distinct().size,
            ingresos = monthly.filter { it.income }.sumOf { it.amountCents } / 100.0,
            gastos = monthly.filter { !it.income }.sumOf { it.amountCents } / 100.0,
            listaAlertas = warnings, alertas = warnings.size, isLoading = loading, error = error,
            documentosPendientes = obligations.count { !it.resolved && it.kind == "DOCUMENTO" },
            cursosProximos = due.count { it.kind == "CURSO" }, medicosProximos = due.count { it.kind == "MEDICO" },
            tareasPendientes = pending.size, trabajosPendientes = projects.count { it.estado != "FINALIZADO" },
            proximasTareas = pending.take(5).map { "${it.dueDate} · ${it.title}" })
    }
}

open class HomeWorkspaceViewModel(val dao: HomeDao) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeWorkspaceState())
    val state = mutableState.asStateFlow()
    private var observation: Job? = null
    val saving = MutableStateFlow(false)
    val saveError = MutableStateFlow<String?>(null)
    val catalogFilters = MutableStateFlow<Map<CatalogKind, CatalogFilters>>(emptyMap())
    fun filter(kind: CatalogKind, value: CatalogFilters) { catalogFilters.update { it + (kind to value) } }
    init { retry() }
    fun retry() {
        observation?.cancel()
        mutableState.update { it.copy(loading = true, error = null) }
        observation = viewModelScope.launch {
            val core = combine(dao.employees(), dao.projects(), dao.hours(), dao.movements()) { e, p, h, m -> HomeWorkspaceState(e, p, h, m, loading = false) }
            val catalogs = combine(dao.subcontractors(), dao.suppliers(), dao.materials(), dao.workerMeta()) { s, p, m, z ->
                HomeWorkspaceState(subcontractors = s, suppliers = p, materials = m, workerMeta = z) }
            val activity = combine(dao.assignments(), dao.obligations(), dao.tasks()) { a, o, t -> HomeWorkspaceState(assignments = a, obligations = o, tasks = t) }
            combine(core, catalogs, activity) { c, l, a -> c.copy(subcontractors = l.subcontractors, suppliers = l.suppliers,
                materials = l.materials, workerMeta = l.workerMeta, assignments = a.assignments, obligations = a.obligations, tasks = a.tasks) }
                .catch { emit(HomeWorkspaceState(loading = false, error = "No se han podido cargar los datos. Pulsa Reintentar.")) }
                .collect { mutableState.value = it }
        }
    }
    fun save(action: suspend HomeDao.() -> Unit, onSuccess: () -> Unit) {
        if (saving.value) return
        saving.value = true
        saveError.value = null
        viewModelScope.launch {
            try { dao.action(); onSuccess() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { saveError.value = "No se ha podido guardar. Inténtalo de nuevo." }
            finally { saving.value = false }
        }
    }
}
