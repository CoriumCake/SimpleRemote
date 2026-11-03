package com.simpleremote.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simpleremote.data.AcAction
import com.simpleremote.data.AcCatalog
import com.simpleremote.data.AcModel
import com.simpleremote.data.AcState
import com.simpleremote.data.AppServices
import com.simpleremote.data.Stats
import com.simpleremote.widget.RemoteWidget
import com.simpleremote.data.Brand
import com.simpleremote.data.DeviceBrand
import com.simpleremote.data.DeviceCatalog
import com.simpleremote.data.DeviceCategory
import com.simpleremote.data.DeviceEntry
import com.simpleremote.data.RemoteButton
import com.simpleremote.data.SavedRemote
import com.simpleremote.data.SavedRemotesStore
import com.simpleremote.data.defaultState
import com.simpleremote.data.normalise
import com.simpleremote.ir.IrSignal
import com.simpleremote.ir.IrTransmitter
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface Screen {
    data object Home : Screen
    /** Add flow, step 1: what kind of device. */
    data object Categories : Screen
    /** AC brands (stateful AC databases). */
    data object Brands : Screen
    /** Step through every code set of a brand until the AC responds. */
    data class TestCodes(val brand: Brand, val index: Int = 0) : Screen
    data class AcRemote(val uid: String) : Screen
    /** Button-remote devices: brand list → test codes → remote. */
    data class DeviceBrands(val category: DeviceCategory) : Screen
    data class DeviceTest(val category: DeviceCategory, val brand: DeviceBrand, val step: Int = 0) : Screen
    data class DeviceRemote(val uid: String) : Screen
    data object About : Screen
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val svc = AppServices.get(app)
    val catalog: AcCatalog = svc.catalog
    val devices: DeviceCatalog = svc.devices
    private val store: SavedRemotesStore = svc.store
    private val ir: IrTransmitter = svc.ir

    val hasIrEmitter = ir.hasEmitter
    val remotes = mutableStateListOf<SavedRemote>().apply { addAll(store.load()) }

    private val backStack = mutableStateListOf<Screen>(Screen.Home)
    val screen: Screen get() = backStack.last()
    val canGoBack: Boolean get() = backStack.size > 1

    var brands by mutableStateOf<List<Brand>>(emptyList()); private set
    /** Number of remotes per device category id (AC = AC code sets). */
    var categoryCounts by mutableStateOf<Map<String, Int>>(emptyMap()); private set
    var message by mutableStateOf<String?>(null)
    /** Anonymous code statistics: null = not asked yet. */
    var statsConsent by mutableStateOf(Stats.consent(app)); private set

    init {
        Stats.flush(app)
        viewModelScope.launch {
            brands = runCatching { catalog.brands() }.getOrDefault(emptyList())
            val counts = runCatching { devices.counts() }.getOrDefault(emptyMap()).toMutableMap()
            counts[DeviceCategory.AC.id] = brands.sumOf { it.entries.size }
            categoryCounts = counts
        }
    }

    /** Re-reads saved remotes (the widget may have changed an AC's state while the app was in the background). */
    fun reload() {
        val fresh = store.load()
        if (fresh != remotes.toList()) { remotes.clear(); remotes.addAll(fresh) }
    }

    /** Opens a saved remote, e.g. from the widget. */
    fun openUid(uid: String) {
        reload()
        val r = remotes.firstOrNull { it.uid == uid } ?: return
        resetTo(Screen.Home)
        open(r)
    }

    fun setStatsConsent(allowed: Boolean) {
        Stats.setConsent(getApplication(), allowed)
        statsConsent = allowed
    }

    /** Result of testing one code during setup ("Yes, it works" / "No"). */
    fun reportTest(cat: String, brand: String, code: String, src: String, ok: Boolean, step: Int, mixed: Boolean) =
        Stats.record(getApplication(), Stats.Event("setup", cat, brand, code, src, ok = ok, step = step, mixed = mixed))

    fun navigate(s: Screen) { backStack += s }
    fun replace(s: Screen) { backStack[backStack.lastIndex] = s }
    fun back() { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
    private fun resetTo(vararg s: Screen) { backStack.clear(); backStack.addAll(s) }

    /** Scan every distinct protocol, skipping the code sets of [after] (already tried). */
    fun tryAllCodes(after: Brand? = null) {
        viewModelScope.launch {
            val all = runCatching { catalog.allProtocols(after?.entries?.map { it.id }.orEmpty()) }.getOrNull() ?: return@launch
            if (after != null) replace(Screen.TestCodes(all)) else navigate(Screen.TestCodes(all))
        }
    }

    // ---- IR ----

    fun send(signals: List<IrSignal>) {
        if (signals.isEmpty()) { message = "This remote has no code for that setting"; return }
        viewModelScope.launch {
            ir.send(*signals.toTypedArray()).onFailure { message = it.message ?: "IR transmit failed" }
        }
    }

    fun sendState(model: AcModel, state: AcState, action: AcAction) = send(model.signals(state, action))

    fun press(button: RemoteButton) {
        val sig = button.signal()
        if (sig == null) message = "Can't send \"${button.label}\"" else send(listOf(sig))
    }

    /** Opens the add flow for a category: AC has its own brand list. */
    fun openCategory(c: DeviceCategory) = navigate(if (c == DeviceCategory.AC) Screen.Brands else Screen.DeviceBrands(c))

    fun open(r: SavedRemote) = navigate(if (r.type == DeviceCategory.AC.id) Screen.AcRemote(r.uid) else Screen.DeviceRemote(r.uid))

    fun addDevice(entry: DeviceEntry, name: String) {
        val r = SavedRemote(uid = UUID.randomUUID().toString(), type = entry.cat, deviceId = entry.id,
            name = name.ifBlank { entry.brand }, created = System.currentTimeMillis(),
            brand = entry.brand, src = entry.src)
        remotes += r
        persist()
        resetTo(Screen.Home, Screen.DeviceRemote(r.uid))
    }

    /** Power toggle straight from the home grid. */
    fun quickPower(r: SavedRemote) {
        if (r.type != DeviceCategory.AC.id) {
            viewModelScope.launch {
                val remote = devices.remote(r.deviceId) ?: run { message = "Remote data missing"; return@launch }
                val b = listOf("power", "power_off", "power_on").firstNotNullOfOrNull { remote.button(it) }
                if (b == null) message = "This remote has no power button" else press(b)
            }
            return
        }
        viewModelScope.launch {
            val model = runCatching { catalog.model(r.deviceId) }.getOrNull() ?: run { message = "Remote data missing"; return@launch }
            val cur = model.normalise(r.state ?: model.defaultState())
            val next = cur.copy(power = !cur.power)
            updateState(r.uid, next)
            sendState(model, next, if (next.power) AcAction.POWER_ON else AcAction.POWER_OFF)
        }
    }

    // ---- saved remotes ----

    fun addRemote(model: AcModel, name: String) {
        val r = SavedRemote(
            uid = UUID.randomUUID().toString(), deviceId = model.id,
            name = name.ifBlank { model.manufacturer }, state = model.defaultState(),
            created = System.currentTimeMillis(), brand = model.manufacturer,
            src = catalog.entrySource(model.id),
        )
        remotes += r
        persist()
        resetTo(Screen.Home, Screen.AcRemote(r.uid))
    }

    fun updateState(uid: String, state: AcState) {
        val i = remotes.indexOfFirst { it.uid == uid }
        if (i >= 0) { remotes[i] = remotes[i].copy(state = state); persist() }
    }

    fun rename(uid: String, name: String) {
        val i = remotes.indexOfFirst { it.uid == uid }
        if (i >= 0 && name.isNotBlank()) { remotes[i] = remotes[i].copy(name = name.trim()); persist() }
    }

    fun delete(uid: String) {
        remotes.firstOrNull { it.uid == uid }?.let { r ->
            // A remote deleted soon after setup hints that its code did not really work.
            val days = if (r.created > 0) ((System.currentTimeMillis() - r.created) / 86_400_000L).toInt() else null
            Stats.record(getApplication(), Stats.Event("deleted", r.type, r.brand, r.deviceId, r.src, days = days))
        }
        remotes.removeAll { it.uid == uid }
        persist()
        resetTo(Screen.Home)
    }

    private fun persist() {
        store.save(remotes.toList())
        RemoteWidget.refreshAll(getApplication())
    }
}
