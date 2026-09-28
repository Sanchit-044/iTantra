package `in`.gov.itantra.android.alert

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.alert.AlertCodec
import `in`.gov.itantra.core.alert.AlertContent
import `in`.gov.itantra.core.alert.AlertPlayer
import `in`.gov.itantra.core.alert.AlertTemplate
import `in`.gov.itantra.core.alert.IncomingAlert
import `in`.gov.itantra.core.diag.AppLog
import kotlinx.coroutines.launch

import java.util.UUID

import `in`.gov.itantra.core.transport.MessageType
import `in`.gov.itantra.core.transport.Packet
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

class BleAlertScanner(
    private val context: Context,
    private val alertPlayer: AlertPlayer
) {
    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false

    private val _acks = MutableSharedFlow<Pair<Int, String>>(extraBufferCapacity = 10)
    val acks = _acks.asSharedFlow()

    private val _alerts = MutableSharedFlow<Packet>(extraBufferCapacity = 10)
    val alerts = _alerts.asSharedFlow()

    private val _latestRssi = MutableStateFlow<Int?>(null)
    val latestRssi: StateFlow<Int?> = _latestRssi.asStateFlow()

    // Sliding-window deduplication cache: Key -> timestampMs (4 second dedup window)
    private val recentAlerts = ConcurrentHashMap<String, Long>()
    private val DEDUP_WINDOW_MS = 4_000L

    private val restartScanRunnable = object : Runnable {
        @SuppressLint("MissingPermission")
        override fun run() {
            if (!isScanning) return
            try {
                val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
                val scanner = adapter?.bluetoothLeScanner
                if (scanner != null && adapter.isEnabled) {
                    scanner.stopScan(scanCallback)
                    val settings = ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .setReportDelay(0)
                        .build()
                    val filters = buildFilters()
                    scanner.startScan(filters, settings, scanCallback)
                    AppLog.d("BleAlertScanner", "Refreshed BLE scan keepalive")
                }
            } catch (e: Exception) {
                AppLog.w("BleAlertScanner", "Scan restart error: ${e.message}")
            }
            handler.postDelayed(this, 25_000)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val record = result.scanRecord ?: return
            _latestRssi.value = result.rssi
            val now = System.currentTimeMillis()
            recentAlerts.entries.removeIf { now - it.value > DEDUP_WINDOW_MS }

            val ackUuid = ParcelUuid(BleAlertBroadcaster.ACK_UUID)
            val alertUuid = ParcelUuid(BleAlertBroadcaster.ALERT_UUID)

            // 1. Check for ACK UUID (direct or through map iteration)
            var ackPayload: ByteArray? = record.serviceData[ackUuid]
            if (ackPayload == null) {
                for ((uuid, data) in record.serviceData) {
                    if (uuid.uuid == BleAlertBroadcaster.ACK_UUID) {
                        ackPayload = data
                        break
                    }
                }
            }

            if (ackPayload != null && ackPayload.size >= 4) {
                try {
                    val buffer = java.nio.ByteBuffer.wrap(ackPayload)
                    val payloadHash = buffer.int
                    val nameBytes = ByteArray(ackPayload.size - 4)
                    buffer.get(nameBytes)
                    val receiverName = String(nameBytes, Charsets.UTF_8).trim()

                    val dedupKey = "ACK:${result.device.address}:$payloadHash"
                    if (recentAlerts.putIfAbsent(dedupKey, now) == null) {
                        AppLog.d("BleAlertScanner", "Received connectionless BLE ACK from $receiverName")
                        _acks.tryEmit(Pair(payloadHash, receiverName))
                    }
                } catch (e: Exception) {
                    AppLog.w("BleAlertScanner", "Failed to parse BLE ACK payload", e)
                }
                return
            }

            // 2. Check for ALERT UUID (direct or through map iteration)
            var payload: ByteArray? = record.serviceData[alertUuid]
            if (payload == null) {
                for ((uuid, data) in record.serviceData) {
                    if (uuid.uuid == BleAlertBroadcaster.ALERT_UUID) {
                        payload = data
                        break
                    }
                }
            }

            if (payload == null || payload.size < 3) return

            try {
                val decoded = AlertCodec.decodeBlePayload(payload)
                if (decoded == null) {
                    AppLog.w("BleAlertScanner", "Failed to decode BLE alert payload")
                    return
                }

                val language = decoded.language
                val sequence = decoded.sequence
                val content = decoded.content
                val senderName = decoded.senderName ?: "Nearby Peer"
                val wirePayload = content.toWirePayload()

                if (BleAlertBroadcaster.isOriginated(sequence)) {
                    return // Ignore self-originated BLE alert broadcast
                }

                val dedupKey = "ALERT:${result.device.address}:$sequence:$wirePayload"
                if (recentAlerts.putIfAbsent(dedupKey, now) != null) {
                    return // Deduplicated within sliding window
                }

                AppLog.d("BleAlertScanner", "Received connectionless BLE alert from $senderName (sq=$sequence)")

                val ttl = decoded.ttl
                val textPayload = "$senderName\u001F$wirePayload"
                val packet = Packet.text(
                    type = MessageType.ALERT,
                    language = language,
                    sequence = sequence.toInt(),
                    text = textPayload,
                    flags = ttl,
                )

                _alerts.tryEmit(packet)

            } catch (e: Exception) {
                AppLog.w("BleAlertScanner", "Failed to parse BLE alert payload", e)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            AppLog.e("BleAlertScanner", "BLE scan failed with error $errorCode")
        }
    }

    private fun buildFilters(): List<ScanFilter> {
        val alertUuid = ParcelUuid(BleAlertBroadcaster.ALERT_UUID)
        val ackUuid = ParcelUuid(BleAlertBroadcaster.ACK_UUID)
        return listOf(
            ScanFilter.Builder().setServiceUuid(alertUuid).build(),
            ScanFilter.Builder().setServiceData(alertUuid, null).build(),
            ScanFilter.Builder().setServiceUuid(ackUuid).build(),
            ScanFilter.Builder().setServiceData(ackUuid, null).build(),
        )
    }

    @SuppressLint("MissingPermission")
    fun startScanning() {
        if (isScanning) return
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            AppLog.w("BleAlertScanner", "Bluetooth disabled, cannot scan for alerts")
            return
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            AppLog.w("BleAlertScanner", "BLE scanning not supported")
            return
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        try {
            scanner.startScan(buildFilters(), settings, scanCallback)
            isScanning = true
            handler.postDelayed(restartScanRunnable, 25_000)
            AppLog.d("BleAlertScanner", "Started BLE background scanning for connectionless alerts and ACKs")
        } catch (e: Exception) {
            AppLog.e("BleAlertScanner", "Failed to start BLE scanning", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (!isScanning) return
        handler.removeCallbacks(restartScanRunnable)
        try {
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {}
        isScanning = false
        AppLog.d("BleAlertScanner", "Stopped BLE background scanning")
    }
}
