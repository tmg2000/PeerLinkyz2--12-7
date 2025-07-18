package com.zsolutions.peerlinkyz

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import java.util.UUID
import androidx.multidex.MultiDexApplication
import com.zsolutions.peerlinkyz.p2p.P2pManager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.*
import java.io.Closeable
import io.ktor.client.*
import io.ktor.client.engine.okhttp.OkHttp
import java.net.Proxy
import java.net.InetSocketAddress
import io.ktor.client.plugins.websocket.*

class PeerLinkyzApplication : MultiDexApplication() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    
    lateinit var p2pManager: P2pManager
    var httpClient: HttpClient? = null // Make it nullable to allow async init

    override fun onCreate() {
        super.onCreate()

        p2pManager = P2pManager(this, applicationScope)

        // Initialize httpClient when Tor is ready
        applicationScope.launch(Dispatchers.IO) {
            p2pManager.torStatusFlow
                .onEach { status: String ->
                    if (status == "Tor Ready" && httpClient == null) {
                        try {
                            httpClient = HttpClient(OkHttp) {
                                engine {
                                    proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 9050))
                                }
                                install(WebSockets) {
                                    maxFrameSize = Long.MAX_VALUE
                                }
                            }
                            android.util.Log.d("PeerLinkyzApplication", "HttpClient initialized with Tor proxy")
                        } catch (e: Exception) {
                            android.util.Log.e("PeerLinkyzApplication", "Failed to initialize HttpClient with Tor proxy: ${e.message}")
                            // Fallback to direct connection if Tor is not available
                            httpClient = HttpClient(OkHttp) {
                                install(WebSockets) {
                                    maxFrameSize = Long.MAX_VALUE
                                }
                            }
                            android.util.Log.d("PeerLinkyzApplication", "HttpClient initialized without Tor proxy as fallback")
                        }
                    }
                }
                .collect()
        }

        p2pManager.start()

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val theme = sharedPreferences.getString("theme_preference", "system")
        AppCompatDelegate.setDefaultNightMode(
            when (theme) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
        // Generate and store a unique user ID if it doesn't exist
        if (!sharedPreferences.contains("user_id")) {
            val userId = UUID.randomUUID().toString()
            sharedPreferences.edit().putString("user_id", userId).apply()
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        
        p2pManager.stop()
        httpClient?.let { (it as? Closeable)?.close() }
    }
}