package com.example.gestorempresa.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gestorempresa.data.local.*
import com.example.gestorempresa.data.local.entities.*
import java.time.LocalDate

@Composable fun ModuleRecordForm(route: String, state: HomeWorkspaceState, vm: HomeWorkspaceViewModel, onBack: () -> Unit) {
    key(route) {
        var title by rememberSaveable { mutableStateOf("") }
        var contact by rememberSaveable { mutableStateOf("") }
        var phone by rememberSaveable { mutableStateOf("") }
        var email by rememberSaveable { mutableStateOf("") }
        var tax by rememberSaveable { mutableStateOf("") }
        var trade by rememberSaveable { mutableStateOf("") }
        var amount by rememberSaveable { mutableStateOf("0") }
        var unit by rememberSaveable { mutableStateOf("unidad") }
        var zone by rememberSaveable { mutableStateOf("") }
        var workerKind by rememberSaveable { mutableStateOf("EMPLEADO") }
        var recordKind by rememberSaveable { mutableStateOf("MEDICO") }
        var workerId by rememberSaveable { mutableStateOf<Long?>(null) }
        var projectId by rememberSaveable { mutableStateOf<Long?>(null) }
        var supplierId by rememberSaveable { mutableStateOf<Long?>(null) }
        var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
        var error by rememberSaveable { mutableStateOf<String?>(null) }
        val saving by vm.saving.collectAsStateWithLifecycle()
        val saveError by vm.saveError.collectAsStateWithLifecycle()
        val workers = if (workerKind == "EMPLEADO") state.employees.filter { it.activo }.map { it.id to "${it.nombre} ${it.apellidos}" }
            else state.subcontractors.filter { it.activo }.map { it.id to it.nombreComercial }
        val titles = mapOf("subcontractor-new" to "Alta de subcontratado", "supplier-new" to "Nuevo proveedor", "material-new" to "Nuevo material",
            "assignment" to "Zona laboral y asignación", "obligation-new" to "Registrar aviso o vencimiento", "task-new" to "Nueva tarea")
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack, enabled = !saving) { Text("Volver") }
            Text(titles[route].orEmpty(), style = MaterialTheme.typography.headlineMedium)
            if (state.loading) CircularProgressIndicator()
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error); Button(onClick = vm::retry) { Text("Reintentar") } }
            when (route) {
                "subcontractor-new", "supplier-new" -> {
                    EntryField("Nombre comercial / empresa *", title, { title = it }, !saving)
                    EntryField("Persona de contacto", contact, { contact = it }, !saving)
                    EntryField("CIF / DNI", tax, { tax = it }, !saving)
                    EntryField("Teléfono", phone, { phone = it }, !saving)
                    EntryField("Correo", email, { email = it }, !saving)
                    if (route == "subcontractor-new") {
                        EntryField("Oficio *", trade, { trade = it }, !saving)
                        EntryField("Tarifa orientativa €/h (0 si no se conoce)", amount, { amount = it }, !saving)
                        Text("La tarifa de cada trabajo se conserva en su parte; esta es solo una referencia.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                "material-new" -> {
                    EntryField("Nombre del material *", title, { title = it }, !saving)
                    SelectionField("Proveedor *", supplierId, state.suppliers.map { it.id to it.nombreEmpresa }, !saving) { supplierId = it }
                    if (state.suppliers.isEmpty()) Text("Primero añade un proveedor desde su listado.")
                    EntryField("Unidad de medida *", unit, { unit = it }, !saving)
                    EntryField("Precio unitario en euros *", amount, { amount = it }, !saving)
                }
                "assignment", "obligation-new" -> {
                    ChoiceField("Tipo de trabajador", workerKind, listOf("EMPLEADO", "SUBCONTRATADO"), !saving) { workerKind = it; workerId = null }
                    SelectionField("Trabajador *", workerId, workers, !saving) { workerId = it }
                    if (workers.isEmpty()) Text("Primero añade un trabajador activo del tipo seleccionado.")
                    if (route == "assignment") {
                        EntryField("Zona habitual de trabajo *", zone, { zone = it }, !saving)
                        Text("Se guarda como zona laboral, independiente de la residencia.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SelectionField("Proyecto (opcional)", projectId, state.projects.filter { it.estado != "FINALIZADO" }.map { it.id to it.titulo }, !saving) { projectId = it }
                    } else {
                        ChoiceField("Tipo de aviso", recordKind, listOf("MEDICO", "CURSO", "DOCUMENTO"), !saving) { recordKind = it }
                        EntryField("Descripción *", title, { title = it }, !saving)
                        EntryField(if (recordKind == "DOCUMENTO") "Fecha límite (opcional, AAAA-MM-DD)" else "Caducidad (AAAA-MM-DD) *", date, { date = it }, !saving)
                        Text("Se avisará con 30 días de antelación; el día del vencimiento se marcará urgente.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                "task-new" -> {
                    EntryField("Descripción de la tarea *", title, { title = it }, !saving)
                    SelectionField("Servicio / proyecto *", projectId, state.projects.filter { it.estado != "FINALIZADO" }.map { it.id to it.titulo }, !saving) { projectId = it }
                    EntryField("Fecha prevista (AAAA-MM-DD) *", date, { date = it }, !saving)
                }
            }
            (error ?: saveError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(enabled = !saving && !state.loading && state.error == null, modifier = Modifier.fillMaxWidth(), onClick = {
                error = null
                val cents = if (amount.trim() == "0" || amount.trim() == "0,00" || amount.trim() == "0.00") 0L else moneyCents(amount)
                when (route) {
                    "subcontractor-new", "supplier-new" -> {
                        if (title.isBlank() || (route == "subcontractor-new" && trade.isBlank())) error = "Completa los campos obligatorios."
                        else if (email.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) error = "Revisa el correo."
                        else if (route == "subcontractor-new" && cents == null) error = "La tarifa debe ser positiva o cero, con hasta dos decimales."
                        else vm.save({
                            if (route == "supplier-new") addSupplier(ProveedorEntity(nombreEmpresa = title.trim(), cif = tax.trim(), telefono = phone.trim(), email = email.trim(), personaContacto = contact.trim()))
                            else addSubcontractor(SubcontratadoEntity(nombreComercial = title.trim(), personaContacto = contact.trim(), cifDni = tax.trim(), telefono = phone.trim(), email = email.trim(), oficio = trade.trim(), tarifaHoraPorDefecto = cents!! / 100.0))
                        }, onBack)
                    }
                    "material-new" -> {
                        if (title.isBlank() || unit.isBlank() || cents == null || state.suppliers.none { it.id == supplierId }) error = "Indica nombre, unidad, proveedor y un precio válido."
                        else vm.save({ addMaterial(MaterialEntity(proveedorId = supplierId!!, nombreMaterial = title.trim(), unidadMedida = unit.trim(), precioUnitario = cents / 100.0)) }, onBack)
                    }
                    "assignment", "obligation-new" -> {
                        val id = workerId
                        if (id == null || workers.none { it.first == id }) error = "Selecciona un trabajador activo."
                        else if (route == "assignment") {
                            if (zone.isBlank()) error = "Indica la zona de trabajo."
                            else if (projectId != null && state.projects.none { it.id == projectId && it.estado != "FINALIZADO" }) error = "Selecciona un proyecto pendiente."
                            else vm.save({ setWorkerMeta(HomeWorkerMeta(workerKind, id, zone.trim())); projectId?.let { assign(HomeAssignment(workerKind, id, it)) } }, onBack)
                        } else if (title.isBlank() || (date.isBlank() && recordKind != "DOCUMENTO") || (date.isNotBlank() && !validDate(date))) error = "Revisa descripción y fecha."
                        else vm.save({ addObligation(HomeObligation(kind = recordKind, title = title.trim(), workerKind = workerKind, workerId = id, dueDate = date.ifBlank { null })) }, onBack)
                    }
                    "task-new" -> {
                        if (title.isBlank() || !validDate(date) || state.projects.none { it.id == projectId && it.estado != "FINALIZADO" }) error = "Completa descripción, proyecto pendiente y fecha válida."
                        else vm.save({ addTask(HomeTask(title = title.trim(), projectId = projectId!!, dueDate = date)) }, onBack)
                    }
                }
            }) { Text(if (saving) "Guardando…" else "Guardar") }
        }
    }
}
