package com.zsolutions.peerlinkyz.p2p

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.Socket
import java.util.concurrent.TimeUnit

class TorService(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning
    
    private val _onionAddress = MutableStateFlow<String?>(null)
    val onionAddress: StateFlow<String?> = _onionAddress
    
    private val _status = MutableStateFlow("Stopped")
    val status: StateFlow<String> = _status

    private val _bootstrapProgress = MutableStateFlow(0)
    val bootstrapProgress: StateFlow<Int> = _bootstrapProgress
    
    private val torDataDir = File(context.filesDir, "tor_data")
    private val torConfigFile = File(torDataDir, "torrc")
    private val hiddenServiceDir = File(torDataDir, "hidden_service")
    private val torBinary = File(context.filesDir, "tor")
    
    private var torProcess: Process? = null
    
    fun start() {
        scope.launch(Dispatchers.IO) {
            try {
                _status.value = "Starting Tor..."
                Log.d("TorService", "Starting Tor service...")
                
                setupTorEnvironment()
                extractTorBinary()
                createTorConfig()
                startTorProcess()
                
                // Wait for Tor to start
                if (waitForTorStart()) {
                    _isRunning.value = true
                    _status.value = "Running"
                    
                    // Get onion address
                    getOnionAddress()?.let { address ->
                        _onionAddress.value = address
                        Log.d("TorService", "Onion address: $address")
                    }
                } else {
                    _status.value = "Failed to start"
                    throw IOException("Tor failed to start")
                }
                
            } catch (e: Exception) {
                Log.e("TorService", "Failed to start Tor: ${e.message}", e)
                _status.value = "Error: ${e.message}"
                _isRunning.value = false
            }
        }
    }
    
    fun stop() {
        scope.launch(Dispatchers.IO) {
            try {
                _status.value = "Stopping..."
                torProcess?.destroyForcibly()
                torProcess?.waitFor(5, TimeUnit.SECONDS)
                torProcess = null
                _isRunning.value = false
                _onionAddress.value = null
                _status.value = "Stopped"
                Log.d("TorService", "Tor service stopped")
            } catch (e: Exception) {
                Log.e("TorService", "Error stopping Tor: ${e.message}", e)
                _status.value = "Error stopping"
            }
        }
    }
    
    private fun setupTorEnvironment() {
        if (!torDataDir.exists()) {
            torDataDir.mkdirs()
        }
        if (!hiddenServiceDir.exists()) {
            hiddenServiceDir.mkdirs()
        }
    }
    
    private fun extractTorBinary() {
        if (torBinary.exists()) {
            Log.d("TorService", "Tor binary already exists, skipping extraction.")
            return
        }

        val abi = android.os.Build.SUPPORTED_ABIS[0] // Get the primary ABI
        val assetPath = "tor_binaries/$abi/tor" // Assuming binaries are in assets/tor_binaries/abi/tor

        try {
            context.assets.open(assetPath).use { input ->
                FileOutputStream(torBinary).use { output ->
                    input.copyTo(output)
                }
            }
            // Set executable permissions
            Runtime.getRuntime().exec("chmod 700 ${torBinary.absolutePath}").waitFor()
            Log.d("TorService", "Tor binary extracted and set as executable: ${torBinary.absolutePath}")
        } catch (e: Exception) {
            Log.e("TorService", "Failed to extract Tor binary: ${e.message}", e)
            throw IOException("Failed to extract Tor binary", e)
        }
    }
    
    private fun createTorConfig() {
        val config = """
            # Tor configuration for PeerLinkyz
            DataDirectory ${torDataDir.absolutePath}
            SocksPort 9050
            ControlPort 9051
            
            # Hidden service configuration
            HiddenServiceDir ${hiddenServiceDir.absolutePath}
            HiddenServicePort 80 127.0.0.1:8080
            HiddenServiceVersion 3
            
            # Logging
            Log notice file ${torDataDir.absolutePath}/notices.log
            Log info file ${torDataDir.absolutePath}/info.log
            
            # Security settings
            CookieAuthentication 1
            CookieAuthFileGroupReadable 1
            
            # Network settings
            ClientOnly 1
            
        """.trimIndent()
        
        torConfigFile.writeText(config)
        Log.d("TorService", "Created Tor configuration")
    }
    
    private fun startTorProcess() {
        try {
            val command = listOf(
                torBinary.absolutePath,
                "-f", torConfigFile.absolutePath
            )
            torProcess = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            // Read Tor's output for logging and progress
            scope.launch(Dispatchers.IO) {
                torProcess?.inputStream?.bufferedReader()?.useLines { lines ->
                    lines.forEach { line ->
                        Log.d("TorService", "Tor: $line")
                        if (line.contains("Bootstrapped ")) {
                            val progressString = line.substringAfter("Bootstrapped ").substringBefore("%")
                            _bootstrapProgress.value = progressString.toIntOrNull() ?: 0
                            _status.value = "Bootstrapping: ${progressString}%"
                        } else if (line.contains("Bootstrapped 100%")) {
                            _status.value = "Tor Ready"
                        } else if (line.contains("Opened Socks listener on")) {
                            _status.value = "SOCKS Ready"
                        } else if (line.contains("Dormancy: No")) {
                            _status.value = "Connected"
                        }
                    }
                }
            }
            Log.d("TorService", "Tor process started with command: ${command.joinToString(" ")}")
        } catch (e: Exception) {
            Log.e("TorService", "Failed to start Tor process: ${e.message}", e)
            throw IOException("Failed to start Tor process", e)
        }
    }

    private suspend fun waitForTorStart(): Boolean {
        var attempts = 0
        val maxAttempts = 60 // Increased attempts for real Tor
        val socksPort = 9050

        while (attempts < maxAttempts) {
            try {
                withContext(Dispatchers.IO) {
                    Socket().use { socket ->
                        socket.connect(java.net.InetSocketAddress("127.0.0.1", socksPort), 1000)
                    }
                }
                Log.d("TorService", "Tor SOCKS proxy is available on port $socksPort")
                return true
            } catch (e: IOException) {
                Log.d("TorService", "Tor SOCKS proxy not available: ${e.message}. Retrying...")
                attempts++
                kotlinx.coroutines.delay(1000) // Wait for 1 second before retrying
            }
        }
        Log.e("TorService", "Tor failed to start within timeout.")
        return false
    }

    private fun getOnionAddress(): String? {
        val hostnameFile = File(hiddenServiceDir, "hostname")
        return if (hostnameFile.exists()) {
            hostnameFile.readText().trim()
        } else {
            Log.w("TorService", "Hostname file not found: ${hostnameFile.absolutePath}")
            null
        }
    }
    
    fun getSocksProxy(): java.net.Proxy {
        return java.net.Proxy(
            java.net.Proxy.Type.SOCKS,
            java.net.InetSocketAddress("127.0.0.1", 9050)
        )
    }
}