package dev.sarvenaz.skycanvas.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import dev.sarvenaz.skycanvas.weather.*
import java.util.Locale
import kotlin.math.*

@Composable
fun SkyCanvasApp(state: SkyState, actions: SkyActions, frames: FrameSummary = FrameSummary()) =
    SkyTheme {
        Scaffold(
            containerColor = Paper,
            topBar = {
                Column(Modifier.background(Paper).statusBarsPadding()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(color = Navy, shape = RoundedCornerShape(12.dp)) {
                            Text(
                                "SC",
                                Modifier.padding(11.dp),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Column(Modifier.weight(1f).padding(start = 11.dp)) {
                            Text("SkyCanvas", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "A LITTLE CLOSER TO THE SKY",
                                fontSize = 9.sp,
                                letterSpacing = 1.5.sp,
                                color = Muted,
                            )
                        }
                        TextButton(onClick = actions.refresh, enabled = !state.refreshing) {
                            Text(if (state.refreshing) "Updating…" else "Refresh")
                        }
                    }
                }
            },
            bottomBar = {
                NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                    for (tab in SkyTab.entries) NavigationBarItem(
                        modifier = Modifier.testTag("tab-${tab.name}"),
                        selected = state.tab == tab,
                        onClick = { actions.tab(tab) },
                        icon = { NavGlyph(tab) },
                        label = { Text(tab.label) },
                        colors =
                            NavigationBarItemDefaults.colors(
                                indicatorColor = Color(0xFFE3EEF6),
                                selectedIconColor = Navy,
                                selectedTextColor = Navy,
                                unselectedIconColor = Muted,
                                unselectedTextColor = Muted,
                            ),
                    )
                }
            },
        ) { padding ->
            val modifier = Modifier.padding(padding).fillMaxSize()
            when (state.tab) {
                SkyTab.TODAY -> Today(state, actions, modifier)
                SkyTab.HOURS -> Hours(state, actions, modifier)
                SkyTab.PLACES -> PlaceList(state, actions, modifier)
                SkyTab.STUDIO -> Studio(state, actions, frames, modifier)
            }
        }
    }

@Composable
private fun NavGlyph(tab: SkyTab) {
    Canvas(Modifier.size(22.dp)) {
        val c = Navy
        val s = size.width
        val stroke = Stroke(1.7.dp.toPx())
        when (tab) {
            SkyTab.TODAY -> {
                drawCircle(c, s * .25f, Offset(s * .5f, s * .5f), style = stroke)
                for (i in 0..7) {
                    val a = i * PI / 4
                    drawLine(
                        c,
                        Offset(
                            (.5 + cos(a) * .37).toFloat() * s,
                            (.5 + sin(a) * .37).toFloat() * s,
                        ),
                        Offset(
                            (.5 + cos(a) * .48).toFloat() * s,
                            (.5 + sin(a) * .48).toFloat() * s,
                        ),
                        1.7.dp.toPx(),
                    )
                }
            }
            SkyTab.HOURS -> {
                for (i in 0..2) drawLine(
                    c,
                    Offset(s * .15f, s * (.25f + i * .25f)),
                    Offset(s * .85f, s * (.25f + i * .25f)),
                    1.7.dp.toPx(),
                )
            }
            SkyTab.PLACES -> {
                drawCircle(c, s * .32f, Offset(s * .5f, s * .4f), style = stroke)
                drawCircle(c, s * .09f, Offset(s * .5f, s * .4f), style = stroke)
                drawLine(c, Offset(s * .25f, s * .62f), Offset(s * .5f, s * .95f), 1.7.dp.toPx())
                drawLine(c, Offset(s * .75f, s * .62f), Offset(s * .5f, s * .95f), 1.7.dp.toPx())
            }
            SkyTab.STUDIO -> {
                drawRoundRect(
                    c,
                    Offset(s * .1f, s * .1f),
                    Size(s * .8f, s * .8f),
                    CornerRadius(4f),
                    style = stroke,
                )
                drawCircle(c, s * .12f, Offset(s * .65f, s * .35f))
                drawLine(c, Offset(s * .14f, s * .77f), Offset(s * .4f, s * .5f), 1.7.dp.toPx())
                drawLine(c, Offset(s * .4f, s * .5f), Offset(s * .8f, s * .85f), 1.7.dp.toPx())
            }
        }
    }
}

@Composable
private fun Eyebrow(text: String) {
    Text(
        text.uppercase(Locale.ROOT),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.6.sp,
        color = Blue,
    )
}

@Composable
private fun Heading(title: String, detail: String? = null) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (detail != null)
            Text(
                detail,
                color = Muted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 5.dp),
            )
    }
}

@Composable
private fun Notice(text: String) {
    Surface(color = Color(0xFFE8EFF6), shape = RoundedCornerShape(14.dp)) {
        Text(
            text,
            Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = Navy,
        )
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Border),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), content = content)
    }
}

@Composable
private fun Today(state: SkyState, actions: SkyActions, modifier: Modifier) {
    val record = state.record
    val f = record?.forecast
    LazyColumn(
        modifier.testTag("workspace"),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("Your sky, thoughtfully drawn")
                    Text(
                        "A view of your day.",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        if (state.loading && f == null)
            item {
                Card {
                    Text("Finding your forecast…")
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp))
                }
            }
        if (f != null) {
            item { Hero(state, actions) }
            item { SourceLine(record, state.now) }
            if (state.message.isNotBlank()) item { Notice(state.message) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Metric(
                        "Feels like",
                        temperature(f.current.feelsLike, state.fahrenheit),
                        Modifier.weight(1f),
                    )
                    Metric("Wind", decimal(f.current.wind), Modifier.weight(1f), "km/h")
                    Metric(
                        "Humidity",
                        f.current.humidity?.roundToInt()?.let { "$it%" } ?: "—",
                        Modifier.weight(1f),
                    )
                }
            }
            item { ChartPanel(state, actions) }
            item { Heading("A week in view", "Daily ranges, with the details kept close.") }
            items(f.days, key = { it.time }) { day ->
                DailyRow(day, state) {
                    actions.day(day.time)
                    actions.tab(SkyTab.HOURS)
                }
            }
            val day = f.dayFor(f.current.time)
            if (day != null)
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Metric(
                            "Sunrise",
                            day.sunrise?.let { localTime(it, state.place.zone) } ?: "—",
                            Modifier.weight(1f),
                        )
                        Metric(
                            "Sunset",
                            day.sunset?.let { localTime(it, state.place.zone) } ?: "—",
                            Modifier.weight(1f),
                        )
                    }
                }
            item { Attribution() }
        } else if (!state.loading)
            item {
                Notice(
                    state.message.ifBlank { "The forecast is unavailable. Refresh to try again." }
                )
            }
    }
}

@Composable
private fun Hero(state: SkyState, actions: SkyActions) {
    val f = state.record!!.forecast
    val c = f.current
    val night = c.isDay == false
    val ink = if (night) Color.White else Navy
    Surface(shape = RoundedCornerShape(28.dp)) {
        Box(Modifier.fillMaxWidth().height(330.dp)) {
            WeatherScene(
                condition(c.code).scene,
                c.isDay != false,
                state.motion,
                Modifier.fillMaxSize(),
                blendDaylight = false,
            )
            Column(Modifier.padding(22.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            state.place.name,
                            style = MaterialTheme.typography.headlineLarge,
                            color = ink,
                        )
                        Text(
                            "IRAN · ${localTime(c.time,state.place.zone,"EEE, d MMM")}",
                            color = ink,
                            fontSize = 11.sp,
                            letterSpacing = .7.sp,
                        )
                    }
                    Surface(
                        color = if (night) Color(0xFF344D6F) else Color.White.copy(alpha = .83f),
                        shape = RoundedCornerShape(30.dp),
                    ) {
                        TextButton(onClick = { actions.tab(SkyTab.PLACES) }) {
                            Text("Places", color = ink)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 14.dp)) {
                    Text(
                        temperature(c.temperature, state.fahrenheit),
                        fontSize = 68.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-3).sp,
                        color = ink,
                        lineHeight = 74.sp,
                    )
                    Text(
                        if (state.fahrenheit) "F" else "C",
                        Modifier.padding(top = 15.dp, start = 4.dp),
                        color = ink,
                        fontSize = 17.sp,
                    )
                }
                Text(
                    condition(c.code).label,
                    color = ink,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "Model conditions · ${localTime(c.time,state.place.zone)}",
                    color = ink,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SourceLine(record: WeatherRecord, now: Long) {
    val recorded = record.origin == Origin.RECORDED
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = if (recorded) Color(0xFFF4E8D5) else Color(0xFFE2EDF5),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                if (recorded) "RECORDED FORECAST"
                else if (record.stale(now)) "SAVED · NEEDS REFRESH" else "SAVED FORECAST",
                Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (recorded) Color(0xFF79501E) else Navy,
            )
        }
        Text(
            "Received ${localTime(record.fetchedAt,record.forecast.place.zone,"d MMM, HH:mm")}",
            Modifier.weight(1f).padding(start = 9.dp),
            fontSize = 11.sp,
            color = Muted,
        )
    }
}

@Composable
private fun Metric(title: String, value: String, modifier: Modifier = Modifier, unit: String = "") {
    Surface(
        modifier,
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Border),
    ) {
        Column(Modifier.padding(13.dp)) {
            Text(title, color = Muted, fontSize = 11.sp)
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (unit.isNotBlank()) Text(unit, color = Muted, fontSize = 10.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Days(state: SkyState, actions: SkyActions) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        FilterChip(
            selected = state.selectedDay == null,
            onClick = { actions.day(null) },
            label = { Text("Next 24 h") },
        )
        for (day in state.record?.forecast?.days.orEmpty()) FilterChip(
            selected = state.selectedDay == day.time,
            onClick = { actions.day(day.time) },
            label = { Text(localTime(day.time, state.place.zone, "EEE d")) },
        )
    }
}

@Composable
private fun ChartPanel(state: SkyState, actions: SkyActions) {
    Card {
        Heading("Follow the hours")
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 10.dp),
        ) {
            FilterChip(
                state.chart == ChartMode.TEMPERATURE,
                { actions.chart(ChartMode.TEMPERATURE) },
                label = { Text("Temperature") },
            )
            FilterChip(
                state.chart == ChartMode.RAIN,
                { actions.chart(ChartMode.RAIN) },
                label = { Text("Precipitation") },
            )
        }
        WeatherChart(state, actions)
        val focus = state.focus
        if (focus != null) {
            Text(
                "${localTime(focus.time,state.place.zone,"EEE HH:mm")} · ${temperature(focus.temperature,state.fahrenheit)}${if(state.fahrenheit)"F"else"C"} · ${decimal(focus.rain," mm")} · ${focus.probability?.roundToInt()?.let{"$it% chance"}?:"Chance unavailable"}",
                style = MaterialTheme.typography.bodySmall,
                color = Navy,
            )
            Slider(
                value = state.hours.indexOf(focus).coerceAtLeast(0).toFloat(),
                onValueChange = { i ->
                    actions.hour(
                        state.hours[i.roundToInt().coerceIn(0, state.hours.lastIndex)].time
                    )
                },
                valueRange = 0f..maxOf(1, state.hours.lastIndex).toFloat(),
                modifier = Modifier.semantics { contentDescription = "Forecast hour" },
            )
        }
        Text(
            if (state.chart == ChartMode.RAIN)
                "Amounts and probabilities refer to the hour ending at the labelled time."
            else "Hourly air temperature at 2 m. Times follow ${state.place.zone}.",
            color = Muted,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun WeatherChart(state: SkyState, actions: SkyActions) {
    val hours = state.hours
    val rain = state.chart == ChartMode.RAIN
    val values = hours.map {
        if (rain) it.rain
        else it.temperature?.let { c -> if (state.fahrenheit) c * 9 / 5 + 32 else c }
    }
    val finite = values.filterNotNull()
    val low = if (rain) 0.0 else (finite.minOrNull() ?: 0.0) - 2
    val high =
        if (rain) maxOf(1.0, finite.maxOrNull() ?: 0.0)
        else maxOf(low + 4, (finite.maxOrNull() ?: 0.0) + 2)
    val selected = hours.indexOfFirst { it.time == state.focus?.time }.coerceAtLeast(0)
    Canvas(
        Modifier.fillMaxWidth()
            .height(180.dp)
            .padding(top = 10.dp)
            .semantics {
                contentDescription =
                    "${if(rain) "Precipitation amount"else"Temperature"} chart. Select an hour with the slider or Forecast list."
            }
            .pointerInput(hours) {
                detectTapGestures { p ->
                    if (hours.isNotEmpty()) {
                        val index =
                            (((p.x - 40.dp.toPx()) / (size.width - 50.dp.toPx())).coerceIn(0f, 1f) *
                                    hours.lastIndex)
                                .roundToInt()
                        actions.hour(hours[index].time)
                    }
                }
            }
    ) {
        val left = 36.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 14.dp.toPx()
        val bottom = size.height - 24.dp.toPx()
        val paint =
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(82, 105, 124)
                textSize = 10.sp.toPx()
            }
        fun x(i: Int) = left + (right - left) * i / maxOf(1, hours.lastIndex)
        fun y(v: Double) = bottom - ((v - low) / (high - low)).toFloat() * (bottom - top)
        for (i in 0..3) {
            val yy = top + (bottom - top) * i / 3
            drawLine(Border, Offset(left, yy), Offset(right, yy), 1f)
            val value = high - (high - low) * i / 3
            drawContext.canvas.nativeCanvas.drawText(
                if (rain) String.format(Locale.ROOT, "%.1f", value)
                else value.roundToInt().toString(),
                0f,
                yy + 4.dp.toPx(),
                paint,
            )
        }
        if (hours.isNotEmpty()) {
            val path = Path()
            var started = false
            values.forEachIndexed { i, v ->
                if (v == null) started = false
                else if (rain) {
                    if (v > 0)
                        drawRoundRect(
                            Color(0xFF6B9BB6),
                            Offset(x(i) - 3.dp.toPx(), y(v)),
                            Size(6.dp.toPx(), maxOf(0f, bottom - y(v))),
                            CornerRadius(3f),
                        )
                } else {
                    if (!started) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v))
                    started = true
                }
            }
            if (!rain)
                drawPath(
                    path,
                    Blue,
                    style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            drawLine(
                Ochre.copy(alpha = .5f),
                Offset(x(selected), top),
                Offset(x(selected), bottom),
                1.dp.toPx(),
            )
            values.getOrNull(selected)?.let {
                drawCircle(Ochre, 4.dp.toPx(), Offset(x(selected), y(it)))
            }
            for (i in listOf(0, hours.lastIndex / 2, hours.lastIndex).distinct()) drawContext.canvas
                .nativeCanvas
                .drawText(
                    localTime(hours[i].time, state.place.zone),
                    x(i) - 13.dp.toPx(),
                    size.height - 5.dp.toPx(),
                    paint,
                )
        }
    }
}

@Composable
private fun DailyRow(day: Day, state: SkyState, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.White,
        shape = RoundedCornerShape(17.dp),
        border = BorderStroke(1.dp, Border),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    localTime(day.time, state.place.zone, "EEE, d MMM"),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(condition(day.code).label, color = Muted, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${temperature(day.low,state.fahrenheit)}  /  ${temperature(day.high,state.fahrenheit)}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "${decimal(day.rain," mm")} · ${day.probability?.roundToInt()?.let{"$it%"}?:"—"}",
                    color = Muted,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun Hours(state: SkyState, actions: SkyActions, modifier: Modifier) {
    LazyColumn(
        modifier.testTag("workspace"),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Eyebrow("Forecast explorer")
            Heading("Make room for the day.", "${state.place.name} · ${state.place.zone}")
        }
        if (state.record != null) {
            if (state.message.isNotBlank()) item { Notice(state.message) }
            item { Days(state, actions) }
            item { ChartPanel(state, actions) }
            items(state.hours, key = { it.time }) { hour ->
                Surface(
                    onClick = { actions.hour(hour.time) },
                    color = if (state.focus?.time == hour.time) Color(0xFFE3EEF6) else Color.White,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                localTime(hour.time, state.place.zone, "EEE HH:mm"),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(condition(hour.code).label, color = Muted, fontSize = 11.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                temperature(hour.temperature, state.fahrenheit),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "${decimal(hour.rain," mm")} · ${hour.probability?.roundToInt()?.let{"$it%"}?:"—"}",
                                color = Muted,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
            item { Attribution() }
        } else item { Notice("Choose a place or refresh to open its forecast.") }
    }
}

@Composable
private fun PlaceList(state: SkyState, actions: SkyActions, modifier: Modifier) {
    LazyColumn(
        modifier.testTag("workspace"),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Eyebrow("A few places to keep close")
            Heading("Where is your sky?", "Four Iranian cities, with their local time kept intact.")
        }
        if (state.message.isNotBlank()) item { Notice(state.message) }
        items(Places.all, key = { it.id }) { place ->
            val record = state.records[place.id]
            val c = record?.forecast?.current
            Surface(
                onClick = { actions.place(place) },
                shape = RoundedCornerShape(24.dp),
                border =
                    BorderStroke(
                        if (state.place.id == place.id) 2.dp else 1.dp,
                        if (state.place.id == place.id) Blue else Border,
                    ),
            ) {
                Box(Modifier.height(190.dp).fillMaxWidth()) {
                    WeatherScene(
                        condition(c?.code).scene,
                        c?.isDay != false,
                        false,
                        Modifier.fillMaxSize(),
                    )
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            place.name,
                            color = if (c?.isDay == false) Color.White else Navy,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            temperature(c?.temperature, state.fahrenheit),
                            color = if (c?.isDay == false) Color.White else Navy,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Surface(
                            color = Color.White.copy(alpha = .9f),
                            shape = RoundedCornerShape(7.dp),
                        ) {
                            Text(
                                if (record?.origin == Origin.NETWORK) "Saved forecast"
                                else "Recorded forecast",
                                Modifier.padding(6.dp),
                                fontSize = 10.sp,
                                color = Navy,
                            )
                        }
                    }
                }
            }
        }
        item {
            Text(
                "Places are selected manually. SkyCanvas does not request your location.",
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Studio(state: SkyState, actions: SkyActions, frames: FrameSummary, modifier: Modifier) {
    LazyColumn(
        modifier.testTag("workspace"),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Eyebrow("The landscape studio")
            Heading(
                "A different kind of forecast.",
                "Explore the illustration, then tune the workspace to your day.",
            )
        }
        item {
            Surface(shape = RoundedCornerShape(24.dp)) {
                WeatherScene(
                    state.studioScene,
                    state.studioDay,
                    state.motion,
                    Modifier.fillMaxWidth().height(260.dp),
                )
            }
        }
        item {
            Notice(
                "Scene study · illustration only. These controls do not change the weather data."
            )
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                for (scene in Scene.entries) FilterChip(
                    state.studioScene == scene,
                    { actions.scene(scene) },
                    label = { Text(scene.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(state.studioDay, { actions.daylight(true) }, label = { Text("Day") })
                FilterChip(!state.studioDay, { actions.daylight(false) }, label = { Text("Night") })
            }
        }
        item {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Gentle motion", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Clouds, water and starlight; follows system motion settings.",
                            color = Muted,
                            fontSize = 11.sp,
                        )
                    }
                    Switch(
                        state.motion,
                        actions.motion,
                        Modifier.semantics { contentDescription = "Gentle motion" },
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Border)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Use Fahrenheit", style = MaterialTheme.typography.titleSmall)
                        Text("Rain stays in mm and wind in km/h.", color = Muted, fontSize = 11.sp)
                    }
                    Switch(
                        state.fahrenheit,
                        actions.fahrenheit,
                        Modifier.semantics { contentDescription = "Use Fahrenheit" },
                    )
                }
            }
        }
        item {
            Card {
                Heading("A measured kind of motion")
                Text(
                    "Window frame timings · latest 600 samples",
                    color = Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Metric("p50", decimal(frames.p50Ms), Modifier.weight(1f), "ms")
                    Metric("p95", decimal(frames.p95Ms), Modifier.weight(1f), "ms")
                    Metric("Samples", frames.count.toString(), Modifier.weight(1f))
                }
                Text(
                    if (frames.count == 0)
                        "No device frames measured yet. Frame timings appear while the app draws on a device."
                    else
                        "${frames.overBudget} frames over their reported budget. ${frames.callbacksMissed} metrics callbacks missed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = actions.resetFrames) { Text("Reset") }
                    TextButton(onClick = actions.exportFrames, enabled = frames.count > 0) {
                        Text("Export timings")
                    }
                }
            }
        }
        if (state.message.isNotBlank()) item { Notice(state.message) }
        item {
            Card {
                Text("Source and care", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Open-Meteo supplies model forecasts. Saved and recorded forecasts retain their receive time. Missing values stay unavailable, and connection failures keep your last saved forecast.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "SkyCanvas is an independent implementation. It contains no employer code, internal services or confidential data.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "Version 1.0.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = Blue,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        item { Attribution() }
    }
}

@Composable
private fun Attribution() {
    val uri = LocalUriHandler.current
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(
            "Weather data by Open-Meteo. Model forecasts, with local times in Asia/Tehran.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { uri.openUri("https://open-meteo.com/") }) { Text("Open-Meteo") }
            TextButton(onClick = { uri.openUri("https://creativecommons.org/licenses/by/4.0/") }) {
                Text("CC BY 4.0")
            }
        }
    }
}
