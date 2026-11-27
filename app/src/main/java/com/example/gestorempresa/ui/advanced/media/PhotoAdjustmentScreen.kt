package com.example.gestorempresa.ui.advanced.media

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.gestorempresa.data.media.CropFrame
import com.example.gestorempresa.data.media.decodePhotoBitmap
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * Ajuste compartido para las fotografías: mover, ampliar, reducir y confirmar.
 * Nada se guarda hasta pulsar Confirmar. [circleGuide] muestra el círculo de las fotos de personas;
 * las imágenes de servicios, proyectos y materiales usan solo el recuadro.
 */
@Composable
fun PhotoAdjustmentScreen(path: String, onConfirm: (File) -> Unit, onCancel: () -> Unit,
    title: String = "Ajustar fotografía", circleGuide: Boolean = true) {
    val context = LocalContext.current
    val store = remember(context) { PhotoAdjustmentStore(context) }
    val bitmap by produceState<android.graphics.Bitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { decodePhotoBitmap(path, 2048) }
    }
    var centerX by rememberSaveable(path) { mutableFloatStateOf(.5f) }
    var centerY by rememberSaveable(path) { mutableFloatStateOf(.5f) }
    var zoom by rememberSaveable(path) { mutableFloatStateOf(1f) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun update(frame: CropFrame) {
        val image = bitmap ?: return
        val bounded = frame.bounded(image.width, image.height)
        centerX = bounded.centerX; centerY = bounded.centerY; zoom = bounded.zoom
    }
    Dialog(onDismissRequest = { if (!saving) onCancel() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.Black, contentColor = Color.White) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(if (circleGuide) "Arrastra la imagen y pellizca para ampliar o reducir. Encuadra el rostro dentro del círculo."
                    else "Arrastra la imagen y pellizca para ampliar o reducir. Se guardará lo que quede dentro del recuadro.")
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val image = bitmap
                    if (image == null) Text("No se puede mostrar la imagen. Si no aparece, cancela y prueba otra foto.")
                    else {
                        val side = minOf(maxWidth, maxHeight)
                        val picture = remember(image) { image.asImageBitmap() }
                        Canvas(Modifier.size(side).clipToBounds().background(Color.DarkGray).testTag("photo-crop-area")
                            .pointerInput(image) {
                                detectTransformGestures { _, pan, scale, _ ->
                                    if (!saving) {
                                        val z = (zoom * scale).coerceIn(1f, 6f)
                                        val displayScale = size.width.toFloat() / min(image.width, image.height) * z
                                        update(CropFrame(centerX - pan.x / (displayScale * image.width),
                                            centerY - pan.y / (displayScale * image.height), z))
                                    }
                                }
                            }) {
                            val frame = CropFrame(centerX, centerY, zoom).bounded(image.width, image.height)
                            val scale = size.width / min(image.width, image.height) * frame.zoom
                            withTransform({
                                translate(size.width / 2 - frame.centerX * image.width * scale,
                                    size.height / 2 - frame.centerY * image.height * scale)
                                scale(scale, scale, pivot = Offset.Zero)
                            }) { drawImage(picture) }
                            drawRect(Color.White.copy(alpha = .8f), style = Stroke(2.dp.toPx()))
                            if (circleGuide) drawCircle(Color.White.copy(alpha = .8f), radius = size.width / 2, style = Stroke(2.dp.toPx()))
                        }
                    }
                }
                Text("Ampliación")
                Slider(zoom, { update(CropFrame(centerX, centerY, it)) }, valueRange = 1f..6f,
                    enabled = bitmap != null && !saving, modifier = Modifier.testTag("photo-zoom"))
                TextButton(onClick = { update(CropFrame()) }, enabled = !saving) { Text("Restablecer encuadre") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onCancel, enabled = !saving, modifier = Modifier.weight(1f).testTag("photo-cancel")) { Text("Cancelar") }
                    Button(onClick = {
                        val image = bitmap ?: return@Button
                        val frame = CropFrame(centerX, centerY, zoom)
                        saving = true
                        scope.launch {
                            var result: File? = null
                            try { val cropped = store.crop(image, frame); result = cropped; onConfirm(cropped); result = null }
                            catch (e: CancellationException) { throw e }
                            catch (_: Exception) { error = "No se pudo ajustar la imagen. Inténtalo de nuevo." }
                            finally { result?.let { store.discard(it.path) }; saving = false }
                        }
                    }, enabled = bitmap != null && !saving, modifier = Modifier.weight(1f).testTag("photo-confirm")) {
                        Text(if (saving) "Ajustando…" else "Confirmar")
                    }
                }
            }
        }
    }
}

@Composable
fun PhotoSourceDialog(onCamera: () -> Unit, onGallery: () -> Unit, onDismiss: () -> Unit,
    title: String = "Fotografía de perfil", cameraAvailable: Boolean = true, onRemove: (() -> Unit)? = null) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { Column(Modifier.fillMaxWidth()) {
            TextButton(onClick = onCamera, enabled = cameraAvailable, modifier = Modifier.fillMaxWidth().testTag("photo-source-camera")) {
                Text(if (cameraAvailable) "Cámara" else "Cámara (no disponible en este dispositivo)")
            }
            TextButton(onClick = onGallery, modifier = Modifier.fillMaxWidth().testTag("photo-source-gallery")) { Text("Galería") }
            onRemove?.let { TextButton(onClick = it, modifier = Modifier.fillMaxWidth().testTag("photo-source-remove")) { Text("Quitar foto") } }
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}
