package com.example.gestorempresa.ui.advanced.media

import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.example.gestorempresa.data.media.AttachmentStorage
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
    // Se guarda la ruta para no perderla si Android cierra la app mientras la cámara está abierta.
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingCameraPath?.let(::File)
        pendingCameraPath = null
        if (ok && file != null) onPhotos(listOf(PhotoSource.Camera(file))) else file?.delete()
    }
    val single = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPhotos(listOf(PhotoSource.Gallery(uri)))
    }
    val many = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) onPhotos(uris.map { PhotoSource.Gallery(it) })
    }
    return remember(storage, multiple) {
        PhotoPicker(
            takePhoto = {
                val (file, uri) = storage.newCameraTarget()
                pendingCameraPath = file.absolutePath
                try { camera.launch(uri) } catch (_: ActivityNotFoundException) {
                    pendingCameraPath = null
                    Toast.makeText(context, "No hay ninguna app de cámara disponible", Toast.LENGTH_SHORT).show()
                }
            },
            pickFromGallery = {
                val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                if (multiple) many.launch(request) else single.launch(request)
            }
        )
    }
}

/** Imagen guardada en el móvil, cargada en segundo plano y reducida al tamaño necesario. */
@Composable
fun LocalImage(path: String?, contentDescription: String?, modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop, maxSide: Int = 720, onClick: (() -> Unit)? = null) {
    val image by produceState<ImageBitmap?>(null, path, maxSide) {
        value = path?.let { withContext(Dispatchers.IO) { decodeImage(it, maxSide) } }
    }
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant).then(clickable), contentAlignment = Alignment.Center) {
        val current = image
        if (current != null) Image(current, contentDescription, Modifier.fillMaxSize(), contentScale = contentScale)
        else if (path != null) CircularProgressIndicator()
    }
}

/**
 * Lee una imagen reduciendo su tamaño (una foto de móvil puede medir 4000 px)
 * y la gira según la orientación con la que se hizo la foto (datos EXIF).
 */
fun decodeImage(path: String, maxSide: Int): ImageBitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxSide && bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
    val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    bitmap?.let { rotateFromExif(path, it).asImageBitmap() }
} catch (_: Exception) { null }

private fun rotateFromExif(path: String, bitmap: Bitmap): Bitmap {
    val degrees = try {
        when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } catch (_: Exception) { 0f }
    if (degrees == 0f) return bitmap
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
}
