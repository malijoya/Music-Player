package com.musp.musicplayer.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.musp.musicplayer.R
import com.musp.musicplayer.databinding.ItemPickSongBinding
import com.musp.musicplayer.model.Song
import com.musp.musicplayer.utils.MusicUtils
import com.musp.musicplayer.utils.loadArtwork

/**
 * Selectable song cards for the add-songs picker. Tapping a card toggles it,
 * tapping its artwork previews the song.
 */
class PickSongAdapter(
    private val isSelected: (Song) -> Boolean,
    private val onToggle: (Song) -> Unit,
    private val onPreview: (Song) -> Unit
) : ListAdapter<Song, PickSongAdapter.ViewHolder>(DIFF) {

    private var previewId: Long? = null
    private var previewPlaying = false
    private var previewProgress = 0f

    class ViewHolder(val binding: ItemPickSongBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPickSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val holder = ViewHolder(binding)
        binding.root.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) return@setOnClickListener
            onToggle(getItem(position))
            // Small pop on the check mark to confirm the tap
            binding.ivCheck.animate().cancel()
            binding.ivCheck.scaleX = 0.8f
            binding.ivCheck.scaleY = 0.8f
            binding.ivCheck.animate().scaleX(1f).scaleY(1f).setDuration(180).start()
        }
        binding.btnPreview.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) onPreview(getItem(position))
        }
        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val song = getItem(position)
        with(holder.binding) {
            tvTitle.text = song.title
            tvSubtitle.text = root.context.getString(
                R.string.dot_separated, song.artist, MusicUtils.formatDuration(song.duration)
            )
            ivArt.loadArtwork(song.albumArt)
        }
        bindSelection(holder, song)
        bindPreview(holder, song)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
            return
        }
        val song = getItem(position)
        if (PAYLOAD_SELECTION in payloads) bindSelection(holder, song)
        if (PAYLOAD_PREVIEW in payloads) bindPreview(holder, song)
        if (PAYLOAD_PROGRESS in payloads) bindProgress(holder)
    }

    private fun bindSelection(holder: ViewHolder, song: Song) {
        val selected = isSelected(song)
        holder.binding.root.isActivated = selected // card tint + filled check mark
        holder.binding.root.isSelected = selected // announced by accessibility services
    }

    private fun bindPreview(holder: ViewHolder, song: Song) {
        val b = holder.binding
        val context = b.root.context
        val isPreviewing = song.id == previewId
        val playing = isPreviewing && previewPlaying
        b.btnPreview.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        b.btnPreview.contentDescription = context.getString(
            if (playing) R.string.stop_preview else R.string.preview_song, song.title
        )
        b.tvTitle.setTextColor(
            ContextCompat.getColor(context, if (isPreviewing) R.color.accent_color else R.color.text_primary)
        )
        b.previewProgress.isVisible = isPreviewing
        if (isPreviewing) bindProgress(holder)
    }

    private fun bindProgress(holder: ViewHolder) {
        holder.binding.previewProgress.setProgressCompat((previewProgress * 1000).toInt(), true)
    }

    fun notifySelectionChanged() = notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)

    fun notifySelectionChanged(song: Song) {
        val index = currentList.indexOfFirst { it.id == song.id }
        if (index >= 0) notifyItemChanged(index, PAYLOAD_SELECTION)
    }

    /** Shows which song is being previewed and whether it is playing. */
    fun setPreview(songId: Long?, playing: Boolean) {
        if (songId == previewId && playing == previewPlaying) return
        val previous = previewId
        previewId = songId
        previewPlaying = playing
        if (previous != songId) previewProgress = 0f
        currentList.forEachIndexed { index, song ->
            if (song.id == previous || song.id == songId) notifyItemChanged(index, PAYLOAD_PREVIEW)
        }
    }

    fun setPreviewProgress(progress: Float) {
        val id = previewId ?: return
        previewProgress = progress
        val index = currentList.indexOfFirst { it.id == id }
        if (index >= 0) notifyItemChanged(index, PAYLOAD_PROGRESS)
    }

    companion object {
        private const val PAYLOAD_SELECTION = "selection"
        private const val PAYLOAD_PREVIEW = "preview"
        private const val PAYLOAD_PROGRESS = "progress"

        private val DIFF = object : DiffUtil.ItemCallback<Song>() {
            override fun areItemsTheSame(oldItem: Song, newItem: Song) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Song, newItem: Song) = oldItem == newItem
        }
    }
}
