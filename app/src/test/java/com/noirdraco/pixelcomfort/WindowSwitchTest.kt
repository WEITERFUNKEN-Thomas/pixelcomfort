package com.noirdraco.pixelcomfort

import org.junit.Assert.assertEquals
import org.junit.Test

class WindowSwitchTest {
    private val activities = setOf(YT_ACTIVITY, LAUNCHER_ACTIVITY, FILES_ACTIVITY)
    private fun isActivity(cls: String) = cls in activities

    private val switch = WindowSwitch()

    private fun window(pkg: String, cls: String?, time: Long, visible: Boolean = true) =
        switch.isAppSwitch(pkg, cls, visible, time, ::isActivity)

    @Test fun activityOfAVisibleAppIsASwitch() {
        assertEquals(true, window(YT, YT_ACTIVITY, 0))
    }

    @Test fun lateViewWindowOfTheAppJustLeftIsIgnored() {
        // YouTube meldet ~0,8 s nach dem Wechsel zum Launcher noch ein FrameLayout.
        window(YT, YT_ACTIVITY, 0)
        window(LAUNCHER, LAUNCHER_ACTIVITY, 10_000)
        assertEquals(false, window(YT, FRAME, 10_800))
    }

    @Test fun lateViewWindowIsIgnoredEvenIfAnotherAppCameInBetween() {
        window(YT, YT_ACTIVITY, 0)
        window(LAUNCHER, LAUNCHER_ACTIVITY, 10_000)
        window(FILES, FILES_ACTIVITY, 10_400)
        assertEquals(false, window(YT, FRAME, 10_800))
    }

    @Test fun viewWindowOfAnAppBroughtBackFromTheBackgroundIsASwitch() {
        // YouTube aus dem Hintergrund nach vorn: es kommt nur ein FrameLayout, keine Activity.
        window(YT, YT_ACTIVITY, 0)
        window(LAUNCHER, LAUNCHER_ACTIVITY, 10_000)
        assertEquals(true, window(YT, FRAME, 30_000))
    }

    @Test fun viewWindowOfAnAppNotSeenBeforeIsASwitch() {
        window(LAUNCHER, LAUNCHER_ACTIVITY, 0)
        assertEquals(true, window(YT, FRAME, 100))
    }

    @Test fun viewWindowOfTheAppInFrontIsASwitch() {
        // Harmlos: gleiche App, die Automatik aendert dann nichts.
        window(YT, YT_ACTIVITY, 0)
        assertEquals(true, window(YT, FRAME, 300))
    }

    @Test fun activityOfTheAppJustLeftStillCounts() {
        window(YT, YT_ACTIVITY, 0)
        window(LAUNCHER, LAUNCHER_ACTIVITY, 10_000)
        assertEquals(true, window(YT, YT_ACTIVITY, 10_300))
    }

    @Test fun ignoredLateWindowDoesNotChangeTheAppInFront() {
        window(YT, YT_ACTIVITY, 0)
        window(LAUNCHER, LAUNCHER_ACTIVITY, 10_000)
        window(YT, FRAME, 10_800)
        // Launcher ist weiter vorn: sein naechstes Fenster ist kein neuer Wechsel weg von YouTube.
        window(LAUNCHER, LAUNCHER_ACTIVITY, 11_000)
        assertEquals(true, window(YT, FRAME, 30_000))
    }

    @Test fun invisibleAppKeepsTheOldBehaviour() {
        window(YT, YT_ACTIVITY, 0)
        window(LAUNCHER, LAUNCHER_ACTIVITY, 10_000)
        assertEquals(true, window(YT, FRAME, 10_800, visible = false))
    }

    @Test fun missingClassNameKeepsTheOldBehaviour() {
        window(YT, YT_ACTIVITY, 0)
        window(LAUNCHER, LAUNCHER_ACTIVITY, 10_000)
        assertEquals(true, window(YT, null, 10_800))
        assertEquals(true, window(YT, "", 10_900))
    }

    private companion object {
        const val YT = "com.google.android.youtube"
        const val YT_ACTIVITY = "com.google.android.apps.youtube.app.watchwhile.MainActivity"
        const val LAUNCHER = "com.google.android.apps.nexuslauncher"
        const val LAUNCHER_ACTIVITY = "com.google.android.apps.nexuslauncher.NexusLauncherActivity"
        const val FILES = "com.google.android.apps.nbu.files"
        const val FILES_ACTIVITY = "com.google.android.apps.nbu.files.home.HomeActivity"
        const val FRAME = "android.widget.FrameLayout"
    }
}
