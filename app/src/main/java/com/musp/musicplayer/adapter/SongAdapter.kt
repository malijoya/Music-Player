package com.musp.musicplayer.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.musp.musicplayer.R
import com.musp.musicplayer.databinding.ItemSongBinding
import com.musp.musicplayer.model.Song
import com.musp.musicplayer.utils.MusicUtils
import com.musp.musicplayer.utils.loadArtwork

class SongAdapter(
    private val onSongClick: (song: Song, position: Int) -> Unit,
    private val onMoreClick: ((song: Song, anchor: View) -> Unit)? = null
) : ListAdapter<Song, SongAdapter.SongViewHolder>(DIFF) {

    private var currentSongId: Long? = null

    class SongViewHolder(val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val holder = SongViewHolder(binding)
        binding.root.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) onSongClick(getItem(position), position)
        }
        binding.btnMore.setOnClickListener { anchor ->
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) onMoreClick?.invoke(getItem(position), anchor)
        }
        // Long press opens the same options menu
        binding.root.setOnLongClickListener {
            val position = holder.bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION || onMoreClick == null) return@setOnLongClickListener false
            onMoreClick.invoke(getItem(position), binding.btnMore)
            true
        }
        binding.btnMore.isVisible = onMoreClick != null
        return holder
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = getItem(position)
        with(holder.binding) {
            tvTitle.text = song.title
            tvArtist.text = song.artist
            tvDuration.text = MusicUtils.formatDuration(song.duration)
            ivArt.loadArtwork(song.albumArt)
        }
        bindCurrent(holder, song)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_CURRENT)) {
            bindCurrent(holder, getItem(position))
        } else {
            onBindViewHolder(holder, position)
        }
    }

    private fun bindCurrent(holder: SongViewHolder, song: Song) {
        val context = holder.itemView.context
        val isCurrent = song.id == currentSongId
        val color = if (isCurrent) R.color.accent_color else R.color.text_primary
        holder.binding.tvTitle.setTextColor(ContextCompat.getColor(context, color))
        holder.binding.root.isActivated = isCurrent // soft accent row background
    }

    /** Highlights the song that is currently playing. */
    fun setCurrentSongId(id: Long?) {
        if (id == currentSongId) return
        val previous = currentSongId
        currentSongId = id
        currentList.forEachIndexed { index, song ->
            if (song.id == previous || song.id == id) notifyItemChanged(index, PAYLOAD_CURRENT)
        }
    }

    companion object {
        private const val PAYLOAD_CURRENT = "current"

        private val DIFF = object : DiffUtil.ItemCallback<Song>() {
            override fun areItemsTheSame(oldItem: Song, newItem: Song) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Song, newItem: Song) = oldItem == newItem
        }
    }
}
