package com.example.gestorempresa.ui.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.example.gestorempresa.domain.auth.CampoAuth
import com.example.gestorempresa.ui.theme.GestorEmpresaTheme

@Composable
fun LoginScreen(state: AuthUiState, onEmailChange: (String) -> Unit, onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit, onRegister: () -> Unit, cuentaCreada: Boolean = false,
    onRecovery: () -> Unit = {}
) {
    AuthLayout("Bienvenido de nuevo", "Accede con tu cuenta de administrador.") {
        if (cuentaCreada) AuthMessage("Cuenta creada. Inicia sesión para entrar en tu empresa.", success = true)
        AuthTextField(state.email, onEmailChange, "Correo electrónico", state.errores[CampoAuth.EMAIL], !state.ocupado, KeyboardType.Email)
        AuthPasswordField(state.password, onPasswordChange, "Contraseña", state.errores[CampoAuth.PASSWORD], !state.ocupado, done = true, onDone = onLogin)
        state.mensaje?.let { AuthMessage(it) }
        AuthSubmit("Iniciar sesión", state.ocupado, onLogin)
        TextButton(onClick = onRecovery, enabled = !state.ocupado, modifier = Modifier.fillMaxWidth()) { Text("He olvidado mi contraseña") }
        TextButton(onClick = onRegister, enabled = !state.ocupado, modifier = Modifier.fillMaxWidth()) { Text("¿Primera vez? Crea tu cuenta") }
        Text("Los datos de tu empresa se guardan en este dispositivo.")
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Preview(showBackground = true, widthDp = 1000, heightDp = 800)
@Composable
private fun LoginPreview() { GestorEmpresaTheme { LoginScreen(AuthUiState(), {}, {}, {}, {}) } }
