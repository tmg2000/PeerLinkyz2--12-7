package com.zsolutions.peerlinkyz.p2p

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TorUtils {
    
    private const val ORBOT_PACKAGE_NAME = "org.torproject.android"
    private const val TOR_SOCKS_PORT = 9050
    private const val TOR_CONTROL_PORT = 9051
    
    fun isOrbotInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(ORBOT_PACKAGE_NAME, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    suspend fun isTorRunning(): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", TOR_SOCKS_PORT), 5000)
                true
            }
        } catch (e: IOException) {
            Log.d("TorUtils", "Tor SOCKS proxy not available: ${e.message}")
            false
        }
    }
    
    fun startOrbot(context: Context) {
        if (isOrbotInstalled(context)) {
            val intent = Intent("org.torproject.android.intent.action.START_TOR")
            context.sendBroadcast(intent)
            Log.d("TorUtils", "Sent start command to Orbot")
        } else {
            Log.w("TorUtils", "Orbot not installed - cannot start Tor")
        }
    }
    
    fun requestOrbotStatus(context: Context) {
        if (isOrbotInstalled(context)) {
            val intent = Intent("org.torproject.android.intent.action.REQUEST_STATUS")
            context.sendBroadcast(intent)
            Log.d("TorUtils", "Requested Orbot status")
        } else {
            Log.w("TorUtils", "Orbot not installed - cannot request status")
        }
    }
    
    fun openOrbotInPlayStore(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse("market://details?id=$ORBOT_PACKAGE_NAME")
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            // Fallback to browser
            val browserIntent = Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("https://play.google.com/store/apps/details?id=$ORBOT_PACKAGE_NAME")
            }
            context.startActivity(browserIntent)
        }
    }
}