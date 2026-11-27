package com.example.gestorempresa.ui.advanced.communication

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.gestorempresa.ui.advanced.forms.DateField
import com.example.gestorempresa.ui.advanced.forms.TimeField
import com.example.gestorempresa.ui.advanced.forms.dateError
import com.example.gestorempresa.ui.advanced.forms.timeError
import com.example.gestorempresa.ui.advanced.forms.timeRangeError
import com.example.gestorempresa.ui.advanced.map.workZonesOf
import com.example.gestorempresa.ui.home.HomeWorkspaceState

/** Tipos de documento que se ofrecen en los mensajes sobre documentación. */
val messageDocumentTypes = listOf("Contrato", "Certificado", "Formación", "Reconocimiento médico", "Otro documento")

private val timeKeys = setOf("HORA", "HORA_INICIO", "HORA_FIN")

/** Errores de los huecos rellenados (horas y fechas inválidas o incoherentes). Vacío si todo está bien. */
fun messageFieldErrors(values: Map<String, String>, keys: List<String>): Map<String, String> = buildMap {
    keys.forEach { key ->
        val value = values[key].orEmpty()
        when {
            key in timeKeys -> timeError(value, required = true)?.let { put(key, it) }
            key == "FECHA" -> dateError(value, required = true)?.let { put(key, it) }
        }
    }
    if ("HORA_INICIO" in keys && "HORA_FIN" in keys && !containsKey("HORA_FIN"))
        timeRangeError(values["HORA_INICIO"].orEmpty(), values["HORA_FIN"].orEmpty())?.let { put("HORA_FIN", it) }
}

/** Nombre legible de un hueco: HORA_INICIO → «Hora de inicio». */
fun placeholderLabel(key: String): String = when (key) {
    "HORA_INICIO" -> "Hora de inicio"
    "HORA_FIN" -> "Hora de fin"
    "DÍA" -> "Día"
    else -> key.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
}

/**
 * Editor adecuado para cada hueco de la plantilla:
 * - [LUGAR]: zona laboral del trabajador, servicio/proyecto o lugar escrito a mano.
 * - [HORA], [HORA_INICIO], [HORA_FIN]: hora de 24 h (HH:mm) con comprobación de inicio/fin.
 * - [FECHA]: fecha AAAA-MM-DD.
 * - [DOCUMENTO]: tipo de documento de una lista o escrito a mano.
 * - Otros: texto libre.
 */
@Composable
fun MessageFieldEditor(key: String, value: String, onChange: (String) -> Unit, contact: ContactInfo, state: HomeWorkspaceState,
    error: String?, showErrors: Boolean) {
    when {
        key == "LUGAR" -> PlaceField(value, onChange, contact, state)
        key in timeKeys -> TimeField(placeholderLabel(key), value, onChange, required = true, showErrors = showErrors,
            extraError = error?.takeIf { key == "HORA_FIN" && timeError(value, true) == null })
        key == "FECHA" -> DateField("Fecha", value, onChange, required = true, showErrors = showErrors)
        key == "DOCUMENTO" -> DocumentTypeField(value, onChange)
        else -> OutlinedTextField(value, onChange, label = { Text(placeholderLabel(key)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}

/** Lugar: zona laboral registrada, servicio o proyecto, o texto manual. La elección se copia al mensaje. */
@Composable
fun PlaceField(value: String, onChange: (String) -> Unit, contact: ContactInfo, state: HomeWorkspaceState) {
    var expanded by remember { mutableStateOf(false) }
    val zones = state.workZonesOf(contact.kind, contact.id)
    val projects = state.projects.filter { it.estado != "FINALIZADO" }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(value, onChange, label = { Text("Lugar") }, modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("Escribe un lugar o elige uno registrado.") },
            trailingIcon = { TextButton(onClick = { expanded = true }) { Text("Elegir") } })
        DropdownMenu(expanded, { expanded = false }) {
            if (zones.isNotEmpty()) {
                DropdownMenuItem(text = { Text("Zonas laborales", style = MaterialTheme.typography.labelMedium) }, onClick = {}, enabled = false)
                zones.forEach { zone ->
                    val text = zone.location?.let { "${zone.name} (${it.address})" } ?: zone.name
                    DropdownMenuItem(text = { Text(text) }, onClick = { onChange(text); expanded = false })
                }
            }
            if (projects.isNotEmpty()) {
                DropdownMenuItem(text = { Text("Servicios y proyectos", style = MaterialTheme.typography.labelMedium) }, onClick = {}, enabled = false)
                projects.forEach { project ->
                    val text = listOf(project.titulo, project.direccionUbicacion).filter { it.isNotBlank() }.joinToString(", ")
                    DropdownMenuItem(text = { Text(text) }, onClick = { onChange(text); expanded = false })
                }
            }
            DropdownMenuItem(text = { Text("✏️ Escribir otro lugar") }, onClick = { onChange(""); expanded = false })
        }
    }
}

/** Tipo de documento: Contrato, Certificado, Formación, Reconocimiento médico, Otro… o uno escrito a mano. */
@Composable
fun DocumentTypeField(value: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedTextField(value, onChange, label = { Text("Documento") }, modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("Elige un tipo o escribe uno personalizado.") },
            trailingIcon = { TextButton(onClick = { expanded = true }) { Text("Tipo") } })
        DropdownMenu(expanded, { expanded = false }) {
            messageDocumentTypes.forEach { type -> DropdownMenuItem(text = { Text(type) }, onClick = { onChange(type.lowercase()); expanded = false }) }
            DropdownMenuItem(text = { Text("✏️ Escribir otro tipo") }, onClick = { onChange(""); expanded = false })
        }
    }
}
