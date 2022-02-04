/**
 * ©2022, SUZUKI PLAN
 * License: https://github.com/suzukiplan/tohovgs4-android/blob/master/LICENSE.txt
 */
package com.suzukiplan.tohovgs

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.ads.*
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.gson.Gson
import com.suzukiplan.tohovgs.api.*
import com.suzukiplan.tohovgs.model.Album
import com.suzukiplan.tohovgs.model.Song
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), SongListFragment.Listener {
    lateinit var settings: Settings
    private lateinit var progress: View
    private lateinit var adContainer: ViewGroup
    private lateinit var adBgImage: View
    private lateinit var adBgText: View
    private lateinit var fragmentContainer: ViewGroup
    private lateinit var currentPage: Page
    private lateinit var playTime: TextView
    private lateinit var leftTime: TextView
    private lateinit var seekBar: AppCompatSeekBar
    private lateinit var seekBarContainer: View
    private lateinit var badge: ImageView
    private var footers = HashMap<Page, View>()
    private var currentFragment: Fragment? = null
    private var currentLength = 0
    private var seekBarTouching = false
    lateinit var musicManager: MusicManager
    lateinit var gson: Gson
    lateinit var api: WebAPI
    private val executor = Executors.newFixedThreadPool(8)
    private var initialized = false
    fun executeAsync(task: () -> Unit) = executor.submit(task)!!

    enum class Page(val value: Pair<Int, String>) {
        NotSelected(Pair(0, "")),
        PerTitle(Pair(1, "home")),
        Sequential(Pair(2, "all")),
        Shuffle(Pair(3, "shuffle")),
        Retro(Pair(4, "retro")),
        Settings(Pair(5, "settings")),
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        progress = findViewById(R.id.progress)
        adContainer = findViewById(R.id.ad_container)
        adBgImage = findViewById(R.id.ad_bg_image)
        adBgText = findViewById(R.id.ad_bg_text)
        fragmentContainer = findViewById(R.id.fragment_container)
        playTime = findViewById(R.id.play_time)
        leftTime = findViewById(R.id.left_time)
        seekBar = findViewById(R.id.seek_bar)
        seekBarContainer = findViewById(R.id.seek_bar_container)
        badge = findViewById(R.id.badge)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    playTime.text = duration(progress)
                    leftTime.text = duration(currentLength - progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                seekBarTouching = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBarTouching = false
                musicManager.seek(seekBar?.progress)
            }
        })
        footers.clear()
        footers[Page.PerTitle] = findViewById(R.id.footer_per_title)
        footers[Page.Sequential] = findViewById(R.id.footer_sequential)
        footers[Page.Shuffle] = findViewById(R.id.footer_shuffle)
        footers[Page.Retro] = findViewById(R.id.footer_retro_ui)
        footers[Page.Settings] = findViewById(R.id.footer_settings)
        footers.forEach { (page, view) -> view.setOnClickListener { movePage(page) } }
        findViewById<SwitchCompat>(R.id.infinity).setOnCheckedChangeListener { _, checked ->
            musicManager.infinity = checked
        }
        currentPage = Page.NotSelected
        executeAsync {
            initialize {
                runOnUiThread {
                    resetSeekBar()
                    movePage(settings.pageName)
                }
            }
        }
    }

    private fun initialize(done: () -> Unit) {
        settings = Settings(this)
        gson = Gson()
        api = WebAPI(this)
        musicManager = MusicManager(this).load()
        musicManager.initialize()
        val adConfig = RequestConfiguration.Builder()
            .setTestDeviceIds(
                listOf(
                    BuildConfig.TEST_DEVICE_ID_1,
                    BuildConfig.TEST_DEVICE_ID_2
                )
            )
            .build()
        MobileAds.setRequestConfiguration(adConfig)
        MobileAds.initialize(this) {
            Logger.d("MobileAds initialized: $it")
            val adView = AdView(this)
            adView.adSize = AdSize.BANNER
            adView.adUnitId = Constants.bannerAdsId
            val layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT, 1)
            adView.layoutParams = layoutParams
            adView.adListener = object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    super.onAdFailedToLoad(error)
                    Logger.e("Failed to load ad: $error")
                }

                override fun onAdLoaded() {
                    super.onAdLoaded()
                    Logger.d("Ad loaded")
                    adBgImage.visibility = View.GONE
                    adBgText.visibility = View.GONE
                }
            }
            runOnUiThread { adContainer.addView(adView) }
            val request = AdRequest.Builder().build()
            adView.loadAd(request)
        }
        if (!settings.badge) {
            api.check(musicManager.version) { updatable ->
                runOnUiThread {
                    badge.visibility = if (true == updatable) {
                        settings.badge = true
                        View.VISIBLE
                    } else View.GONE
                }
            }
        } else {
            runOnUiThread {
                badge.visibility = View.VISIBLE
            }
        }
        initialized = true
        done.invoke()
    }

    @SuppressLint("SetTextI18n")
    private fun resetSeekBar() {
        seekBar.min = 0
        seekBar.max = 0
        playTime.text = "00:00"
        leftTime.text = "00:00"
        currentLength = 0
        seekBarTouching = false
    }

    private fun movePage(name: String?) = movePage(
        when (name) {
            Page.Sequential.value.second -> Page.Sequential
            Page.Retro.value.second -> Page.Retro
            else -> Page.PerTitle
        }
    )

    private fun movePage(page: Page) {
        if (currentPage != Page.NotSelected && page == currentPage) return
        stopSong()
        seekBarContainer.visibility = if (page == Page.Retro || page == Page.Settings) {
            View.GONE
        } else {
            View.VISIBLE
        }
        val fragment = when (page) {
            Page.NotSelected -> return
            Page.PerTitle -> AlbumPagerFragment.create()
            Page.Sequential -> SongListFragment.createAsSequential()
            Page.Shuffle -> SongListFragment.createAsShuffle()
            Page.Retro -> RetroFragment.create()
            Page.Settings -> SettingsFragment.create()
        }
        if (fragment is SongListFragment) fragment.listener = this
        val transaction = supportFragmentManager.beginTransaction()
        if (currentPage != Page.NotSelected) {
            if (currentPage.value.first < page.value.first) {
                transaction.setCustomAnimations(
                    R.anim.slide_in_from_right,
                    R.anim.slide_out_to_left
                )
            } else {
                transaction.setCustomAnimations(
                    R.anim.slide_in_from_left,
                    R.anim.slide_out_to_right
                )
            }
        }
        transaction.replace(fragmentContainer.id, fragment)
        currentFragment = fragment
        transaction.commit()
        if (currentPage == Page.NotSelected) {
            footers[Page.PerTitle]?.setBackgroundResource(R.drawable.bottom_menu_unselected)
            footers[page]?.setBackgroundResource(R.drawable.bottom_menu_selected)
        } else {
            footers[currentPage]?.setBackgroundResource(R.drawable.bottom_menu_unselected)
            executeAsync {
                Thread.sleep(300L)
                runOnUiThread {
                    if (page == currentPage) {
                        footers[page]?.setBackgroundResource(R.drawable.bottom_menu_selected)
                    }
                }
            }
            settings.pageName = page.value.second
        }
        currentPage = page
    }

    private fun refreshAlbumPagerFragment() {
        if (currentPage != Page.PerTitle) return
        stopSong()
        val fragment = AlbumPagerFragment.create()
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(fragmentContainer.id, fragment)
        currentFragment = fragment
        transaction.commit()
    }

    override fun onBackPressed() = finish()

    override fun finish() {
        musicManager.stop()
        musicManager.terminate()
        super.finish()
    }

    override fun onRequestLock(song: Song, done: () -> Unit) {
        AskDialog.start(this, getString(R.string.ask_lock, song.name), object : AskDialog.Listener {
            override fun onClick(isYes: Boolean) {
                if (isYes) {
                    val previousStatus = musicManager.isExistLockedSong(settings)
                    settings.lock(song)
                    if (!previousStatus) {
                        refreshAlbumPagerFragment()
                    }
                    done()
                }
            }
        })
    }

    override fun onRequestUnlock(album: Album, done: (unlocked: Boolean) -> Unit) {
        val message = getString(R.string.ask_unlock, album.name)
        AskDialog.start(this, message, object : AskDialog.Listener {
            override fun onClick(isYes: Boolean) {
                if (!isYes) return
                startRewardForUnlock(album, done)
            }
        })
    }

    override fun onRequestUnlockAll() {
        val message = getString(R.string.ask_unlock_all)
        AskDialog.start(this, message, object : AskDialog.Listener {
            override fun onClick(isYes: Boolean) {
                if (!isYes) return
                startRewardForUnlock(null, null)
            }
        })
    }

    private fun startRewardForUnlock(album: Album?, done: ((unlocked: Boolean) -> Unit)?) {
        startProgress()
        stopSong()
        if (null == album) {
            Logger.d("Unlocking all songs")
        } else {
            Logger.d("Unlocking ${album.name}")
        }
        val request = AdManagerAdRequest.Builder().build()
        val callback = object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(error: LoadAdError) {
                super.onAdFailedToLoad(error)
                Logger.e("Rewarded Ad load failed: $error")
                endProgress()
                if (3 == error.code) {
                    MessageDialog.start(this@MainActivity, getString(R.string.error_ads_no_config))
                    if (null == album) {
                        musicManager.albums.forEach { settings.unlock(it) }
                        refreshAlbumPagerFragment()
                    } else {
                        settings.unlock(album)
                        if (!musicManager.isExistLockedSong(settings)) {
                            refreshAlbumPagerFragment()
                        }
                    }
                    done?.invoke(true)
                } else {
                    MessageDialog.start(this@MainActivity, getString(R.string.error_ads))
                    done?.invoke(false)
                }
            }

            override fun onAdLoaded(ad: RewardedAd) {
                super.onAdLoaded(ad)
                ad.fullScreenContentCallback =
                    object : FullScreenContentCallback() {
                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            super.onAdFailedToShowFullScreenContent(error)
                            Logger.e("onAdFailedToShowFullScreenContent: $error")
                            endProgress()
                            MessageDialog.start(this@MainActivity, getString(R.string.error_ads))
                            done?.invoke(false)
                        }

                        override fun onAdDismissedFullScreenContent() {
                            super.onAdDismissedFullScreenContent()
                            endProgress()
                        }
                    }
                ad.show(this@MainActivity) { rewardItem ->
                    Logger.d("RewardItem: type=${rewardItem.type}, amount=${rewardItem.amount}")
                    if (null == album) {
                        musicManager.albums.forEach { settings.unlock(it) }
                        refreshAlbumPagerFragment()
                    } else {
                        settings.unlock(album)
                        if (!musicManager.isExistLockedSong(settings)) {
                            refreshAlbumPagerFragment()
                        }
                    }
                    endProgress()
                    done?.invoke(true)
                }
            }
        }
        RewardedAd.load(this@MainActivity, Constants.rewardAdsId, request, callback)
    }

    private fun duration(sec: Int) = String.format("%02d:%02d", sec / 60, sec % 60)

    private fun seek(length: Int, time: Int) {
        if (!seekBarTouching) {
            runOnUiThread {
                val left = length - time
                seekBar.max = length
                seekBar.progress = time
                playTime.text = duration(time)
                leftTime.text = duration(left)
                currentLength = length
            }
        }
    }

    override fun onPlay(album: Album, song: Song, onPlayEnded: () -> Unit) {
        if (musicManager.isPlaying(album, song)) {
            Logger.d("pause/resume ${song.name}")
            musicManager.pause(seekBar.progress, { length, time -> seek(length, time) }) {
                resetSeekBar()
                onPlayEnded.invoke()
            }
        } else {
            Logger.d("play ${song.name}")
            musicManager.play(this, album, song, { length, time -> seek(length, time) }, {
                resetSeekBar()
                onPlayEnded.invoke()
            }, 0)
        }
    }

    override fun onPause() {
        if (initialized) {
            musicManager.isBackground = true
            musicManager.startJob(this)
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (initialized) {
            musicManager.stopJob(this)
            musicManager.isBackground = false
        }
    }

    fun startProgress() {
        runOnUiThread {
            progress.visibility = View.VISIBLE
        }
    }

    fun endProgress() {
        runOnUiThread {
            progress.visibility = View.GONE
        }
    }

    fun stopSong() {
        musicManager.stop()
        resetSeekBar()
    }

    fun showInterstitialAd() {
        val request = AdRequest.Builder().build()
        InterstitialAd.load(
            this,
            Constants.interstitialAdsId,
            request,
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Logger.e("Failed to load ad: ${adError.message}")
                }

                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    Logger.d("Loaded interstitial ad: ${interstitialAd.adUnitId}")
                    interstitialAd.show(this@MainActivity)
                }
            })
    }

    fun showAddedSongs(songs: List<Song>) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.modal_fragment_container, AddedSongsFragment.create(this, songs))
            .commit()
    }

    fun hideBadge() {
        badge.visibility = View.GONE
        settings.badge = false
    }
}