package `in`.gov.itantra.android.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import `in`.gov.itantra.core.crypto.KeyAgreementProvider
import `in`.gov.itantra.core.transport.TransportException
import `in`.gov.itantra.core.transport.TransportKind
import java.util.UUID

/**
 * Module B5, Bluetooth RFCOMM implementation.
 *
 * Role is explicit rather than negotiated. Bluetooth cannot have both ends calling
 * connect(), and auto-negotiating a role over a link that does not exist yet is a
 * chicken-and-egg problem; the operator (via a future UI) picks who hosts.
 *
 * Uses the insecure variant of the RFCOMM socket deliberately. The secure variant
 * triggers the platform's own pairing dialog and Bluetooth link-key encryption, which
 * sounds preferable but is not: it means confidentiality depends on Bluetooth's pairing
 * (historically weak, and vulnerable to a range of downgrade attacks) and it puts a
 * second, confusing pairing prompt in front of the operator. This app carries its own
 * ECDH exchange, AES-256-GCM and a 6-digit SAS on top of the socket, so the transport
 * is treated as an untrusted pipe and the security comes from the layer above.
 */
class BluetoothTransport(
    private val context: Context,
    keyAgreement: KeyAgreementProvider,
    private val role: Role,
    /** Required when [role] is [Role.CLIENT]: the MAC address of the host handset. */
    private val peerAddress: String? = null,
    private val serviceUuid: UUID = SERVICE_UUID,
) : StreamTransport(keyAgreement, winsFloorTies = role == Role.HOST) {

    enum class Role { HOST, CLIENT }

    override val kind: TransportKind = TransportKind.BLUETOOTH_RFCOMM

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)
            ?.adapter

    private var serverSocket: BluetoothServerSocket? = null

    @SuppressLint("MissingPermission") // BLUETOOTH_CONNECT is checked by the calling layer.
    override fun openLink(timeoutMs: Long): Link {
        val a = adapter ?: throw TransportException("this device has no Bluetooth adapter")
        if (!a.isEnabled) throw TransportException("Bluetooth is disabled")

        return when (role) {
            Role.HOST -> acceptAsHost(a, timeoutMs)
            Role.CLIENT -> connectAsClient(a)
        }
    }

    @SuppressLint("MissingPermission")
    private fun acceptAsHost(adapter: BluetoothAdapter, timeoutMs: Long): Link {
        val server = adapter.listenUsingInsecureRfcommWithServiceRecord(SERVICE_NAME, serviceUuid)
        serverSocket = server
        return try {
            // BluetoothServerSocket.accept(int) takes an int millisecond timeout.
            val socket = server.accept(timeoutMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            // Only one peer per session; stop advertising as soon as we have it.
            server.close()
            serverSocket = null
            socket.toLink()
        } catch (e: Exception) {
            runCatching { server.close() }
            serverSocket = null
            throw TransportException("no peer connected within ${timeoutMs}ms", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectAsClient(adapter: BluetoothAdapter): Link {
        val address = peerAddress
            ?: throw TransportException("CLIENT role requires a peer address")
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            throw TransportException("malformed Bluetooth address: $address")
        }
        val device: BluetoothDevice = adapter.getRemoteDevice(address)

        // Discovery is extremely disruptive to an active RFCOMM connection attempt --
        // it monopolises the radio and is the single most common cause of a connect()
        // that hangs and then fails. Always cancel it first.
        if (adapter.isDiscovering) adapter.cancelDiscovery()

        val socket = device.createInsecureRfcommSocketToServiceRecord(serviceUuid)
        return try {
            socket.connect()
            socket.toLink()
        } catch (e: Exception) {
            runCatching { socket.close() }
            throw TransportException("RFCOMM connect to $address failed", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothSocket.toLink(): Link = Link(
        input = inputStream,
        output = outputStream,
        peerName = runCatching { remoteDevice?.name }.getOrNull() ?: "unknown",
        peerAddress = runCatching { remoteDevice?.address }.getOrNull() ?: "unknown",
        closer = { runCatching { close() } },
    )

    override fun disconnect() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        super.disconnect()
    }

    companion object {
        const val SERVICE_NAME = "iTantra"

        /**
         * Application-specific UUID. Both handsets must use the same value; it is not
         * a standard service and must not collide with SPP (00001101-...).
         */
        val SERVICE_UUID: UUID = UUID.fromString("7f3d2a10-4c9b-4f2e-9a61-1b5c8d0e4a77")
    }
}
