package com.example.gestorempresa.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gestorempresa.data.local.HomeMovement
import com.example.gestorempresa.data.local.entities.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

internal fun positiveNumber(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
    ?.takeIf { it.isFinite() && it > 0 && it <= 1_000_000_000 }
internal fun moneyCents(text: String): Long? = try {
    BigDecimal(text.trim().replace(',', '.')).setScale(2, RoundingMode.UNNECESSARY)
        .movePointRight(2).longValueExact().takeIf { it > 0 && it <= 100_000_000_000L }
} catch (_: Exception) { null }
internal fun validDate(value: String): Boolean = try {
    value.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && LocalDate.parse(value).toString() == value
} catch (_: Exception) { false }

@Composable
fun HomeEntryForm(kind: String, vm: HomeWorkspaceViewModel, state: HomeWorkspaceState, onBack: () -> Unit) {
    key(kind) {
        var title by rememberSaveable { mutableStateOf("") }
        var surname by rememberSaveable { mutableStateOf("") }
        var trade by rememberSaveable { mutableStateOf("") }
        var type by rememberSaveable { mutableStateOf(if (kind == "project-new") "SERVICIO" else "FIJO") }
        var phone by rememberSaveable { mutableStateOf("") }
        var email by rememberSaveable { mutableStateOf("") }
        var client by rememberSaveable { mutableStateOf("") }
        var location by rememberSaveable { mutableStateOf("") }
        var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
        var endDate by rememberSaveable { mutableStateOf("") }
        var amount by rememberSaveable { mutableStateOf("") }
        var rate by rememberSaveable { mutableStateOf("0") }
        var income by rememberSaveable { mutableStateOf(false) }
        var projectId by rememberSaveable { mutableStateOf<Long?>(null) }
        var employeeId by rememberSaveable { mutableStateOf<Long?>(null) }
        var error by rememberSaveable { mutableStateOf<String?>(null) }
        val saving by vm.saving.collectAsStateWithLifecycle()
        val saveError by vm.saveError.collectAsStateWithLifecycle()
        LaunchedEffect(kind) { vm.saveError.value = null }
        val titleText = when (kind) {
            "employee-new" -> "Alta de empleado"
            "project-new" -> "Nuevo servicio / proyecto"
            "movement" -> "Gasto / Ingreso"
            else -> "Parte diario"
        }
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack, enabled = !saving) { Text("Volver") }
            Text(titleText, style = MaterialTheme.typography.headlineMedium)
            if (state.loading) CircularProgressIndicator()
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error); Button(onClick = vm::retry) { Text("Reintentar") } }
            if (kind == "employee-new") {
                EntryField("Nombre *", title, { title = it }, !saving)
                EntryField("Apellidos *", surname, { surname = it }, !saving)
                EntryField("Oficio *", trade, { trade = it }, !saving)
                ChoiceField("Tipo de contrato", type, listOf("FIJO", "PARCIAL"), !saving) { type = it }
                EntryField("Teléfono (opcional)", phone, { phone = it }, !saving)
                EntryField("Correo (opcional)", email, { email = it }, !saving)
            } else if (kind == "project-new") {
                ChoiceField("Tipo", type, listOf("SERVICIO", "PROYECTO"), !saving) { type = it }
                EntryField("Nombre *", title, { title = it }, !saving)
                EntryField("Cliente *", client, { client = it }, !saving)
                EntryField("Ubicación *", location, { location = it }, !saving)
                EntryField("Inicio (AAAA-MM-DD) *", date, { date = it }, !saving)
                EntryField("Fin previsto (AAAA-MM-DD, opcional)", endDate, { endDate = it }, !saving)
            } else {
                val available = state.projects.filter { it.estado == "ACTIVO" }
                SelectionField("Servicio / proyecto", projectId, available.map { it.id to it.titulo }, !saving) { projectId = it }
                if (available.isEmpty()) Text("Primero crea un servicio o proyecto desde Inicio.", color = MaterialTheme.colorScheme.secondary)
                EntryField("Fecha (AAAA-MM-DD) *", date, { date = it }, !saving)
                if (kind == "movement") {
                    EntryField("Concepto *", title, { title = it }, !saving)
                    EntryField("Importe en euros *", amount, { amount = it }, !saving)
                    ChoiceField("Tipo", if (income) "Ingreso" else "Gasto", listOf("Gasto", "Ingreso"), !saving) { income = it == "Ingreso" }
                } else {
                    SelectionField("Empleado", employeeId, state.employees.filter { it.activo }.map { it.id to "${it.nombre} ${it.apellidos}" }, !saving) { employeeId = it }
                    if (state.employees.isEmpty()) Text("Primero da de alta un empleado desde Inicio.", color = MaterialTheme.colorScheme.secondary)
                    EntryField("Horas trabajadas *", amount, { amount = it }, !saving)
                    EntryField("Coste por hora en euros (0 si no se conoce)", rate, { rate = it }, !saving)
                }
            }
            (error ?: saveError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(enabled = !saving && !state.loading && state.error == null,
                onClick = {
                    error = null
                    when (kind) {
                        "employee-new" -> {
                            if (title.isBlank() || surname.isBlank() || trade.isBlank()) error = "Completa nombre, apellidos y oficio."
                            else if (email.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) error = "Revisa el correo electrónico."
                            else vm.save({ addEmployee(EmpleadoEntity(nombre = title.trim(), apellidos = surname.trim(), dni = "",
                                telefono = phone.trim(), email = email.trim(), oficio = trade.trim(), tipoEmpleado = type,
                                salarioBase = 0.0, lugarResidencia = "", direccionPersonal = "")) }, onBack)
                        }
                        "project-new" -> {
                            if (title.isBlank() || client.isBlank() || location.isBlank()) error = "Completa nombre, cliente y ubicación."
                            else if (!validDate(date) || (endDate.isNotBlank() && (!validDate(endDate) || endDate < date))) error = "Revisa las fechas; el fin no puede ser anterior al inicio."
                            else vm.save({ addProject(UnidadTrabajoEntity(titulo = title.trim(), tipo = type,
                                clienteNombre = client.trim(), direccionUbicacion = location.trim(), fechaInicio = date, fechaFin = endDate.ifBlank { null })) }, onBack)
                        }
                        else -> {
                            val p = projectId
                            val e = employeeId
                            val numeric = positiveNumber(amount)
                            val cents = moneyCents(amount)
                            val hourly = rate.replace(',', '.').toDoubleOrNull()
                            if (p == null || state.projects.none { it.id == p && it.estado == "ACTIVO" }) error = "Selecciona un servicio o proyecto activo."
                            else if (!validDate(date)) error = "Introduce una fecha válida con formato AAAA-MM-DD."
                            else if (kind == "movement") {
                                if (title.isBlank() || cents == null) error = "Indica el concepto y un importe positivo con un máximo de dos decimales."
                                else vm.save({ addMovement(HomeMovement(projectId = p, concept = title.trim(), amountCents = cents, income = income, date = date)) }, onBack)
                            } else if (e == null || state.employees.none { it.id == e && it.activo }) error = "Selecciona un empleado activo."
                            else if (numeric == null || numeric > 24 || hourly == null || !hourly.isFinite() || hourly < 0 || hourly > 1_000_000) error = "Las horas deben estar entre 0 y 24 y el coste por hora no puede ser negativo."
                            else if (date > LocalDate.now().toString()) error = "No se pueden registrar horas trabajadas en una fecha futura."
                            else if (state.hours.filter { it.empleadoId == e && it.fecha == date }.sumOf { it.horasTrabajadas } + numeric > 24) error = "Este empleado superaría las 24 horas registradas en el día."
                            else vm.save({ addHours(RegistroHorasEntity(unidadTrabajoId = p, empleadoId = e, fecha = date, horasTrabajadas = numeric, costeHoraAplicado = hourly)) }, onBack)
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(if (saving) "Guardando…" else "Guardar") }
            if (kind == "movement") {
                Text("Últimos movimientos", style = MaterialTheme.typography.titleMedium)
                state.movements.take(20).forEach { movement ->
                    Text("${movement.date} · ${movement.concept} · ${if (movement.income) "+" else "−"}${"%.2f".format(movement.amountCents / 100.0)} €")
                }
            } else if (kind == "daily") {
                Text("Últimos partes", style = MaterialTheme.typography.titleMedium)
                state.hours.take(20).forEach { hours ->
                    val employee = state.employees.firstOrNull { it.id == hours.empleadoId }
                    val project = state.projects.firstOrNull { it.id == hours.unidadTrabajoId }
                    Text("${hours.fecha} · ${employee?.nombre.orEmpty()} · ${project?.titulo.orEmpty()} · ${hours.horasTrabajadas} h")
                }
            }
        }
    }
}

@Composable
internal fun EntryField(label: String, value: String, onChange: (String) -> Unit, enabled: Boolean) {
    OutlinedTextField(value, onChange, label = { Text(label) }, enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth())
}

@Composable
internal fun ChoiceField(label: String, value: String, options: List<String>, enabled: Boolean, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("$label: $value") }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onChange(option); expanded = false }) }
        }
    }
}

@Composable
internal fun SelectionField(label: String, selected: Long?, options: List<Pair<Long, String>>, enabled: Boolean, onChange: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled && options.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${options.firstOrNull { it.first == selected }?.let { "${it.second} (#${it.first})" } ?: "Seleccionar"}")
        }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { (id, name) -> DropdownMenuItem(text = { Text("$name (#$id)") }, onClick = { onChange(id); expanded = false }) }
        }
    }
}
