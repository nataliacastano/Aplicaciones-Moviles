package com.example.gestorempresa.ui.advanced

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.gestorempresa.data.local.HomeAttachment
import com.example.gestorempresa.data.local.entities.ConsumoMaterialEntity
import com.example.gestorempresa.data.media.AttachmentStorage
import com.example.gestorempresa.ui.advanced.communication.ContactCard
import com.example.gestorempresa.ui.advanced.communication.ContactInfo
import com.example.gestorempresa.ui.advanced.documents.DocumentsCard
import com.example.gestorempresa.ui.advanced.documents.writeProjectReport
import com.example.gestorempresa.ui.advanced.map.ProjectMapCard
import com.example.gestorempresa.ui.advanced.media.PhotosCard
import com.example.gestorempresa.ui.advanced.media.WorkerPhotoHeader
import com.example.gestorempresa.ui.home.*
import java.time.LocalDate

/** Tipo de propietario con el que se guardan los adjuntos de cada ficha. */
fun CatalogKind.ownerKind(): String = when (this) {
    CatalogKind.EMPLOYEES -> "EMPLEADO"
    CatalogKind.SUBCONTRACTORS -> "SUBCONTRATADO"
    CatalogKind.PROJECTS -> "PROYECTO"
    CatalogKind.SUPPLIERS -> "PROVEEDOR"
    CatalogKind.MATERIALS -> "MATERIAL"
}

/** Archivos que hay que borrar del móvil al eliminar un registro (fotos y documentos). */
fun HomeWorkspaceState.filesOwnedBy(selection: CatalogSelection): List<String> {
    val owner = selection.kind.ownerKind()
    val own = attachments.filter { it.ownerKind == owner && it.ownerId == selection.id }.map { it.path }
    val photo = when (selection.kind) {
        CatalogKind.EMPLOYEES -> employees.firstOrNull { it.id == selection.id }?.fotoUrl
        CatalogKind.SUBCONTRACTORS -> subcontractors.firstOrNull { it.id == selection.id }?.fotoUrl
        else -> null
    }
    val supplierMaterials = if (selection.kind == CatalogKind.SUPPLIERS) {
        val ids = materials.filter { it.proveedorId == selection.id }.map { it.id }.toSet()
        attachments.filter { it.ownerKind == "MATERIAL" && it.ownerId in ids }.map { it.path }
    } else emptyList()
    return own + listOfNotNull(photo) + supplierMaterials
}

/** Parte superior de la ficha: foto (cámara/galería) y botones de comunicación (WhatsApp/Email/llamar). */
@Composable
fun AdvancedDetailHeader(selection: CatalogSelection, state: HomeWorkspaceState, workspace: HomeWorkspaceViewModel, storage: AttachmentStorage) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (selection.kind) {
            CatalogKind.EMPLOYEES -> state.employees.firstOrNull { it.id == selection.id }?.let { e ->
                WorkerPhotoHeader("EMPLEADO", e.id, e.fotoUrl, "${e.nombre.take(1)}${e.apellidos.take(1)}".uppercase(), storage, workspace)
                ContactCard(ContactInfo("EMPLEADO", e.id, "${e.nombre} ${e.apellidos}", e.telefono, e.email), state)
            }
            CatalogKind.SUBCONTRACTORS -> state.subcontractors.firstOrNull { it.id == selection.id }?.let { s ->
                WorkerPhotoHeader("SUBCONTRATADO", s.id, s.fotoUrl, s.nombreComercial.take(2).uppercase(), storage, workspace)
                ContactCard(ContactInfo("SUBCONTRATADO", s.id, s.personaContacto.ifBlank { s.nombreComercial }, s.telefono, s.email), state)
            }
            CatalogKind.SUPPLIERS -> state.suppliers.firstOrNull { it.id == selection.id }?.let { p ->
                ContactCard(ContactInfo("PROVEEDOR", p.id, p.personaContacto.ifBlank { p.nombreEmpresa }, p.telefono, p.email), state)
            }
            else -> Unit
        }
    }
}

/** Parte inferior de la ficha: mapa, fotos, materiales usados y documentos (PDF). */
@Composable
fun AdvancedDetailFooter(selection: CatalogSelection, state: HomeWorkspaceState, workspace: HomeWorkspaceViewModel, storage: AttachmentStorage,
    companyName: String, onOpenAttachment: (HomeAttachment) -> Unit, onOpenMap: () -> Unit, onPickLocation: (Long) -> Unit) {
    val owner = selection.kind.ownerKind()
    val saveError by workspace.saveError.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (selection.kind == CatalogKind.PROJECTS) {
            state.projects.firstOrNull { it.id == selection.id }?.let { project ->
                ProjectMapCard(project, onOpenMap) { onPickLocation(project.id) }
                MaterialUsageCard(project.id, state, workspace)
                PhotosCard(owner, project.id, state.attachments, storage, workspace, onOpenAttachment, "📷 Fotos de la obra y trabajos realizados")
            }
        }
        if (selection.kind == CatalogKind.MATERIALS) PhotosCard(owner, selection.id, state.attachments, storage, workspace, onOpenAttachment)
        DocumentsCard(owner, selection.id, state, storage, workspace, onOpenAttachment, extraActions = {
            if (selection.kind == CatalogKind.PROJECTS) {
                val saving by workspace.saving.collectAsState()
                OutlinedButton(enabled = !saving, onClick = {
                    val snapshot = state
                    workspace.save({
                        val path = storage.saveGenerated("informe_proyecto_${selection.id}", "pdf") { out ->
                            writeProjectReport(snapshot, selection.id, companyName, out)
                        }
                        val title = snapshot.projects.firstOrNull { it.id == selection.id }?.titulo ?: "Proyecto"
                        addAttachment(HomeAttachment(ownerKind = owner, ownerId = selection.id, type = "DOCUMENTO", category = "INFORME",
                            name = "Informe de costes · $title · ${LocalDate.now()}", path = path, mimeType = "application/pdf",
                            createdAt = LocalDate.now().toString()))
                    }) {}
                }) { Text("Generar informe PDF") }
            }
        })
    }
}

/** Materiales usados en un servicio o proyecto, con el precio que tenían ese día. */
@Composable
private fun MaterialUsageCard(projectId: Long, state: HomeWorkspaceState, workspace: HomeWorkspaceViewModel) {
    val uses = state.materialUses.filter { it.unidadTrabajoId == projectId }
    val saving by workspace.saving.collectAsState()
    var adding by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("🧱 Materiales utilizados", style = MaterialTheme.typography.titleMedium)
            if (uses.isEmpty()) Text("No se han registrado materiales.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            uses.forEach { use ->
                val material = state.materials.firstOrNull { it.id == use.materialId }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(material?.nombreMaterial ?: "Material eliminado", style = MaterialTheme.typography.bodyLarge)
                        Text("${use.fecha} · ${use.cantidad} ${material?.unidadMedida.orEmpty()} × ${"%.2f".format(use.precioUnitarioAplicado)} € = ${"%.2f".format(use.cantidad * use.precioUnitarioAplicado)} €",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(enabled = !saving, onClick = { workspace.save({ deleteMaterialUse(use.id) }) {} }) { Text("Quitar") }
                }
            }
            if (uses.isNotEmpty()) Text("Coste de materiales: ${"%.2f".format(uses.sumOf { it.cantidad * it.precioUnitarioAplicado })} €",
                style = MaterialTheme.typography.titleSmall)
            Button(enabled = !saving && state.materials.isNotEmpty(), onClick = { adding = true }) { Text("Registrar material usado") }
            if (state.materials.isEmpty()) Text("Primero da de alta materiales desde Proveedores.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (adding) {
        var materialId by rememberSaveable { mutableStateOf<Long?>(null) }
        var quantity by rememberSaveable { mutableStateOf("") }
        var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
        var error by remember { mutableStateOf<String?>(null) }
        val selected = state.materials.firstOrNull { it.id == materialId }
        AlertDialog(onDismissRequest = { if (!saving) adding = false }, title = { Text("Material usado") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SelectionField("Material", materialId, state.materials.map { it.id to "${it.nombreMaterial} (${"%.2f".format(it.precioUnitario)} €/${it.unidadMedida})" }, !saving) { materialId = it }
                    OutlinedTextField(quantity, { quantity = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
                        label = { Text("Cantidad${selected?.let { " (${it.unidadMedida})" }.orEmpty()}") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(date, { date = it }, label = { Text("Fecha (AAAA-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(enabled = !saving, onClick = {
                    val amount = quantity.replace(',', '.').toDoubleOrNull()
                    when {
                        selected == null -> error = "Elige un material."
                        amount == null || amount <= 0 || !amount.isFinite() -> error = "Indica una cantidad mayor que 0."
                        !validDate(date) -> error = "La fecha debe tener el formato AAAA-MM-DD."
                        else -> workspace.save({
                            addMaterialUse(ConsumoMaterialEntity(unidadTrabajoId = projectId, materialId = selected.id, cantidad = amount,
                                precioUnitarioAplicado = selected.precioUnitario, fecha = date))
                        }) { adding = false }
                    }
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(enabled = !saving, onClick = { adding = false }) { Text("Cancelar") } })
    }
}
