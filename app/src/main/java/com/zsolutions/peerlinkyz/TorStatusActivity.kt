package com.zsolutions.peerlinkyz

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.launch

class TorStatusActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var onionAddressText: TextView
    private lateinit var refreshButton: Button
    private var isTorReadyHandled = false // Flag to prevent multiple launches

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create simple layout programmatically
        setContentView(createLayout())

        refreshButton.setOnClickListener {
            updateTorStatus()
        }

        // Update status periodically
        lifecycleScope.launch {
            while (!isTorReadyHandled) { // Loop until Tor is ready and handled
                updateTorStatus()
                kotlinx.coroutines.delay(2000) // Update every 2 seconds
            }
        }

        // Observe onion address changes
        lifecycleScope.launch {
            val app = application as PeerLinkyzApplication
            val p2pManager = app.p2pManager
            p2pManager.onionAddress.collect { address ->
                onionAddressText.text = if (address != null) {
                    "Onion address: $address"
                } else {
                    "Onion address: Not available"
                }
            }
        }
    }

    private fun createLayout(): android.widget.LinearLayout {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // Title
        val title = TextView(this).apply {
            text = "Tor Status"
            textSize = 24f
            setPadding(0, 0, 0, 32)
        }
        layout.addView(title)

        // Status text
        statusText = TextView(this).apply {
            text = "Checking Tor status..."
            textSize = 16f
            setPadding(0, 0, 0, 16)
        }
        layout.addView(statusText)

        // Progress bar
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            setPadding(0, 0, 0, 16)
        }
        layout.addView(progressBar)

        // Onion address
        onionAddressText = TextView(this).apply {
            text = "Onion address: Not available"
            textSize = 14f
            setPadding(0, 0, 0, 32)
        }
        layout.addView(onionAddressText)

        // Refresh button
        refreshButton = Button(this).apply {
            text = "Refresh"
        }
        layout.addView(refreshButton)

        return layout
    }

    private fun updateTorStatus() {
        if (isTorReadyHandled) return // Don't do anything if already handled

        val app = application as PeerLinkyzApplication
        val p2pManager = app.p2pManager

        val status = p2pManager.getTorStatus()
        val progress = p2pManager.getTorBootstrapProgress()
        val isReady = p2pManager.isTorReady()

        statusText.text = "Status: $status"
        progressBar.progress = progress

        

        // Update button text based on status
        refreshButton.text = if (isReady) "Tor Ready" else "Refresh"
        refreshButton.isEnabled = !isReady

        // If Tor is ready, start MainActivity and finish this activity
        if (isReady) {
            isTorReadyHandled = true // Set the flag
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
            val onboardingComplete = sharedPreferences.getBoolean("onboarding_complete", false)

            if (onboardingComplete) {
                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra("socks_host", "127.0.0.1")
                    putExtra("socks_port", 9050)
                }
                startActivity(intent)
            } else {
                val intent = Intent(this, OnboardingActivity::class.java)
                startActivity(intent)
            }
            finish()
        }
    }
}