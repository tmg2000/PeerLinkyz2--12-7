package com.zsolutions.peerlinkyz

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var skipButton: Button
    private lateinit var nextButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)
        skipButton = findViewById(R.id.skipButton)
        nextButton = findViewById(R.id.nextButton)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val rootLayout = findViewById<ConstraintLayout>(R.id.rootLayout)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        val onboardingScreens = listOf(
            OnboardingScreen("Welcome to PeerLinkyz!", "A secure and private P2P chat application."),
            OnboardingScreen("Set Up Your Profile", "Choose a unique username in settings to get started."),
            OnboardingScreen("Connect with Friends", "Generate a QR code to share your ID, or scan a friend's QR code."),
            OnboardingScreen("Customize Your Experience", "Change themes and manage message history in settings.")
        )

        val adapter = OnboardingPagerAdapter(onboardingScreens)
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            // No text for tabs, just indicators
        }.attach()

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                if (position == onboardingScreens.size - 1) {
                    nextButton.text = "Finish"
                } else {
                    nextButton.text = "Next"
                }
            }
        })

        skipButton.setOnClickListener { navigateToMain() }
        nextButton.setOnClickListener { 
            if (viewPager.currentItem == onboardingScreens.size - 1) {
                navigateToMain()
            } else {
                viewPager.currentItem = viewPager.currentItem + 1
            }
        }
    }

    private fun navigateToMain() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPreferences.edit().putBoolean("onboarding_complete", true).commit()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
