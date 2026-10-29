package com.example.gestorempresa.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
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
fun HomeEntryForm(kind: String, vm: HomeWorkspaceViewModel, state: HomeWorkspaceState, onBack: () -> Unit,
    editingEmployee: EmpleadoEntity? = null) {
    key(kind, editingEmployee?.id) {
        var title by rememberSaveable { mutableStateOf(editingEmployee?.nombre.orEmpty()) }
        var surname by rememberSaveable { mutableStateOf(editingEmployee?.apellidos.orEmpty()) }
        var trade by rememberSaveable { mutableStateOf(editingEmployee?.oficio.orEmpty()) }
        var type by rememberSaveable { mutableStateOf(editingEmployee?.tipoEmpleado?.let { if (it == "FIJO") "COMPLETO" else it } ?: if (kind == "project-new") "SERVICIO" else "COMPLETO") }
        var dni by rememberSaveable { mutableStateOf(editingEmployee?.dni.orEmpty()) }
        var socialSecurity by rememberSaveable { mutableStateOf(editingEmployee?.numeroSeguridadSocial.orEmpty().filter { it.isDigit() }.take(12)) }
        var residence by rememberSaveable { mutableStateOf(editingEmployee?.lugarResidencia.orEmpty()) }
        var address by rememberSaveable { mutableStateOf(editingEmployee?.direccionPersonal.orEmpty()) }
        var salary by rememberSaveable { mutableStateOf(editingEmployee?.salarioBase?.takeIf { it > 0 }?.toString().orEmpty()) }
        var phone by rememberSaveable { mutableStateOf(editingEmployee?.telefono.orEmpty().filter { it.isDigit() }.take(15)) }
        var email by rememberSaveable { mutableStateOf(editingEmployee?.email.orEmpty()) }
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
            "employee-edit" -> "Editar empleado"
            "project-new" -> if (type == "SERVICIO") "Nuevo servicio" else "Nuevo proyecto"
            "movement" -> "Gasto / Ingreso"
            else -> "Parte diario"
        }
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack, enabled = !saving) { Text("Volver") }
            Text(titleText, style = MaterialTheme.typography.headlineMedium)
            if (state.loading) CircularProgressIndicator()
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error); Button(onClick = vm::retry) { Text("Reintentar") } }
            if (kind == "employee-new" || kind == "employee-edit") {
                EntryField("Nombre *", title, { title = it }, !saving)
                EntryField("Apellidos *", surname, { surname = it }, !saving)
                EntryField("DNI / NIE *", dni, { dni = it }, !saving)
                EntryField("Oficio *", trade, { trade = it }, !saving)
                ChoiceField("Jornada", if (type == "FIJO") "COMPLETO" else type, listOf("COMPLETO", "PARCIAL"), !saving) { type = it }
                Text(if (type == "PARCIAL") "Jornada parcial: horario reducido. Registra las horas realizadas desde Parte diario." else "Jornada completa. Registra las horas realizadas desde Parte diario.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                EntryField("Teléfono *", phone, { phone = it }, !saving)
                EntryField("Correo electrónico *", email, { email = it }, !saving)
                EntryField("Salario base mensual (€) *", salary, { salary = it }, !saving)
                EntryField("Nº de Seguridad Social", socialSecurity, { socialSecurity = it }, !saving)
                EntryField("Lugar de residencia *", residence, { residence = it }, !saving)
                EntryField("Dirección personal *", address, { address = it }, !saving)
            } else if (kind == "project-new") {
                ChoiceField("Tipo", type, listOf("SERVICIO", "PROYECTO"), !saving) { type = it }
                Text(if (type == "SERVICIO") "Registra un local o una actividad que gestionas de forma habitual. La fecha de apertura inicia su historial." else "Registra un trabajo concreto. Puedes indicar el cliente y la fecha de fin si los conoces.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                EntryField(if (type == "SERVICIO") "Nombre del servicio o local *" else "Nombre del proyecto *", title, { title = it }, !saving)
                if (type == "PROYECTO") EntryField("Cliente (opcional)", client, { client = it }, !saving)
                EntryField("Ubicación o dirección *", location, { location = it }, !saving)
                EntryField(if (type == "SERVICIO") "Fecha de apertura (AAAA-MM-DD) *" else "Fecha de inicio (AAAA-MM-DD) *", date, { date = it }, !saving)
                if (type == "PROYECTO") EntryField("Fecha de fin prevista (opcional)", endDate, { endDate = it }, !saving)
            } else {
                val available = state.projects.filter { it.estado == "ACTIVO" }
                SelectionField("Servicio / proyecto", projectId, available.map { it.id to it.titulo }, !saving) { projectId = it }
                if (available.isEmpty()) Text("Primero crea un servicio o proyecto desde Inicio.", color = MaterialTheme.colorScheme.secondary)
                EntryField("Fecha (AAAA-MM-DD) *", date, { date = it }, !saving)
                if (kind == "movement") {
                    Text("Ingreso: dinero que recibes por un servicio. Gasto: dinero que pagas, por ejemplo a proveedores. El balance resta gastos a ingresos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        "employee-new", "employee-edit" -> {
                            if (!validPersonName(title) || !validPersonName(surname)) error = "El nombre y los apellidos solo pueden contener letras, espacios, guiones o apóstrofos."
                            else if (dni.isBlank() || !Regex("(?i)^[0-9XYZ]\\d{7}[A-Z]$").matches(dni.trim().replace(" ", ""))) error = "Introduce un DNI o NIE válido (8 números y una letra)."
                            else if (trade.isBlank()) error = "Indica el oficio del empleado."
                            else if (phone.length !in 9..15) error = "El teléfono debe tener entre 9 y 15 dígitos."
                            else if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) error = "Introduce un correo electrónico válido."
                            else if (salary.isBlank() || (salary.replace(',', '.').toDoubleOrNull()?.let { !it.isFinite() || it < 0 } != false)) error = "Introduce un salario base válido."
                            else if (residence.isBlank() || address.isBlank()) error = "Completa el lugar de residencia y la dirección personal."
                            else if (socialSecurity.isNotBlank() && socialSecurity.length != 12) error = "El número de Seguridad Social debe tener 12 dígitos."
                            else {
                                val salaryValue = salary.replace(',', '.').toDouble()
                                val value = EmpleadoEntity(id = editingEmployee?.id ?: 0, nombre = title.trim(), apellidos = surname.trim(), dni = dni.trim(),
                                    telefono = phone.trim(), email = email.trim(), oficio = trade.trim(), tipoEmpleado = type, salarioBase = salaryValue,
                                    lugarResidencia = residence.trim(), direccionPersonal = address.trim(), fotoUrl = editingEmployee?.fotoUrl,
                                    activo = editingEmployee?.activo ?: true, numeroSeguridadSocial = socialSecurity)
                                vm.save({ if (editingEmployee == null) addEmployee(value) else updateEmployee(value) }, onBack)
                            }
                        }
                        "project-new" -> {
                            if (title.isBlank() || location.isBlank()) error = "Completa el nombre y la ubicación."
                            else if (!validDate(date) || (endDate.isNotBlank() && (!validDate(endDate) || endDate < date))) error = "Revisa las fechas; el fin no puede ser anterior al inicio."
                            else vm.save({ addProject(UnidadTrabajoEntity(titulo = title.trim(), tipo = type,
                                clienteNombre = if (type == "PROYECTO") client.trim() else "", direccionUbicacion = location.trim(), fechaInicio = date,
                                fechaFin = if (type == "PROYECTO") endDate.ifBlank { null } else null)) }, onBack)
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
    val isPhone = label.contains("teléfono", ignoreCase = true)
    val isSocialSecurity = label.contains("Seguridad Social", ignoreCase = true)
    val isPersonName = label == "Nombre *" || label == "Apellidos *"
    OutlinedTextField(value, { input ->
        val clean = when {
            isPhone || isSocialSecurity -> input.filter { it.isDigit() }.take(if (isPhone) 15 else 12)
            isPersonName -> input.filter { it.isLetter() || it.isWhitespace() || it == '-' || it == '\'' }
            else -> input
        }
        onChange(clean)
    }, label = { Text(label) }, enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = when {
            isPhone || isSocialSecurity -> KeyboardType.Number
            label.contains("correo", ignoreCase = true) -> KeyboardType.Email
            label.contains("salario", ignoreCase = true) || label.contains("importe", ignoreCase = true) || label.contains("euros", ignoreCase = true) -> KeyboardType.Decimal
            else -> KeyboardType.Text
        }))
}

private fun validPersonName(value: String): Boolean = value.trim().matches(Regex("[\\p{L}][\\p{L} .'-]{0,99}"))

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
            Text("$label: ${options.firstOrNull { it.first == selected }?.second ?: "Seleccionar"}")
        }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onChange(id); expanded = false }) }
        }
    }
}
