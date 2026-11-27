package com.example.gestorempresa.ui.advanced.documents

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.gestorempresa.data.local.HomeAttachment
import com.example.gestorempresa.data.local.HomeDao
import com.example.gestorempresa.data.media.AttachmentStorage
import com.example.gestorempresa.data.media.PdfCheck
import com.example.gestorempresa.data.media.checkPdf
import com.example.gestorempresa.ui.advanced.forms.DateField
import com.example.gestorempresa.ui.home.ChoiceField
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Documento PDF elegido durante un alta, todavía sin guardar (el empleado aún no tiene id). */
data class PendingPdf(val uri: String, val fileName: String, val category: String, val title: String = "", val expiresAt: String? = null)

/** Tipos que se ofrecen en el alta: contrato, certificados, formación, documentación laboral y otros. */
val signupDocumentCategories = listOf("CONTRATO", "CERTIFICADO", "CURSO", "LABORAL", "OTRO")

/** Lista de documentos pendientes que sobrevive al giro de pantalla y a la recreación de la actividad. */
@Composable
fun rememberPendingPdfs(): MutableState<List<PendingPdf>> = rememberSaveable(saver = listSaver(
    save = { state -> state.value.flatMap { listOf(it.uri, it.fileName, it.category, it.title, it.expiresAt.orEmpty()) } },
    restore = { flat -> mutableStateOf(flat.chunked(5).map { PendingPdf(it[0], it[1], it[2], it[3], it[4].ifBlank { null }) }) }
)) { mutableStateOf(emptyList()) }

/**
 * Componente para adjuntar documentos PDF en un formulario de alta (por ejemplo, el de empleados).
 * Solo admite PDF válidos (se comprueba el contenido). Los documentos quedan pendientes hasta que el
 * formulario guarda el registro y llama a [attachPendingPdfs] con el id recién creado.
 */
@Composable
fun PendingPdfDocumentsEditor(documents: List<PendingPdf>, onChange: (List<PendingPdf>) -> Unit,
    enabled: Boolean = true, categories: List<String> = signupDocumentCategories) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var category by rememberSaveable { mutableStateOf(categories.first()) }
    var title by rememberSaveable { mutableStateOf("") }
    var expiresAt by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val latestDocuments by rememberUpdatedState(documents)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            when (val check = checkPdf(context, uri)) {
                is PdfCheck.Invalid -> error = check.reason
                is PdfCheck.Valid -> {
                    if (latestDocuments.any { it.uri == uri.toString() }) { error = "Ese documento ya está en la lista."; return@launch }
                    // Permiso duradero: el archivo seguirá accesible aunque Android recree la pantalla antes de guardar.
                    try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
                    error = null
                    onChange(latestDocuments + PendingPdf(uri.toString(), check.fileName, category, title.trim(), expiresAt.ifBlank { null }))
                    title = ""; expiresAt = ""
                }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Documentación (PDF)", style = MaterialTheme.typography.titleSmall)
        ChoiceField("Tipo", categoryLabel(category), categories.map(::categoryLabel), enabled) { label -> category = categories.first { categoryLabel(it) == label } }
        OutlinedTextField(title, { title = it }, label = { Text("Descripción (opcional)") }, singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth())
        DateField("Caduca el", expiresAt, { expiresAt = it }, enabled = enabled)
        OutlinedButton(enabled = enabled && com.example.gestorempresa.ui.advanced.forms.dateError(expiresAt, false) == null,
            onClick = { error = null; picker.launch(arrayOf("application/pdf")) }) { Text("Seleccionar PDF") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        documents.forEach { doc ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(doc.title.ifBlank { doc.fileName }, style = MaterialTheme.typography.bodyLarge)
                    Text("${categoryLabel(doc.category)} · ${doc.fileName}${doc.expiresAt?.let { " · Caduca: $it" }.orEmpty()}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(enabled = enabled, onClick = { releasePendingPdfs(context, listOf(doc)); onChange(documents - doc) }) { Text("Quitar") }
            }
        }
    }
}

/**
 * Copia los PDF pendientes a la carpeta privada de la empresa y los asocia al registro [ownerId].
 * Llamar dentro de `workspace.save { … }` justo después de insertar el registro:
 *
 *     vm.save({ val id = addEmployee(empleado); attachPendingPdfs(storage, "EMPLEADO", id, pendientes) }, onBack)
 *
 * Si algo falla se borran las copias ya hechas y se relanza el error para que el formulario lo muestre.
 */
suspend fun HomeDao.attachPendingPdfs(storage: AttachmentStorage, ownerKind: String, ownerId: Long, documents: List<PendingPdf>) {
    val copied = mutableListOf<String>()
    val inserted = mutableListOf<Long>()
    try {
        documents.forEach { doc ->
            val stored = storage.importUri(Uri.parse(doc.uri), ownerKind.lowercase())
            copied += stored.path
            inserted += addAttachment(HomeAttachment(ownerKind = ownerKind, ownerId = ownerId, type = "DOCUMENTO", category = doc.category,
                name = doc.title.ifBlank { doc.fileName.removeSuffix(".pdf").removeSuffix(".PDF") }, path = stored.path,
                mimeType = "application/pdf", createdAt = LocalDate.now().toString(), expiresAt = doc.expiresAt))
        }
    } catch (e: Exception) {
        // Se deshace todo: ni filas sin archivo ni archivos sin fila.
        inserted.forEach { runCatching { deleteAttachment(it) } }
        copied.forEach { storage.delete(it) }
        throw e
    }
}

/** Libera el permiso de lectura que se pidió al elegir los PDF (después de guardarlos o al quitarlos). */
fun releasePendingPdfs(context: Context, documents: List<PendingPdf>) = documents.forEach {
    try { context.contentResolver.releasePersistableUriPermission(Uri.parse(it.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
}
