package dev.sarvenaz.skycanvas.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.semantics.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import dev.sarvenaz.skycanvas.weather.Scene
import kotlin.math.sin

/**
 * A small illustrated landscape. Continuous motion reads state in the drawing layers; the bridge
 * and landmarks redraw only when their city or daylight palette changes.
 */
@Composable
fun WeatherScene(
    scene: Scene,
    day: Boolean,
    motion: Boolean,
    modifier: Modifier = Modifier,
    blendDaylight: Boolean = true,
    placeId: String = "isfahan",
) {
    val context = LocalContext.current
    val artwork = remember(placeId, context) { CityArtwork.forPlace(context, placeId) }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val animate = motion && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val phase: State<Float> =
        if (animate)
            rememberInfiniteTransition(label = "Sky drift")
                .animateFloat(
                    0f,
                    1f,
                    infiniteRepeatable(tween(24_000, easing = LinearEasing)),
                    label = "Cloud drift",
                )
        else remember { mutableFloatStateOf(0f) }
    val daylight =
        animateFloatAsState(
            targetValue = if (day) 1f else 0f,
            animationSpec =
                tween(if (animate && blendDaylight) 1_600 else 0, easing = FastOutSlowInEasing),
            label = "Daylight",
        )
    val storm = scene == Scene.STORM
    Box(
        modifier.semantics {
            contentDescription =
                "Illustrated ${artwork.name} ${scene.name.lowercase()} landscape, ${if(day) "day" else "night"}. Decorative scene."
        }
    ) {
        Canvas(Modifier.fillMaxSize()) { drawSceneSky(daylight.value, storm) }
        Canvas(Modifier.fillMaxSize()) { drawSceneCelestial(daylight.value, phase.value) }
        Canvas(Modifier.fillMaxSize()) { drawCityArtwork(artwork, daylight.value) }
        Canvas(Modifier.fillMaxSize()) {
            drawCityArtwork(artwork, daylight.value, phase.value, moving = true)
            drawCityWater(artwork, daylight.value, phase.value)
            drawSceneAtmosphere(scene, daylight.value, phase.value)
        }
    }
}

internal fun DrawScope.drawSceneSky(daylight: Float, storm: Boolean) {
    val colors = scenePalette(daylight, storm)
    drawRect(Brush.verticalGradient(listOf(colors.skyTop, colors.skyBottom)))
}

internal fun DrawScope.drawSceneCelestial(daylight: Float, phase: Float) {
    val w = size.width
    val h = size.height
    val light = daylight
    val t = phase
    val pulse = .5f + .5f * sin(t * TAU * 3)
    val sun = Offset(w * .77f, h * (.25f + sin(t * TAU) * .004f))
    drawCircle(
        Color(0xFFFFE3A3).copy(alpha = (.22f + pulse * .12f) * light),
        w * (.125f + pulse * .009f),
        sun,
    )
    drawCircle(Color(0xFFFFDB8E).copy(alpha = light), w * .075f, sun)
    val night = 1f - light
    for (i in 0..30) {
        val x = ((i * 47 % 97) + 1) / 100f * w
        val y = ((i * 29 % 33) + 3) / 100f * h
        val glow = .40f + .30f * (.5f + .5f * sin(t * TAU * (2 + i % 3) + i * 1.7f))
        drawCircle(
            Color.White.copy(alpha = glow * night),
            if (i % 4 == 0) w * .0023f else w * .0012f,
            Offset(x, y),
        )
    }
    val moonY = h * (.25f + sin(t * TAU + .7f) * .004f)
    val moon = Path().apply { addOval(Rect(Offset(w * .78f, moonY), w * .052f)) }
    val cutout = Path().apply { addOval(Rect(Offset(w * .805f, moonY - h * .022f), w * .049f)) }
    drawPath(
        Path.combine(PathOperation.Difference, moon, cutout),
        Color(0xFFF9E9C4).copy(alpha = night),
    )
}

internal fun DrawScope.drawSceneAtmosphere(scene: Scene, daylight: Float, phase: Float) {
    val storm = scene == Scene.STORM
    val w = size.width
    val h = size.height
    val t = phase
    val light = daylight
    val night = 1f - light
    val drift = sin(t * TAU)
    fun cloud(x: Float, y: Float, scale: Float, alpha: Float) {
        val c =
            if (storm) Color(0xFF3D526C).copy(alpha = alpha) else Color.White.copy(alpha = alpha)
        drawRoundRect(
            c,
            Offset(x - w * .07f * scale, y),
            Size(w * .19f * scale, h * .055f * scale),
            CornerRadius(h * .03f),
        )
        drawCircle(c, h * .045f * scale, Offset(x, y))
        drawCircle(c, h * .035f * scale, Offset(x + w * .06f * scale, y + h * .003f))
    }
    val cloudAlpha = if (scene == Scene.CLEAR) .32f else .72f
    cloud(w * (.22f + drift * .035f), h * .30f, 1f, cloudAlpha * (1f - night * .6f))
    cloud(
        w * (.69f - sin(t * TAU * 2 + .4f) * .04f),
        h * .43f,
        .7f,
        cloudAlpha * .8f * (1f - night * .6f),
    )
    if (scene == Scene.CLOUD || storm)
        cloud(w * (.66f + drift * .03f), h * .16f, 1.2f, .8f - night * .5f)
    if (scene == Scene.RAIN || storm)
        for (i in 0..25) {
            val x = w * ((i * 17 % 97) / 100f)
            val y = h * (.28f + ((i * .13f + t * 3.6f) % 0.6f))
            drawLine(
                lerp(Color(0xFFB9D7ED), Color(0xFF557F9C), light).copy(alpha = .52f),
                Offset(x, y),
                Offset(x - w * .01f, y + h * .04f),
                1.8f,
            )
        }
    if (scene == Scene.SNOW)
        for (i in 0..30) {
            val x = w * ((i * 23 % 97) / 100f)
            val y = h * (.1f + ((i * .12f + t * 2.4f) % 0.8f))
            drawCircle(
                Color.White.copy(alpha = .85f),
                if (i % 2 == 0) 3f else 2f,
                Offset(x, y),
            )
        }
    if (scene == Scene.FOG)
        for (i in 0..4) drawRoundRect(
            Color.White.copy(alpha = .25f),
            Offset(w * (i % 2 * .15f + sin(t * TAU + i) * .025f), h * (.46f + i * .075f)),
            Size(w * .85f, h * .017f),
            CornerRadius(12f),
        )
    if (storm)
        drawPath(
            Path().apply {
                moveTo(w * .60f, h * .28f)
                lineTo(w * .56f, h * .40f)
                lineTo(w * .61f, h * .39f)
                lineTo(w * .58f, h * .52f)
                lineTo(w * .68f, h * .35f)
                lineTo(w * .62f, h * .36f)
                close()
            },
            Color(0xFFFFDE95),
        )
}

private const val TAU = 6.2831855f

private data class ScenePalette(
    val skyTop: Color,
    val skyBottom: Color,
    val mountain: Color,
    val land: Color,
    val stone: Color,
    val teal: Color,
    val river: Color,
    val bridgeCap: Color,
    val trees: Color,
)

private fun scenePalette(daylight: Float, storm: Boolean): ScenePalette {
    fun blend(night: Long, day: Long) = lerp(Color(night), Color(day), daylight)
    return ScenePalette(
        blend(0xFF182E51, if (storm) 0xFF7892AB else 0xFFD8E9F8),
        blend(0xFF6A6C93, 0xFFF4DDAD),
        blend(0xFF465276, 0xFFB5B8CC),
        blend(0xFF293F5A, 0xFFD6B690),
        blend(0xFF596274, 0xFFBA8F70),
        blend(0xFF467482, 0xFF669B9E),
        blend(0xFF244C68, 0xFFA3CDCE),
        blend(0xFF6A6E80, 0xFFE8C9A1),
        blend(0xFF193A4B, 0xFF4B7270),
    )
}
