package `in`.gov.itantra.android.transport

import android.content.Context
import `in`.gov.itantra.core.crypto.KeyAgreementProvider
import `in`.gov.itantra.core.transport.TransportException
import `in`.gov.itantra.core.transport.TransportKind
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Mobile Hotspot / LAN transport.
 * 
 * Host opens a ServerSocket and beacons its presence over UDP.
 * Client listens for the UDP beacon to find the Host's IP, then connects via TCP.
 */
class LanTransport(
    private val context: Context,
    keyAgreement: KeyAgreementProvider,
    private val role: Role,
    private val tcpPort: Int = 8988,
    private val udpPort: Int = 8989
) : StreamTransport(keyAgreement, winsFloorTies = role == Role.HOST) {

    enum class Role { HOST, CLIENT }

    override val kind: TransportKind = TransportKind.BLUETOOTH_RFCOMM // Using a dummy kind or we should add LAN to TransportKind

    private var serverSocket: ServerSocket? = null
    private var isHosting = AtomicBoolean(false)
    private var beaconThread: Thread? = null

    override fun openLink(timeoutMs: Long): Link {
        return when (role) {
            Role.HOST -> host(timeoutMs)
            Role.CLIENT -> join(timeoutMs)
        }
    }

    private fun host(timeoutMs: Long): Link {
        val server = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(tcpPort))
            soTimeout = timeoutMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
        serverSocket = server
        isHosting.set(true)
        
        // Start UDP beacon
        beaconThread = thread {
            try {
                val socket = DatagramSocket()
                socket.broadcast = true
                val payload = "iTantra-Host".toByteArray()
                val packet = DatagramPacket(payload, payload.size, InetAddress.getByName("255.255.255.255"), udpPort)
                
                while (isHosting.get()) {
                    socket.send(packet)
                    Thread.sleep(1000)
                }
                socket.close()
            } catch (e: Exception) {
                // Ignore beacon errors
            }
        }

        return try {
            val socket = server.accept()
            isHosting.set(false)
            server.close()
            serverSocket = null
            socket.toLink("Hotspot Peer", socket.inetAddress?.hostAddress ?: "unknown")
        } catch (e: Exception) {
            isHosting.set(false)
            runCatching { server.close() }
            serverSocket = null
            throw TransportException("No peer joined the hotspot within ${timeoutMs}ms", e)
        }
    }

    private fun join(timeoutMs: Long): Link {
        val deadline = System.currentTimeMillis() + timeoutMs
        var hostIp: String? = null
        
        // Listen for UDP beacon
        try {
            val udpSocket = DatagramSocket(udpPort).apply {
                soTimeout = 5000
                reuseAddress = true
            }
            val buffer = ByteArray(256)
            val packet = DatagramPacket(buffer, buffer.size)
            
            while (System.currentTimeMillis() < deadline) {
                try {
                    udpSocket.receive(packet)
                    val message = String(packet.data, 0, packet.length)
                    if (message == "iTantra-Host") {
                        hostIp = packet.address.hostAddress
                        break
                    }
                } catch (e: Exception) {
                    // Timeout, keep listening until deadline
                }
            }
            udpSocket.close()
        } catch (e: Exception) {
            throw TransportException("Failed to bind UDP port for discovery", e)
        }
        
        if (hostIp == null) {
            throw TransportException("Could not find Host on the local network")
        }

        val socket = Socket()
        return try {
            socket.bind(null)
            socket.connect(
                InetSocketAddress(hostIp, tcpPort),
                (deadline - System.currentTimeMillis()).coerceAtLeast(0).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            )
            socket.toLink("Hotspot Host", hostIp)
        } catch (e: Exception) {
            runCatching { socket.close() }
            throw TransportException("Could not connect to Host at $hostIp:$tcpPort", e)
        }
    }

    private fun Socket.toLink(peerName: String, peerAddress: String): Link = Link(
        input = getInputStream(),
        output = getOutputStream(),
        peerName = peerName,
        peerAddress = peerAddress,
        closer = { runCatching { close() } },
    )

    override fun disconnect() {
        isHosting.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        super.disconnect()
    }
}
