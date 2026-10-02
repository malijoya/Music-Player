package com.musp.musicplayer.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.musp.musicplayer.R
import com.musp.musicplayer.databinding.ItemAlbumBinding
import com.musp.musicplayer.model.Album
import com.musp.musicplayer.utils.loadArtwork

class AlbumAdapter(
    private val onAlbumClick: (Album) -> Unit
) : ListAdapter<Album, AlbumAdapter.AlbumViewHolder>(DIFF) {

    class AlbumViewHolder(val binding: ItemAlbumBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val binding = ItemAlbumBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val holder = AlbumViewHolder(binding)
        binding.root.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) onAlbumClick(getItem(position))
        }
        return holder
    }

    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        val album = getItem(position)
        val context = holder.itemView.context
        with(holder.binding) {
            tvTitle.text = album.title
            tvSubtitle.text = context.getString(
                R.string.dot_separated,
                album.artist,
                context.resources.getQuantityString(R.plurals.song_count, album.songCount, album.songCount)
            )
            ivArt.loadArtwork(album.artUri)
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Album>() {
            override fun areItemsTheSame(oldItem: Album, newItem: Album) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Album, newItem: Album) = oldItem == newItem
        }
    }
}
