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

class AllPagerFragment : Fragment() {
    companion object {
        fun create(): AllPagerFragment {
            return AllPagerFragment()
        }
    }

    private lateinit var mainActivity: MainActivity
    private lateinit var tabLayout: TabLayout
    private lateinit var pager: ViewPager2
    private lateinit var fragments: List<SongListFragment>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        mainActivity = activity as MainActivity
        fragments = listOf(
            SongListFragment.createAsSequential(),
            SongListFragment.createAsFavorite()
        )
        val view = inflater.inflate(R.layout.fragment_all_pager, container, false)
        tabLayout = view.findViewById(R.id.tab)
        pager = view.findViewById(R.id.pager)
        pager.adapter = Adapter(mainActivity)
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                (activity as? MainActivity)?.stopSong()
                fragments[0].reloadIfNeeded()
                fragments[1].makeFavoriteSongsList()
                super.onPageSelected(position)
            }
        })
        pager.setCurrentItem(0, false)
        tabLayout.tabMode = TabLayout.MODE_FIXED
        TabLayoutMediator(tabLayout, pager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.all_songs)
                1 -> getString(R.string.favorites)
                else -> null
            }
        }.attach()
        return view
    }

    inner class Adapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount() = 2

        override fun createFragment(position: Int): Fragment {
            val fragment = when (position) {
                0, 1 -> fragments[position]
                else -> throw RuntimeException("logic error")
            }
            fragment.listener = mainActivity
            return fragment
        }
    }
}