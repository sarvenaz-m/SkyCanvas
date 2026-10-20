package dev.sarvenaz.skycanvas.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
 * and mountains redraw only when their scene or daylight palette changes.
 */
@Composable
fun WeatherScene(
    scene: Scene,
    day: Boolean,
    motion: Boolean,
    modifier: Modifier = Modifier,
    blendDaylight: Boolean = true,
) {
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
                "Illustrated ${scene.name.lowercase()} landscape, ${if(day) "day" else "night"}. Decorative scene."
        }
    ) {
        Canvas(Modifier.fillMaxSize()) { drawSceneSky(daylight.value, storm) }
        Canvas(Modifier.fillMaxSize()) { drawSceneCelestial(daylight.value, phase.value) }
        Canvas(Modifier.fillMaxSize()) { drawSceneLandscape(scene, daylight.value) }
        Canvas(Modifier.fillMaxSize()) { drawSceneAtmosphere(scene, daylight.value, phase.value) }
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

internal fun DrawScope.drawSceneLandscape(scene: Scene, daylight: Float) {
    val storm = scene == Scene.STORM
    val w = size.width
    val h = size.height
    val light = daylight
    val colors = scenePalette(light, storm)
    val mountain = colors.mountain
    val land = colors.land
    val stone = colors.stone
    val teal = colors.teal
    fun path(vararg points: Pair<Float, Float>, color: Color) {
        val p = Path()
        p.moveTo(points.first().first * w, points.first().second * h)
        for (v in points.drop(1)) p.lineTo(v.first * w, v.second * h)
        p.close()
        drawPath(p, color)
    }
    path(
        0f to .63f,
        .12f to .52f,
        .21f to .58f,
        .39f to .40f,
        .55f to .57f,
        .70f to .44f,
        .89f to .59f,
        1f to .50f,
        1f to 1f,
        0f to 1f,
        color = mountain,
    )
    path(
        0f to .74f,
        .19f to .65f,
        .35f to .73f,
        .53f to .61f,
        .78f to .71f,
        1f to .62f,
        1f to 1f,
        0f to 1f,
        color = land,
    )
    if (scene == Scene.SNOW)
        path(
            .28f to .51f,
            .39f to .40f,
            .49f to .52f,
            .40f to .49f,
            .36f to .50f,
            color = Color(0xFFEAF2F8),
        )
    // An imagined dome and bridge bring a sense of place without representing a map.
    drawRect(stone, Offset(w * .30f, h * .65f), Size(w * .16f, h * .15f))
    val dome =
        Path().apply {
            moveTo(w * .28f, h * .65f)
            cubicTo(w * .30f, h * .55f, w * .36f, h * .55f, w * .38f, h * .50f)
            cubicTo(w * .40f, h * .55f, w * .46f, h * .55f, w * .48f, h * .65f)
            close()
        }
    drawPath(dome, teal)
    drawLine(stone, Offset(w * .38f, h * .50f), Offset(w * .38f, h * .47f), w * .004f)
    for (x in listOf(.265f, .49f)) {
        drawRect(stone, Offset(w * x, h * .53f), Size(w * .018f, h * .27f))
        drawCircle(teal, w * .015f, Offset(w * (x + .009f), h * .535f))
    }
    val river =
        Path().apply {
            moveTo(0f, h * .89f)
            cubicTo(w * .35f, h * .78f, w * .64f, h * .86f, w, h * .80f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
    drawPath(river, colors.river)
    drawRect(stone, Offset(w * .06f, h * .77f), Size(w * .88f, h * .105f))
    drawRect(
        colors.bridgeCap,
        Offset(w * .04f, h * .755f),
        Size(w * .92f, h * .018f),
    )
    for (i in 0..12) {
        val x = w * (.08f + i * .065f)
        val width = w * .040f
        val top = h * .79f
        val arch =
            Path().apply {
                moveTo(x, h * .88f)
                lineTo(x, top + width / 2)
                quadraticTo(x + width / 2, top - width / 2, x + width, top + width / 2)
                lineTo(x + width, h * .88f)
                close()
            }
        drawPath(arch, lerp(Color(0xFF263B54), Color(0xFFD6E0D6), light))
        if (i % 3 == 0)
            drawLine(
                Color(0xFFF2C47A).copy(alpha = 1f - light),
                Offset(x + width / 2, top + width),
                Offset(x + width / 2, top + width * 1.6f),
                2f,
            )
    }
    for (x in listOf(.035f, .96f)) {
        val tree =
            Path().apply {
                moveTo(w * x, h * .58f)
                cubicTo(w * (x - .05f), h * .75f, w * (x - .04f), h * .86f, w * x, h * .87f)
                cubicTo(w * (x + .04f), h * .86f, w * (x + .04f), h * .72f, w * x, h * .58f)
                close()
            }
        drawPath(tree, colors.trees)
        drawLine(stone, Offset(w * x, h * .82f), Offset(w * x, h * .94f), w * .008f)
    }
}

internal fun DrawScope.drawSceneAtmosphere(scene: Scene, daylight: Float, phase: Float) {
    val storm = scene == Scene.STORM
    val w = size.width
    val h = size.height
    val t = phase
    val light = daylight
    val night = 1f - light
    val drift = sin(t * TAU)
    for (i in 0..7) {
        val x = w * (.06f + i * .12f + sin(t * TAU * 4 + i) * .018f)
        val y = h * (.91f + i % 3 * .026f)
        val opacity = .16f + .13f * (.5f + .5f * sin(t * TAU * 4 + i * .9f))
        drawLine(
            Color.White.copy(alpha = opacity),
            Offset(x, y),
            Offset(x + w * .09f, y),
            w * .0018f,
            StrokeCap.Round,
        )
    }
    for (i in 0..8) {
        val width = w * (.035f + i * .009f)
        val x = w * (.775f + sin(t * TAU * 3 + i * .8f) * .012f)
        val y = h * (.895f + i * .011f)
        val color = lerp(Color(0xFFF9E9C4), Color(0xFFFFE4AA), light)
        drawLine(
            color.copy(alpha = (.08f + night * .08f) * (1f - i * .07f)),
            Offset(x - width / 2, y),
            Offset(x + width / 2, y),
            h * .003f,
            StrokeCap.Round,
        )
    }
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
