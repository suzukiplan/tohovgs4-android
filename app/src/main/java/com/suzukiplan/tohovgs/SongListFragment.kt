/**
 * ©2022, SUZUKI PLAN
 * License: https://github.com/suzukiplan/tohovgs4-android/blob/master/LICENSE.txt
 */
package com.suzukiplan.tohovgs

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.suzukiplan.tohovgs.api.Settings
import com.suzukiplan.tohovgs.model.Album
import com.suzukiplan.tohovgs.model.Song
import java.util.*
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
        fun onRequestUnlockAll()
        fun onRequestUnlock(album: Album, done: (unlocked: Boolean) -> Unit)
        fun onRequestLock(song: Song, done: () -> Unit)
        fun onPlay(album: Album, song: Song, onPlayEnded: () -> Unit)
    }

    private lateinit var inflater: LayoutInflater
    private var albums = ArrayList<Album>(0)
    private var songs = ArrayList<Song>(0)
    private lateinit var mainActivity: MainActivity
    private val settings: Settings get() = mainActivity.settings
    private lateinit var list: RecyclerView
    private lateinit var songAdapter: SongAdapter
    private var titleAdapter: TitleAdapter? = null
    private var mergeAdapter: MergeAdapter? = null
    private lateinit var random: Random
    private lateinit var progress: View
    private lateinit var allLocked: View
    var listener: Listener? = null
    private var isSequentialMode = false
    private var showSongIndex = false
    private var language = Language.Default

    enum class Language {
        Japanese,
        French,
        Default
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        this.inflater = inflater
        language = when (Locale.getDefault().language) {
            "ja" -> Language.Japanese
            "fr" -> Language.French
            else -> Language.Default
        }
        mainActivity = activity as MainActivity
        albums.clear()
        songs.clear()
        random = Random(System.currentTimeMillis())
        val view = inflater.inflate(R.layout.fragment_song_list, container, false)
        progress = view.findViewById(R.id.progress)
        allLocked = view.findViewById(R.id.all_locked)
        when (requireArguments().getString("mode")) {
            "album" -> {
                val albumId = requireArguments().getString("album_id")
                val album = mainActivity.musicManager.albums.find { it.id == albumId }!!
                album.songs.forEach { songs.add(it) }
            }
            "sequential" -> {
                addAvailableSongs(songs)
                if (songs.count() < 1) {
                    allLocked.visibility = View.VISIBLE
                } else {
                    songs.forEach { song ->
                        if (null == albums.find { it.id == song.parentAlbum?.id }) {
                            albums.add(song.parentAlbum!!)
                        }
                    }
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
        songAdapter = SongAdapter()
        if (isSequentialMode) {
            titleAdapter = TitleAdapter()
            mergeAdapter = MergeAdapter()
            list.adapter = mergeAdapter
        } else {
            list.adapter = songAdapter
        }
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
                songs.clear()
                while (tmpItems.isNotEmpty()) {
                    songs.add(tmpItems.removeAt(abs(random.nextInt()) % tmpItems.count()))
                }
                mainActivity.runOnUiThread {
                    list.adapter?.notifyDataSetChanged()
                    list.scrollToPosition(0)
                    progress.visibility = View.GONE
                    allLocked.visibility = if (songs.count() < 1) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun addAvailableSongs(list: ArrayList<Song>) {
        mainActivity.musicManager.albums.forEach { album ->
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

    private fun reloadIfNeeded() = songs.forEach { if (it.needReload) reload(it) }

    private fun reload(song: Song?) {
        song ?: return
        val index = songs.indexOf(song)
        if (0 <= index) {
            if (isSequentialMode) {
                mergeAdapter?.notifyItemChanged(song)
            } else {
                songAdapter.notifyItemChanged(index)
            }
            song.needReload = false
        }
    }

    inner class MergeAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val positions = ArrayList<String>(0)

        fun notifyItemChanged(song: Song) =
            notifyItemChanged(positions.indexOf("S#${songs.indexOf(song)}"))

        fun focus(song: Song) = activity?.runOnUiThread {
            list.smoothScrollToPosition(positions.indexOf("S#${songs.indexOf(song)}"))
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = when (viewType) {
            0 -> titleAdapter!!.onCreateViewHolder(parent, 0)
            1 -> songAdapter.onCreateViewHolder(parent, 0)
            else -> throw RuntimeException("unknown viewType: $viewType")
        }

        override fun getItemViewType(position: Int): Int {
            if (positions.size < 1) {
                var albumIndex = 0
                var songIndex = 0
                albums.forEach { album ->
                    positions.add("A#${albumIndex++}")
                    songs.forEach { song ->
                        if (song.parentAlbum?.id == album.id) {
                            positions.add("S#${songIndex++}")
                        }
                    }
                }
            }
            return when {
                positions[position].startsWith("A#") -> 0
                positions[position].startsWith("S#") -> 1
                else -> throw RuntimeException("!? positions[$position]=${positions[position]}")
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val index = positions[position].substring(2).toInt()
            when (holder) {
                is TitleViewHolder -> holder.bind(albums[index])
                is SongViewHolder -> holder.bind(songs[index])
            }
        }

        override fun getItemCount() = songs.count() + albums.count()
    }

    inner class TitleAdapter : RecyclerView.Adapter<TitleViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TitleViewHolder =
            TitleViewHolder(inflater.inflate(R.layout.view_holder_title_list, parent, false))

        override fun onBindViewHolder(holder: TitleViewHolder, position: Int) {
            holder.bind(albums[position])
        }

        override fun getItemCount() = albums.count()
    }

    inner class SongAdapter : RecyclerView.Adapter<SongViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            SongViewHolder(inflater.inflate(R.layout.view_holder_song_list, parent, false))

        override fun onBindViewHolder(holder: SongViewHolder, position: Int) =
            holder.bind(songs[position])

        override fun getItemCount() = songs.count()

        fun focus(song: Song) = activity?.runOnUiThread {
            list.smoothScrollToPosition(songs.indexOf(song))
        }
    }

    private fun onPlayEnded(song: Song) {
        reload(song)
        var nextIndex = songs.indexOf(song)
        val previousIndex = nextIndex
        var nextSong: Song
        if (mainActivity.musicManager.infinity) {
            nextSong = song
        } else {
            do {
                nextIndex++
                nextIndex %= songs.count()
                nextSong = songs[nextIndex]
                if (previousIndex == nextIndex && settings.isLocked(nextSong)) {
                    return // all songs are unlocked
                }
            } while (settings.isLocked(nextSong))
        }
        play(nextSong)
        reload(nextSong)
        moveFocus(nextSong)
    }

    private fun play(song: Song) {
        if (isSequentialMode) settings.initialPositionSequential = songs.indexOf(song)
        listener?.onPlay(song.parentAlbum!!, song) { onPlayEnded(song) }
        moveFocus(song)
    }

    private fun moveFocus(song: Song) {
        if (isSequentialMode) {
            mergeAdapter?.focus(song)
        } else {
            songAdapter.focus(song)
        }
    }

    inner class TitleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.title)
        private val copyright: TextView = itemView.findViewById(R.id.copyright)
        fun bind(album: Album) {
            title.text = album.formalName
            copyright.text = album.copyright
        }
    }

    inner class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val root: View = itemView.findViewById(R.id.root)
        private val lock: View = itemView.findViewById(R.id.lock)
        private val play: ImageView = itemView.findViewById(R.id.play)
        private val songTitle: TextView = itemView.findViewById(R.id.song_title)
        private val englishTitle: TextView = itemView.findViewById(R.id.english_title)
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
                play.visibility = when (song.status) {
                    Song.Status.Play -> {
                        play.setImageResource(R.drawable.ic_baseline_play_circle_filled_24)
                        View.VISIBLE
                    }
                    Song.Status.Pause -> {
                        play.setImageResource(R.drawable.ic_baseline_pause_circle_outline_24)
                        View.VISIBLE
                    }
                    null, Song.Status.Stop -> {
                        View.GONE
                    }
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
            when (language) {
                Language.Japanese -> {
                    songTitle.text = song.name
                    englishTitle.visibility = View.GONE
                }
                Language.French,
                Language.Default -> {
                    songTitle.text = song.name
                    if (null != song.english) {
                        englishTitle.text = song.english
                        englishTitle.visibility = View.VISIBLE
                        songTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                        englishTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    } else {
                        songTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                        englishTitle.visibility = View.GONE
                    }
                }
            }
            songIndex.visibility = if (showSongIndex) {
                songIndex.text = "#${songs.indexOf(song) + 1}"
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }
}