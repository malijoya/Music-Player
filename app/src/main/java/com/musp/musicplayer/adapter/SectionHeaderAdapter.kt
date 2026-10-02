package com.musp.musicplayer.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.musp.musicplayer.databinding.ItemSectionHeaderBinding

/** A single section title that can be shown or hidden (used with ConcatAdapter). */
class SectionHeaderAdapter(private val title: String) :
    RecyclerView.Adapter<SectionHeaderAdapter.HeaderViewHolder>() {

    class HeaderViewHolder(val binding: ItemSectionHeaderBinding) : RecyclerView.ViewHolder(binding.root)

    var visible: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value) notifyItemInserted(0) else notifyItemRemoved(0)
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder =
        HeaderViewHolder(ItemSectionHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) {
        holder.binding.tvHeader.text = title
    }

    override fun getItemCount(): Int = if (visible) 1 else 0
}
