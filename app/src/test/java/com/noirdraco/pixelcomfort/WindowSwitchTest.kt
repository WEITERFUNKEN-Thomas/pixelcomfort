package com.noirdraco.pixelcomfort

import org.junit.Assert.assertEquals
import org.junit.Test

class WindowSwitchTest {
    private val activities = setOf("com.example.MainActivity")
    private fun isActivity(cls: String) = cls in activities

    @Test fun activityOfAVisibleAppIsASwitch() {
        assertEquals(true, WindowSwitch.isAppSwitch("com.example.MainActivity", packageVisible = true, ::isActivity))
    }

    @Test fun lateViewWindowOfAVisibleAppIsIgnored() {
        // YouTube meldet nach dem Wechsel zum Launcher noch ein FrameLayout.
        assertEquals(false, WindowSwitch.isAppSwitch("android.widget.FrameLayout", packageVisible = true, ::isActivity))
    }

    @Test fun invisibleAppKeepsTheOldBehaviour() {
        assertEquals(true, WindowSwitch.isAppSwitch("android.widget.FrameLayout", packageVisible = false, ::isActivity))
    }

    @Test fun missingClassNameKeepsTheOldBehaviour() {
        assertEquals(true, WindowSwitch.isAppSwitch(null, packageVisible = true, ::isActivity))
        assertEquals(true, WindowSwitch.isAppSwitch("", packageVisible = true, ::isActivity))
    }
}
