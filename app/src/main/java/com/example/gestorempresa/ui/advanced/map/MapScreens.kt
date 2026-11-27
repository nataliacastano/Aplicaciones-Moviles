package com.example.gestorempresa.ui.advanced.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.gestorempresa.data.local.entities.UnidadTrabajoEntity
import com.example.gestorempresa.ui.home.HomeWorkspaceState
import com.example.gestorempresa.ui.home.HomeWorkspaceViewModel
import kotlinx.coroutines.launch

fun UnidadTrabajoEntity.toPin(): MapPin? = if (latitud != null && longitud != null)
    MapPin(id, titulo, listOf(clienteNombre, direccionUbicacion, estado).filter { it.isNotBlank() }.joinToString(" · "), latitud, longitud) else null

/**
 * Mapa con la ubicación de todos los servicios y proyectos (y, por tanto, de sus clientes). Válido para cualquier sector.
 * Se puede filtrar por estado; al tocar dos veces un punto se abre su ficha.
 */
@Composable
fun ProjectsMapScreen(state: HomeWorkspaceState, onOpenProject: (Long) -> Unit, onPickLocation: (Long) -> Unit, modifier: Modifier = Modifier) {
    var status by rememberSaveable { mutableStateOf("TODOS") }
    val visible = state.projects.filter { status == "TODOS" || it.estado == status }
    val pins = visible.mapNotNull { it.toPin() }
    val missing = visible.filter { it.latitud == null || it.longitud == null }
    Column(modifier.fillMaxSize().testTag("map-screen")) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Mapa de servicios y proyectos", style = MaterialTheme.typography.headlineSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("TODOS" to "Todos", "ACTIVO" to "Activos", "PAUSADO" to "Pausados", "FINALIZADO" to "Finalizados")) { (code, label) ->
                    FilterChip(selected = status == code, onClick = { status = code }, label = { Text(label) })
                }
            }
            Text("${pins.size} en el mapa. Toca un punto para ver su nombre y otra vez para abrir la ficha.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OsmMap(pins, Modifier.weight(1f).fillMaxWidth(), onPinClick = { onOpenProject(it.id) }, focusKey = status)
        if (missing.isNotEmpty()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Sin ubicación en el mapa (${missing.size})", style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(missing, key = { it.id }) { project ->
                        AssistChip(onClick = { onPickLocation(project.id) }, label = { Text("📍 ${project.titulo}") })
                    }
                }
            }
        }
    }
}

/**
 * Pantalla para fijar la ubicación de un servicio o proyecto. Guarda a la vez la dirección y las
 * coordenadas para que no se contradigan (ver [LocationPickerContent]).
 */
@Composable
fun LocationPickerScreen(project: UnidadTrabajoEntity, workspace: HomeWorkspaceViewModel, onBack: () -> Unit) {
    val saving by workspace.saving.collectAsState()
    val initialPoint = if (project.latitud != null && project.longitud != null) project.latitud to project.longitud else null
    LocationPickerContent(title = "Ubicación de ${project.titulo}", initialAddress = project.direccionUbicacion,
        initialPoint = initialPoint, saving = saving, onCancel = onBack,
        onConfirm = { location ->
            workspace.save({ setProjectAddressAndLocation(project.id, location.address, location.latitude, location.longitude) }, onBack)
        },
        onRemove = if (initialPoint != null) { { workspace.save({ setProjectLocation(project.id, null, null) }, onBack) } } else null)
}

/** Vista previa del mapa dentro de la ficha de un servicio o proyecto. */
@Composable
fun ProjectMapCard(project: UnidadTrabajoEntity, onOpenMap: () -> Unit, onPickLocation: () -> Unit) {
    val context = LocalContext.current
    val pin = project.toPin()
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("📍 Ubicación", style = MaterialTheme.typography.titleMedium)
            Text(project.direccionUbicacion.ifBlank { "Sin dirección" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (pin != null) {
                Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp))) {
                    OsmMap(listOf(pin), Modifier.fillMaxSize(), interactive = false)
                    // Capa transparente: el mapa pequeño no roba el desplazamiento de la ficha y al tocarlo se abre el mapa completo.
                    Box(Modifier.matchParentSize().clickable(onClickLabel = "Abrir mapa", onClick = onOpenMap))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onPickLocation) { Text(if (pin == null) "Fijar en el mapa" else "Cambiar") }
                OutlinedButton(onClick = { openInMapsApp(context, project.latitud, project.longitud, project.titulo, project.direccionUbicacion) }) {
                    Text("Cómo llegar")
                }
            }
        }
    }
}
