package com.example.gestorempresa.ui.advanced.communication

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TemplateStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val title = messageTemplates.first().title

    @Test fun savedChangesSurviveReopeningTheApp() {
        MessageTemplateStore(context, 77).save(title, "Hola [NOMBRE], te esperamos en [LUGAR] a las [HORA].")
        // Una instancia nueva equivale a volver a abrir la app.
        val reopened = MessageTemplateStore(context, 77)
        assertEquals("Hola [NOMBRE], te esperamos en [LUGAR] a las [HORA].", reopened.templates.value.first { it.title == title }.text)
        assertTrue(reopened.isCustomized(title))
        // Otra empresa no ve el cambio.
        assertEquals(messageTemplates.first().text, MessageTemplateStore(context, 78).templates.value.first().text)
    }

    @Test fun restoringOrSavingTheOriginalForgetsTheChange() {
        val store = MessageTemplateStore(context, 79)
        store.save(title, "Otro texto")
        store.restore(title)
        assertFalse(store.isCustomized(title))
        assertEquals(messageTemplates.first().text, store.templates.value.first().text)
        store.save(title, messageTemplates.first().text)
        assertFalse(store.isCustomized(title))
    }
}
