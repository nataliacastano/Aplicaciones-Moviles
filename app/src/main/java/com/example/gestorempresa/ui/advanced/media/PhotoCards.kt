package com.example.gestorempresa.ui.advanced.media

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.gestorempresa.data.local.HomeAttachment
import com.example.gestorempresa.data.local.HomeDao
import com.example.gestorempresa.data.media.AttachmentStorage
import com.example.gestorempresa.ui.home.HomeWorkspaceViewModel
import java.time.LocalDate

/**
 * Foto de un empleado o subcontratado. Al tocarla (o «Cambiar foto») se abre el flujo reutilizable
 * Cámara/Galería → ajuste → confirmar. La ruta se guarda en el campo fotoUrl que ya tenían las fichas.
 */
@Composable
fun WorkerPhotoHeader(workerKind: String, workerId: Long, photoPath: String?, initials: String,
    storage: AttachmentStorage, workspace: HomeWorkspaceViewModel) {
    val saving by workspace.saving.collectAsState()
    suspend fun HomeDao.setPhoto(path: String?) =
        if (workerKind == "EMPLEADO") setEmployeePhoto(workerId, path) else setSubcontractorPhoto(workerId, path)
    val flow = rememberImageCaptureFlow(storage, title = "Fotografía", circleGuide = true,
        onConfirmed = { cropped ->
            // Se copia antes de devolver el control: el archivo recortado es temporal.
            val stored = storage.importCameraPhoto(cropped, "foto_${workerKind.lowercase()}_$workerId")
            workspace.save({
                try { setPhoto(stored.path) } catch (e: Exception) { storage.delete(stored.path); throw e }
                storage.delete(photoPath)
            }) {}
        },
        onRemove = photoPath?.let { { workspace.save({ setPhoto(null); storage.delete(photoPath) }) {} } })
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(96.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
            LocalImage(photoPath, "Foto", Modifier.fillMaxSize(), maxSide = 384,
                onClick = if (saving || flow.busy) null else flow.start,
                placeholder = { PersonPlaceholder(initials) })
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilledTonalButton(enabled = !saving && !flow.busy, onClick = flow.start) {
                Text(if (flow.busy) "Preparando…" else if (photoPath == null) "📷 Añadir foto" else "📷 Cambiar foto")
            }
            Text("Cámara o galería, con ajuste del encuadre.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Imagen predeterminada de una persona sin foto: sus iniciales sobre un círculo de color. */
@Composable
fun PersonPlaceholder(initials: String) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primaryContainer) {
        Box(contentAlignment = Alignment.Center) {
            Text(initials.ifBlank { "?" }, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

/**
 * Galería de fotos de un servicio, proyecto o material (trabajos realizados, estado de la obra…).
 * Cada foto pasa por el mismo flujo de cámara/galería y ajuste antes de guardarse.
 */
@Composable
fun PhotosCard(ownerKind: String, ownerId: Long, attachments: List<HomeAttachment>, storage: AttachmentStorage,
    workspace: HomeWorkspaceViewModel, onOpen: (HomeAttachment) -> Unit, title: String = "📷 Fotografías") {
    val photos = attachments.filter { it.ownerKind == ownerKind && it.ownerId == ownerId && it.type == "FOTO" }
    val saving by workspace.saving.collectAsState()
    val flow = rememberImageCaptureFlow(storage, title = "Añadir imagen", circleGuide = false, onConfirmed = { cropped ->
        val stored = storage.importCameraPhoto(cropped, "foto_${ownerKind.lowercase()}_$ownerId")
        workspace.save({
            try {
                addAttachment(HomeAttachment(ownerKind = ownerKind, ownerId = ownerId, type = "FOTO", category = "FOTO",
                    name = stored.displayName, path = stored.path, mimeType = "image/jpeg", createdAt = LocalDate.now().toString()))
            } catch (e: Exception) { storage.delete(stored.path); throw e }
        }) {}
    })
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (photos.isEmpty()) Text("Todavía no hay fotos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(photos, key = { it.id }) { photo ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LocalImage(photo.path, photo.name, Modifier.size(112.dp).clip(RoundedCornerShape(12.dp)), maxSide = 320) { onOpen(photo) }
                        Text(photo.createdAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            FilledTonalButton(enabled = !saving && !flow.busy, onClick = flow.start) {
                Text(if (flow.busy) "Preparando…" else "📷 Añadir foto")
            }
        }
    }
}
