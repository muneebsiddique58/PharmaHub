package com.example.pharmahub11.fragments.settings

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pharmahub11.R
import com.example.pharmahub11.adapters.MessageAdapter
import com.example.pharmahub11.data.Chat
import com.example.pharmahub11.data.Message
import com.example.pharmahub11.data.order.Order
import com.example.pharmahub11.databinding.FragmentOrderSupportChatBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.UUID

class OrderSupportChatFragment : Fragment() {
    private lateinit var binding: FragmentOrderSupportChatBinding
    private lateinit var adapter: MessageAdapter
    private val messages = mutableListOf<Message>()
    private var chatId: String = ""
    private var orderId: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentOrderSupportChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        orderId = arguments?.getString("orderId") ?: ""
        if (orderId.isEmpty()) {
            findNavController().popBackStack()
            return
        }

        setupRecyclerView()
        checkExistingChatOrCreateNew()

        binding.btnSend.setOnClickListener {
            sendMessage()
        }

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun setupRecyclerView() {
        adapter = MessageAdapter(messages)
        binding.rvMessages.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMessages.adapter = adapter
    }

    private fun checkExistingChatOrCreateNew() {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        FirebaseFirestore.getInstance()
            .collection("chats")
            .whereEqualTo("orderId", orderId)
            .whereEqualTo("chatType", "order_support")
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot.isEmpty) {
                    createNewOrderSupportChat(currentUser)
                } else {
                    chatId = querySnapshot.documents[0].id
                    loadMessages()
                }
            }
    }

    private fun createNewOrderSupportChat(currentUser: FirebaseUser) {
        // First get order details to get pharmacistId
        FirebaseFirestore.getInstance()
            .collection("orders")
            .document(orderId)
            .get()
            .addOnSuccessListener { document ->
                val order = document.toObject(Order::class.java) ?: return@addOnSuccessListener

                chatId = UUID.randomUUID().toString()
                val chat = Chat(
                    chatId = chatId,
                    customerId = currentUser.uid,
                    customerName = currentUser.displayName ?: "Customer",
                    pharmacistId = order.pharmacistId,
                    orderId = orderId,
                    chatType = "order_support"
                )

                FirebaseFirestore.getInstance()
                    .collection("chats")
                    .document(chatId)
                    .set(chat)
                    .addOnSuccessListener {
                        loadMessages()
                    }
            }
    }

    private fun loadMessages() {
        FirebaseFirestore.getInstance()
            .collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) return@addSnapshotListener

                messages.clear()
                snapshots?.documents?.forEach { doc ->
                    val message = doc.toObject(Message::class.java)
                    message?.let { messages.add(it) }
                }
                adapter.notifyDataSetChanged()
                binding.rvMessages.scrollToPosition(messages.size - 1)
            }
    }

    private fun sendMessage() {
        val text = binding.etMessage.text.toString().trim()
        if (text.isEmpty()) return

        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val message = Message(
            messageId = UUID.randomUUID().toString(),
            senderId = currentUser.uid,
            senderName = currentUser.displayName ?: "Customer",
            text = text
        )

        FirebaseFirestore.getInstance()
            .collection("chats")
            .document(chatId)
            .collection("messages")
            .document(message.messageId)
            .set(message)
            .addOnSuccessListener {
                binding.etMessage.text.clear()
                updateLastMessage(text)
            }
    }

    private fun updateLastMessage(text: String) {
        FirebaseFirestore.getInstance()
            .collection("chats")
            .document(chatId)
            .update(
                "lastMessage", text,
                "lastMessageTime", FieldValue.serverTimestamp()
            )
    }
}