/**
 * ©2022, SUZUKI PLAN
 * License: https://github.com/suzukiplan/tohovgs4-android/blob/master/LICENSE.txt
 */
package com.suzukiplan.tohovgs

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.android.billingclient.api.*
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
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity(), SongListFragment.Listener {
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
    var settings: Settings? = null
    var musicManager: MusicManager? = null
    var gson: Gson? = null
    var api: WebAPI? = null
    private val executor = Executors.newFixedThreadPool(8)
    private var initialized = false
    fun executeAsync(task: () -> Unit) = executor.submit(task)!!
    private var pausing = true
    private val procedureQueue = ArrayList<() -> Unit>(0)
    private lateinit var billingClient: BillingClient
    private var billingClientReady = false
    private var billingProducts = ArrayList<SkuDetails>()
    private var adView: AdView? = null
    private val skuRemoveRewardAds = "remove_reward_ads"
    private val skuRemoveBannerAds = "remove_banner_ads"

    fun executeWhileResume(procedure: () -> Unit) {
        if (pausing) {
            procedureQueue.add(procedure)
        } else {
            procedure.invoke()
        }
    }

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
                musicManager?.seek(seekBar?.progress)
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
            musicManager?.infinity = checked
        }
        setupBillingClient()
        val adConfig = RequestConfiguration.Builder()
            .setTestDeviceIds(
                listOf(
                    BuildConfig.TEST_DEVICE_ID_1,
                    BuildConfig.TEST_DEVICE_ID_2
                )
            )
            .build()
        MobileAds.setRequestConfiguration(adConfig)
        currentPage = Page.NotSelected
        executeAsync {
            initialize {
                runOnUiThread {
                    setupBanner()
                    resetSeekBar()
                    movePage(settings?.pageName)
                }
            }
        }
    }

    private fun setupBanner() {
        if (settings?.removeBannerAds != true) {
            executeAsync {
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
                    this.adView = adView
                }
            }
        }
    }

    private fun initialize(done: () -> Unit) {
        settings = Settings(this)
        gson = Gson()
        api = WebAPI(this)
        musicManager = MusicManager(this).load()
        musicManager?.initialize()
        if (false == settings?.badge) {
            api?.check(musicManager?.version) { updatable ->
                runOnUiThread {
                    badge.visibility = if (true == updatable) {
                        settings?.badge = true
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

    private fun movePage(page: Page) = executeWhileResume { movePageInternal(page) }
    private fun movePageInternal(page: Page) {
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
            settings?.pageName = page.value.second
        }
        currentPage = page
    }

    private fun refreshAlbumPagerFragment() {
        if (currentPage != Page.PerTitle) return
        stopSong()
        executeWhileResume {
            val fragment = AlbumPagerFragment.create()
            val transaction = supportFragmentManager.beginTransaction()
            transaction.replace(fragmentContainer.id, fragment)
            currentFragment = fragment
            transaction.commit()
        }
    }

    override fun onBackPressed() = finish()

    override fun finish() {
        super.finish()
        executeAsync {
            val currentFragment = this.currentFragment
            if (null != currentFragment && currentFragment is RetroFragment) {
                currentFragment.stopRenderThread()
            }
            musicManager?.stop()
            musicManager?.terminate()
            musicManager = null
            settings?.commit()
            exitProcess(0)
        }
    }

    override fun onRequestLock(song: Song, done: () -> Unit) {
        if (true == settings?.removeRewardAds) {
            doLock(song, done)
            return
        }
        AskDialog.start(this, getString(R.string.ask_lock, song.name), object : AskDialog.Listener {
            override fun onClick(isYes: Boolean) {
                if (isYes) {
                    doLock(song, done)
                }
            }
        })
    }

    private fun doLock(song: Song, done: () -> Unit) {
        val previousStatus = musicManager?.isExistLockedSong(settings)
        settings?.lock(song)
        if (false == previousStatus) {
            refreshAlbumPagerFragment()
        }
        done()
    }

    override fun onLongPressed(song: Song, unlocked: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(song.name)
            .setItems(
                arrayOf(
                    getString(R.string.apple_music),
                    getString(R.string.lock_song)
                )
            ) { _, index ->
                when (index) {
                    0 -> {
                        Logger.d("appleId: album=${song.parentAlbum?.appleId} song=${song.appleId}")
                        if (null != song.appleMusicURL) {
                            startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(song.appleMusicURL)
                                )
                            )
                        } else {
                            MessageDialog.start(this, getString(R.string.apple_music_not_exist))
                        }
                    }
                    1 -> doLock(song, unlocked)
                }
            }
            .show()
    }

    override fun onRequestUnlockAll(done: ((unlocked: Boolean) -> Unit)?) {
        if (true == settings?.removeRewardAds) {
            musicManager?.albums?.forEach { settings?.unlock(it) }
            refreshAlbumPagerFragment()
            done?.invoke(true)
            return
        }
        val message = getString(R.string.ask_unlock_all)
        AskDialog.start(this, message, object : AskDialog.Listener {
            override fun onClick(isYes: Boolean) {
                if (!isYes) return
                if (true == settings?.badge) {
                    AskDialog.start(
                        this@MainActivity,
                        getString(R.string.ask_download_before_unlock),
                        getString(R.string.answer_download),
                        getString(R.string.answer_unlock),
                        object : AskDialog.Listener {
                            override fun onClick(isYes: Boolean) {
                                if (isYes) {
                                    movePage(Page.Settings)
                                } else {
                                    startRewardForUnlock(done)
                                }
                            }
                        })
                } else {
                    startRewardForUnlock(done)
                }
            }
        })
    }

    private fun startRewardForUnlock(done: ((unlocked: Boolean) -> Unit)?) {
        startProgress()
        stopSong()
        val request = AdManagerAdRequest.Builder().build()
        val callback = object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(error: LoadAdError) {
                super.onAdFailedToLoad(error)
                Logger.e("Rewarded Ad load failed: $error")
                endProgress()
                if (3 == error.code) {
                    MessageDialog.start(this@MainActivity, getString(R.string.error_ads_no_config))
                    musicManager?.albums?.forEach { settings?.unlock(it) }
                    refreshAlbumPagerFragment()
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
                    musicManager?.albums?.forEach { settings?.unlock(it) }
                    refreshAlbumPagerFragment()
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
        if (true == musicManager?.isPlaying(album, song)) {
            Logger.d("pause/resume ${song.name}")
            musicManager?.pause(seekBar.progress, { length, time -> seek(length, time) }) {
                resetSeekBar()
                onPlayEnded.invoke()
            }
        } else {
            Logger.d("play ${song.name}")
            musicManager?.play(this, album, song, { length, time -> seek(length, time) }, {
                resetSeekBar()
                onPlayEnded.invoke()
            }, 0)
        }
    }

    override fun onPause() {
        pausing = true
        if (initialized) {
            musicManager?.isBackground = true
            musicManager?.startJob(this)
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (initialized) {
            musicManager?.stopJob(this)
            musicManager?.isBackground = false
        }
        pausing = false
        while (procedureQueue.isNotEmpty()) {
            procedureQueue.removeAt(0).invoke()
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
        musicManager?.stop()
        resetSeekBar()
    }

    fun showInterstitialAd() {
        if (true == settings?.removeBannerAds) {
            return
        }
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
        executeWhileResume {
            supportFragmentManager.beginTransaction()
                .replace(
                    R.id.modal_fragment_container,
                    AddedSongsFragment.create(this, songs, object : AddedSongsFragment.Listener {
                        override fun onClose() {
                            onRequestUnlockAll { Logger.d("unlocked: $it") }
                        }
                    })
                )
                .commit()
        }
    }

    fun hideBadge() {
        badge.visibility = View.GONE
        settings?.badge = false
    }

    private fun setupBillingClient() {
        billingClient = BillingClient.newBuilder(this)
            .setListener { result, purchases -> proceedPurchases(result, purchases) }
            .enablePendingPurchases()
            .build()
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() {
            }

            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    val skuList = ArrayList<String>(2)
                    skuList.add(skuRemoveRewardAds)
                    skuList.add(skuRemoveBannerAds)
                    val params = SkuDetailsParams.newBuilder()
                        .setSkusList(skuList)
                        .setType(BillingClient.SkuType.INAPP)
                        .build()
                    billingClient.querySkuDetailsAsync(params) { result2, skuDetails ->
                        if (result2.responseCode == BillingClient.BillingResponseCode.OK) {
                            skuDetails?.forEach { skuDetail ->
                                Logger.d("sku = ${skuDetail.sku}, title = ${skuDetail.title} price = ${skuDetail.price}")
                                billingProducts.add(skuDetail)
                            }
                            billingClientReady = true
                        }
                    }
                }
            }
        })
    }

    fun getPriceOfRemoveRewardAds() =
        billingProducts.find { it.sku == skuRemoveRewardAds }?.price

    fun getPriceOfRemoveBannerAds() =
        billingProducts.find { it.sku == skuRemoveBannerAds }?.price

    fun purchaseRemoveRewardAds() = purchase(skuRemoveRewardAds)

    fun purchaseRemoveBannerAds() = purchase(skuRemoveBannerAds)

    private fun purchase(sku: String) {
        startProgress()
        billingClient.querySkuDetailsAsync(
            SkuDetailsParams.newBuilder()
                .setType(BillingClient.SkuType.INAPP)
                .setSkusList(listOf(sku))
                .build()
        ) { skuResult, skuDetails ->
            endProgress()
            if (null == skuDetails || skuResult.responseCode != BillingClient.BillingResponseCode.OK) {
                MessageDialog.start(this, getString(R.string.cannot_connect_google_play))
                return@querySkuDetailsAsync
            }
            val skuDetail = skuDetails[0]
            val message = when (sku) {
                skuRemoveRewardAds -> getString(R.string.remove_reward_ads_about)
                skuRemoveBannerAds -> getString(R.string.remove_banner_ads_about)
                else -> ""
            }
            AskDialog.start(
                this,
                message,
                getString(R.string.purchase),
                getString(R.string.cancel),
                object : AskDialog.Listener {
                    override fun onClick(isYes: Boolean) {
                        if (isYes) {
                            startPurchase(skuDetail)
                        }
                    }
                })
        }
    }

    private fun startPurchase(skuDetail: SkuDetails) {
        val params = BillingFlowParams.newBuilder()
            .setSkuDetails(skuDetail)
            .build()
        val responseCode = billingClient.launchBillingFlow(this, params).responseCode
        Logger.d("ResponseCode: $responseCode")
    }

    fun restorePurchase() {
        startProgress()
        billingClient.queryPurchasesAsync(BillingClient.SkuType.INAPP) { result, purchases ->
            proceedPurchases(result, purchases)
            executeAsync {
                Thread.sleep(1500)
                runOnUiThread {
                    endProgress()
                }
            }
        }
    }

    private fun proceedPurchases(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode != BillingClient.BillingResponseCode.OK) return
        purchases?.forEach { purchase ->
            purchase.skus.forEach { sku ->
                Logger.d("checking sku: $sku")
                when (sku) {
                    skuRemoveRewardAds -> {
                        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                            settings?.removeRewardAds = true
                        }
                    }
                    skuRemoveBannerAds -> {
                        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                            settings?.removeBannerAds = true
                            val adView = this.adView
                            if (null != adView) {
                                runOnUiThread {
                                    adContainer.removeView(adView)
                                    adBgImage.visibility = View.VISIBLE
                                    adBgText.visibility = View.VISIBLE
                                }
                                this.adView = null
                            }
                        }
                    }
                }
            }
            if (!purchase.isAcknowledged && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { result ->
                    Logger.d("acknowledgePurchase: $result")
                }
            }
        }
    }
}