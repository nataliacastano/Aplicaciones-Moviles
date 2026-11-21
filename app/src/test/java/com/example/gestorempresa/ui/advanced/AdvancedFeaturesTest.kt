package com.example.gestorempresa.ui.advanced

import com.example.gestorempresa.data.local.HomeAssignment
import com.example.gestorempresa.data.local.HomeAttachment
import com.example.gestorempresa.data.local.HomeMovement
import com.example.gestorempresa.data.local.HomeObligation
import com.example.gestorempresa.data.local.HomeTask
import com.example.gestorempresa.data.local.entities.*
import com.example.gestorempresa.ui.advanced.communication.*
import com.example.gestorempresa.ui.advanced.dates.DueKind
import com.example.gestorempresa.ui.advanced.dates.DueStatus
import com.example.gestorempresa.ui.advanced.dates.dueItems
import com.example.gestorempresa.ui.advanced.dates.pendingWarnings
import com.example.gestorempresa.ui.advanced.stats.monthlyValues
import com.example.gestorempresa.ui.advanced.stats.projectCosts
import com.example.gestorempresa.ui.advanced.stats.workersByTrade
import com.example.gestorempresa.ui.home.CatalogKind
import com.example.gestorempresa.ui.home.CatalogSelection
import com.example.gestorempresa.ui.home.HomeWorkspaceState
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class AdvancedFeaturesTest {
    private val today = LocalDate.parse("2026-11-21")
    private fun employee(id: Long, trade: String, active: Boolean = true) = EmpleadoEntity(id = id, nombre = "Juan$id", apellidos = "Pérez",
        dni = "", telefono = "600111222", email = "juan@example.com", oficio = trade, tipoEmpleado = "COMPLETO", salarioBase = 0.0,
        lugarResidencia = "", direccionPersonal = "", activo = active)
    private val project = UnidadTrabajoEntity(id = 10, titulo = "Reforma Pirámide", tipo = "PROYECTO", clienteNombre = "Cliente",
        direccionUbicacion = "Murcia", fechaInicio = "2026-10-01", fechaFin = "2026-11-30")
    private val state = HomeWorkspaceState(
        employees = listOf(employee(1, "albañil"), employee(2, "Albañil"), employee(3, "Pintor"), employee(4, "Pintor", active = false)),
        projects = listOf(project),
        hours = listOf(RegistroHorasEntity(unidadTrabajoId = 10, empleadoId = 1, fecha = "2026-11-03", horasTrabajadas = 8.0, costeHoraAplicado = 15.0),
            RegistroHorasEntity(unidadTrabajoId = 10, empleadoId = 2, fecha = "2026-10-03", horasTrabajadas = 4.0, costeHoraAplicado = 10.0)),
        movements = listOf(HomeMovement(projectId = 10, concept = "Cobro", amountCents = 200_000, income = true, date = "2026-11-10"),
            HomeMovement(projectId = 10, concept = "Alquiler", amountCents = 5_000, income = false, date = "2026-11-11")),
        materials = listOf(MaterialEntity(id = 5, proveedorId = 1, nombreMaterial = "Cemento", unidadMedida = "saco", precioUnitario = 6.0)),
        materialUses = listOf(ConsumoMaterialEntity(unidadTrabajoId = 10, materialId = 5, cantidad = 10.0, precioUnitarioAplicado = 5.5, fecha = "2026-11-05")),
        assignments = listOf(HomeAssignment("EMPLEADO", 1, 10)),
        obligations = listOf(HomeObligation(1, "MEDICO", "Reconocimiento anual", "EMPLEADO", 1, "2026-11-25"),
            HomeObligation(2, "CURSO", "PRL 20h", "EMPLEADO", 1, "2026-11-01"),
            HomeObligation(3, "CURSO", "Altura", "EMPLEADO", 2, "2027-06-01")),
        tasks = listOf(HomeTask(1, "Entregar llaves", 10, "2026-11-22")),
        attachments = listOf(HomeAttachment(1, "EMPLEADO", 1, "DOCUMENTO", "CONTRATO", "Contrato temporal", "/x.pdf", "application/pdf", "2026-01-01", "2026-12-01"))
    )

    @Test fun fillsTemplatePlaceholdersAndKeepsMissingOnes() {
        val text = "El [DÍA] debes acudir a [LUGAR] a las [HORA] en [LUGAR]."
        assertEquals(listOf("DÍA", "LUGAR", "HORA"), placeholdersOf(text))
        assertEquals("El lunes debes acudir a Murcia a las [HORA] en Murcia.", fillTemplate(text, mapOf("DÍA" to "lunes", "LUGAR" to "Murcia", "HORA" to " ")))
    }

    @Test fun preparesWhatsappNumbers() {
        assertEquals("34600111222", whatsappNumber("600 111 222"))
        assertEquals("34600111222", whatsappNumber("+34 600-111-222"))
        assertEquals("34600111222", whatsappNumber("0034600111222"))
        assertNull(whatsappNumber("123"))
    }

    @Test fun suggestsValuesFromAssignmentsAndObligations() {
        val contact = ContactInfo("EMPLEADO", 1, "Juan1 Pérez", "600111222", "")
        val medical = suggestedValues(state, contact, messageTemplates.first { it.title == "Reconocimiento médico" }, today)
        assertEquals("Juan1", medical["NOMBRE"])
        assertEquals("Reforma Pirámide", medical["SERVICIO"])
        assertEquals("Murcia", medical["LUGAR"])
        assertEquals("2026-11-25", medical["FECHA"])
        assertEquals("PRL 20h", suggestedValues(state, contact, messageTemplates.first { it.title == "Curso próximo a caducar" }, today)["CURSO"])
    }

    @Test fun calculatesProjectCostsWithPersonnelMaterialsAndExpenses() {
        val costs = state.projectCosts(10)
        assertEquals(8 * 15.0 + 4 * 10.0, costs.personnel, 0.001)
        assertEquals(55.0, costs.materials, 0.001)
        assertEquals(50.0, costs.otherExpenses, 0.001)
        assertEquals(2000.0, costs.income, 0.001)
        assertEquals(2000.0 - (160.0 + 55.0 + 50.0), costs.result, 0.001)
    }

    @Test fun groupsValuesByMonthAndWorkersByTrade() {
        val months = state.monthlyValues(3, today)
        assertEquals(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11)), months.map { it.month })
        assertEquals(4.0, months[1].hours, 0.001)
        assertEquals(8.0, months[2].hours, 0.001)
        assertEquals(55.0, months[2].materials, 0.001)
        assertEquals(2000.0, months[2].income, 0.001)
        // El oficio no distingue mayúsculas y los inactivos no cuentan.
        assertEquals(listOf("Albañil" to 2, "Pintor" to 1), state.workersByTrade())
    }

    @Test fun collectsDueDatesWithStatus() {
        val items = state.dueItems()
        val contract = items.single { it.kind == DueKind.CONTRATO }
        assertEquals(CatalogSelection(CatalogKind.EMPLOYEES, 1), contract.target)
        assertEquals(DueStatus.PROGRAMADO, contract.status(today, 7))
        assertEquals(DueStatus.PROXIMO, contract.status(today, 15))
        assertEquals(DueStatus.VENCIDO, items.single { it.title == "PRL 20h" }.status(today, 15))
        assertEquals(DueStatus.HECHO, items.single { it.kind == DueKind.INICIO }.status(today, 15))
        assertEquals(items.sortedBy { it.date }, items)
    }

    @Test fun warningsFollowSettings() {
        val all = state.pendingWarnings(today, 15, includeExpiries = true, includeTasks = true).map { it.title }.toSet()
        assertEquals(setOf("Reconocimiento anual", "PRL 20h", "Contrato temporal", "Entregar llaves", "Reforma Pirámide"), all)
        val onlyExpiries = state.pendingWarnings(today, 15, includeExpiries = true, includeTasks = false).map { it.title }.toSet()
        assertEquals(setOf("Reconocimiento anual", "PRL 20h", "Contrato temporal"), onlyExpiries)
        assertTrue(state.pendingWarnings(today, 15, includeExpiries = false, includeTasks = false).isEmpty())
    }

    @Test fun homeSummaryWarnsAboutExpiringAttachments() {
        val alerts = state.summary("Ana", "Empresa", today).listaAlertas
        val contract = alerts.single { it.titulo == "Contrato temporal" }
        assertEquals("calendar", contract.destino)
        assertFalse(contract.urgente)
    }

    @Test fun listsFilesToDeleteWithTheirOwner() {
        val withPhoto = state.copy(employees = state.employees.map { if (it.id == 1L) it.copy(fotoUrl = "/foto.jpg") else it })
        assertEquals(listOf("/x.pdf", "/foto.jpg"), withPhoto.filesOwnedBy(CatalogSelection(CatalogKind.EMPLOYEES, 1)))
        assertTrue(withPhoto.filesOwnedBy(CatalogSelection(CatalogKind.EMPLOYEES, 2)).isEmpty())
    }
}
