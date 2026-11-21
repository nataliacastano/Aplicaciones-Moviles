package com.example.gestorempresa.ui.advanced.communication

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.gestorempresa.ui.home.ChoiceField
import com.example.gestorempresa.ui.home.HomeWorkspaceState
import java.time.LocalDate

/** Datos de contacto de la persona a la que se escribe. */
data class ContactInfo(val kind: String, val id: Long, val name: String, val phone: String, val email: String)

/**
 * Valores propuestos para los huecos de las plantillas a partir de los datos de la app:
 * el servicio asignado, su dirección, el próximo reconocimiento médico, el curso o el documento pendiente.
 */
fun suggestedValues(state: HomeWorkspaceState, contact: ContactInfo, template: MessageTemplate, today: LocalDate = LocalDate.now()): Map<String, String> {
    val firstName = contact.name.substringBefore(' ').ifBlank { contact.name }
    val project = state.assignments.filter { it.workerKind == contact.kind && it.workerId == contact.id }
        .mapNotNull { a -> state.projects.firstOrNull { it.id == a.projectId } }
        .sortedBy { if (it.estado == "ACTIVO") 0 else 1 }.firstOrNull()
    val pending = state.obligations.filter { it.workerKind == contact.kind && it.workerId == contact.id && !it.resolved }.sortedBy { it.dueDate ?: "9999" }
    val medical = pending.firstOrNull { it.kind == "MEDICO" }
    val course = pending.firstOrNull { it.kind == "CURSO" }
    val document = pending.firstOrNull { it.kind == "DOCUMENTO" }
    val date = when (template.title) {
        "Reconocimiento médico" -> medical?.dueDate
        "Curso próximo a caducar" -> course?.dueDate
        else -> null
    } ?: today.plusDays(1).toString()
    return mapOf(
        "NOMBRE" to firstName,
        "SERVICIO" to (project?.titulo ?: ""),
        "LUGAR" to (project?.direccionUbicacion ?: ""),
        "FECHA" to date,
        "DÍA" to "lunes",
        "HORA" to "8:00",
        "CURSO" to (course?.title ?: ""),
        "DOCUMENTO" to (document?.title ?: ""),
        "HORARIO" to "de 8:00 a 16:00"
    )
}

/** Botones de comunicación de la ficha: llamar y enviar mensaje por WhatsApp, email o ambos. */
@Composable
fun ContactCard(contact: ContactInfo, state: HomeWorkspaceState) {
    val context = LocalContext.current
    var composing by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("💬 Comunicación", style = MaterialTheme.typography.titleMedium)
            Text(listOf(contact.phone, contact.email).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Sin teléfono ni correo" },
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = contact.phone.isNotBlank() || contact.email.isNotBlank(), onClick = { composing = true }) { Text("Enviar mensaje") }
                OutlinedButton(enabled = contact.phone.isNotBlank(), onClick = { dial(context, contact.phone) }) { Text("Llamar") }
            }
        }
    }
    if (composing) MessageComposerDialog(contact, state) { composing = false }
}

/**
 * Redacta un mensaje a partir de una plantilla (o uno personalizado), rellena los huecos
 * y lo envía por el medio elegido: WhatsApp, Email o WhatsApp + Email.
 */
@Composable
fun MessageComposerDialog(contact: ContactInfo, state: HomeWorkspaceState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var templateTitle by rememberSaveable { mutableStateOf(messageTemplates.first().title) }
    val template = messageTemplates.first { it.title == templateTitle }
    var text by rememberSaveable(templateTitle) { mutableStateOf(template.text) }
    val suggestions = remember(templateTitle, state) { suggestedValues(state, contact, template) }
    val values = remember(templateTitle) { mutableStateMapOf<String, String>().apply { putAll(suggestions) } }
    var subject by rememberSaveable { mutableStateOf("Mensaje de la empresa") }
    val placeholders = placeholdersOf(text)
    val finalMessage = fillTemplate(text, values)
    val missing = placeholdersOf(finalMessage)
    val hasWhatsapp = whatsappNumber(contact.phone) != null
    val hasEmail = contact.email.isNotBlank()

    AlertDialog(onDismissRequest = onDismiss, title = { Text("Mensaje para ${contact.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ChoiceField("Plantilla", templateTitle, messageTemplates.map { it.title }, true) { templateTitle = it }
                OutlinedTextField(text, { text = it }, label = { Text(if (template.title == CUSTOM_TEMPLATE) "Escribe tu mensaje" else "Texto de la plantilla") },
                    minLines = 3, modifier = Modifier.fillMaxWidth())
                if (template.title == CUSTOM_TEMPLATE) Text("Puedes usar huecos como [NOMBRE] o [FECHA] y se rellenarán abajo.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                placeholders.forEach { key ->
                    OutlinedTextField(values[key].orEmpty(), { values[key] = it }, label = { Text(key.lowercase().replaceFirstChar(Char::uppercase)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                if (hasEmail) OutlinedTextField(subject, { subject = it }, label = { Text("Asunto del email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Vista previa", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(finalMessage.ifBlank { "(mensaje vacío)" }, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                if (missing.isNotEmpty()) Text("Falta rellenar: ${missing.joinToString()}", color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
                if (!hasWhatsapp) Text("WhatsApp no disponible: el teléfono no es válido.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!hasEmail) Text("Email no disponible: no hay correo.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                val ready = finalMessage.isNotBlank() && missing.isEmpty()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(enabled = ready && hasWhatsapp, onClick = {
                        if (sendMessage(context, Channel.WHATSAPP, contact.phone, contact.email, subject, finalMessage)) onDismiss()
                    }, modifier = Modifier.fillMaxWidth()) { Text("📱 WhatsApp") }
                    Button(enabled = ready && hasEmail, onClick = {
                        if (sendMessage(context, Channel.EMAIL, contact.phone, contact.email, subject, finalMessage)) onDismiss()
                    }, modifier = Modifier.fillMaxWidth()) { Text("✉️ Email") }
                    OutlinedButton(enabled = ready && hasWhatsapp && hasEmail, onClick = {
                        if (sendMessage(context, Channel.BOTH, contact.phone, contact.email, subject, finalMessage)) onDismiss()
                    }, modifier = Modifier.fillMaxWidth()) { Text("📱 + ✉️ WhatsApp + Email") }
                }
            }
        },
        confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}
