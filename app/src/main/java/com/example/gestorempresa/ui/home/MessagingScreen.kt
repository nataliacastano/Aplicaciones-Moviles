package com.example.gestorempresa.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.example.gestorempresa.ui.advanced.communication.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun MessagingScreen(state: HomeWorkspaceState, onBack: () -> Unit) {
    val context = LocalContext.current
    // Plantillas compartidas con el botón de comunicación de las fichas (funciones avanzadas).
    val templates = messageTemplates.associate { it.title to it.text }
    var selectedTemplate by rememberSaveable { mutableStateOf(templates.keys.first()) }
    var message by rememberSaveable { mutableStateOf(templates.values.first()) }
    var composingFor by remember { mutableStateOf<Contact?>(null) }
    val people = state.employees.map {
        Contact(it.id, "${it.nombre} ${it.apellidos}", "Empleado · ${it.oficio}", it.telefono, it.email)
    } + state.subcontractors.map {
        Contact(-it.id, it.nombreComercial, "Subcontratado · ${it.personaContacto}", it.telefono, it.email)
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        item { Text("Mensajería", style = MaterialTheme.typography.headlineMedium) }
        item { Text("Contacta con empleados y subcontratados por WhatsApp, correo o ambos. Los huecos como [LUGAR] o [HORA] se pueden rellenar con «Personalizar».", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { ChoiceField("Mensajes frecuentes", selectedTemplate, templates.keys.toList(), true) { choice -> selectedTemplate = choice; message = templates[choice].orEmpty() } }
        item { OutlinedTextField(value = message, onValueChange = { message = it }, label = { Text("Mensaje (puedes editarlo)") }, minLines = 3, modifier = Modifier.fillMaxWidth()) }
        if (people.isEmpty()) item { EmptyCatalog("Aún no hay empleados ni subcontratados para contactar.") }
        items(people, key = { it.id }) { person ->
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(person.name, style = MaterialTheme.typography.titleMedium)
                    Text(person.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (person.phone.isNotBlank()) Text(person.phone)
                    if (person.email.isNotBlank()) Text(person.email)
                    val personalizedMessage = fillTemplate(message, mapOf("NOMBRE" to person.name.substringBefore(' ')))
                    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = whatsappNumber(person.phone) != null && personalizedMessage.isNotBlank(), onClick = {
                            sendMessage(context, Channel.WHATSAPP, person.phone, person.email, "Mensaje de empresa", personalizedMessage)
                        }) { Text("WhatsApp") }
                        Button(enabled = person.email.isNotBlank() && personalizedMessage.isNotBlank(), onClick = {
                            sendMessage(context, Channel.EMAIL, person.phone, person.email, "Mensaje de empresa", personalizedMessage)
                        }) { Text("Correo") }
                    }
                    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = whatsappNumber(person.phone) != null && person.email.isNotBlank() && personalizedMessage.isNotBlank(), onClick = {
                            sendMessage(context, Channel.BOTH, person.phone, person.email, "Mensaje de empresa", personalizedMessage)
                        }) { Text("WhatsApp + Email") }
                        androidx.compose.material3.TextButton(onClick = { composingFor = person }) { Text("Personalizar") }
                    }
                }
            }
        }
        item { androidx.compose.material3.TextButton(onClick = onBack) { Text("Volver a Inicio") } }
    }
    composingFor?.let { person ->
        // Los subcontratados usan id negativo en esta lista para no chocar con los empleados.
        MessageComposerDialog(ContactInfo(if (person.id < 0) "SUBCONTRATADO" else "EMPLEADO", kotlin.math.abs(person.id), person.name,
            person.phone, person.email), state) { composingFor = null }
    }
}

private data class Contact(val id: Long, val name: String, val description: String, val phone: String, val email: String)
