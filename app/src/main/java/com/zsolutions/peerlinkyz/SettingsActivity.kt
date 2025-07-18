package com.zsolutions.peerlinkyz

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentResult
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.BarcodeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.zsolutions.peerlinkyz.AppDatabase
import com.zsolutions.peerlinkyz.Setting
import com.zsolutions.peerlinkyz.SettingDao
import com.zsolutions.peerlinkyz.p2p.P2pManager
import com.zsolutions.peerlinkyz.BuildConfig
import androidx.activity.result.contract.ActivityResultContracts


class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Add smooth exit animation
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            }
        })
    }

    

    class SettingsFragment : PreferenceFragmentCompat() {

        private val settingDao: SettingDao by lazy { AppDatabase.getDatabase(requireContext()).settingDao() }
        private val PICK_IMAGE_REQUEST = 1

        // Activity Result Launchers
        private val qrScanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val intentResult = IntentIntegrator.parseActivityResult(result.resultCode, result.data)
            if (intentResult != null && intentResult.contents != null) {
                // Add friend to database
                lifecycleScope.launch {
                    val res = addFriendFromQrJson(requireContext(), intentResult.contents)
                    res.onSuccess { username ->
                        requireActivity().setResult(android.app.Activity.RESULT_OK, android.content.Intent().putExtra("friend_added", true))
                        Toast.makeText(requireContext(), "Friend added: $username", Toast.LENGTH_LONG).show()
                    }.onFailure { e ->
                        Toast.makeText(requireContext(), "Error adding friend: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } else if (intentResult != null) {
                Toast.makeText(requireContext(), "Scan cancelled", Toast.LENGTH_LONG).show()
            }
        }
        private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK && result.data != null && result.data!!.data != null) {
                val uri = result.data!!.data!!
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val bitmap = BitmapFactory.decodeStream(requireContext().contentResolver.openInputStream(uri))
                        val pixels = IntArray(bitmap.width * bitmap.height).also {
                            bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                        }
                        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
                        val binaryBitmap = com.google.zxing.BinaryBitmap(HybridBinarizer(source))
                        val result = QRCodeReader().decode(binaryBitmap)
                        val qrText = result.text
                        val res = addFriendFromQrJson(requireContext(), qrText)
                        withContext(Dispatchers.Main) {
                            res.onSuccess { username ->
                                requireActivity().setResult(android.app.Activity.RESULT_OK, android.content.Intent().putExtra("friend_added", true))
                                Toast.makeText(requireContext(), "Friend added from gallery: $username", Toast.LENGTH_LONG).show()
                            }.onFailure { e ->
                                Toast.makeText(requireContext(), "Error adding friend: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Error scanning QR Code from image", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.settings_preferences, rootKey)

            // Initially disable all preferences except username
            togglePreferences(false)

            // Load the username and update the UI
            lifecycleScope.launch(Dispatchers.IO) {
                val username = settingDao.get("username_preference")
                withContext(Dispatchers.Main) {
                    if (!username.isNullOrBlank()) {
                        findPreference<Preference>("username_preference")?.title = "Welcome $username"
                        togglePreferences(true)
                    } else {
                        findPreference<Preference>("username_preference")?.title = "Welcome User"
                    }
                }
            }

            // Username change listener
            findPreference<Preference>("username_preference")?.setOnPreferenceChangeListener { pref, newVal ->
                val newUsername = newVal.toString()
                lifecycleScope.launch(Dispatchers.IO) {
                    settingDao.insert(Setting(pref.key, newUsername))
                    withContext(Dispatchers.Main) {
                        pref.title = "Welcome $newUsername"
                        togglePreferences(true)
                    }
                }
                true
            }

            // Generate QR code action
            findPreference<Preference>("generate_qr_code")?.setOnPreferenceClickListener {
                lifecycleScope.launch(Dispatchers.IO) {
                    val user = settingDao.get("username_preference") ?: ""
                    if (user.isBlank()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(),
                                "Please enter a username first.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            // Show loading dialog
                            val progressDialog = AlertDialog.Builder(requireContext())
                                .setView(R.layout.dialog_loading)
                                .setCancelable(false)
                                .create()
                            progressDialog.show()
                            
                            // Generate QR code in background
                            generateAndShowQrCode(user, progressDialog)
                        }
                    }
                }
                true
            }

            // Scan friend via QR
            findPreference<Preference>("add_friend_qr_scan")?.setOnPreferenceClickListener {
                val integrator = IntentIntegrator.forSupportFragment(this)
                integrator.setPrompt("Scan a friend's QR code")
                qrScanLauncher.launch(integrator.createScanIntent())
                true
            }

            // Scan QR from gallery
            findPreference<Preference>("scan_qr_from_gallery")?.setOnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
                imagePickerLauncher.launch(intent)
                true
            }

            findPreference<Preference>("theme_preference")?.setOnPreferenceChangeListener { _, newValue ->
                val theme = newValue as String
                AppCompatDelegate.setDefaultNightMode(
                    when (theme) {
                        "light" -> AppCompatDelegate.MODE_NIGHT_NO
                        "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    }
                )
                true
            }

            
        }

        private fun generateAndShowQrCode(username: String, progressDialog: AlertDialog? = null) {
            // Generate only once and cache in settings
            lifecycleScope.launch(Dispatchers.IO) {
                val existing = settingDao.get("my_qr_code_content")
                val content = existing ?: run {
                    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
                    val userId = sharedPreferences.getString("user_id", "")
                    val app = requireActivity().application as PeerLinkyzApplication
                    val peerId = app.p2pManager.getPeerAddress()

                    JSONObject().apply {
                        put("username", username)
                        put("userId", userId)
                        put("peerId", peerId)
                    }.toString().also {
                        settingDao.insert(Setting("my_qr_code_content", it))
                    }
                }

                try {
                    val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 512, 512)
                    val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565).apply {
                        for (x in 0 until width) for (y in 0 until height) {
                            setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        // Dismiss progress dialog
                        progressDialog?.dismiss()
                        
                        val view = layoutInflater.inflate(R.layout.dialog_qr_code, null)
                        view.findViewById<ImageView>(R.id.qrCodeImageView).setImageBitmap(bmp)
                        
                        val dialog = AlertDialog.Builder(requireContext())
                            .setView(view)
                            .setCancelable(true)
                            .create()
                        
                        // Set up button click listeners
                        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.shareButton).setOnClickListener {
                            val shareIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Scan this QR code to add me as a friend on PeerLinkyz!")
                            }
                            startActivity(Intent.createChooser(shareIntent, "Share QR Code"))
                        }
                        
                        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.closeButton).setOnClickListener {
                            dialog.dismiss()
                        }
                        
                        dialog.show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        progressDialog?.dismiss()
                        Toast.makeText(requireContext(), "Failed to generate QR code", Toast.LENGTH_SHORT).show()
                    }
                    Log.e("SettingsFragment", "QR generation failed", e)
                }
            }
        }

        private fun togglePreferences(enabled: Boolean) {
            findPreference<Preference>("generate_qr_code")?.isEnabled = enabled
            findPreference<Preference>("add_friend_qr_scan")?.isEnabled = enabled
            findPreference<Preference>("scan_qr_from_gallery")?.isEnabled = enabled
            findPreference<Preference>("theme_preference")?.isEnabled = enabled
            findPreference<Preference>("message_retention_days")?.isEnabled = enabled

            // Enable/disable background sync preference
            
        }

        
    }
}
