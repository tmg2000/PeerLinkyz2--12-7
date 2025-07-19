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

class P2pClient(
    private val scope: CoroutineScope,
    private val httpClient: HttpClient
) {
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
            
            while (shouldReconnect && isActive) {
                isConnecting = true
                try {
                    Log.d("P2pClient", "Attempting to connect to $address (attempt ${retryCount + 1})")
                    httpClient.webSocket(address) {
                        session = this
                        isConnecting = false
                        retryCount = 0 // Reset retry count on successful connection
                        Log.d("P2pClient", "Successfully connected to $address")
                        
                        try {
                            Log.d("P2pClient", "Starting incoming message loop for session: ${this.hashCode()}")
                            for (frame in incoming) {
                                Log.d("P2pClient", "Processing frame: ${frame.javaClass.simpleName}")
                                if (frame is Frame.Text) {
                                    val text = frame.readText()
                                    Log.d("P2pClient", "Received message: $text")
                                    messageChannel.send(text)
                                } else if (frame is Frame.Close) {
                                    Log.d("P2pClient", "Received close frame: ${frame.readReason()}")
                                    break
                                } else if (frame is Frame.Ping) {
                                    Log.d("P2pClient", "Received ping frame")
                                } else if (frame is Frame.Pong) {
                                    Log.d("P2pClient", "Received pong frame")
                                }
                            }
                            Log.d("P2pClient", "Incoming message loop ended for session: ${this.hashCode()}")
                        } catch (e: Exception) {
                            Log.e("P2pClient", "Error in message loop: ${e.message}", e)
                        } finally {
                            Log.d("P2pClient", "Finally block executed for session: ${this.hashCode()}, shouldReconnect: $shouldReconnect")
                            // Only set session to null if we should not reconnect
                            if (!shouldReconnect) {
                                session = null
                                Log.d("P2pClient", "WebSocket session closed - reconnection disabled")
                            } else {
                                Log.d("P2pClient", "WebSocket session ended but will reconnect, preserving session reference")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("P2pClient", "Connection failed (attempt ${retryCount + 1}): ${e.message}")
                    // Only set session to null if we should not reconnect
                    if (!shouldReconnect) {
                        session = null
                        Log.d("P2pClient", "Connection failed - reconnection disabled, session nullified")
                    } else {
                        Log.d("P2pClient", "Connection failed but will reconnect, preserving session reference")
                    }
                    isConnecting = false
                    retryCount++
                    
                    if (shouldReconnect) {
                        val delayMs = minOf(30000, (1000 * retryCount).toLong()) // Cap at 30 seconds
                        Log.d("P2pClient", "Retrying connection in ${delayMs}ms")
                        delay(delayMs)
                    }
                }
            }
            
            // Infinite retry with exponential backoff - only stop if shouldReconnect is false
            Log.d("P2pClient", "Connection loop ended for $address")
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
