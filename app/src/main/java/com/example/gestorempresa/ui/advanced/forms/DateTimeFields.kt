package com.example.gestorempresa.ui.advanced.forms

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

// ---------------------------------------------------------------------------------------------
// Lógica (sin Android): se puede probar con tests unitarios y reutilizar en cualquier formulario.
// ---------------------------------------------------------------------------------------------

private val strictDate = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT)

/** Convierte lo que escribe el usuario en «AAAA-MM-DD», añadiendo los guiones automáticamente. */
fun formatDateInput(input: String): String {
    val digits = input.filter(Char::isDigit).take(8)
    return buildString {
        digits.forEachIndexed { i, c -> if (i == 4 || i == 6) append('-'); append(c) }
    }
}

/** Fecha real en formato AAAA-MM-DD (rechaza 2026-02-30, 2026-13-01…), o null. */
fun parseDate(value: String): LocalDate? = try { LocalDate.parse(value, strictDate) } catch (_: Exception) { null }

/** Mensaje de error de un campo de fecha, o null si es válido. Un campo opcional puede quedar vacío. */
fun dateError(value: String, required: Boolean, min: LocalDate? = null, max: LocalDate? = null): String? {
    if (value.isBlank()) return if (required) "La fecha es obligatoria." else null
    if (value.length < 10) return "Fecha incompleta: usa el formato AAAA-MM-DD."
    val date = parseDate(value) ?: return "La fecha no existe. Revisa el día y el mes."
    if (min != null && date < min) return "La fecha no puede ser anterior a $min."
    if (max != null && date > max) return "La fecha no puede ser posterior a $max."
    return null
}

/** Convierte lo que escribe el usuario en «HH:mm» (24 horas), añadiendo los dos puntos. */
fun formatTimeInput(input: String): String {
    val digits = input.filter(Char::isDigit).take(4)
    return if (digits.length <= 2) digits else digits.take(2) + ":" + digits.drop(2)
}

/** Hora válida en formato de 24 horas HH:mm (00:00 a 23:59), o null. */
fun parseTime(value: String): LocalTime? {
    val match = Regex("^([01]\\d|2[0-3]):([0-5]\\d)$").matchEntire(value) ?: return null
    return LocalTime.of(match.groupValues[1].toInt(), match.groupValues[2].toInt())
}

fun timeError(value: String, required: Boolean): String? = when {
    value.isBlank() -> if (required) "La hora es obligatoria." else null
    value.length < 5 -> "Hora incompleta: usa el formato HH:mm (por ejemplo, 08:30)."
    parseTime(value) == null -> "Hora no válida: de 00:00 a 23:59."
    else -> null
}

/** Comprueba que la hora final es posterior a la inicial. Devuelve el error o null. */
fun timeRangeError(start: String, end: String): String? {
    val from = parseTime(start) ?: return null
    val to = parseTime(end) ?: return null
    return if (!to.isAfter(from)) "La hora de fin debe ser posterior a la de inicio." else null
}

// ---------------------------------------------------------------------------------------------
// Componentes visuales
// ---------------------------------------------------------------------------------------------

/** Muestra los guiones o los dos puntos sin que formen parte del texto que escribe el usuario. */
private class SeparatorTransformation(private val positions: Set<Int>, private val separator: Char) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val out = buildString { raw.forEachIndexed { i, c -> if (i in positions) append(separator); append(c) } }
        val mapping = object : OffsetMapping {
            // Cada separador se dibuja delante del carácter que ocupa su posición.
            override fun originalToTransformed(offset: Int) = offset + positions.count { it < offset }
            override fun transformedToOriginal(offset: Int): Int {
                var original = offset
                positions.sorted().forEachIndexed { index, p -> if (offset > p + index) original-- }
                return original.coerceIn(0, raw.length)
            }
        }
        return TransformedText(AnnotatedString(out), mapping)
    }
}

/**
 * Campo de fecha reutilizable (AAAA-MM-DD): teclado numérico, guiones automáticos,
 * validación de fechas reales, borde rojo con el error y botón de calendario.
 *
 * @param value fecha con guiones («2026-11-30») o vacía.
 * @param onValueChange recibe siempre el texto ya formateado con guiones.
 * @param showErrors pásalo a true al pulsar Guardar para marcar también los campos vacíos obligatorios.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    required: Boolean = false, enabled: Boolean = true, showErrors: Boolean = false,
    min: LocalDate? = null, max: LocalDate? = null) {
    var picking by remember { mutableStateOf(false) }
    val error = dateError(value, required, min, max)
    // Mientras escribe solo se marca el error cuando la fecha ya está completa, para no molestar.
    val visibleError = error?.takeIf { showErrors || value.length >= 10 }
    OutlinedTextField(
        value = value.filter(Char::isDigit),
        onValueChange = { onValueChange(formatDateInput(it)) },
        label = { Text(label + if (required) " *" else " (opcional)") },
        placeholder = { Text("AAAA-MM-DD") },
        singleLine = true, enabled = enabled, isError = visibleError != null,
        supportingText = visibleError?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        visualTransformation = SeparatorTransformation(setOf(4, 6), '-'),
        trailingIcon = { IconButton(enabled = enabled, onClick = { picking = true }) { Icon(Icons.Outlined.DateRange, "Elegir fecha en el calendario") } },
        modifier = modifier.fillMaxWidth()
    )
    if (picking) {
        val initial = parseDate(value)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(onDismissRequest = { picking = false },
            confirmButton = { TextButton(onClick = {
                state.selectedDateMillis?.let { onValueChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                picking = false
            }) { Text("Aceptar") } },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancelar") } }) {
            DatePicker(state)
        }
    }
}

/**
 * Campo de hora reutilizable en formato de 24 horas (HH:mm): teclado numérico, dos puntos automáticos,
 * validación (00:00–23:59) y selector de reloj.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    required: Boolean = false, enabled: Boolean = true, showErrors: Boolean = false, extraError: String? = null) {
    var picking by remember { mutableStateOf(false) }
    val error = timeError(value, required) ?: extraError
    val visibleError = error?.takeIf { showErrors || value.length >= 5 || extraError != null }
    OutlinedTextField(
        value = value.filter(Char::isDigit),
        onValueChange = { onValueChange(formatTimeInput(it)) },
        label = { Text(label + if (required) " *" else "") },
        placeholder = { Text("HH:mm") },
        singleLine = true, enabled = enabled, isError = visibleError != null,
        supportingText = visibleError?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        visualTransformation = SeparatorTransformation(setOf(2), ':'),
        trailingIcon = { TextButton(enabled = enabled, onClick = { picking = true }) { Text("🕒") } },
        modifier = modifier.fillMaxWidth()
    )
    if (picking) {
        val current = parseTime(value) ?: LocalTime.of(8, 0)
        val state = rememberTimePickerState(current.hour, current.minute, is24Hour = true)
        AlertDialog(onDismissRequest = { picking = false }, title = { Text(label) },
            text = { TimePicker(state) },
            confirmButton = { TextButton(onClick = { onValueChange("%02d:%02d".format(state.hour, state.minute)); picking = false }) { Text("Aceptar") } },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancelar") } })
    }
}
