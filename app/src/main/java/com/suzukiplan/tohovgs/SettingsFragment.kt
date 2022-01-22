/**
 * ©2022, SUZUKI PLAN
 * License: https://github.com/suzukiplan/tohovgs4-android/blob/master/LICENSE.txt
 */
package com.suzukiplan.tohovgs

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.suzukiplan.tohovgs.api.Settings
import com.suzukiplan.tohovgs.api.WebAPI
import com.suzukiplan.tohovgs.model.Song

class SettingsFragment : Fragment(), SeekBar.OnSeekBarChangeListener {
    companion object {
        fun create() = SettingsFragment()
    }

    private lateinit var mainActivity: MainActivity
    private lateinit var api: WebAPI
    private lateinit var settings: Settings
    private lateinit var masterVolumeText: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        mainActivity = activity as MainActivity
        api = mainActivity.api
        settings = Settings(context)
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        view.findViewById<View>(R.id.download).setOnClickListener { updateSongList() }
        masterVolumeText = view.findViewById(R.id.master_volume_text)
        val masterVolume = settings.masterVolume
        masterVolumeText.text = getString(R.string.master_volume, masterVolume)
        val seekBar = view.findViewById<SeekBar>(R.id.master_volume_seek_bar)
        seekBar.progress = masterVolume
        seekBar.setOnSeekBarChangeListener(this)
        view.findViewById<View>(R.id.twitter).setOnClickListener {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://twitter.com/suzukiplan")
                )
            )
        }
        view.findViewById<View>(R.id.github).setOnClickListener {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://suzukiplan.github.io/tohovgs4-android")
                )
            )
        }
        return view
    }

    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        masterVolumeText.text = getString(R.string.master_volume, progress)
        mainActivity.musicManager.changeMasterVolume(progress)
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {
        val album = mainActivity.musicManager.albums[0]
        val song = album.songs[0]
        mainActivity.musicManager.play(context, album, song)
    }

    override fun onStopTrackingTouch(seekBar: SeekBar?) {
        mainActivity.musicManager.stop()
        settings.masterVolume = seekBar?.progress ?: return
    }

    private fun updateSongList() {
        mainActivity.startProgress()
        mainActivity.api.check(mainActivity.musicManager.version) { updatable ->
            Thread.sleep(1000L)
            if (null == updatable) {
                msg(getString(R.string.communication_error, mainActivity.api.lastStatusCode))
                return@check
            }
            if (!updatable) {
                msg(getString(R.string.up_to_date))
                return@check
            }
            mainActivity.api.downloadSongList { songList ->
                if (null == songList) {
                    msg(getString(R.string.communication_error, mainActivity.api.lastStatusCode))
                    return@downloadSongList
                }
                val downloadSongs = ArrayList<Song>()
                songList.albums.forEach { album ->
                    album.songs.forEach { song ->
                        if (!song.checkExistMML(context)) {
                            downloadSongs.add(song)
                        }
                    }
                }
                var error = false
                downloadSongs.forEach { song ->
                    val mml = mainActivity.api.downloadMML(song)
                    if (null == mml) {
                        error = true
                    } else {
                        song.getDownloadFile(context).writeText(mml, Charsets.UTF_8)
                    }
                }
                if (error) {
                    msg(getString(R.string.communication_error, mainActivity.api.lastStatusCode))
                } else {
                    mainActivity.musicManager.updateSongList(songList)
                    if (downloadSongs.size < 1) {
                        msg(getString(R.string.update_list_only))
                    } else {
                        mainActivity.runOnUiThread {
                            mainActivity.showAddedSongs(downloadSongs)
                        }
                    }
                }
            }
        }
    }

    private fun msg(message: String) {
        mainActivity.runOnUiThread {
            mainActivity.endProgress()
            MessageDialog.start(mainActivity, message)
        }
    }
}