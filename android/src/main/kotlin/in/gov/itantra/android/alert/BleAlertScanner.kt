package `in`.gov.itantra.android.alert

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
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

import kotlinx.coroutines.flow.asSharedFlow

class BleAlertScanner(
    private val context: Context,
    private val alertPlayer: AlertPlayer
) {
    private var isScanning = false
    
    private val _acks = kotlinx.coroutines.flow.MutableSharedFlow<Pair<Int, String>>(extraBufferCapacity = 10)
    val acks = _acks.asSharedFlow()

    // Track recent alert sequences to avoid playing the same alert twice
    private val recentAlerts = mutableSetOf<String>()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val record = result.scanRecord ?: return
            
            // Check for ACK UUID first
            val ackUuid = ParcelUuid(BleAlertBroadcaster.ACK_UUID)
            val ackPayload = record.serviceData[ackUuid]
            if (ackPayload != null && ackPayload.size >= 4) {
                try {
                    val buffer = java.nio.ByteBuffer.wrap(ackPayload)
                    val payloadHash = buffer.int
                    val nameBytes = ByteArray(ackPayload.size - 4)
                    buffer.get(nameBytes)
                    val receiverName = String(nameBytes, Charsets.UTF_8)
                    
                    val dedupKey = "ACK:${result.device.address}:$payloadHash"
                    if (recentAlerts.add(dedupKey)) {
                        AppLog.d("BleAlertScanner", "Received connectionless BLE ACK from $receiverName")
                        _acks.tryEmit(Pair(payloadHash, receiverName))
                    }
                } catch (e: Exception) {
                    AppLog.w("BleAlertScanner", "Failed to parse BLE ACK payload", e)
                }
                return
            }

            // Check for ALERT UUID
            val alertUuid = ParcelUuid(BleAlertBroadcaster.ALERT_UUID)
            val payload = record.serviceData[alertUuid] ?: return
            
            if (payload.size < 3) return // Must be at least 3 bytes

            try {
                val decoded = AlertCodec.decodeBlePayload(payload)
                if (decoded == null) {
                    AppLog.w("BleAlertScanner", "Failed to decode BLE alert payload")
                    return
                }

                val language = decoded.language
                val template = (decoded.content as? AlertContent.Template)?.template ?: return
                val sequence = decoded.sequence
                val content = decoded.content
                val senderName = decoded.senderName
                
                // Deduplication key: device address + sequence
                val dedupKey = "${result.device.address}:$sequence"
                if (!recentAlerts.add(dedupKey)) {
                    return // Already played this one
                }
                
                // Cap cache size
                if (recentAlerts.size > 200) {
                    val iterator = recentAlerts.iterator()
                    for (i in 0 until 100) {
                        if (iterator.hasNext()) iterator.next()
                        iterator.remove()
                    }
                }

                val alert = IncomingAlert(
                    content = content,
                    language = language,
                    sequence = sequence,
                    receivedAtMs = System.currentTimeMillis(),
                    senderName = senderName
                )
                AppLog.d("BleAlertScanner", "Received connectionless BLE alert from $senderName: ${template.name}")
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    alertPlayer.play(alert)
                }

            } catch (e: Exception) {
                AppLog.w("BleAlertScanner", "Failed to parse BLE alert payload", e)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            AppLog.e("BleAlertScanner", "BLE scan failed with error $errorCode")
        }
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
            .build()

        val alertFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(BleAlertBroadcaster.ALERT_UUID))
            .build()
            
        val ackFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(BleAlertBroadcaster.ACK_UUID))
            .build()

        try {
            scanner.startScan(listOf(alertFilter, ackFilter), settings, scanCallback)
            isScanning = true
            AppLog.d("BleAlertScanner", "Started BLE background scanning for connectionless alerts and ACKs")
        } catch (e: Exception) {
            AppLog.e("BleAlertScanner", "Failed to start BLE scanning", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (!isScanning) return
        try {
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {}
        isScanning = false
        AppLog.d("BleAlertScanner", "Stopped BLE background scanning")
    }
}
