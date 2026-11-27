package com.example.gestorempresa.ui.advanced.communication

import com.example.gestorempresa.domain.phone.InternationalPhones

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
    MessageTemplate("Documentación pendiente", "Hola [NOMBRE], necesitamos que nos entregues este documento: [DOCUMENTO]."),
    MessageTemplate("Cambio de horario", "Hola [NOMBRE], a partir del [FECHA] tu horario será de [HORA_INICIO] a [HORA_FIN]. ¿Puedes confirmarlo?"),
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
 * Los prefijos explícitos se respetan. Solo los contactos antiguos sin prefijo
 * conservan la compatibilidad con números nacionales españoles de 9 dígitos.
 */
fun whatsappNumber(phone: String): String? {
    val text = phone.trim()
    if (text.startsWith("+") || text.startsWith("00")) {
        return InternationalPhones.internationalE164(text)?.removePrefix("+")
    }
    var digits = phone.trim().let { if (it.startsWith("+")) it.drop(1) else it }.filter { it.isDigit() }
    if (digits.startsWith("00")) digits = digits.drop(2)
    if (digits.length == 9) digits = "34$digits"
    return digits.takeIf { it.length in 10..15 }
}

/**
 * Abre WhatsApp o el correo con el mensaje ya escrito; el administrador solo tiene que pulsar enviar.
 * «WhatsApp + Email» NO se abre aquí: lanzar dos apps a la vez (startActivities) dejaba la segunda
 * oculta o perdida, porque WhatsApp y Gmail se abren en su propia tarea. Se usa [SendProgress], que
 * abre un canal y continúa con el siguiente cuando el usuario vuelve.
 */
fun sendMessage(context: Context, channel: Channel, phone: String, email: String, subject: String, message: String): Boolean {
    require(channel != Channel.BOTH) { "Usa SendProgress para enviar por WhatsApp y Email" }
    val problem = openChannel(context, channel, phone, email, subject, message)
    if (problem != null) Toast.makeText(context, problem, Toast.LENGTH_SHORT).show()
    return problem == null
}

/** Llamada de teléfono (abre el marcador; no llama sin que el usuario lo confirme). */
fun dial(context: Context, phone: String) {
    try { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.filter { it.isDigit() || it == '+' }}"))) }
    catch (_: ActivityNotFoundException) { Toast.makeText(context, "No hay ninguna app de teléfono", Toast.LENGTH_SHORT).show() }
}
