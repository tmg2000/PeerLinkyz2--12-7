package com.zsolutions.peerlinkyz

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zsolutions.peerlinkyz.p2p.P2pManager
import com.zsolutions.peerlinkyz.p2p.P2pClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.Frame
import kotlinx.coroutines.channels.consumeEach
import java.io.Closeable
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.security.PrivateKey
import java.security.PublicKey
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets

class ChatActivity : AppCompatActivity() {

    private var localECDHPrivateKey: PrivateKey? = null
    private var remoteECDHPublicKey: PublicKey? = null
    private var sharedSecret: ByteArray? = null
    private var isKeyExchangeComplete: Boolean = false

    private lateinit var messageAdapter: MessageAdapter
    private lateinit var messageDao: MessageDao
    private lateinit var friendDao: FriendDao
    private val messages = mutableListOf<Message>()
    private var friendId: Int = -1
    private var remotePeerAddress: String? = null
    private var webSocketSession: DefaultClientWebSocketSession? = null
    private var outboxProcessingJob: Job? = null

    private lateinit var p2pManager: P2pManager
    private val p2pClient: P2pClient? get() = p2pManager.getP2pClient()
    private lateinit var cryptoManager: CryptoManager
    private lateinit var outboxRepository: com.zsolutions.peerlinkyz.db.OutboxRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            Toast.makeText(this, "DEBUG: ChatActivity onCreate started", Toast.LENGTH_SHORT).show()
            setContentView(R.layout.activity_chat)
            Toast.makeText(this, "DEBUG: Layout set successfully", Toast.LENGTH_SHORT).show()

            val app = application as? PeerLinkyzApplication
            if (app == null) {
                Toast.makeText(this, "ERROR: Application cast failed", Toast.LENGTH_LONG).show()
                Log.e("ChatActivity", "Application is not PeerLinkyzApplication")
                finish()
                return
            }
            Toast.makeText(this, "DEBUG: Application cast successful", Toast.LENGTH_SHORT).show()
            
            p2pManager = app.p2pManager
            cryptoManager = CryptoManager(applicationContext)
            Toast.makeText(this, "DEBUG: Managers initialized", Toast.LENGTH_SHORT).show()

            friendId = intent.getIntExtra("friendId", -1)
            if (friendId == -1) {
                Toast.makeText(this, "ERROR: Invalid friend ID", Toast.LENGTH_LONG).show()
                Log.e("ChatActivity", "Invalid friendId: $friendId")
                finish()
                return
            }
            Toast.makeText(this, "DEBUG: Friend ID: $friendId", Toast.LENGTH_SHORT).show()
            
            // Restore key exchange state if available
            restoreKeyExchangeState()
            Toast.makeText(this, "DEBUG: Key exchange state restored", Toast.LENGTH_SHORT).show()

            lifecycleScope.launch {
                try {
                    
                    messageDao = AppDatabase.getDatabase(applicationContext).messageDao()
                    
                    friendDao = AppDatabase.getDatabase(applicationContext).friendDao()
                    
                    outboxRepository = com.zsolutions.peerlinkyz.db.OutboxRepository(com.zsolutions.peerlinkyz.db.AppDatabase.getDatabase(applicationContext).outboxDao())
                    
                    val friend = friendDao.getFriendById(friendId)
                    
                    if (friend != null) {
                        Log.d("ChatActivity", "Friend found: ${friend.username}, onionAddress: ${friend.onionAddress}")
                        remotePeerAddress = friend.onionAddress
                        val contactName = friend.username

                        withContext(Dispatchers.Main) {
                            try {
                                
                                val toolbar: Toolbar = findViewById(R.id.toolbar)
                                
                                setSupportActionBar(toolbar)
                                supportActionBar?.setDisplayShowTitleEnabled(false)

                                val toolbarTitle: TextView = findViewById(R.id.toolbarTitle)
                                toolbarTitle.text = contactName

                                val toolbarAvatar: ImageView = findViewById(R.id.toolbarAvatar)
                                toolbarAvatar.setImageResource(R.drawable.ic_launcher_background)

                                val chatRecyclerView: RecyclerView = findViewById(R.id.chatRecyclerView)
                                messageAdapter = MessageAdapter(messages, cryptoManager, sharedSecret) { "" }
                                chatRecyclerView.layoutManager = LinearLayoutManager(this@ChatActivity)
                                chatRecyclerView.adapter = messageAdapter
                                
                                val messageEditText: EditText = findViewById(R.id.messageEditText)
                                val sendButton: ImageView = findViewById(R.id.sendButton)
                                
                                sendButton.setOnClickListener {
                                    val messageText = messageEditText.text.toString().trim()
                                    if (messageText.isNotEmpty()) {
                                        // Commented out for testing - skip key exchange requirement
                                        // if (!isKeyExchangeComplete || sharedSecret == null) {
                                        //     Toast.makeText(this@ChatActivity, "Key exchange not complete. Cannot send message.", Toast.LENGTH_SHORT).show()
                                        //     return@setOnClickListener
                                        // }
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            val friend = friendDao.getFriendById(friendId)
                                            if (friend != null) {
                                                // Commented out for testing - send plain text
                                                // val encryptedMessage = cryptoManager.encrypt(messageText.toByteArray(StandardCharsets.UTF_8), sharedSecret!!)
                                                val encryptedMessage = messageText.toByteArray(StandardCharsets.UTF_8)
                                                val localPeerId = p2pManager.getPeerAddress()
                                                if (localPeerId == null) {
                                                    Log.e("ChatActivity", "Cannot send message: localPeerId is null (Tor may not be ready)")
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(this@ChatActivity, "Cannot send message: Tor not ready", Toast.LENGTH_SHORT).show()
                                                    }
                                                    return@launch
                                                }
                                                val outboxMessage = com.zsolutions.peerlinkyz.db.OutboxMessage(
                                                    senderOnionAddress = localPeerId,
                                                    recipientOnionAddress = friend.onionAddress,
                                                    message = encryptedMessage,
                                                    sent = false
                                                )
                                                val outboxMessageId = outboxRepository.addMessage(outboxMessage)

                                                val newMessage = Message(friendId = friendId, data = encryptedMessage, isSent = true)
                                                messageDao.insertMessage(newMessage)

                                                withContext(Dispatchers.Main) {
                                                    messages.add(newMessage)
                                                    messageAdapter.notifyItemInserted(messages.size - 1)
                                                    chatRecyclerView.scrollToPosition(messages.size - 1)
                                                    messageEditText.text.clear()
                                                }

                                                // Attempt to send immediately
                                                lifecycleScope.launch {
                                                    sendOutboxMessage(outboxMessage.copy(id = outboxMessageId))
                                                }
                                            }
                                        }
                                    }
                                }
                                
                            } catch (e: Exception) {
                                Toast.makeText(this@ChatActivity, "ERROR: UI setup failed: ${e.message}", Toast.LENGTH_LONG).show()
                                Log.e("ChatActivity", "Failed to initialize UI components: ${e.message}", e)
                                finish()
                                return@withContext
                            }
                        }

                        // Start periodic outbox processing
                        outboxProcessingJob = lifecycleScope.launch(Dispatchers.IO) {
                            while (isActive) {
                                processOutbox()
                                delay(10_000)
                            }
                        }

                        // Establish persistent WebSocket connection using P2pClient
                        remotePeerAddress?.let { address ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(this@ChatActivity, "DEBUG: Starting connection to $address", Toast.LENGTH_SHORT).show()
                                    }
                                    
                                    // Wait for Tor to be ready with retry logic
                                    var torReadyAttempts = 0
                                    while (!p2pManager.isTorReady() && torReadyAttempts < 5) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(this@ChatActivity, "DEBUG: Waiting for Tor (${torReadyAttempts + 1}/5)", Toast.LENGTH_SHORT).show()
                                        }
                                        delay(2000)
                                        torReadyAttempts++
                                    }
                                    
                                    if (!p2pManager.isTorReady()) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(this@ChatActivity, "ERROR: Tor timeout after 5 attempts", Toast.LENGTH_LONG).show()
                                        }
                                        return@launch
                                    }
                                    
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(this@ChatActivity, "DEBUG: Tor is ready", Toast.LENGTH_SHORT).show()
                                    }
                                    
                                    val client = p2pClient
                                    if (client == null) {
                                        Log.e("ChatActivity", "P2pClient is null after Tor ready check")
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(this@ChatActivity, "ERROR: P2pClient not initialized", Toast.LENGTH_LONG).show()
                                        }
                                        return@launch
                                    }
                                    
                                    val fullAddress = if (!address.startsWith("ws://") && !address.startsWith("wss://")) {
                                        "ws://$address"
                                    } else {
                                        address
                                    }
                                    
                                    Log.d("ChatActivity", "Starting P2pClient connection to: $fullAddress")
                                    Log.d("ChatActivity", "Tor status: ${p2pManager.getTorStatus()}, Bootstrap: ${p2pManager.getTorBootstrapProgress()}%")
                                    
                                    val localPeerId = p2pManager.getPeerAddress()
                                    Log.d("ChatActivity", "Local peer address: $localPeerId")
                                    
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(this@ChatActivity, "DEBUG: Starting WebSocket connection", Toast.LENGTH_SHORT).show()
                                    }
                                    
                                    client.start(fullAddress)
                                    
                                    // Determine initiator and start handshake
                                    val remotePeerId = friend.onionAddress
                                    
                                    if (localPeerId == null || localPeerId.isEmpty()) {
                                        Log.w("ChatActivity", "Local peer address not available yet (Tor may not be ready)")
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(this@ChatActivity, "DEBUG: Local peer address not ready", Toast.LENGTH_SHORT).show()
                                        }
                                    } else if (localPeerId < remotePeerId) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(this@ChatActivity, "DEBUG: Initiating handshake", Toast.LENGTH_SHORT).show()
                                        }
                                        initiateHandshake()
                                    }
                                    
                                    // Process any unsent messages from the outbox when connection is established
                                    processOutbox()
                                    
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(this@ChatActivity, "DEBUG: Connection setup complete", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Log.e("ChatActivity", "Error in connection establishment: ${e.message}", e)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(this@ChatActivity, "ERROR: Connection error: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                        
                        // Start observing incoming messages from P2pClient
                        lifecycleScope.launch {
                            val client = p2pClient
                            if (client != null) {
                                client.observeMessages().consumeEach { messageText ->
                                    Log.d("ChatActivity", "Received message from P2pClient: $messageText")
                                    if (messageText.startsWith("FROM:")) {
                                        val parts = messageText.split(" ", limit = 2)
                                        if (parts.size == 2) {
                                            val senderPeerId = parts[0].substringAfter("FROM:")
                                                .replace("/ip4/", "")
                                                .replace("/tcp/", ":")
                                                .replace("/http", "")
                                            val actualMessage = parts[1]

                                            // Only process messages from the current friend
                                            val senderFriend = friendDao.getFriendByOnionAddress(senderPeerId)
                                            if (senderFriend?.id == friendId) {
                                                // Handle key exchange message
                                                if (actualMessage.startsWith("ECDH_PUBLIC_KEY:")) {
                                                    val remotePublicKeyEncoded = actualMessage.substringAfter("ECDH_PUBLIC_KEY:")
                                                    Log.d("ChatActivity", "Received remote public key: $remotePublicKeyEncoded")
                                                    
                                                    try {
                                                        val remotePublicKeyBytes = Base64.getDecoder().decode(remotePublicKeyEncoded)
                                                        val keyFactory = KeyFactory.getInstance("ECDH", "BC")
                                                        val publicKeySpec = X509EncodedKeySpec(remotePublicKeyBytes)
                                                        remoteECDHPublicKey = keyFactory.generatePublic(publicKeySpec)
                                                        
                                                        if (localECDHPrivateKey != null) {
                                                            sharedSecret = cryptoManager.deriveSharedSecret(localECDHPrivateKey!!, remoteECDHPublicKey!!)
                                                            isKeyExchangeComplete = true
                                                            saveKeyExchangeState()
                                                            Log.d("ChatActivity", "Key exchange completed successfully")
                                                            withContext(Dispatchers.Main) {
                                                                Toast.makeText(this@ChatActivity, "Secure connection established", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                        
                                                        // If we are not the initiator, send our public key back
                                                        val localPeerId = p2pManager.getPeerAddress()
                                                        val remotePeerId = friend.onionAddress
                                                        
                                                        if (localPeerId != null && localPeerId.isNotEmpty() && localPeerId > remotePeerId) {
                                                            initiateHandshake() // Send our public key
                                                        } else if (localPeerId == null || localPeerId.isEmpty()) {
                                                            Log.w("ChatActivity", "Cannot respond to key exchange: local peer address not available")
                                                        }
                                                    } catch (e: Exception) {
                                                        Log.e("ChatActivity", "Failed to process remote public key: ${e.message}")
                                                    }
                                                } else {
                                                    // Handle regular message - display plain text for testing
                                                    val decryptedMessage = actualMessage
                                                    
                                                    val newMessage = Message(friendId = friendId, data = decryptedMessage.toByteArray(StandardCharsets.UTF_8), isSent = false)
                                                    messageDao.insertMessage(newMessage)
                                                    
                                                    withContext(Dispatchers.Main) {
                                                        messages.add(newMessage)
                                                        messageAdapter.notifyItemInserted(messages.size - 1)
                                                        findViewById<RecyclerView>(R.id.chatRecyclerView).scrollToPosition(messages.size - 1)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        loadMessages()
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@ChatActivity, "ERROR: Friend not found with ID: $friendId", Toast.LENGTH_LONG).show()
                        }
                        Log.e("ChatActivity", "Friend not found with ID: $friendId")
                        finish()
                    }
                } catch (e: Exception) {
                    Log.e("ChatActivity", "Error in onCreate lifecycle: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ChatActivity, "ERROR: Database/lifecycle error: ${e.message}", Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "CRITICAL ERROR in onCreate: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("ChatActivity", "Critical error in onCreate: ${e.message}", e)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        (webSocketSession as? Closeable)?.close()
        outboxProcessingJob?.cancel()
    }

    private fun loadMessages() {
        lifecycleScope.launch {
            messages.clear()
            val storedMessages = messageDao.getMessagesForFriend(friendId)
            messages.addAll(storedMessages)
            messageAdapter.notifyDataSetChanged()
            if (messages.isNotEmpty()) {
                findViewById<RecyclerView>(R.id.chatRecyclerView).scrollToPosition(messages.size - 1)
            }
        }
    }

    private suspend fun initiateHandshake() {
        Log.d("ChatActivity", "Initiating handshake...")
        val keyPair = cryptoManager.generateECDHKeyPair()
        localECDHPrivateKey = keyPair.private
        val publicKeyEncoded = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        Log.d("ChatActivity", "Local Public Key Encoded: $publicKeyEncoded")
        sendPublicKey(publicKeyEncoded)
    }

    private suspend fun sendPublicKey(publicKeyEncoded: String) {
        p2pClient?.sendMessage("ECDH_PUBLIC_KEY:$publicKeyEncoded")
    }

    private suspend fun sendOutboxMessage(outboxMessage: com.zsolutions.peerlinkyz.db.OutboxMessage) {
        withContext(Dispatchers.IO) {
            try {
                val client = p2pClient
                if (client == null) {
                    Log.e("ChatActivity", "P2pClient not initialized")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ChatActivity, "P2pClient not initialized. Message will be retried.", Toast.LENGTH_SHORT).show()
                    }
                    return@withContext
                }
                
                // P2pClient now handles persistent connections automatically
                // No need for manual reconnection attempts

                if (client.isConnected()) {
                    val messageString = String(outboxMessage.message, Charsets.ISO_8859_1)
                    client.sendMessage("FROM:${outboxMessage.senderOnionAddress} $messageString")
                    outboxRepository.markAsSent(outboxMessage.id)
                    Log.d("ChatActivity", "Message sent successfully: ${outboxMessage.id}")
                } else {
                    Log.w("ChatActivity", "P2pClient is not connected")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ChatActivity, "Connection lost. Message will be retried.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatActivity", "Failed to send message: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ChatActivity, "Failed to send message: ${e.message}. Will retry.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun processOutbox() {
        lifecycleScope.launch(Dispatchers.IO) {
            val unsentMessages = outboxRepository.getUnsentMessages()
            unsentMessages.forEach { message ->
                sendOutboxMessage(message)
            }
        }
    }
    
    private fun saveKeyExchangeState() {
        if (isKeyExchangeComplete && sharedSecret != null && localECDHPrivateKey != null && remoteECDHPublicKey != null) {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
            val editor = sharedPreferences.edit()
            sharedSecret?.let { secret ->
                editor.putString("shared_secret_$friendId", Base64.getEncoder().encodeToString(secret))
            }
            localECDHPrivateKey?.let { privateKey ->
                editor.putString("local_private_key_$friendId", Base64.getEncoder().encodeToString(privateKey.encoded))
            }
            remoteECDHPublicKey?.let { publicKey ->
                editor.putString("remote_public_key_$friendId", Base64.getEncoder().encodeToString(publicKey.encoded))
            }
            editor.putBoolean("key_exchange_complete_$friendId", true)
            editor.apply()
            Log.d("ChatActivity", "Key exchange state saved for friend $friendId")
        }
    }
    
    private fun restoreKeyExchangeState() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val isComplete = sharedPreferences.getBoolean("key_exchange_complete_$friendId", false)
        
        if (isComplete) {
            try {
                val sharedSecretEncoded = sharedPreferences.getString("shared_secret_$friendId", null)
                val localPrivateKeyEncoded = sharedPreferences.getString("local_private_key_$friendId", null)
                val remotePublicKeyEncoded = sharedPreferences.getString("remote_public_key_$friendId", null)
                
                if (sharedSecretEncoded != null && localPrivateKeyEncoded != null && remotePublicKeyEncoded != null) {
                    sharedSecret = Base64.getDecoder().decode(sharedSecretEncoded)
                    
                    val privateKeyBytes = Base64.getDecoder().decode(localPrivateKeyEncoded)
                    val privateKeySpec = PKCS8EncodedKeySpec(privateKeyBytes)
                    val keyFactory = KeyFactory.getInstance("ECDH", "BC")
                    localECDHPrivateKey = keyFactory.generatePrivate(privateKeySpec)
                    
                    val publicKeyBytes = Base64.getDecoder().decode(remotePublicKeyEncoded)
                    val publicKeySpec = X509EncodedKeySpec(publicKeyBytes)
                    remoteECDHPublicKey = keyFactory.generatePublic(publicKeySpec)
                    
                    isKeyExchangeComplete = true
                    Log.d("ChatActivity", "Key exchange state restored for friend $friendId")
                }
            } catch (e: Exception) {
                Log.e("ChatActivity", "Failed to restore key exchange state: ${e.message}")
                // Clear corrupted state
                clearKeyExchangeState()
            }
        }
    }
    
    private fun clearKeyExchangeState() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val editor = sharedPreferences.edit()
        editor.remove("shared_secret_$friendId")
        editor.remove("local_private_key_$friendId")
        editor.remove("remote_public_key_$friendId")
        editor.remove("key_exchange_complete_$friendId")
        editor.apply()
        
        isKeyExchangeComplete = false
        sharedSecret = null
        localECDHPrivateKey = null
        remoteECDHPublicKey = null
    }
}
