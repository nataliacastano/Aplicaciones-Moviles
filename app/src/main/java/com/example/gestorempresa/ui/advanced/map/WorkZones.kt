package com.example.gestorempresa.ui.advanced.map

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.gestorempresa.data.local.HomeWorkerMeta
import com.example.gestorempresa.data.local.HomeZoneLocation
import com.example.gestorempresa.ui.home.HomeWorkspaceState
import com.example.gestorempresa.ui.home.HomeWorkspaceViewModel
import com.example.gestorempresa.ui.home.normalizedSearch

/** Zona laboral de un trabajador, con su ubicación si se ha marcado en el mapa. */
data class WorkZone(val name: String, val location: HomeZoneLocation?)

/** Zonas laborales de un trabajador (pueden ser varias, independientes de su dirección personal). */
fun HomeWorkspaceState.workZonesOf(workerKind: String, workerId: Long): List<WorkZone> =
    workerMeta.filter { it.workerKind == workerKind && it.workerId == workerId && it.workZone.isNotBlank() }
        .map { meta -> WorkZone(meta.workZone, zoneLocations.firstOrNull { it.workerKind == workerKind && it.workerId == workerId && it.workZone == meta.workZone }) }
        .sortedBy { it.name.normalizedSearch() }

/**
 * Diálogo reutilizable para crear o editar una zona laboral: nombre y ubicación en el mapa
 * (con autocompletado y la misma comprobación dirección ↔ marcador que los proyectos).
 * El formulario del miembro 3 puede usarlo para elegir zonas durante el alta.
 */
@Composable
fun WorkZonePickerDialog(initialName: String = "", initialLocation: HomeZoneLocation? = null, existingNames: List<String> = emptyList(),
    saving: Boolean = false, onConfirm: (name: String, location: ConfirmedLocation) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    val duplicated = existingNames.any { it.normalizedSearch() == name.normalizedSearch() && it != initialName }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre de la zona *") }, singleLine = true,
                    enabled = !saving && initialName.isBlank(), isError = duplicated || name.isBlank(),
                    supportingText = { Text(if (duplicated) "Ya existe una zona con ese nombre." else "Por ejemplo: Murcia centro, Polígono Oeste…") },
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp))
                LocationPickerContent(title = "Ubicación de la zona", initialAddress = initialLocation?.address.orEmpty(),
                    initialPoint = initialLocation?.let { it.latitude to it.longitude }, saving = saving, onCancel = onDismiss,
                    onConfirm = { location -> if (name.isNotBlank() && !duplicated) onConfirm(name.trim(), location) })
            }
        }
    }
}

/** Tarjeta de la ficha de un trabajador con sus zonas laborales y un mapa con todas ellas. */
@Composable
fun WorkZonesCard(workerKind: String, workerId: Long, state: HomeWorkspaceState, workspace: HomeWorkspaceViewModel) {
    val zones = state.workZonesOf(workerKind, workerId)
    val saving by workspace.saving.collectAsState()
    var editing by remember { mutableStateOf<WorkZone?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var removing by remember { mutableStateOf<WorkZone?>(null) }
    val pins = zones.mapIndexedNotNull { i, z -> z.location?.let { MapPin(i.toLong(), z.name, it.address, it.latitude, it.longitude) } }

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("📍 Zonas laborales", style = MaterialTheme.typography.titleMedium)
            Text("Lugares donde suele trabajar. No tienen que ver con su dirección personal.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (zones.isEmpty()) Text("Sin zonas laborales.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            zones.forEach { zone ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(zone.name, style = MaterialTheme.typography.bodyLarge)
                        Text(zone.location?.address ?: "Sin ubicar en el mapa", style = MaterialTheme.typography.bodySmall,
                            color = if (zone.location == null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(enabled = !saving, onClick = { editing = zone }) { Text(if (zone.location == null) "Ubicar" else "Cambiar") }
                    TextButton(enabled = !saving, onClick = { removing = zone }) { Text("Quitar") }
                }
            }
            if (pins.isNotEmpty()) Box(Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp))) {
                OsmMap(pins, Modifier.fillMaxSize(), interactive = false)
            }
            OutlinedButton(enabled = !saving, onClick = { adding = true }) { Text("Añadir zona laboral") }
        }
    }

    fun save(name: String, location: ConfirmedLocation, close: () -> Unit) = workspace.save({
        saveWorkZone(HomeWorkerMeta(workerKind, workerId, name),
            HomeZoneLocation(workerKind, workerId, name, location.address, location.latitude, location.longitude))
    }, close)
    if (adding) WorkZonePickerDialog(existingNames = zones.map { it.name }, saving = saving, onDismiss = { adding = false },
        onConfirm = { name, location -> save(name, location) { adding = false } })
    editing?.let { zone ->
        WorkZonePickerDialog(initialName = zone.name, initialLocation = zone.location, saving = saving, onDismiss = { editing = null },
            onConfirm = { name, location -> save(name, location) { editing = null } })
    }
    removing?.let { zone ->
        AlertDialog(onDismissRequest = { removing = null }, title = { Text("¿Quitar la zona «${zone.name}»?") },
            text = { Text("Se quitará de este trabajador junto con su ubicación.") },
            confirmButton = { Button(onClick = { workspace.save({ removeWorkZone(workerKind, workerId, zone.name) }) { removing = null } }) { Text("Quitar") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancelar") } })
    }
}
