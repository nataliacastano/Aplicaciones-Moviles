package com.example.gestorempresa

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.gestorempresa.data.local.HomeDatabase
import com.example.gestorempresa.ui.employees.*
import com.example.gestorempresa.ui.home.*
import com.example.gestorempresa.data.local.entities.*
import com.example.gestorempresa.data.local.HomeWorkerMeta
import com.example.gestorempresa.data.local.HomeAssignment
import com.example.gestorempresa.domain.auth.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class HomeModuleUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun authenticatedWorkspaceNavigatesAcrossCatalogsAndHandsOffDetailId() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dao = HomeDatabase.forCompany(context, 800004).homeDao()
        runBlocking {
            dao.addSubcontractor(SubcontratadoEntity(nombreComercial = "Electricidad Norte", personaContacto = "Ana", cifDni = "", telefono = "", email = "", oficio = "Electricista", tarifaHoraPorDefecto = 25.0))
            dao.addSupplier(ProveedorEntity(nombreEmpresa = "Suministros Sur", cif = "", telefono = "", email = "", personaContacto = "Pedro"))
            val supplierId = dao.suppliers().first().single().id
            dao.addMaterial(MaterialEntity(proveedorId = supplierId, nombreMaterial = "Cable azul", unidadMedida = "metro", precioUnitario = 2.0))
        }
        val selected = AtomicReference<CatalogSelection>()
        val session = SesionUsuario(Usuario(1, "Ana", "Pérez", "prueba@example.test", 800004), Empresa(800004, "Empresa de pruebas", "Servicios"), Long.MAX_VALUE)
        compose.setContent { HomeListsContent(session = session, onOpenDetails = { selected.set(it) }) }
        listOf("Subcontratación" to "Electricidad Norte", "Proveedores" to "Suministros Sur", "Materiales" to "Cable azul").forEach { (menu, item) ->
            compose.onNodeWithTag("module-menu").performScrollToNode(hasText(menu))
            compose.onNodeWithText(menu).performClick()
            compose.onNodeWithTag("catalog-list").performScrollToNode(hasText(item))
            compose.onNodeWithText(item).assertIsDisplayed()
        }
        compose.onNodeWithText("Cable azul").performClick()
        compose.runOnIdle { assertEquals(CatalogSelection(CatalogKind.MATERIALS, 1), selected.get()) }
    }

    @Test fun emptyEmployeeListUsesHomeStyleAndExplainsHowToAdd() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, HomeDatabase::class.java).build()
        val vm = HomeWorkspaceViewModel(db.homeDao())
        compose.setContent {
            HomeModuleTheme {
                CatalogScreen(CatalogKind.EMPLOYEES, HomeWorkspaceState(loading = false), vm, {}, {}, {})
            }
        }
        compose.onNodeWithTag("catalog-list").performScrollToNode(hasText("Todavía no hay registros. Pulsa + Añadir para crear el primero."))
        compose.onNodeWithText("Todavía no hay registros. Pulsa + Añadir para crear el primero.").assertIsDisplayed()
        val outputDirectory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val output = File(outputDirectory ?: context.getExternalFilesDir(null)!!.absolutePath, "empleados-vacio.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        db.close()
    }

    @Test fun catalogSearchPassesEntityTypeAndStableId() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, HomeDatabase::class.java).build()
        val vm = HomeWorkspaceViewModel(db.homeDao())
        val selection = AtomicReference<CatalogSelection>()
        val fixture = HomeWorkspaceState(loading = false, employees = listOf(
            EmpleadoEntity(42, "Ángela", "Pérez", "", "", "", "Electricista", "FIJO", 0.0, "Madrid", ""),
            EmpleadoEntity(43, "Juan", "López", "", "", "", "Pintor", "PARCIAL", 0.0, "Madrid", "")),
            workerMeta = listOf(HomeWorkerMeta("EMPLEADO", 42, "Palencia")), assignments = listOf(HomeAssignment("EMPLEADO", 42, 1)))
        try {
            compose.setContent { HomeModuleTheme { CatalogScreen(CatalogKind.EMPLOYEES, fixture, vm, {}, {}, { selection.set(it) }) } }
            compose.onNodeWithText("Buscar en empleados").performScrollTo().performTextInput("PEREZ angela")
            compose.onNodeWithTag("catalog-list").performScrollToNode(hasText("Ángela Pérez"))
            compose.onNodeWithText("Ángela Pérez").performClick()
            compose.runOnIdle { assertEquals(CatalogSelection(CatalogKind.EMPLOYEES, 42), selection.get()) }
            compose.onNodeWithText("Zona laboral: Palencia").assertExists()
            compose.onNodeWithText("Limpiar búsqueda y filtros").performScrollTo().performClick()
            compose.onNodeWithTag("catalog-list").performScrollToNode(hasText("Juan López"))
            compose.onNodeWithText("Juan López").assertIsDisplayed()
        } finally { db.close() }
    }

    @Test fun supplierFormPersistsAndReturnsAfterSave() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, HomeDatabase::class.java).build()
        val vm = HomeWorkspaceViewModel(db.homeDao())
        val saved = AtomicBoolean(false)
        try {
            compose.setContent { HomeModuleTheme { ModuleRecordForm("supplier-new", HomeWorkspaceState(loading = false), vm) { saved.set(true) } } }
            compose.onNodeWithText("Nombre comercial / empresa *").performScrollTo().performTextInput("Materiales Murcia")
            compose.onNodeWithText("Guardar").performScrollTo().performClick()
            compose.waitUntil(10000) { saved.get() }
            assertEquals("Materiales Murcia", runBlocking { db.homeDao().suppliers().first().single().nombreEmpresa })
        } finally { db.close() }
    }

    @Test fun employeeFormValidatesAndPersists() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, HomeDatabase::class.java).build()
        val vm = HomeWorkspaceViewModel(db.homeDao())
        val saved = AtomicBoolean(false)
        try {
            compose.setContent { HomeModuleTheme { HomeEntryForm("employee-new", vm, HomeWorkspaceState(loading = false)) { saved.set(true) } } }
            compose.onNodeWithText("Guardar").performScrollTo().performClick()
            compose.onNodeWithText("Completa nombre, apellidos y oficio.").assertExists()
            compose.onNodeWithText("Nombre *").performScrollTo().performTextInput("Ana")
            compose.onNodeWithText("Apellidos *").performScrollTo().performTextInput("Pérez")
            compose.onNodeWithText("Oficio *").performScrollTo().performTextInput("Electricista")
            compose.onNodeWithText("Guardar").performScrollTo().performClick()
            compose.waitUntil(10000) { saved.get() }
            val employees = runBlocking { db.homeDao().employees().first() }
            assertEquals("Ana", employees.single().nombre)
        } finally { db.close() }
    }

    @Test fun homeQuickActionsAndAlertsInvokeDestinations() {
        val actions = mutableSetOf<String>()
        compose.setContent {
            HomeModuleTheme {
                Box(Modifier.safeDrawingPadding()) {
                HomeScreen(stateOverride = HomeUiState(nombreAdministrador = "Ana", listaAlertas = listOf(HomeAlert(1, "Fin próximo", "Mañana", AlertType.GENERAL))),
                    onNuevaObra = { actions.add("obra") }, onAltaEmpleado = { actions.add("empleado") },
                    onMovimiento = { actions.add("movimiento") }, onParteDiario = { actions.add("parte") },
                    onAlertClick = { actions.add("alerta") })
                }
            }
        }
        listOf("+ Nueva obra", "+ Alta empleado", "Gasto / Ingreso", "Parte diario", "Ver").forEach {
            if (it == "Ver") compose.onNodeWithTag("home-list").performScrollToNode(hasText(it))
            compose.onNodeWithText(it).performScrollTo()
            if (it == "+ Nueva obra") {
                val dir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")!!
                File(dir).mkdirs()
                File(dir, "home-acciones.png").outputStream().use { output -> compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output) }
                File(dir, "home-semantics.txt").writeText(compose.onRoot().printToString())
            }
            compose.onNodeWithText(it).performClick()
        }
        compose.runOnIdle { assertEquals(setOf("obra", "empleado", "movimiento", "parte", "alerta"), actions) }
    }
}
