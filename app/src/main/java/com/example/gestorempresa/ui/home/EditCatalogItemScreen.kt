package com.example.gestorempresa.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gestorempresa.data.local.entities.MaterialEntity
import com.example.gestorempresa.data.local.entities.ProveedorEntity
import com.example.gestorempresa.data.local.entities.SubcontratadoEntity
import com.example.gestorempresa.data.local.entities.UnidadTrabajoEntity

@Composable
fun EditCatalogItemScreen(selection: CatalogSelection, state: HomeWorkspaceState, vm: HomeWorkspaceViewModel, onBack: () -> Unit) {
    val project = state.projects.firstOrNull { it.id == selection.id }
    val subcontractor = state.subcontractors.firstOrNull { it.id == selection.id }
    val supplier = state.suppliers.firstOrNull { it.id == selection.id }
    val material = state.materials.firstOrNull { it.id == selection.id }
    var title by remember(selection) { mutableStateOf(project?.titulo ?: subcontractor?.nombreComercial ?: supplier?.nombreEmpresa ?: material?.nombreMaterial.orEmpty()) }
    var client by remember(selection) { mutableStateOf(project?.clienteNombre.orEmpty()) }
    var location by remember(selection) { mutableStateOf(project?.direccionUbicacion.orEmpty()) }
    var startDate by remember(selection) { mutableStateOf(project?.fechaInicio.orEmpty()) }
    var endDate by remember(selection) { mutableStateOf(project?.fechaFin.orEmpty()) }
    var type by remember(selection) { mutableStateOf(project?.tipo ?: "SERVICIO") }
    var contact by remember(selection) { mutableStateOf(subcontractor?.personaContacto ?: supplier?.personaContacto.orEmpty()) }
    var tax by remember(selection) { mutableStateOf(subcontractor?.cifDni ?: supplier?.cif.orEmpty()) }
    var phone by remember(selection) { mutableStateOf(subcontractor?.telefono ?: supplier?.telefono.orEmpty()) }
    var email by remember(selection) { mutableStateOf(subcontractor?.email ?: supplier?.email.orEmpty()) }
    var trade by remember(selection) { mutableStateOf(subcontractor?.oficio.orEmpty()) }
    var amount by remember(selection) { mutableStateOf((subcontractor?.tarifaHoraPorDefecto ?: material?.precioUnitario)?.toString().orEmpty()) }
    var unit by remember(selection) { mutableStateOf(material?.unidadMedida.orEmpty()) }
    var supplierId by remember(selection) { mutableStateOf<Long?>(material?.proveedorId) }
    var error by remember(selection) { mutableStateOf<String?>(null) }
    val saving by vm.saving.collectAsStateWithLifecycle()
    val saveError by vm.saveError.collectAsStateWithLifecycle()
    val kind = selection.kind
    val recordExists = when (kind) {
        CatalogKind.PROJECTS -> project != null
        CatalogKind.SUBCONTRACTORS -> subcontractor != null
        CatalogKind.SUPPLIERS -> supplier != null
        CatalogKind.MATERIALS -> material != null
        CatalogKind.EMPLOYEES -> false
    }
    val screenTitle = when (kind) {
        CatalogKind.PROJECTS -> "Editar servicio o proyecto"
        CatalogKind.SUBCONTRACTORS -> "Editar subcontratado"
        CatalogKind.SUPPLIERS -> "Editar proveedor"
        CatalogKind.MATERIALS -> "Editar material"
        CatalogKind.EMPLOYEES -> "Editar registro"
    }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack, enabled = !saving) { Text("Volver sin guardar") }
        Text(screenTitle, style = MaterialTheme.typography.headlineMedium)
        if (!recordExists) Text("No se encuentra este registro.", color = MaterialTheme.colorScheme.error)
        when (kind) {
            CatalogKind.PROJECTS -> {
                ChoiceField("Tipo", type, listOf("SERVICIO", "PROYECTO"), !saving) { type = it }
                EntryField(if (type == "SERVICIO") "Nombre del servicio o local *" else "Nombre del proyecto *", title, { title = it }, !saving)
                if (type == "PROYECTO") EntryField("Cliente (opcional)", client, { client = it }, !saving)
                EntryField("Ubicación o dirección *", location, { location = it }, !saving)
                EntryField(if (type == "SERVICIO") "Fecha de apertura (AAAA-MM-DD) *" else "Fecha de inicio (AAAA-MM-DD) *", startDate, { startDate = it }, !saving)
                if (type == "PROYECTO") EntryField("Fecha de fin prevista (opcional)", endDate, { endDate = it }, !saving)
            }
            CatalogKind.SUBCONTRACTORS -> {
                EntryField("Nombre comercial *", title, { title = it }, !saving)
                EntryField("Persona de contacto", contact, { contact = it }, !saving)
                EntryField("CIF / DNI", tax, { tax = it }, !saving)
                EntryField("Teléfono", phone, { phone = it }, !saving)
                EntryField("Correo electrónico", email, { email = it }, !saving)
                EntryField("Oficio *", trade, { trade = it }, !saving)
                EntryField("Tarifa por hora (€)", amount, { amount = it }, !saving)
            }
            CatalogKind.SUPPLIERS -> {
                EntryField("Nombre de empresa *", title, { title = it }, !saving)
                EntryField("Persona de contacto", contact, { contact = it }, !saving)
                EntryField("CIF", tax, { tax = it }, !saving)
                EntryField("Teléfono", phone, { phone = it }, !saving)
                EntryField("Correo electrónico", email, { email = it }, !saving)
            }
            CatalogKind.MATERIALS -> {
                EntryField("Nombre del material *", title, { title = it }, !saving)
                SelectionField("Proveedor *", supplierId, state.suppliers.map { it.id to it.nombreEmpresa }, !saving) { supplierId = it }
                EntryField("Unidad de medida *", unit, { unit = it }, !saving)
                EntryField("Precio unitario (€) *", amount, { amount = it }, !saving)
            }
            CatalogKind.EMPLOYEES -> Unit
        }
        (error ?: saveError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(enabled = recordExists && !saving, modifier = Modifier.fillMaxWidth(), onClick = {
            error = null
            val number = amount.replace(',', '.').toDoubleOrNull()
            when (kind) {
                CatalogKind.PROJECTS -> {
                    if (title.isBlank() || location.isBlank() || !validDate(startDate) || (type == "PROYECTO" && endDate.isNotBlank() && (!validDate(endDate) || endDate < startDate))) error = "Revisa el nombre, la ubicación y las fechas."
                    else vm.save({ updateProject(project!!.copy(titulo = title.trim(), tipo = type, clienteNombre = if (type == "PROYECTO") client.trim() else "",
                        direccionUbicacion = location.trim(), fechaInicio = startDate, fechaFin = if (type == "PROYECTO") endDate.ifBlank { null } else null)) }, onBack)
                }
                CatalogKind.SUBCONTRACTORS -> {
                    if (title.isBlank() || trade.isBlank() || number == null || !number.isFinite() || number < 0) error = "Revisa el nombre, el oficio y la tarifa."
                    else vm.save({ updateSubcontractor(subcontractor!!.copy(nombreComercial = title.trim(), personaContacto = contact.trim(), cifDni = tax.trim(),
                        telefono = phone.trim(), email = email.trim(), oficio = trade.trim(), tarifaHoraPorDefecto = number)) }, onBack)
                }
                CatalogKind.SUPPLIERS -> {
                    if (title.isBlank()) error = "Indica el nombre de la empresa."
                    else vm.save({ updateSupplier(supplier!!.copy(nombreEmpresa = title.trim(), personaContacto = contact.trim(), cif = tax.trim(), telefono = phone.trim(), email = email.trim())) }, onBack)
                }
                CatalogKind.MATERIALS -> {
                    if (title.isBlank() || unit.isBlank() || supplierId == null || number == null || !number.isFinite() || number <= 0) error = "Completa nombre, proveedor, unidad y precio válido."
                    else vm.save({ updateMaterial(material!!.copy(nombreMaterial = title.trim(), proveedorId = supplierId!!, unidadMedida = unit.trim(), precioUnitario = number)) }, onBack)
                }
                CatalogKind.EMPLOYEES -> Unit
            }
        }) { Text(if (saving) "Guardando…" else "Guardar cambios") }
    }
}
