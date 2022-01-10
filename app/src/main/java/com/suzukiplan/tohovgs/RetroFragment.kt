package com.suzukiplan.tohovgs

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import com.suzukiplan.tohovgs.api.JNI
import com.suzukiplan.tohovgs.api.Logger
import com.suzukiplan.tohovgs.api.MusicManager
import com.suzukiplan.tohovgs.api.Settings
import com.suzukiplan.tohovgs.model.Song
import java.nio.charset.Charset

class RetroFragment : Fragment(), SurfaceHolder.Callback {
    companion object {
        fun create(): RetroFragment = RetroFragment()
    }

    private lateinit var settings: Settings
    private lateinit var surfaceView: SurfaceView
    private lateinit var holder: SurfaceHolder
    private lateinit var vram: Bitmap
    private lateinit var gestureDetector: GestureDetector
    private val vramRect = Rect(0, 0, 240, 320)
    private val surfaceRect = Rect()
    private val paint = Paint()
    private var renderThread: Thread? = null
    private var alive = false
    private var previousX = 0
    private var previousY = 0

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        settings = Settings(context)
        vram = Bitmap.createBitmap(vramRect.width(), vramRect.height(), Bitmap.Config.RGB_565)
        val view = inflater.inflate(R.layout.fragment_retro, container, false)
        surfaceView = view.findViewById(R.id.surface_view)
        surfaceView.setZOrderOnTop(true)
        holder = surfaceView.holder
        holder.addCallback(this)
        surfaceView.isClickable = true
        surfaceView.isFocusable = false
        gestureDetector = GestureDetector(context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent?,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    val zx = vramRect.width().toFloat() / surfaceRect.width().toFloat()
                    val vx = (velocityX * zx * 0.25252).toInt()
                    val zy = vramRect.height().toFloat() / surfaceRect.height().toFloat()
                    val vy = (velocityY * zy * 0.25252).toInt()
                    JNI.compatOnFling(vx, vy)
                    return super.onFling(e1, e2, velocityX, velocityY)
                }
            })
        surfaceView.setOnTouchListener { _, motionEvent ->
            when (motionEvent.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN,
                MotionEvent.ACTION_MOVE -> {
                    val zx = vramRect.width().toFloat() / surfaceRect.width().toFloat()
                    val cx = (motionEvent.x * zx).toInt()
                    val zy = vramRect.height().toFloat() / surfaceRect.height().toFloat()
                    val cy = (motionEvent.y * zy).toInt()
                    if (motionEvent.actionMasked == MotionEvent.ACTION_MOVE) {
                        JNI.compatOnTouch(cx, cy, cx - previousX, cy - previousY)
                    } else {
                        JNI.compatOnTouch(cx, cy, 0, 0)
                    }
                    previousX = cx
                    previousY = cy
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_POINTER_UP,
                MotionEvent.ACTION_CANCEL -> JNI.compatOnReleaseTouch()
            }
            gestureDetector.onTouchEvent(motionEvent)
        }
        return view
    }

    override fun onDestroyView() {
        vram.recycle()
        super.onDestroyView()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Logger.d("surfaceCreated")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            holder.surface.setFrameRate(60.0f, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
        }
        stopRenderThread()
        startRenderThread()
    }

    override fun surfaceChanged(holder: SurfaceHolder, f: Int, w: Int, h: Int) {
        Logger.d("surfaceChanged: width=$w, height=$h, format=$f")
        surfaceRect.set(0, 0, w, h)
    }

    override fun surfaceDestroyed(p0: SurfaceHolder) {
        Logger.d("surfaceDestroyed")
        stopRenderThread()
    }

    private fun startRenderThread() {
        alive = true
        renderThread = Thread { renderThreadMain() }
        renderThread?.start()
    }

    private fun stopRenderThread() {
        alive = false
        renderThread?.join()
        renderThread = null
    }

    private fun renderThreadMain() {
        Logger.d("Start render thread")
        var start: Long
        var procTime: Long
        val intervals = Array(3) {
            when (it) {
                0, 1 -> 17L
                else -> 16L
            }
        }
        val baseTime = intervals.minOf { it }
        var currentInterval = 0
        paint.isAntiAlias = false

        JNI.compatCleanUp()
        val albums = MusicManager.getInstance(activity as MainActivity)?.albums ?: return
        val unlockedSongs = HashMap<String, List<Song>>(albums.size)
        var unlockedSongsCount = 0
        albums.forEach { album ->
            unlockedSongs[album.id] = album.songs.filter { song ->
                !settings.isLocked(song)
            }
            unlockedSongsCount += unlockedSongs[album.id]?.size ?: 0
        }
        val unlockedAlbums = albums.filter { true == unlockedSongs[it.id]?.isNotEmpty() }
        JNI.compatAllocate(unlockedAlbums.size, unlockedSongsCount, context?.assets)
        var titleIndex = 0
        var songIndex = 0
        var compatId = 0x0010
        val sjis = Charset.forName("SJIS")
        unlockedAlbums.forEach { album ->
            val songNum = unlockedSongs[album.id]?.size ?: 0
            JNI.compatAddTitle(
                titleIndex,
                compatId,
                songNum,
                album.formalName.toByteArray(sjis),
                album.copyright.toByteArray(sjis)
            )
            unlockedSongs[album.id]?.forEach { song ->
                val songNo = song.mml.substring(song.mml.indexOf('-') + 1).toInt()
                JNI.compatAddSong(
                    songIndex++,
                    compatId,
                    songNo,
                    song.loop,
                    album.compatColor,
                    "mml/${song.mml}.mml".toByteArray(Charsets.UTF_8),
                    song.name.toByteArray(sjis)
                )
            }
            titleIndex++
            compatId += 0x10
        }
        JNI.compatLoadKanji(compatAsset("DSLOT255.DAT"))
        JNI.compatLoadGraphic(0, compatAsset("GSLOT000.CHR"))
        JNI.compatLoadGraphic(1, compatAsset("GSLOT255.CHR"))
        while (alive) {
            start = System.currentTimeMillis()
            val canvas = holder.lockHardwareCanvas()
            if (null != canvas) {
                JNI.compatTick(vram)
                canvas.drawBitmap(vram, vramRect, surfaceRect, paint)
                holder.unlockCanvasAndPost(canvas)
            }
            procTime = System.currentTimeMillis() - start
            currentInterval++
            currentInterval %= intervals.size
            if (procTime < baseTime) {
                Thread.sleep(intervals[currentInterval] - procTime)
            }
        }
        Logger.d("End render thread")
        JNI.compatCleanUp()
    }

    private fun compatAsset(name: String) = context?.assets?.open("compat/$name")?.readBytes()
}