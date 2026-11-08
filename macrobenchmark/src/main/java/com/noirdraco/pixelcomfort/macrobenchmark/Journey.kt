package com.noirdraco.pixelcomfort.macrobenchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/** Die Mess-Variante laeuft unter eigener App-ID (siehe app/build.gradle.kts). */
const val TARGET_PACKAGE = "com.noirdraco.pixelcomfort.benchmark"

private const val UI_TIMEOUT_MS = 10_000L

/** Nach einem Wisch ausklingen lassen (fling() wartet bis zu 5 s aufs Listenende). */
private const val FLING_SETTLE_MS = 800L

/** Startseite: wartet, bis die Karten da sind. */
fun MacrobenchmarkScope.openHome() {
    startActivityAndWait()
    waitFor("feature_comfort")
}

/**
 * Oeffnet die Seite hinter einer Karte und geht danach zurueck. Angetippt wird die Mitte
 * der Karte (der Text), nie der Schalter rechts - die Messung verstellt nichts am Geraet.
 */
fun MacrobenchmarkScope.visit(tag: String, waitTag: String? = null, scrollTag: String? = null) {
    findOnHome(tag).click()
    device.waitForIdle()
    if (waitTag != null) waitFor(waitTag)
    if (scrollTag != null) flingList(scrollTag)
    device.pressBack()
    waitFor("feature_comfort")
}

/** Zweimal nach unten, einmal nach oben. */
fun MacrobenchmarkScope.flingList(tag: String) {
    val list = device.findObject(By.res(tag)) ?: return
    val r = list.visibleBounds
    val x = r.centerX()
    val low = r.top + r.height() * 4 / 5
    val high = r.top + r.height() / 5
    repeat(2) {
        device.swipe(x, low, x, high, 6)
        Thread.sleep(FLING_SETTLE_MS)
    }
    device.swipe(x, high, x, low, 6)
    Thread.sleep(FLING_SETTLE_MS)
}

/** Der ganze Rundgang: Apps (mit Scrollen), DNS, Anzeige, Verlauf. */
fun MacrobenchmarkScope.tour() {
    visit("feature_comfort", waitTag = "apps_list", scrollTag = "apps_list")
    visit("feature_dns")
    visit("feature_display")
    visit("history")
}

/**
 * Karte auf der Startseite finden. In der Mess-Variante fehlt die Einrichtung (kein Shizuku,
 * keine Bedienungshilfe), die Einrichtungs-Gruppe schiebt die unteren Karten dann unter den
 * Bildschirmrand - UiAutomator findet nur Sichtbares, also bei Bedarf hinscrollen.
 */
private fun MacrobenchmarkScope.findOnHome(tag: String): UiObject2 {
    device.wait(Until.findObject(By.res(tag)), 2_000L)?.let { return it }
    device.findObject(By.res("home_list"))?.scroll(Direction.DOWN, 1f)
    return checkNotNull(device.wait(Until.findObject(By.res(tag)), UI_TIMEOUT_MS)) { "'$tag' nicht gefunden" }
}

private fun MacrobenchmarkScope.waitFor(tag: String) {
    check(device.wait(Until.hasObject(By.res(tag)), UI_TIMEOUT_MS)) { "'$tag' nicht sichtbar" }
}
