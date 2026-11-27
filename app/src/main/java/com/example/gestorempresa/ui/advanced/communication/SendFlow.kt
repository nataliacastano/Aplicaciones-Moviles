package com.example.gestorempresa.ui.advanced.communication

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Estado de cada paso del envío. Android y las apps externas solo permiten saber que se ABRIÓ la
 * pantalla de redacción, no que el mensaje se enviara: por eso nunca se marca «enviado» por sí solo.
 */
enum class SendStatus(val label: String) {
    PENDING("Pendiente"),
    OPENED("Abierto para enviar · no verificado"),
    CONFIRMED("Enviado (confirmado por ti)"),
    NOT_SENT("No enviado"),
    FAILED("No se pudo abrir")
}

data class SendStep(val channel: Channel, val status: SendStatus = SendStatus.PENDING, val detail: String = "")

/** Plan de envío: uno o dos pasos (WhatsApp y después Email) que se abren de uno en uno. */
data class SendPlan(val steps: List<SendStep>) {
    /** Paso en curso: el primero que no ha terminado. */
    val current: SendStep? get() = steps.firstOrNull { it.status == SendStatus.PENDING || it.status == SendStatus.OPENED }
    val finished: Boolean get() = current == null
    fun update(channel: Channel, status: SendStatus, detail: String = "") =
        copy(steps = steps.map { if (it.channel == channel) it.copy(status = status, detail = detail) else it })

    /** Resumen final honesto: cuántos se confirmaron y cuántos quedan sin verificar. */
    fun summary(): String = steps.joinToString(" · ") { "${it.channel.label}: ${it.status.label}" }
}

/** «WhatsApp + Email» se convierte en dos pasos que se abren de uno en uno. */
fun planFor(channel: Channel): SendPlan = SendPlan(when (channel) {
    Channel.BOTH -> listOf(SendStep(Channel.WHATSAPP), SendStep(Channel.EMAIL))
    else -> listOf(SendStep(channel))
})

/** Intent que abre la redacción en un canal concreto (WhatsApp o Email), o null si faltan datos. */
fun composeIntent(channel: Channel, phone: String, email: String, subject: String, message: String): Intent? = when (channel) {
    Channel.WHATSAPP -> whatsappNumber(phone)?.let { Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$it?text=${Uri.encode(message)}")) }
    Channel.EMAIL -> email.trim().takeIf { it.isNotBlank() }?.let { address ->
        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
            .putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, message)
    }
    Channel.BOTH -> error("«WhatsApp + Email» se abre en dos pasos con planFor()")
}

/** Abre un canal. Devuelve null si se abrió o el motivo por el que no se pudo abrir. */
fun openChannel(context: Context, channel: Channel, phone: String, email: String, subject: String, message: String): String? {
    val intent = composeIntent(channel, phone, email, subject, message)
        ?: return if (channel == Channel.EMAIL) "El contacto no tiene correo." else "El teléfono no es válido para WhatsApp."
    return try { context.startActivity(intent); null } catch (_: ActivityNotFoundException) {
        if (channel == Channel.EMAIL) "No hay ninguna app de correo instalada." else "No se ha podido abrir WhatsApp ni el navegador."
    }
}

/**
 * Progreso del envío paso a paso. Abre un canal; cuando el usuario vuelve a la app, pregunta si
 * llegó a enviarlo (no hay forma fiable de comprobarlo) y entonces ofrece continuar con el siguiente.
 */
@Composable
fun SendProgress(plan: SendPlan, onPlanChange: (SendPlan) -> Unit, phone: String, email: String, subject: String, message: String,
    onFinish: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Canal abierto del que se espera la vuelta del usuario (se conserva si Android recrea la pantalla).
    var awaiting by rememberSaveable { mutableStateOf<Channel?>(null) }
    val latestPlan by rememberUpdatedState(plan)
    val latestOnPlanChange by rememberUpdatedState(onPlanChange)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            val channel = awaiting
            if (event == Lifecycle.Event.ON_RESUME && channel != null) {
                awaiting = null
                latestOnPlanChange(latestPlan.update(channel, SendStatus.OPENED, "¿Llegaste a enviarlo?"))
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Envío paso a paso", style = MaterialTheme.typography.titleMedium)
        Text("La app abre cada canal con el mensaje preparado; tú lo envías desde WhatsApp o tu correo. " +
            "Android no informa de si se envió, así que te lo preguntaremos al volver.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        plan.steps.forEachIndexed { index, step ->
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}. ${step.channel.label}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        Text(step.status.label, style = MaterialTheme.typography.labelMedium, color = statusColor(step.status))
                    }
                    if (step.detail.isNotBlank()) Text(step.detail, style = MaterialTheme.typography.bodySmall)
                    val isCurrent = plan.current == step
                    when {
                        isCurrent && step.status == SendStatus.PENDING -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                val problem = openChannel(context, step.channel, phone, email, subject, message)
                                if (problem == null) awaiting = step.channel
                                else onPlanChange(plan.update(step.channel, SendStatus.FAILED, problem))
                            }) { Text("Abrir ${step.channel.label}") }
                            TextButton(onClick = { onPlanChange(plan.update(step.channel, SendStatus.NOT_SENT, "Omitido por ti.")) }) { Text("Omitir") }
                        }
                        isCurrent && step.status == SendStatus.OPENED -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onPlanChange(plan.update(step.channel, SendStatus.CONFIRMED)) }) { Text("Sí, lo envié") }
                            OutlinedButton(onClick = { onPlanChange(plan.update(step.channel, SendStatus.NOT_SENT, "Cancelado o sin enviar.")) }) { Text("No") }
                            TextButton(onClick = {
                                val problem = openChannel(context, step.channel, phone, email, subject, message)
                                if (problem == null) awaiting = step.channel
                            }) { Text("Reabrir") }
                        }
                    }
                }
            }
        }
        if (plan.finished) {
            Text(plan.summary(), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text("Terminar") }
        }
    }
}

@Composable
private fun statusColor(status: SendStatus): Color = when (status) {
    SendStatus.CONFIRMED -> Color(0xFF10B981)
    SendStatus.OPENED -> Color(0xFFF59E0B)
    SendStatus.FAILED, SendStatus.NOT_SENT -> MaterialTheme.colorScheme.error
    SendStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
}
