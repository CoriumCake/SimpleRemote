package com.simpleremote.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simpleremote.data.AcAction
import com.simpleremote.data.AcModel
import com.simpleremote.data.AcState
import com.simpleremote.data.IrextAc
import com.simpleremote.data.defaultState
import com.simpleremote.data.normalise
import com.simpleremote.data.tempKey

@Composable
fun AcRemoteScreen(vm: AppViewModel, s: Screen.AcRemote) {
    val remote = vm.remotes.firstOrNull { it.uid == s.uid } ?: return
    val model by produceState<AcModel?>(null, remote.deviceId) {
        value = runCatching { vm.catalog.model(remote.deviceId) }.getOrNull()
    }
    var showEdit by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(remote.name, onBack = vm::back) {
            IconButton(onClick = { showEdit = true }) { Icon(Icons.Rounded.Edit, "Edit remote") }
        }
        val d = model
        if (d == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            AcControls(vm, remote.uid, d, d.normalise(remote.state ?: d.defaultState()))
        }
    }

    if (showEdit) EditRemoteDialog(
        initialName = remote.name,
        onDismiss = { showEdit = false },
        onRename = { vm.rename(remote.uid, it); showEdit = false },
        onDelete = { showEdit = false; vm.delete(remote.uid) },
    )
}

@Composable
private fun AcControls(vm: AppViewModel, uid: String, d: AcModel, state: AcState) {
    val caps = d.capabilities(state.mode)
    val haptic = LocalHapticFeedback.current

    /** AC remotes resend the full state on every press while the unit is on. */
    fun apply(new: AcState, action: AcAction) {
        val n = d.normalise(new)
        vm.updateState(uid, n)
        if (n.power || action == AcAction.POWER_OFF) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            vm.sendState(d, n, action)
        }
    }
    fun <T> List<T>.after(cur: T?): T = this[(indexOf(cur) + 1).mod(size)]

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (!vm.hasIrEmitter) NoIrBanner()
        DisplayPanel(d, state, caps.temps.isNotEmpty())

        // power + temperature rocker
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PowerButton(state.power) {
                apply(state.copy(power = !state.power), if (state.power) AcAction.POWER_OFF else AcAction.POWER_ON)
            }
            Spacer(Modifier.weight(1f))
            val i = caps.temps.indexOf(state.temp)
            val canTemp = state.power && caps.temps.size > 1
            TempRocker(
                canDown = canTemp && i > 0,
                canUp = canTemp && i in 0 until caps.temps.lastIndex,
                onDown = { apply(state.copy(temp = caps.temps[i - 1]), AcAction.TEMP_DOWN) },
                onUp = { apply(state.copy(temp = caps.temps[i + 1]), AcAction.TEMP_UP) },
            )
        }

        // function keys — only what this remote supports
        val keys = buildList<@Composable () -> Unit> {
            if (d.modes.size > 1) add {
                FunctionKey(modeStyle(state.mode).icon, "Mode", modeStyle(state.mode).label, state.power) {
                    apply(state.copy(mode = d.modes.after(state.mode)), AcAction.MODE)
                }
            }
            if (caps.fans.size > 1) add {
                FunctionKey(Icons.Rounded.Air, "Fan", pretty(state.fan ?: "auto"), state.power) {
                    apply(state.copy(fan = caps.fans.after(state.fan)), AcAction.FAN)
                }
            }
            if (caps.swings.size > 1) add {
                FunctionKey(Icons.Rounded.SwapVert, "Swing", pretty(state.swing ?: ""), state.power) {
                    apply(state.copy(swing = caps.swings.after(state.swing)), AcAction.SWING)
                }
            }
            d.extraKeys.forEach { key ->
                add {
                    FunctionKey(Icons.Rounded.AutoAwesome, pretty(key), null, true) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.send(listOfNotNull(d.extraSignal(key)))
                    }
                }
            }
        }
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { k -> Box(Modifier.weight(1f)) { k() } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        Text(
            "${d.manufacturer} · ${if (d is IrextAc) "IRext" else "SmartIR"} code set",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 24.dp),
        )
    }
}

@Composable
private fun DisplayPanel(d: AcModel, state: AcState, hasTemp: Boolean) {
    val style = modeStyle(state.mode)
    val off = MaterialTheme.colorScheme.surfaceContainerHigh
    val from by animateColorAsState(if (state.power) style.from else off, label = "from")
    val to by animateColorAsState(if (state.power) style.to else off, label = "to")
    val fg = if (state.power) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val unit = if (d.fahrenheit) "°F" else "°C"

    Box(
        Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(32.dp)).background(Brush.linearGradient(listOf(from, to))).padding(22.dp),
    ) {
        Row(Modifier.align(Alignment.TopStart), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(50), color = fg.copy(alpha = .18f)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(style.icon, null, Modifier.size(18.dp), tint = fg)
                    Spacer(Modifier.width(6.dp))
                    Text(style.label, color = fg, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Text(
            if (state.power) "ON" else "OFF",
            Modifier.align(Alignment.TopEnd), color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
        )
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.Top) {
            Text(
                if (hasTemp) tempKey(state.temp) else "--",
                color = fg, fontSize = 88.sp, fontWeight = FontWeight.Light, lineHeight = 88.sp,
                modifier = Modifier.alpha(if (state.power) 1f else .7f),
            )
            if (hasTemp) Text(unit, color = fg, fontSize = 24.sp, modifier = Modifier.padding(top = 14.dp))
        }
        Row(Modifier.align(Alignment.BottomStart), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            state.fan?.let { PanelInfo(Icons.Rounded.Air, "Fan ${pretty(it)}", fg) }
            state.swing?.let { PanelInfo(Icons.Rounded.SwapVert, pretty(it), fg) }
        }
    }
}

@Composable
private fun PanelInfo(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = color)
        Spacer(Modifier.width(4.dp))
        Text(text, color = color, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PowerButton(on: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (on) 1f else .96f, label = "scale")
    Button(
        onClick = onClick,
        shape = CircleShape,
        modifier = Modifier.size(92.dp).scale(scale),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (on) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        ),
    ) { Icon(Icons.Rounded.PowerSettingsNew, if (on) "Turn off" else "Turn on", Modifier.size(42.dp)) }
}

@Composable
private fun TempRocker(canDown: Boolean, canUp: Boolean, onDown: () -> Unit, onUp: () -> Unit) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            RockerHalf(Icons.Rounded.Remove, "Temperature down", canDown, onDown)
            Text("TEMP", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp))
            RockerHalf(Icons.Rounded.Add, "Temperature up", canUp, onUp)
        }
    }
}

@Composable
private fun RockerHalf(icon: ImageVector, desc: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, enabled = enabled, shape = CircleShape,
        color = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.size(68.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon, desc, Modifier.size(30.dp),
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun FunctionKey(icon: ImageVector, label: String, value: String?, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth().aspectRatio(1f).alpha(if (enabled) 1f else .45f),
    ) {
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (value != null) Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
internal fun EditRemoteDialog(initialName: String, onDismiss: () -> Unit, onRename: (String) -> Unit, onDelete: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (confirmDelete) "Delete remote?" else "Edit remote") },
        text = {
            if (confirmDelete) Text("\"$initialName\" will be removed from your remotes.")
            else OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
        },
        confirmButton = {
            if (confirmDelete) Button(onClick = onDelete, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
            else Button(onClick = { onRename(name) }) { Text("Save") }
        },
        dismissButton = {
            if (confirmDelete) TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            else TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
    )
}
