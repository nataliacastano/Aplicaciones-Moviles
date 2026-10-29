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
fun ForgotPasswordScreen(state: AuthUiState, onEmailChange: (String) -> Unit,
    onSend: () -> Unit, onBack: () -> Unit
) {
    AuthLayout("Recupera tu contraseña", "Introduce el correo de tu cuenta para solicitar un enlace de recuperación. Por seguridad, la app no confirma en pantalla si ese correo está registrado.") {
        AuthTextField(state.emailRecuperacion, onEmailChange, "Correo electrónico",
            state.errores[CampoAuth.EMAIL], !state.ocupado, KeyboardType.Email)
        state.mensaje?.let { AuthMessage(it) }
        if (state.recuperacionSolicitada) {
            AuthMessage("Si tu cuenta tiene activada la recuperación por correo, recibirás un enlace para cambiar la contraseña. Revisa también la carpeta de spam.", success = true)
            Text("Después de cambiarla desde el enlace, vuelve a iniciar sesión con la nueva contraseña.")
        } else {
            AuthSubmit("Enviar enlace de recuperación", state.ocupado, onSend)
        }
        TextButton(onClick = onBack, enabled = !state.ocupado, modifier = Modifier.fillMaxWidth()) { Text("Volver a iniciar sesión") }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Preview(showBackground = true, widthDp = 1000, heightDp = 800)
@Composable
private fun RecoveryPreview() { GestorEmpresaTheme { ForgotPasswordScreen(AuthUiState(), {}, {}, {}) } }
