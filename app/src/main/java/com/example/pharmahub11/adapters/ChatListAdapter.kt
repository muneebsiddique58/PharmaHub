package com.example.pharmahub11.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.pharmahub11.R
import com.example.pharmahub11.data.Chat
import java.text.SimpleDateFormat
import java.util.Locale

class ChatListAdapter(
    private val chats: List<Chat>,
    private val onChatClick: (Chat) -> Unit
) : RecyclerView.Adapter<ChatListAdapter.ChatViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(chats[position])
    }

    override fun getItemCount() = chats.size

    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(chat: Chat) {
            itemView.apply {
                findViewById<TextView>(R.id.tvChatTitle).text = when (chat.chatType) {
                    "app_support" -> "Application Support"
                    "order_support" -> "Order Support - #${chat.orderId.take(8)}"
                    else -> "Chat"
                }

                findViewById<TextView>(R.id.tvLastMessage).text = chat.lastMessage
                findViewById<TextView>(R.id.tvTime).text =
                    SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(chat.lastMessageTime)

                if (chat.unreadCount > 0) {
                    findViewById<TextView>(R.id.tvUnreadCount).visibility = View.VISIBLE
                    findViewById<TextView>(R.id.tvUnreadCount).text = chat.unreadCount.toString()
                } else {
                    findViewById<TextView>(R.id.tvUnreadCount).visibility = View.GONE
                }

                setOnClickListener { onChatClick(chat) }
            }
        }
    }
}