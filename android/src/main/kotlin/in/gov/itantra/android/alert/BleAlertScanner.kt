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

data class BleAck(
    val payloadHash: Int,
    val receiverName: String,
    val isTracking: Boolean,
    val rssi: Int = 0,
    val isStopped: Boolean = false,
)

class BleAlertScanner(
    private val context: Context,
    private val alertPlayer: AlertPlayer
) {
    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false

    private val _acks = MutableSharedFlow<BleAck>(
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val acks = _acks.asSharedFlow()

    private val _alerts = MutableSharedFlow<Packet>(
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val alerts = _alerts.asSharedFlow()

    private val _latestRssi = MutableStateFlow<Int?>(null)
    val latestRssi: StateFlow<Int?> = _latestRssi.asStateFlow()

    private var smoothedRssi: Float? = null

    private fun updateSmoothedRssi(rawRssi: Int) {
        if (rawRssi == 0) return
        val prev = smoothedRssi
        val next = if (prev == null) {
            rawRssi.toFloat()
        } else {
            prev * 0.70f + rawRssi.toFloat() * 0.30f
        }
        smoothedRssi = next
        _latestRssi.value = Math.round(next)
    }

    // Sliding-window deduplication cache: Key -> timestampMs (4 second dedup window)
    private val recentAlerts = ConcurrentHashMap<String, Long>()
    private val DEDUP_WINDOW_MS = 4_000L
    private val TRACKING_DEDUP_WINDOW_MS = 1_500L

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
                    try {
                        scanner.startScan(emptyList(), settings, scanCallback)
                    } catch (_: Exception) {
                        scanner.startScan(buildFilters(), settings, scanCallback)
                    }
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
                    updateSmoothedRssi(result.rssi)
                    val buffer = java.nio.ByteBuffer.wrap(ackPayload)
                    val payloadHash = buffer.int
                    val (statusByte, receiverName) = if (ackPayload.size >= 5) {
                        val status = buffer.get().toInt()
                        val nameBytes = ByteArray(buffer.remaining())
                        buffer.get(nameBytes)
                        val name = String(nameBytes, Charsets.UTF_8).trim().ifBlank { "Responder" }
                        status to name.replace(" (Tracking)", "").trim()
                    } else {
                        val nameBytes = ByteArray(buffer.remaining())
                        buffer.get(nameBytes)
                        val name = String(nameBytes, Charsets.UTF_8).trim().ifBlank { "Responder" }
                        1 to name.replace(" (Tracking)", "").trim()
                    }

                    val isTracking = statusByte == 2 || receiverName.contains("Tracking")
                    val isStopped = statusByte == 3
                    val dedupKey = "ACK:${result.device.address}:$payloadHash:$statusByte"
                    val windowMs = if (isTracking) TRACKING_DEDUP_WINDOW_MS else DEDUP_WINDOW_MS
                    val lastSeen = recentAlerts[dedupKey]
                    if (lastSeen == null || now - lastSeen >= windowMs) {
                        recentAlerts[dedupKey] = now
                        AppLog.d("BleAlertScanner", "Received connectionless BLE ACK from $receiverName (tracking=$isTracking, stopped=$isStopped, rssi=${result.rssi})")
                        _acks.tryEmit(BleAck(payloadHash, receiverName, isTracking, result.rssi, isStopped = isStopped))
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

                updateSmoothedRssi(result.rssi)

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
            // Use empty filter list to prevent OEM BLE hardware filter bugs from dropping serviceData packets
            scanner.startScan(emptyList(), settings, scanCallback)
            isScanning = true
            handler.postDelayed(restartScanRunnable, 25_000)
            AppLog.d("BleAlertScanner", "Started BLE background scanning for connectionless alerts and ACKs (low-latency)")
        } catch (e: Exception) {
            AppLog.w("BleAlertScanner", "startScan with emptyList failed (${e.message}), trying with fallback filters")
            try {
                scanner.startScan(buildFilters(), settings, scanCallback)
                isScanning = true
                handler.postDelayed(restartScanRunnable, 25_000)
            } catch (e2: Exception) {
                AppLog.e("BleAlertScanner", "Failed to start BLE scanning", e2)
            }
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
