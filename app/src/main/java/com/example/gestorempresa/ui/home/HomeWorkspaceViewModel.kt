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
    val obligations: List<HomeObligation> = emptyList(), val tasks: List<HomeTask> = emptyList(),
    val attachments: List<HomeAttachment> = emptyList(), val materialUses: List<ConsumoMaterialEntity> = emptyList(),
    val zoneLocations: List<HomeZoneLocation> = emptyList()
) {
    fun workerName(kind: String, id: Long): String = if (kind == "EMPLEADO")
        employees.firstOrNull { it.id == id }?.let { "${it.nombre} ${it.apellidos}" } ?: "Empleado"
        else subcontractors.firstOrNull { it.id == id }?.nombreComercial ?: "Subcontratado"

    /** Nombre legible del registro al que pertenece un adjunto. */
    fun attachmentOwnerName(file: HomeAttachment): String = when (file.ownerKind) {
        "EMPLEADO", "SUBCONTRATADO" -> workerName(file.ownerKind, file.ownerId)
        "PROYECTO" -> projects.firstOrNull { it.id == file.ownerId }?.titulo ?: "Servicio"
        "PROVEEDOR" -> suppliers.firstOrNull { it.id == file.ownerId }?.nombreEmpresa ?: "Proveedor"
        "MATERIAL" -> materials.firstOrNull { it.id == file.ownerId }?.nombreMaterial ?: "Material"
        else -> "Registro"
    }

    fun summary(name: String, company: String, today: LocalDate = LocalDate.now(), warnDays: Int = 15): HomeUiState {
        val monthly = movements.filter { it.date.startsWith(YearMonth.from(today).toString()) }
        val projectAlerts = projects.filter {
            it.estado != "FINALIZADO" && it.fechaFin != null && validDate(it.fechaFin) && it.fechaFin <= today.plusDays(warnDays.toLong()).toString()
        }.map { project -> HomeAlert(0, project.titulo,
            if (project.fechaFin!! <= today.toString()) "Finalización prevista vencida: ${project.fechaFin}" else "Finalización prevista: ${project.fechaFin}",
            AlertType.GENERAL, project.fechaFin <= today.toString(), "PROJECTS", project.id) }
        val due = obligations.filter { obligation ->
            !obligation.resolved && (obligation.kind == "DOCUMENTO" && obligation.dueDate.isNullOrBlank() ||
                obligation.dueDate?.let { validDate(it) && it <= today.plusDays(warnDays.toLong()).toString() } == true)
        }
        val obligationAlerts = due.map { record -> HomeAlert(0, record.title,
            "${record.kind} · ${workerName(record.workerKind, record.workerId)} · ${record.dueDate?.let { if (it <= today.toString()) "Vencido: $it" else "Vence: $it" } ?: "Documentación pendiente"}",
            AlertType.DOCUMENTACION, record.dueDate != null && record.dueDate <= today.toString(), "obligations", record.id) }
        // Contratos, certificados y otros documentos adjuntos con fecha de caducidad (funciones avanzadas).
        val documentAlerts = attachments.filter { file ->
            file.type == "DOCUMENTO" && file.expiresAt?.let { validDate(it) && it <= today.plusDays(warnDays.toLong()).toString() } == true &&
                obligations.none { obligation ->
                    !obligation.resolved && obligation.workerKind == file.ownerKind && obligation.workerId == file.ownerId &&
                        obligation.title.normalizedSearch() == file.name.normalizedSearch() && obligation.dueDate == file.expiresAt
                }
        }.map { file ->
            HomeAlert(0, file.name, "${attachmentOwnerName(file)} · ${if (file.expiresAt!! <= today.toString()) "Caducado: ${file.expiresAt}" else "Caduca: ${file.expiresAt}"}",
                AlertType.DOCUMENTACION, file.expiresAt <= today.toString(), "calendar", file.id) }
        val pending = tasks.filter { !it.completed }.sortedBy { it.dueDate }
        val taskAlerts = pending.filter { validDate(it.dueDate) && it.dueDate <= today.plusDays(warnDays.toLong()).toString() }.map { task -> HomeAlert(0, task.title,
            "Tarea pendiente: ${task.dueDate}", AlertType.GENERAL, task.dueDate <= today.toString(), "tasks", task.id) }
        val warnings = (projectAlerts + obligationAlerts + documentAlerts + taskAlerts)
            .distinctBy { "${it.destino}:${it.elementoId}" }
            .sortedWith(compareByDescending<HomeAlert> { it.urgente }.thenBy { it.titulo.normalizedSearch() })
            .mapIndexed { i, alert -> alert.copy(id = i) }
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
            val catalogs = combine(dao.subcontractors(), dao.suppliers(), dao.materials(), dao.workerMeta(), dao.zoneLocations()) { s, p, m, z, g ->
                HomeWorkspaceState(subcontractors = s, suppliers = p, materials = m, workerMeta = z, zoneLocations = g) }
            val activity = combine(dao.assignments(), dao.obligations(), dao.tasks(), dao.attachments(), dao.materialUses()) { a, o, t, f, u ->
                HomeWorkspaceState(assignments = a, obligations = o, tasks = t, attachments = f, materialUses = u) }
            combine(core, catalogs, activity) { c, l, a -> c.copy(subcontractors = l.subcontractors, suppliers = l.suppliers,
                materials = l.materials, workerMeta = l.workerMeta, zoneLocations = l.zoneLocations, assignments = a.assignments, obligations = a.obligations, tasks = a.tasks,
                attachments = a.attachments, materialUses = a.materialUses) }
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
