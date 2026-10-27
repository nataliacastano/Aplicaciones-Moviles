package com.example.gestorempresa.ui.home

import com.example.gestorempresa.data.local.HomeMovement
import com.example.gestorempresa.data.local.entities.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class HomeWorkspaceTest {
    @Test fun moneyRejectsInvalidPrecisionAndNegativeAmounts() {
        assertEquals(12345L, moneyCents("123,45"))
        assertNull(moneyCents("1.234"))
        assertNull(moneyCents("-2"))
        assertNull(moneyCents("NaN"))
        assertNull(positiveNumber("Infinity"))
    }

    @Test fun datesRequireRealCalendarDates() {
        assertTrue(validDate("2028-02-29"))
        assertFalse(validDate("2026-02-29"))
        assertFalse(validDate("2026-2-01"))
    }

    @Test fun summaryUsesCurrentMonthAndDistinctWorkersToday() {
        val today = LocalDate.now().toString()
        val state = HomeWorkspaceState(
            projects = listOf(UnidadTrabajoEntity(1, "Limpieza", "SERVICIO", "Cliente", "Murcia", fechaInicio = today, fechaFin = today)),
            hours = listOf(
                RegistroHorasEntity(1, 1, 2, fecha = today, horasTrabajadas = 2.0, costeHoraAplicado = 10.0),
                RegistroHorasEntity(2, 1, 2, fecha = today, horasTrabajadas = 3.0, costeHoraAplicado = 10.0)),
            movements = listOf(
                HomeMovement(1, 1, "Cobro", 12345, true, today),
                HomeMovement(2, 1, "Material", 2345, false, today),
                HomeMovement(3, 1, "Anterior", 99900, true, LocalDate.now().minusMonths(1).toString())),
            loading = false)
        val summary = state.summary("Ana", "Empresa")
        assertEquals("Ana", summary.nombreAdministrador)
        assertEquals(1, summary.trabajadoresEnCampo)
        assertEquals(1, summary.obrasActivas)
        assertEquals(100.0, summary.balance, 0.001)
        assertEquals(1, summary.listaAlertas.size)
    }
}
