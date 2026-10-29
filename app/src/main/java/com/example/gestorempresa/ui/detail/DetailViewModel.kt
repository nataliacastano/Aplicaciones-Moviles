package com.example.gestorempresa.ui.detail

import androidx.lifecycle.ViewModel
import com.example.gestorempresa.ui.home.CatalogKind
import com.example.gestorempresa.ui.home.CatalogSelection
import com.example.gestorempresa.ui.home.HomeWorkspaceState

data class DetailField(val label: String, val value: String, val related: CatalogSelection? = null)
data class DetailSection(val title: String, val fields: List<DetailField>)
data class DetailContent(val title: String, val subtitle: String, val sections: List<DetailSection>)

/** Prepara la ficha desde el estado observable compartido del módulo de listados. */
class DetailViewModel(selection: CatalogSelection, state: HomeWorkspaceState) : ViewModel() {
    val content: DetailContent = when (selection.kind) {
        CatalogKind.EMPLOYEES -> employee(selection.id, state)
        CatalogKind.SUBCONTRACTORS -> subcontractor(selection.id, state)
        CatalogKind.PROJECTS -> project(selection.id, state)
        CatalogKind.SUPPLIERS -> supplier(selection.id, state)
        CatalogKind.MATERIALS -> material(selection.id, state)
    } ?: DetailContent("Registro no disponible", "Puede haberse eliminado", emptyList())

    private fun employee(id: Long, state: HomeWorkspaceState): DetailContent? {
        val person = state.employees.firstOrNull { it.id == id } ?: return null
        val zone = state.workerMeta.firstOrNull { it.workerKind == "EMPLEADO" && it.workerId == id }?.workZone
        val assignments = state.assignments.filter { it.workerKind == "EMPLEADO" && it.workerId == id }
            .mapNotNull { a -> state.projects.firstOrNull { it.id == a.projectId } }
        val hours = state.hours.filter { it.empleadoId == id }.sortedByDescending { it.fecha }
        val obligations = state.obligations.filter { it.workerKind == "EMPLEADO" && it.workerId == id }
        return DetailContent("${person.nombre} ${person.apellidos}", "${person.oficio.ifBlank { "Oficio sin indicar" }} · ${if (person.activo) "Activo" else "Inactivo"}", listOf(
            DetailSection("Datos personales", listOf(
                DetailField("DNI", person.dni), DetailField("Nº de Seguridad Social", person.numeroSeguridadSocial.ifBlank { "Sin indicar" }), DetailField("Teléfono", person.telefono), DetailField("Correo", person.email),
                DetailField("Tipo de empleado", person.tipoEmpleado), DetailField("Residencia", person.lugarResidencia),
                DetailField("Dirección", person.direccionPersonal), DetailField("Zona de trabajo", zone ?: "Sin indicar"),
                DetailField("Salario base", "${"%.2f".format(person.salarioBase)} €")
            )),
            DetailSection("Servicios y proyectos", assignments.map { DetailField(it.titulo, "${it.direccionUbicacion} · ${it.estado}") }.ifEmpty { listOf(DetailField("Asignaciones", "Sin asignaciones")) }),
            DetailSection("Historial de horas", hours.map { h ->
                val project = state.projects.firstOrNull { it.id == h.unidadTrabajoId }
                DetailField(h.fecha, "${project?.titulo ?: "Servicio"} · ${h.horasTrabajadas} h · ${"%.2f".format(h.costeHoraAplicado)} €/h")
            }.ifEmpty { listOf(DetailField("Horas", "Todavía no hay horas registradas")) }),
            DetailSection("Formación, reconocimientos y documentación", obligations.map { o ->
                DetailField(o.title, "${o.kind} · ${o.dueDate ?: "Sin fecha"} · ${if (o.resolved) "Atendido" else "Pendiente"}")
            }.ifEmpty { listOf(DetailField("Avisos", "No hay registros asociados")) })
        ))
    }

    private fun subcontractor(id: Long, state: HomeWorkspaceState): DetailContent? {
        val worker = state.subcontractors.firstOrNull { it.id == id } ?: return null
        val zone = state.workerMeta.firstOrNull { it.workerKind == "SUBCONTRATADO" && it.workerId == id }?.workZone
        val projects = state.assignments.filter { it.workerKind == "SUBCONTRATADO" && it.workerId == id }
            .mapNotNull { a -> state.projects.firstOrNull { it.id == a.projectId } }
        val hours = state.hours.filter { it.subcontratadoId == id }.sortedByDescending { it.fecha }
        return DetailContent(worker.nombreComercial, "${worker.oficio} · ${if (worker.activo) "Activo" else "Inactivo"}", listOf(
            DetailSection("Datos de contacto", listOf(DetailField("Persona de contacto", worker.personaContacto), DetailField("CIF / DNI", worker.cifDni),
                DetailField("Teléfono", worker.telefono), DetailField("Correo", worker.email), DetailField("Zona de trabajo", zone ?: "Sin indicar"),
                DetailField("Tarifa por hora", "${"%.2f".format(worker.tarifaHoraPorDefecto)} €"))),
            DetailSection("Servicios y proyectos", projects.map { DetailField(it.titulo, "${it.direccionUbicacion} · ${it.estado}") }.ifEmpty { listOf(DetailField("Asignaciones", "Sin asignaciones")) }),
            DetailSection("Historial de horas", hours.map { h -> DetailField(h.fecha,
                "${state.projects.firstOrNull { it.id == h.unidadTrabajoId }?.titulo ?: "Servicio"} · ${h.horasTrabajadas} h") }
                .ifEmpty { listOf(DetailField("Horas", "Todavía no hay horas registradas")) })
        ))
    }

    private fun project(id: Long, state: HomeWorkspaceState): DetailContent? {
        val project = state.projects.firstOrNull { it.id == id } ?: return null
        val hours = state.hours.filter { it.unidadTrabajoId == id }.sortedByDescending { it.fecha }
        val assignments = state.assignments.filter { it.projectId == id }
        val movements = state.movements.filter { it.projectId == id }
        return DetailContent(project.titulo, "${project.tipo} · ${project.estado}", listOf(
            DetailSection("Datos del servicio", listOf(DetailField("Cliente", project.clienteNombre.ifBlank { "Sin cliente asociado" }), DetailField("Ubicación", project.direccionUbicacion),
                DetailField("Fecha de inicio", project.fechaInicio), DetailField("Finalización prevista", project.fechaFin ?: "Sin fecha prevista"),
                DetailField("Coordenadas", if (project.latitud != null && project.longitud != null) "${project.latitud}, ${project.longitud}" else "Sin ubicación en mapa"))),
            DetailSection("Equipo asignado", assignments.map { a -> DetailField(state.workerName(a.workerKind, a.workerId), a.workerKind) }
                .ifEmpty { listOf(DetailField("Asignaciones", "Sin trabajadores asignados")) }),
            DetailSection("Horas registradas", hours.map { h -> DetailField(h.fecha,
                "${state.workerName(if (h.empleadoId != null) "EMPLEADO" else "SUBCONTRATADO", h.empleadoId ?: h.subcontratadoId ?: 0)} · ${h.horasTrabajadas} h · ${"%.2f".format(h.costeHoraAplicado)} €/h") }
                .ifEmpty { listOf(DetailField("Horas", "Todavía no hay horas registradas")) }),
            DetailSection("Movimientos económicos", movements.map { m -> DetailField(m.date,
                "${m.concept} · ${if (m.income) "Ingreso" else "Gasto"} · ${"%.2f".format(m.amountCents / 100.0)} €") }
                .ifEmpty { listOf(DetailField("Movimientos", "No hay ingresos o gastos registrados")) }),
            DetailSection("Costes", listOf(DetailField("Personal", "${"%.2f".format(hours.sumOf { it.horasTrabajadas * it.costeHoraAplicado })} €"),
                DetailField("Saldo de movimientos", "${"%.2f".format(movements.sumOf { if (it.income) it.amountCents else -it.amountCents } / 100.0)} €")))
        ))
    }

    private fun supplier(id: Long, state: HomeWorkspaceState): DetailContent? {
        val supplier = state.suppliers.firstOrNull { it.id == id } ?: return null
        val materials = state.materials.filter { it.proveedorId == id }
        return DetailContent(supplier.nombreEmpresa, "Proveedor", listOf(
            DetailSection("Datos de contacto", listOf(DetailField("Persona de contacto", supplier.personaContacto), DetailField("CIF", supplier.cif),
                DetailField("Teléfono", supplier.telefono), DetailField("Correo", supplier.email))),
            DetailSection("Materiales suministrados", materials.map { DetailField(it.nombreMaterial,
                "${"%.2f".format(it.precioUnitario)} € / ${it.unidadMedida}", CatalogSelection(CatalogKind.MATERIALS, it.id)) }
                .ifEmpty { listOf(DetailField("Materiales", "No hay materiales asociados")) })
        ))
    }

    private fun material(id: Long, state: HomeWorkspaceState): DetailContent? {
        val material = state.materials.firstOrNull { it.id == id } ?: return null
        val supplier = state.suppliers.firstOrNull { it.id == material.proveedorId }
        return DetailContent(material.nombreMaterial, "Material", listOf(DetailSection("Información", listOf(
            DetailField("Unidad de medida", material.unidadMedida), DetailField("Precio unitario", "${"%.2f".format(material.precioUnitario)} €"),
            DetailField("Proveedor", supplier?.nombreEmpresa ?: "Proveedor no disponible"),
            DetailField("Contacto", supplier?.personaContacto ?: "Sin contacto"), DetailField("Teléfono", supplier?.telefono ?: "Sin teléfono"),
            DetailField("Correo", supplier?.email ?: "Sin correo")
        ))))
    }
}
