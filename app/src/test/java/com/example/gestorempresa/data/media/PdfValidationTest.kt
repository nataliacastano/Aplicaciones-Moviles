package com.example.gestorempresa.data.media

import org.junit.Assert.*
import org.junit.Test

class PdfValidationTest {
    @Test fun recognisesPdfByContentNotByName() {
        assertTrue(looksLikePdf("%PDF-1.7\n%âãÏÓ".toByteArray(Charsets.ISO_8859_1)))
        // Cabecera precedida de bytes basura (permitido por la especificación).
        assertTrue(looksLikePdf(ByteArray(10) + "%PDF-1.4".toByteArray()))
        assertFalse(looksLikePdf("\u0089PNG\r\n".toByteArray(Charsets.ISO_8859_1)))
        assertFalse(looksLikePdf("PK\u0003\u0004 docx disfrazado de .pdf".toByteArray(Charsets.ISO_8859_1)))
        assertFalse(looksLikePdf(ByteArray(0)))
    }
}
