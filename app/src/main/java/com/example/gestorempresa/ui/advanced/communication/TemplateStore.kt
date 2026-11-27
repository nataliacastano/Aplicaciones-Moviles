package com.example.gestorempresa.ui.advanced.communication

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Plantillas de mensajes de una empresa. Parte de las plantillas de [messageTemplates] y guarda
 * los cambios que el administrador decida conservar («Guardar cambios en plantilla»), de modo que
 * se recuperan al volver a abrir la app. Editar un mensaje sin guardar no cambia la plantilla.
 */
class MessageTemplateStore(context: Context, companyId: Long) {
    private val prefs = context.applicationContext.getSharedPreferences("plantillas_mensajes_$companyId", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())

    /** Plantillas actuales (con los cambios guardados aplicados). Se actualiza al guardar o restaurar. */
    val templates: StateFlow<List<MessageTemplate>> = state.asStateFlow()

    fun isCustomized(title: String): Boolean = prefs.contains(key(title))

    /** Guarda permanentemente el nuevo texto de una plantilla. */
    fun save(title: String, text: String) {
        require(title != CUSTOM_TEMPLATE) { "El mensaje personalizado no es una plantilla" }
        val original = messageTemplates.firstOrNull { it.title == title }?.text
        // Si vuelve a quedar igual que la original, se olvida el cambio.
        prefs.edit().apply { if (text == original) remove(key(title)) else putString(key(title), text) }.apply()
        state.value = load()
    }

    /** Vuelve al texto original de la plantilla. */
    fun restore(title: String) {
        prefs.edit().remove(key(title)).apply()
        state.value = load()
    }

    private fun load(): List<MessageTemplate> = messageTemplates.map { template ->
        prefs.getString(key(template.title), null)?.let { template.copy(text = it) } ?: template
    }

    private fun key(title: String) = "plantilla:$title"
}

/** Almacén de plantillas de la empresa, compartido mientras dura la pantalla. */
@Composable
fun rememberTemplateStore(companyId: Long): MessageTemplateStore {
    val context = LocalContext.current
    return remember(companyId) { MessageTemplateStore(context, companyId) }
}
