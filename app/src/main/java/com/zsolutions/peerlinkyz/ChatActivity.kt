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
        setContentView(R.layout.activity_chat)

        val app = application as PeerLinkyzApplication
        p2pManager = app.p2pManager
        cryptoManager = CryptoManager(applicationContext)

        friendId = intent.getIntExtra("friendId", -1)
        if (friendId == -1) {
            finish()
            return
        }
        
        // Restore key exchange state if available
        restoreKeyExchangeState()

        lifecycleScope.launch {
            messageDao = AppDatabase.getDatabase(applicationContext).messageDao()
            friendDao = AppDatabase.getDatabase(applicationContext).friendDao()
            outboxRepository = com.zsolutions.peerlinkyz.db.OutboxRepository(com.zsolutions.peerlinkyz.db.AppDatabase.getDatabase(applicationContext).outboxDao())
            val friend = friendDao.getFriendById(friendId)
            if (friend != null) {
                remotePeerAddress = friend.onionAddress // Assuming onionAddress is the address to connect to

                val contactName = friend.username

                val toolbar: Toolbar = findViewById(R.id.toolbar)
                setSupportActionBar(toolbar)
                supportActionBar?.setDisplayShowTitleEnabled(false)

                val toolbarTitle: TextView = findViewById(R.id.toolbarTitle)
                toolbarTitle.text = contactName

                val toolbarAvatar: ImageView = findViewById(R.id.toolbarAvatar)
                toolbarAvatar.setImageResource(R.drawable.ic_launcher_background) // Placeholder

                val chatRecyclerView: RecyclerView = findViewById(R.id.chatRecyclerView)
                messageAdapter = MessageAdapter(messages, cryptoManager, sharedSecret) { "" }
                chatRecyclerView.layoutManager = LinearLayoutManager(this@ChatActivity)
                chatRecyclerView.adapter = messageAdapter

                val messageEditText: EditText = findViewById(R.id.messageEditText)
                val sendButton: ImageView = findViewById(R.id.sendButton)

                // Start periodic outbox processing
                outboxProcessingJob = lifecycleScope.launch(Dispatchers.IO) {
                    while (isActive) {
                        processOutbox()
                        delay(10_000) // Retry every 10 seconds
                    }
                }

                // Establish persistent WebSocket connection using P2pClient
                remotePeerAddress?.let { address ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        // Wait for Tor to be ready before attempting connection
                        if (!p2pManager.isTorReady()) {
                            Log.d("ChatActivity", "Waiting for Tor to be ready...")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@ChatActivity, "Waiting for Tor to be ready...", Toast.LENGTH_SHORT).show()
                            }
                            // Wait and retry
                            delay(2000)
                            if (!p2pManager.isTorReady()) {
                                Log.w("ChatActivity", "Tor still not ready after waiting")
                                return@launch
                            }
                        }
                        
                        val client = p2pClient
                        if (client == null) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@ChatActivity, "P2pClient not initialized. Tor may not be ready.", Toast.LENGTH_SHORT).show()
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
                        Log.d("ChatActivity", "Local peer address: ${p2pManager.getPeerAddress()}")
                        
                        client.start(fullAddress)
                        
                        // Determine initiator and start handshake
                        val localPeerId = p2pManager.getPeerAddress()
                        val remotePeerId = friend.onionAddress
                        
                        if (localPeerId == null || localPeerId.isEmpty()) {
                            Log.w("ChatActivity", "Local peer address not available yet (Tor may not be ready)")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@ChatActivity, "Waiting for Tor to be ready...", Toast.LENGTH_SHORT).show()
                            }
                        } else if (localPeerId < remotePeerId) {
                            initiateHandshake()
                        }
                        
                        // Start observing incoming messages from P2pClient
                        launch {
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
                                                Log.d("ChatActivity", "Received ECDH_PUBLIC_KEY message.")
                                                val remotePublicKeyEncoded = actualMessage.substringAfter("ECDH_PUBLIC_KEY:")
                                                val publicKeyBytes = Base64.getDecoder().decode(remotePublicKeyEncoded)
                                                val spec = X509EncodedKeySpec(publicKeyBytes)
                                                val keyFactory = KeyFactory.getInstance("ECDH", "BC")
                                                remoteECDHPublicKey = keyFactory.generatePublic(spec)
                                                Log.d("ChatActivity", "Remote Public Key Decoded: ${remoteECDHPublicKey?.encoded?.let { Base64.getEncoder().encodeToString(it) }}")

                                                // If we are not the initiator, send our public key back
                                                val localPeerId = p2pManager.getPeerAddress()
                                                val remotePeerId = friend.onionAddress
                                                
                                                if (localPeerId != null && localPeerId.isNotEmpty() && localPeerId > remotePeerId) {
                                                    initiateHandshake() // Send our public key
                                                } else if (localPeerId == null || localPeerId.isEmpty()) {
                                                    Log.w("ChatActivity", "Cannot respond to key exchange: local peer address not available")
                                                }

                                                localECDHPrivateKey?.let { privateKey ->
                                                    remoteECDHPublicKey?.let { publicKey ->
                                                        Log.d("ChatActivity", "Deriving shared secret...")
                                                        sharedSecret = cryptoManager.deriveSharedSecret(privateKey, publicKey)
                                                        isKeyExchangeComplete = true
                                                        saveKeyExchangeState()
                                                        Log.d("ChatActivity", "Shared Secret Derived. Key exchange complete: $isKeyExchangeComplete")
                                                        withContext(Dispatchers.Main) {
                                                            Toast.makeText(this@ChatActivity, "Key exchange complete!", Toast.LENGTH_SHORT).show()
                                                        }
                                                    } ?: Log.e("ChatActivity", "Remote public key is null after decoding.")
                                                } ?: Log.e("ChatActivity", "Local private key is null.")
                                            } else {
                                                // Commented out for testing - process plain text messages
                                                // } else if (isKeyExchangeComplete && sharedSecret != null) {
                                                //     Log.d("ChatActivity", "Key exchange complete. Attempting to decrypt message.")
                                                //     // Decrypt message using shared secret
                                                //     try {
                                                //         val decryptedMessageBytes = cryptoManager.decrypt(Base64.getDecoder().decode(actualMessage), sharedSecret!!)
                                                //         val decryptedMessage = String(decryptedMessageBytes, StandardCharsets.UTF_8)
                                                Log.d("ChatActivity", "Processing plain text message for testing.")
                                                try {
                                                    // Process as plain text for testing
                                                    val decryptedMessage = actualMessage
                                                    val receivedMessage = Message(friendId = friendId, data = decryptedMessage.toByteArray(StandardCharsets.UTF_8), isSent = false)
                                                    messageDao.insertMessage(receivedMessage)
                                                    withContext(Dispatchers.Main) {
                                                        messages.add(receivedMessage)
                                                        messageAdapter.notifyItemInserted(messages.size - 1)
                                                        chatRecyclerView.scrollToPosition(messages.size - 1)
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e("ChatActivity", "Message processing failed: ${e.message}")
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(this@ChatActivity, "Failed to process message.", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                                // } else {
                                                //     Log.d("ChatActivity", "Key exchange not complete. Message not processed. isKeyExchangeComplete: $isKeyExchangeComplete, sharedSecret: ${sharedSecret != null}")
                                                //     withContext(Dispatchers.Main) {
                                                //         Toast.makeText(this@ChatActivity, "Key exchange not complete. Message not processed.", Toast.LENGTH_SHORT).show()
                                                //     }
                                            }
                                        } else {
                                            Log.d("ChatActivity", "Message from unknown sender or not current friend. SenderPeerId: $senderPeerId, FriendId: $friendId")
                                        }
                                    } else {
                                        Log.d("ChatActivity", "Received message parts size is not 2. Message: $messageText")
                                    }
                                } else {
                                    Log.d("ChatActivity", "Received message does not start with 'FROM:'. Message: $messageText")
                                }
                            }
                        }
                        
                        
                        // Process any unsent messages from the outbox when connection is established
                        processOutbox()
                    }
                }

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

                loadMessages()
            } else {
                Toast.makeText(this@ChatActivity, "Friend not found", Toast.LENGTH_SHORT).show()
                finish()
            }
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
                
                if (!client.isConnected()) {
                    Log.d("ChatActivity", "P2pClient not connected, attempting to reconnect...")
                    remotePeerAddress?.let { address ->
                        val fullAddress = if (!address.startsWith("ws://") && !address.startsWith("wss://")) {
                            "ws://$address"
                        } else {
                            address
                        }
                        client.start(fullAddress)
                        delay(2000)
                    }
                }

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
            editor.putString("shared_secret_$friendId", Base64.getEncoder().encodeToString(sharedSecret!!))
            editor.putString("local_private_key_$friendId", Base64.getEncoder().encodeToString(localECDHPrivateKey!!.encoded))
            editor.putString("remote_public_key_$friendId", Base64.getEncoder().encodeToString(remoteECDHPublicKey!!.encoded))
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
