package com.example.gestorempresa.ui.navigation

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.example.gestorempresa.ui.profile.components.ProfileAvatar
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gestorempresa.ui.advanced.dates.DueDateNotifications
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.gestorempresa.domain.auth.SesionUsuario
import com.example.gestorempresa.ui.home.HomeListsContent
import com.example.gestorempresa.ui.profile.ProfileScreen
import com.example.gestorempresa.ui.profile.ProfileViewModel
import com.example.gestorempresa.ui.settings.SettingsScreen
import com.example.gestorempresa.ui.settings.SettingsViewModel

/**
 * Las tres secciones de la barra de navegación inferior.
 * Cada una tiene un icono relleno (pestaña seleccionada) y otro de contorno (no seleccionada).
 */
enum class SeccionPrincipal(val titulo: String, val iconoSeleccionado: ImageVector, val icono: ImageVector) {
    INICIO("Inicio", Icons.Filled.Home, Icons.Outlined.Home),
    PERFIL("Perfil", Icons.Filled.Person, Icons.Outlined.Person),
    AJUSTES("Ajustes", Icons.Filled.Settings, Icons.Outlined.Settings)
}

/**
 * Estructura principal de la app una vez iniciada la sesión:
 * el contenido de la sección elegida arriba y la barra de navegación abajo.
 */
@Composable
fun MainScaffold(
    sesion: SesionUsuario,
    onCerrarSesion: () -> Unit,
    profileViewModel: ProfileViewModel,
    settingsViewModel: SettingsViewModel
) {
    var seccion by rememberSaveable { mutableStateOf(SeccionPrincipal.INICIO) }
    var homeResetSignal by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val ajustes by settingsViewModel.ui.collectAsStateWithLifecycle()
    val profileState by profileViewModel.ui.collectAsStateWithLifecycle()
    val perfil = profileState.perfil?.takeIf { it.usuarioId == sesion.usuario.id }
    var settingsSection by rememberSaveable(sesion.usuario.id) { mutableStateOf<String?>(null) }
    var confirmarCierre by rememberSaveable(sesion.usuario.id) { mutableStateOf(false) }
    var abrirEdicion by rememberSaveable(sesion.usuario.id) { mutableStateOf(false) }
    LaunchedEffect(seccion, perfil?.usuarioId, abrirEdicion) {
        if (seccion == SeccionPrincipal.PERFIL && perfil != null && abrirEdicion) {
            profileViewModel.empezarEdicion()
            abrirEdicion = false
        }
    }
    val preferencias = ajustes.ajustes
    val empresaId = sesion.empresa.id
    DisposableEffect(sesion.usuario.id) { onDispose { DueDateNotifications.cancel(context) } }

    // Avisos de vencimientos (funciones avanzadas): se reprograman cada vez que cambian los Ajustes.
    LaunchedEffect(empresaId, ajustes.cargando, ajustes.usuarioId, preferencias.avisosVencimientos, preferencias.avisosTareas, preferencias.diasAntelacion) {
        if (ajustes.cargando || ajustes.usuarioId != sesion.usuario.id) return@LaunchedEffect
        DueDateNotifications.schedule(context, empresaId, preferencias.diasAntelacion, preferencias.avisosVencimientos, preferencias.avisosTareas)
    }
    // Android 13 o superior pide permiso para mostrar notificaciones; se pregunta una vez por sesión.
    var permisoPedido by rememberSaveable { mutableStateOf(false) }
    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(ajustes.cargando, ajustes.usuarioId, preferencias.avisosVencimientos, preferencias.avisosTareas) {
        if (ajustes.cargando || ajustes.usuarioId != sesion.usuario.id) return@LaunchedEffect
        if (!permisoPedido && Build.VERSION.SDK_INT >= 33 && (preferencias.avisosVencimientos || preferencias.avisosTareas) &&
            !DueDateNotifications.canNotify(context)) {
            permisoPedido = true
            pedirPermiso.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val cerrarSesion = { DueDateNotifications.cancel(context); onCerrarSesion() }

    // Guarda el estado de cada sección (por ejemplo, el listado abierto en Inicio)
    // para que no se pierda al cambiar de pestaña y volver.
    val estadoSecciones = rememberSaveableStateHolder()

    // Desde Perfil o Ajustes, el botón Atrás vuelve a Inicio en lugar de cerrar la app.
    BackHandler(enabled = seccion != SeccionPrincipal.INICIO) { seccion = SeccionPrincipal.INICIO }

    Scaffold(
        bottomBar = {
            NavigationBar {
                SeccionPrincipal.entries.forEach { opcion ->
                    val seleccionada = seccion == opcion
                    NavigationBarItem(
                        modifier = Modifier.testTag("main-${opcion.name}"),
                        selected = seleccionada,
                        onClick = {
                            if (opcion == SeccionPrincipal.INICIO) {
                                homeResetSignal++
                                seccion = SeccionPrincipal.INICIO
                            } else { settingsSection = null; seccion = opcion }
                        },
                        // contentDescription = null porque el texto de debajo ya describe el icono.
                        icon = {
                            if (opcion == SeccionPrincipal.PERFIL) ProfileAvatar(perfil?.fotoRuta,
                                perfil?.iniciales ?: "${sesion.usuario.nombre.take(1)}${sesion.usuario.apellidos.take(1)}", tamano = 28.dp)
                            else Icon(if (seleccionada) opcion.iconoSeleccionado else opcion.icono, contentDescription = null)
                        },
                        label = { Text(opcion.titulo) }
                    )
                }
            }
        }
    ) { padding ->
        // imePadding: deja hueco para el teclado al escribir en los formularios.
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            estadoSecciones.SaveableStateProvider(seccion.name) {
                when (seccion) {
                    SeccionPrincipal.INICIO -> HomeListsContent(session = sesion, warnDays = preferencias.diasAntelacion, notifyExpiries = preferencias.avisosVencimientos, notifyTasks = preferencias.avisosTareas, resetHomeSignal = homeResetSignal,
                        perfil = perfil,
                        menuUsuario = listOf(
                            OpcionMenu("user:profile", "Perfil", Icons.Filled.Person, separadorAntes = true),
                            OpcionMenu("user:edit", "Editar datos", Icons.Filled.Edit),
                            OpcionMenu("user:settings", "Ajustes", Icons.Filled.Settings),
                            OpcionMenu("user:security", "Seguridad", Icons.Filled.Lock),
                            OpcionMenu("user:notifications", "Notificaciones", Icons.Filled.Notifications),
                            OpcionMenu("user:logout", "Cerrar sesión", Icons.Filled.ExitToApp)),
                        onUserNavigation = { route -> when (route) {
                            "user:profile" -> seccion = SeccionPrincipal.PERFIL
                            "user:edit" -> { abrirEdicion = true; seccion = SeccionPrincipal.PERFIL }
                            "user:settings", "user:security", "user:notifications" -> {
                                settingsSection = when (route) { "user:security" -> "security"; "user:notifications" -> "notifications"; else -> null }
                                seccion = SeccionPrincipal.AJUSTES
                            }
                            "user:logout" -> confirmarCierre = true
                        } },
                        onNotifyNow = {
                            DueDateNotifications.runNow(context, empresaId, preferencias.diasAntelacion, preferencias.avisosVencimientos, preferencias.avisosTareas)
                            val mensaje = if (DueDateNotifications.canNotify(context)) "Revisando vencimientos…" else "Activa las notificaciones de la app para recibir avisos"
                            Toast.makeText(context, mensaje, Toast.LENGTH_SHORT).show()
                        })
                    SeccionPrincipal.PERFIL -> ProfileScreen(profileViewModel)
                    SeccionPrincipal.AJUSTES -> SettingsScreen(
                        viewModel = settingsViewModel,
                        onAbrirPerfil = { abrirEdicion = true; seccion = SeccionPrincipal.PERFIL },
                        onCerrarSesion = cerrarSesion,
                        seccionInicial = settingsSection
                    )
                }
            }
        }
    }
    if (confirmarCierre) AlertDialog(onDismissRequest = { confirmarCierre = false }, title = { Text("¿Cerrar sesión?") },
        text = { Text("Se cerrará la sesión de este dispositivo.") },
        confirmButton = { Button(onClick = { confirmarCierre = false; cerrarSesion() }) { Text("Cerrar sesión") } },
        dismissButton = { TextButton(onClick = { confirmarCierre = false }) { Text("Cancelar") } })
}
