package com.example.gestorempresa.ui.advanced.media

import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.example.gestorempresa.data.media.AttachmentStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

/** Acciones del flujo de imagen. Se obtiene con [rememberImageCaptureFlow]. */
class ImageCaptureFlow internal constructor(
    /** Abre el diálogo «Cámara / Galería». */
    val start: () -> Unit,
    /** true mientras se prepara la imagen elegida (para deshabilitar botones). */
    val busy: Boolean
)

/**
 * Flujo reutilizable para cualquier foto de la app (administrador, empleados, subcontratados,
 * servicios, proyectos y materiales), parecido al de la foto de perfil de WhatsApp:
 *
 * 1. Diálogo para elegir Cámara o Galería (con «Quitar foto» si se indica [onRemove]).
 * 2. La app de cámara del sistema o el selector de fotos (no piden permisos a la app).
 * 3. Pantalla de ajuste: mover, ampliar y ver el área de recorte.
 * 4. Confirmar o cancelar. [onConfirmed] solo se llama tras Confirmar, con el archivo recortado
 *    en una carpeta temporal: quien lo recibe debe moverlo a su almacenamiento definitivo.
 *
 * Cancelar el selector o la cámara no muestra error ni deja archivos temporales.
 * Debe llamarse dentro de la composición: dibuja él mismo los diálogos cuando hacen falta.
 *
 * @param circleGuide true para fotos de personas (guía circular); false para imágenes de servicios.
 * @param onError recibe un mensaje para el usuario; por defecto se muestra un Toast.
 */
@Composable
fun rememberImageCaptureFlow(storage: AttachmentStorage, title: String, circleGuide: Boolean,
    onConfirmed: suspend (File) -> Unit, onRemove: (() -> Unit)? = null, onError: ((String) -> Unit)? = null): ImageCaptureFlow {
    val context = LocalContext.current
    val store = remember(context) { PhotoAdjustmentStore(context) }
    val scope = rememberCoroutineScope()
    val latestConfirmed by rememberUpdatedState(onConfirmed)
    val reportError: (String) -> Unit = onError ?: { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    var choosing by remember { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }
    // Se guarda la ruta para no perder la imagen si Android recrea la pantalla durante el ajuste.
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    val cameraAvailable = remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }

    val picker = rememberPhotoPicker(storage) { sources ->
        if (preparing) return@rememberPhotoPicker
        scope.launch {
            preparing = true
            var prepared: File? = null
            try {
                prepared = store.prepare(sources.first())
                pendingPath = prepared.path
                prepared = null
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { reportError("No se ha podido abrir la imagen. Prueba con otra o vuelve a hacer la foto.") }
            finally { prepared?.let { store.discard(it.path) }; preparing = false }
        }
    }

    if (choosing) PhotoSourceDialog(
        title = title, cameraAvailable = cameraAvailable,
        onCamera = { choosing = false; picker.takePhoto() },
        onGallery = { choosing = false; picker.pickFromGallery() },
        onRemove = onRemove?.let { remove -> { choosing = false; remove() } },
        onDismiss = { choosing = false })

    pendingPath?.let { path ->
        if (!File(path).isFile) {
            // La imagen temporal ya no existe (por ejemplo, Android limpió la caché): se avisa y se cierra.
            LaunchedEffect(path) { pendingPath = null; reportError("La imagen ya no está disponible. Vuelve a elegirla.") }
        } else PhotoAdjustmentScreen(path, title = "Ajustar imagen", circleGuide = circleGuide,
            onConfirm = { cropped ->
                pendingPath = null
                store.discard(path)
                scope.launch {
                    try { latestConfirmed(cropped) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { reportError("No se ha podido guardar la imagen. Inténtalo de nuevo.") }
                    finally { store.discard(cropped.path) }
                }
            },
            onCancel = { pendingPath = null; store.discard(path) })
    }

    return ImageCaptureFlow(start = { if (!preparing) choosing = true }, busy = preparing)
}
