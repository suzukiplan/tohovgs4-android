package com.suzukiplan.tohovgs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.reflect.TypeToken
import com.suzukiplan.tohovgs.model.Song
import java.lang.reflect.Type


class AddedSongsFragment : Fragment() {
    companion object {
        fun create(
            mainActivity: MainActivity,
            songs: List<Song>,
            listener: Listener
        ): AddedSongsFragment {
            val result = AddedSongsFragment()
            result.arguments = Bundle()
            result.arguments?.putString("songs", mainActivity.gson?.toJson(songs))
            result.listener = listener
            return result
        }
    }

    interface Listener {
        fun onClose()
    }

    private lateinit var inflater: LayoutInflater
    private lateinit var songs: List<Song>
    private lateinit var listener: Listener

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        this.inflater = inflater
        val view = inflater.inflate(R.layout.fragment_added_songs, container, false)
        val listType: Type = object : TypeToken<List<Song>>() {}.type
        songs = (activity as MainActivity).gson?.fromJson(
            requireArguments().getString("songs"),
            listType
        )!!
        val albums = (activity as MainActivity).musicManager?.albums
        songs.forEach { song ->
            song.parentAlbum = albums?.find { it.id == song.parentAlbumId }
        }
        val list = view.findViewById<RecyclerView>(R.id.list)
        list.layoutManager = LinearLayoutManager(
            requireContext(), LinearLayoutManager.VERTICAL, false
        )
        list.adapter = Adapter()
        view.findViewById<View>(R.id.close).setOnClickListener {
            listener.onClose()
            parentFragmentManager.beginTransaction().remove(this).commit()
        }
        return view
    }


    inner class Adapter : RecyclerView.Adapter<ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(inflater.inflate(R.layout.view_holder_added_song, parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) =
            holder.bind(songs[position])

        override fun getItemCount() = songs.size
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.title)
        private val name: TextView = itemView.findViewById(R.id.name)
        private val kind: TextView = itemView.findViewById(R.id.kind)

        fun bind(song: Song) {
            title.text = song.parentAlbum?.name
            name.text = song.name
            kind.text = when (song.primaryUsage) {
                Song.PrimaryUsage.Assets -> getString(R.string.added)
                Song.PrimaryUsage.Files -> getString(R.string.updated)
            }
        }
    }
}