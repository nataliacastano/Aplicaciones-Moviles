package com.example.gestorempresa.ui.advanced.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.gestorempresa.data.geo.AddressProvider
import com.example.gestorempresa.data.geo.AddressSuggestion
import com.example.gestorempresa.data.geo.PhotonAddressProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Proveedor compartido por toda la app (OpenStreetMap/Photon, sin clave de API). */
val defaultAddressProvider: AddressProvider by lazy { PhotonAddressProvider() }

/**
 * Campo de dirección con sugerencias reales mientras se escribe (como en las tiendas online).
 * Al elegir una sugerencia se llama a [onSelected] con calle, localidad, provincia, código postal,
 * país y coordenadas. Si después el usuario edita el texto a mano, [onValueChange] avisa y la
 * dirección deja de estar comprobada (el formulario debe olvidar las coordenadas anteriores).
 *
 * Sin conexión, o si el servicio falla, se puede seguir escribiendo la dirección a mano.
 */
@Composable
fun AddressAutocompleteField(value: String, onValueChange: (String) -> Unit, onSelected: (AddressSuggestion) -> Unit,
    modifier: Modifier = Modifier, label: String = "Dirección", enabled: Boolean = true, verified: Boolean = false,
    near: Pair<Double, Double>? = null, provider: AddressProvider = defaultAddressProvider) {
    var suggestions by remember { mutableStateOf(emptyList<AddressSuggestion>()) }
    var loading by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    // Se busca solo cuando el usuario escribe, no cuando el texto viene de elegir una sugerencia.
    var userTyped by remember { mutableStateOf(false) }
    val latestNear by rememberUpdatedState(near)

    LaunchedEffect(value, userTyped) {
        if (!userTyped || value.trim().length < 3) { suggestions = emptyList(); loading = false; return@LaunchedEffect }
        delay(450) // Espera a que deje de escribir para no saturar el servicio.
        loading = true
        try {
            suggestions = provider.search(value, latestNear)
            problem = if (suggestions.isEmpty()) "Sin resultados. Revisa la dirección o escríbela completa." else null
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { suggestions = emptyList(); problem = "No se pueden cargar sugerencias (¿sin conexión?). Puedes escribirla a mano." }
        finally { loading = false }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(value = value, onValueChange = { userTyped = true; onValueChange(it) }, label = { Text(label) },
            enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth(),
            supportingText = {
                Text(when {
                    loading -> "Buscando direcciones…"
                    problem != null && userTyped -> problem!!
                    verified -> "✓ Dirección comprobada en el mapa"
                    value.isNotBlank() -> "Elige una sugerencia para completar los datos y la ubicación"
                    else -> "Escribe al menos 3 letras, por ejemplo «Gran Vía, Mur»"
                })
            },
            trailingIcon = { if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) })
        if (suggestions.isNotEmpty()) {
            Card(shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(4.dp)) {
                Column {
                    suggestions.forEach { suggestion ->
                        Column(Modifier.fillMaxWidth().clickable {
                            userTyped = false
                            suggestions = emptyList()
                            problem = null
                            onSelected(suggestion)
                        }.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(suggestion.line1.ifBlank { suggestion.locality }, style = MaterialTheme.typography.bodyLarge)
                            Text(listOf(suggestion.postcode, suggestion.locality, suggestion.province, suggestion.country)
                                .filter { it.isNotBlank() }.distinct().joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider()
                    }
                    Text("Datos © OpenStreetMap", Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
