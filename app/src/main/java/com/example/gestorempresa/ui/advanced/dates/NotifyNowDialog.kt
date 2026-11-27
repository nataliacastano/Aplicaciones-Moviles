package com.example.gestorempresa.ui.advanced.dates

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.gestorempresa.ui.advanced.communication.ContactInfo
import com.example.gestorempresa.ui.advanced.communication.MessageComposerDialog
import com.example.gestorempresa.ui.advanced.communication.MessageTemplateStore
import com.example.gestorempresa.ui.home.CatalogKind
import com.example.gestorempresa.ui.home.HomeWorkspaceState
import java.time.LocalDate

/** Persona a la que se puede avisar de un vencimiento, si el vencimiento es de un trabajador. */
fun HomeWorkspaceState.contactFor(item: DueItem): ContactInfo? {
    val target = item.target ?: return null
    return when (target.kind) {
        CatalogKind.EMPLOYEES -> employees.firstOrNull { it.id == target.id }?.let { ContactInfo("EMPLEADO", it.id, "${it.nombre} ${it.apellidos}", it.telefono, it.email) }
        CatalogKind.SUBCONTRACTORS -> subcontractors.firstOrNull { it.id == target.id }?.let {
            ContactInfo("SUBCONTRATADO", it.id, it.personaContacto.ifBlank { it.nombreComercial }, it.telefono, it.email) }
        else -> null
    }
}

/** Plantilla y datos con los que se prepara el aviso a un trabajador sobre su vencimiento. */
fun dueMessagePreset(item: DueItem): Pair<String, Map<String, String>> = when (item.kind) {
    DueKind.MEDICO -> "Reconocimiento médico" to mapOf("FECHA" to item.date.toString())
    DueKind.CURSO -> "Curso próximo a caducar" to mapOf("CURSO" to item.title, "FECHA" to item.date.toString())
    else -> "Documentación pendiente" to mapOf("DOCUMENTO" to item.title)
}

/**
 * Antes de «Enviar ahora el aviso» se explica qué hace realmente: qué vencimientos se comunican,
 * a quién (al administrador, con una notificación en este móvil), por qué canal y con qué texto.
 * Nada se muestra ni se envía hasta pulsar el botón de confirmación. Desde aquí también se puede
 * preparar un mensaje para el trabajador afectado (con su propia vista previa).
 */
@Composable
fun NotifyNowDialog(state: HomeWorkspaceState, today: LocalDate, warnDays: Int, includeExpiries: Boolean, includeTasks: Boolean,
    templates: MessageTemplateStore, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val items = remember(state, today, warnDays, includeExpiries, includeTasks) { state.pendingWarnings(today, warnDays, includeExpiries, includeTasks) }
    val content = DueDateNotifications.content(items, today, warnDays)
    val canNotify = DueDateNotifications.canNotify(context)
    var messaging by remember { mutableStateOf<DueItem?>(null) }

    AlertDialog(onDismissRequest = onDismiss, title = { Text("Aviso de vencimientos") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Section("A quién", "A ti, el administrador, en este móvil. No se envía nada a los trabajadores.")
                Section("Canal", "Notificación del sistema." + if (!canNotify) " ⚠️ Las notificaciones de la app están desactivadas: no se verá." else "")
                Text("Qué se comunica (${items.size})", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!includeExpiries && !includeTasks) Text("Los avisos están desactivados en Ajustes.", color = MaterialTheme.colorScheme.error)
                if (items.isEmpty()) Text("No hay nada vencido ni próximo a vencer: no se mostrará ninguna notificación.")
                items.forEach { item ->
                    val contact = state.contactFor(item)
                    Column {
                        Text("${item.date} · ${item.title}", style = MaterialTheme.typography.bodyLarge)
                        Text("${item.kind.label} · ${item.detail} · ${item.status(today, warnDays).label}", style = MaterialTheme.typography.bodySmall,
                            color = statusColor(item.status(today, warnDays)))
                        if (contact != null) TextButton(onClick = { messaging = item }, contentPadding = PaddingValues(0.dp)) {
                            Text("Preparar aviso para ${contact.name}…")
                        }
                    }
                }
                if (items.isNotEmpty()) {
                    Text("Texto de la notificación", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(content.title, style = MaterialTheme.typography.titleSmall)
                            content.lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                            content.more?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    Text("En la pantalla de bloqueo solo se verá «Vencimientos pendientes», sin nombres.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { if (items.isNotEmpty()) Button(onClick = onConfirm) { Text("Mostrar notificación ahora") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (items.isEmpty()) "Cerrar" else "Cancelar") } })

    messaging?.let { item ->
        state.contactFor(item)?.let { contact ->
            val (template, values) = dueMessagePreset(item)
            MessageComposerDialog(contact, state, templates, onDismiss = { messaging = null }, initialTemplate = template, initialValues = values)
        }
    }
}

@Composable
private fun Section(label: String, text: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
