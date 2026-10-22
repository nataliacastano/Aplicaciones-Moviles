package com.example.gestorempresa.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
internal fun AuthLayout(title: String, description: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            val wide = maxWidth >= 840.dp
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                if (wide) Surface(Modifier.weight(1f).fillMaxHeight(), color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.padding(48.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                        Text("GESTOR EMPRESA", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(24.dp))
                        Text("Tu equipo.\nTu empresa.\nTodo conectado.", style = MaterialTheme.typography.displaySmall)
                        Spacer(Modifier.height(24.dp))
                        Text("Personas, servicios y recursos para empresas de cualquier sector.", style = MaterialTheme.typography.titleMedium)
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Column(
                        Modifier.widthIn(max = 520.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (!wide) Text("GESTOR EMPRESA", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        Text(title, style = MaterialTheme.typography.headlineMedium)
                        Text(description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        content()
                    }
                }
            }
        }
    }
}

@Composable
internal fun AuthTextField(value: String, onValueChange: (String) -> Unit, label: String,
    error: String? = null, enabled: Boolean = true, keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = enabled,
        isError = error != null, supportingText = { if (error != null) Text(error) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next)
    )
}

@Composable
internal fun AuthPasswordField(value: String, onValueChange: (String) -> Unit, label: String,
    error: String? = null, enabled: Boolean = true, done: Boolean = false, onDone: () -> Unit = {}
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = enabled,
        isError = error != null, supportingText = { if (error != null) Text(error) },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { TextButton(onClick = { visible = !visible }, enabled = enabled) { Text(if (visible) "Ocultar" else "Mostrar") } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (done) ImeAction.Done else ImeAction.Next),
        keyboardActions = KeyboardActions(onDone = { if (enabled) onDone() })
    )
}

@Composable
internal fun AuthMessage(message: String, success: Boolean = false) {
    Surface(
        color = if (success) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
    ) { Text(message, Modifier.padding(16.dp)) }
}

@Composable
internal fun AuthSubmit(text: String, busy: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp)); Text("Procesando…")
        } else Text(text)
    }
}
