package com.example.gestorempresa.ui.home

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun MessagingScreen(state: HomeWorkspaceState, onBack: () -> Unit) {
    val context = LocalContext.current
    val templates = linkedMapOf(
        "Indicaciones para hoy" to "Hola [nombre], hoy debes acudir a [lugar] a las [hora].",
        "Asignación a un servicio" to "Hola [nombre], te hemos asignado al servicio [servicio] el día [fecha].",
        "Recordatorio de documentación" to "Hola [nombre], recuerda revisar la documentación pendiente: [documento].",
        "Confirmar horario" to "Hola [nombre], ¿puedes confirmar que has recibido tu horario?",
        "Mensaje personalizado" to ""
    )
    var selectedTemplate by rememberSaveable { mutableStateOf(templates.keys.first()) }
    var message by rememberSaveable { mutableStateOf(templates.values.first()) }
    val people = state.employees.map {
        Contact(it.id, "${it.nombre} ${it.apellidos}", "Empleado · ${it.oficio}", it.telefono, it.email)
    } + state.subcontractors.map {
        Contact(-it.id, it.nombreComercial, "Subcontratado · ${it.personaContacto}", it.telefono, it.email)
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        item { Text("Mensajería", style = MaterialTheme.typography.headlineMedium) }
        item { Text("Contacta con empleados y subcontratados por WhatsApp o correo.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
                    val personalizedMessage = message.replace("[nombre]", person.name)
                    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = person.phone.isNotBlank() && personalizedMessage.isNotBlank(), onClick = {
                            val number = person.phone.filter { it.isDigit() }
                            if (number.isNotEmpty()) try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number?text=${Uri.encode(personalizedMessage)}")))
                            } catch (_: ActivityNotFoundException) { }
                        }) { Text("WhatsApp") }
                        Button(enabled = person.email.isNotBlank() && personalizedMessage.isNotBlank(), onClick = {
                            try { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", person.email, null))
                                .putExtra(Intent.EXTRA_SUBJECT, "Mensaje de empresa").putExtra(Intent.EXTRA_TEXT, personalizedMessage)) }
                            catch (_: ActivityNotFoundException) { }
                        }) { Text("Correo") }
                    }
                }
            }
        }
        item { androidx.compose.material3.TextButton(onClick = onBack) { Text("Volver a Inicio") } }
    }
}

private data class Contact(val id: Long, val name: String, val description: String, val phone: String, val email: String)
