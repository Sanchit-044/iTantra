package `in`.gov.itantra.android.transport

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import `in`.gov.itantra.core.crypto.KeyAgreementProvider
import `in`.gov.itantra.core.transport.TransportException
import `in`.gov.itantra.core.transport.TransportKind
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Module B5, Wi-Fi Direct implementation.
 *
 * Wi-Fi Direct gives a link, not a socket. The sequence is: discover peers, form a
 * group, learn from [WifiP2pInfo] which handset became group owner, and only then open
 * a TCP socket over the resulting interface. The group owner listens; the client dials
 * the owner's address.
 *
 * Role is decided by the framework, not by us -- [WifiP2pInfo.isGroupOwner] is
 * authoritative and cannot be predicted in advance, which is why the socket setup has
 * to happen after group formation rather than being configured up front.
 */
class WifiDirectTransport(
    private val context: Context,
    keyAgreement: KeyAgreementProvider,
    private val role: Role,
    private val port: Int = DEFAULT_PORT,
) : StreamTransport(keyAgreement) {

    enum class Role { HOST, CLIENT }

    override val kind: TransportKind = TransportKind.WIFI_DIRECT

    private val manager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager

    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null
    private var serverSocket: ServerSocket? = null

    @Volatile private var connectionInfo: WifiP2pInfo? = null
    @Volatile private var discoveredPeer: WifiP2pDevice? = null

    @Volatile private var peersFound: CountDownLatch? = null
    @Volatile private var connected: CountDownLatch? = null

    @SuppressLint("MissingPermission") // Location / NEARBY_WIFI_DEVICES checked by the caller.
    override fun openLink(timeoutMs: Long): Link {
        val m = manager ?: throw TransportException("this device has no Wi-Fi Direct support")
        val deadline = System.currentTimeMillis() + timeoutMs

        val ch = m.initialize(context, Looper.getMainLooper(), null)
            ?: throw TransportException("could not initialise a Wi-Fi P2P channel")
        channel = ch

        connected = CountDownLatch(1)
        registerReceiver(m, ch)

        try {
            // Fast-path: Check if already connected via sticky broadcast
            if (connected?.await(1000, TimeUnit.MILLISECONDS) == true) {
                val info = connectionInfo
                if (info != null && info.groupFormed) {
                    val peerName = discoveredPeer?.deviceName ?: "Connected Peer"
                    val peerAddr = discoveredPeer?.deviceAddress ?: "unknown"
                    return if (info.isGroupOwner) {
                        acceptAsOwner(peerName, peerAddr, remaining(deadline))
                    } else {
                        connectToOwner(info, peerName, peerAddr, remaining(deadline))
                    }
                }
            }

            if (role == Role.HOST) {
                val groupCreated = CountDownLatch(1)
                m.createGroup(ch, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() { groupCreated.countDown() }
                    override fun onFailure(reason: Int) { groupCreated.countDown() }
                })
                groupCreated.await(5000, TimeUnit.MILLISECONDS)
                
                if (connected?.await(10000, TimeUnit.MILLISECONDS) == true) {
                    val info = connectionInfo
                    if (info != null && info.groupFormed && info.isGroupOwner) {
                        return acceptAsOwner("Connected Peer", "unknown", remaining(deadline))
                    }
                }
                throw TransportException("Failed to host Wi-Fi Direct group")
            } else {
                // Not connected or fast-path failed. Enter robust retry loop.
                while (System.currentTimeMillis() < deadline) {
                    peersFound = CountDownLatch(1)
                    connected = CountDownLatch(1)
                    
                    discoverPeers(m, ch)
                    
                    if (peersFound?.await(5000, TimeUnit.MILLISECONDS) != true) {
                        // Retry discovery
                        continue
                    }
                    
                    val peer = discoveredPeer ?: continue
                    
                    invite(m, ch, peer)
                    
                    if (connected?.await(15000, TimeUnit.MILLISECONDS) == true) {
                        val info = connectionInfo
                        if (info != null && info.groupFormed && !info.isGroupOwner) {
                            val peerName = peer.deviceName ?: "unknown"
                            val peerAddr = peer.deviceAddress ?: "unknown"
                            return connectToOwner(info, peerName, peerAddr, remaining(deadline))
                        }
                    }
                }
                throw TransportException("Failed to connect to Host within ${timeoutMs}ms master timeout")
            }
        } catch (e: Exception) {
            unregisterReceiver()
            throw e
        }
    }

    private fun remaining(deadline: Long): Long =
        (deadline - System.currentTimeMillis()).coerceAtLeast(0)

    @SuppressLint("MissingPermission")
    private fun discoverPeers(m: WifiP2pManager, ch: WifiP2pManager.Channel) {
        m.discoverPeers(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() = Unit
            override fun onFailure(reason: Int) {
                // Surface rather than silently retry: repeated discovery on a device
                // whose Wi-Fi is off or whose location permission was denied will never
                // succeed, and hiding that wastes field-test time.
                peersFound?.countDown()
                connectionInfo = null
                lastFailureReason = reason
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun invite(m: WifiP2pManager, ch: WifiP2pManager.Channel, peer: WifiP2pDevice) {
        val config = WifiP2pConfig().apply {
            deviceAddress = peer.deviceAddress
            // Nudge group ownership toward whichever side invites, for predictability.
            groupOwnerIntent = GROUP_OWNER_INTENT
        }
        m.connect(ch, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() = Unit
            override fun onFailure(reason: Int) {
                lastFailureReason = reason
                connected?.countDown()
            }
        })
    }

    private fun acceptAsOwner(peerName: String, peerAddress: String, timeoutMs: Long): Link {
        val server = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(port))
            soTimeout = timeoutMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
        serverSocket = server
        return try {
            val socket = server.accept()
            server.close()
            serverSocket = null
            socket.toLink(peerName, peerAddress)
        } catch (e: Exception) {
            runCatching { server.close() }
            serverSocket = null
            throw TransportException("peer did not open a socket within ${timeoutMs}ms", e)
        }
    }

    private fun connectToOwner(info: WifiP2pInfo, peerName: String, peerAddress: String, timeoutMs: Long): Link {
        val ownerAddress = info.groupOwnerAddress
            ?: throw TransportException("group owner address was not provided")
        val socket = Socket()
        return try {
            socket.bind(null)
            socket.connect(
                InetSocketAddress(ownerAddress, port),
                timeoutMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            )
            socket.toLink(peerName, peerAddress)
        } catch (e: Exception) {
            runCatching { socket.close() }
            throw TransportException("could not reach the group owner at $ownerAddress:$port", e)
        }
    }

    private fun Socket.toLink(peerName: String, peerAddress: String): Link = Link(
        input = getInputStream(),
        output = getOutputStream(),
        peerName = peerName,
        peerAddress = peerAddress,
        closer = { runCatching { close() } },
    )

    @SuppressLint("MissingPermission")
    private fun registerReceiver(m: WifiP2pManager, ch: WifiP2pManager.Channel) {
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        }
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION ->
                        m.requestPeers(ch) { peers ->
                            // Look for a peer named "iTantra..." to avoid connecting to random smart TVs
                            val candidate = peers.deviceList.firstOrNull { 
                                it.deviceName.contains("iTantra", ignoreCase = true) 
                            } ?: peers.deviceList.firstOrNull() // fallback to first if none match
                            
                            if (candidate != null) {
                                discoveredPeer = candidate
                                peersFound?.countDown()
                            }
                        }

                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION ->
                        m.requestConnectionInfo(ch) { info ->
                            if (info.groupFormed) {
                                connectionInfo = info
                                connected?.countDown()
                            }
                        }
                }
            }
        }
        receiver = r
        // System broadcasts must be received from outside the app's process, so they 
        // must be registered as EXPORTED on Android 13+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(r, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(r, filter)
        }
    }

    private fun unregisterReceiver() {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
    }

    /** Last WifiP2pManager failure code, for diagnostics. See WifiP2pManager constants. */
    @Volatile
    var lastFailureReason: Int? = null
        private set

    @SuppressLint("MissingPermission")
    override fun disconnect() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        unregisterReceiver()
        val m = manager
        val ch = channel
        if (m != null && ch != null) {
            runCatching { m.removeGroup(ch, null) }
        }
        channel = null
        connectionInfo = null
        discoveredPeer = null
        super.disconnect()
    }

    companion object {
        const val DEFAULT_PORT = 8988

        /** 0..15; higher means more likely to become group owner. */
        const val GROUP_OWNER_INTENT = 15
    }
}
