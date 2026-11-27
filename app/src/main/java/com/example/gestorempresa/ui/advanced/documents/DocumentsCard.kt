package com.example.gestorempresa.ui.advanced.documents

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.gestorempresa.data.local.HomeAttachment
import com.example.gestorempresa.data.media.AttachmentStorage
import com.example.gestorempresa.data.media.PdfCheck
import com.example.gestorempresa.data.media.checkPdf
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import com.example.gestorempresa.ui.home.ChoiceField
import com.example.gestorempresa.ui.home.HomeWorkspaceState
import com.example.gestorempresa.ui.home.HomeWorkspaceViewModel
import com.example.gestorempresa.ui.advanced.forms.DateField
import com.example.gestorempresa.ui.advanced.forms.dateError
import java.time.LocalDate

/** Tipos de documento que se pueden adjuntar y su nombre visible. */
val documentCategories = linkedMapOf(
    "CONTRATO" to "Contrato",
    "CERTIFICADO" to "Certificado",
    "CURSO" to "Curso / formación",
    "MEDICO" to "Reconocimiento médico",
    "LABORAL" to "Documentación laboral",
    "PRESUPUESTO" to "Presupuesto",
    "FACTURA" to "Factura / justificante de gasto",
    "INFORME" to "Informe",
    "OTRO" to "Otro"
)

fun categoryLabel(code: String): String = documentCategories[code] ?: code

/** Los documentos que tienen sentido para cada tipo de ficha. */
fun categoriesFor(ownerKind: String): List<String> = when (ownerKind) {
    "EMPLEADO", "SUBCONTRATADO" -> listOf("CONTRATO", "CERTIFICADO", "CURSO", "MEDICO", "LABORAL", "OTRO")
    "PROYECTO" -> listOf("PRESUPUESTO", "FACTURA", "CONTRATO", "CERTIFICADO", "INFORME", "OTRO")
    "PROVEEDOR" -> listOf("FACTURA", "PRESUPUESTO", "CONTRATO", "OTRO")
    else -> listOf("CERTIFICADO", "FACTURA", "OTRO")
}

/** En empleados y subcontratados solo se admiten PDF (documentación laboral). */
fun acceptsOnlyPdf(ownerKind: String): Boolean = ownerKind == "EMPLEADO" || ownerKind == "SUBCONTRATADO"

/**
 * Tarjeta de documentación de una ficha: lista los documentos adjuntos y permite consultarlos,
 * sustituirlos, borrarlos y adjuntar nuevos con su fecha de caducidad.
 * En empleados y subcontratados el selector solo muestra PDF y se valida el contenido del archivo.
 */
@Composable
fun DocumentsCard(ownerKind: String, ownerId: Long, state: HomeWorkspaceState, storage: AttachmentStorage,
    workspace: HomeWorkspaceViewModel, onOpen: (HomeAttachment) -> Unit, extraActions: @Composable () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pdfOnly = acceptsOnlyPdf(ownerKind)
    val documents = state.attachments.filter { it.ownerKind == ownerKind && it.ownerId == ownerId && it.type == "DOCUMENTO" }
    val saving by workspace.saving.collectAsState()
    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingName by rememberSaveable { mutableStateOf("") }
    var replacingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickError by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<HomeAttachment?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) { replacingId = null; return@rememberLauncherForActivityResult } // Cancelado: no es un error.
        scope.launch {
            pickError = null
            val check = if (pdfOnly) checkPdf(context, uri) else PdfCheck.Valid("Documento", -1)
            when (check) {
                is PdfCheck.Invalid -> { pickError = check.reason; replacingId = null }
                is PdfCheck.Valid -> {
                    val target = documents.firstOrNull { it.id == replacingId }
                    replacingId = null
                    if (target != null) workspace.save({
                        val stored = storage.importUri(uri, ownerKind.lowercase())
                        try { updateAttachmentFile(target.id, stored.path, stored.mimeType) } catch (e: Exception) { storage.delete(stored.path); throw e }
                        storage.delete(target.path)
                    }) {}
                    else { pendingName = check.fileName; pendingUri = uri.toString() }
                }
            }
        }
    }
    val mimeTypes = if (pdfOnly) arrayOf("application/pdf") else arrayOf("application/pdf", "image/*")
    val today = LocalDate.now().toString()

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("📄 Documentación", style = MaterialTheme.typography.titleMedium)
            if (pdfOnly) Text("Solo se admiten documentos PDF (máximo 20 MB).", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (documents.isEmpty()) Text("No hay documentos adjuntos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            documents.forEach { doc ->
                val expired = doc.expiresAt != null && doc.expiresAt <= today
                val soon = doc.expiresAt != null && !expired && doc.expiresAt <= LocalDate.now().plusDays(30).toString()
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(if (doc.mimeType == "application/pdf") "PDF" else "IMG", Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(doc.name, style = MaterialTheme.typography.bodyLarge)
                            Text(categoryLabel(doc.category) + (doc.expiresAt?.let { if (expired) " · Caducado: $it" else " · Caduca: $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = when { expired -> MaterialTheme.colorScheme.error; soon -> MaterialTheme.colorScheme.tertiary; else -> MaterialTheme.colorScheme.onSurfaceVariant })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { onOpen(doc) }) { Text("Ver") }
                        TextButton(enabled = !saving, onClick = { replacingId = doc.id; picker.launch(mimeTypes) }) { Text("Sustituir") }
                        TextButton(enabled = !saving, onClick = { deleting = doc }) { Text("Borrar") }
                    }
                }
            }
            pickError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !saving, onClick = { pickError = null; picker.launch(mimeTypes) }) { Text(if (pdfOnly) "Adjuntar PDF" else "Adjuntar documento") }
                extraActions()
            }
        }
    }

    pendingUri?.let { uri ->
        AttachDocumentDialog(categoriesFor(ownerKind), saving, workspace.saveError.collectAsState().value, fileName = pendingName,
            onDismiss = { pendingUri = null; workspace.saveError.value = null }) { name, category, expiresAt ->
            workspace.save({
                val stored = storage.importUri(Uri.parse(uri), ownerKind.lowercase())
                // Si falla la base de datos no se deja el archivo copiado sin registro.
                try {
                    addAttachment(HomeAttachment(ownerKind = ownerKind, ownerId = ownerId, type = "DOCUMENTO", category = category,
                        name = name.ifBlank { stored.displayName }, path = stored.path, mimeType = stored.mimeType,
                        createdAt = LocalDate.now().toString(), expiresAt = expiresAt))
                } catch (e: Exception) { storage.delete(stored.path); throw e }
            }) { pendingUri = null }
        }
    }
    deleting?.let { doc ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("¿Borrar ${doc.name}?") },
            text = { Text("El documento se eliminará de este dispositivo. Esta acción no se puede deshacer.") },
            confirmButton = { Button(onClick = {
                workspace.save({ deleteAttachment(doc.id); storage.delete(doc.path) }) { deleting = null }
            }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } })
    }
}

/** Pide el nombre, el tipo y la fecha de caducidad (opcional) del documento elegido. */
@Composable
private fun AttachDocumentDialog(categories: List<String>, saving: Boolean, saveError: String?, fileName: String = "", onDismiss: () -> Unit,
    onConfirm: (name: String, category: String, expiresAt: String?) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(categories.first()) }
    var expiresAt by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("Adjuntar documento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (fileName.isNotBlank()) Text("Archivo: $fileName", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(name, { name = it }, label = { Text("Nombre (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                ChoiceField("Tipo", categoryLabel(category), categories.map(::categoryLabel), !saving) { label ->
                    category = categories.first { categoryLabel(it) == label }
                }
                DateField("Caduca el", expiresAt, { expiresAt = it }, enabled = !saving, showErrors = error != null)
                Text("Si indicas una fecha, aparecerá en Avisos y en el Calendario antes de que caduque.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                (error ?: saveError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = !saving, onClick = {
                val dateProblem = dateError(expiresAt, required = false)
                if (dateProblem != null) error = dateProblem
                else onConfirm(name.trim(), category, expiresAt.ifBlank { null })
            }) { Text(if (saving) "Guardando…" else "Guardar") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancelar") } })
}
