package com.musp.musicplayer.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.musp.musicplayer.R
import com.musp.musicplayer.adapter.QueueAdapter
import com.musp.musicplayer.databinding.SheetQueueBinding
import com.musp.musicplayer.viewmodel.PlayerViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * "Up next" queue: tap to jump, drag the handle to reorder (when shuffle is off),
 * swipe to remove.
 */
class QueueBottomSheet : BottomSheetDialogFragment() {

    private var _binding: SheetQueueBinding? = null
    private val binding get() = _binding!!

    private val playerViewModel: PlayerViewModel by activityViewModels()

    private lateinit var queueAdapter: QueueAdapter
    private lateinit var touchHelper: ItemTouchHelper
    private var isDragging = false
    private var dragFrom = RecyclerView.NO_POSITION
    private var dragTo = RecyclerView.NO_POSITION
    private var scrolledToCurrent = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetQueueBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        queueAdapter = QueueAdapter(
            onItemClick = { playerViewModel.playQueueItem(it.index) },
            onStartDrag = { holder -> touchHelper.startDrag(holder) }
        )
        touchHelper = ItemTouchHelper(TouchCallback())

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = queueAdapter
            // Bounded height so large queues are recycled instead of all laid out at once
            updateLayoutParams { height = (resources.displayMetrics.heightPixels * 0.65f).toInt() }
        }
        touchHelper.attachToRecyclerView(binding.recyclerView)

        binding.btnClearQueue.setOnClickListener {
            playerViewModel.clearQueue()
            dismiss()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                playerViewModel.queue
                    .combine(playerViewModel.uiState) { queue, state -> queue to state.shuffleEnabled }
                    .collect { (queue, shuffle) ->
                        queueAdapter.dragEnabled = !shuffle
                        val count = resources.getQuantityString(R.plurals.song_count, queue.size, queue.size)
                        binding.tvQueueInfo.text = if (shuffle && queue.isNotEmpty()) {
                            getString(R.string.dot_separated, count, getString(R.string.queue_reorder_hint))
                        } else {
                            count
                        }
                        binding.tvEmpty.isVisible = queue.isEmpty()
                        binding.recyclerView.isVisible = queue.isNotEmpty()
                        binding.btnClearQueue.isVisible = queue.isNotEmpty()

                        if (!isDragging) queueAdapter.submit(queue)

                        if (!scrolledToCurrent && queue.isNotEmpty()) {
                            scrolledToCurrent = true
                            val current = queue.indexOfFirst { it.isCurrent }.coerceAtLeast(0)
                            (binding.recyclerView.layoutManager as LinearLayoutManager)
                                .scrollToPositionWithOffset(current, 0)
                        }
                    }
            }
        }
    }

    private inner class TouchCallback : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN,
        ItemTouchHelper.START or ItemTouchHelper.END
    ) {
        // Dragging starts only from the handle
        override fun isLongPressDragEnabled() = false

        override fun getDragDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int =
            if (queueAdapter.dragEnabled) super.getDragDirs(recyclerView, viewHolder) else 0

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
            if (dragFrom == RecyclerView.NO_POSITION) dragFrom = from
            dragTo = to
            queueAdapter.moveLocal(from, to)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val item = queueAdapter.itemAt(viewHolder.bindingAdapterPosition) ?: return
            playerViewModel.removeQueueItem(item.index)
        }

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) isDragging = true
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            if (!isDragging) return
            isDragging = false
            // With shuffle off, list positions are the player's playlist indices
            if (dragFrom != RecyclerView.NO_POSITION && dragTo != RecyclerView.NO_POSITION && dragFrom != dragTo) {
                playerViewModel.moveQueueItem(dragFrom, dragTo)
            } else {
                queueAdapter.submit(playerViewModel.queue.value)
            }
            dragFrom = RecyclerView.NO_POSITION
            dragTo = RecyclerView.NO_POSITION
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "queue"
    }
}
