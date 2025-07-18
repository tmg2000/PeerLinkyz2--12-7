package com.zsolutions.peerlinkyz.p2p

import android.util.Log
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class P2pClient(private val scope: CoroutineScope) {

    private val client = HttpClient { 
        install(WebSockets) {
            maxFrameSize = Long.MAX_VALUE
        }
    }
    private val messageChannel = Channel<String>(Channel.UNLIMITED)
    private var session: WebSocketSession? = null
    private var connectionJob: Job? = null
    private var isConnecting = false
    private var shouldReconnect = true

    fun start(address: String) {
        if (isConnecting) {
            Log.d("P2pClient", "Already connecting to $address")
            return
        }
        
        shouldReconnect = true
        connectionJob = scope.launch(Dispatchers.IO) {
            var retryCount = 0
            val maxRetries = 5
            
            while (shouldReconnect && retryCount < maxRetries && isActive) {
                isConnecting = true
                try {
                    Log.d("P2pClient", "Attempting to connect to $address (attempt ${retryCount + 1})")
                    client.webSocket(address) {
                        session = this
                        isConnecting = false
                        retryCount = 0 // Reset retry count on successful connection
                        Log.d("P2pClient", "Successfully connected to $address")
                        
                        try {
                            for (frame in incoming) {
                                if (frame is Frame.Text) {
                                    val text = frame.readText()
                                    Log.d("P2pClient", "Received message: $text")
                                    messageChannel.send(text)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("P2pClient", "Error receiving message: ${e.message}")
                        } finally {
                            session = null
                            Log.d("P2pClient", "WebSocket session closed")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("P2pClient", "Connection failed (attempt ${retryCount + 1}): ${e.message}")
                    session = null
                    isConnecting = false
                    retryCount++
                    
                    if (retryCount < maxRetries && shouldReconnect) {
                        val delayMs = (1000 * retryCount).toLong() // Exponential backoff
                        Log.d("P2pClient", "Retrying connection in ${delayMs}ms")
                        delay(delayMs)
                    }
                }
            }
            
            if (retryCount >= maxRetries) {
                Log.e("P2pClient", "Max retry attempts reached for $address")
            }
            isConnecting = false
        }
    }

    suspend fun sendMessage(message: String) {
        try {
            val currentSession = session
            if (currentSession != null && currentSession.isActive) {
                currentSession.send(Frame.Text(message))
                Log.d("P2pClient", "Message sent: $message")
            } else {
                Log.w("P2pClient", "Cannot send message - no active session")
            }
        } catch (e: Exception) {
            Log.e("P2pClient", "Failed to send message: ${e.message}")
        }
    }

    fun observeMessages(): Channel<String> {
        return messageChannel
    }

    fun isConnected(): Boolean {
        return session?.isActive == true
    }

    fun disconnect() {
        shouldReconnect = false
        connectionJob?.cancel()
        session?.let { 
            scope.launch {
                try {
                    it.close()
                } catch (e: Exception) {
                    Log.e("P2pClient", "Error closing session: ${e.message}")
                }
            }
        }
        session = null
        Log.d("P2pClient", "Disconnected")
    }
}