package com.noirdraco.pixelcomfort.aod

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.noirdraco.pixelcomfort.ComfortAccessibilityService
import com.noirdraco.pixelcomfort.History
import com.noirdraco.pixelcomfort.HistoryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

private val Context.aodStore: DataStore<AodData> by dataStore(
    fileName = "aod_rules.json",
    serializer = AodDataSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler { AodData() },
)

/**
 * Always-On-Display je nach Bluetooth-Gerät oder WLAN (übernommen aus PixelAwakeLink).
 *
 * Statt eines eigenen Vordergrunddienstes mit Dauer-Benachrichtigung läuft die Überwachung in
 * der Bedienungshilfe ([ComfortAccessibilityService]), die ohnehin dauerhaft läuft und nach einem
 * Neustart vom System gestartet wird. Rein ereignisgesteuert: kein Polling, keine Scans; einzige
 * Verzögerung ist die Entprellung nach einem Ereignis.
 *
 * Die Entscheidungen trifft [RuleEngine]; hier nur Quellen zusammenführen, schreiben, merken.
 */
@OptIn(FlowPreview::class)
object AodMonitor {

    private const val TAG = ComfortAccessibilityService.TAG
    private const val DEBOUNCE_MS = 750L

    fun repository(context: Context) = RuleRepository(context.applicationContext.aodStore)
    fun controller(context: Context) = AodController(context.applicationContext)
    fun bluetooth(context: Context) = BluetoothSource(context.applicationContext)
    fun wifi(context: Context) = WifiSource(context.applicationContext)

    private val _status = MutableStateFlow<Status>(Status.Disabled)

    /** Was die Automatik gerade tut - für die Oberfläche. Ohne laufende Bedienungshilfe: Disabled. */
    val status: StateFlow<Status> = _status.asStateFlow()

    private var scope: CoroutineScope? = null
    private var pipeline: Job? = null
    private var appContext: Context? = null
    private var engine = EngineState()

    @Synchronized
    fun start(context: Context) {
        if (scope != null) return
        val ctx = context.applicationContext
        appContext = ctx
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = s
        pipeline = s.launch {
            engine = repository(ctx).data.first().engine
            monitor(ctx)
        }
        Log.i(TAG, "AOD: Überwachung gestartet")
    }

    /**
     * Neu aufsetzen, z. B. nachdem eine Berechtigung erteilt wurde: Die Quellen registrieren sich
     * dann mit den neuen Rechten. Nur wenn die Bedienungshilfe läuft.
     */
    @Synchronized
    fun restart() {
        val ctx = appContext ?: return
        if (scope == null) return
        stop()
        start(ctx)
    }

    @Synchronized
    fun stop() {
        scope?.cancel()
        scope = null
        pipeline = null
        _status.value = Status.Disabled
    }

    /**
     * Die Bedienungshilfe wird abgeschaltet: Hat eine Regel das AOD umgestellt, kommt der eigene
     * Wert des Nutzers zurück - sonst bliebe die Regel ohne Überwachung stehen.
     */
    fun stopAndRestore(context: Context, timeoutMs: Long) {
        stop()
        val ctx = context.applicationContext
        runBlocking {
            withTimeoutOrNull(timeoutMs) {
                val repo = repository(ctx)
                val aod = controller(ctx)
                val data = repo.data.first()
                val input = RuleEngine.Input(automation = false, rules = data.rules, activeKeys = emptySet(), currentAod = aod.read())
                val result = RuleEngine.evaluate(input, data.engine)
                result.write?.let { aod.write(it) }
                repo.updateEngine(result.state)
            }
        }
    }

    private suspend fun monitor(ctx: Context) {
        val repo = repository(ctx)
        val settings = repo.data.map { it.automation to it.rules }.distinctUntilChanged()
        combine(settings, bluetooth(ctx).keys(), wifi(ctx).keys(), controller(ctx).observe()) { (automation, rules), bt, wifi, aod ->
            RuleEngine.Input(automation, rules, bt + wifi, aod)
        }
            // Lässt die Quellen nach dem (Neu-)Registrieren ankommen und schluckt das Echo der eigenen Schreibaktion.
            .debounce(DEBOUNCE_MS)
            .collect { apply(ctx, repo, it) }
    }

    private suspend fun apply(ctx: Context, repo: RuleRepository, input: RuleEngine.Input) {
        val aod = controller(ctx)
        val active = input.automation && input.rules.any { it.enabled }
        if (active && !aod.canWrite()) {
            _status.value = Status.MissingPermission
            return
        }
        val result = RuleEngine.evaluate(input.copy(automation = active), engine)
        val written = result.write?.let(aod::write) ?: true
        if (active && !written) {
            // Nichts geschrieben, also wird auch nichts "erwartet": alten Zustand behalten.
            _status.value = Status.MissingPermission
            return
        }
        result.write?.let { on ->
            Log.i(TAG, "AOD -> ${if (on) "an" else "aus"} (${result.status})")
            val rule = (result.status as? Status.RuleActive)?.let { s -> input.rules.firstOrNull { it.id == s.ruleId } }
            HistoryStore.add(
                ctx,
                History.Entry(
                    time = System.currentTimeMillis(),
                    kind = if (on) History.Kind.AOD_ON else History.Kind.AOD_OFF,
                    subject = rule?.trigger?.label.orEmpty(),
                ),
            )
        }
        if (result.state != engine) {
            engine = result.state
            repo.updateEngine(engine)
        }
        _status.value = result.status
    }
}
