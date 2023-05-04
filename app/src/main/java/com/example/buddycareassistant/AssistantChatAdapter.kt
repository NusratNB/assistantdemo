package com.example.buddycareassistant

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder

class AssistantChatAdapter : Adapter<AssistantChatAdapter.AssistantChatViewHolder>() {
    val items: MutableList<Pair<Boolean, String>> = mutableListOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AssistantChatViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)
        val contactView = inflater.inflate(if (viewType == 1) R.layout.item_assistant else R.layout.item_user, parent, false)
        return AssistantChatViewHolder(contactView)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: AssistantChatViewHolder, position: Int) {
        val item = items[position]
        holder.tvData.text = item.second
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position].first) 1 else 0
    }

    inner class AssistantChatViewHolder(private val itemView: View): ViewHolder(itemView) {
        val tvData = itemView.findViewById<TextView>(R.id.tvData)
    }
}