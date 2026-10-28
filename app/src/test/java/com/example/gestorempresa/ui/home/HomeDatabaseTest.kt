package com.example.gestorempresa.ui.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.gestorempresa.data.local.*
import com.example.gestorempresa.data.local.entities.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HomeDatabaseTest {
    @Test fun migratesVersionOneWithoutLosingEmployees() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val schema = javaClass.classLoader!!.getResourceAsStream("com.example.gestorempresa.data.local.HomeDatabase/1.json")!!.bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        context.openOrCreateDatabase("home_empresa_900003.db", Context.MODE_PRIVATE, null).use { old ->
            val tables = schema.getJSONArray("entities")
            for (i in 0 until tables.length()) {
                val table = tables.getJSONObject(i)
                old.execSQL(table.getString("createSql").replace("\${TABLE_NAME}", table.getString("tableName")))
            }
            val queries = schema.getJSONArray("setupQueries")
            for (i in 0 until queries.length()) old.execSQL(queries.getString(i))
            old.execSQL("INSERT INTO empleados (id,nombre,apellidos,dni,telefono,email,oficio,tipoEmpleado,salarioBase,lugarResidencia,direccionPersonal,fotoUrl,activo) VALUES (41,'Conservado','Pérez','','','','Pintor','FIJO',0,'','',NULL,1)")
            old.execSQL("INSERT INTO unidades_trabajo (id,titulo,tipo,clienteNombre,direccionUbicacion,latitud,longitud,estado,fechaInicio,fechaFin) VALUES (42,'Proyecto conservado','SERVICIO','Cliente','Murcia',NULL,NULL,'ACTIVO','2026-10-24',NULL)")
            old.execSQL("INSERT INTO movimientos (id,projectId,concept,amountCents,income,date) VALUES (7,42,'Cobro conservado',10000,1,'2026-10-24')")
            old.execSQL("INSERT INTO registros_horas (id,unidadTrabajoId,empleadoId,subcontratadoId,fecha,horasTrabajadas,costeHoraAplicado) VALUES (8,42,41,NULL,'2026-10-24',8,10)")
            old.version = 1
        }
        val dao = HomeDatabase.forCompany(context, 900003).homeDao()
        assertEquals("Conservado", dao.employees().first().single().nombre)
        assertEquals("Proyecto conservado", dao.projects().first().single().titulo)
        assertEquals(10000L, dao.movements().first().single().amountCents)
        assertEquals(8.0, dao.hours().first().single().horasTrabajadas, 0.001)
        dao.addSubcontractor(SubcontratadoEntity(nombreComercial = "Externo", personaContacto = "Ana", cifDni = "", telefono = "", email = "", oficio = "Limpieza", tarifaHoraPorDefecto = 10.0))
        assertEquals("Externo", dao.subcontractors().first().single().nombreComercial)
    }
    @Test fun savesAllHomeRecordsAndSeparatesCompanies() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = HomeDatabase.forCompany(context, 900001).homeDao()
        val second = HomeDatabase.forCompany(context, 900002).homeDao()
        first.addEmployee(EmpleadoEntity(nombre = "Ana", apellidos = "Pérez", dni = "", telefono = "", email = "",
            oficio = "Limpieza", tipoEmpleado = "FIJO", salarioBase = 0.0, lugarResidencia = "", direccionPersonal = ""))
        first.addProject(UnidadTrabajoEntity(titulo = "Oficina", tipo = "SERVICIO", clienteNombre = "Cliente", direccionUbicacion = "Murcia", fechaInicio = "2026-10-24"))
        val employeeId = first.employees().first().single().id
        val projectId = first.projects().first().single().id
        first.addMovement(HomeMovement(projectId = projectId, concept = "Cobro", amountCents = 12000, income = true, date = "2026-10-24"))
        first.addHours(RegistroHorasEntity(unidadTrabajoId = projectId, empleadoId = employeeId, fecha = "2026-10-24", horasTrabajadas = 8.0, costeHoraAplicado = 10.0))
        assertEquals(12000L, first.movements().first().single().amountCents)
        assertEquals(employeeId, first.hours().first().single().empleadoId)
        assertTrue(second.employees().first().isEmpty())
        assertTrue(second.projects().first().isEmpty())
        assertTrue(second.movements().first().isEmpty())
        assertTrue(second.hours().first().isEmpty())
        first.addSupplier(ProveedorEntity(nombreEmpresa = "Proveedor", cif = "", telefono = "", email = "", personaContacto = ""))
        val supplierId = first.suppliers().first().single().id
        first.addMaterial(MaterialEntity(proveedorId = supplierId, nombreMaterial = "Cemento", unidadMedida = "saco", precioUnitario = 6.0))
        first.setWorkerMeta(HomeWorkerMeta("EMPLEADO", employeeId, "Palencia"))
        first.assign(HomeAssignment("EMPLEADO", employeeId, projectId))
        first.assign(HomeAssignment("EMPLEADO", employeeId, projectId))
        assertEquals(1, first.assignments().first().size)
        assertEquals("Palencia", first.workerMeta().first().single().workZone)
        first.setEmployeeActive(employeeId, false)
        assertFalse(first.employees().first().single().activo)
        first.addTask(HomeTask(title = "Pendiente", projectId = projectId, dueDate = "2026-10-24"))
        val taskId = first.tasks().first().single().id
        first.setTaskCompleted(taskId, true)
        assertTrue(first.tasks().first().single().completed)
        first.addObligation(HomeObligation(kind = "CURSO", title = "PRL", workerKind = "EMPLEADO", workerId = employeeId, dueDate = "2026-12-01"))
        val obligationId = first.obligations().first().single().id
        first.setObligationResolved(obligationId, true)
        assertTrue(first.obligations().first().single().resolved)
        assertTrue(second.materials().first().isEmpty())
        assertTrue(second.obligations().first().isEmpty())
        assertTrue(second.assignments().first().isEmpty())
        first.unassign("EMPLEADO", employeeId, projectId)
        assertTrue(first.assignments().first().isEmpty())
    }
}
