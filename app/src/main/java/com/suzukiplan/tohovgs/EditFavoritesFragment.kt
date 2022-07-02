package com.suzukiplan.tohovgs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.suzukiplan.tohovgs.model.Album
import com.suzukiplan.tohovgs.model.Song


class EditFavoritesFragment : Fragment() {
    companion object {
        fun create(listener: Listener): EditFavoritesFragment {
            val result = EditFavoritesFragment()
            result.arguments = Bundle()
            result.listener = listener
            return result
        }
    }

    interface Listener {
        fun onClose()
    }

    private lateinit var inflater: LayoutInflater
    private lateinit var listener: Listener
    private var songs = ArrayList<Song>(0)
    private var albums = ArrayList<Album>(0)
    private lateinit var songAdapter: SongAdapter
    private lateinit var titleAdapter: TitleAdapter
    private lateinit var mergeAdapter: MergeAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        this.inflater = inflater
        val view = inflater.inflate(R.layout.fragment_edit_favorites, container, false)
        val settings = (activity as MainActivity?)?.settings
        songs.clear()
        albums.clear()
        (activity as MainActivity?)?.musicManager?.albums?.forEach { album ->
            album.songs.forEach { song ->
                if (false == settings?.isLocked(song)) {
                    songs.add(song)
                    if (null == albums.find { it.id == song.parentAlbum?.id }) {
                        albums.add(song.parentAlbum!!)
                    }
                }
            }
        }
        val list = view.findViewById<RecyclerView>(R.id.list)
        list.layoutManager = LinearLayoutManager(
            requireContext(), LinearLayoutManager.VERTICAL, false
        )
        titleAdapter = TitleAdapter()
        songAdapter = SongAdapter()
        mergeAdapter = MergeAdapter()
        list.adapter = mergeAdapter
        view.findViewById<View>(R.id.close).setOnClickListener {
            listener.onClose()
            parentFragmentManager.beginTransaction().remove(this).commit()
        }
        return view
    }


    inner class MergeAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val positions = java.util.ArrayList<String>(0)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = when (viewType) {
            0 -> titleAdapter.onCreateViewHolder(parent, 0)
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
            TitleViewHolder(
                inflater.inflate(
                    R.layout.view_holder_favorite_edit_title,
                    parent,
                    false
                )
            )

        override fun onBindViewHolder(holder: TitleViewHolder, position: Int) {
            holder.bind(albums[position])
        }

        override fun getItemCount() = albums.count()
    }

    inner class SongAdapter : RecyclerView.Adapter<SongViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            SongViewHolder(inflater.inflate(R.layout.view_holder_favorite_edit_song, parent, false))

        override fun onBindViewHolder(holder: SongViewHolder, position: Int) =
            holder.bind(songs[position])

        override fun getItemCount() = songs.count()
    }

    inner class TitleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.name)
        fun bind(album: Album) {
            name.text = album.name
        }
    }

    inner class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.name)
        private val favorite: ImageView = itemView.findViewById(R.id.favorite)
        fun bind(song: Song) {
            name.text = song.name
            val settings = (activity as? MainActivity)?.settings
            if (true == settings?.isFavorite(song)) {
                favorite.setImageResource(R.drawable.ic_like_on)
            } else {
                favorite.setImageResource(R.drawable.ic_like_off)
            }
            favorite.setOnClickListener {
                val f = !(settings?.isFavorite(song) ?: false)
                settings?.favorite(song, f)
                favorite.setImageResource(if (f) R.drawable.ic_like_on else R.drawable.ic_like_off)
            }
        }
    }
}