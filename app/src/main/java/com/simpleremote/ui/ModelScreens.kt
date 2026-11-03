package com.simpleremote.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.simpleremote.data.DeviceCategory
import com.simpleremote.data.ModelChoice
import com.simpleremote.data.models
import com.simpleremote.data.pinned

@Composable
fun DeviceModelsScreen(vm: AppViewModel, s: Screen.DeviceModels) {
    val models = remember(s.brand) { s.brand.models }
    ModelPicker(
        title = "Choose ${s.brand.name} model",
        noun = if (s.category == DeviceCategory.TV) "TV" else s.category.label.lowercase(),
        models = models,
        codesToTry = s.brand.steps.size,
        onUnknown = { vm.navigate(Screen.DeviceTest(s.category, s.brand)) },
        onPick = { m -> vm.navigate(Screen.DeviceTest(s.category, s.brand.copy(pin = m.id))) },
        onBack = vm::back,
    )
}

@Composable
fun AcModelsScreen(vm: AppViewModel, s: Screen.AcModels) {
    val models = remember(s.brand) { s.brand.models() }
    ModelPicker(
        title = "Choose ${s.brand.name} model",
        noun = "AC",
        models = models,
        codesToTry = s.brand.entries.size,
        onUnknown = { vm.navigate(Screen.TestCodes(s.brand)) },
        onPick = { m -> vm.navigate(Screen.TestCodes(s.brand.pinned(m.id))) },
        onBack = vm::back,
    )
}

/** Searchable model list with an "I don't know my model" shortcut to step-by-step testing. */
@Composable
private fun ModelPicker(
    title: String,
    noun: String,
    models: List<ModelChoice>,
    codesToTry: Int,
    onUnknown: () -> Unit,
    onPick: (ModelChoice) -> Unit,
    onBack: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(query, models) { models.filter { it.matches(query) } }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title, onBack = onBack)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item(key = "unknown") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Surface(
                        onClick = onUnknown,
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("I don't know my model", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Test the codes one by one ($codesToTry to try, most common first)",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Or pick your model. The model number is on a label on the back of the $noun, " +
                            "or on the back of the remote (inside the battery cover).",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextField(
                        value = query, onValueChange = { query = it },
                        placeholder = { Text("Search ${models.size} models, e.g. ${models.first().label}") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Clear, "Clear") } },
                        singleLine = true,
                        shape = RoundedCornerShape(50),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (shown.isEmpty()) item(key = "none") {
                Text(
                    "No model matches \"$query\". Similar models often share one code, so try \"I don't know my model\" above.",
                    Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(shown, key = { it.label + it.id }) { m ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(m) }.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(m.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}
