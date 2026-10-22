package dev.sarvenaz.skycanvas.ui

import android.content.Context
import androidx.core.graphics.toColorInt
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.math.sin
import org.json.JSONObject

/** Parsed once, then drawn by Compose. The browser reads the same original vector geometry. */
internal data class CityArtwork(
    val name: String,
    val landmark: String,
    val description: String,
    val groups: List<CityArtGroup>,
    val waterMask: Path,
    val waterY: Float,
) {
    companion object {
        @Volatile private var catalog: Map<String, CityArtwork>? = null

        fun forPlace(context: Context, placeId: String): CityArtwork {
            val all = catalog ?: synchronized(this) {
                catalog ?: parse(context.assets.open("city-scenes.json").bufferedReader().use {
                    it.readText()
                }).also { catalog = it }
            }
            return all[placeId] ?: all.getValue("isfahan")
        }

        internal fun parse(source: String): Map<String, CityArtwork> {
            val root = JSONObject(source)
            require(root.getInt("schema") == 1) { "Unknown city artwork schema" }
            val defaults = root.getJSONObject("palette")
            val cities = root.getJSONObject("cities")
            return cities.keys().asSequence().associateWith { id ->
                val city = cities.getJSONObject(id)
                val overrides = city.getJSONObject("palette")
                val groups = city.getJSONArray("groups")
                CityArtwork(
                    city.getString("name"), city.getString("landmark"), city.getString("description"),
                    List(groups.length()) { i ->
                        val group = groups.getJSONObject(i)
                        val shapes = group.getJSONArray("shapes")
                        val pivot = group.optJSONArray("pivot")
                        CityArtGroup(
                            group.optString("motion", ""), group.optBoolean("nightOnly", false),
                            Offset(pivot?.optDouble(0, 500.0)?.toFloat() ?: 500f,
                                pivot?.optDouble(1, 500.0)?.toFloat() ?: 500f),
                            List(shapes.length()) { j ->
                                val shape = shapes.getJSONObject(j)
                                val tone = shape.getString("tone")
                                val colors = if (overrides.has(tone)) overrides.getJSONArray(tone)
                                    else defaults.getJSONArray(tone)
                                CityArtShape(
                                    PathParser().parsePathString(shape.getString("d")).toPath(),
                                    Color(colors.getString(0).toColorInt()),
                                    Color(colors.getString(1).toColorInt()),
                                    shape.optDouble("stroke", 0.0).toFloat(),
                                    shape.optDouble("opacity", 1.0).toFloat(),
                                )
                            },
                        )
                    },
                    PathParser().parsePathString(city.getString("waterMask")).toPath(),
                    city.getDouble("waterY").toFloat(),
                )
            }
        }
    }
}

internal data class CityArtGroup(
    val motion: String,
    val nightOnly: Boolean,
    val pivot: Offset,
    val shapes: List<CityArtShape>,
)
internal data class CityArtShape(
    val path: Path,
    val day: Color,
    val night: Color,
    val stroke: Float,
    val opacity: Float,
)

internal fun DrawScope.drawCityArtwork(
    artwork: CityArtwork,
    daylight: Float,
    phase: Float = 0f,
    moving: Boolean = false,
) {
    // Match the companion's xMidYMax slice instead of stretching landmarks on narrow cards.
    val scale = maxOf(size.width / 1000f, size.height / 650f)
    val left = (size.width - 1000f * scale) / 2f
    val top = size.height - 650f * scale
    withTransform({ translate(left, top); scale(scale, scale, Offset.Zero) }) {
        for (group in artwork.groups) {
            if ((group.motion.isNotEmpty()) != moving) continue
            var opacity = if (group.nightOnly) 1f - daylight else 1f
            val wave = sin(phase * 6.2831855f * when (group.motion) {
                "boat" -> 3; "sway", "petals" -> 2; "fountain" -> 6; else -> 3
            })
            if (group.motion == "glint") opacity *= .82f + wave * .17f
            if (group.motion == "petals") opacity *= .67f + wave * .17f
            if (group.motion == "fountain") opacity *= .70f + wave * .20f
            if (opacity <= 0f) continue
            withTransform({
                when (group.motion) {
                    "boat" -> { translate(0f, wave * 3f); rotate(wave * .7f, group.pivot) }
                    "sway" -> rotate(wave * .8f, group.pivot)
                    "petals" -> translate(wave * 12f, wave * 8f)
                    "fountain" -> translate(0f, wave * 2.5f)
                }
            }) {
                for (shape in group.shapes) drawPath(
                    shape.path, lerp(shape.night, shape.day, daylight),
                    alpha = opacity * shape.opacity,
                    style = if (shape.stroke > 0f) Stroke(shape.stroke, cap = StrokeCap.Round,
                        join = StrokeJoin.Round) else Fill,
                )
            }
        }
    }
}

/** Reflections stay on each city's water surface, clear of the coastal sand and garden paths. */
internal fun DrawScope.drawCityWater(artwork: CityArtwork, daylight: Float, phase: Float) {
    val scale = maxOf(size.width / 1000f, size.height / 650f)
    val left = (size.width - 1000f * scale) / 2f
    val top = size.height - 650f * scale
    val tau = phase * 6.2831855f
    withTransform({ translate(left, top); scale(scale, scale, Offset.Zero) }) {
        clipPath(artwork.waterMask) {
            for (i in 0..7) {
                val x = 60f + i * 120f + sin(tau * 4 + i) * 18f
                val y = artwork.waterY + i % 3 * 17f
                val opacity = .16f + .13f * (.5f + .5f * sin(tau * 4 + i * .9f))
                drawLine(Color.White.copy(alpha = opacity), Offset(x, y), Offset(x + 90f, y),
                    1.8f, StrokeCap.Round)
            }
            for (i in 0..8) {
                val width = 35f + i * 9f
                val x = 775f + sin(tau * 3 + i * .8f) * 12f
                val y = artwork.waterY - 10f + i * 7f
                val color = lerp(Color(0xFFF9E9C4), Color(0xFFFFE4AA), daylight)
                drawLine(color.copy(alpha = (.08f + (1f - daylight) * .08f) * (1f - i * .07f)),
                    Offset(x - width / 2, y), Offset(x + width / 2, y), 2f, StrokeCap.Round)
            }
        }
    }
}
