package com.example.gestorempresa.ui.advanced.map

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.gestorempresa.data.geo.AddressProvider
import com.example.gestorempresa.data.geo.AddressSuggestion
import com.example.gestorempresa.data.geo.LocationConsistency
import com.example.gestorempresa.data.geo.checkLocationConsistency
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Ubicación confirmada: dirección escrita y punto del mapa, ya coherentes (o aceptados por el usuario). */
data class ConfirmedLocation(val address: String, val latitude: Double, val longitude: Double, val details: AddressSuggestion? = null)

/**
 * Selector de ubicación reutilizable (servicios, proyectos, zonas laborales…). Mantiene sincronizados
 * la dirección y el marcador:
 * - Al elegir una dirección sugerida, el marcador se coloca en sus coordenadas.
 * - Al mover el marcador, se busca la dirección de ese punto y se propone usarla.
 * - Si la dirección y el marcador no coinciden (o la dirección no se ha comprobado), se avisa antes
 *   de guardar y nunca se guardan datos contradictorios sin la confirmación del usuario.
 */
@Composable
fun LocationPickerContent(title: String, initialAddress: String, initialPoint: Pair<Double, Double>?,
    onConfirm: (ConfirmedLocation) -> Unit, onCancel: () -> Unit, saving: Boolean = false,
    onRemove: (() -> Unit)? = null, provider: AddressProvider = defaultAddressProvider) {
    val scope = rememberCoroutineScope()
    var address by rememberSaveable { mutableStateOf(initialAddress) }
    // Punto de la dirección: se conoce si la dirección se eligió de la lista o se tomó del marcador.
    // Una ubicación ya guardada se considera comprobada al abrir.
    var addressLat by rememberSaveable { mutableStateOf(initialPoint?.first) }
    var addressLon by rememberSaveable { mutableStateOf(initialPoint?.second) }
    var markerLat by rememberSaveable { mutableStateOf(initialPoint?.first) }
    var markerLon by rememberSaveable { mutableStateOf(initialPoint?.second) }
    var details by remember { mutableStateOf<AddressSuggestion?>(null) }
    var proposal by remember { mutableStateOf<AddressSuggestion?>(null) }
    var reverseState by remember { mutableStateOf<String?>(null) }
    var warning by remember { mutableStateOf<LocationConsistency?>(null) }
    var focus by remember { mutableIntStateOf(0) }
    val addressPoint = if (addressLat != null && addressLon != null) addressLat!! to addressLon!! else null
    val marker = if (markerLat != null && markerLon != null) markerLat!! to markerLon!! else null
    val consistency = checkLocationConsistency(addressPoint, marker)

    fun useSuggestion(s: AddressSuggestion) {
        address = s.fullAddress; details = s
        addressLat = s.latitude; addressLon = s.longitude
        markerLat = s.latitude; markerLon = s.longitude
        proposal = null; reverseState = null; focus++
    }
    fun confirm() {
        val point = marker ?: return
        onConfirm(ConfirmedLocation(address.trim(), point.first, point.second, details))
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp).heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel, enabled = !saving) { Text("Volver") }
            Text(title, style = MaterialTheme.typography.titleLarge)
            AddressAutocompleteField(address, { typed ->
                address = typed
                // Escrita a mano: ya no se sabe si coincide con el marcador.
                addressLat = null; addressLon = null; details = null
            }, ::useSuggestion, verified = consistency == LocationConsistency.Consistent, near = marker, enabled = !saving, provider = provider)
            proposal?.let { found ->
                ProposalCard("Dirección en el punto marcado:", found.fullAddress,
                    primary = "Usar esta dirección" to { useSuggestion(found) },
                    secondary = "Mantener la escrita" to { proposal = null })
            }
            reverseState?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(when (consistency) {
                LocationConsistency.NoMarker -> "Elige una dirección de la lista o toca el mapa para marcar la ubicación."
                LocationConsistency.Consistent -> "✓ La dirección y el marcador coinciden."
                LocationConsistency.Unverified -> "⚠️ La dirección escrita no se ha comprobado con el marcador."
                is LocationConsistency.Mismatch -> "⚠️ El marcador está a ${distanceLabel(consistency.meters)} de la dirección."
            }, style = MaterialTheme.typography.bodySmall,
                color = if (consistency is LocationConsistency.Mismatch || consistency == LocationConsistency.Unverified) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !saving && marker != null && address.isNotBlank(), onClick = {
                    if (consistency == LocationConsistency.Consistent) confirm() else warning = consistency
                }) { Text(if (saving) "Guardando…" else "Guardar ubicación") }
                onRemove?.let { OutlinedButton(enabled = !saving, onClick = it) { Text("Quitar del mapa") } }
            }
        }
        OsmMap(marker?.let { listOf(MapPin(0, title, address, it.first, it.second)) }.orEmpty(), Modifier.weight(1f).fillMaxWidth(),
            focusKey = focus, onTap = { lat, lon ->
                markerLat = lat; markerLon = lon; proposal = null
                reverseState = "Buscando la dirección de ese punto…"
                scope.launch {
                    try {
                        val found = provider.reverse(lat, lon)
                        // Solo si el marcador no se ha vuelto a mover mientras tanto.
                        if (markerLat == lat && markerLon == lon) {
                            proposal = found
                            reverseState = if (found == null) "No se ha encontrado una dirección en ese punto." else null
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { reverseState = "No se ha podido obtener la dirección de ese punto (¿sin conexión?)." }
                }
            })
    }

    warning?.let { problem ->
        AlertDialog(onDismissRequest = { warning = null },
            title = { Text(if (problem is LocationConsistency.Mismatch) "La dirección y el mapa no coinciden" else "Dirección sin comprobar") },
            text = { Text(if (problem is LocationConsistency.Mismatch)
                "El marcador está a ${distanceLabel(problem.meters)} de «$address». Si guardas así, la ficha tendrá una dirección y una ubicación distintas."
                else "«$address» no se ha elegido de la lista de sugerencias, así que no se sabe si corresponde al punto marcado.") },
            confirmButton = { TextButton(onClick = { warning = null; confirm() }) { Text("Guardar igualmente") } },
            dismissButton = {
                Row {
                    if (problem is LocationConsistency.Mismatch && addressPoint != null) TextButton(onClick = {
                        markerLat = addressPoint.first; markerLon = addressPoint.second; focus++; warning = null
                    }) { Text("Mover marcador") }
                    TextButton(onClick = { warning = null }) { Text("Revisar") }
                }
            })
    }
}

@Composable
private fun ProposalCard(title: String, text: String, primary: Pair<String, () -> Unit>, secondary: Pair<String, () -> Unit>) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = primary.second) { Text(primary.first) }
                TextButton(onClick = secondary.second) { Text(secondary.first) }
            }
        }
    }
}

fun distanceLabel(meters: Double): String = if (meters >= 1000) "%.1f km".format(meters / 1000) else "%.0f m".format(meters)
