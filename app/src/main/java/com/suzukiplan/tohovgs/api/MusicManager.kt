/**
 * ©2022, SUZUKI PLAN
 * License: https://github.com/suzukiplan/tohovgs4-android/blob/master/LICENSE.txt
 */
package com.suzukiplan.tohovgs.api

import android.annotation.SuppressLint
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.suzukiplan.tohovgs.MainActivity
import com.suzukiplan.tohovgs.model.Album
import com.suzukiplan.tohovgs.model.Albums
import com.suzukiplan.tohovgs.model.Song

class MusicManager {
    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: MusicManager? = null

        fun getInstance(mainActivity: MainActivity): MusicManager? {
            if (null == instance) {
                instance = MusicManager()
                instance?.load(mainActivity)
            }
            return instance
        }
    }

    val albums: List<Album>? get() = albumsRawData?.albums
    private var locker = Object()
    private var albumsRawData: Albums? = null
    private var vgsContext = 0L
    private var playingAlbum: Album? = null
    private var playingSong: Song? = null
    private var audioTrack: AudioTrack? = null
    private val basicBufferSize = 4096
    private var fadeoutExecuted = false
    private var decodeSize = 0
    private var decodeAudioBuffers = arrayOf(
        ByteArray(basicBufferSize),
        ByteArray(basicBufferSize)
    )
    private var decodeAudioBufferLatch = 0
    private var decodeAudioBufferFirst = ByteArray(basicBufferSize * 2)
    private var decodeFirst = true
    var infinity = false
    var isBackground = false
    private var startedContext: Context? = null

    fun isExistLockedSong(settings: Settings): Boolean {
        return null != albums?.find { album ->
            null != album.songs.find { settings.isLocked(it) }
        }
    }

    private fun load(mainActivity: MainActivity) {
        val songListInput = mainActivity.assets.open("songlist.json")
        val songListJson = String(songListInput.readBytes(), Charsets.UTF_8)
        albumsRawData = mainActivity.gson.fromJson(songListJson, Albums::class.java)
        albumsRawData?.albums?.forEach { album ->
            album.songs.forEach { song ->
                song.parentAlbum = album
            }
        }
    }

    fun initialize() {
        terminate()
        synchronized(locker) { vgsContext = JNI.createDecoder() }
    }

    fun terminate() {
        audioTrack?.release()
        audioTrack = null
        if (0L != vgsContext) {
            synchronized(locker) { JNI.releaseDecoder(vgsContext) }
            vgsContext = 0L
        }
    }

    private fun find(album: Album?, song: Song?): Song? {
        album ?: return null
        song ?: return null
        albumsRawData?.albums?.find { it.id == album.id } ?: return null
        return album.songs.find { it.mml == song.mml }
    }

    fun stop() {
        if (isBackground) {
            stopJob(startedContext)
        }
        audioTrack?.release()
        playingSong?.needReload = true
        find(playingAlbum, playingSong)?.playing = false
        playingAlbum = null
        playingSong = null
    }

    fun isPlaying(album: Album?, song: Song?): Boolean {
        album ?: return false
        song ?: return false
        return playingAlbum == album && playingSong == song
    }

    fun play(
        context: Context?,
        album: Album?,
        song: Song?,
        onSeek: (length: Int, time: Int) -> Unit,
        onPlayEnded: () -> Unit
    ) {
        album ?: return
        song ?: return
        stop()
        find(album, song)?.playing = true
        playingAlbum = album
        playingSong = song
        playingSong?.needReload = true
        decodeFirst = true
        fadeoutExecuted = false
        decodeSize = 0
        synchronized(locker) { JNI.load(vgsContext, song.readMML(context)) }
        createAudioTrack(onSeek, onPlayEnded)
        if (isBackground) startJob(context)
        startedContext = context
    }

    fun seek(progress: Int?) {
        progress ?: return
        synchronized(locker) { JNI.seek(vgsContext, progress * 22050) }
    }

    private fun createAudioTrack(
        onSeek: (length: Int, time: Int) -> Unit,
        onPlayEnded: () -> Unit
    ) {
        audioTrack?.release()
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val format = AudioFormat.Builder()
            .setSampleRate(22050)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(basicBufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack?.positionNotificationPeriod = basicBufferSize / 2
        audioTrack?.setPlaybackPositionUpdateListener(object :
            AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(audioTrack: AudioTrack?) {
            }

            override fun onPeriodicNotification(p0: AudioTrack?) {
                audioTrack?.write(
                    decodeAudioBuffers[decodeAudioBufferLatch],
                    0,
                    decodeAudioBuffers[decodeAudioBufferLatch].size
                )
                val timeLength: Int
                val time: Int
                synchronized(locker) {
                    timeLength = JNI.getTimeLength(vgsContext) / 22050
                    time = JNI.getTime(vgsContext) / 22050
                }
                onSeek.invoke(timeLength, time)
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
                if (isPlaying) {
                    decodeAudioBufferLatch = 1 - decodeAudioBufferLatch
                    decode(decodeAudioBuffers[decodeAudioBufferLatch])
                } else {
                    stop()
                    onPlayEnded.invoke()
                }
            }
        })
        decode(decodeAudioBufferFirst)
        audioTrack?.write(decodeAudioBufferFirst, 0, decodeAudioBufferFirst.size)
        audioTrack?.play()
        decodeAudioBufferLatch = 0
        decode(decodeAudioBuffers[decodeAudioBufferLatch])
    }

    private fun decode(buffer: ByteArray) {
        synchronized(locker) { JNI.decode(vgsContext, buffer) }
        decodeSize += buffer.size
    }

    private fun jobIdOfSong(song: Song?): Int {
        song ?: return 1
        song.parentAlbum ?: return 1
        val albumIndex = albums?.indexOf(song.parentAlbum) ?: return 1
        val songIndex = song.parentAlbum?.songs?.indexOf(song) ?: return 1
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