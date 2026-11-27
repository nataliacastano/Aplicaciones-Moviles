package com.example.gestorempresa.data.geo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Dirección real con sus partes y coordenadas, lista para rellenar un formulario. */
data class AddressSuggestion(
    val street: String,
    val houseNumber: String,
    val locality: String,
    val province: String,
    val postcode: String,
    val country: String,
    val countryCode: String,
    val latitude: Double,
    val longitude: Double,
    /** Nombre del lugar cuando no es una calle (un polígono, un comercio…). */
    val placeName: String = ""
) {
    /** Primera línea: calle y número, o el nombre del lugar. */
    val line1: String get() = listOf(street, houseNumber).filter { it.isNotBlank() }.joinToString(" ").ifBlank { placeName }

    /** Dirección completa en una línea, como se guarda en las fichas. */
    val fullAddress: String get() = listOf(line1, listOf(postcode, locality).filter { it.isNotBlank() }.joinToString(" "),
        province.takeIf { it != locality }.orEmpty(), country).filter { it.isNotBlank() }.joinToString(", ")
}

/**
 * Proveedor de direcciones. La app usa [PhotonAddressProvider] (OpenStreetMap, sin clave).
 * Para usar Google Places bastaría otra implementación de esta interfaz con su clave de API.
 */
interface AddressProvider {
    /** Sugerencias para lo que va escribiendo el usuario. [near] da prioridad a lo que está cerca. */
    suspend fun search(query: String, near: Pair<Double, Double>? = null, limit: Int = 6): List<AddressSuggestion>
    /** Dirección más cercana a un punto del mapa (geocodificación inversa). */
    suspend fun reverse(latitude: Double, longitude: Double): AddressSuggestion?
}

/**
 * Photon (komoot) sobre datos de OpenStreetMap: pensado para autocompletar mientras se escribe,
 * gratuito y sin clave de API. El servidor público tiene un uso razonable limitado; para producción
 * se recomienda un servidor propio o un proveedor de pago.
 */
class PhotonAddressProvider(private val baseUrl: String = "https://photon.komoot.io",
    private val userAgent: String = "GestorEmpresa/1.0 (Android; proyecto academico de gestion empresarial)") : AddressProvider {

    override suspend fun search(query: String, near: Pair<Double, Double>?, limit: Int): List<AddressSuggestion> {
        if (query.trim().length < 3) return emptyList()
        val bias = near?.let { "&lat=${it.first}&lon=${it.second}" }.orEmpty()
        // Photon no admite lang=es; «default» devuelve los nombres locales (en castellano en España).
        return parsePhotonResponse(get("$baseUrl/api/?q=${URLEncoder.encode(query.trim(), "UTF-8")}&limit=${limit + 4}$bias&lang=default"))
            .take(limit)
    }

    override suspend fun reverse(latitude: Double, longitude: Double): AddressSuggestion? =
        parsePhotonResponse(get("$baseUrl/reverse?lat=$latitude&lon=$longitude&limit=1&lang=default")).firstOrNull()

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "application/json")
            check(connection.responseCode == 200) { "El servicio de direcciones respondió ${connection.responseCode}" }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally { connection.disconnect() }
    }
}

private val json = Json { ignoreUnknownKeys = true }

/** Convierte la respuesta GeoJSON de Photon en sugerencias, sin repetidos. Pública para poder probarla. */
fun parsePhotonResponse(body: String): List<AddressSuggestion> {
    val features = json.parseToJsonElement(body).jsonObject["features"]?.jsonArray ?: return emptyList()
    return features.mapNotNull { feature ->
        val obj = feature.jsonObject
        val props = obj["properties"]?.jsonObject ?: return@mapNotNull null
        val coords = obj["geometry"]?.jsonObject?.get("coordinates")?.jsonArray ?: return@mapNotNull null
        val lon = coords.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
        val lat = coords.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
        fun text(key: String) = props.string(key)
        val type = text("type")
        val name = text("name")
        // Si el resultado es la propia calle, su nombre está en «name» y no en «street».
        val street = text("street").ifBlank { if (type == "street") name else "" }
        val postcode = text("postcode")
        val countryCode = text("countrycode").uppercase()
        val province = (if (countryCode == "ES") spanishProvinceFromPostcode(postcode) else null)
            ?: text("county").takeIf { it.isNotBlank() && !it.startsWith("Área") } ?: text("state")
        AddressSuggestion(street = street, houseNumber = text("housenumber"),
            locality = text("city").ifBlank { text("town") }.ifBlank { text("village") }.ifBlank { text("locality") },
            province = province, postcode = postcode, country = text("country"), countryCode = countryCode,
            latitude = lat, longitude = lon, placeName = if (name != street) name else "")
    }.distinctBy { it.fullAddress }
}

private fun JsonObject.string(key: String): String = this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

/** Provincia española a partir de las dos primeras cifras del código postal (más fiable que las regiones de OSM). */
fun spanishProvinceFromPostcode(postcode: String): String? {
    val code = postcode.trim().take(2).toIntOrNull() ?: return null
    return listOf("Álava", "Albacete", "Alicante", "Almería", "Ávila", "Badajoz", "Illes Balears", "Barcelona", "Burgos", "Cáceres",
        "Cádiz", "Castellón", "Ciudad Real", "Córdoba", "A Coruña", "Cuenca", "Girona", "Granada", "Guadalajara", "Gipuzkoa", "Huelva",
        "Huesca", "Jaén", "León", "Lleida", "La Rioja", "Lugo", "Madrid", "Málaga", "Murcia", "Navarra", "Ourense", "Asturias",
        "Palencia", "Las Palmas", "Pontevedra", "Salamanca", "Santa Cruz de Tenerife", "Cantabria", "Segovia", "Sevilla", "Soria",
        "Tarragona", "Teruel", "Toledo", "Valencia", "Valladolid", "Bizkaia", "Zamora", "Zaragoza", "Ceuta", "Melilla").getOrNull(code - 1)
}

/** Distancia en metros entre dos puntos (fórmula del haversine). */
fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * r * atan2(sqrt(a), sqrt(1 - a))
}

/** Relación entre la dirección escrita y el marcador del mapa. */
sealed interface LocationConsistency {
    /** La dirección se eligió de la lista (o se confirmó) y el marcador está en ella. */
    data object Consistent : LocationConsistency
    /** La dirección se escribió a mano: no se sabe si corresponde al marcador. */
    data object Unverified : LocationConsistency
    /** El marcador está lejos del punto de la dirección. */
    data class Mismatch(val meters: Double) : LocationConsistency
    /** Todavía no hay marcador. */
    data object NoMarker : LocationConsistency
}

/**
 * Compara el punto de la dirección elegida con el marcador. Más de [toleranceMeters] se considera
 * una contradicción que hay que confirmar antes de guardar.
 */
fun checkLocationConsistency(addressPoint: Pair<Double, Double>?, marker: Pair<Double, Double>?, toleranceMeters: Double = 150.0): LocationConsistency = when {
    marker == null -> LocationConsistency.NoMarker
    addressPoint == null -> LocationConsistency.Unverified
    else -> distanceMeters(addressPoint.first, addressPoint.second, marker.first, marker.second)
        .let { if (it <= toleranceMeters) LocationConsistency.Consistent else LocationConsistency.Mismatch(it) }
}
