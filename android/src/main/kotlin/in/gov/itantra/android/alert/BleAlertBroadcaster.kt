package `in`.gov.itantra.android.alert

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.alert.AlertCodec
import `in`.gov.itantra.core.alert.AlertContent
import `in`.gov.itantra.core.diag.AppLog
import java.util.UUID

class BleAlertBroadcaster(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var isAdvertising = false
    private var isAdvertisingAck = false
    private var advertiseCallback: AdvertiseCallback? = null
    private var advertiseAckCallback: AdvertiseCallback? = null

    private var stopBroadcastRunnable: Runnable? = null

    fun markOriginated(sequence: Int) = Companion.markOriginated(sequence)
    fun isOriginated(sequence: Int): Boolean = Companion.isOriginated(sequence)
    fun clearOriginated(sequence: Int) = Companion.clearOriginated(sequence)

    @SuppressLint("MissingPermission")
    fun broadcastAlert(
        language: Language,
        content: AlertContent,
        sequence: Long,
        senderName: String? = null,
        ttl: Int = 3,
        durationMs: Long = 300_000L, // 5 minutes default
    ) {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            AppLog.w("BleAlertBroadcaster", "Bluetooth disabled or unavailable, cannot broadcast alert")
            return
        }

        val advertiser = adapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            AppLog.w("BleAlertBroadcaster", "BLE advertising not supported on this device")
            return
        }

        val payload = encodePayload(language, content, sequence, senderName, ttl)
        if (payload == null) {
            AppLog.w("BleAlertBroadcaster", "Alert too large for BLE broadcast (connectionless)")
            return
        }

        originatedSequences[sequence.toInt()] = System.currentTimeMillis()
        stopBroadcasting()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0)
            .build()

        val uuid = ParcelUuid(ALERT_UUID)
        // Using 16-bit service data fits within 31-byte legacy BLE advertising frame
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceData(uuid, payload)
            .build()

        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(uuid)
            .build()

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                AppLog.d("BleAlertBroadcaster", "Started broadcasting alert via BLE successfully")
                isAdvertising = true
            }

            override fun onStartFailure(errorCode: Int) {
                AppLog.e("BleAlertBroadcaster", "BLE broadcast failed: $errorCode")
                isAdvertising = false
            }
        }

        try {
            advertiser.startAdvertising(settings, data, scanResponse, callback)
            advertiseCallback = callback

            val runnable = Runnable { stopBroadcasting() }
            stopBroadcastRunnable = runnable
            handler.postDelayed(runnable, durationMs)
        } catch (e: Exception) {
            AppLog.e("BleAlertBroadcaster", "Error starting BLE advertising", e)
        }
    }

    private var currentAckPayloadHash: Int? = null
    private var currentAckStatusByte: Byte? = null
    private var currentAckReceiverName: String? = null

    @SuppressLint("MissingPermission")
    fun broadcastAck(payloadHash: Int, receiverName: String, statusByte: Byte = STATUS_RECEIVED) {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter == null || !adapter.isEnabled) return

        val advertiser = adapter.bluetoothLeAdvertiser ?: return

        val cleanName = receiverName.replace(" (Tracking)", "").trim().ifBlank { "Responder" }

        // If already advertising this ACK with the same status state, refresh timeout and avoid BLE restart churn
        if (isAdvertisingAck && currentAckPayloadHash == payloadHash && currentAckStatusByte == statusByte && currentAckReceiverName == cleanName) {
            handler.removeCallbacksAndMessages(currentAckToken)
            handler.postDelayed({ stopBroadcastingAck() }, currentAckToken, if (statusByte == STATUS_TRACKING) 25_000L else 15_000L)
            return
        }

        stopBroadcastingAck()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0)
            .build()

        val uuid = ParcelUuid(ACK_UUID)

        // Encode payloadHash (4 bytes) + status byte (1 byte: 1=RECEIVED, 2=TRACKING, 3=STOPPED) + clean receiverName (safe UTF-8 up to 18 bytes)
        var nameBytes = cleanName.toByteArray(Charsets.UTF_8)
        var safeNameLen = Math.min(nameBytes.size, 18)
        while (safeNameLen > 0 && (nameBytes[safeNameLen - 1].toInt() and 0xC0) == 0x80) {
            safeNameLen--
        }
        if (safeNameLen > 0 && (nameBytes[safeNameLen - 1].toInt() and 0x80) != 0) {
            safeNameLen--
        }
        if (safeNameLen == 0) {
            val asciiName = cleanName.filter { it.code in 32..126 }.take(18).ifBlank { "Responder" }
            nameBytes = asciiName.toByteArray(Charsets.UTF_8)
            safeNameLen = Math.min(nameBytes.size, 18)
        }

        val buffer = java.nio.ByteBuffer.allocate(5 + safeNameLen)
        buffer.putInt(payloadHash)
        buffer.put(statusByte)
        buffer.put(nameBytes, 0, safeNameLen)
        val payload = buffer.array()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceData(uuid, payload)
            .build()

        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(uuid)
            .build()

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                AppLog.d("BleAlertBroadcaster", "Started broadcasting ACK via BLE successfully (status=$statusByte, name=$cleanName)")
                isAdvertisingAck = true
                currentAckPayloadHash = payloadHash
                currentAckStatusByte = statusByte
                currentAckReceiverName = cleanName
            }
            override fun onStartFailure(errorCode: Int) {
                AppLog.e("BleAlertBroadcaster", "BLE ACK broadcast failed: $errorCode")
                isAdvertisingAck = false
                currentAckPayloadHash = null
                currentAckStatusByte = null
                currentAckReceiverName = null
            }
        }

        try {
            advertiser.startAdvertising(settings, data, scanResponse, callback)
            advertiseAckCallback = callback

            val timeoutMs = if (statusByte == STATUS_TRACKING) 25_000L else 15_000L
            handler.postDelayed({
                stopBroadcastingAck()
            }, currentAckToken, timeoutMs)
        } catch (e: Exception) {
            AppLog.e("BleAlertBroadcaster", "Error starting BLE ACK advertising", e)
        }
    }

    fun broadcastAck(payloadHash: Int, receiverName: String, isTracking: Boolean) {
        broadcastAck(payloadHash, receiverName, if (isTracking) STATUS_TRACKING else STATUS_RECEIVED)
    }

    private val currentAckToken = Any()

    @SuppressLint("MissingPermission")
    fun stopBroadcasting() {
        stopBroadcastRunnable?.let { handler.removeCallbacks(it) }
        stopBroadcastRunnable = null
        if (!isAdvertising) return
        val cb = advertiseCallback ?: return
        try {
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            adapter?.bluetoothLeAdvertiser?.stopAdvertising(cb)
        } catch (_: Exception) {}
        advertiseCallback = null
        isAdvertising = false
        AppLog.d("BleAlertBroadcaster", "Stopped broadcasting alert")
    }

    @SuppressLint("MissingPermission")
    fun stopBroadcastingAck() {
        if (!isAdvertisingAck) return
        val cb = advertiseAckCallback ?: return
        try {
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            adapter?.bluetoothLeAdvertiser?.stopAdvertising(cb)
        } catch (_: Exception) {}
        advertiseAckCallback = null
        isAdvertisingAck = false
    }

    private fun encodePayload(language: Language, content: AlertContent, sequence: Long, senderName: String? = null, ttl: Int = 3): ByteArray? {
        return AlertCodec.encodeBlePayload(language, content, sequence, senderName, ttl)
    }

    companion object {
        val originatedSequences = java.util.concurrent.ConcurrentHashMap<Int, Long>()
        // Standard 16-bit UUID base format ensures payload fits within 31-byte legacy BLE frame
        val ALERT_UUID: UUID = UUID.fromString("00007F3D-0000-1000-8000-00805F9B34FB")
        val ACK_UUID: UUID = UUID.fromString("00007F3E-0000-1000-8000-00805F9B34FB")

        const val STATUS_RECEIVED: Byte = 1
        const val STATUS_TRACKING: Byte = 2
        const val STATUS_STOPPED: Byte = 3

        fun markOriginated(sequence: Int) {
            originatedSequences[sequence] = System.currentTimeMillis()
        }

        fun isOriginated(sequence: Int): Boolean {
            val ts = originatedSequences[sequence] ?: return false
            if (System.currentTimeMillis() - ts > 300_000L) {
                originatedSequences.remove(sequence)
                return false
            }
            return true
        }

        fun clearOriginated(sequence: Int) {
            originatedSequences.remove(sequence)
        }
    }
}
