/**
 * ©2022, SUZUKI PLAN
 * License: https://github.com/suzukiplan/tohovgs4-android/blob/master/LICENSE.txt
 */
package com.suzukiplan.tohovgs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.suzukiplan.tohovgs.api.Settings
import com.suzukiplan.tohovgs.model.Album

class AlbumPagerFragment : Fragment() {
    companion object {
        fun create(): AlbumPagerFragment {
            return AlbumPagerFragment()
        }
    }

    private lateinit var mainActivity: MainActivity
    private lateinit var unlockAll: View
    private lateinit var tabLayout: TabLayout
    private lateinit var pager: ViewPager2
    private val settings: Settings? get() = mainActivity.settings
    private lateinit var items: List<Album>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        mainActivity = activity as MainActivity
        val view = inflater.inflate(R.layout.fragment_album_pager, container, false)
        unlockAll = view.findViewById(R.id.unlock_all)
        tabLayout = view.findViewById(R.id.tab)
        pager = view.findViewById(R.id.pager)
        items = mainActivity.musicManager?.albums ?: return null
        pager.adapter = Adapter(mainActivity)
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                (activity as? MainActivity)?.stopSong()
                settings?.lastSelectedAlbumId = items[position].id
                super.onPageSelected(position)
            }
        })
        val targetAlbumId = settings?.lastSelectedAlbumId
        val index = items.indexOfFirst { it.id == targetAlbumId }
        pager.setCurrentItem(index, false)
        tabLayout.tabMode = TabLayout.MODE_SCROLLABLE
        TabLayoutMediator(tabLayout, pager) { tab, position ->
            tab.text = items[position].name
        }.attach()
        val allUnlocked = mainActivity.musicManager?.isExistLockedSong(settings)
        unlockAll.visibility = if (true == allUnlocked) {
            unlockAll.setOnClickListener {
                mainActivity.onRequestUnlockAll(null)
            }
            View.VISIBLE
        } else {
            View.GONE
        }
        return view
    }

    inner class Adapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount() = items.count()

        override fun createFragment(position: Int): Fragment {
            val fragment = SongListFragment.create(items[position])
            fragment.listener = mainActivity
            return fragment
        }
    }
}