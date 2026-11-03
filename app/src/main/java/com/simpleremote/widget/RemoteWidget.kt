package com.simpleremote.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import com.simpleremote.MainActivity
import com.simpleremote.R
import com.simpleremote.data.AcAction
import com.simpleremote.data.AppServices
import com.simpleremote.data.DeviceCategory
import com.simpleremote.data.DeviceRemote
import com.simpleremote.data.RemoteButton
import com.simpleremote.data.SavedRemote
import com.simpleremote.data.defaultState
import com.simpleremote.data.normalise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Home-screen widget for one saved remote: power plus three buttons.
 * AC: power, temp −, current temperature (opens the app), temp +.
 * Other devices: power and the best three of volume/mute/channel, fan speed/swing/timer, brightness...
 */
class RemoteWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        scope.launch {
            try { ids.forEach { render(context, manager, it) } } finally { pending.finish() }
        }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { WidgetPrefs.clear(context, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PRESS) return super.onReceive(context, intent)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val action = intent.getStringExtra(EXTRA_ACTION) ?: return
        val pending = goAsync()
        scope.launch {
            try { press(context, id, action) } finally { pending.finish() }
        }
    }

    companion object {
        private const val ACTION_PRESS = "com.simpleremote.widget.PRESS"
        private const val EXTRA_ACTION = "action"
        const val EXTRA_OPEN_UID = "open_remote_uid"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private const val POWER = "power"
        private const val TEMP_DOWN = "ac_temp_down"
        private const val TEMP_UP = "ac_temp_up"
        private const val KEY = "key:"
        private const val EXTRA = "extra:"

        /** Re-renders every widget, e.g. after a remote was renamed, deleted or its AC state changed. */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = runCatching { manager.getAppWidgetIds(ComponentName(context, RemoteWidget::class.java)) }.getOrNull()
            if (ids == null || ids.isEmpty()) return
            scope.launch { ids.forEach { render(context, manager, it) } }
        }

        suspend fun render(context: Context, manager: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_remote)
            val svc = AppServices.get(context)
            val uid = WidgetPrefs.uid(context, id)
            val remote = uid?.let { u -> svc.store.load().firstOrNull { it.uid == u } }

            if (remote == null) {
                views.setTextViewText(R.id.widget_title, if (uid == null) "Tap to choose a remote" else "Remote was deleted")
                views.setTextViewText(R.id.widget_status, "")
                views.setViewVisibility(R.id.widget_keys, View.GONE)
                views.setOnClickPendingIntent(R.id.widget_root, configIntent(context, id))
                manager.updateAppWidget(id, views)
                return
            }

            views.setViewVisibility(R.id.widget_keys, View.VISIBLE)
            views.setTextViewText(R.id.widget_title, remote.name)
            views.setOnClickPendingIntent(R.id.widget_header, openAppIntent(context, remote.uid))
            views.setOnClickPendingIntent(R.id.slot0, pressIntent(context, id, 0, POWER))

            val slots: List<Pair<String, String?>> = if (remote.type == DeviceCategory.AC.id) {
                val model = runCatching { svc.catalog.model(remote.deviceId) }.getOrNull()
                val state = model?.let { it.normalise(remote.state ?: it.defaultState()) }
                val unit = if (model?.fahrenheit == true) "°F" else "°"
                views.setTextViewText(R.id.widget_status,
                    if (state?.power == true) state.mode.replace('_', ' ').replaceFirstChar { it.uppercase() } else "Off")
                listOf("−" to TEMP_DOWN, (state?.let { "${fmt(it.temp)}$unit" } ?: "") to null, "+" to TEMP_UP)
            } else {
                views.setTextViewText(R.id.widget_status, DeviceCategory.of(remote.type).label)
                val r = svc.devices.remote(remote.deviceId)
                (r?.let { pickButtons(it) } ?: emptyList()).map { b ->
                    shortLabel(b) to (if (b.key.isNotEmpty()) KEY + b.key else EXTRA + b.label)
                }
            }
            listOf(R.id.slot1, R.id.slot2, R.id.slot3).forEachIndexed { i, viewId ->
                val (label, action) = slots.getOrNull(i) ?: ("" to null)
                views.setViewVisibility(viewId, if (i < slots.size) View.VISIBLE else View.INVISIBLE)
                views.setTextViewText(viewId, label)
                views.setOnClickPendingIntent(viewId,
                    if (action != null) pressIntent(context, id, i + 1, action) else openAppIntent(context, remote.uid))
            }
            manager.updateAppWidget(id, views)
        }

        private suspend fun press(context: Context, id: Int, action: String) {
            val svc = AppServices.get(context)
            val uid = WidgetPrefs.uid(context, id) ?: return
            val remotes = svc.store.load()
            val remote = remotes.firstOrNull { it.uid == uid } ?: return render(context, AppWidgetManager.getInstance(context), id)

            val result = if (remote.type == DeviceCategory.AC.id) {
                pressAc(context, remote, remotes, action)
            } else {
                val r = svc.devices.remote(remote.deviceId) ?: return toast(context, "Remote data missing")
                val button = when {
                    action == POWER -> listOf("power", "power_off", "power_on").firstNotNullOfOrNull { r.button(it) }
                    action.startsWith(KEY) -> r.button(action.removePrefix(KEY))
                    action.startsWith(EXTRA) -> r.buttons.firstOrNull { it.label == action.removePrefix(EXTRA) }
                    else -> null
                } ?: return toast(context, "This remote has no such button")
                val signal = button.signal() ?: return toast(context, "Can't send \"${button.label}\"")
                svc.ir.send(signal)
            }
            result.onFailure { toast(context, it.message ?: "IR transmit failed") }
        }

        private suspend fun pressAc(context: Context, remote: SavedRemote, all: List<SavedRemote>, action: String): Result<Unit> {
            val svc = AppServices.get(context)
            val model = runCatching { svc.catalog.model(remote.deviceId) }.getOrNull()
                ?: return Result.failure(IllegalStateException("Remote data missing"))
            val cur = model.normalise(remote.state ?: model.defaultState())
            val temps = model.capabilities(cur.mode).temps
            val i = temps.indexOf(cur.temp)
            val (next, acAction) = when (action) {
                POWER -> cur.copy(power = !cur.power) to (if (cur.power) AcAction.POWER_OFF else AcAction.POWER_ON)
                TEMP_DOWN, TEMP_UP -> {
                    if (!cur.power) return Result.failure(IllegalStateException("Turn the AC on first"))
                    val j = if (action == TEMP_UP) i + 1 else i - 1
                    if (i < 0 || j !in temps.indices) return Result.success(Unit)
                    cur.copy(temp = temps[j]) to (if (action == TEMP_UP) AcAction.TEMP_UP else AcAction.TEMP_DOWN)
                }
                else -> return Result.success(Unit)
            }
            val signals = model.signals(next, acAction)
            if (signals.isEmpty()) return Result.failure(IllegalStateException("This remote has no code for that setting"))
            // Save first so the widget and the app show the new state even while the IR frame is sent.
            svc.store.save(all.map { if (it.uid == remote.uid) it.copy(state = next) else it })
            refreshAll(context)
            return svc.ir.send(*signals.toTypedArray())
        }

        /** The three most useful buttons after power, laid out as − / middle / + where possible. */
        internal fun pickButtons(r: DeviceRemote): List<RemoteButton> {
            val templates = listOf(
                listOf("vol_down", "mute", "vol_up"), listOf("vol_down", "input", "vol_up"),
                listOf("vol_down", "ok", "vol_up"), listOf("ch_down", "mute", "ch_up"),
                listOf("speed_down", "speed", "speed_up"), listOf("speed", "oscillate", "timer"),
                listOf("bright_down", "power_on", "bright_up"), listOf("bright_down", "light", "bright_up"),
                listOf("rew", "play_pause", "ff"), listOf("rew", "play", "ff"), listOf("prev", "play_pause", "next"),
            )
            val best = templates.maxBy { t -> t.count { r.has(it) } }
            val picked = best.mapNotNull { r.button(it) }.toMutableList()
            val fallback = listOf("mute", "speed", "oscillate", "mode", "timer", "light", "input", "ok", "play", "pause",
                "stop", "menu", "home", "back", "ch_up", "ch_down", "vol_up", "vol_down", "power_on", "power_off")
            for (k in fallback) {
                if (picked.size >= 3) break
                r.button(k)?.takeIf { it !in picked }?.let { picked += it }
            }
            for (b in r.extras) {
                if (picked.size >= 3) break
                picked += b
            }
            // keep − … + order when only some of a template matched
            return picked.take(3).sortedBy { b -> best.indexOf(b.key).let { if (it < 0) 1 else it } }
        }

        private fun shortLabel(b: RemoteButton): String = when (b.key) {
            "vol_up" -> "VOL +"; "vol_down" -> "VOL −"; "ch_up" -> "CH +"; "ch_down" -> "CH −"
            "speed_up" -> "Speed +"; "speed_down" -> "Speed −"; "bright_up" -> "Bright +"; "bright_down" -> "Bright −"
            "oscillate" -> "Swing"; "play_pause" -> "Play"; "rew" -> "«"; "ff" -> "»"; "prev" -> "⏮"; "next" -> "⏭"
            "power_on" -> "On"; "power_off" -> "Off"; "ok" -> "OK"
            else -> b.label.take(9)
        }

        private fun fmt(t: Double) = if (t % 1.0 == 0.0) t.toInt().toString() else "%.1f".format(t)

        private fun toast(context: Context, text: String) {
            Handler(Looper.getMainLooper()).post { Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show() }
        }

        private fun pressIntent(context: Context, id: Int, slot: Int, action: String): PendingIntent {
            val intent = Intent(context, RemoteWidget::class.java).setAction(ACTION_PRESS)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra(EXTRA_ACTION, action)
            return PendingIntent.getBroadcast(context, id * 8 + slot, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun openAppIntent(context: Context, uid: String): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN_UID, uid)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            return PendingIntent.getActivity(context, uid.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun configIntent(context: Context, id: Int): PendingIntent {
            val intent = Intent(context, WidgetConfigActivity::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            return PendingIntent.getActivity(context, id * 8 + 7, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}

/** widget id → saved remote uid. */
internal object WidgetPrefs {
    private fun prefs(context: Context) = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)
    fun uid(context: Context, id: Int): String? = prefs(context).getString("w$id", null)
    fun set(context: Context, id: Int, uid: String) = prefs(context).edit().putString("w$id", uid).apply()
    fun clear(context: Context, id: Int) = prefs(context).edit().remove("w$id").apply()
}
