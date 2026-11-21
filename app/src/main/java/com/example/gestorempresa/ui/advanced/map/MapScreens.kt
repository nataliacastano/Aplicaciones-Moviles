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
 * Mapa con la ubicación de todas las obras, servicios y proyectos (y, por tanto, de sus clientes).
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
            Text("Mapa de obras y servicios", style = MaterialTheme.typography.headlineSmall)
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
 * Pantalla para fijar la ubicación de un servicio o proyecto:
 * busca la dirección con el geocodificador o toca el punto exacto en el mapa.
 */
@Composable
fun LocationPickerScreen(project: UnidadTrabajoEntity, workspace: HomeWorkspaceViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saving by workspace.saving.collectAsState()
    var latitude by rememberSaveable { mutableStateOf(project.latitud) }
    var longitude by rememberSaveable { mutableStateOf(project.longitud) }
    var query by rememberSaveable { mutableStateOf(project.direccionUbicacion) }
    var searching by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var focus by remember { mutableIntStateOf(0) }
    val pin = if (latitude != null && longitude != null) listOf(MapPin(project.id, project.titulo, query, latitude!!, longitude!!)) else emptyList()

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack, enabled = !saving) { Text("Volver") }
            Text("Ubicación de ${project.titulo}", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(query, { query = it }, label = { Text("Dirección") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !searching && query.isNotBlank(), onClick = {
                    searching = true; message = null
                    scope.launch {
                        val found = geocode(context, query)
                        searching = false
                        if (found == null) message = "No se ha encontrado la dirección. Toca el punto en el mapa."
                        else { latitude = found.first; longitude = found.second; focus++ }
                    }
                }) { Text(if (searching) "Buscando…" else "🔎 Buscar dirección") }
                Button(enabled = !saving && pin.isNotEmpty(), onClick = {
                    workspace.save({ setProjectLocation(project.id, latitude, longitude) }, onBack)
                }) { Text("Guardar") }
            }
            Text(message ?: if (pin.isEmpty()) "Toca el mapa para marcar la ubicación." else "%.5f, %.5f · toca el mapa para corregirla.".format(latitude, longitude),
                style = MaterialTheme.typography.bodySmall,
                color = if (message != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            if (project.latitud != null) TextButton(enabled = !saving, onClick = {
                workspace.save({ setProjectLocation(project.id, null, null) }, onBack)
            }) { Text("Quitar ubicación del mapa") }
        }
        OsmMap(pin, Modifier.weight(1f).fillMaxWidth(), onTap = { lat, lon -> latitude = lat; longitude = lon }, focusKey = focus)
    }
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
