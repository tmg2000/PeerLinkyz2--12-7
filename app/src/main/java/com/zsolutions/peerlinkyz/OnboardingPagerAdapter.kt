package com.zsolutions.peerlinkyz

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class OnboardingPagerAdapter(private val screens: List<OnboardingScreen>) :
    RecyclerView.Adapter<OnboardingPagerAdapter.OnboardingViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OnboardingViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.onboarding_screen_item, parent, false)
        return OnboardingViewHolder(view)
    }

    override fun onBindViewHolder(holder: OnboardingViewHolder, position: Int) {
        val screen = screens[position]
        holder.bind(screen)
    }

    override fun getItemCount(): Int = screens.size

    class OnboardingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.onboardingTitle)
        private val descriptionTextView: TextView = itemView.findViewById(R.id.onboardingDescription)

        fun bind(screen: OnboardingScreen) {
            titleTextView.text = screen.title
            descriptionTextView.text = screen.description
        }
    }
}