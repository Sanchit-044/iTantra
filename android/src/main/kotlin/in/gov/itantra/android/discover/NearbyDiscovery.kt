package `in`.gov.itantra.android.discover

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import `in`.gov.itantra.android.transport.BluetoothTransport
import `in`.gov.itantra.core.discover.NearbyPeer
import `in`.gov.itantra.core.discover.NearbyPeerBook
import `in`.gov.itantra.core.discover.NearbyRadio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Scan + advertise for the Radar tab. Does not open a session.
 *
 * Wi-Fi Direct: system peer list (not a full AP scan). BLE: only our service UUID.
 */
class NearbyDiscovery(private val context: Context) {

    private val _peers = MutableStateFlow<List<NearbyPeer>>(emptyList())
    val peers: StateFlow<List<NearbyPeer>> = _peers.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val running = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())

    private var p2pManager: WifiP2pManager? = null
    private var p2pChannel: WifiP2pManager.Channel? = null
    private var p2pReceiver: BroadcastReceiver? = null

    private var leScanner: BluetoothLeScanner? = null
    private var leAdvertiser: BluetoothLeAdvertiser? = null

    private val pruneTick = object : Runnable {
        override fun run() {
            if (!running.get()) return
            val now = nowMs()
            _peers.update { NearbyPeerBook.prune(it, now) }
            handler.postDelayed(this, PRUNE_MS)
        }
    }

    private val rescanTick = object : Runnable {
        override fun run() {
            if (!running.get()) return
            requestWifiPeers()
            startWifiDiscovery()
            handler.postDelayed(this, RESCAN_MS)
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (!running.compareAndSet(false, true)) return
        _error.value = null
        startWifi()
        startBluetooth()
        handler.post(pruneTick)
        handler.postDelayed(rescanTick, RESCAN_MS)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (!running.compareAndSet(true, false)) return
        handler.removeCallbacks(pruneTick)
        handler.removeCallbacks(rescanTick)
        stopWifi()
        stopBluetooth()
    }

    @SuppressLint("MissingPermission")
    private fun startWifi() {
        val manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        if (manager == null) {
            noteError("Wi-Fi Direct is not available")
            return
        }
        val channel = manager.initialize(context, Looper.getMainLooper(), null)
        if (channel == null) {
            noteError("Could not start Wi-Fi Direct discovery")
            return
        }
        p2pManager = manager
        p2pChannel = channel
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (!running.get()) return
                when (intent.action) {
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> requestWifiPeers()
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        val on = intent.getIntExtra(
                            WifiP2pManager.EXTRA_WIFI_STATE,
                            WifiP2pManager.WIFI_P2P_STATE_DISABLED,
                        ) == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                        if (!on) noteError("Wi-Fi Direct is off")
                    }
                }
            }
        }
        p2pReceiver = receiver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        startWifiDiscovery()
        requestWifiPeers()
    }

    @SuppressLint("MissingPermission")
    private fun startWifiDiscovery() {
        val manager = p2pManager ?: return
        val channel = p2pChannel ?: return
        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() = Unit
            override fun onFailure(reason: Int) {
                if (reason != WifiP2pManager.BUSY) {
                    noteError("Wi-Fi scan failed ($reason)")
                }
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun requestWifiPeers() {
        val manager = p2pManager ?: return
        val channel = p2pChannel ?: return
        manager.requestPeers(channel) { list ->
            if (!running.get()) return@requestPeers
            val now = nowMs()
            list.deviceList.forEach { device ->
                remember(wifiSighting(device, now))
            }
        }
    }

    private fun wifiSighting(device: WifiP2pDevice, nowMs: Long): NearbyPeer =
        NearbyPeerBook.sighting(
            name = device.deviceName ?: "Unknown",
            radio = NearbyRadio.WIFI,
            address = device.deviceAddress,
            rssiDbm = null,
            nowMs = nowMs,
        )

    private fun stopWifi() {
        p2pReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        p2pReceiver = null
        val manager = p2pManager
        val channel = p2pChannel
        if (manager != null && channel != null) {
            runCatching { manager.stopPeerDiscovery(channel, null) }
        }
        p2pManager = null
        p2pChannel = null
    }

    @SuppressLint("MissingPermission")
    private fun startBluetooth() {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
            ?.adapter
        if (adapter == null || !adapter.isEnabled) {
            noteError("Bluetooth is off")
            return
        }
        val uuid = ParcelUuid(BluetoothTransport.SERVICE_UUID)
        leScanner = adapter.bluetoothLeScanner
        val filters = listOf(ScanFilter.Builder().setServiceUuid(uuid).build())
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            leScanner?.startScan(filters, settings, scanCallback)
        } catch (e: Exception) {
            noteError("Bluetooth scan failed: ${e.message}")
        }

        leAdvertiser = adapter.bluetoothLeAdvertiser
        val advSettings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(false)
            .setTimeout(0)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(uuid)
            .build()
        try {
            leAdvertiser?.startAdvertising(advSettings, data, advertiseCallback)
        } catch (e: Exception) {
            noteError("Bluetooth advertise failed: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBluetooth() {
        runCatching { leScanner?.stopScan(scanCallback) }
        runCatching { leAdvertiser?.stopAdvertising(advertiseCallback) }
        leScanner = null
        leAdvertiser = null
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!running.get()) return
            val name = result.scanRecord?.deviceName
                ?: result.device.name
                ?: "Unknown"
            remember(
                NearbyPeerBook.sighting(
                    name = name,
                    radio = NearbyRadio.BLUETOOTH,
                    address = result.device.address,
                    rssiDbm = result.rssi,
                    nowMs = nowMs(),
                )
            )
        }

        override fun onScanFailed(errorCode: Int) {
            noteError("Bluetooth scan failed ($errorCode)")
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            if (errorCode != ADVERTISE_FAILED_ALREADY_STARTED) {
                noteError("Bluetooth advertise failed ($errorCode)")
            }
        }
    }

    private fun remember(sighting: NearbyPeer) {
        _peers.update { NearbyPeerBook.upsert(it, sighting) }
    }

    private fun noteError(message: String) {
        _error.update { current -> current ?: message }
    }

    private fun nowMs(): Long = SystemClock.elapsedRealtime()

    companion object {
        private const val PRUNE_MS = 1_000L
        private const val RESCAN_MS = 8_000L
    }
}
