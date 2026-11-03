package com.simpleremote.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Input
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.simpleremote.data.DeviceBrand
import com.simpleremote.data.DeviceCategory
import com.simpleremote.data.DeviceRemote
import com.simpleremote.data.RemoteButton

// ---------------------------------------------------------------- add flow, step 1

@Composable
fun CategoriesScreen(vm: AppViewModel) {
    val counts = vm.categoryCounts
    val cats = DeviceCategory.entries.filter { it == DeviceCategory.AC || (counts[it.id] ?: 0) > 0 }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Add a remote", onBack = vm::back)
        Text(
            "What do you want to control?",
            Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(cats, key = { it.id }) { c ->
                val st = categoryStyle(c)
                Card(
                    onClick = { vm.openCategory(c) },
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.92f),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            Modifier.size(52.dp).clip(CircleShape).background(Brush.linearGradient(listOf(st.from, st.to))),
                            contentAlignment = Alignment.Center,
                        ) { Icon(st.icon, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
                        Spacer(Modifier.height(10.dp))
                        Text(c.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        counts[c.id]?.let {
                            Text("$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item(span = { GridItemSpan(3) }) { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ---------------------------------------------------------------- step 2: brand

private val POPULAR_DEVICES = mapOf(
    DeviceCategory.TV to listOf("Samsung", "LG", "Sony", "TCL", "Hisense", "Panasonic", "Philips", "Sharp", "Toshiba", "Xiaomi", "Vizio", "Haier", "Skyworth", "Changhong"),
    DeviceCategory.PROJECTOR to listOf("Epson", "BenQ", "Optoma", "ViewSonic", "Acer", "Sony", "NEC", "Panasonic", "LG", "XGIMI", "Xiaomi"),
    DeviceCategory.FAN to listOf("Dyson", "Hatari", "Xiaomi", "Midea", "Honeywell", "Lasko", "Vornado", "Rowenta", "Panasonic", "Mitsubishi"),
    DeviceCategory.AUDIO to listOf("Sony", "Samsung", "LG", "Bose", "Yamaha", "Denon", "JBL", "Onkyo", "Pioneer", "Philips", "Marantz", "Vizio"),
    DeviceCategory.STB to listOf("Samsung", "Humax", "Motorola", "Arris", "Technicolor", "Huawei", "ZTE", "Skyworth"),
    DeviceCategory.STREAMING to listOf("Apple", "Roku", "Amazon", "Xiaomi", "Nvidia", "Google"),
    DeviceCategory.DISC to listOf("Sony", "Samsung", "LG", "Panasonic", "Philips", "Pioneer", "Toshiba"),
)

@Composable
fun DeviceBrandsScreen(vm: AppViewModel, s: Screen.DeviceBrands) {
    val brands by produceState<List<DeviceBrand>>(emptyList(), s.category) {
        value = runCatching { vm.devices.brands(s.category) }.getOrDefault(emptyList())
    }
    val items = remember(brands) { brands.map { BrandItem(it.name, it.entries.size) } }
    val byKey = remember(brands) { brands.associateBy { it.key } }
    BrandPicker(
        title = "Choose ${s.category.label.lowercase()} brand".replace("tv brand", "TV brand"),
        items = items,
        popularNames = POPULAR_DEVICES[s.category].orEmpty(),
        summary = "${brands.sumOf { it.entries.size }} remotes in total",
        onPick = { item -> byKey[item.key]?.let { vm.navigate(Screen.DeviceTest(s.category, it)) } },
        onBack = vm::back,
    )
}

// ---------------------------------------------------------------- step 3: test

/** Mi-Remote-style setup: send one button of each distinct code, ask whether the device responded. */
@Composable
fun DeviceTestScreen(vm: AppViewModel, s: Screen.DeviceTest) {
    val steps = s.brand.steps
    val step = s.step.coerceIn(steps.indices)
    val group = steps[step]
    val entry = group.first()
    val remote by produceState<DeviceRemote?>(null, entry.id) { value = vm.devices.remote(entry.id) }
    var tested by remember(entry.id) { mutableStateOf(false) }
    var naming by rememberSaveable { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val st = categoryStyle(s.category)

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Set up ${s.brand.name}", onBack = vm::back)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!vm.hasIrEmitter) NoIrBanner()
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Code ${step + 1} of ${steps.size}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    if (group.size > 1) "${group.size} models" else entry.model.take(28),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (step + 1f) / steps.size },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            )
            Spacer(Modifier.height(36.dp))
            Box(
                Modifier.size(150.dp).clip(CircleShape).background(Brush.linearGradient(listOf(st.from.copy(alpha = .15f), st.to.copy(alpha = .15f)))),
                contentAlignment = Alignment.Center,
            ) { Icon(st.icon, null, Modifier.size(72.dp), tint = st.from) }
            Spacer(Modifier.height(24.dp))
            Text("Point your phone at the ${s.category.label.lowercase().replace("tv", "TV")}",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            val testButton = remote?.testButton
            Text(
                "Tap the button and see if the device responds.",
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = {
                    val b = testButton ?: return@Button
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.press(b)
                    tested = true
                },
                enabled = testButton != null,
                shape = CircleShape,
                modifier = Modifier.size(104.dp),
                contentPadding = PaddingValues(0.dp),
            ) { Icon(iconFor(testButton?.key ?: "power") ?: Icons.Rounded.PowerSettingsNew, testButton?.label, Modifier.size(48.dp)) }
            Spacer(Modifier.height(8.dp))
            Text(testButton?.label ?: "…", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // A few other buttons to try when power is not convenient (e.g. device already on).
            remote?.let { r ->
                val others = listOf("mute", "vol_up", "ch_up", "power_off", "power_on", "speed", "light", "ok")
                    .mapNotNull { r.button(it) }.filter { it != testButton }.take(3)
                if (others.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    others.forEach { b -> TextButton(onClick = { vm.press(b); tested = true }) { Text(b.label) } }
                }
            }

            Spacer(Modifier.height(16.dp))
            AnimatedVisibility(tested, enter = fadeIn() + slideInVertically { it / 3 }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Did it respond?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                if (step < steps.lastIndex) vm.replace(s.copy(step = step + 1))
                                else vm.message = "That was the last code for ${s.brand.name}"
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                        ) { Text(if (step < steps.lastIndex) "No, try next" else "No") }
                        Button(onClick = { naming = true }, modifier = Modifier.weight(1f).height(56.dp)) { Text("Yes, it works") }
                    }
                }
            }
            if (step > 0) TextButton(onClick = { vm.replace(s.copy(step = step - 1)) }) { Text("Back to previous code") }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (naming) {
        var name by rememberSaveable { mutableStateOf("${s.brand.name} ${s.category.label}") }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text("Name this remote") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("e.g. Living room TV") }) },
            confirmButton = { Button(onClick = { vm.addDevice(entry, name); naming = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("Cancel") } },
        )
    }
}

// ---------------------------------------------------------------- the remote

@Composable
fun DeviceRemoteScreen(vm: AppViewModel, s: Screen.DeviceRemote) {
    val saved = vm.remotes.firstOrNull { it.uid == s.uid } ?: return
    val remote by produceState<DeviceRemote?>(null, saved.deviceId) { value = vm.devices.remote(saved.deviceId) }
    var showEdit by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(saved.name, onBack = vm::back) {
            IconButton(onClick = { showEdit = true }) { Icon(Icons.Rounded.Edit, "Edit remote") }
        }
        val r = remote
        if (r == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else ButtonRemote(r, DeviceCategory.of(saved.type), onPress = vm::press)
    }
    if (showEdit) EditRemoteDialog(
        initialName = saved.name,
        onDismiss = { showEdit = false },
        onRename = { vm.rename(saved.uid, it); showEdit = false },
        onDelete = { showEdit = false; vm.delete(saved.uid) },
    )
}

private fun iconFor(key: String): ImageVector? = when (key) {
    "power", "power_on", "power_off" -> Icons.Rounded.PowerSettingsNew
    "mute" -> Icons.AutoMirrored.Rounded.VolumeOff
    "home" -> Icons.Rounded.Home
    "menu" -> Icons.Rounded.Menu
    "back" -> Icons.AutoMirrored.Rounded.ArrowBack
    "input" -> Icons.Rounded.Input
    "info" -> Icons.Rounded.Info
    "play", "play_pause" -> Icons.Rounded.PlayArrow
    "pause" -> Icons.Rounded.Pause
    "stop" -> Icons.Rounded.Stop
    "ff" -> Icons.Rounded.FastForward
    "rew" -> Icons.Rounded.FastRewind
    "next" -> Icons.Rounded.SkipNext
    "prev" -> Icons.Rounded.SkipPrevious
    "record" -> Icons.Rounded.FiberManualRecord
    "up" -> Icons.Rounded.KeyboardArrowUp
    "down" -> Icons.Rounded.KeyboardArrowDown
    "left" -> Icons.Rounded.KeyboardArrowLeft
    "right" -> Icons.Rounded.KeyboardArrowRight
    "vol_up", "ch_up", "speed_up", "temp_up", "bright_up" -> Icons.Rounded.Add
    "vol_down", "ch_down", "speed_down", "temp_down", "bright_down" -> Icons.Rounded.Remove
    else -> null
}

/** Remote layout built from the buttons this remote actually has. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ButtonRemote(r: DeviceRemote, category: DeviceCategory, onPress: (RemoteButton) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val press: (RemoteButton) -> Unit = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onPress(it) }
    val st = categoryStyle(category)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        // Power row
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoundKey(r.button("input"), press, Modifier.weight(1f))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val power = r.button("power") ?: r.button("power_on") ?: r.button("power_off")
                if (power != null) Surface(
                    onClick = { press(power) }, shape = CircleShape,
                    color = Color.Transparent, modifier = Modifier.size(76.dp),
                ) {
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0xFFF97316)))), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.PowerSettingsNew, power.label, tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                }
            }
            RoundKey(r.button("mute"), press, Modifier.weight(1f))
        }
        if (r.has("power") && (r.has("power_on") || r.has("power_off")) || (!r.has("power") && r.has("power_on") && r.has("power_off"))) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOfNotNull(r.button("power_on"), r.button("power_off")).forEach { PillKey(it, press) }
            }
        }

        // Function tiles for appliances (fan, heater, light, purifier…)
        val functions = listOf("speed", "oscillate", "mode", "timer", "light").mapNotNull { r.button(it) }
        if (functions.isNotEmpty()) FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) { functions.forEach { FunctionTile(it, st, press) } }

        // Rockers
        val rockers = listOf(
            Triple("VOL", "vol_up", "vol_down"), Triple("CH", "ch_up", "ch_down"), Triple("SPEED", "speed_up", "speed_down"),
            Triple("TEMP", "temp_up", "temp_down"), Triple("LIGHT", "bright_up", "bright_down"),
        ).filter { (_, u, d) -> r.has(u) || r.has(d) }
        if (rockers.isNotEmpty()) Row(
            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
        ) {
            rockers.forEach { (label, u, d) -> Rocker(label, r.button(u), r.button(d), press) }
            val middle = listOf("home", "menu", "back").mapNotNull { r.button(it) }
            if (rockers.size < 3 && middle.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                middle.forEach { RoundKey(it, press) }
            }
        }

        // D-pad
        if (listOf("up", "down", "left", "right", "ok").count { r.has(it) } >= 3) {
            DPad(r, press)
            val nav = listOf("back", "home", "menu", "exit", "info", "guide").filter { rockers.size >= 3 || it !in setOf("home", "menu", "back") }
                .mapNotNull { r.button(it) }
            if (nav.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
                nav.forEach { RoundKey(it, press) }
            }
        } else {
            val nav = listOf("ok", "back", "home", "menu", "exit", "info", "guide", "up", "down", "left", "right")
                .filter { rockers.isEmpty() || it !in setOf("home", "menu", "back") }.mapNotNull { r.button(it) }
            if (nav.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
                nav.forEach { RoundKey(it, press) }
            }
        }

        // Media
        val media = listOf("prev", "rew", "play", "play_pause", "pause", "stop", "ff", "next", "record").mapNotNull { r.button(it) }
        if (media.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
            media.forEach { RoundKey(it, press, size = 52) }
        }

        // Number pad
        val digits = (1..9).map { "$it" } + listOf("0")
        if (digits.count { r.has(it) } >= 6) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            digits.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    if (row.size == 1) Spacer(Modifier.size(64.dp))
                    row.forEach { d -> RoundKey(r.button(d), press, size = 64, text = d) }
                    if (row.size == 1) Spacer(Modifier.size(64.dp))
                }
            }
        }

        // Everything else
        val shown = buildSet {
            addAll(listOf("power", "input", "mute", "speed", "oscillate", "mode", "timer", "light"))
            rockers.forEach { (_, u, d) -> add(u); add(d) }
            addAll(listOf("up", "down", "left", "right", "ok", "back", "home", "menu", "exit", "info", "guide"))
            addAll(listOf("prev", "rew", "play", "play_pause", "pause", "stop", "ff", "next", "record"))
            if (digits.count { r.has(it) } >= 6) addAll(digits)
            if (r.has("power") && (r.has("power_on") || r.has("power_off")) || (!r.has("power") && r.has("power_on") && r.has("power_off"))) {
                add("power_on"); add("power_off")
            }
            if (!r.has("power")) { add(if (r.has("power_on")) "power_on" else "power_off") }
        }
        val rest = r.buttons.filter { it.key.isEmpty() || it.key !in shown }
        if (rest.isNotEmpty()) {
            Text("More buttons", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth())
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { rest.forEach { PillKey(it, press) } }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun RoundKey(b: RemoteButton?, press: (RemoteButton) -> Unit, modifier: Modifier = Modifier, size: Int = 56, text: String? = null) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (b == null) { Spacer(Modifier.size(size.dp)); return@Box }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                onClick = { press(b) }, shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.size(size.dp),
                shadowElevation = 1.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val icon = if (text == null) iconFor(b.key) else null
                    if (icon != null) Icon(icon, b.label)
                    else Text(text ?: b.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(4.dp))
                }
            }
            if (text == null && iconFor(b.key) != null && size >= 56) {
                Text(b.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width((size + 16).dp), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun PillKey(b: RemoteButton, press: (RemoteButton) -> Unit) {
    Surface(onClick = { press(b) }, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerLowest, shadowElevation = 1.dp) {
        Text(b.label, Modifier.padding(horizontal = 16.dp, vertical = 10.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun FunctionTile(b: RemoteButton, st: ModeStyle, press: (RemoteButton) -> Unit) {
    Surface(onClick = { press(b) }, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 1.dp, modifier = Modifier.size(width = 100.dp, height = 72.dp)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(st.from))
            Spacer(Modifier.height(6.dp))
            Text(b.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Rocker(label: String, up: RemoteButton?, down: RemoteButton?, press: (RemoteButton) -> Unit) {
    Surface(shape = RoundedCornerShape(36.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest, shadowElevation = 1.dp,
        modifier = Modifier.width(72.dp).height(164.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = { up?.let(press) }, enabled = up != null, modifier = Modifier.size(72.dp)) { Icon(Icons.Rounded.Add, up?.label) }
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = { down?.let(press) }, enabled = down != null, modifier = Modifier.size(72.dp)) { Icon(Icons.Rounded.Remove, down?.label) }
        }
    }
}

@Composable
private fun DPad(r: DeviceRemote, press: (RemoteButton) -> Unit) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLowest, shadowElevation = 1.dp, modifier = Modifier.size(208.dp)) {
        Box(Modifier.fillMaxSize()) {
            @Composable
            fun arrow(key: String, align: Alignment) {
                val b = r.button(key)
                IconButton(onClick = { b?.let(press) }, enabled = b != null, modifier = Modifier.align(align).size(68.dp)) {
                    Icon(iconFor(key)!!, b?.label, Modifier.size(36.dp))
                }
            }
            arrow("up", Alignment.TopCenter); arrow("down", Alignment.BottomCenter)
            arrow("left", Alignment.CenterStart); arrow("right", Alignment.CenterEnd)
            val ok = r.button("ok")
            Surface(
                onClick = { ok?.let(press) }, enabled = ok != null, shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.align(Alignment.Center).size(76.dp),
            ) { Box(contentAlignment = Alignment.Center) { Text("OK", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer) } }
        }
    }
}
