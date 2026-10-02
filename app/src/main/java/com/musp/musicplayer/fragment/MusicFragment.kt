package com.musp.musicplayer.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.musp.musicplayer.R
import com.musp.musicplayer.activity.MainActivity
import com.musp.musicplayer.databinding.FragmentMusicBinding

/** Library tab: Songs, Albums and Artists. */
class MusicFragment : Fragment() {

    private var _binding: FragmentMusicBinding? = null
    private val binding get() = _binding!!

    private var tabMediator: TabLayoutMediator? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMusicBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Tied to the view lifecycle so pages are recreated cleanly when returning from the back stack
        binding.viewPager.adapter = object : FragmentStateAdapter(childFragmentManager, viewLifecycleOwner.lifecycle) {
            override fun getItemCount() = 3
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> SongsFragment()
                1 -> AlbumsFragment()
                else -> ArtistsFragment()
            }
        }
        tabMediator = TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.setText(
                when (position) {
                    0 -> R.string.tab_songs
                    1 -> R.string.tab_albums
                    else -> R.string.tab_artists
                }
            )
        }.also { it.attach() }

        binding.btnSearch.setOnClickListener {
            (activity as? MainActivity)?.navigateTo(SearchFragment())
        }
    }

    override fun onDestroyView() {
        tabMediator?.detach()
        tabMediator = null
        binding.viewPager.adapter = null
        super.onDestroyView()
        _binding = null
    }
}
