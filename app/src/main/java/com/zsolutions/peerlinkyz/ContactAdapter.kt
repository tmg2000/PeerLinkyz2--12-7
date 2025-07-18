package com.zsolutions.peerlinkyz

import android.graphics.*
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zsolutions.peerlinkyz.R

class ContactAdapter(
    private val onItemClick: (Contact) -> Unit,
    private val onItemLongClick: (Contact) -> Unit
) :
    ListAdapter<Contact, ContactAdapter.ContactViewHolder>(ContactDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_contact, parent, false)
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contact = getItem(position)
        holder.bind(contact)
        holder.itemView.setOnClickListener { onItemClick(contact) }
        holder.itemView.setOnLongClickListener {
            onItemLongClick(contact)
            true
        }
    }

    class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatarImageView: ImageView = itemView.findViewById(R.id.avatarImageView)
        private val nameTextView: TextView = itemView.findViewById(R.id.nameTextView)

        fun bind(contact: Contact) {
            nameTextView.text = contact.name
            avatarImageView.setImageBitmap(createInitialsAvatar(contact.name))
        }

        private fun createInitialsAvatar(name: String): Bitmap {
            val initials = name.trim().split(" ").mapNotNull { it.firstOrNull()?.toString()?.uppercase() }.take(2).joinToString("")
            val size = 96
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val paint = Paint().apply {
                isAntiAlias = true
                color = Color.parseColor("#90CAF9")
            }
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
            paint.color = Color.WHITE
            paint.textSize = 36f
            paint.textAlign = Paint.Align.CENTER
            val y = size / 2f - (paint.descent() + paint.ascent()) / 2
            canvas.drawText(initials, size / 2f, y, paint)
            return bmp
        }
    }

    private class ContactDiffCallback : DiffUtil.ItemCallback<Contact>() {
        override fun areItemsTheSame(oldItem: Contact, newItem: Contact): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Contact, newItem: Contact): Boolean {
            return oldItem == newItem
        }
    }
}
