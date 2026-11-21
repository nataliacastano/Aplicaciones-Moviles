package com.example.gestorempresa.ui.advanced.documents

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.gestorempresa.data.local.HomeAttachment
import com.example.gestorempresa.data.media.AttachmentStorage
import com.example.gestorempresa.ui.advanced.media.LocalImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Visor de adjuntos dentro de la app: muestra los PDF página a página (PdfRenderer de Android)
 * y las imágenes a pantalla completa. También permite abrirlos con otra app o compartirlos.
 */
@Composable
fun AttachmentViewerScreen(file: HomeAttachment, ownerName: String, storage: AttachmentStorage, onBack: () -> Unit,
    onDelete: (() -> Unit)? = null) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onBack) { Text("Volver") }
            Text(file.name, style = MaterialTheme.typography.titleLarge)
            Text("$ownerName · ${categoryLabel(file.category)}${file.expiresAt?.let { " · Caduca: $it" }.orEmpty()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { openExternally(context, storage, file, share = false) }) { Text("Abrir con…") }
                OutlinedButton(onClick = { openExternally(context, storage, file, share = true) }) { Text("Compartir") }
                if (onDelete != null) TextButton(onClick = { confirmDelete = true }) { Text("Borrar") }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !File(file.path).isFile -> Text("El archivo ya no está disponible en este dispositivo.", Modifier.padding(16.dp))
                file.mimeType == "application/pdf" -> PdfPages(file.path)
                file.mimeType.startsWith("image/") -> LocalImage(file.path, file.name, Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit, maxSide = 2048)
                else -> Text("Vista previa no disponible. Usa «Abrir con…».", Modifier.padding(16.dp))
            }
        }
    }
    if (confirmDelete && onDelete != null) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text("¿Borrar ${file.name}?") }, text = { Text("Se eliminará de este dispositivo. Esta acción no se puede deshacer.") },
        confirmButton = { Button(onClick = { confirmDelete = false; onDelete() }) { Text("Borrar") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } })
}

/** Abre el archivo en otra app (visor de PDF, galería…) o lo comparte (WhatsApp, correo…). */
fun openExternally(context: android.content.Context, storage: AttachmentStorage, file: HomeAttachment, share: Boolean) {
    val uri = storage.shareUri(file.path)
    val intent = if (share) Intent.createChooser(Intent(Intent.ACTION_SEND).setType(file.mimeType)
        .putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_SUBJECT, file.name)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Compartir ${file.name}")
    else Intent(Intent.ACTION_VIEW).setDataAndType(uri, file.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try { context.startActivity(intent) } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No hay ninguna app para abrir este archivo", Toast.LENGTH_SHORT).show()
    }
}

/** Muestra cada página del PDF como imagen, renderizándolas solo cuando aparecen en pantalla. */
@Composable
private fun PdfPages(path: String) {
    val renderer by produceState<PdfRenderer?>(null, path) {
        value = withContext(Dispatchers.IO) {
            try { PdfRenderer(ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)) } catch (_: Exception) { null }
        }
    }
    val current = renderer
    // PdfRenderer solo admite una página abierta a la vez, y no se puede cerrar mientras dibuja una.
    val lock = remember(path) { Mutex() }
    DisposableEffect(current) {
        onDispose { current?.let { pdf -> CoroutineScope(Dispatchers.IO).launch { lock.withLock { pdf.close() } } } }
    }
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = with(LocalDensity.current) { (maxWidth - 32.dp).roundToPx() }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(current.pageCount) { index ->
                val page by produceState<ImageBitmap?>(null, current, index, widthPx) {
                    value = withContext(Dispatchers.IO) { lock.withLock { renderPage(current, index, widthPx) } }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    page?.let { Image(it, "Página ${index + 1}", Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(androidx.compose.ui.graphics.Color.White)) }
                        ?: Box(Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    Text("Página ${index + 1} de ${current.pageCount}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun renderPage(renderer: PdfRenderer, index: Int, widthPx: Int): ImageBitmap? = try {
    val page = renderer.openPage(index)
    try {
        val width = widthPx.coerceIn(320, 2000)
        val height = (width.toFloat() * page.height / page.width).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        bitmap.asImageBitmap()
    } finally { page.close() }
} catch (_: Exception) { null }
