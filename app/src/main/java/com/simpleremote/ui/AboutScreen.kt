package com.simpleremote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AboutScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    fun license(name: String) = runCatching {
        ctx.assets.open("licenses/$name").bufferedReader().use { it.readText() }
    }.getOrDefault("")
    val smartir = remember { license("SmartIR-LICENSE.txt") }
    val irext = remember { license("IRext-LICENSE.txt") }
    val airbridge = remember { license("AirBridge-LICENSE.txt") }
    val esp = remember { license("IRremoteESP8266-LICENSE.txt") }
    val flipper = remember { license("Flipper-IRDB-LICENSE.txt") }
    val sets = vm.brands.flatMap { it.entries }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("About", onBack = vm::back)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("SimpleRemote", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("A simple IR remote for phones with an infrared blaster.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${vm.brands.size} AC brands · ${sets.size} code sets " +
                    "(${sets.count { it.source == "irext" }} IRext, ${sets.count { it.source == "smartir" }} SmartIR)",
                style = MaterialTheme.typography.bodyMedium,
            )
            SourceCard("Flipper-IRDB", "github.com/Lucaslhm/Flipper-IRDB — TV, projector, fan, audio, light and other remotes (CC0)", flipper)
            SourceCard(
                "IRremoteESP8266",
                "github.com/crankyoldgit/IRremoteESP8266 — AC protocol encoders, used unmodified except " +
                    "for capturing timings (source in the app's repository, LGPL-2.1, linked as libirac.so)",
                esp,
            )
            SourceCard("IRext", "github.com/irext — open IR database: AC, TV, set-top box, fan, projector and more", irext)
            SourceCard("SmartIR", "github.com/smartHomeHub/SmartIR — AC code tables", smartir)
            SourceCard("AirBridge", "github.com/bejranonda/AirBridge — Thai-market AC protocols (Saijo Denki)", airbridge)
            SourceCard("cpe_air_remote", "github.com/Rapeepat29/cpe_air_remote — Saijo Denki remote capture", "")
            Text("", Modifier.padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun SourceCard(title: String, subtitle: String, license: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(license, style = MaterialTheme.typography.bodySmall)
        }
    }
}
