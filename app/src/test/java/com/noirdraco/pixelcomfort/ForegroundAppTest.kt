package com.noirdraco.pixelcomfort

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundAppTest {
    private val ignored = setOf(OWN, PERMISSION_DIALOG)

    private fun decide(event: String, resumed: String?, heuristic: Boolean) =
        ForegroundApp.decide(event, resumed, heuristic) { it in ignored }

    @Test fun lateWindowOfTheAppJustLeftCountsAsTheAppInFront() {
        // Spaetes YouTube-Fenster, Android sagt: Launcher ist vorn.
        assertEquals(LAUNCHER, decide(YT, resumed = LAUNCHER, heuristic = false))
    }

    @Test fun androidWinsEvenIfTheHeuristicCalledItASwitch() {
        // Z. B. direkt nach einem Update: die Heuristik kennt den Verlauf noch nicht.
        assertEquals(LAUNCHER, decide(YT, resumed = LAUNCHER, heuristic = true))
    }

    @Test fun appBroughtBackQuicklyCountsEvenIfTheHeuristicRejectedIt() {
        assertEquals(YT, decide(YT, resumed = YT, heuristic = false))
    }

    @Test fun ignoredAppInFrontIsNoSwitch() {
        assertNull(decide(OWN, resumed = OWN, heuristic = true))
        assertNull(decide(CAMERA, resumed = PERMISSION_DIALOG, heuristic = true))
    }

    @Test fun withoutAndroidsAnswerTheHeuristicDecides() {
        assertEquals(YT, decide(YT, resumed = null, heuristic = true))
        assertNull(decide(YT, resumed = null, heuristic = false))
    }

    private companion object {
        const val YT = "com.google.android.youtube"
        const val LAUNCHER = "com.google.android.apps.nexuslauncher"
        const val CAMERA = "com.google.android.GoogleCamera"
        const val OWN = "com.noirdraco.pixelcomfort"
        const val PERMISSION_DIALOG = "com.google.android.permissioncontroller"
    }
}
