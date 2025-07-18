package com.zsolutions.peerlinkyz

import android.util.Log

import androidx.appcompat.app.AlertDialog
import com.zsolutions.peerlinkyz.Contact
import com.zsolutions.peerlinkyz.ContactAdapter
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentResult
import kotlinx.coroutines.launch
import org.json.JSONObject
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.widget.TextView
import android.view.View

import android.content.SharedPreferences
import androidx.preference.PreferenceManager

class MainActivity : AppCompatActivity() {

    private lateinit var friendDao: FriendDao
    private lateinit var contactAdapter: ContactAdapter
    private lateinit var noFriendsTextView: TextView
    private val qrScanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val intentResult = IntentIntegrator.parseActivityResult(result.resultCode, result.data)
        if (intentResult != null) {
            if (intentResult.contents != null) {
                handleScannedData(intentResult.contents)
            } else {
                Toast.makeText(this, "Scan cancelled", Toast.LENGTH_LONG).show()
            }
        }
    }

    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data?.getBooleanExtra("friend_added", false) == true) {
            loadContacts()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle SOCKS proxy info from TorStatusActivity
        val socksHost = intent.getStringExtra("socks_host")
        val socksPort = intent.getIntExtra("socks_port", -1)

        if (socksHost != null && socksPort != -1) {
            Log.d("MainActivity", "Received SOCKS proxy: $socksHost:$socksPort")
            // Here you would configure your networking library to use this proxy
            // For example, with OkHttp:
            // val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(socksHost, socksPort))
            // val okHttpClient = OkHttpClient.Builder().proxy(proxy).build()
            // (application as PeerLinkyzApplication).p2pManager.setHttpClient(okHttpClient)
        }


        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val onboardingComplete = sharedPreferences.getBoolean("onboarding_complete", false)

        if (!onboardingComplete) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        // Initialize database
        val database = AppDatabase.getDatabase(this)
        friendDao = database.friendDao()

        noFriendsTextView = findViewById(R.id.noFriendsTextView)

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Add smooth exit animation
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            }
        })

        // Set up RecyclerView
        val recyclerView = findViewById<RecyclerView>(R.id.contactsRecyclerView)
        contactAdapter = ContactAdapter(
            onItemClick = { contact ->
                val intent = Intent(this, ChatActivity::class.java).apply {
                    putExtra("friendId", contact.id)
                    putExtra("friendName", contact.name)
                }
                startActivity(intent)
                // Add smooth transition animation
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            },
            onItemLongClick = { contact ->
                if (contact.id != 0) { // Assuming 0 is the admin
                    showDeleteFriendDialog(contact)
                }
            }
        )
        recyclerView.adapter = contactAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Load contacts with loading animation
        loadContacts()

        // Add friend button
        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.addFriendButton).setOnClickListener {
            //val intent = Intent(this, SettingsActivity::class.java)
            //startActivity(intent)
            // Add smooth transition animation
            //overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            val intent = Intent(this, SettingsActivity::class.java)
            settingsLauncher.launch(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // Settings button
        findViewById<ImageView>(R.id.settingsButton).setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            settingsLauncher.launch(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        
        
    }

    override fun onResume() {
        super.onResume()
        loadContacts()
    }

    

    private fun loadContacts() {
        lifecycleScope.launch(Dispatchers.IO) {
            val friends = friendDao.getAllFriends()
            Log.d("MainActivity", "Friends retrieved from DB: ${friends.size}")
            withContext(Dispatchers.Main) {
                contactAdapter.submitList(friends.map { Contact(it.id, it.username, "") })
                Log.d("MainActivity", "Submitted list to adapter. Adapter item count: ${contactAdapter.itemCount}")

                if (friends.isEmpty()) {
                    noFriendsTextView.visibility = View.VISIBLE
                } else {
                    noFriendsTextView.visibility = View.GONE
                }
                
                // Add fade-in animation for contacts
                val recyclerView = findViewById<RecyclerView>(R.id.contactsRecyclerView)
                recyclerView.alpha = 0f
                recyclerView.animate()
                    .alpha(1f)
                    .setDuration(500)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
            }
        }
    }

    private fun handleScannedData(contents: String) {
        lifecycleScope.launch {
            val result = addFriendFromQrJson(this@MainActivity, contents)
            result.onSuccess { username ->
                Toast.makeText(this@MainActivity, "Friend added: $username", Toast.LENGTH_LONG).show()
                Log.d("MainActivity", "Friend $username added to database.")
                loadContacts()
            }.onFailure { e ->
                Log.e("MainActivity", "Error adding friend: ${e.message}", e)
                Toast.makeText(this@MainActivity, "Error adding friend: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showDeleteFriendDialog(contact: Contact) {
        AlertDialog.Builder(this)
            .setTitle("Delete Friend")
            .setMessage("Are you sure you want to delete ${contact.name}?")
            .setPositiveButton("Delete") { _, _ ->
                deleteFriend(contact)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteFriend(contact: Contact) {
        lifecycleScope.launch {
            friendDao.deleteFriend(contact.id)
            // Also delete messages associated with the friend
            val messageDao = AppDatabase.getDatabase(applicationContext).messageDao()
            messageDao.deleteMessagesForFriend(contact.id)
            loadContacts()
            Toast.makeText(this@MainActivity, "${contact.name} deleted", Toast.LENGTH_SHORT).show()
        }
    }

    
}
