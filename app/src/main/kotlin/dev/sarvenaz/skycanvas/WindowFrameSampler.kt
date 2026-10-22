package dev.sarvenaz.skycanvas

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import dev.sarvenaz.skycanvas.weather.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** Window timings are kept in a bounded sample window, never uploaded. */
class WindowFrameSampler {
    private val samples = FrameWindow(600)
    private val mutable = MutableStateFlow(FrameSummary())
    val state = mutable.asStateFlow()
    private var thread: HandlerThread? = null
    private var target: Window? = null
    private var callback: Window.OnFrameMetricsAvailableListener? = null

    fun start(window: Window, refreshHz: Float) {
        stop()
        val worker = HandlerThread("SkyCanvas frames").also { it.start() }
        val fallback = 1000.0 / refreshHz.coerceAtLeast(30f)
        var received = 0
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
            if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L)
                return@OnFrameMetricsAvailableListener
            val duration = metrics.getMetric(FrameMetrics.TOTAL_DURATION) / 1e6
            val deadline =
                if (Build.VERSION.SDK_INT >= 31) metrics.getMetric(FrameMetrics.DEADLINE) / 1e6
                else 0.0
            if (duration > 0) {
                samples.add(
                    duration,
                    if (deadline > 0) deadline else fallback,
                    dropped.coerceAtLeast(0),
                )
                if (++received % 30 == 0) mutable.value = samples.summary()
            }
        }
        target = window
        thread = worker
        callback = listener
        window.addOnFrameMetricsAvailableListener(listener, Handler(worker.looper))
    }

    fun stop() {
        callback?.let { target?.removeOnFrameMetricsAvailableListener(it) }
        thread?.quitSafely()
        thread = null
        target = null
        callback = null
        mutable.value = samples.summary()
    }

    fun reset() {
        samples.reset()
        mutable.value = FrameSummary()
    }

    fun export(): String {
        val summary = samples.summary()
        return JSONObject()
            .apply {
                put("schema", "skycanvas.window-frames/v1")
                put("version", "2.0.0")
                put("source", "Android Window.FrameMetrics")
                put("capturedAtEpochMs", System.currentTimeMillis())
                put("deviceModel", Build.MODEL)
                put("androidApi", Build.VERSION.SDK_INT)
                put("windowCapacity", 600)
                put("sampleCount", summary.count)
                put("p50Ms", summary.p50Ms ?: JSONObject.NULL)
                put("p95Ms", summary.p95Ms ?: JSONObject.NULL)
                put("framesOverReportedBudget", summary.overBudget)
                put("metricsCallbacksMissed", summary.callbacksMissed)
                put(
                    "notes",
                    "Nearest-rank percentiles of the latest reported frames. First-draw frames excluded. Callback drops are not dropped display frames.",
                )
            }
            .toString(2)
    }
}
