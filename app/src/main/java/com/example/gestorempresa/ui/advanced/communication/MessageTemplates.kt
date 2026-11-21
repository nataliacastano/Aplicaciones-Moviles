package com.example.gestorempresa.ui.advanced.communication

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Plantilla de mensaje. Los huecos van entre corchetes: [LUGAR], [HORA], [FECHA]… */
data class MessageTemplate(val title: String, val text: String)

const val CUSTOM_TEMPLATE = "✏️ Crear mensaje personalizado"

val messageTemplates = listOf(
    MessageTemplate("Acudir a un lugar", "Hola [NOMBRE], el [DÍA] debes acudir a [LUGAR] a las [HORA]."),
    MessageTemplate("Asignación a un servicio", "Hola [NOMBRE], has sido asignado al servicio [SERVICIO] el día [FECHA]."),
    MessageTemplate("Reconocimiento médico", "Hola [NOMBRE], tu reconocimiento médico está próximo a caducar ([FECHA]). Por favor, pide cita lo antes posible."),
    MessageTemplate("Curso próximo a caducar", "Hola [NOMBRE], tu curso [CURSO] caduca el [FECHA]. Avísanos para organizar la renovación."),
    MessageTemplate("Documentación pendiente", "Hola [NOMBRE], necesitamos que nos entregues la siguiente documentación: [DOCUMENTO]."),
    MessageTemplate("Cambio de horario", "Hola [NOMBRE], a partir del [FECHA] tu horario será [HORARIO]. ¿Puedes confirmarlo?"),
    MessageTemplate(CUSTOM_TEMPLATE, "")
)

/** Medio por el que se envía el mensaje. */
enum class Channel(val label: String) { WHATSAPP("WhatsApp"), EMAIL("Email"), BOTH("WhatsApp + Email") }

private val placeholderRegex = Regex("\\[([^\\[\\]]+)]")

/** Huecos que aparecen en el texto, sin repetir y en orden: «[LUGAR] a las [HORA]» → LUGAR, HORA. */
fun placeholdersOf(text: String): List<String> = placeholderRegex.findAll(text).map { it.groupValues[1] }.distinct().toList()

/** Sustituye los huecos que tienen valor; los vacíos se dejan tal cual para que se vea qué falta. */
fun fillTemplate(text: String, values: Map<String, String>): String =
    placeholderRegex.replace(text) { match -> values[match.groupValues[1]]?.takeIf { it.isNotBlank() } ?: match.value }

/**
 * Prepara el número para WhatsApp (wa.me exige el prefijo del país y solo dígitos).
 * Si el número tiene 9 dígitos se entiende que es de España y se añade el 34.
 */
fun whatsappNumber(phone: String): String? {
    var digits = phone.trim().let { if (it.startsWith("+")) it.drop(1) else it }.filter { it.isDigit() }
    if (digits.startsWith("00")) digits = digits.drop(2)
    if (digits.length == 9) digits = "34$digits"
    return digits.takeIf { it.length in 10..15 }
}

/** Abre WhatsApp, el correo o ambos con el mensaje ya escrito; el administrador solo tiene que pulsar enviar. */
fun sendMessage(context: Context, channel: Channel, phone: String, email: String, subject: String, message: String): Boolean {
    val whatsapp = whatsappNumber(phone)?.let { number ->
        Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number?text=${Uri.encode(message)}"))
    }
    val mail = email.trim().takeIf { it.isNotBlank() }?.let { address ->
        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
            .putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, message)
    }
    val intents = when (channel) {
        Channel.WHATSAPP -> listOfNotNull(whatsapp)
        Channel.EMAIL -> listOfNotNull(mail)
        // Se abre primero WhatsApp; al volver atrás aparece el correo ya preparado.
        Channel.BOTH -> listOfNotNull(mail, whatsapp)
    }
    if (intents.isEmpty()) {
        Toast.makeText(context, if (channel == Channel.EMAIL) "Este contacto no tiene correo" else "El teléfono no es válido para WhatsApp", Toast.LENGTH_SHORT).show()
        return false
    }
    return try {
        if (intents.size == 1) context.startActivity(intents.first()) else context.startActivities(intents.toTypedArray())
        true
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No hay ninguna app para enviar el mensaje", Toast.LENGTH_SHORT).show()
        false
    }
}

/** Llamada de teléfono (abre el marcador; no llama sin que el usuario lo confirme). */
fun dial(context: Context, phone: String) {
    try { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.filter { it.isDigit() || it == '+' }}"))) }
    catch (_: ActivityNotFoundException) { Toast.makeText(context, "No hay ninguna app de teléfono", Toast.LENGTH_SHORT).show() }
}
