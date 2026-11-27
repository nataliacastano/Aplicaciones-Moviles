package com.example.gestorempresa.ui.advanced.documents

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.gestorempresa.data.local.HomeDatabase
import com.example.gestorempresa.data.local.entities.EmpleadoEntity
import com.example.gestorempresa.data.media.AttachmentStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PendingPdfDocumentsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** Simula un PDF elegido en el selector de documentos (content://). */
    private fun pdf(name: String): Uri {
        val uri = Uri.parse("content://documentos.prueba/$name")
        shadowOf(context.contentResolver).registerInputStream(uri, "%PDF-1.4\nprueba".byteInputStream())
        return uri
    }

    @Test fun attachesPendingDocumentsToTheNewEmployee() = runBlocking {
        val dao = HomeDatabase.forCompany(context, 900020).homeDao()
        val storage = AttachmentStorage(context, 900020)
        val id = dao.addEmployee(EmpleadoEntity(nombre = "Ana", apellidos = "Ruiz", dni = "", telefono = "", email = "", oficio = "Limpieza",
            tipoEmpleado = "COMPLETO", salarioBase = 0.0, lugarResidencia = "", direccionPersonal = ""))
        val docs = listOf(PendingPdf(pdf("contrato.pdf").toString(), "contrato.pdf", "CONTRATO", expiresAt = "2027-01-31"),
            PendingPdf(pdf("prl.pdf").toString(), "prl.pdf", "CURSO", title = "PRL 20 h"))
        dao.attachPendingPdfs(storage, "EMPLEADO", id, docs)
        val saved = dao.attachments().first()
        assertEquals(2, saved.size)
        assertTrue(saved.all { it.ownerKind == "EMPLEADO" && it.ownerId == id && it.mimeType == "application/pdf" })
        assertEquals(setOf("contrato", "PRL 20 h"), saved.map { it.name }.toSet())
        assertEquals("2027-01-31", saved.single { it.category == "CONTRATO" }.expiresAt)
        assertTrue(saved.all { File(it.path).isFile && it.path.startsWith(storage.folder.path) })
    }

    @Test fun undoesEverythingWhenOneDocumentFails() = runBlocking {
        val dao = HomeDatabase.forCompany(context, 900021).homeDao()
        val storage = AttachmentStorage(context, 900021)
        val docs = listOf(PendingPdf(pdf("bueno.pdf").toString(), "bueno.pdf", "CONTRATO"),
            PendingPdf("content://documentos.prueba/no-existe.pdf", "no-existe.pdf", "OTRO"))
        assertThrows(Exception::class.java) { runBlocking { dao.attachPendingPdfs(storage, "EMPLEADO", 1, docs) } }
        assertTrue(dao.attachments().first().isEmpty())
        assertTrue(storage.folder.listFiles().orEmpty().isEmpty())
    }
}
