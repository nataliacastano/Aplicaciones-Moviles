package com.example.gestorempresa.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.input.KeyboardType
import com.example.gestorempresa.domain.auth.AuthValidator
import com.example.gestorempresa.domain.auth.CampoAuth
import com.example.gestorempresa.domain.auth.RegistroUsuario
import com.example.gestorempresa.ui.theme.GestorEmpresaTheme

@Composable
fun RegisterScreen(state: AuthUiState, onChange: (RegistroUsuario) -> Unit, onRegister: () -> Unit, onLogin: () -> Unit) {
    val r = state.registro
    val enabled = !state.ocupado
    AuthLayout("Crea tu cuenta", "Registra tu empresa y empieza a gestionarla como administrador.") {
        AuthTextField(r.nombre, { onChange(r.copy(nombre = it)) }, "Nombre", state.errores[CampoAuth.NOMBRE], enabled)
        AuthTextField(r.apellidos, { onChange(r.copy(apellidos = it)) }, "Apellidos", state.errores[CampoAuth.APELLIDOS], enabled)
        AuthTextField(r.email, { onChange(r.copy(email = it)) }, "Correo electrónico", state.errores[CampoAuth.EMAIL], enabled, KeyboardType.Email)
        AuthTextField(r.empresa, { onChange(r.copy(empresa = it)) }, "Nombre de la empresa", state.errores[CampoAuth.EMPRESA], enabled)
        var expanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(if (r.sector.isEmpty()) "Selecciona el sector de tu empresa" else "Sector: ${r.sector}")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                AuthValidator.sectores.forEach { sector ->
                    DropdownMenuItem(text = { Text(sector) }, onClick = { onChange(r.copy(sector = sector)); expanded = false })
                }
            }
        }
        state.errores[CampoAuth.SECTOR]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        AuthPasswordField(r.password, { onChange(r.copy(password = it)) }, "Contraseña", state.errores[CampoAuth.PASSWORD], enabled)
        Text("Entre 12 y 128 caracteres. Puedes usar una frase fácil de recordar.", style = MaterialTheme.typography.bodySmall)
        AuthPasswordField(r.confirmarPassword, { onChange(r.copy(confirmarPassword = it)) }, "Repite la contraseña", state.errores[CampoAuth.CONFIRMACION], enabled, done = true, onDone = onRegister)
        state.mensaje?.let { AuthMessage(it) }
        AuthSubmit("Crear cuenta y empresa", state.ocupado, onRegister)
        TextButton(onClick = onLogin, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Ya tengo cuenta. Iniciar sesión") }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 900)
@Preview(showBackground = true, widthDp = 1000, heightDp = 800)
@Composable
private fun RegisterPreview() { GestorEmpresaTheme { RegisterScreen(AuthUiState(), {}, {}, {}) } }
