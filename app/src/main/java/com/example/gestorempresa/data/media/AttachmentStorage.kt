package com.example.gestorempresa.data.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

/** Archivo ya copiado a la carpeta interna de la app. */
data class StoredFile(val path: String, val displayName: String, val mimeType: String)

/**
 * Guarda las fotos y documentos de una empresa en el almacenamiento interno de la app.
 *
 * La cámara, la galería y el selector de documentos devuelven enlaces temporales (content://...).
 * Copiar el archivo asegura que siga disponible aunque el usuario lo borre de su móvil,
 * y cada empresa tiene su propia carpeta, igual que su propia base de datos.
 */
class AttachmentStorage(context: Context, companyId: Long) {
    private val appContext = context.applicationContext
    private val authority = "${appContext.packageName}.fileprovider"
    val folder: File = File(appContext.filesDir, "adjuntos/empresa_$companyId")
    private val cameraFolder: File get() = File(appContext.cacheDir, "camara").apply { mkdirs() }

    /** Prepara un archivo temporal donde la app de cámara guardará la foto. */
    fun newCameraTarget(): Pair<File, Uri> {
        val file = File(cameraFolder, "foto_${System.currentTimeMillis()}.jpg")
        return file to FileProvider.getUriForFile(appContext, authority, file)
    }

    /** Mueve la foto recién hecha a la carpeta de la empresa. */
    suspend fun importCameraPhoto(file: File, prefix: String): StoredFile = withContext(Dispatchers.IO) {
        check(file.isFile && file.length() > 0) { "La cámara no ha guardado la foto" }
        val target = newFile(prefix, "jpg")
        file.copyTo(target, overwrite = true)
        file.delete()
        StoredFile(target.absolutePath, "Foto ${java.time.LocalDate.now()}", "image/jpeg")
    }

    /** Copia una imagen o documento elegido desde la galería o el selector de archivos. */
    suspend fun importUri(uri: Uri, prefix: String): StoredFile = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "Documento"
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            ?: name.substringAfterLast('.', "").ifBlank { "bin" }
        val target = newFile(prefix, extension)
        // Si la copia falla a medias se borra el archivo parcial para no dejar restos.
        try {
            val input = resolver.openInputStream(uri) ?: error("No se ha podido abrir el archivo seleccionado")
            input.use { source -> target.outputStream().use { source.copyTo(it) } }
        } catch (e: Exception) { target.delete(); throw e }
        StoredFile(target.absolutePath, name.substringBeforeLast('.').ifBlank { name }, mime)
    }

    /** Guarda un archivo generado por la app (por ejemplo, un informe PDF). */
    suspend fun saveGenerated(prefix: String, extension: String, write: (OutputStream) -> Unit): String = withContext(Dispatchers.IO) {
        val target = newFile(prefix, extension)
        try { target.outputStream().use(write) } catch (e: Exception) { target.delete(); throw e }
        target.absolutePath
    }

    /** Enlace content:// para abrir o compartir el archivo con otra app. */
    fun shareUri(path: String): Uri = FileProvider.getUriForFile(appContext, authority, File(path))

    /** Borra un archivo. Solo borra dentro de la carpeta de la empresa, nunca otros archivos. */
    suspend fun delete(path: String?) = withContext(Dispatchers.IO) {
        val file = path?.let(::File) ?: return@withContext
        if (file.canonicalPath.startsWith(folder.canonicalPath + File.separator)) file.delete()
    }

    companion object {
        /**
         * Borra fotos temporales (cámara y ajuste) de más de un día. Quedan si Android cierra la app
         * a mitad de una captura, y pueden contener imágenes personales.
         */
        suspend fun cleanTemporaryFiles(context: Context, maxAgeMillis: Long = 24L * 60 * 60 * 1000) = withContext(Dispatchers.IO) {
            val limit = System.currentTimeMillis() - maxAgeMillis
            listOf("camara", "ajuste_fotos").forEach { name ->
                File(context.cacheDir, name).listFiles()?.filter { it.isFile && it.lastModified() < limit }?.forEach { it.delete() }
            }
        }
    }

    private fun newFile(prefix: String, extension: String): File {
        folder.mkdirs()
        val safePrefix = prefix.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(folder, "${safePrefix}_${System.currentTimeMillis()}_${(1000..9999).random()}.$extension")
    }
}
