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

    private fun pending(ctx: Context, code: Int = 77): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, code, Intent(ctx, GeofenceReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

    private fun windowAlarm(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 78, Intent(ctx, GeoWindowReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
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
                val same = prev != null && prev.lat == lat && prev.lon == lon && prev.once == n.geoOnce &&
                    prev.on == n.geoOn && prev.from == n.geoFrom && prev.to == n.geoTo && prev.days == n.geoDays
                GeoEntry(
                    n.id, n.displayTitle(), lat, lon, n.geoRadius.coerceIn(50, 5000), n.geoLabel, n.geoOnce,
                    if (same) prev!!.firedAt else 0,
                    shoppingItems(n),
                    n.geoOn, n.geoFrom, n.geoTo, n.geoDays,
                )
            }
            data.copy(places = places)
        }
        register(ctx)
    }

    private fun fence(it: GeoEntry) = Geofence.Builder()
        .setRequestId(it.id.toString())
        .setCircularRegion(it.lat, it.lon, it.radius.toFloat())
        .setTransitionTypes(if (it.on == 1) Geofence.GEOFENCE_TRANSITION_EXIT else Geofence.GEOFENCE_TRANSITION_ENTER)
        .setExpirationDuration(Geofence.NEVER_EXPIRE)
        .build()

    /**
     * Геозоны сбрасываются при перезагрузке: после неё регистрируем снова из индекса.
     * Места с временным окном (приход) лежат в отдельном наборе с проверкой «уже внутри»: так, если вы пришли до начала окна
     * и остались, уведомление придёт в начале окна.
     */
    fun register(ctx: Context) {
        try {
            val client = LocationServices.getGeofencingClient(ctx)
            client.removeGeofences(pending(ctx, 77))
            client.removeGeofences(pending(ctx, 79))
            scheduleWindows(ctx)
            val active = ReminderStore.load(ctx).places.filter { !(it.once && it.firedAt > 0) }.take(100)
            if (active.isEmpty()) return
            if (ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
            val (timed, plain) = active.partition { it.on == 0 && GeoWindow.hasWindow(it.from, it.to, it.days) }
            if (plain.isNotEmpty()) {
                client.addGeofences(GeofencingRequest.Builder().setInitialTrigger(0).addGeofences(plain.map(::fence)).build(), pending(ctx, 77))
            }
            if (timed.isNotEmpty()) {
                client.addGeofences(
                    GeofencingRequest.Builder().setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER).addGeofences(timed.map(::fence)).build(),
                    pending(ctx, 79),
                )
            }
        } catch (e: SecurityException) {
            android.util.Log.w("MyNotes", "geofence: no permission", e)
        } catch (e: Exception) {
            android.util.Log.e("MyNotes", "geofence failed", e)
        }
    }

    /** Один будильник на ближайшее открытие окна: в этот момент геозоны регистрируются заново и проверяют, не вы ли уже внутри. */
    fun scheduleWindows(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val now = System.currentTimeMillis()
        val next = ReminderStore.load(ctx).places
            .filter { it.on == 0 && !(it.once && it.firedAt > 0) }
            .mapNotNull { GeoWindow.nextOpen(it.from, it.to, it.days, now) }
            .minOrNull()
        val pi = windowAlarm(ctx)
        if (next == null) { am.cancel(pi); return }
        try {
            am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, next, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, next, pi)
        }
    }

    fun onTriggered(ctx: Context, ids: List<Long>, exit: Boolean = false) {
        val now = System.currentTimeMillis()
        var fired = emptyList<GeoEntry>()
        ReminderStore.update(ctx) { data ->
            val hit = data.places.filter {
                it.id in ids && (it.on == 1) == exit && now - it.firedAt > COOLDOWN &&
                    (!GeoWindow.hasWindow(it.from, it.to, it.days) || GeoWindow.isOpen(it.from, it.to, it.days, now))
            }
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
        val tr = ev.geofenceTransition
        if (ev.hasError() || (tr != Geofence.GEOFENCE_TRANSITION_ENTER && tr != Geofence.GEOFENCE_TRANSITION_EXIT)) return
        val ids = ev.triggeringGeofences?.mapNotNull { it.requestId.toLongOrNull() } ?: return
        GeoSync.onTriggered(context, ids, exit = tr == Geofence.GEOFENCE_TRANSITION_EXIT)
    }
}

/** Начало временного окна: заново регистрирует геозоны, чтобы узнать, не находитесь ли вы уже в зоне. */
class GeoWindowReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        GeoSync.register(context)
    }
}
