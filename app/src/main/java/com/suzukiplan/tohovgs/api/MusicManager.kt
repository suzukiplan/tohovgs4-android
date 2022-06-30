/**
 * ©2022, SUZUKI PLAN
 * License: https://github.com/suzukiplan/tohovgs4-android/blob/master/LICENSE.txt
 */
package com.suzukiplan.tohovgs.api

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import com.suzukiplan.tohovgs.MainActivity
import com.suzukiplan.tohovgs.model.Album
import com.suzukiplan.tohovgs.model.Song
import com.suzukiplan.tohovgs.model.SongList
import java.io.File
import java.util.*

class MusicManager(private val mainActivity: MainActivity) {
    val version: String get() = songList.version
    val albums: List<Album> get() = songList.albums
    private var locker = Object()
    private lateinit var songList: SongList
    private var vgsContext = 0L
    private var playingAlbum: Album? = null
    private var playingSong: Song? = null
    private var fadeoutExecuted = false
    var infinity = false
    var isBackground = false
    private var startedContext: Context? = null
    private var masterVolume = 100
    private var kobushi = 0
    private val downloadSongListFile: File get() = File("${mainActivity.filesDir}/songlist.json")
    private var playMonitor: Timer? = null

    fun isExistLockedSong(settings: Settings?): Boolean? {
        if (null == settings) {
            return null
        }
        return null != albums.find { album ->
            null != album.songs.find { settings.isLocked(it) }
        }
    }

    fun isExistUnlockedSong(settings: Settings?): Boolean {
        return null != albums.find { album ->
            null != album.songs.find { false == settings?.isLocked(it) }
        }
    }

    fun updateSongList(songList: SongList) {
        val json = mainActivity.gson?.toJson(songList) ?: return
        downloadSongListFile.writeText(json, Charsets.UTF_8)
        mainActivity.musicManager = load()
    }

    fun load(): MusicManager {
        changeMasterVolume(mainActivity.settings?.masterVolume ?: 100)
        val assetSongListInput = mainActivity.assets.open("songlist.json")
        val assetSongListJson = String(assetSongListInput.readBytes(), Charsets.UTF_8)
        assetSongListInput.close()
        val assetSongList = mainActivity.gson?.fromJson(assetSongListJson, SongList::class.java)
        val downloadSongListFile = this.downloadSongListFile
        val downloadSongListJson = if (downloadSongListFile.exists()) {
            downloadSongListFile.readText(Charsets.UTF_8)
        } else null
        val downloadSongList = if (null != downloadSongListJson) {
            mainActivity.gson?.fromJson(downloadSongListJson, SongList::class.java)
        } else {
            null
        }
        assetSongList?.albums?.forEach { album ->
            album.songs.forEach { song ->
                val downloadAlbum = downloadSongList?.albums?.find { it.id == album.id }
                val downloadSong = downloadAlbum?.songs?.find { it.mml == song.mml }
                song.primaryUsage = if (null != downloadSong) {
                    if (song.ver < downloadSong.ver) {
                        Song.PrimaryUsage.Files
                    } else {
                        Song.PrimaryUsage.Assets
                    }
                } else {
                    Song.PrimaryUsage.Assets
                }
            }
        }
        downloadSongList?.albums?.forEach { album ->
            album.songs.forEach { song ->
                val assetAlbum = assetSongList?.albums?.find { it.id == album.id }
                val assetSong = assetAlbum?.songs?.find { it.mml == song.mml }
                song.primaryUsage = if (null != assetSong) {
                    if (assetSong.ver < song.ver) {
                        Song.PrimaryUsage.Files
                    } else {
                        Song.PrimaryUsage.Assets
                    }
                } else {
                    Song.PrimaryUsage.Files
                }
            }
        }
        songList = when {
            null == downloadSongList -> {
                Logger.d("use preset songlist.json ${assetSongList!!.version} (not downloaded)")
                assetSongList
            }
            null != assetSongList && assetSongList.version < downloadSongList.version -> {
                Logger.d("use downloaded songlist.json ${downloadSongList.version}")
                downloadSongList
            }
            else -> {
                Logger.d("use preset songlist.json ${assetSongList!!.version} (newer than downloaded)")
                assetSongList
            }
        }
        songList.albums.forEach { album ->
            album.songs.forEach { song ->
                song.parentAlbum = album
                Logger.d("PrimaryUsage: ${song.mml} = ${song.primaryUsage}")
            }
        }
        return this
    }

    fun searchSongOfMML(mml: String): Song? {
        songList.albums.forEach { album ->
            album.songs.forEach { song ->
                if (song.mml == mml) {
                    return song
                }
            }
        }
        return null
    }

    fun changeMasterVolume(masterVolume: Int) {
        this.masterVolume = masterVolume
        //audioTrack?.setVolume(masterVolume / 100.0f)
    }

    fun initialize() {
        terminate()
        synchronized(locker) { vgsContext = JNI.createDecoder() }
    }

    fun terminate() {
        releasePlayMonitor()
        if (0L != vgsContext) {
            synchronized(locker) {
                JNI.endPlay()
                JNI.releaseDecoder(vgsContext)
            }
            vgsContext = 0L
        }
    }

    private fun find(album: Album?, song: Song?): Song? {
        album ?: return null
        song ?: return null
        songList.albums.find { it.id == album.id } ?: return null
        return album.songs.find { it.mml == song.mml }
    }

    fun stop() {
        if (isBackground) {
            stopJob(startedContext)
        }
        releasePlayMonitor()
        JNI.endPlay()
        playingSong?.needReload = true
        find(playingAlbum, playingSong)?.status = Song.Status.Stop
        playingAlbum = null
        playingSong = null
    }

    fun pause(
        progress: Int,
        onSeek: ((length: Int, time: Int) -> Unit)?,
        onPlayEnded: (() -> Unit)?
    ) {
        val song = playingSong ?: return
        val album = playingAlbum ?: return
        if (song.status == Song.Status.Play) {
            song.status = Song.Status.Pause
            song.needReload = true
            releasePlayMonitor()
            JNI.endPlay()
        } else {
            play(startedContext, album, song, onSeek, onPlayEnded, progress)
        }
    }

    fun isPlaying(album: Album?, song: Song?): Boolean {
        album ?: return false
        song ?: return false
        return playingAlbum == album && playingSong == song
    }

    fun play(context: Context?, album: Album?, song: Song?) =
        play(context, album, song, null, null, 0)

    fun play(
        context: Context?,
        album: Album?,
        song: Song?,
        onSeek: ((length: Int, time: Int) -> Unit)?,
        onPlayEnded: (() -> Unit)?,
        seek: Int
    ) {
        album ?: return
        song ?: return
        stop()
        find(album, song)?.status = Song.Status.Play
        playingAlbum = album
        playingSong = song
        playingSong?.needReload = true
        fadeoutExecuted = false
        song.readMML(mainActivity) {
            synchronized(locker) {
                kobushi = mainActivity.settings?.compatKobushi ?: 0
                JNI.load(vgsContext, it)
                createAudioTrack(seek, onSeek, onPlayEnded)
                if (isBackground) startJob(context)
                startedContext = context
            }
        }
    }

    fun seek(progress: Int?) {
        progress ?: return
        synchronized(locker) {
            JNI.seek(vgsContext, progress * 22050)
            JNI.kobushi(vgsContext, kobushi)
        }
    }

    private fun createAudioTrack(
        seek: Int,
        onSeek: ((length: Int, time: Int) -> Unit)?,
        onPlayEnded: (() -> Unit)?
    ) {
        releasePlayMonitor()
        JNI.seek(vgsContext, seek * 22050)
        JNI.kobushi(vgsContext, kobushi)
        JNI.startPlay(vgsContext)
        Timer().schedule(object : TimerTask() {
            override fun run() {
                val timeLength: Int
                val time: Int
                synchronized(locker) {
                    timeLength = JNI.getTimeLength(vgsContext) / 22050
                    time = JNI.getTime(vgsContext) / 22050
                }
                onSeek?.invoke(timeLength, time)
                val loop = playingSong?.loop ?: 0
                if (!infinity && !fadeoutExecuted && 0 < loop) {
                    val loopCount: Int
                    synchronized(locker) { loopCount = JNI.loopCount(vgsContext) }
                    if (loop <= loopCount) {
                        synchronized(locker) { JNI.fadeout(vgsContext) }
                        fadeoutExecuted = true
                    }
                }
                val isPlaying: Boolean
                synchronized(locker) {
                    isPlaying = JNI.isPlaying(vgsContext)
                }
                if (!isPlaying) {
                    stop()
                    mainActivity.runOnUiThread { onPlayEnded?.invoke() }
                }
            }
        }, 100L, 100L)
    }

    private fun releasePlayMonitor() {
        playMonitor?.cancel()
        playMonitor?.purge()
        playMonitor = null
    }

    private fun jobIdOfSong(song: Song?): Int {
        val albumIndex = albums.indexOf(song?.parentAlbum)
        val songIndex = song?.parentAlbum?.songs?.indexOf(song) ?: 0
        return (albumIndex + 1) * 1000 + songIndex + 1
    }

    fun startJob(context: Context?) {
        if (playingSong == null) {
            Logger.d("Job not scheduled, because any music playing")
            return
        }
        val scheduler = context?.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        val componentName = ComponentName(context, MusicJobService::class.java)
        val jobInfo = JobInfo.Builder(jobIdOfSong(playingSong), componentName)
            .apply {
                setBackoffCriteria(10000, JobInfo.BACKOFF_POLICY_LINEAR)
                setPersisted(false)
                setPeriodic(1000 * 60 * 15)
                setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                setRequiresCharging(false)
            }.build()
        scheduler.schedule(jobInfo)
        Logger.d("Job Scheduled: ${playingSong?.name} (id: ${jobIdOfSong(playingSong)})")
    }

    fun stopJob(context: Context?) {
        val playingSong = this.playingSong ?: return
        val scheduler = context?.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        scheduler.cancel(jobIdOfSong(playingSong))
        Logger.d("Job Canceled: ${playingSong.name} (id: ${jobIdOfSong(playingSong)})")
    }
}