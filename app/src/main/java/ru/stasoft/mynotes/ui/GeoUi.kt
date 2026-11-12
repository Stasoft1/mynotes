package ru.stasoft.mynotes.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import ru.stasoft.mynotes.data.NoteEntity
import ru.stasoft.mynotes.data.PlaceEntity
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private class FoundPlace(val name: String, val lat: Double, val lon: Double, val km: Double)

private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2).let { it * it } +
        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2).let { it * it }
    return 2 * r * Math.asin(Math.sqrt(a))
}

private fun nominatim(q: String, box: String?, limit: Int): JSONArray {
    var url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=$limit&accept-language=ru&q=" + URLEncoder.encode(q, "UTF-8")
    if (box != null) url += "&viewbox=$box&bounded=1"
    val c = URL(url).openConnection() as HttpURLConnection
    c.setRequestProperty("User-Agent", "MyNotes/0.2 (Android notes app)")
    c.connectTimeout = 10000
    c.readTimeout = 15000
    return JSONArray(c.inputStream.bufferedReader().readText())
}

/** Поиск места в OpenStreetMap (Nominatim): сначала в радиусе примерно 25 км от центра карты, потом по всему миру; результаты от ближнего к дальнему. */
private suspend fun searchPlaces(q: String, cLat: Double, cLon: Double): List<FoundPlace> = withContext(Dispatchers.IO) {
    val dLat = 0.25
    val dLon = 0.25 / Math.cos(Math.toRadians(cLat)).coerceAtLeast(0.2)
    val box = "${cLon - dLon},${cLat + dLat},${cLon + dLon},${cLat - dLat}"
    var a = nominatim(q, box, 30)
    if (a.length() == 0) a = nominatim(q, null, 20)
    (0 until a.length()).map { i ->
        val o = a.getJSONObject(i)
        val la = o.getString("lat").toDouble()
        val lo = o.getString("lon").toDouble()
        FoundPlace(o.getString("display_name"), la, lo, distanceKm(cLat, cLon, la, lo))
    }.sortedBy { it.km }.take(8)
}

/** Строка в заметке: где сработает напоминание. */
@Composable
fun GeoRow(note: NoteEntity, onEdit: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(
                "📍 " + note.geoLabel.ifBlank { "Выбранное место" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Напомнить рядом, радиус ${note.geoRadius} м · " + if (note.geoOnce) "один раз" else "каждый раз",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onEdit) { Text("Изменить") }
    }
}

/** Окно выбора места на карте. */
@Composable
fun GeoDialog(
    note: NoteEntity,
    onDismiss: () -> Unit,
    onSave: (lat: Double, lon: Double, radius: Int, label: String, once: Boolean) -> Unit,
    onClear: () -> Unit,
    places: List<PlaceEntity>,
    onSavePlace: (name: String, lat: Double, lon: Double, radius: Int) -> Unit,
    onDeletePlace: (Long) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var lat by remember { mutableStateOf(note.geoLat) }
    var lon by remember { mutableStateOf(note.geoLon) }
    var radius by remember { mutableIntStateOf(if (note.geoRadius > 0) note.geoRadius else 200) }
    var label by remember { mutableStateOf(note.geoLabel) }
    var once by remember { mutableStateOf(note.geoOnce) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<FoundPlace>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var mapRef by remember { mutableStateOf<MapView?>(null) }
    var permTick by remember { mutableIntStateOf(0) }
    var savePlaceDlg by remember { mutableStateOf(false) }
    var newPlaceName by remember { mutableStateOf("") }

    remember {
        Configuration.getInstance().apply {
            load(ctx, ctx.getSharedPreferences("osmdroid", 0))
            userAgentValue = ctx.packageName
            osmdroidBasePath = File(ctx.cacheDir, "osm")
            osmdroidTileCache = File(ctx.cacheDir, "osm/tiles")
        }
        0
    }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) permTick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val fine = remember(permTick) { ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED }
    val bg = remember(permTick) { ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED }

    val fineLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permTick++ }
    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permTick++ }

    fun goTo(p: GeoPoint, zoom: Double = 17.0) {
        mapRef?.controller?.setZoom(zoom)
        mapRef?.controller?.animateTo(p)
    }

    fun myLocation() {
        if (!fine) {
            fineLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }
        try {
            LocationServices.getFusedLocationProviderClient(ctx)
                .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { loc -> if (loc != null) goTo(GeoPoint(loc.latitude, loc.longitude), 16.0) else status = "Местоположение пока не определено" }
        } catch (e: SecurityException) {
            status = "Нет доступа к геопозиции"
        }
    }

    fun doSearch() {
        if (query.isBlank()) return
        status = "Ищу…"
        scope.launch {
            try {
                val c = mapRef?.mapCenter
                results = searchPlaces(query.trim(), c?.latitude ?: lat ?: 59.9343, c?.longitude ?: lon ?: 30.3351)
                status = if (results.isEmpty()) "Ничего не найдено" else ""
            } catch (e: Exception) {
                status = "Поиск не сработал: ${e.message?.take(80)}"
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Закрыть") }
                    Text("Напомнить по месту", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (note.geoLat != null) TextButton(onClick = onClear) { Text("Снять") }
                    TextButton(
                        enabled = lat != null,
                        onClick = { onSave(lat!!, lon!!, radius, label.trim(), once) },
                    ) { Text("Сохранить") }
                }
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.weight(1f).height(44.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { doSearch() }),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (query.isEmpty()) Text("Найти место:", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    inner()
                                }
                            },
                        )
                        IconButton(onClick = { doSearch() }, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.Search, "Найти") }
                    }
                    IconButton(onClick = { myLocation() }) { Icon(Icons.Filled.MyLocation, "Где я") }
                }
                if (results.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
                        results.forEach { r ->
                            Text(
                                (if (r.km < 1) "${(r.km * 1000).toInt()} м · " else "%.1f км · ".format(r.km)) + r.name,
                                maxLines = 2,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    lat = r.lat
                                    lon = r.lon
                                    label = r.name.substringBefore(',')
                                    results = emptyList()
                                    goTo(GeoPoint(r.lat, r.lon))
                                }.padding(vertical = 8.dp, horizontal = 4.dp),
                            )
                            HorizontalDivider()
                        }
                    }
                }
                if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { c ->
                            MapView(c).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)
                                zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
                                controller.setZoom(if (lat != null) 16.0 else 12.0)
                                controller.setCenter(GeoPoint(lat ?: 59.9343, lon ?: 30.3351))
                                overlays.add(
                                    MapEventsOverlay(object : MapEventsReceiver {
                                        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                            lat = p.latitude
                                            lon = p.longitude
                                            label = ""
                                            results = emptyList()
                                            return true
                                        }
                                        override fun longPressHelper(p: GeoPoint): Boolean = false
                                    }),
                                )
                                mapRef = this
                            }
                        },
                        update = { mv ->
                            while (mv.overlays.size > 1) mv.overlays.removeAt(1)
                            val la = lat
                            val lo = lon
                            if (la != null && lo != null) {
                                val c = GeoPoint(la, lo)
                                mv.overlays.add(
                                    Polygon(mv).apply {
                                        points = Polygon.pointsAsCircle(c, radius.toDouble())
                                        fillPaint.color = 0x332196F3
                                        outlinePaint.color = 0xFF1E88E5.toInt()
                                        outlinePaint.strokeWidth = 3f
                                    },
                                )
                                mv.overlays.add(
                                    Marker(mv).apply {
                                        position = c
                                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    },
                                )
                            }
                            mv.invalidate()
                        },
                    )
                    DisposableEffect(Unit) { onDispose { mapRef?.onDetach() } }
                }
                val saved = places.firstOrNull { p -> lat != null && lon != null && Math.abs(p.lat - lat!!) < 1e-6 && Math.abs(p.lon - lon!!) < 1e-6 }
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp)) {
                    // Сохранённые места: нажать — выбрать; ниже можно добавить текущую точку или удалить выбранное место.
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (places.isEmpty()) Text("Сохранённых мест пока нет", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                        places.forEach { p ->
                            FilterChip(
                                selected = saved?.id == p.id,
                                onClick = {
                                    lat = p.lat
                                    lon = p.lon
                                    radius = p.radius
                                    label = p.name
                                    results = emptyList()
                                    goTo(GeoPoint(p.lat, p.lon))
                                },
                                label = { Text(p.name, maxLines = 1) },
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when {
                                lat == null -> "Нажмите на карту или выберите место"
                                else -> "📍 " + label.ifBlank { "Выбранное место" }
                            },
                            style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        if (lat != null && saved == null) TextButton(onClick = { newPlaceName = label; savePlaceDlg = true }) { Text("Сохранить место") }
                        if (saved != null) TextButton(onClick = { onDeletePlace(saved.id) }) { Text("Удалить место", color = MaterialTheme.colorScheme.error) }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Радиус $radius м", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(110.dp))
                        Slider(
                            value = radius.toFloat(), onValueChange = { radius = (it / 50f).toInt() * 50 },
                            valueRange = 100f..1000f, modifier = Modifier.weight(1f),
                        )
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.clickable { once = !once }, verticalAlignment = Alignment.CenterVertically) {
                            AppCheckbox(checked = once, onCheckedChange = { once = it })
                            Text("Один раз", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            when {
                                !fine -> "Нет доступа к геопозиции"
                                !bg -> "Нужен доступ «Всегда»"
                                else -> "Геопозиция разрешена"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (fine && bg) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f).padding(start = 12.dp),
                        )
                        if (!fine) TextButton(onClick = { fineLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) { Text("Разрешить") }
                        else if (!bg) TextButton(onClick = { bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }) { Text("Разрешить") }
                    }
                }
            }
        }
    }
    if (savePlaceDlg) {
        AlertDialog(
            onDismissRequest = { savePlaceDlg = false },
            title = { Text("Сохранить место") },
            text = {
                OutlinedTextField(
                    value = newPlaceName, onValueChange = { newPlaceName = it }, singleLine = true,
                    label = { Text("Название (например, Пятёрочка у дома)") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val la = lat
                    val lo = lon
                    if (la != null && lo != null) {
                        label = newPlaceName.trim().ifBlank { "Место" }
                        onSavePlace(label, la, lo, radius)
                    }
                    savePlaceDlg = false
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { savePlaceDlg = false }) { Text("Отмена") } },
        )
    }
}
