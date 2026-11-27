package com.example.gestorempresa.ui.advanced.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.gestorempresa.data.local.HomeDatabase
import com.example.gestorempresa.data.local.HomeWorkerMeta
import com.example.gestorempresa.data.local.HomeZoneLocation
import com.example.gestorempresa.data.local.entities.EmpleadoEntity
import com.example.gestorempresa.ui.home.HomeWorkspaceState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WorkZonesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun storesSeveralZonesWithLocationAndRemovesThem() = runBlocking {
        val dao = HomeDatabase.forCompany(context, 900030).homeDao()
        val id = dao.addEmployee(EmpleadoEntity(nombre = "Ana", apellidos = "Ruiz", dni = "", telefono = "", email = "", oficio = "Limpieza",
            tipoEmpleado = "COMPLETO", salarioBase = 0.0, lugarResidencia = "Murcia", direccionPersonal = "C/ Mayor 1"))
        dao.saveWorkZone(HomeWorkerMeta("EMPLEADO", id, "Murcia centro"), HomeZoneLocation("EMPLEADO", id, "Murcia centro", "Gran Vía, Murcia", 37.99, -1.13))
        dao.saveWorkZone(HomeWorkerMeta("EMPLEADO", id, "Cartagena"), null)
        var state = HomeWorkspaceState(workerMeta = dao.workerMeta().first(), zoneLocations = dao.zoneLocations().first())
        val zones = state.workZonesOf("EMPLEADO", id)
        assertEquals(listOf("Cartagena", "Murcia centro"), zones.map { it.name })
        assertNull(zones[0].location)
        assertEquals("Gran Vía, Murcia", zones[1].location?.address)

        dao.removeWorkZone("EMPLEADO", id, "Murcia centro")
        state = HomeWorkspaceState(workerMeta = dao.workerMeta().first(), zoneLocations = dao.zoneLocations().first())
        assertEquals(listOf("Cartagena"), state.workZonesOf("EMPLEADO", id).map { it.name })
        assertTrue(dao.zoneLocations().first().isEmpty())

        dao.saveWorkZone(HomeWorkerMeta("EMPLEADO", id, "Molina"), HomeZoneLocation("EMPLEADO", id, "Molina", "Polígono La Serreta", 38.08, -1.22))
        dao.deleteEmployeeAndHistory(id)
        assertTrue(dao.zoneLocations().first().isEmpty())
        assertTrue(dao.workerMeta().first().isEmpty())
    }
}
