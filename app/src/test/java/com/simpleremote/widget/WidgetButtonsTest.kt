package com.simpleremote.widget

import com.simpleremote.data.DeviceEntry
import com.simpleremote.data.DeviceRemote
import com.simpleremote.data.RemoteButton
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetButtonsTest {
    private fun remote(vararg keys: String) = DeviceRemote(
        DeviceEntry(id = "x", cat = "tv", brand = "B", src = "flipper", g = "g", n = keys.size),
        keys.map { RemoteButton(it, if (it.startsWith("Extra")) "" else it) { null } },
    )

    private fun picked(vararg keys: String) = RemoteWidget.pickButtons(remote(*keys)).map { it.label }

    @Test fun tvGetsVolumeAndMute() = assertEquals(listOf("vol_down", "mute", "vol_up"),
        picked("power", "ch_up", "vol_up", "mute", "vol_down", "1"))

    @Test fun missingMiddleIsFilledInBetween() = assertEquals(listOf("vol_down", "input", "vol_up"),
        picked("power", "vol_up", "vol_down", "input"))

    @Test fun fanGetsSpeedSwingTimer() = assertEquals(listOf("speed", "oscillate", "timer"),
        picked("power", "timer", "oscillate", "speed"))

    @Test fun fallsBackToExtras() = assertEquals(listOf("Extra A", "Extra B"),
        picked("power", "Extra A", "Extra B"))
}
