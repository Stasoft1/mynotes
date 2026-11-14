package ru.stasoft.mynotes.reminders

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import ru.stasoft.mynotes.data.NoteEntity
import ru.stasoft.mynotes.data.displayTitle

/** Связь заметок с геозонами телефона (Google Play services): адреса и названия лежат в индексе, база для срабатывания не нужна. */
object GeoSync {
    private const val COOLDOWN = 30 * 60 * 1000L

    private fun pending(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 77, Intent(ctx, GeofenceReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

    /** Что показать в уведомлении: неотмеченные пункты списка либо первые строки текста. */
    private fun shoppingItems(n: NoteEntity): List<String> {
        val lines = n.body.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val src = if (n.checklist) lines.filter { !it.startsWith("[x] ") && !it.startsWith("[X] ") } else lines
        return src.take(12)
    }

    /** Пересобирает места из заметок (сохраняя, какие уже сработали) и заново регистрирует геозоны. */
    fun rebuild(ctx: Context, notes: List<NoteEntity>) {
        ReminderStore.update(ctx) { data ->
            val old = data.places.associateBy { it.id }
            val places = notes.mapNotNull { n ->
                val lat = n.geoLat ?: return@mapNotNull null
                val lon = n.geoLon ?: return@mapNotNull null
                val prev = old[n.id]
                val same = prev != null && prev.lat == lat && prev.lon == lon && prev.once == n.geoOnce
                GeoEntry(
                    n.id, n.displayTitle(), lat, lon, n.geoRadius.coerceIn(50, 5000), n.geoLabel, n.geoOnce,
                    if (same) prev!!.firedAt else 0,
                    shoppingItems(n),
                )
            }
            data.copy(places = places)
        }
        register(ctx)
    }

    /** Геозоны сбрасываются при перезагрузке: после неё регистрируем снова из индекса. */
    fun register(ctx: Context) {
        try {
            val client = LocationServices.getGeofencingClient(ctx)
            val pi = pending(ctx)
            client.removeGeofences(pi)
            val active = ReminderStore.load(ctx).places.filter { !(it.once && it.firedAt > 0) }.take(100)
            if (active.isEmpty()) return
            if (ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
            val list = active.map {
                Geofence.Builder()
                    .setRequestId(it.id.toString())
                    .setCircularRegion(it.lat, it.lon, it.radius.toFloat())
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .build()
            }
            val req = GeofencingRequest.Builder().setInitialTrigger(0).addGeofences(list).build()
            client.addGeofences(req, pi)
        } catch (e: SecurityException) {
            android.util.Log.w("MyNotes", "geofence: no permission", e)
        } catch (e: Exception) {
            android.util.Log.e("MyNotes", "geofence failed", e)
        }
    }

    fun onTriggered(ctx: Context, ids: List<Long>) {
        val now = System.currentTimeMillis()
        var fired = emptyList<GeoEntry>()
        ReminderStore.update(ctx) { data ->
            val hit = data.places.filter { it.id in ids && now - it.firedAt > COOLDOWN }
            fired = hit
            data.copy(places = data.places.map { p -> if (hit.any { it.id == p.id }) p.copy(firedAt = now) else p })
        }
        fired.forEach { ReminderNotifier.showGeo(ctx, it) }
        if (fired.any { it.once }) register(ctx)
    }
}

class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ev = GeofencingEvent.fromIntent(intent) ?: return
        if (ev.hasError() || ev.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return
        val ids = ev.triggeringGeofences?.mapNotNull { it.requestId.toLongOrNull() } ?: return
        GeoSync.onTriggered(context, ids)
    }
}
