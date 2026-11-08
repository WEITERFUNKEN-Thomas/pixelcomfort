package com.noirdraco.pixelcomfort.aod

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

/** Connection events only: never starts a Bluetooth scan. */
class BluetoothSource(private val context: Context) : TriggerSource {
    private val manager get() = context.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? get() = manager?.adapter

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun bondedDevices(): List<Trigger.Bluetooth> {
        if (!hasPermission()) return emptyList()
        return adapter?.bondedDevices.orEmpty()
            .map { Trigger.Bluetooth(it.address, it.name ?: it.address) }
            .sortedBy { it.label.lowercase() }
    }

    @SuppressLint("MissingPermission")
    override fun keys(): Flow<Set<String>> = callbackFlow {
        if (!hasPermission()) {
            trySend(emptySet())
            awaitClose {}
            return@callbackFlow
        }
        val connected = mutableSetOf<String>()
        fun key(device: BluetoothDevice) = Trigger.Bluetooth(device.address, "").key

        // Initial state: there is no public "all connected devices" call, so combine what exists.
        adapter?.bondedDevices.orEmpty().filter(::isConnected).forEach { connected += key(it) }
        manager?.getConnectedDevices(BluetoothProfile.GATT).orEmpty().forEach { connected += key(it) }
        val proxies = mutableListOf<Pair<Int, BluetoothProfile>>()
        val profileListener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                proxies += profile to proxy
                proxy.connectedDevices.forEach { connected += key(it) }
                trySend(connected.toSet())
            }

            override fun onServiceDisconnected(profile: Int) = Unit
        }
        for (profile in intArrayOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)) {
            adapter?.getProfileProxy(context, profileListener, profile)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                when (intent.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> device?.let { connected += key(it) }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> device?.let { connected -= key(it) }
                    BluetoothAdapter.ACTION_STATE_CHANGED ->
                        if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_OFF) {
                            connected.clear()
                        }
                }
                trySend(connected.toSet())
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        // Exported because the Bluetooth process (not uid system) sends these; all three are
        // protected broadcasts, so other apps cannot fake them.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        trySend(connected.toSet())
        awaitClose {
            context.unregisterReceiver(receiver)
            proxies.forEach { (profile, proxy) -> adapter?.closeProfileProxy(profile, proxy) }
        }
    }.distinctUntilChanged().flowOn(Dispatchers.Main.immediate)

    private fun isConnected(device: BluetoothDevice): Boolean = try {
        BluetoothDevice::class.java.getMethod("isConnected").invoke(device) as Boolean
    } catch (e: ReflectiveOperationException) {
        false
    }
}
