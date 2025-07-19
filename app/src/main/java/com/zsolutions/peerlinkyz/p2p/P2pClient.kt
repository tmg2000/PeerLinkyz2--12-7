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
                            Log.d("P2pClient", "LOOP_STEP_1: Starting incoming message loop for session: ${this.hashCode()}")
                            Log.d("P2pClient", "LOOP_STEP_2: Session isActive before loop: ${this.isActive}")
                            Log.d("P2pClient", "LOOP_STEP_3: Incoming channel: ${incoming.javaClass.simpleName}")
                            
                            var frameCount = 0
                            for (frame in incoming) {
                                frameCount++
                                Log.d("P2pClient", "LOOP_STEP_4: Processing frame #$frameCount: ${frame.javaClass.simpleName}")
                                Log.d("P2pClient", "LOOP_STEP_5: Session still active during frame processing: ${this.isActive}")
                                
                                if (frame is Frame.Text) {
                                    val text = frame.readText()
                                    Log.d("P2pClient", "LOOP_STEP_6: Received text message: $text")
                                    messageChannel.send(text)
                                    Log.d("P2pClient", "LOOP_STEP_7: Message sent to channel successfully")
                                    Log.d("P2pClient", "LOOP_STEP_8: Session active after processing text: ${this.isActive}")
                                } else if (frame is Frame.Close) {
                                    Log.d("P2pClient", "LOOP_STEP_9: Received close frame: ${frame.readReason()}")
                                    Log.d("P2pClient", "LOOP_STEP_10: Breaking from loop due to close frame")
                                    break
                                } else if (frame is Frame.Ping) {
                                    Log.d("P2pClient", "LOOP_STEP_11: Received ping frame")
                                } else if (frame is Frame.Pong) {
                                    Log.d("P2pClient", "LOOP_STEP_12: Received pong frame")
                                } else {
                                    Log.d("P2pClient", "LOOP_STEP_13: Received unknown frame type: ${frame.javaClass.simpleName}")
                                }
                                
                                Log.d("P2pClient", "LOOP_STEP_14: Completed processing frame #$frameCount")
                                Log.d("P2pClient", "LOOP_STEP_15: About to check for next frame in incoming channel")
                            }
                            Log.d("P2pClient", "LOOP_STEP_16: Incoming message loop ended for session: ${this.hashCode()}")
                            Log.d("P2pClient", "LOOP_STEP_17: Total frames processed: $frameCount")
                            Log.d("P2pClient", "LOOP_STEP_18: Session active at loop end: ${this.isActive}")
                        } catch (e: Exception) {
                            Log.e("P2pClient", "LOOP_ERROR: Error in message loop: ${e.message}", e)
                            Log.e("P2pClient", "LOOP_ERROR: Session active during exception: ${this.isActive}")
                        } finally {
                            Log.d("P2pClient", "FINALLY_STEP_1: Finally block executed for session: ${this.hashCode()}")
                            Log.d("P2pClient", "FINALLY_STEP_2: shouldReconnect: $shouldReconnect")
                            Log.d("P2pClient", "FINALLY_STEP_3: Session active in finally: ${this.isActive}")
                            Log.d("P2pClient", "FINALLY_STEP_4: Current session reference: ${session?.hashCode()}")
                            
                            // Only set session to null if we should not reconnect
                            if (!shouldReconnect) {
                                session = null
                                Log.d("P2pClient", "FINALLY_STEP_5: WebSocket session nullified - reconnection disabled")
                            } else {
                                Log.d("P2pClient", "FINALLY_STEP_5: WebSocket session preserved - will reconnect")
                                Log.d("P2pClient", "FINALLY_STEP_6: Session reference after preservation: ${session?.hashCode()}")
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
            Log.d("P2pClient", "SEND_STEP_1: Starting sendMessage for: $message")
            val currentSession = session
            Log.d("P2pClient", "SEND_STEP_2: Current session: ${currentSession?.hashCode()}")
            Log.d("P2pClient", "SEND_STEP_3: Session isActive: ${currentSession?.isActive}")
            
            if (currentSession != null && currentSession.isActive) {
                Log.d("P2pClient", "SEND_STEP_4: About to send Frame.Text")
                currentSession.send(Frame.Text(message))
                Log.d("P2pClient", "SEND_STEP_5: Frame.Text sent successfully")
                Log.d("P2pClient", "SEND_STEP_6: Checking session after send - isActive: ${currentSession.isActive}")
                Log.d("P2pClient", "SEND_STEP_7: Message sent successfully: $message")
            } else {
                Log.w("P2pClient", "SEND_ERROR: Cannot send message - session null or inactive")
                Log.w("P2pClient", "SEND_ERROR: Session null: ${currentSession == null}")
                Log.w("P2pClient", "SEND_ERROR: Session inactive: ${currentSession?.isActive == false}")
            }
        } catch (e: Exception) {
            Log.e("P2pClient", "SEND_ERROR: Failed to send message: ${e.message}", e)
            Log.e("P2pClient", "SEND_ERROR: Session after exception: ${session?.hashCode()}")
            Log.e("P2pClient", "SEND_ERROR: Session active after exception: ${session?.isActive}")
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
