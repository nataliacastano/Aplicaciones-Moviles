package com.example.gestorempresa.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.gestorempresa.domain.auth.EstadoSesion
import com.example.gestorempresa.domain.auth.SesionUsuario
import com.example.gestorempresa.ui.auth.*
import kotlinx.serialization.Serializable

@Serializable data class Login(val cuentaCreada: Boolean = false) : NavKey
@Serializable data object Registro : NavKey
@Serializable data object RecuperarPassword : NavKey
@Serializable data class Inicio(val usuarioId: Long, val empresaId: Long) : NavKey

/** El contenido privado solo se compone con una sesión válida. */
@Composable
fun AuthRoot(
    viewModel: AuthViewModel,
    contenidoAutenticado: @Composable (sesion: SesionUsuario, onLogout: () -> Unit) -> Unit = { sesion, logout ->
        InicioIntegracionScreen(sesion, logout)
    }
) {
    val session by viewModel.sesion.collectAsStateWithLifecycle()
    val state by viewModel.ui.collectAsStateWithLifecycle()
    when (val actual = session) {
        EstadoSesion.Cargando -> Surface(Modifier.fillMaxSize()) {
            Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        EstadoSesion.Fallo -> Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text("No se ha podido comprobar la sesión.")
                Button(onClick = viewModel::reintentarSesion) { Text("Reintentar") }
            }
        }
        EstadoSesion.SinSesion -> key("sin-sesion") {
            val backStack = rememberNavBackStack(Login())
            LaunchedEffect(viewModel) {
                viewModel.eventos.collect { event ->
                    if (event is AuthEvent.CuentaCreada) {
                        backStack.clear(); backStack.add(Login(cuentaCreada = true))
                    }
                }
            }
            NavDisplay(
                backStack = backStack,
                onBack = { if (!state.ocupado && backStack.size > 1) { viewModel.limpiarSecretos(); backStack.removeAt(backStack.lastIndex) } },
                entryProvider = entryProvider {
                    entry<Login> { route ->
                        LoginScreen(state, { viewModel.editarLogin(email = it) }, { viewModel.editarLogin(password = it) },
                            viewModel::iniciarSesion, { viewModel.limpiarSecretos(); backStack.add(Registro) }, route.cuentaCreada,
                            onRecovery = { viewModel.abrirRecuperacion(); backStack.add(RecuperarPassword) })
                    }
                    entry<Registro> {
                        RegisterScreen(state, viewModel::editarRegistro, viewModel::registrar, {
                            viewModel.limpiarSecretos(); backStack.clear(); backStack.add(Login())
                        })
                    }
                    entry<RecuperarPassword> {
                        ForgotPasswordScreen(state, viewModel::editarEmailRecuperacion, viewModel::recuperarPassword, {
                            viewModel.volverDeRecuperacion(); backStack.clear(); backStack.add(Login())
                        })
                    }
                }
            )
        }
        is EstadoSesion.Autenticada -> key(actual.sesion.usuario.id, actual.sesion.expiraEn) {
            // La pila privada desaparece al salir: Atrás no permite recuperarla.
            val s = actual.sesion
            val backStack = rememberNavBackStack(Inicio(s.usuario.id, s.empresa.id))
            Column(Modifier.fillMaxSize()) {
                state.mensaje?.let { message ->
                    Surface(color = MaterialTheme.colorScheme.errorContainer) { Text(message, Modifier.safeDrawingPadding().padding(16.dp)) }
                }
                Box(Modifier.weight(1f)) {
                    NavDisplay(backStack = backStack, onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }, entryProvider = entryProvider {
                        entry<Inicio> { route ->
                            // Los parámetros identifican el destino; la sesión es la autoridad.
                            if (route.usuarioId == s.usuario.id && route.empresaId == s.empresa.id) {
                                contenidoAutenticado(s, viewModel::cerrarSesion)
                            }
                        }
                    })
                }
            }
        }
    }
}

/** Sustituir mediante contenidoAutenticado por HomeScreen del miembro 2. */
@Composable
private fun InicioIntegracionScreen(session: SesionUsuario, onLogout: () -> Unit) {
    Surface {
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Sesión iniciada", style = MaterialTheme.typography.headlineMedium)
            Text("Hola, ${session.usuario.nombre}")
            Text(session.empresa.nombre, style = MaterialTheme.typography.titleLarge)
            Text("Sector: ${session.empresa.sector}")
            Text("Esta pantalla comprueba la conexión con Inicio. El miembro 2 añadirá aquí el resumen y los listados de la empresa.")
            Button(onClick = onLogout) { Text("Cerrar sesión") }
        }
    }
}
