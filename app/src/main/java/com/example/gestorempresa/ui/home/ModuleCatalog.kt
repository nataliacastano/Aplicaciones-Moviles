package com.example.gestorempresa.ui.home

import java.text.Normalizer
import java.util.Locale

enum class CatalogKind(val title: String, val newRoute: String) {
    EMPLOYEES("Empleados", "employee-new"), SUBCONTRACTORS("Subcontratación", "subcontractor-new"),
    PROJECTS("Servicios y proyectos", "project-new"), SUPPLIERS("Proveedores", "supplier-new"), MATERIALS("Materiales", "material-new")
}
data class CatalogSelection(val kind: CatalogKind, val id: Long)
data class CatalogRow(
    val id: Long, val title: String, val subtitle: String, val searchText: String,
    val trade: String = "", val type: String = "", val status: String = "",
    val zone: String = "", val projectIds: Set<Long> = emptySet(), val supplierId: Long? = null,
    val startDate: String = "", val endDate: String? = null
)
data class CatalogFilters(
    val query: String = "", val trade: String = "", val type: String = "", val status: String = "",
    val zone: String = "", val projectId: Long? = null, val supplierId: Long? = null,
    val fromDate: String = "", val toDate: String = ""
)
internal fun String.normalizedSearch(): String = Normalizer.normalize(trim(), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

fun filterCatalog(rows: List<CatalogRow>, filters: CatalogFilters): List<CatalogRow> {
    if ((filters.fromDate.isNotEmpty() && !validDate(filters.fromDate)) ||
        (filters.toDate.isNotEmpty() && !validDate(filters.toDate)) ||
        (filters.fromDate.isNotEmpty() && filters.toDate.isNotEmpty() && filters.fromDate > filters.toDate)) return emptyList()
    val words = filters.query.normalizedSearch().split(Regex("\\s+")).filter { it.isNotBlank() }
    return rows.filter { row ->
        val text = "${row.title} ${row.subtitle} ${row.searchText} ${row.status} ${row.zone} ${row.trade} ${row.type}".normalizedSearch()
        words.all { it in text } && (filters.trade.isEmpty() || row.trade == filters.trade) &&
            (filters.type.isEmpty() || row.type == filters.type) && (filters.status.isEmpty() || row.status == filters.status || (filters.status == "PENDIENTE" && row.status != "FINALIZADO")) &&
            (filters.zone.isEmpty() || row.zone == filters.zone) && (filters.projectId == null || filters.projectId in row.projectIds) &&
            (filters.supplierId == null || filters.supplierId == row.supplierId) &&
            (filters.fromDate.isEmpty() || row.startDate >= filters.fromDate) &&
            (filters.toDate.isEmpty() || row.startDate <= filters.toDate)
    }.sortedWith(compareBy({ it.title.normalizedSearch() }, { it.id }))
}

fun HomeWorkspaceState.catalog(kind: CatalogKind): List<CatalogRow> {
    fun zone(workerKind: String, id: Long) = workerMeta.firstOrNull { it.workerKind == workerKind && it.workerId == id }?.workZone.orEmpty()
    fun assigned(workerKind: String, id: Long) = assignments.filter { it.workerKind == workerKind && it.workerId == id }.map { it.projectId }.toSet()
    return when (kind) {
        CatalogKind.EMPLOYEES -> employees.map { e ->
            val z = zone("EMPLEADO", e.id)
            CatalogRow(e.id, "${e.nombre} ${e.apellidos}", "${e.oficio} · ${e.tipoEmpleado}", "${e.email} ${e.telefono} $z",
                e.oficio, e.tipoEmpleado, if (e.activo) "ACTIVO" else "INACTIVO", z, assigned("EMPLEADO", e.id))
        }
        CatalogKind.SUBCONTRACTORS -> subcontractors.map { s ->
            val z = zone("SUBCONTRATADO", s.id)
            CatalogRow(s.id, s.nombreComercial, "${s.oficio} · ${s.personaContacto}", "${s.email} ${s.telefono} $z",
                trade = s.oficio, type = "SUBCONTRATADO", status = if (s.activo) "ACTIVO" else "INACTIVO", zone = z, projectIds = assigned("SUBCONTRATADO", s.id))
        }
        CatalogKind.PROJECTS -> projects.map { p -> CatalogRow(p.id, p.titulo, "${p.clienteNombre.ifBlank { "Sin cliente" }} · ${p.direccionUbicacion}",
            "${p.tipo} ${p.estado} ${p.fechaInicio} ${p.fechaFin.orEmpty()}", type = p.tipo, status = p.estado,
            zone = p.direccionUbicacion, startDate = p.fechaInicio, endDate = p.fechaFin) }
        CatalogKind.SUPPLIERS -> suppliers.map { s -> CatalogRow(s.id, s.nombreEmpresa, s.personaContacto, "${s.cif} ${s.telefono} ${s.email}") }
        CatalogKind.MATERIALS -> materials.map { m ->
            val supplier = suppliers.firstOrNull { it.id == m.proveedorId }?.nombreEmpresa ?: "Proveedor no disponible"
            CatalogRow(m.id, m.nombreMaterial, "${"%.2f".format(m.precioUnitario)} € / ${m.unidadMedida} · $supplier", supplier,
                type = m.unidadMedida, supplierId = m.proveedorId)
        }
    }
}
