package dev.sarvenaz.skycanvas

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import dev.sarvenaz.skycanvas.ui.*
import dev.sarvenaz.skycanvas.weather.Scene
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Render the production drawing layers directly, independently of Android's display capture. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TestSkyApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SceneMotionTest {
    @Test
    fun clearDayHasVisibleMotion() {
        assertFalse(frame(1f, 0f).contentEquals(frame(1f, .125f)))
    }

    @Test
    fun clearNightHasVisibleMotion() {
        assertFalse(frame(0f, 0f).contentEquals(frame(0f, .125f)))
    }

    @Test
    fun aFixedSceneFrameIsDeterministic() {
        for (light in listOf(0f, 1f)) assertArrayEquals(frame(light, 0f), frame(light, 0f))
        assertFalse(frame(1f, 0f).contentEquals(frame(0f, 0f)))
    }

    @Test
    fun daylightPaletteHasAnIntermediateFrame() {
        val day = frame(1f, 0f)
        val dusk = frame(.5f, 0f)
        val night = frame(0f, 0f)
        val sky = 5 * WIDTH + WIDTH / 2
        assertNotEquals(day[sky], dusk[sky])
        assertNotEquals(night[sky], dusk[sky])
        assertNotEquals(day[sky], night[sky])
    }

    private fun frame(daylight: Float, phase: Float): IntArray {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val scope = CanvasDrawScope()
        scope.draw(
            Density(1f),
            LayoutDirection.Ltr,
            Canvas(bitmap.asImageBitmap()),
            Size(WIDTH.toFloat(), HEIGHT.toFloat()),
        ) {
            drawSceneSky(daylight, false)
            drawSceneCelestial(daylight, phase)
            drawSceneLandscape(Scene.CLEAR, daylight)
            drawSceneAtmosphere(Scene.CLEAR, daylight, phase)
        }
        return IntArray(WIDTH * HEIGHT).also {
            bitmap.getPixels(it, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
            bitmap.recycle()
        }
    }

    companion object {
        private const val WIDTH = 360
        private const val HEIGHT = 240
    }
}
