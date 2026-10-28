package com.example.gestorempresa.ui.home

import org.junit.Assert.*
import org.junit.Test
import com.example.gestorempresa.data.local.*
import java.time.LocalDate

class ModuleCatalogTest {
    private val rows = listOf(
        CatalogRow(1, "Ángela Pérez", "Electricista", "", trade = "Electricista", type = "FIJO", status = "ACTIVO", zone = "Murcia", projectIds = setOf(5)),
        CatalogRow(2, "Ana López", "Electricista", "", trade = "Electricista", type = "PARCIAL", status = "INACTIVO", zone = "Palencia", projectIds = setOf(6)),
        CatalogRow(3, "Juan Pérez", "Pintor", "", trade = "Pintor", type = "FIJO", status = "ACTIVO", zone = "Murcia"))

    @Test fun combinesTextTradeTypeStateZoneAndProject() {
        val filter = CatalogFilters("PEREZ angela", "Electricista", "FIJO", "ACTIVO", "Murcia", 5)
        assertEquals(listOf(1L), filterCatalog(rows, filter).map { it.id })
        assertTrue(filterCatalog(rows, filter.copy(projectId = 6)).isEmpty())
    }
    @Test fun inactiveWorkersRemainSearchableAndClearRestoresAll() {
        assertEquals(2L, filterCatalog(rows, CatalogFilters(status = "INACTIVO")).single().id)
        assertEquals(3, filterCatalog(rows, CatalogFilters()).size)
    }
    @Test fun dateRangeIsInclusiveAndInvalidRangeIsRejected() {
        val projects = listOf(CatalogRow(1, "A", "", "", startDate = "2026-01-10", status = "PAUSADO"), CatalogRow(2, "B", "", "", startDate = "2026-02-10", status = "FINALIZADO"))
        assertEquals(1L, filterCatalog(projects, CatalogFilters(fromDate = "2026-01-10", toDate = "2026-01-10")).single().id)
        assertEquals(1L, filterCatalog(projects, CatalogFilters(status = "PENDIENTE")).single().id)
        assertTrue(filterCatalog(projects, CatalogFilters(fromDate = "2026-02-30")).isEmpty())
        assertTrue(filterCatalog(projects, CatalogFilters(fromDate = "2026-03-01", toDate = "2026-01-01")).isEmpty())
    }
    @Test fun filtersMaterialsBySupplierAndUnit() {
        val materials = listOf(CatalogRow(1, "Pintura", "", "Proveedor", type = "litro", supplierId = 7), CatalogRow(2, "Pintura", "", "Otro", type = "litro", supplierId = 8))
        assertEquals(1L, filterCatalog(materials, CatalogFilters(type = "litro", supplierId = 7)).single().id)
    }
    @Test fun summarySeparatesUpcomingExpiredAndResolvedRecords() {
        val state = HomeWorkspaceState(loading = false, obligations = listOf(
            HomeObligation(1, "MEDICO", "Revisión", "EMPLEADO", 1, "2026-10-24"),
            HomeObligation(2, "CURSO", "PRL", "EMPLEADO", 1, "2026-11-10"),
            HomeObligation(3, "CURSO", "Futuro", "EMPLEADO", 1, "2028-01-01"),
            HomeObligation(4, "DOCUMENTO", "Contrato", "EMPLEADO", 1),
            HomeObligation(5, "MEDICO", "Archivado", "EMPLEADO", 1, "2026-01-01", true)),
            tasks = listOf(HomeTask(1, "Hoy", 1, "2026-10-24"), HomeTask(2, "Terminada", 1, "2026-01-01", true)))
        val summary = state.summary("Ana", "Empresa", LocalDate.parse("2026-10-24"))
        assertEquals(1, summary.medicosProximos)
        assertEquals(1, summary.cursosProximos)
        assertEquals(1, summary.documentosPendientes)
        assertEquals(1, summary.tareasPendientes)
        assertEquals(2, summary.listaAlertas.count { it.urgente })
        assertFalse(summary.listaAlertas.any { it.titulo == "Archivado" || it.titulo == "Futuro" })
    }
}
