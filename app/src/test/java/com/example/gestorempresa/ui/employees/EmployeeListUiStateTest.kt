package com.example.gestorempresa.ui.employees

import com.example.gestorempresa.data.local.entities.EmpleadoEntity
import org.junit.Assert.*
import org.junit.Test

class EmployeeListUiStateTest {
    private fun employee(id: Long, name: String, trade: String, contract: String, active: Boolean = true) =
        EmpleadoEntity(id, name, "Pérez", "", "", "", trade, contract, 0.0, "Murcia", "", activo = active)

    private val employees = listOf(
        employee(1, "Ángela", "Electricista", "FIJO"),
        employee(2, "Juan", "Albañil", "PARCIAL"),
        employee(3, "Ana", "Electricista", "PARCIAL"),
        employee(4, "Inactivo", "Electricista", "FIJO", false)
    )

    @Test fun searchIgnoresAccentsCaseAndWordOrder() {
        val state = EmployeeListUiState(employees, query = " PEREZ   angela ")
        assertEquals(listOf(1L), state.visibleEmployees.map { it.id })
    }

    @Test fun combinesSearchTradeAndContract() {
        val state = EmployeeListUiState(employees, query = "perez", trade = "Electricista", contract = "PARCIAL")
        assertEquals(listOf(3L), state.visibleEmployees.map { it.id })
    }

    @Test fun excludesInactiveAndDoesNotSearchResidenceAsWorkplace() {
        assertEquals(3, EmployeeListUiState(employees).visibleEmployees.size)
        assertTrue(EmployeeListUiState(employees, query = "Murcia").visibleEmployees.isEmpty())
    }

    @Test fun noMatchesProducesEmptyList() {
        assertTrue(EmployeeListUiState(employees, trade = "Fontanero").visibleEmployees.isEmpty())
    }
}
