package dev.sarvenaz.skycanvas

import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.sarvenaz.skycanvas.ui.SkyCanvasApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val graph
        get() = (application as SkyApplication).graph

    private val model: SkyViewModel by viewModels { graph.factory }
    private val frames = WindowFrameSampler()
    private val export =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri
            ->
            if (uri != null) {
                val report = frames.export()
                lifecycleScope.launch {
                    val result =
                        withContext(Dispatchers.IO) {
                            runCatching {
                                contentResolver.openOutputStream(uri)?.use {
                                    it.write(report.toByteArray())
                                } ?: error("No destination")
                            }
                        }
                    model.message(
                        if (result.isSuccess) "Frame timing report saved."
                        else
                            "The frame timing report could not be saved. Please choose another destination."
                    )
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.rgb(246, 248, 251), Color.rgb(23, 49, 87)),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.rgb(23, 49, 87)),
        )
        setContent {
            val state = model.state.collectAsStateWithLifecycle().value
            val summary = frames.state.collectAsStateWithLifecycle().value
            val reduced =
                Settings.Global.getFloat(
                    contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f,
                ) == 0f
            SkyCanvasApp(
                state.copy(motion = state.motion && !reduced),
                model.actions.copy(
                    resetFrames = frames::reset,
                    exportFrames = { export.launch("SkyCanvas-frame-timings.json") },
                ),
                summary,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        model.resumed()
        @Suppress("DEPRECATION") val rate = windowManager.defaultDisplay.refreshRate
        frames.start(window, rate)
    }

    override fun onPause() {
        frames.stop()
        super.onPause()
    }
}
