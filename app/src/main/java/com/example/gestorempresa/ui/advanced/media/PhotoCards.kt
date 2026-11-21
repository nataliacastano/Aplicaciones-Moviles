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
import com.example.gestorempresa.data.media.AttachmentStorage
import com.example.gestorempresa.ui.home.HomeWorkspaceViewModel
import java.time.LocalDate

/**
 * Foto de un empleado o subcontratado con los botones de cámara y galería.
 * La ruta se guarda en el campo fotoUrl que ya tenían las fichas.
 */
@Composable
fun WorkerPhotoHeader(workerKind: String, workerId: Long, photoPath: String?, initials: String,
    storage: AttachmentStorage, workspace: HomeWorkspaceViewModel) {
    val saving by workspace.saving.collectAsState()
    fun savePhoto(newPath: String?) = workspace.save({
        if (workerKind == "EMPLEADO") setEmployeePhoto(workerId, newPath) else setSubcontractorPhoto(workerId, newPath)
        storage.delete(photoPath)
    }) {}
    val picker = rememberPhotoPicker(storage) { sources ->
        workspace.save({
            val stored = storage.importPhoto(sources.first(), "foto_${workerKind.lowercase()}_$workerId")
            if (workerKind == "EMPLEADO") setEmployeePhoto(workerId, stored.path) else setSubcontractorPhoto(workerId, stored.path)
            storage.delete(photoPath)
        }) {}
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(96.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
            if (photoPath != null) LocalImage(photoPath, "Foto", Modifier.fillMaxSize(), maxSide = 384)
            else Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center) {
                    Text(initials.ifBlank { "?" }, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(enabled = !saving, onClick = picker.takePhoto) { Text("📷 Cámara") }
                FilledTonalButton(enabled = !saving, onClick = picker.pickFromGallery) { Text("🖼️ Galería") }
            }
            if (photoPath != null) TextButton(enabled = !saving, onClick = { savePhoto(null) }) { Text("Quitar foto") }
        }
    }
}

/**
 * Galería de fotos de un servicio, proyecto o material (trabajos realizados, estado de la obra…).
 * Permite hacer fotos con la cámara o elegir varias de la galería.
 */
@Composable
fun PhotosCard(ownerKind: String, ownerId: Long, attachments: List<HomeAttachment>, storage: AttachmentStorage,
    workspace: HomeWorkspaceViewModel, onOpen: (HomeAttachment) -> Unit, title: String = "📷 Fotografías") {
    val photos = attachments.filter { it.ownerKind == ownerKind && it.ownerId == ownerId && it.type == "FOTO" }
    val saving by workspace.saving.collectAsState()
    val picker = rememberPhotoPicker(storage, multiple = true) { sources ->
        workspace.save({
            sources.forEach { source ->
                val stored = storage.importPhoto(source, "foto_${ownerKind.lowercase()}_$ownerId")
                addAttachment(HomeAttachment(ownerKind = ownerKind, ownerId = ownerId, type = "FOTO", category = "FOTO",
                    name = stored.displayName, path = stored.path, mimeType = stored.mimeType.ifBlank { "image/jpeg" },
                    createdAt = LocalDate.now().toString()))
            }
        }) {}
    }
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(enabled = !saving, onClick = picker.takePhoto) { Text("📷 Hacer foto") }
                FilledTonalButton(enabled = !saving, onClick = picker.pickFromGallery) { Text("🖼️ Galería") }
            }
        }
    }
}
