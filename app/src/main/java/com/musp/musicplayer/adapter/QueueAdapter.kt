package com.musp.musicplayer.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.musp.musicplayer.R
import com.musp.musicplayer.databinding.ItemQueueBinding
import com.musp.musicplayer.utils.loadArtwork
import com.musp.musicplayer.viewmodel.QueueItem

/**
 * Queue ("Up next") list. Keeps its own mutable list so items can be moved
 * locally while dragging; the player is updated once the drag ends.
 */
class QueueAdapter(
    private val onItemClick: (QueueItem) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<QueueAdapter.QueueViewHolder>() {

    private val items = mutableListOf<QueueItem>()

    var dragEnabled: Boolean = true
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    class QueueViewHolder(val binding: ItemQueueBinding) : RecyclerView.ViewHolder(binding.root)

    fun submit(newItems: List<QueueItem>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(oldPosition: Int, newPosition: Int) =
                items[oldPosition].index == newItems[newPosition].index &&
                    items[oldPosition].song.id == newItems[newPosition].song.id
            override fun areContentsTheSame(oldPosition: Int, newPosition: Int) =
                items[oldPosition] == newItems[newPosition]
        }, false)
        items.clear()
        items.addAll(newItems)
        diff.dispatchUpdatesTo(this)
    }

    fun itemAt(position: Int): QueueItem? = items.getOrNull(position)

    fun moveLocal(from: Int, to: Int) {
        if (from !in items.indices || to !in items.indices) return
        items.add(to, items.removeAt(from))
        notifyItemMoved(from, to)
    }

    override fun getItemCount(): Int = items.size

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
        val binding = ItemQueueBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val holder = QueueViewHolder(binding)
        binding.root.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) onItemClick(items[position])
        }
        binding.ivDrag.setOnTouchListener { _, event ->
            if (dragEnabled && event.actionMasked == MotionEvent.ACTION_DOWN) onStartDrag(holder)
            false
        }
        return holder
    }

    override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        with(holder.binding) {
            tvTitle.text = item.song.title
            tvArtist.text = item.song.artist
            ivArt.loadArtwork(item.song.albumArt)
            ivPlaying.isVisible = item.isCurrent
            ivDrag.alpha = if (dragEnabled) 1f else 0.3f
            tvTitle.setTextColor(
                ContextCompat.getColor(context, if (item.isCurrent) R.color.accent_color else R.color.text_primary)
            )
        }
    }
}
