package com.noirdraco.pixelcomfort

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Speichert den Verlauf als Textdatei, eine Zeile pro Eintrag (Format: [History]).
 *
 * Liegt in noBackupFilesDir: der Verlauf gehoert nur zu diesem Geraet und wandert
 * weder ins Cloud-Backup noch beim Geraetewechsel mit.
 */
object HistoryStore {

    private const val TAG = ComfortAccessibilityService.TAG
    private const val FILE = "history.tsv"

    private fun file(context: Context) = File(context.applicationContext.noBackupFilesDir, FILE)

    @Synchronized
    fun load(context: Context): List<History.Entry> =
        try {
            val f = file(context)
            if (f.exists()) f.readLines().mapNotNull(History::decode) else emptyList()
        } catch (t: Throwable) {
            Log.w(TAG, "Verlauf nicht lesbar", t)
            emptyList()
        }

    @Synchronized
    fun add(context: Context, entry: History.Entry) {
        try {
            val entries = History.add(load(context), entry)
            file(context).writeText(entries.joinToString("\n", transform = History::encode))
        } catch (t: Throwable) {
            // Der Verlauf ist nur Beiwerk - ein Schreibfehler darf nie das Schalten stoeren.
            Log.w(TAG, "Verlauf nicht schreibbar", t)
        }
    }

    @Synchronized
    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}
