package com.example.gestorempresa.ui.advanced.map

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

/** Punto que se dibuja en el mapa. */
data class MapPin(val id: Long, val title: String, val snippet: String, val latitude: Double, val longitude: Double)

/** Centro de España, para cuando todavía no hay ninguna ubicación. */
val defaultMapCenter = GeoPoint(40.0, -3.7)

/**
 * Mapa de OpenStreetMap (osmdroid). No necesita clave de API, solo conexión a internet.
 *
 * @param interactive si es false el mapa es una vista previa fija (para dentro de una ficha).
 * @param onTap se llama al tocar un punto vacío del mapa (para elegir una ubicación).
 */
@Composable
fun OsmMap(pins: List<MapPin>, modifier: Modifier = Modifier, interactive: Boolean = true,
    onPinClick: (MapPin) -> Unit = {}, onTap: ((Double, Double) -> Unit)? = null, focusKey: Any? = null) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { configureOsmdroid(context); createMapView(context) }
    val currentPinClick by rememberUpdatedState(onPinClick)
    val currentTap by rememberUpdatedState(onTap)

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose { lifecycle.removeObserver(observer); mapView.onPause(); mapView.onDetach() }
    }

    // Centra el mapa en los puntos cuando cambian (o cuando cambia focusKey).
    LaunchedEffect(mapView, pins.map { it.latitude to it.longitude }, focusKey) {
        mapView.post { focus(mapView, pins) }
    }

    // clipToBounds: osmdroid dibuja las teselas fuera de su espacio y taparía la barra superior.
    AndroidView(factory = { mapView }, modifier = modifier.clipToBounds(), update = { view ->
        view.setMultiTouchControls(interactive)
        view.isClickable = interactive
        view.isEnabled = interactive
        view.overlays.clear()
        // La licencia de OpenStreetMap obliga a mostrar el crédito «© OpenStreetMap contributors».
        view.overlays.add(CopyrightOverlay(view.context))
        if (onTap != null) view.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean { currentTap?.invoke(p.latitude, p.longitude); return true }
            override fun longPressHelper(p: GeoPoint): Boolean { currentTap?.invoke(p.latitude, p.longitude); return true }
        }))
        pins.forEach { pin ->
            view.overlays.add(Marker(view).apply {
                position = GeoPoint(pin.latitude, pin.longitude)
                title = pin.title
                snippet = pin.snippet
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                setOnMarkerClickListener { marker, map ->
                    if (marker.isInfoWindowShown) { marker.closeInfoWindow(); currentPinClick(pin) } else marker.showInfoWindow()
                    map.controller.animateTo(marker.position)
                    true
                }
            })
        }
        view.invalidate()
    })
}

private fun focus(view: MapView, pins: List<MapPin>) {
    when {
        pins.isEmpty() -> { view.controller.setZoom(5.5); view.controller.setCenter(defaultMapCenter) }
        pins.size == 1 -> { view.controller.setZoom(15.0); view.controller.setCenter(GeoPoint(pins[0].latitude, pins[0].longitude)) }
        else -> {
            val box = BoundingBox.fromGeoPoints(pins.map { GeoPoint(it.latitude, it.longitude) })
            if (view.width > 0 && view.height > 0) view.zoomToBoundingBox(box.increaseByScale(1.3f), false, 64)
            else { view.controller.setZoom(10.0); view.controller.setCenter(box.centerWithDateLine) }
        }
    }
}

private fun createMapView(context: Context) = MapView(context).apply {
    setTileSource(TileSourceFactory.MAPNIK)
    setMultiTouchControls(true)
    zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
    minZoomLevel = 3.0
    maxZoomLevel = 19.0
    isTilesScaledToDpi = true
    controller.setZoom(5.5)
    controller.setCenter(defaultMapCenter)
}

/** osmdroid necesita identificarse ante los servidores de OpenStreetMap y una carpeta para guardar las teselas. */
private fun configureOsmdroid(context: Context) {
    val config = Configuration.getInstance()
    // Los servidores de OpenStreetMap bloquean los identificadores genéricos (com.example…, osmdroid…).
    config.userAgentValue = "GestorEmpresa/1.0 (Android; proyecto academico de gestion empresarial)"
    val base = File(context.cacheDir, "osmdroid")
    config.osmdroidBasePath = base
    config.osmdroidTileCache = File(base, "tiles")
}

/**
 * Busca las coordenadas de una dirección con el geocodificador del sistema.
 * Devuelve null si no se encuentra o si el dispositivo no tiene servicio de geocodificación.
 */
suspend fun geocode(context: Context, address: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
    if (address.isBlank() || !Geocoder.isPresent()) return@withContext null
    val geocoder = Geocoder(context, Locale("es", "ES"))
    try {
        if (Build.VERSION.SDK_INT >= 33) suspendCancellableCoroutine { cont ->
            geocoder.getFromLocationName(address, 1, object : Geocoder.GeocodeListener {
                override fun onGeocode(results: MutableList<android.location.Address>) {
                    cont.resume(results.firstOrNull()?.let { it.latitude to it.longitude })
                }
                override fun onError(errorMessage: String?) { cont.resume(null) }
            })
        } else @Suppress("DEPRECATION") geocoder.getFromLocationName(address, 1)?.firstOrNull()?.let { it.latitude to it.longitude }
    } catch (_: Exception) { null }
}

/** Abre la ubicación en la app de mapas del móvil (Google Maps u otra) para ver la ruta. */
fun openInMapsApp(context: Context, latitude: Double?, longitude: Double?, label: String, address: String) {
    val uri = if (latitude != null && longitude != null) Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude(${Uri.encode(label)})")
        else Uri.parse("geo:0,0?q=${Uri.encode(address)}")
    try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: ActivityNotFoundException) {
        val web = if (latitude != null && longitude != null) "https://www.openstreetmap.org/?mlat=$latitude&mlon=$longitude#map=17/$latitude/$longitude"
            else "https://www.openstreetmap.org/search?query=${Uri.encode(address)}"
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(web))) } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No hay ninguna app de mapas disponible", Toast.LENGTH_SHORT).show()
        }
    }
}
