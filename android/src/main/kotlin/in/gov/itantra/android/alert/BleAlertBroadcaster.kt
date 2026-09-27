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

    @SuppressLint("MissingPermission")
    fun broadcastAlert(language: Language, content: AlertContent, sequence: Long, senderName: String? = null, ttl: Int = 3) {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            AppLog.w("BleAlertBroadcaster", "Bluetooth disabled, cannot broadcast alert")
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

            handler.postDelayed({
                stopBroadcasting()
            }, 30_000)
        } catch (e: Exception) {
            AppLog.e("BleAlertBroadcaster", "Error starting BLE advertising", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun broadcastAck(payloadHash: Int, receiverName: String) {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter == null || !adapter.isEnabled) return

        val advertiser = adapter.bluetoothLeAdvertiser ?: return

        stopBroadcastingAck()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0)
            .build()

        val uuid = ParcelUuid(ACK_UUID)

        // Encode payloadHash (4 bytes) and truncated receiverName (up to 9 bytes)
        val nameBytes = receiverName.toByteArray(Charsets.UTF_8)
        val nameLen = Math.min(nameBytes.size, 9)
        val buffer = java.nio.ByteBuffer.allocate(4 + nameLen)
        buffer.putInt(payloadHash)
        buffer.put(nameBytes, 0, nameLen)
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
                AppLog.d("BleAlertBroadcaster", "Started broadcasting ACK via BLE successfully")
                isAdvertisingAck = true
            }
            override fun onStartFailure(errorCode: Int) {
                AppLog.e("BleAlertBroadcaster", "BLE ACK broadcast failed: $errorCode")
                isAdvertisingAck = false
            }
        }

        try {
            advertiser.startAdvertising(settings, data, scanResponse, callback)
            advertiseAckCallback = callback

            // Broadcast ACK for 10 seconds
            handler.postDelayed({
                stopBroadcastingAck()
            }, 10_000)
        } catch (e: Exception) {
            AppLog.e("BleAlertBroadcaster", "Error starting BLE ACK advertising", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopBroadcasting() {
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
        // Standard 16-bit UUID base format ensures payload fits within 31-byte legacy BLE frame
        val ALERT_UUID: UUID = UUID.fromString("00007F3D-0000-1000-8000-00805F9B34FB")
        val ACK_UUID: UUID = UUID.fromString("00007F3E-0000-1000-8000-00805F9B34FB")
    }
}
