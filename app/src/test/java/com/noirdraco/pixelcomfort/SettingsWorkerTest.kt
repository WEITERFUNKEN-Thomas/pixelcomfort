package com.noirdraco.pixelcomfort

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SettingsWorkerTest {

    private val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
    private val previousOnError = SettingsWorker.onError

    @Volatile
    private var uncaught: Throwable? = null
    private val reported = mutableListOf<Throwable>()

    @Before
    fun setUp() {
        // Auf Android beendet eine nicht gefangene Ausnahme den ganzen Prozess.
        Thread.setDefaultUncaughtExceptionHandler { _, t -> uncaught = t }
        SettingsWorker.onError = { synchronized(reported) { reported += it } }
    }

    @After
    fun tearDown() {
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        SettingsWorker.onError = previousOnError
    }

    @Test
    fun failingTaskDoesNotEscapeAndLaterTasksStillRun() {
        val done = CountDownLatch(1)
        SettingsWorker.submit { throw IllegalStateException("boom") }
        SettingsWorker.submit { done.countDown() }

        assertTrue(done.await(2, TimeUnit.SECONDS))
        assertNull(uncaught)
        synchronized(reported) {
            assertEquals(1, reported.size)
            assertEquals("boom", reported.single().message)
        }
    }
}
