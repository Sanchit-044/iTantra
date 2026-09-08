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

class BleAlertScanner(
    private val context: Context,
    private val alertPlayer: AlertPlayer
) {
    private var isScanning = false
    
    // Track recent alert sequences to avoid playing the same alert twice
    private val recentAlerts = mutableSetOf<String>()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val record = result.scanRecord ?: return
            val uuid = ParcelUuid(BleAlertBroadcaster.ALERT_UUID)
            val payload = record.serviceData[uuid] ?: return
            
            if (payload.size != 3) return // Must be exactly 3 bytes

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
                
                // Deduplication key: device address + sequence
                val dedupKey = "${result.device.address}:$sequence"
                if (!recentAlerts.add(dedupKey)) {
                    return // Already played this one
                }
                
                // Cap cache size
                if (recentAlerts.size > 100) {
                    val iterator = recentAlerts.iterator()
                    for (i in 0 until 50) {
                        if (iterator.hasNext()) iterator.next()
                        iterator.remove()
                    }
                }

                val alert = IncomingAlert(
                    content = content,
                    language = language,
                    sequence = sequence,
                    receivedAtMs = System.currentTimeMillis()
                )
                AppLog.d("BleAlertScanner", "Received connectionless BLE alert: ${template.name}")
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

        val uuid = ParcelUuid(BleAlertBroadcaster.ALERT_UUID)
        val filter = ScanFilter.Builder()
            .setServiceUuid(uuid)
            .build()
            
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(listOf(filter), settings, scanCallback)
            isScanning = true
            AppLog.d("BleAlertScanner", "Started BLE background scanning for connectionless alerts")
        } catch (e: Exception) {
            AppLog.e("BleAlertScanner", "Error starting BLE scan", e)
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
