/**
 * ©2022, SUZUKI PLAN
 * License: https://github.com/suzukiplan/tohovgs4-android/blob/master/LICENSE.txt
 */
package com.suzukiplan.tohovgs

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.suzukiplan.tohovgs.api.MusicManager
import com.suzukiplan.tohovgs.api.Settings
import com.suzukiplan.tohovgs.model.Album
import com.suzukiplan.tohovgs.model.Song
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs

class SongListFragment : Fragment() {
    companion object {
        fun create(album: Album): SongListFragment {
            val fragment = SongListFragment()
            fragment.arguments = Bundle()
            fragment.arguments?.putString("mode", "album")
            fragment.arguments?.putString("album_id", album.id)
            return fragment
        }

        fun createAsSequential(): SongListFragment {
            val fragment = SongListFragment()
            fragment.arguments = Bundle()
            fragment.arguments?.putString("mode", "sequential")
            return fragment
        }

        fun createAsShuffle(): SongListFragment {
            val fragment = SongListFragment()
            fragment.arguments = Bundle()
            fragment.arguments?.putString("mode", "shuffle")
            return fragment
        }
    }

    interface Listener {
        fun onRequestUnlockAll(done: (unlocked: Boolean) -> Unit)
        fun onRequestUnlock(album: Album, done: (unlocked: Boolean) -> Unit)
        fun onRequestLock(song: Song, done: () -> Unit)
        fun onPlay(album: Album, song: Song, onPlayEnded: () -> Unit)
    }

    private var items = ArrayList<Song>(0)
    private lateinit var settings: Settings
    private lateinit var mainActivity: MainActivity
    private lateinit var list: RecyclerView
    private lateinit var adapter: Adapter
    private lateinit var random: Random
    private lateinit var progress: View
    private lateinit var allLocked: View
    var listener: Listener? = null
    private var isSequentialMode = false
    private var showSongIndex = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        settings = Settings(context)
        mainActivity = activity as MainActivity
        items.clear()
        random = Random(System.currentTimeMillis())
        val view = inflater.inflate(R.layout.fragment_song_list, container, false)
        progress = view.findViewById(R.id.progress)
        allLocked = view.findViewById(R.id.all_locked)
        when (requireArguments().getString("mode")) {
            "album" -> {
                val albumId = requireArguments().getString("album_id")
                val album =
                    MusicManager.getInstance(mainActivity)?.albums?.find { it.id == albumId }!!
                album.songs.forEach { items.add(it) }
            }
            "sequential" -> {
                addAvailableSongs(items)
                if (items.count() < 1) {
                    allLocked.visibility = View.VISIBLE
                }
                isSequentialMode = true
                showSongIndex = true
            }
            "shuffle" -> {
                showSongIndex = true
                val shuffleButton = view.findViewById<View>(R.id.shuffle)
                shuffleButton.visibility = View.VISIBLE
                shuffleButton.setOnClickListener { executeShuffle() }
                mainActivity.showInterstitialAd()
                executeShuffle()
            }
        }
        list = view.findViewById(R.id.list)
        adapter = Adapter()
        list.adapter = adapter
        list.itemAnimator = null
        list.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        if (isSequentialMode) {
            list.scrollToPosition(settings.initialPositionSequential)
        }
        return view
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun executeShuffle() {
        mainActivity.runOnUiThread {
            progress.visibility = View.VISIBLE
            mainActivity.executeAsync {
                Thread.sleep(1500)
                val tmpItems = ArrayList<Song>()
                addAvailableSongs(tmpItems)
                items.clear()
                while (tmpItems.isNotEmpty()) {
                    items.add(tmpItems.removeAt(abs(random.nextInt()) % tmpItems.count()))
                }
                mainActivity.runOnUiThread {
                    adapter.notifyDataSetChanged()
                    list.scrollToPosition(0)
                    progress.visibility = View.GONE
                    allLocked.visibility = if (items.count() < 1) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun addAvailableSongs(list: ArrayList<Song>) {
        MusicManager.getInstance(mainActivity)?.albums?.forEach { album ->
            album.songs.forEach { song ->
                if (!settings.isLocked(song)) {
                    list.add(song)
                }
            }
        }
    }

    override fun onResume() {
        reloadIfNeeded()
        super.onResume()
    }

    private fun reloadIfNeeded() = items.forEach { if (it.needReload) reload(it) }

    private fun reload(song: Song?) {
        song ?: return
        val index = items.indexOf(song)
        if (0 <= index) {
            adapter.notifyItemChanged(index)
            song.needReload = false
        }
    }

    inner class Adapter : RecyclerView.Adapter<ViewHolder>() {
        private val inflater = LayoutInflater.from(requireContext())

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(inflater.inflate(R.layout.view_holder_song_list, parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) =
            holder.bind(items[position])

        override fun getItemCount() = items.count()
    }

    private fun onPlayEnded(song: Song) {
        reload(song)
        var nextIndex = items.indexOf(song)
        val previousIndex = nextIndex
        var nextSong: Song
        if (true == MusicManager.getInstance(mainActivity)?.infinity) {
            nextSong = song
        } else {
            do {
                nextIndex++
                nextIndex %= items.count()
                nextSong = items[nextIndex]
                if (previousIndex == nextIndex && settings.isLocked(nextSong)) {
                    return // all songs are unlocked
                }
            } while (settings.isLocked(nextSong))
        }
        play(nextSong)
        reload(nextSong)
        list.scrollToPosition(items.indexOf(nextSong))
    }

    private fun play(song: Song) {
        if (isSequentialMode) settings.initialPositionSequential = items.indexOf(song)
        listener?.onPlay(song.parentAlbum!!, song) { onPlayEnded(song) }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val root: View = itemView.findViewById(R.id.root)
        private val lock: View = itemView.findViewById(R.id.lock)
        private val play: View = itemView.findViewById(R.id.play)
        private val songTitle: TextView = itemView.findViewById(R.id.song_title)
        private val songIndex: TextView = itemView.findViewById(R.id.song_index)

        @SuppressLint("SetTextI18n")
        fun bind(song: Song?) {
            song ?: return
            val album = song.parentAlbum ?: return
            val locked = settings.isLocked(song)
            if (locked) {
                lock.visibility = View.VISIBLE
                play.visibility = View.GONE
                root.setBackgroundResource(R.drawable.card_locked)
                root.setOnClickListener {
                    listener?.onRequestUnlock(album) {
                        reloadIfNeeded()
                    }
                }
                root.setOnLongClickListener {
                    listener?.onRequestUnlock(album) {
                        reloadIfNeeded()
                    }
                    true
                }
            } else {
                lock.visibility = View.GONE
                play.visibility = if (song.playing) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
                root.setBackgroundResource(R.drawable.card)
                root.setOnClickListener {
                    play(song)
                    reloadIfNeeded()
                }
                root.setOnLongClickListener {
                    listener?.onRequestLock(song) { reloadIfNeeded() }
                    true
                }
            }
            songTitle.text = song.name
            songIndex.visibility = if (showSongIndex) {
                songIndex.text = "#${items.indexOf(song) + 1}"
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }
}