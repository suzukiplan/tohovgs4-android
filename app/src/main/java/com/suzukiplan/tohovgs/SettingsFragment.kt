/**
 * ©2022, SUZUKI PLAN
 * License: https://github.com/suzukiplan/tohovgs4-android/blob/master/LICENSE.txt
 */
package com.suzukiplan.tohovgs

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.AppCompatToggleButton
import androidx.fragment.app.Fragment
import com.suzukiplan.tohovgs.api.Logger
import com.suzukiplan.tohovgs.api.Settings
import com.suzukiplan.tohovgs.model.Song

class SettingsFragment : Fragment(), SeekBar.OnSeekBarChangeListener {
    companion object {
        fun create() = SettingsFragment()
    }

    private lateinit var mainActivity: MainActivity
    private val settings: Settings? get() = mainActivity.settings
    private lateinit var masterVolumeSeekBar: SeekBar
    private lateinit var masterVolumeText: TextView
    private lateinit var playbackSpeedSeekBar: SeekBar
    private lateinit var playbackSpeedText: TextView
    private var checked = false

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        mainActivity = activity as MainActivity
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        view.findViewById<TextView>(R.id.version).text = "Version ${BuildConfig.VERSION_NAME}"
        view.findViewById<View>(R.id.download).setOnClickListener { updateSongList() }
        val kobusiToggle = view.findViewById<AppCompatToggleButton>(R.id.kobusi_toggle)
        kobusiToggle.isChecked = 0 != mainActivity.settings?.compatKobushi
        kobusiToggle.setOnCheckedChangeListener { _, value ->
            val before = mainActivity.settings?.compatKobushi
            mainActivity.settings?.compatKobushi = if (value) 1 else 0
            Logger.d("KoBuSi: $before -> ${mainActivity.settings?.compatKobushi}")
        }
        masterVolumeText = view.findViewById(R.id.master_volume_text)
        masterVolumeSeekBar = view.findViewById(R.id.master_volume_seek_bar)
        masterVolumeSeekBar.setOnSeekBarChangeListener(this)
        masterVolumeSeekBar.progress = settings?.masterVolume ?: 100
        playbackSpeedText = view.findViewById(R.id.playback_speed_text)
        playbackSpeedSeekBar = view.findViewById(R.id.playback_speed_seek_bar)
        playbackSpeedSeekBar.setOnSeekBarChangeListener(this)
        playbackSpeedSeekBar.progress = ((settings?.playbackSpeed ?: 100) - 25) / 5
        view.findViewById<View>(R.id.twitter).setOnClickListener {
            mainActivity.safeStartIntent(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://twitter.com/suzukiplan")
                )
            )
        }
        view.findViewById<View>(R.id.youtube).setOnClickListener {
            mainActivity.safeStartIntent(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.youtube.com/channel/UCAIpmEfeuTAXQ0ERTSkb6oA")
                )
            )
        }
        view.findViewById<View>(R.id.tiktok).setOnClickListener {
            mainActivity.safeStartIntent(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.tiktok.com/@suzukiplan")
                )
            )
        }
        view.findViewById<View>(R.id.github).setOnClickListener {
            mainActivity.safeStartIntent(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://suzukiplan.github.io/tohovgs4-android")
                )
            )
        }
        view.findViewById<View>(R.id.apple_music).setOnClickListener {
            mainActivity.safeStartIntent(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://music.apple.com/jp/artist/1190977068")
                )
            )
        }
        val removeRewardAdsButton = view.findViewById<TextView>(R.id.remove_reward_ads)
        removeRewardAdsButton.text = getString(
            R.string.remove_reward_ads_with_price,
            mainActivity.getPriceOfRemoveRewardAds()
        )
        if (true == settings?.removeRewardAds) {
            removeRewardAdsButton.isEnabled = false
            removeRewardAdsButton.setBackgroundResource(R.drawable.button_disabled)
        } else {
            removeRewardAdsButton.setOnClickListener {
                mainActivity.purchaseRemoveRewardAds()
            }
        }
        val removeBannerAdsButton = view.findViewById<TextView>(R.id.remove_banner_ads)
        removeBannerAdsButton.text = getString(
            R.string.remove_banner_ads_with_price,
            mainActivity.getPriceOfRemoveBannerAds()
        )
        if (true == settings?.removeBannerAds) {
            removeBannerAdsButton.isEnabled = false
            removeBannerAdsButton.setBackgroundResource(R.drawable.button_disabled)
        } else {
            removeBannerAdsButton.setOnClickListener {
                mainActivity.purchaseRemoveBannerAds()
            }
        }
        val restoreButton = view.findViewById<View>(R.id.restore_purchase)
        if (true == settings?.removeRewardAds && true == settings?.removeBannerAds) {
            restoreButton.isEnabled = false
            restoreButton.setBackgroundResource(R.drawable.button_disabled)
        } else {
            restoreButton.setOnClickListener {
                mainActivity.restorePurchase()
            }
        }
        return view
    }

    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        when (seekBar) {
            masterVolumeSeekBar -> {
                masterVolumeText.text = getString(R.string.master_volume, progress)
                mainActivity.musicManager?.changeMasterVolume(progress)
                settings?.masterVolume = progress
            }
            playbackSpeedSeekBar -> {
                val speed = (progress * 5) + 25
                mainActivity.settings?.playbackSpeed = speed
                playbackSpeedText.text =
                    getString(R.string.playback_speed_setting, speed / 100, speed % 100)
            }
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {
        if (seekBar == masterVolumeSeekBar) {
            val album = mainActivity.musicManager?.albums?.get(0)
            val song = album?.songs?.get(0)
            mainActivity.musicManager?.play(context, album, song)
        }
    }

    override fun onStopTrackingTouch(seekBar: SeekBar?) {
        if (seekBar == masterVolumeSeekBar) {
            mainActivity.musicManager?.stop()
        }
    }

    private fun updateSongList() {
        if (checked) {
            msg(getString(R.string.up_to_date))
            return
        }
        mainActivity.startProgress()
        mainActivity.api?.check(mainActivity.musicManager?.version) { updatable ->
            Thread.sleep(1000L)
            if (null == updatable) {
                msg(getString(R.string.communication_error, mainActivity.api?.lastStatusCode))
                return@check
            }
            if (!updatable) {
                msg(getString(R.string.up_to_date))
                checked = true
                return@check
            }
            mainActivity.api?.downloadSongList { songList ->
                if (null == songList) {
                    msg(getString(R.string.communication_error, mainActivity.api?.lastStatusCode))
                    return@downloadSongList
                }
                songList.albums.forEach { album ->
                    album.songs.forEach { song ->
                        song.primaryUsage = Song.PrimaryUsage.Assets
                    }
                }
                Logger.d("check need download mml files...")
                val downloadSongs = ArrayList<Song>()
                mainActivity.runOnUiThread {
                    songList.albums.forEach { album ->
                        album.songs.forEach { song ->
                            Logger.d("check ${song.mml}.mml")
                            if (!song.checkExistMML(requireContext())) {
                                Logger.d("need download: ${song.name}")
                                song.parentAlbumId = album.id
                                downloadSongs.add(song)
                            } else {
                                val currentSong =
                                    mainActivity.musicManager?.searchSongOfMML(song.mml)
                                if (null != currentSong) {
                                    if (currentSong.ver < song.ver) {
                                        downloadSongs.add(song)
                                        song.primaryUsage = Song.PrimaryUsage.Files
                                    }
                                }
                            }
                        }
                    }
                    mainActivity.executeAsync {
                        Logger.d("need download files: ${downloadSongs.size}")
                        var error = false
                        downloadSongs.forEach { song ->
                            val mml = mainActivity.api?.downloadMML(song)
                            if (null == mml) {
                                error = true
                            } else {
                                song.getDownloadFile(context).writeText(mml, Charsets.UTF_8)
                            }
                        }
                        if (error) {
                            msg(
                                getString(
                                    R.string.communication_error,
                                    mainActivity.api?.lastStatusCode
                                )
                            )
                        } else {
                            mainActivity.runOnUiThread {
                                mainActivity.hideBadge()
                                mainActivity.musicManager?.updateSongList(songList)
                                if (downloadSongs.size < 1) {
                                    msg(getString(R.string.update_list_only))
                                } else {
                                    mainActivity.endProgress()
                                    mainActivity.showAddedSongs(downloadSongs)
                                }
                            }
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