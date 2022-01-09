package com.suzukiplan.tohovgs

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import com.suzukiplan.tohovgs.api.Logger
import kotlin.random.Random

class RetroFragment : Fragment(), SurfaceHolder.Callback {
    companion object {
        fun create(): RetroFragment = RetroFragment()
    }

    private lateinit var surfaceView: SurfaceView
    private lateinit var holder: SurfaceHolder
    private lateinit var vram: Bitmap
    private lateinit var random: Random
    private val vramRect = Rect(0, 0, 240, 320)
    private val surfaceRect = Rect()
    private val paint = Paint()
    private var renderThread: Thread? = null
    private var alive = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        vram = Bitmap.createBitmap(vramRect.width(), vramRect.height(), Bitmap.Config.RGB_565)
        random = Random(System.currentTimeMillis())
        val view = inflater.inflate(R.layout.fragment_retro, container, false)
        surfaceView = view.findViewById(R.id.surface_view)
        surfaceView.setZOrderOnTop(true)
        holder = surfaceView.holder
        holder.addCallback(this)
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
        while (alive) {
            start = System.currentTimeMillis()
            tick()
            procTime = System.currentTimeMillis() - start
            currentInterval++
            currentInterval %= intervals.size
            if (procTime < baseTime) {
                Thread.sleep(intervals[currentInterval] - procTime)
            }
        }
        Logger.d("End render thread")
    }

    private fun tick() {
        vram.setPixel(
            random.nextInt(vramRect.width()),
            random.nextInt(vramRect.height()),
            random.nextInt(65536)
        )
        val canvas = holder.lockHardwareCanvas() ?: return
        canvas.drawBitmap(vram, vramRect, surfaceRect, paint)
        holder.unlockCanvasAndPost(canvas)
    }
}