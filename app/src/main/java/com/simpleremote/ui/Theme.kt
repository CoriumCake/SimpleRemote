package com.simpleremote.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Shower
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.WindPower
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val Light = lightColorScheme(
    primary = Color(0xFF2F6BFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3EBFF),
    onPrimaryContainer = Color(0xFF0B2A80),
    background = Color(0xFFF3F5F9),
    onBackground = Color(0xFF15171C),
    surface = Color(0xFFF3F5F9),
    onSurface = Color(0xFF15171C),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEDEFF4),
    surfaceContainerHighest = Color(0xFFE5E8EE),
    onSurfaceVariant = Color(0xFF6B7180),
    outline = Color(0xFFB7BCC8),
    outlineVariant = Color(0xFFE1E4EA),
    error = Color(0xFFE5484D),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7FA2FF),
    onPrimary = Color(0xFF06205F),
    primaryContainer = Color(0xFF1C2D57),
    onPrimaryContainer = Color(0xFFDCE5FF),
    background = Color(0xFF0E1014),
    onBackground = Color(0xFFE8EAF0),
    surface = Color(0xFF0E1014),
    onSurface = Color(0xFFE8EAF0),
    surfaceContainerLowest = Color(0xFF16191F),
    surfaceContainerLow = Color(0xFF16191F),
    surfaceContainer = Color(0xFF1A1D24),
    surfaceContainerHigh = Color(0xFF22262E),
    surfaceContainerHighest = Color(0xFF2A2F38),
    onSurfaceVariant = Color(0xFF9AA0AD),
    outline = Color(0xFF4A505C),
    outlineVariant = Color(0xFF2A2F38),
    error = Color(0xFFFF6B6F),
)

@Composable
fun SimpleRemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}

/** Visual identity of each AC mode: gradient for the display panel + icon. */
data class ModeStyle(val label: String, val icon: ImageVector, val from: Color, val to: Color)

fun modeStyle(mode: String): ModeStyle = when (mode) {
    "cool" -> ModeStyle("Cool", Icons.Rounded.AcUnit, Color(0xFF3B82F6), Color(0xFF22B8F0))
    "heat" -> ModeStyle("Heat", Icons.Rounded.WbSunny, Color(0xFFFF7A45), Color(0xFFFFB443))
    "dry" -> ModeStyle("Dry", Icons.Rounded.WaterDrop, Color(0xFF14B8A6), Color(0xFF4ADE80))
    "fan_only" -> ModeStyle("Fan", Icons.Rounded.Air, Color(0xFF10B981), Color(0xFF6EE7B7))
    "auto", "heat_cool" -> ModeStyle("Auto", Icons.Rounded.Autorenew, Color(0xFF8B5CF6), Color(0xFFC084FC))
    else -> ModeStyle(pretty(mode), Icons.Rounded.Autorenew, Color(0xFF64748B), Color(0xFF94A3B8))
}

fun pretty(key: String): String = when (key) {
    "fan_only" -> "Fan"
    "heat_cool" -> "Auto"
    "mid", "med", "medium" -> "Medium"
    "fixed" -> "Fixed"
    "swing" -> "Swing"
    else -> key.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

/** Icon and accent gradient per device category. */
fun categoryStyle(cat: com.simpleremote.data.DeviceCategory): ModeStyle {
    val i = androidx.compose.material.icons.Icons.Rounded
    return when (cat) {
        com.simpleremote.data.DeviceCategory.AC -> ModeStyle(cat.label, i.AcUnit, Color(0xFF3B82F6), Color(0xFF22B8F0))
        com.simpleremote.data.DeviceCategory.TV -> ModeStyle(cat.label, i.Tv, Color(0xFF6366F1), Color(0xFF8B5CF6))
        com.simpleremote.data.DeviceCategory.STB -> ModeStyle(cat.label, i.Router, Color(0xFF0EA5E9), Color(0xFF38BDF8))
        com.simpleremote.data.DeviceCategory.STREAMING -> ModeStyle(cat.label, i.Cast, Color(0xFFEC4899), Color(0xFFF472B6))
        com.simpleremote.data.DeviceCategory.PROJECTOR -> ModeStyle(cat.label, i.Slideshow, Color(0xFF8B5CF6), Color(0xFFC084FC))
        com.simpleremote.data.DeviceCategory.FAN -> ModeStyle(cat.label, i.WindPower, Color(0xFF10B981), Color(0xFF6EE7B7))
        com.simpleremote.data.DeviceCategory.AUDIO -> ModeStyle(cat.label, i.Speaker, Color(0xFFF97316), Color(0xFFFBBF24))
        com.simpleremote.data.DeviceCategory.DISC -> ModeStyle(cat.label, i.Album, Color(0xFF64748B), Color(0xFF94A3B8))
        com.simpleremote.data.DeviceCategory.LIGHT -> ModeStyle(cat.label, i.Lightbulb, Color(0xFFF59E0B), Color(0xFFFDE047))
        com.simpleremote.data.DeviceCategory.AIR_PURIFIER -> ModeStyle(cat.label, i.Air, Color(0xFF14B8A6), Color(0xFF5EEAD4))
        com.simpleremote.data.DeviceCategory.HEATER -> ModeStyle(cat.label, i.LocalFireDepartment, Color(0xFFEF4444), Color(0xFFFB923C))
        com.simpleremote.data.DeviceCategory.CAMERA -> ModeStyle(cat.label, i.PhotoCamera, Color(0xFF475569), Color(0xFF64748B))
        com.simpleremote.data.DeviceCategory.VACUUM -> ModeStyle(cat.label, i.CleaningServices, Color(0xFF06B6D4), Color(0xFF67E8F9))
        com.simpleremote.data.DeviceCategory.WATER_HEATER -> ModeStyle(cat.label, i.Shower, Color(0xFFDC2626), Color(0xFFF87171))
        com.simpleremote.data.DeviceCategory.OTHER -> ModeStyle(cat.label, i.SettingsRemote, Color(0xFF64748B), Color(0xFF94A3B8))
    }
}
