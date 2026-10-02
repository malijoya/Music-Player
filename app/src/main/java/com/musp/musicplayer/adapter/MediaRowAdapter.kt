package com.musp.musicplayer.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.shape.ShapeAppearanceModel
import com.musp.musicplayer.R
import com.musp.musicplayer.databinding.ItemMediaRowBinding
import com.musp.musicplayer.model.Album
import com.musp.musicplayer.model.Artist
import com.musp.musicplayer.model.Playlist
import com.musp.musicplayer.utils.loadArtwork

/** What a generic row displays. */
data class RowContent(
    val title: String,
    val subtitle: String,
    val imageUri: String?,
    val circular: Boolean = false
)

/**
 * Generic list row (image, title, subtitle, optional menu) used for artists,
 * playlists and album search results.
 */
class MediaRowAdapter<T : Any>(
    idOf: (T) -> Any,
    private val content: (Context, T) -> RowContent,
    private val onClick: (T) -> Unit,
    private val onMoreClick: ((T, View) -> Unit)? = null
) : ListAdapter<T, MediaRowAdapter.RowViewHolder>(RowDiff(idOf)) {

    class RowViewHolder(val binding: ItemMediaRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        val binding = ItemMediaRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val holder = RowViewHolder(binding)
        binding.root.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) onClick(getItem(position))
        }
        binding.btnMore.isVisible = onMoreClick != null
        binding.btnMore.setOnClickListener { anchor ->
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) onMoreClick?.invoke(getItem(position), anchor)
        }
        return holder
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        val row = content(holder.itemView.context, getItem(position))
        with(holder.binding) {
            tvTitle.text = row.title
            tvSubtitle.text = row.subtitle
            val cornerRes = if (row.circular) null else R.dimen.art_corner_small
            ivImage.shapeAppearanceModel = if (cornerRes == null) {
                ShapeAppearanceModel.builder().setAllCornerSizes(ShapeAppearanceModel.PILL).build()
            } else {
                ShapeAppearanceModel.builder()
                    .setAllCornerSizes(root.resources.getDimension(cornerRes))
                    .build()
            }
            ivImage.loadArtwork(row.imageUri)
        }
    }

    private class RowDiff<T : Any>(private val idOf: (T) -> Any) : DiffUtil.ItemCallback<T>() {
        override fun areItemsTheSame(oldItem: T, newItem: T) = idOf(oldItem) == idOf(newItem)

        @SuppressLint("DiffUtilEquals") // T is always a data class
        override fun areContentsTheSame(oldItem: T, newItem: T) = oldItem == newItem
    }

    companion object {
        fun forArtists(onClick: (Artist) -> Unit) = MediaRowAdapter<Artist>(
            idOf = { it.name },
            content = { context, artist ->
                RowContent(
                    title = artist.name,
                    subtitle = context.getString(
                        R.string.dot_separated,
                        context.resources.getQuantityString(R.plurals.album_count, artist.albumCount, artist.albumCount),
                        context.resources.getQuantityString(R.plurals.song_count, artist.songCount, artist.songCount)
                    ),
                    imageUri = artist.artUri,
                    circular = true
                )
            },
            onClick = onClick
        )

        fun forAlbums(onClick: (Album) -> Unit) = MediaRowAdapter<Album>(
            idOf = { it.id },
            content = { context, album ->
                RowContent(
                    title = album.title,
                    subtitle = context.getString(
                        R.string.dot_separated,
                        album.artist,
                        context.resources.getQuantityString(R.plurals.song_count, album.songCount, album.songCount)
                    ),
                    imageUri = album.artUri
                )
            },
            onClick = onClick
        )

        fun forPlaylists(
            onClick: (Playlist) -> Unit,
            onMoreClick: (Playlist, View) -> Unit
        ) = MediaRowAdapter<Playlist>(
            idOf = { it.id },
            content = { context, playlist ->
                RowContent(
                    title = playlist.name,
                    subtitle = context.resources.getQuantityString(
                        R.plurals.song_count, playlist.songCount, playlist.songCount
                    ),
                    imageUri = null
                )
            },
            onClick = onClick,
            onMoreClick = onMoreClick
        )
    }
}
