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
        "HORA" to "08:00",
        "HORA_INICIO" to "08:00",
        "HORA_FIN" to "16:00",
        "CURSO" to (course?.title ?: ""),
        "DOCUMENTO" to (document?.title ?: ""),
        "HORARIO" to "de 08:00 a 16:00"
    )
}

/** Botones de comunicación de la ficha: llamar y enviar mensaje por WhatsApp, email o ambos. */
@Composable
fun ContactCard(contact: ContactInfo, state: HomeWorkspaceState, templates: MessageTemplateStore) {
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
    if (composing) MessageComposerDialog(contact, state, templates) { composing = false }
}

/**
 * Redacta un mensaje a partir de una plantilla (o uno personalizado), rellena los huecos
 * y lo envía por el medio elegido: WhatsApp, Email o WhatsApp + Email.
 */
@Composable
fun MessageComposerDialog(contact: ContactInfo, state: HomeWorkspaceState, templates: MessageTemplateStore,
    initialTemplate: String? = null, initialValues: Map<String, String> = emptyMap(), initialText: String? = null,
    initialChannel: Channel? = null, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val available by templates.templates.collectAsState()
    var templateTitle by rememberSaveable { mutableStateOf(initialTemplate?.takeIf { t -> available.any { it.title == t } } ?: available.first().title) }
    val template = available.first { it.title == templateTitle }
    // Texto de ESTE mensaje: empieza como la plantilla; cambiarlo no modifica la plantilla guardada.
    var text by rememberSaveable(templateTitle) { mutableStateOf(initialText?.takeIf { templateTitle == initialTemplate } ?: template.text) }
    var savedNotice by remember { mutableStateOf<String?>(null) }
    val suggestions = remember(templateTitle, state) { suggestedValues(state, contact, template) }
    val values = remember(templateTitle) { mutableStateMapOf<String, String>().apply { putAll(suggestions); if (templateTitle == initialTemplate) putAll(initialValues) } }
    var subject by rememberSaveable { mutableStateOf("Mensaje de la empresa") }
    var channel by rememberSaveable { mutableStateOf(initialChannel ?: if (whatsappNumber(contact.phone) != null) Channel.WHATSAPP else Channel.EMAIL) }
    // EDITAR → ENVIAR. El plan guarda el estado de cada canal.
    var sendPlan by remember { mutableStateOf<SendPlan?>(null) }
    // Vista previa antes de abrir nada. Al volver a editar se conserva todo lo escrito.
    var previewing by rememberSaveable { mutableStateOf(false) }
    val placeholders = placeholdersOf(text)
    val finalMessage = fillTemplate(text, values)
    val missing = placeholdersOf(finalMessage)
    // Horas (HH:mm, inicio antes que fin) y fechas válidas antes de poder enviar.
    val fieldErrors = messageFieldErrors(values, placeholders)
    var triedToSend by remember { mutableStateOf(false) }
    val hasWhatsapp = whatsappNumber(contact.phone) != null
    val hasEmail = contact.email.isNotBlank()

    val plan = sendPlan
    if (plan != null) {
        AlertDialog(onDismissRequest = {}, title = { Text("Mensaje para ${contact.name}") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                SendProgress(plan, { sendPlan = it }, contact.phone, contact.email, subject, finalMessage, onFinish = onDismiss)
            } },
            confirmButton = {}, dismissButton = { if (!plan.finished) TextButton(onClick = onDismiss) { Text("Cerrar") } })
        return
    }
    if (previewing) {
        AlertDialog(onDismissRequest = { previewing = false }, title = { Text("Revisa antes de enviar") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                MessagePreview(contact, channel, subject, finalMessage, values.filterKeys { it in placeholders })
            } },
            confirmButton = { Button(onClick = { previewing = false; sendPlan = planFor(channel) }) {
                Text(if (channel == Channel.BOTH) "Empezar envío" else "Abrir ${channel.label}") } },
            dismissButton = { TextButton(onClick = { previewing = false }) { Text("Editar") } })
        return
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Mensaje para ${contact.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ChoiceField("Plantilla", templateTitle, available.map { it.title }, true) { templateTitle = it; savedNotice = null }
                OutlinedTextField(text, { text = it }, label = { Text(if (template.title == CUSTOM_TEMPLATE) "Escribe tu mensaje" else "Texto de la plantilla") },
                    minLines = 3, modifier = Modifier.fillMaxWidth())
                if (template.title != CUSTOM_TEMPLATE) TemplateEditActions(edited = text != template.text, customized = templates.isCustomized(template.title),
                    onSaveTemplate = { templates.save(template.title, text); savedNotice = "Plantilla guardada para futuros mensajes." },
                    onUndoEdit = { text = template.text },
                    onRestore = { templates.restore(template.title); text = messageTemplates.first { it.title == template.title }.text
                        savedNotice = "Se ha restaurado la plantilla original." })
                savedNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                if (template.title == CUSTOM_TEMPLATE) Text("Puedes usar huecos como [NOMBRE] o [FECHA] y se rellenarán abajo.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                placeholders.forEach { key ->
                    MessageFieldEditor(key, values[key].orEmpty(), { values[key] = it }, contact, state, fieldErrors[key], showErrors = triedToSend)
                }
                fieldErrors.values.firstOrNull()?.takeIf { triedToSend }?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
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
                val ready = finalMessage.isNotBlank() && missing.isEmpty() && fieldErrors.isEmpty()
                Text("Canal", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Channel.entries.forEach { option ->
                    val possible = when (option) { Channel.WHATSAPP -> hasWhatsapp; Channel.EMAIL -> hasEmail; Channel.BOTH -> hasWhatsapp && hasEmail }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = channel == option, enabled = possible, onClick = { channel = option })
                        Text(option.label + if (!possible) " (no disponible)" else "", color = if (possible) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val channelPossible = when (channel) { Channel.WHATSAPP -> hasWhatsapp; Channel.EMAIL -> hasEmail; Channel.BOTH -> hasWhatsapp && hasEmail }
                // Si falta algo se marca en rojo al intentarlo, en lugar de dejar el botón apagado sin explicación.
                Button(enabled = finalMessage.isNotBlank() && channelPossible, onClick = {
                    triedToSend = true
                    if (ready) previewing = true
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Vista previa")
                }
            }
        },
        confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

/**
 * Diferencia las dos formas de cambiar una plantilla:
 * - Editar solo este mensaje (lo que ya ocurre al escribir en el campo).
 * - Guardar cambios en plantilla: se conserva para los próximos mensajes, también tras cerrar la app.
 */
@Composable
private fun TemplateEditActions(edited: Boolean, customized: Boolean, onSaveTemplate: () -> Unit, onUndoEdit: () -> Unit, onRestore: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (edited) {
            Text("✏️ Has editado este mensaje. La plantilla no cambia a menos que la guardes.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onSaveTemplate) { Text("Guardar cambios en plantilla") }
                TextButton(onClick = onUndoEdit) { Text("Deshacer") }
            }
        } else if (customized) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Plantilla modificada por ti.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onRestore) { Text("Restaurar original") }
        }
    }
}

/**
 * Resumen de la comunicación antes de abrir WhatsApp o el correo: destinatario, contacto, canal,
 * mensaje completo y los datos clave (lugar, fecha, horas y documento cuando corresponden).
 */
@Composable
fun MessagePreview(contact: ContactInfo, channel: Channel, subject: String, message: String, values: Map<String, String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PreviewRow("Destinatario", contact.name)
        if (channel != Channel.EMAIL) PreviewRow("Teléfono (WhatsApp)", contact.phone)
        if (channel != Channel.WHATSAPP) PreviewRow("Correo", contact.email)
        PreviewRow("Canal", if (channel == Channel.BOTH) "WhatsApp y después Email" else channel.label)
        if (channel != Channel.WHATSAPP) PreviewRow("Asunto", subject)
        listOf("LUGAR", "FECHA", "DÍA", "HORA", "HORA_INICIO", "HORA_FIN", "DOCUMENTO", "SERVICIO", "CURSO").forEach { key ->
            values[key]?.takeIf { it.isNotBlank() }?.let { PreviewRow(placeholderLabel(key), it) }
        }
        Text("Mensaje", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(message, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Text("Se abrirá la app con el mensaje preparado; el envío lo haces tú desde ella.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PreviewRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge)
    }
}
