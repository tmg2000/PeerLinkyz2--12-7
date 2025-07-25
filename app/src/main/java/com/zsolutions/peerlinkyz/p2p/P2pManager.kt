package com.zsolutions.peerlinkyz.p2p

import android.content.Context
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import io.ktor.websocket.DefaultWebSocketSession
import java.util.concurrent.TimeUnit
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import io.ktor.client.*
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets

class P2pManager(private val context: Context, private val scope: CoroutineScope) {

    private var server: NettyApplicationEngine? = null
    private val connections = Collections.synchronizedSet<DefaultWebSocketSession>(LinkedHashSet())
    private val messageChannel = Channel<String>(Channel.UNLIMITED)

    private val workingTorService = WorkingTorService(context, scope)
    var p2pClient: P2pClient? = null
        private set
    private val _onionAddress = MutableStateFlow<String?>(null)
    val onionAddress: SharedFlow<String?> = _onionAddress.asSharedFlow()

    private val _torStatus = MutableStateFlow("Stopped")
    val torStatusFlow: StateFlow<String> = _torStatus
    
    private val _connectionStatus = MutableStateFlow("Disconnected")
    val connectionStatus: StateFlow<String> = _connectionStatus
    
    private var connectionMonitorJob: kotlinx.coroutines.Job? = null

    init {
        // Observe working Tor service onion address changes
        scope.launch {
            workingTorService.onionAddress.collect { address ->
                Log.d("P2pManager", "Working Tor service onion address: $address")
                _onionAddress.value = address
            }
        }
        
        // Observe Tor status changes
        scope.launch {
            workingTorService.status.collect { status ->
                Log.d("P2pManager", "Tor status: $status")
                _torStatus.value = status
                if (status == "Tor Ready") {
                    ensureP2pClientReady()
                }
            }
        }
    }

    fun start() {
        try {
            // Start working Tor service
            Log.d("P2pManager", "Starting working Tor service...")
            workingTorService.start()

            scope.launch(Dispatchers.IO) {
                try {
                    server = embeddedServer(Netty, port = 8080, host = "127.0.0.1") {
                        install(WebSockets) {
                            maxFrameSize = Long.MAX_VALUE
                        }
                        routing {
                            webSocket("/chat") {
                                connections.add(this)
                                Log.d("P2pManager", "New WebSocket connection added. Total connections: ${connections.size}")
                                try {
                                    for (frame in incoming) {
                                        if (frame is Frame.Text) {
                                            val text = frame.readText()
                                            Log.d("P2pManager", "Received message: $text")
                                            messageChannel.send(text) // Send to internal channel
                                            // Broadcast to all other connections
                                            for (connection in connections) {
                                                if (connection != this) { // Don't send back to sender
                                                    try {
                                                        connection.send(Frame.Text(text))
                                                    } catch (e: Exception) {
                                                        Log.e("P2pManager", "Failed to send message to connection: ${e.message}")
                                                        connections.remove(connection)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("P2pManager", "WebSocket error: ${e.message}")
                                } finally {
                                    connections.remove(this)
                                    Log.d("P2pManager", "WebSocket connection removed. Total connections: ${connections.size}")
                                }
                            }
                        }
                    }.start(wait = false)
                    Log.d("P2pManager", "WebSocket server started on port 8080")
                } catch (e: Exception) {
                    Log.e("P2pManager", "Failed to start WebSocket server: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("P2pManager", "Failed to register Tor broadcast receiver: ${e.message}")
        }
    }

    fun stop() {
        try {
            connectionMonitorJob?.cancel()
            Log.d("P2pManager", "Connection monitoring stopped")
        } catch (e: Exception) {
            Log.e("P2pManager", "Error stopping connection monitoring: ${e.message}")
        }
        
        try {
            p2pClient?.disconnect()
            p2pClient = null
            Log.d("P2pManager", "P2pClient disconnected and nullified")
        } catch (e: Exception) {
            Log.e("P2pManager", "Error stopping P2pClient: ${e.message}")
        }
        
        try {
            server?.stop(1000L, 5000L, TimeUnit.MILLISECONDS)
            Log.d("P2pManager", "WebSocket server stopped")
        } catch (e: Exception) {
            Log.e("P2pManager", "Error stopping WebSocket server: ${e.message}")
        }
        
        try {
            workingTorService.stop()
            Log.d("P2pManager", "Working Tor service stopped")
        } catch (e: Exception) {
            Log.e("P2pManager", "Error stopping working Tor service: ${e.message}")
        }
    }

    fun getPeerAddress(): String? {
        val onion = _onionAddress.value
        if (onion != null) {
            return "ws://$onion:8080/chat"
        }
        return getIpAddressInLocalNetwork()?.let { "ws://$it:8080/chat" } ?: "ws://127.0.0.1:8080/chat"
    }

    private fun getIpAddressInLocalNetwork(): String? {
        val networkInterfaces = NetworkInterface.getNetworkInterfaces()
        while (networkInterfaces.hasMoreElements()) {
            val networkInterface = networkInterfaces.nextElement()
            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (!address.isLoopbackAddress && address is InetAddress && address.hostAddress.indexOf(':') < 0) {
                    return address.hostAddress
                }
            }
        }
        return null
    }

    

    fun observeMessages(): Channel<String> {
        return messageChannel
    }
    
    fun getTorStatus(): String {
        return workingTorService.status.value
    }
    
    fun getTorBootstrapProgress(): Int {
        return workingTorService.bootstrapProgress.value
    }
    
    fun isTorReady(): Boolean {
        return workingTorService.status.value == "Tor Ready"
    }
    
    
    fun ensureP2pClientReady() {
        if (p2pClient == null || !isTorReady()) {
            initializeP2pClient()
        }
        startConnectionMonitoring()
    }
    
    private fun initializeP2pClient() {
        try {
            // Clean up existing client if any
            p2pClient?.disconnect()
            
            val torProxy = workingTorService.getSocksProxy()
            val httpClient = HttpClient(OkHttp) {
                engine {
                    proxy = torProxy
                }
                install(ClientWebSockets)
            }
            p2pClient = P2pClient(scope, httpClient)
            _connectionStatus.value = "Initialized"
            Log.d("P2pManager", "P2pClient initialized with Tor proxy")
        } catch (e: Exception) {
            Log.e("P2pManager", "Failed to initialize P2pClient: ${e.message}")
            _connectionStatus.value = "Failed"
        }
    }
    
    private fun startConnectionMonitoring() {
        connectionMonitorJob?.cancel()
        connectionMonitorJob = scope.launch {
            while (isActive) {
                try {
                    val client = p2pClient
                    if (client == null) {
                        _connectionStatus.value = "No Client"
                        if (isTorReady()) {
                            Log.d("P2pManager", "P2pClient is null but Tor is ready, reinitializing...")
                            initializeP2pClient()
                        }
                    } else if (!client.isConnected()) {
                        _connectionStatus.value = "Disconnected"
                        Log.d("P2pManager", "P2pClient disconnected, checking if reinitialization needed...")
                        
                        // If Tor is ready but client is disconnected, create new instance
                        if (isTorReady()) {
                            Log.d("P2pManager", "Tor is ready, creating new P2pClient instance...")
                            initializeP2pClient()
                        }
                    } else {
                        _connectionStatus.value = "Connected"
                    }
                    
                    kotlinx.coroutines.delay(5000) // Check every 5 seconds
                } catch (e: Exception) {
                    Log.e("P2pManager", "Error in connection monitoring: ${e.message}")
                    kotlinx.coroutines.delay(10000) // Wait longer on error
                }
            }
        }
    }
    
    fun forceReconnect() {
        Log.d("P2pManager", "Force reconnect requested")
        if (isTorReady()) {
            initializeP2pClient()
        } else {
            Log.w("P2pManager", "Cannot force reconnect - Tor not ready")
        }
    }
}
