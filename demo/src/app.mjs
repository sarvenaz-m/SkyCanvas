import {
  places,
  scenes,
  condition,
  decode,
  dateKey,
  time,
  temp,
  amount,
  hoursFor,
  forecastUrl,
  frameReport,
} from "./model.mjs";
import { sceneSvg, setSceneDaylight } from "./scene.mjs";

const bundle = JSON.parse(document.getElementById("forecast-data").textContent);
const app = document.getElementById("app");
const escape = (s) =>
  String(s).replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ],
  );
let preferences = { place: "isfahan", fahrenheit: false, motion: true };
try {
  preferences = {
    ...preferences,
    ...JSON.parse(localStorage.getItem("skycanvas.preferences") || "{}"),
  };
} catch {}
if (!places.some((p) => p.id === preferences.place))
  preferences.place = "isfahan";
const state = {
  tab: "today",
  place: places.find((p) => p.id === preferences.place),
  record: null,
  records: { ...bundle.snapshots },
  day: null,
  focus: null,
  chart: "temperature",
  scene: "clear",
  daylight: true,
  fahrenheit: preferences.fahrenheit === true,
  motion: preferences.motion !== false,
  loading: true,
  refreshing: false,
  message: "",
  storage: false,
  frames: null,
  measuring: false,
};
let db = null,
  request = null,
  generation = 0,
  blockedUntil = 0,
  lastAttempt = 0,
  measureToken = 0;
const forecast = () =>
  state.record ? decode(state.record.payload, state.place) : null;
const rows = () => (forecast() ? hoursFor(forecast(), state.day) : []);
const focus = () => rows().find((h) => h.time === state.focus) || rows()[0];
const announce = (s) => {
  document.getElementById("status").textContent = s;
};
function savePreferences() {
  try {
    localStorage.setItem(
      "skycanvas.preferences",
      JSON.stringify({
        place: state.place.id,
        fahrenheit: state.fahrenheit,
        motion: state.motion,
      }),
    );
  } catch {
    state.message = "This preference could not be saved for your next visit.";
  }
}
function openDatabase() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open("skycanvas-weather", 1);
    request.onupgradeneeded = () =>
      request.result.createObjectStore("forecasts", { keyPath: "placeId" });
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
    request.onblocked = () => reject(Error("Storage blocked"));
  });
}
function readRecord(id) {
  return new Promise((resolve, reject) => {
    const tx = db.transaction("forecasts", "readonly"),
      request = tx.objectStore("forecasts").get(id);
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}
function putRecord(row) {
  return new Promise((resolve, reject) => {
    const tx = db.transaction("forecasts", "readwrite");
    tx.objectStore("forecasts").put(row);
    tx.oncomplete = () => resolve();
    tx.onabort = tx.onerror = () =>
      reject(tx.error || Error("Storage unavailable"));
  });
}
function validateRecord(row, place) {
  if (
    !row ||
    row.placeId !== place.id ||
    !Number.isSafeInteger(row.fetchedAt) ||
    row.fetchedAt <= 0 ||
    !["RECORDED", "NETWORK"].includes(row.origin)
  )
    throw Error("Saved forecast");
  decode(row.payload, place);
  return row;
}
async function selectPlace(id) {
  const place = places.find((p) => p.id === id);
  if (!place) return;
  const token = ++generation;
  request?.abort();
  request = null;
  state.place = place;
  state.day = null;
  state.focus = null;
  state.refreshing = false;
  state.loading = true;
  state.message = "";
  state.tab = state.tab === "places" ? "today" : state.tab;
  state.record = bundle.snapshots[id];
  render();
  savePreferences();
  let row = bundle.snapshots[id],
    message = "";
  if (db) {
    try {
      const saved = await readRecord(id);
      if (saved) row = validateRecord(saved, place);
      else await putRecord(row);
    } catch {
      message =
        "The saved forecast could not be read. A recorded forecast is shown.";
      try {
        await putRecord(row);
      } catch {
        message =
          "Local storage could not save this forecast. A recorded forecast is shown.";
      }
    }
  }
  if (token !== generation) return;
  state.record = row;
  state.records[id] = row;
  state.loading = false;
  state.message = message;
  render();
}
async function refresh() {
  if (state.refreshing || state.loading) return;
  const now = Date.now() / 1000;
  if (now < blockedUntil) {
    state.message =
      "The weather service asked us to wait. Try again after " +
      time(blockedUntil, state.place.zone) +
      ".";
    render();
    return;
  }
  if (now - lastAttempt < 5) {
    state.message = "The last request just finished. Please wait a moment.";
    render();
    return;
  }
  const token = generation,
    place = state.place,
    controller = new AbortController();
  request = controller;
  lastAttempt = now;
  state.refreshing = true;
  state.message = "";
  render();
  let timedOut = false;
  const timer = setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, 15000);
  try {
    const response = await fetch(forecastUrl(place), {
      signal: controller.signal,
      headers: { Accept: "application/json" },
    });
    if (response.status === 429) {
      const h = response.headers.get("Retry-After"),
        seconds = /^\d+$/.test(h || "")
          ? Number(h)
          : (Date.parse(h || "") - Date.now()) / 1000;
      blockedUntil =
        Date.now() / 1000 +
        Math.max(5, Math.min(3600, Number.isFinite(seconds) ? seconds : 60));
      throw Error("RATE");
    }
    if (!response.ok) throw Error("NETWORK");
    if (
      !/application\/(?:[\w.+-]*\+)?json/i.test(
        response.headers.get("Content-Type") || "",
      )
    )
      throw Error("FORMAT");
    const reader = response.body.getReader(),
      decoder = new TextDecoder("utf-8", { fatal: true });
    let size = 0,
      payload = "";
    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        size += value.byteLength;
        if (size > 1048576) throw Error("FORMAT");
        payload += decoder.decode(value, { stream: true });
      }
      payload += decoder.decode();
    } finally {
      await reader.cancel().catch(() => {});
      reader.releaseLock();
    }
    try {
      decode(payload, place);
    } catch {
      throw Error("FORMAT");
    }
    const row = {
      placeId: place.id,
      payload,
      fetchedAt: Math.floor(Date.now() / 1000),
      origin: "NETWORK",
    };
    if (!db) throw Error("STORAGE");
    try {
      await putRecord(row);
    } catch {
      throw Error("STORAGE");
    }
    state.records[place.id] = row;
    if (token !== generation) return;
    state.record = row;
    state.day = null;
    state.focus = null;
    state.message = "Forecast updated and saved.";
    announce(state.message);
  } catch (error) {
    if (token !== generation) return;
    if (error.name === "AbortError" && !timedOut) return;
    state.message =
      error.message === "RATE"
        ? "The weather service is busy. Try again after " +
          time(blockedUntil, place.zone) +
          ". Your saved forecast is still available."
        : error.message === "FORMAT"
          ? "The forecast response was unexpected. Your saved forecast has been kept."
          : error.message === "STORAGE"
            ? "The new forecast could not be saved. Your previous forecast is still available."
            : "Could not reach the forecast service. Your saved forecast is still available.";
    announce(state.message);
  } finally {
    clearTimeout(timer);
    if (token === generation) {
      state.refreshing = false;
      request = null;
      render();
    }
  }
}
const icons = {
  today:
    '<circle cx="12" cy="12" r="4"/><path d="M12 1v3m0 16v3M1 12h3m16 0h3M4 4l2 2m12 12 2 2M4 20l2-2M18 6l2-2"/>',
  hours: '<path d="M4 6h16M4 12h16M4 18h16"/>',
  places:
    '<path d="M19 9c0 6-7 13-7 13S5 15 5 9a7 7 0 1 1 14 0Z"/><circle cx="12" cy="9" r="2"/>',
  studio:
    '<rect x="3" y="3" width="18" height="18" rx="3"/><circle cx="16" cy="8" r="2"/><path d="m4 17 6-6 10 9"/>',
};
const icon = (id) =>
  `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true">${icons[id]}</svg>`;
function nav() {
  return [
    ["today", "Today"],
    ["hours", "Forecast"],
    ["places", "Places"],
    ["studio", "Studio"],
  ]
    .map(
      ([id, label]) =>
        `<button id="nav-${id}" class="nav ${state.tab === id ? "active" : ""}" data-action="tab" data-value="${id}" ${state.tab === id ? 'aria-current="page"' : ""}>${icon(id)}${label}</button>`,
    )
    .join("");
}
const brand = () =>
  '<div class="brandmark">SC</div><div><b>SkyCanvas</b><span>AN ILLUSTRATED DAY</span></div>';
const footer = () =>
  `<footer><span>Weather data by <a href="https://open-meteo.com/" target="_blank" rel="noreferrer">Open-Meteo</a> · <a href="https://creativecommons.org/licenses/by/4.0/" target="_blank" rel="noreferrer">CC BY 4.0</a><br>Model forecasts · local times in Asia/Tehran.</span><span>SkyCanvas 1.0.0<br>Independent software and original scene artwork.</span></footer>`;
function heading(eyebrow, title, detail) {
  return `<div class="page-heading"><div><p class="eyebrow">${eyebrow}</p><h1 tabindex="-1">${title}</h1><p>${detail}</p></div></div>`;
}
function stamp(record) {
  const recorded = record.origin === "RECORDED",
    stale =
      Date.now() / 1000 < record.fetchedAt ||
      Date.now() / 1000 - record.fetchedAt >= 1800;
  return `<span class="tag ${recorded ? "recorded" : ""}">${recorded ? "RECORDED FORECAST" : stale ? "SAVED · NEEDS REFRESH" : "SAVED FORECAST"}</span>`;
}
function hero() {
  const f = forecast(),
    c = f.current;
  return `<div class="hero ${c.isDay === false ? "night" : ""}">${sceneSvg(condition(c.code).scene, c.isDay !== false, "hero-sky")}<div class="hero-content"><h2>${state.place.name}</h2><div class="hero-date">IRAN · ${time(c.time, state.place.zone, { weekday: "short", day: "numeric", month: "short" }).toUpperCase()}</div><div class="temperature">${temp(c.temperature, state.fahrenheit)}<sup>${state.fahrenheit ? "F" : "C"}</sup></div><div class="hero-condition">${condition(c.code).label}</div><div class="hero-meta">MODEL CONDITIONS · ${time(c.time, state.place.zone)}</div></div><div class="hero-source">AN ILLUSTRATED VIEW</div></div>`;
}
function metric(label, value, unit = "", className = "") {
  return `<div class="metric ${className}"><span class="label">${label}</span><b>${value}${unit ? ` <small>${unit}</small>` : ""}</b></div>`;
}
function sourceCard() {
  const f = forecast(),
    recorded = state.record.origin === "RECORDED";
  return `<div class="card source-card">${stamp(state.record)}<h3>${recorded ? "A real forecast, kept for exploring." : "A forecast you can return to."}</h3><p>${recorded ? "This is a saved Open-Meteo response. Use Get latest when you want a fresh forecast." : "The latest successful response stays on this device for the next visit."}</p><div class="source-stamp"><span>Received<br><b>${time(state.record.fetchedAt, state.place.zone, { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit", hour12: false })}</b></span><span>Timezone<br><b>${state.place.zone}</b></span></div></div>`;
}
function chartDrawing(viewWidth = 600) {
  const hours = rows(),
    rain = state.chart === "rain",
    values = hours.map((h) =>
      rain
        ? h.rain
        : h.temperature == null
          ? null
          : state.fahrenheit
            ? (h.temperature * 9) / 5 + 32
            : h.temperature,
    ),
    valid = values.filter((v) => v != null),
    low = rain
      ? 0
      : (Math.min(...valid, Infinity) === Infinity ? 0 : Math.min(...valid)) -
        2,
    high = rain
      ? Math.max(1, ...valid)
      : Math.max(
          low + 4,
          (Math.max(...valid, -Infinity) === -Infinity
            ? 0
            : Math.max(...valid)) + 2,
        );
  const left = 36,
    right = viewWidth - 18,
    top = 24,
    bottom = 184,
    x = (i) => left + ((right - left) * i) / Math.max(1, hours.length - 1),
    y = (v) => bottom - ((v - low) / (high - low)) * (bottom - top),
    selected = Math.max(
      0,
      hours.findIndex((h) => h.time === focus()?.time),
    );
  let grid = "",
    path = "",
    started = false,
    bars = "";
  for (let i = 0; i < 4; i++) {
    const yy = top + ((bottom - top) * i) / 3,
      v = high - ((high - low) * i) / 3;
    grid += `<path class="grid" d="M${left} ${yy}H${right}"/><text x="0" y="${yy + 4}">${rain ? v.toFixed(1) : Math.round(v)}</text>`;
  }
  values.forEach((v, i) => {
    if (v == null) {
      started = false;
      return;
    }
    if (rain) {
      if (v > 0)
        bars += `<rect x="${x(i) - 4}" y="${y(v)}" width="8" height="${bottom - y(v)}" rx="3" fill="#6b9bb6"/>`;
    } else {
      path += `${started ? "L" : "M"}${x(i)} ${y(v)} `;
      started = true;
    }
  });
  const labels = [0, Math.floor((hours.length - 1) / 2), hours.length - 1]
    .filter((v, i, a) => v >= 0 && a.indexOf(v) === i)
    .map(
      (i) =>
        `<text x="${x(i)}" y="211" text-anchor="${i === hours.length - 1 ? "end" : "middle"}">${time(hours[i].time, state.place.zone)}</text>`,
    )
    .join("");
  return `<svg id="weather-chart" class="chart" viewBox="0 0 ${viewWidth} 225" role="img" aria-label="${rain ? "Precipitation amount in mm" : "Temperature in " + (state.fahrenheit ? "Fahrenheit" : "Celsius")}. Use the hour slider or Forecast table to inspect values."><text x="36" y="9">${rain ? "Precipitation / mm" : state.fahrenheit ? "Temperature / °F" : "Temperature / °C"}</text>${grid}${bars}<path class="line" d="${path}"/>${hours.length ? `<path class="cursor" d="M${x(selected)} ${top}V${bottom}"/>${values[selected] != null ? `<circle cx="${x(selected)}" cy="${y(values[selected])}" r="4" fill="#9a6123"/>` : ""}` : ""}${labels}</svg>`;
}
function focusMarkup() {
  const h = focus();
  return h
    ? `<div><small>${time(h.time, state.place.zone, { weekday: "short", hour: "2-digit", minute: "2-digit", hour12: false })}</small><br><b>${temp(h.temperature, state.fahrenheit)}${state.fahrenheit ? "F" : "C"}</b></div><div style="text-align:right"><b>${amount(h.rain, " mm")}</b><br><small>${h.probability == null ? "Chance unavailable" : Math.round(h.probability) + "% precipitation chance"}</small></div>`
    : "<small>No hourly data available.</small>";
}
function chartCard() {
  const hours = rows(),
    selected = Math.max(
      0,
      hours.findIndex((h) => h.time === focus()?.time),
    );
  return `<section class="card"><div class="section-top"><div><h2>Follow the hours</h2><p>${state.day ? time(state.day, state.place.zone, { weekday: "long", day: "numeric", month: "short" }) : "The next 24 forecast hours"}</p></div><div class="chips"><button id="chart-temperature" class="chip ${state.chart === "temperature" ? "active" : ""}" data-action="chart" data-value="temperature" aria-pressed="${state.chart === "temperature"}">Temperature</button><button id="chart-rain" class="chip ${state.chart === "rain" ? "active" : ""}" data-action="chart" data-value="rain" aria-pressed="${state.chart === "rain"}">Precipitation</button></div></div><div id="chart-visual">${chartDrawing()}</div><div class="chart-focus" id="chart-focus">${focusMarkup()}</div><label class="sr-only" for="hour-range">Forecast hour</label><input id="hour-range" class="hour-range" type="range" min="0" max="${Math.max(0, hours.length - 1)}" value="${selected}" step="1" ${!hours.length ? "disabled" : ""}><p class="chart-note">${state.chart === "rain" ? "Amounts and probabilities refer to the hour ending at the labelled time." : "Hourly air temperature at 2 m. Gaps remain visible when a value is unavailable."}</p></section>`;
}
function lightCard(day, modelTime) {
  if (!day?.sunrise || !day?.sunset || day.sunset <= day.sunrise) return "";
  const duration = Math.round((day.sunset - day.sunrise) / 60),
    elapsed = Math.max(
      0,
      Math.min(1, (modelTime - day.sunrise) / (day.sunset - day.sunrise)),
    );
  return `<section class="card" style="margin-top:20px"><div class="section-top"><div><h3>A little light to plan around.</h3><p>${Math.floor(duration / 60)} h ${duration % 60} min of daylight</p></div></div><div class="daylight-track"><span style="width:${elapsed * 100}%"></span></div><div style="display:flex;justify-content:space-between;font-size:10px;color:#52697c"><span>Sunrise <b>${time(day.sunrise, state.place.zone)}</b></span><span>Sunset <b>${time(day.sunset, state.place.zone)}</b></span></div><p class="chart-note" style="margin-top:8px">Daylight progress at model time ${time(modelTime, state.place.zone)}.</p></section>`;
}
function dailyRows() {
  const days = forecast().days,
    all = days.flatMap((d) => [d.low, d.high]).filter((v) => v != null),
    min = Math.min(...all),
    max = Math.max(...all);
  return days
    .map(
      (d) =>
        `<button class="daily-row" data-action="day" data-value="${d.time}"><span><b>${time(d.time, state.place.zone, { weekday: "short", day: "numeric", month: "short" })}</b><br><small>${condition(d.code).label}</small></span><span><small>${amount(d.rain, " mm")} · ${d.probability == null ? "—" : Math.round(d.probability) + "%"}</small><span class="range-line"><span style="margin-left:${d.low == null ? 0 : ((d.low - min) / Math.max(1, max - min)) * 60}%;width:${d.low == null || d.high == null ? 0 : ((d.high - d.low) / Math.max(1, max - min)) * 100}%"></span></span></span><span class="temps"><span>${temp(d.low, state.fahrenheit)}</span> / <b>${temp(d.high, state.fahrenheit)}</b></span></button>`,
    )
    .join("");
}
function today() {
  const f = forecast(),
    c = f.current,
    day = f.days.find(
      (d) =>
        dateKey(d.time, state.place.zone) === dateKey(c.time, state.place.zone),
    );
  return (
    heading(
      "YOUR SKY, THOUGHTFULLY DRAWN",
      "A view of your day.",
      "The forecast, the light and a little space to look ahead.",
    ) +
    `<div class="weather-grid">${hero()}<aside class="right-stack"><div class="metric-grid">${metric("Feels like", temp(c.feelsLike, state.fahrenheit))}${metric("Wind", amount(c.wind), "km/h")}${metric("Humidity", c.humidity == null ? "—" : Math.round(c.humidity) + "%")}${metric("Sunset", day?.sunset ? time(day.sunset, state.place.zone) : "—", "", "sun")}</div>${sourceCard()}</aside></div><div class="below-grid"><div>${chartCard()}${lightCard(day, c.time)}</div><section class="card"><div class="section-top"><div><h2>A week in view</h2><p>Daily ranges, with the details kept close.</p></div><span class="tag">7 DAYS</span></div>${dailyRows()}</section></div>`
  );
}
function hoursPage() {
  const f = forecast();
  return (
    heading(
      "FORECAST EXPLORER",
      "Make room for the day.",
      `${state.place.name} · ${state.place.zone}`,
    ) +
    `<div class="chips" style="margin-bottom:18px"><button id="day-next" class="chip ${state.day == null ? "active" : ""}" data-action="filter-day" data-value="next" aria-pressed="${state.day == null}">Next 24 h</button>${f.days.map((d) => `<button id="day-${d.time}" class="chip ${state.day === d.time ? "active" : ""}" data-action="filter-day" data-value="${d.time}" aria-pressed="${state.day === d.time}">${time(d.time, state.place.zone, { weekday: "short", day: "numeric" })}</button>`).join("")}</div><div class="forecast-layout">${chartCard()}<section class="card table-wrap"><h2>Hour by hour</h2><table class="hour-table"><thead><tr><th scope="col">Local time</th><th scope="col">Conditions</th><th scope="col">Temperature</th><th scope="col">Amount</th><th scope="col">Chance</th></tr></thead><tbody>${rows()
      .map(
        (h) =>
          `<tr class="${focus()?.time === h.time ? "active" : ""}" data-time="${h.time}"><td><button data-action="hour" data-value="${h.time}">${time(h.time, state.place.zone, { weekday: "short", hour: "2-digit", minute: "2-digit", hour12: false })}</button></td><td>${condition(h.code).label}</td><td>${temp(h.temperature, state.fahrenheit)}${state.fahrenheit ? "F" : "C"}</td><td>${amount(h.rain, " mm")}</td><td>${h.probability == null ? "—" : Math.round(h.probability) + "%"}</td></tr>`,
      )
      .join("")}</tbody></table></section></div>`
  );
}
function placesPage() {
  return (
    heading(
      "A FEW PLACES TO KEEP CLOSE",
      "Where is your sky?",
      "Four Iranian cities, with their local time kept intact.",
    ) +
    `<div class="place-grid">${places
      .map((p) => {
        const r = state.records[p.id] || bundle.snapshots[p.id],
          f = decode(r.payload, p),
          c = f.current;
        return `<button class="place-card ${c.isDay === false ? "night" : ""}" data-action="place" data-value="${p.id}" aria-label="Open ${p.name} forecast">${sceneSvg(condition(c.code).scene, c.isDay !== false, "place-" + p.id)}<div class="hero-content" style="color:${c.isDay === false ? "#fff" : "#183754"}"><b>${p.name}</b><span class="temp">${temp(c.temperature, state.fahrenheit)}</span>${stamp(r)}</div></button>`;
      })
      .join(
        "",
      )}</div><p class="small" style="margin-top:20px">Places are selected manually. SkyCanvas does not request your location.</p>`
  );
}
function studioPage() {
  return (
    heading(
      "THE LANDSCAPE STUDIO",
      "A different kind of forecast.",
      "Explore the illustration, then tune the workspace to your day.",
    ) +
    `<div class="studio-grid"><section><div class="studio-scene">${sceneSvg(state.scene, state.daylight, "studio-sky")}</div><p class="study-label"><b>Scene study · illustration only.</b> These controls do not change the weather data.</p><div class="chips">${scenes.map((s) => `<button id="scene-${s}" class="chip ${state.scene === s ? "active" : ""}" data-action="scene" data-value="${s}" aria-pressed="${state.scene === s}">${s[0].toUpperCase() + s.slice(1)}</button>`).join("")}</div><div class="chips" style="margin-top:12px"><button id="daylight-day" class="chip ${state.daylight ? "active" : ""}" data-action="daylight" data-value="day" aria-pressed="${state.daylight}">Day</button><button id="daylight-night" class="chip ${!state.daylight ? "active" : ""}" data-action="daylight" data-value="night" aria-pressed="${!state.daylight}">Night</button></div></section><aside class="studio-controls"><section class="card"><div class="setting"><div><b>Gentle motion</b><p>Clouds, water and starlight.<br>System reduced-motion settings take priority.</p></div><input id="motion" type="checkbox" aria-label="Gentle motion" ${state.motion ? "checked" : ""}></div><div class="setting"><div><b>Use Fahrenheit</b><p>Rain stays in mm and wind in km/h.</p></div><input id="fahrenheit" type="checkbox" aria-label="Use Fahrenheit" ${state.fahrenheit ? "checked" : ""}></div></section><section class="card"><h3>A measured kind of motion</h3><p class="frame-copy" style="margin-top:8px">Browser frame pacing · requestAnimationFrame cadence. Results describe this browser and viewport.</p><div class="frame-values"><div class="frame-value"><span>p50 / ms</span><b>${state.frames ? state.frames.p50Ms.toFixed(1) : "—"}</b></div><div class="frame-value"><span>p95 / ms</span><b>${state.frames ? state.frames.p95Ms.toFixed(1) : "—"}</b></div></div><p class="frame-copy">${state.frames ? `${state.frames.sampleCount} intervals · ${state.frames.intervalsOver1_5Median} longer than 1.5× the median.` : "No browser timings measured yet. A run collects 240 visible frame intervals."}</p><div class="actions" style="margin-top:14px"><button class="button primary" data-action="measure" ${state.measuring ? "disabled" : ""}>${state.measuring ? "Measuring…" : "Measure motion"}</button><button class="button" data-action="export-frames" ${!state.frames ? "disabled" : ""}>Export</button></div></section><section class="card"><h3>Source and care</h3><p class="frame-copy" style="margin-top:9px">Open-Meteo supplies model forecasts. Successful responses are kept in IndexedDB with their receive time. A failed refresh keeps the previous record.</p><p class="frame-copy" style="margin-top:9px">This independent implementation contains original artwork and no employer code, internal services or confidential data.</p></section></aside></div>`
  );
}
function render() {
  const active = document.activeElement?.id;
  app.className = state.motion ? "" : "no-motion";
  app.innerHTML = `<div class="app"><aside class="sidebar"><div class="brand">${brand()}</div><nav aria-label="Main navigation">${nav()}</nav><div class="sidebar-bottom">A forecast with a sense of place.<br>Made to look at, built to return to.<br><b style="display:block;margin-top:12px">SKYCANVAS / 1.0.0</b></div></aside><main><header class="topbar"><div class="brand mobile-brand">${brand()}</div><div class="top-title">${state.place.name}<span>Iran · Asia/Tehran</span></div><div class="actions"><button id="refresh" class="button primary" data-action="refresh" ${state.loading || state.refreshing ? "disabled" : ""}>${state.refreshing ? "Updating…" : "Get latest"}</button></div></header>${state.message ? `<div class="notice">${escape(state.message)}</div>` : ""}${state.record ? (state.tab === "today" ? today() : state.tab === "hours" ? hoursPage() : state.tab === "places" ? placesPage() : studioPage()) : '<div class="card">Opening the recorded forecast…</div>'}${footer()}</main></div>`;
  connectChart();
  if (active) document.getElementById(active)?.focus({ preventScroll: true });
}
let chartObserver;
function connectChart() {
  chartObserver?.disconnect();
  const target = document.getElementById("chart-visual");
  if (!target) return;
  let width = 0;
  const redraw = () => {
    const next = Math.max(220, Math.round(target.clientWidth));
    if (next !== width) {
      width = next;
      target.innerHTML = chartDrawing(width);
    }
  };
  redraw();
  chartObserver = new ResizeObserver(redraw);
  chartObserver.observe(target);
}
function selectHour(value) {
  const hours = rows(),
    selected = hours.find((h) => h.time === value);
  if (!selected) return;
  state.focus = value;
  const target = document.getElementById("chart-visual");
  if (target)
    target.innerHTML = chartDrawing(
      Math.max(220, Math.round(target.clientWidth)),
    );
  const info = document.getElementById("chart-focus");
  if (info) info.innerHTML = focusMarkup();
  const range = document.getElementById("hour-range");
  if (range) range.value = hours.indexOf(selected);
  document
    .querySelectorAll(".hour-table tr[data-time]")
    .forEach((row) =>
      row.classList.toggle("active", Number(row.dataset.time) === value),
    );
}
function download(name, data) {
  const blob = new Blob([JSON.stringify(data, null, 2)], {
      type: "application/json",
    }),
    url = URL.createObjectURL(blob),
    a = document.createElement("a");
  a.href = url;
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
async function measure() {
  if (state.measuring) return;
  const token = ++measureToken;
  state.measuring = true;
  render();
  const intervals = [];
  let previous = null,
    warmup = 0;
  const result = await new Promise((resolve) => {
    function tick(t) {
      if (token !== measureToken || document.visibilityState !== "visible") {
        resolve(null);
        return;
      }
      if (previous != null) {
        if (warmup++ >= 10) intervals.push(t - previous);
      }
      previous = t;
      if (intervals.length >= 240) resolve(frameReport(intervals));
      else requestAnimationFrame(tick);
    }
    requestAnimationFrame(tick);
  });
  state.measuring = false;
  if (result) {
    state.frames = {
      ...result,
      capturedAt: new Date().toISOString(),
      viewport: { width: innerWidth, height: innerHeight },
      scene: state.scene,
      daylight: state.daylight,
      motion: state.motion,
      systemReducedMotion: matchMedia("(prefers-reduced-motion:reduce)")
        .matches,
    };
    announce("Browser frame timings measured.");
  } else
    state.message =
      "The timing run paused because the page was hidden. Start a fresh run when you return.";
  render();
}
document.addEventListener("click", (event) => {
  const button = event.target.closest("[data-action]");
  if (!button || button.disabled) return;
  const { action, value } = button.dataset;
  if (action === "refresh") {
    refresh();
    return;
  }
  if (action === "place") {
    selectPlace(value);
    scrollTo(0, 0);
    return;
  }
  if (action === "hour") {
    selectHour(Number(value));
    return;
  }
  if (action === "measure") {
    measure();
    return;
  }
  if (action === "export-frames") {
    if (state.frames)
      download("SkyCanvas-browser-frame-timings.json", state.frames);
    return;
  }
  if (action === "tab") {
    state.tab = value;
    render();
    scrollTo(0, 0);
    return;
  }
  if (action === "chart") state.chart = value;
  if (action === "day" || action === "filter-day") {
    state.day = value === "next" ? null : Number(value);
    state.focus = null;
    if (action === "day") state.tab = "hours";
  }
  if (action === "scene") state.scene = value;
  if (action === "daylight") {
    state.daylight = value === "day";
    const svg = app.querySelector(".studio-scene .landscape");
    if (svg) setSceneDaylight(svg, state.daylight);
    for (const option of app.querySelectorAll('[data-action="daylight"]')) {
      const selected = (option.dataset.value === "day") === state.daylight;
      option.classList.toggle("active", selected);
      option.setAttribute("aria-pressed", String(selected));
    }
    announce(state.daylight ? "Daylight scene." : "Night scene.");
    return;
  }
  render();
});
document.addEventListener("input", (event) => {
  if (event.target.id === "hour-range") {
    const hours = rows();
    selectHour(hours[Number(event.target.value)]?.time);
  }
});
document.addEventListener("change", (event) => {
  if (event.target.id === "motion" || event.target.id === "fahrenheit") {
    state[event.target.id] = event.target.checked;
    savePreferences();
    render();
  }
});
document.addEventListener("click", (event) => {
  const svg = event.target.closest("#weather-chart");
  if (!svg) return;
  const rect = svg.getBoundingClientRect(),
    width = svg.viewBox.baseVal.width,
    x = ((event.clientX - rect.left) / rect.width) * width,
    hours = rows(),
    index = Math.round(
      Math.max(0, Math.min(1, (x - 36) / (width - 54))) * (hours.length - 1),
    );
  if (hours[index]) selectHour(hours[index].time);
});
document.addEventListener("visibilitychange", () => {
  document.body.classList.toggle("paused", document.hidden);
  if (document.hidden) measureToken++;
});
state.record = bundle.snapshots[state.place.id];
render();
try {
  db = await openDatabase();
  state.storage = true;
  db.onversionchange = () => {
    db.close();
    db = null;
    state.storage = false;
    state.message =
      "Local storage changed in another tab. Reload to reconnect.";
    render();
  };
} catch {
  state.message =
    "Local storage is unavailable. Recorded forecasts remain available; fresh responses cannot be saved in this browser.";
}
const startupMessage = state.message;
await selectPlace(state.place.id);
if (startupMessage) {
  state.message = startupMessage;
  render();
}
