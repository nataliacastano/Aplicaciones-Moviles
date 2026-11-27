package com.example.gestorempresa.data.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Resultado de comprobar si un archivo elegido es un PDF válido. */
sealed interface PdfCheck {
    data class Valid(val fileName: String, val sizeBytes: Long) : PdfCheck
    data class Invalid(val reason: String) : PdfCheck
}

/** Tamaño máximo de un documento adjunto. */
const val MAX_PDF_BYTES = 20L * 1024 * 1024

/**
 * Un PDF empieza siempre por «%PDF-». Se comprueba el contenido real del archivo y no solo
 * la extensión o el tipo que declara la app de origen, que se pueden falsear.
 */
fun looksLikePdf(header: ByteArray): Boolean {
    // Algunos generadores añaden bytes antes de la cabecera; la especificación permite hasta 1024.
    val text = header.copyOf(minOf(header.size, 1024)).toString(Charsets.ISO_8859_1)
    return text.contains("%PDF-")
}

/** Valida tipo declarado, contenido y tamaño antes de copiar nada al almacenamiento de la app. */
suspend fun checkPdf(context: Context, uri: Uri): PdfCheck = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    var name = "Documento.pdf"
    var size = -1L
    try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
    } catch (_: Exception) { return@withContext PdfCheck.Invalid("No se ha podido leer el archivo seleccionado.") }
    val declared = resolver.getType(uri)
    if (declared != null && declared != "application/pdf" && declared != "application/octet-stream")
        return@withContext PdfCheck.Invalid("«$name» no es un PDF. Solo se admiten documentos PDF.")
    if (size > MAX_PDF_BYTES) return@withContext PdfCheck.Invalid("«$name» supera el tamaño máximo de 20 MB.")
    if (size == 0L) return@withContext PdfCheck.Invalid("«$name» está vacío.")
    val header = try {
        resolver.openInputStream(uri)?.use { input -> ByteArray(1024).let { buffer -> buffer.copyOf(input.read(buffer).coerceAtLeast(0)) } }
    } catch (_: Exception) { null } ?: return@withContext PdfCheck.Invalid("No se ha podido abrir «$name».")
    if (!looksLikePdf(header)) return@withContext PdfCheck.Invalid("«$name» no es un PDF válido aunque lo parezca por su nombre.")
    PdfCheck.Valid(name, size)
}
