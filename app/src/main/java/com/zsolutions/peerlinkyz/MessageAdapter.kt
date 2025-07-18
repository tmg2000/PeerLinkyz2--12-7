package com.zsolutions.peerlinkyz

import android.graphics.*
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.*
import java.nio.charset.StandardCharsets

class MessageAdapter(
    private val messages: List<Message>,
    private val cryptoManager: CryptoManager,
    private val sharedSecret: ByteArray?,
    private val getFriendUsername: ((friendId: Int) -> String?)? = null // callback to get username for avatar
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val VIEW_TYPE_SENT = 1
    private val VIEW_TYPE_RECEIVED = 2

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isSent) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_SENT) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_sent, parent, false)
            SentMessageViewHolder(view, cryptoManager, sharedSecret)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_received, parent, false)
            ReceivedMessageViewHolder(view, cryptoManager, sharedSecret)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        if (holder.itemViewType == VIEW_TYPE_SENT) {
            (holder as SentMessageViewHolder).bind(message)
        } else {
            val username = getFriendUsername?.invoke(message.friendId) ?: "?"
            (holder as ReceivedMessageViewHolder).bind(message, username)
        }
    }

    override fun getItemCount(): Int = messages.size

    class SentMessageViewHolder(itemView: View, private val cryptoManager: CryptoManager, private val sharedSecret: ByteArray?) : RecyclerView.ViewHolder(itemView) {
        private val messageTextView: TextView = itemView.findViewById(R.id.messageTextView)
        private val timestampTextView: TextView = itemView.findViewById(R.id.timestampTextView)

        fun bind(message: Message) {
            try {
                if (sharedSecret != null) {
                    messageTextView.text = String(cryptoManager.decrypt(message.data, sharedSecret), StandardCharsets.UTF_8)
                } else {
                    messageTextView.text = "[Key exchange not complete]"
                }
            } catch (e: Exception) {
                messageTextView.text = "[Could not decrypt message]"
            }
            timestampTextView.text = formatTimestamp(message.timestamp)
        }
    }

    class ReceivedMessageViewHolder(itemView: View, private val cryptoManager: CryptoManager, private val sharedSecret: ByteArray?) : RecyclerView.ViewHolder(itemView) {
        private val messageTextView: TextView = itemView.findViewById(R.id.messageTextView)
        private val timestampTextView: TextView = itemView.findViewById(R.id.timestampTextView)
        fun bind(message: Message, username: String) {
            try {
                if (sharedSecret != null) {
                    messageTextView.text = String(cryptoManager.decrypt(message.data, sharedSecret), StandardCharsets.UTF_8)
                } else {
                    messageTextView.text = "[Key exchange not complete]"
                }
            } catch (e: Exception) {
                messageTextView.text = "[Could not decrypt message]"
            }
            timestampTextView.text = formatTimestamp(message.timestamp)
        }

        private fun createInitialsAvatar(username: String): Bitmap {
            if (username.isEmpty()) {
                val size = 72
                return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            }
            val initials = username.trim().split(" ").mapNotNull { it.firstOrNull()?.toString()?.uppercase() }.take(2).joinToString("")
            val size = 72
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val paint = Paint().apply {
                isAntiAlias = true
                color = Color.parseColor("#90CAF9")
            }
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
            paint.color = Color.WHITE
            paint.textSize = 32f
            paint.textAlign = Paint.Align.CENTER
            val y = size / 2f - (paint.descent() + paint.ascent()) / 2
            canvas.drawText(initials, size / 2f, y, paint)
            return bmp
        }
    }

    companion object {
        fun formatTimestamp(timestamp: Long): String {
            val cal = Calendar.getInstance(Locale.getDefault())
            cal.timeInMillis = timestamp
            return DateFormat.format("hh:mm a", cal).toString()
        }
    }
}
