package com.suzukiplan.tohovgs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.ToggleButton
import androidx.fragment.app.Fragment
import com.suzukiplan.tohovgs.api.MusicManager
import com.suzukiplan.tohovgs.api.Settings

class PlaybackSettingsFragment : Fragment(), SeekBar.OnSeekBarChangeListener {
    companion object {
        fun create(listener: Listener): PlaybackSettingsFragment {
            val result = PlaybackSettingsFragment()
            result.listener = listener
            return result
        }
    }

    interface Listener {
        fun onClose()
    }

    private lateinit var listener: Listener
    private lateinit var masterVolumeText: TextView
    private lateinit var masterVolumeSeekBar: SeekBar
    private lateinit var playbackSpeedText: TextView
    private lateinit var playbackSpeedSeekBar: SeekBar
    private lateinit var kobushiToggle: ToggleButton
    private var settings: Settings? = null
    private var musicManager: MusicManager? = null
    private var masterVolume = 0
    private var playbackSpeed = 0
    private var kobushi = false


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        settings = (activity as? MainActivity)?.settings
        musicManager = (activity as? MainActivity)?.musicManager
        val view = inflater.inflate(R.layout.fragment_playback_settings, container, false)
        masterVolumeText = view.findViewById(R.id.master_volume_text)
        masterVolumeSeekBar = view.findViewById(R.id.master_volume_seek_bar)
        masterVolumeSeekBar.setOnSeekBarChangeListener(this)
        masterVolumeSeekBar.progress = settings?.masterVolume ?: 100
        playbackSpeedText = view.findViewById(R.id.playback_speed_text)
        playbackSpeedSeekBar = view.findViewById(R.id.playback_speed_seek_bar)
        playbackSpeedSeekBar.setOnSeekBarChangeListener(this)
        playbackSpeedSeekBar.progress = ((settings?.playbackSpeed ?: 100) - 25) / 5
        kobushiToggle = view.findViewById(R.id.kobusi_toggle)
        kobushiToggle.isChecked = 0 != settings?.compatKobushi
        kobushiToggle.setOnCheckedChangeListener { _, on ->
            settings?.compatKobushi = if (on) 1 else 0
        }
        view.findViewById<View>(R.id.reset).setOnClickListener {
            masterVolumeSeekBar.progress = 100
            playbackSpeedSeekBar.progress = (100 - 25) / 5
            kobushiToggle.isChecked = false
        }
        view.findViewById<View>(R.id.close).setOnClickListener {
            listener.onClose()
            parentFragmentManager.beginTransaction().remove(this).commit()
        }
        return view
    }

    override fun onStartTrackingTouch(p0: SeekBar?) {}
    override fun onStopTrackingTouch(p0: SeekBar?) {}
    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, byUser: Boolean) {
        when (seekBar) {
            masterVolumeSeekBar -> {
                settings?.masterVolume = progress
                musicManager?.changeMasterVolume(progress)
                masterVolumeText.text = getString(R.string.master_volume, progress)
            }
            playbackSpeedSeekBar -> {
                val speed = progress * 5 + 25
                settings?.playbackSpeed = speed
                playbackSpeedText.text =
                    getString(R.string.playback_speed_setting, speed / 100, speed % 100)
            }
        }
    }
}