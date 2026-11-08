package com.noirdraco.pixelcomfort.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Erzeugt das Baseline- und Start-Profil: `./gradlew :app:generateReleaseBaselineProfile`. */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    /** Start bis zur fertigen Startseite - landet auch im Start-Profil (DEX-Anordnung). */
    @Test
    fun startup() = rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
        pressHome()
        openHome()
    }

    /** Alle Seiten einmal, die App-Liste mit Scrollen. */
    @Test
    fun tour() = rule.collect(packageName = TARGET_PACKAGE) {
        pressHome()
        openHome()
        tour()
    }
}
