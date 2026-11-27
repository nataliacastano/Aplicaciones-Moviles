package com.example.gestorempresa.ui.advanced.media

import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.example.gestorempresa.data.media.AttachmentStorage
import com.example.gestorempresa.data.media.decodePhotoBitmap
import com.example.gestorempresa.data.media.StoredFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Origen de una foto nueva: la cámara (archivo temporal) o la galería (enlace content://). */
sealed interface PhotoSource {
    data class Camera(val file: File) : PhotoSource
    data class Gallery(val uri: Uri) : PhotoSource
}

/** Copia la foto a la carpeta de la empresa, venga de donde venga. */
suspend fun AttachmentStorage.importPhoto(source: PhotoSource, prefix: String): StoredFile = when (source) {
    is PhotoSource.Camera -> importCameraPhoto(source.file, prefix)
    is PhotoSource.Gallery -> importUri(source.uri, prefix)
}

/** Acciones para conseguir fotos. Se crean con [rememberPhotoPicker]. */
class PhotoPicker internal constructor(val takePhoto: () -> Unit, val pickFromGallery: () -> Unit)

/**
 * Prepara la cámara y la galería del sistema.
 *
 * - Cámara: usa la app de cámara del móvil (TakePicture), así no hace falta pedir el permiso CAMERA.
 * - Galería: usa el selector de fotos del sistema, que tampoco necesita permisos de almacenamiento.
 * Con [multiple] la galería permite elegir varias fotos a la vez.
 */
@Composable
fun rememberPhotoPicker(storage: AttachmentStorage, multiple: Boolean = false, onPhotos: (List<PhotoSource>) -> Unit): PhotoPicker {
    val context = LocalContext.current
    val latestOnPhotos by rememberUpdatedState(onPhotos)
    // Se guarda la ruta para no perderla si Android cierra la app mientras la cámara está abierta.
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingCameraPath?.let(::File)
        pendingCameraPath = null
        if (ok && file != null) latestOnPhotos(listOf(PhotoSource.Camera(file))) else file?.delete()
    }
    val single = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) latestOnPhotos(listOf(PhotoSource.Gallery(uri)))
    }
    val many = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) latestOnPhotos(uris.map { PhotoSource.Gallery(it) })
    }
    return remember(storage, multiple) {
        PhotoPicker(
            takePhoto = {
                val (file, uri) = storage.newCameraTarget()
                pendingCameraPath = file.absolutePath
                try { camera.launch(uri) } catch (_: ActivityNotFoundException) {
                    pendingCameraPath = null
                    file.delete()
                    Toast.makeText(context, "No hay ninguna app de cámara disponible", Toast.LENGTH_SHORT).show()
                }
            },
            pickFromGallery = {
                val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                try { if (multiple) many.launch(request) else single.launch(request) }
                catch (_: ActivityNotFoundException) { Toast.makeText(context, "No hay ningún selector de imágenes disponible", Toast.LENGTH_SHORT).show() }
            }
        )
    }
}

/** Imagen guardada en el móvil, cargada en segundo plano y reducida al tamaño necesario. */
@Composable
fun LocalImage(path: String?, contentDescription: String?, modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop, maxSide: Int = 720, onClick: (() -> Unit)? = null,
    placeholder: @Composable () -> Unit = { ImagePlaceholder() }) {
    // retry cambia al pulsar «Reintentar» y vuelve a leer el archivo.
    var retry by remember(path) { mutableIntStateOf(0) }
    val result by produceState<ImageLoad>(ImageLoad.Loading, path, maxSide, retry) {
        value = when {
            path == null -> ImageLoad.Empty
            else -> withContext(Dispatchers.IO) { decodeImage(path, maxSide) }?.let { ImageLoad.Ready(it) } ?: ImageLoad.Failed
        }
    }
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant).then(clickable), contentAlignment = Alignment.Center) {
        when (val current = result) {
            is ImageLoad.Ready -> Image(current.image, contentDescription, Modifier.fillMaxSize(), contentScale = contentScale)
            ImageLoad.Loading -> CircularProgressIndicator()
            ImageLoad.Empty -> placeholder()
            ImageLoad.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
                Text("⚠️", style = MaterialTheme.typography.titleMedium)
                Text("No se pudo cargar", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { retry++ }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text("Reintentar", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** Estado de carga de una imagen local. */
private sealed interface ImageLoad {
    data object Loading : ImageLoad
    data object Empty : ImageLoad
    data object Failed : ImageLoad
    data class Ready(val image: ImageBitmap) : ImageLoad
}

/** Imagen predeterminada cuando todavía no hay foto. */
@Composable
fun ImagePlaceholder(label: String = "Sin foto") {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🖼️", style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Lee una imagen reduciendo su tamaño (una foto de móvil puede medir 4000 px)
 * y la gira según la orientación con la que se hizo la foto (datos EXIF).
 */
fun decodeImage(path: String, maxSide: Int): ImageBitmap? = decodePhotoBitmap(path, maxSide)?.asImageBitmap()
