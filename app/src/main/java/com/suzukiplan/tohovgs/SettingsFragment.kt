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
import com.suzukiplan.tohovgs.api.MusicManager
import com.suzukiplan.tohovgs.api.Settings
import com.suzukiplan.tohovgs.api.WebAPI

class SettingsFragment : Fragment(), SeekBar.OnSeekBarChangeListener {
    companion object {
        fun create() = SettingsFragment()
    }

    private lateinit var musicManager: MusicManager
    private lateinit var api: WebAPI
    private lateinit var settings: Settings
    private lateinit var masterVolumeText: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        val mainActivity = activity as MainActivity
        musicManager = mainActivity.musicManager!!
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
        musicManager.changeMasterVolume(progress)
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {
        val album = musicManager.albums?.get(0) ?: return
        val song = album.songs[0]
        musicManager.play(context, album, song)
    }

    override fun onStopTrackingTouch(seekBar: SeekBar?) {
        musicManager.stop()
        settings.masterVolume = seekBar?.progress ?: return
    }

    private fun updateSongList() {
    }
}