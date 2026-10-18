package com.afu.ledger.perf

import android.util.Log
import android.view.Choreographer
import com.afu.ledger.BuildConfig

/**
 * 仅 debug 构建启用的帧耗时统计。
 *
 * 为什么自己量：HyperOS 上 `dumpsys gfxinfo` 恒为 0 帧、`SurfaceFlinger --latency` 除首行外全 0，
 * 系统不提供帧数据，只能由应用侧用 [Choreographer] 记录帧间隔。
 *
 * 只统计「主线程完成一帧回调」的间隔，用于判断掉帧比例，不作为 GPU 耗时依据。
 */
object FrameStats {

    private const val TAG = "AfuFrameStats"
    private const val JANK_MS = 32L
    private const val MAX_SAMPLES = 4096

    private val intervals = ArrayList<Long>(MAX_SAMPLES)
    private var lastFrameNs = 0L
    private var running = false

    private val callback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (lastFrameNs != 0L) {
                val deltaMs = (frameTimeNanos - lastFrameNs) / 1_000_000
                if (intervals.size < MAX_SAMPLES) intervals.add(deltaMs)
            }
            lastFrameNs = frameTimeNanos
            if (running) Choreographer.getInstance().postFrameCallback(this)
        }
    }

    /** 开始一段统计（会清空之前的样本）。 */
    fun start() {
        if (!BuildConfig.DEBUG || running) return
        intervals.clear()
        lastFrameNs = 0L
        running = true
        Choreographer.getInstance().postFrameCallback(callback)
    }

    /** 结束统计，返回并打印摘要。 */
    fun stop(): String {
        if (!running) return summarize()
        running = false
        Choreographer.getInstance().removeFrameCallback(callback)
        val summary = summarize()
        Log.d(TAG, summary)
        return summary
    }

    fun summarize(): String {
        if (intervals.isEmpty()) return "无帧数据"
        val sorted = intervals.sorted()
        val avg = intervals.average()
        val p90 = sorted[((sorted.size - 1) * 0.9).toInt()]
        val jank = intervals.count { it > JANK_MS }
        val jankPercent = jank * 100.0 / intervals.size
        return "帧数=${intervals.size} 均值=%.1fms p90=${p90}ms >${JANK_MS}ms=${jank}(%.1f%%)"
            .format(avg, jankPercent)
    }
}
