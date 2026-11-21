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
import com.example.gestorempresa.ui.home.ChoiceField
import com.example.gestorempresa.ui.home.HomeWorkspaceState
import com.example.gestorempresa.ui.home.HomeWorkspaceViewModel
import com.example.gestorempresa.ui.home.validDate
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

/**
 * Tarjeta de documentación de una ficha: lista los PDF e imágenes adjuntos,
 * permite consultarlos, borrarlos y adjuntar nuevos con su fecha de caducidad.
 */
@Composable
fun DocumentsCard(ownerKind: String, ownerId: Long, state: HomeWorkspaceState, storage: AttachmentStorage,
    workspace: HomeWorkspaceViewModel, onOpen: (HomeAttachment) -> Unit, extraActions: @Composable () -> Unit = {}) {
    val documents = state.attachments.filter { it.ownerKind == ownerKind && it.ownerId == ownerId && it.type == "DOCUMENTO" }
    val saving by workspace.saving.collectAsState()
    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<HomeAttachment?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingUri = uri?.toString() }
    val today = LocalDate.now().toString()

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("📄 Documentación", style = MaterialTheme.typography.titleMedium)
            if (documents.isEmpty()) Text("No hay documentos adjuntos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            documents.forEach { doc ->
                val expired = doc.expiresAt != null && doc.expiresAt <= today
                val soon = doc.expiresAt != null && !expired && doc.expiresAt <= LocalDate.now().plusDays(30).toString()
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
                    TextButton(onClick = { onOpen(doc) }) { Text("Ver") }
                    TextButton(enabled = !saving, onClick = { deleting = doc }) { Text("Borrar") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !saving, onClick = { picker.launch(arrayOf("application/pdf", "image/*")) }) { Text("Adjuntar documento") }
                extraActions()
            }
        }
    }

    pendingUri?.let { uri ->
        AttachDocumentDialog(categoriesFor(ownerKind), saving, workspace.saveError.collectAsState().value,
            onDismiss = { pendingUri = null; workspace.saveError.value = null }) { name, category, expiresAt ->
            workspace.save({
                val stored = storage.importUri(Uri.parse(uri), ownerKind.lowercase())
                addAttachment(HomeAttachment(ownerKind = ownerKind, ownerId = ownerId, type = "DOCUMENTO", category = category,
                    name = name.ifBlank { stored.displayName }, path = stored.path, mimeType = stored.mimeType,
                    createdAt = LocalDate.now().toString(), expiresAt = expiresAt))
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
private fun AttachDocumentDialog(categories: List<String>, saving: Boolean, saveError: String?, onDismiss: () -> Unit,
    onConfirm: (name: String, category: String, expiresAt: String?) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(categories.first()) }
    var expiresAt by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("Adjuntar documento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                ChoiceField("Tipo", categoryLabel(category), categories.map(::categoryLabel), !saving) { label ->
                    category = categories.first { categoryLabel(it) == label }
                }
                OutlinedTextField(expiresAt, { expiresAt = it.filter { c -> c.isDigit() || c == '-' }.take(10) },
                    label = { Text("Caduca el (AAAA-MM-DD, opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Si indicas una fecha, aparecerá en Avisos y en el Calendario antes de que caduque.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                (error ?: saveError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = !saving, onClick = {
                if (expiresAt.isNotBlank() && !validDate(expiresAt)) error = "La fecha debe tener el formato AAAA-MM-DD."
                else onConfirm(name.trim(), category, expiresAt.ifBlank { null })
            }) { Text(if (saving) "Guardando…" else "Guardar") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancelar") } })
}
