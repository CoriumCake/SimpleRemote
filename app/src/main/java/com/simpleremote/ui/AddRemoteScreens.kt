package com.simpleremote.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simpleremote.data.AcAction
import com.simpleremote.data.AcCatalog
import com.simpleremote.data.AcModel
import com.simpleremote.data.Brand
import com.simpleremote.data.brandKey
import com.simpleremote.data.defaultState
import kotlinx.coroutines.launch

private val POPULAR = listOf(
    "Daikin", "Mitsubishi Electric", "Panasonic", "LG", "Samsung", "Gree", "Midea", "Haier", "Carrier",
    "Saijo Denki", "Sharp", "Toshiba", "Hitachi", "Fujitsu", "TCL", "Hisense", "Electrolux", "York",
)

private fun sectionOf(name: String): Char = name.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: '#'

/** A brand row in [BrandPicker]. */
data class BrandItem(val name: String, val count: Int) {
    val key: String get() = brandKey(name)
}

@Composable
fun BrandsScreen(vm: AppViewModel) {
    val brands = vm.brands
    val items = remember(brands) { brands.map { BrandItem(it.name, it.entries.size) } }
    val byKey = remember(brands) { brands.associateBy { it.key } }
    val total = remember(brands) { brands.sumOf { it.entries.size } }
    BrandPicker(
        title = "Choose AC brand",
        items = items,
        popularNames = POPULAR,
        summary = "$total code sets in total",
        onPick = { item -> byKey[item.key]?.let { vm.navigate(Screen.TestCodes(it)) } },
        onBack = vm::back,
        extra = { TryAllCard("Can't find your AC, or no code works?", "Test one code per AC protocol, from all brands") { vm.tryAllCodes() } },
    )
}

/** Searchable A–Z brand list with popular chips, shared by every device category. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun BrandPicker(
    title: String,
    items: List<BrandItem>,
    popularNames: List<String>,
    summary: String,
    onPick: (BrandItem) -> Unit,
    onBack: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(query, items) {
        val q = brandKey(query)
        if (query.isBlank()) items
        else items.filter { it.key.contains(q) || it.name.contains(query.trim(), ignoreCase = true) }
            .sortedBy { if (it.key.startsWith(q)) 0 else 1 }
    }
    val popular = remember(items, popularNames) {
        val byKey = items.associateBy { it.key }
        popularNames.mapNotNull { byKey[brandKey(it)] }.ifEmpty { items.sortedByDescending { it.count }.take(12) }
    }
    val sections = remember(filtered) { filtered.groupBy { sectionOf(it.name) }.toSortedMap(compareBy { if (it == '#') '~' else it }) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Brands load asynchronously; keep the list anchored at the top (Popular) instead of a restored key.
    LaunchedEffect(query, items.size) { listState.scrollToItem(0) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title, onBack = onBack)
        TextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("Search ${items.size} brands") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Clear, "Clear") } },
            singleLine = true,
            shape = RoundedCornerShape(50),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )

        Row(Modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 32.dp)) {
                if (query.isBlank() && popular.isNotEmpty()) {
                    item(key = "popular") {
                        Column(Modifier.padding(16.dp)) {
                            Text("Popular", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(10.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                popular.forEach { b ->
                                    Surface(
                                        onClick = { onPick(b) },
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                    ) { Text(b.name, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium) }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (extra != null) { Spacer(Modifier.height(12.dp)); extra() }
                        }
                    }
                }
                if (filtered.isEmpty() && items.isNotEmpty()) {
                    item {
                        Column(Modifier.padding(16.dp)) {
                            Text("No brand matches \"$query\"", Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            extra?.invoke()
                        }
                    }
                }
                sections.forEach { (letter, list) ->
                    stickyHeader(key = "h$letter") {
                        Text(
                            letter.toString(),
                            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                        )
                    }
                    items(list, key = { "b" + it.key + it.name }) { b ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(b) }.padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(b.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Text("${b.count}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
            // A–Z quick index
            if (query.isBlank() && sections.size > 3) {
                Column(
                    Modifier.fillMaxHeight().width(28.dp).padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    sections.keys.forEach { letter ->
                        Text(
                            letter.toString(),
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                var index = if (query.isBlank() && popular.isNotEmpty()) 1 else 0
                                for ((l, list) in sections) { if (l == letter) break; index += 1 + list.size }
                                scope.launch { listState.scrollToItem(index) }
                            }.padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Mi-Remote-style guided setup: tap power, answer "did it respond?", move through code sets. */
@Composable
fun TestCodesScreen(vm: AppViewModel, s: Screen.TestCodes) {
    val entries = s.brand.entries
    val index = s.index.coerceIn(entries.indices)
    val entry = entries[index]
    val model by produceState<AcModel?>(null, entry.id) { value = runCatching { vm.catalog.model(entry.id) }.getOrNull() }
    val scanAll = s.brand.name == AcCatalog.ALL_BRANDS
    var tested by remember(entry.id) { mutableStateOf(false) }
    var naming by rememberSaveable { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val cool = modeStyle("cool")

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (scanAll) "Try all codes" else "Set up ${s.brand.name}", onBack = vm::back)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!vm.hasIrEmitter) NoIrBanner()
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Code set ${index + 1} of ${entries.size}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    if (scanAll) "${entry.manufacturer} · ${sourceLabel(entry.source)}" else sourceLabel(entry.source),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (index + 1f) / entries.size },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            )
            Spacer(Modifier.height(36.dp))

            Box(
                Modifier.size(150.dp).clip(CircleShape).background(Brush.linearGradient(listOf(cool.from.copy(alpha = .15f), cool.to.copy(alpha = .15f)))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.AcUnit, null, Modifier.size(72.dp), tint = cool.from) }
            Spacer(Modifier.height(24.dp))
            Text("Point your phone at the AC", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                "Tap the power button and see if the air conditioner beeps or turns on.",
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))

            Button(
                onClick = {
                    val m = model ?: return@Button
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.sendState(m, m.defaultState().copy(power = true), AcAction.POWER_ON)
                    tested = true
                },
                enabled = model != null,
                shape = CircleShape,
                modifier = Modifier.size(104.dp),
                contentPadding = PaddingValues(0.dp),
            ) { Icon(Icons.Rounded.PowerSettingsNew, "Test power", Modifier.size(48.dp)) }
            Spacer(Modifier.height(8.dp))
            Text(if (tested) "Tap again to resend" else "Tap to test", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row {
                TextButton(onClick = { model?.let { vm.sendState(it, it.defaultState().copy(power = false), AcAction.POWER_OFF); tested = true } }) {
                    Text("Send OFF instead")
                }
            }

            Spacer(Modifier.height(16.dp))
            AnimatedVisibility(tested, enter = fadeIn() + slideInVertically { it / 3 }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Did the AC respond?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                vm.reportTest("ac", entry.manufacturer, entry.id, entry.source, ok = false, step = index, mixed = scanAll)
                                when {
                                    index < entries.lastIndex -> vm.replace(s.copy(index = index + 1))
                                    !scanAll -> vm.tryAllCodes(after = s.brand)
                                    else -> vm.message = "That was the last code set"
                                }
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                        ) { Text(if (index < entries.lastIndex) "No, try next" else if (!scanAll) "No, try all brands" else "No") }
                        Button(onClick = {
                            vm.reportTest("ac", entry.manufacturer, entry.id, entry.source, ok = true, step = index, mixed = scanAll)
                            naming = true
                        }, modifier = Modifier.weight(1f).height(56.dp)) { Text("Yes, it works") }
                    }
                }
            }
            if (!scanAll) TextButton(onClick = { vm.tryAllCodes(after = s.brand) }) {
                Text("None work? Try codes from all brands")
            }
            if (index > 0) TextButton(onClick = { vm.replace(s.copy(index = index - 1)) }) { Text("Back to previous code set") }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (naming) {
        var name by rememberSaveable { mutableStateOf(if (scanAll) "My AC" else "${s.brand.name} AC") }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text("Name this remote") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("e.g. Bedroom AC") })
            },
            confirmButton = {
                Button(onClick = { model?.let { vm.addRemote(it, name) }; naming = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("Cancel") } },
        )
    }
}

@Composable
internal fun TryAllCard(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
        }
    }
}

fun sourceLabel(source: String) = when (source) {
    "irext" -> "IRext"
    "esp" -> "IRremoteESP8266"
    "smartir" -> "SmartIR"
    "github" -> "GitHub capture"
    else -> source
}
