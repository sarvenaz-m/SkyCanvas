export const places = [
  {
    id: "isfahan",
    name: "Isfahan",
    latitude: 32.6546,
    longitude: 51.668,
    zone: "Asia/Tehran",
  },
  {
    id: "shiraz",
    name: "Shiraz",
    latitude: 29.5918,
    longitude: 52.5837,
    zone: "Asia/Tehran",
  },
  {
    id: "tehran",
    name: "Tehran",
    latitude: 35.6892,
    longitude: 51.389,
    zone: "Asia/Tehran",
  },
  {
    id: "tabriz",
    name: "Tabriz",
    latitude: 38.08,
    longitude: 46.2919,
    zone: "Asia/Tehran",
  },
  {
    id: "mashhad",
    name: "Mashhad",
    latitude: 36.2605,
    longitude: 59.6168,
    zone: "Asia/Tehran",
  },
  {
    id: "bandar-abbas",
    name: "Bandar Abbas",
    latitude: 27.1832,
    longitude: 56.2666,
    zone: "Asia/Tehran",
  },
];
export const scenes = ["clear", "cloud", "fog", "rain", "snow", "storm"];
export function condition(code) {
  const names = {
    0: "Clear sky",
    1: "Mainly clear",
    2: "Partly cloudy",
    3: "Overcast",
    45: "Fog",
    48: "Rime fog",
    51: "Light drizzle",
    53: "Moderate drizzle",
    55: "Dense drizzle",
    56: "Freezing drizzle",
    57: "Freezing drizzle",
    61: "Slight rain",
    63: "Moderate rain",
    65: "Heavy rain",
    66: "Freezing rain",
    67: "Freezing rain",
    71: "Slight snow",
    73: "Moderate snow",
    75: "Heavy snow",
    77: "Snow grains",
    80: "Rain showers",
    81: "Rain showers",
    82: "Rain showers",
    85: "Snow showers",
    86: "Snow showers",
    95: "Thunderstorm",
    96: "Thunderstorm with hail",
    97: "Thunderstorm",
    99: "Thunderstorm with hail",
  };
  const scene =
    code === 0 || code === 1
      ? "clear"
      : [45, 48].includes(code)
        ? "fog"
        : [51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82].includes(code)
          ? "rain"
          : [71, 73, 75, 77, 85, 86].includes(code)
            ? "snow"
            : [95, 96, 97, 99].includes(code)
              ? "storm"
              : "cloud";
  return { label: names[code] || "Conditions unavailable", scene };
}
export function decode(payload, place) {
  if (
    typeof payload !== "string" ||
    new TextEncoder().encode(payload).length > 1048576
  )
    throw Error("Forecast size");
  const d = JSON.parse(payload),
    check = (ok, message) => {
      if (!ok) throw Error(message);
    };
  check(!d.error && d.timezone === place.zone, "Forecast timezone");
  check(
    Number.isFinite(d.latitude) &&
      Math.abs(d.latitude - place.latitude) < 0.75 &&
      Number.isFinite(d.longitude) &&
      Math.abs(d.longitude - place.longitude) < 0.75,
    "Forecast grid",
  );
  const unit = (s, k, v) => check(d[s]?.[k] === v, "Forecast units");
  for (const s of ["current_units", "hourly_units", "daily_units"])
    unit(s, "time", "unixtime");
  for (const s of ["current_units", "hourly_units"]) {
    unit(s, "temperature_2m", "°C");
    unit(s, "precipitation", "mm");
  }
  unit("current_units", "apparent_temperature", "°C");
  unit("current_units", "relative_humidity_2m", "%");
  unit("current_units", "wind_speed_10m", "km/h");
  unit("hourly_units", "precipitation_probability", "%");
  unit("daily_units", "temperature_2m_max", "°C");
  unit("daily_units", "temperature_2m_min", "°C");
  unit("daily_units", "precipitation_sum", "mm");
  unit("daily_units", "precipitation_probability_max", "%");
  const n = (v, min = -Number.MAX_VALUE, max = Number.MAX_VALUE) => {
    if (v == null) return null;
    check(
      typeof v === "number" && Number.isFinite(v) && v >= min && v <= max,
      "Forecast value",
    );
    return v;
  };
  const code = (v) => {
    if (v == null) return null;
    check(Number.isInteger(v) && v >= 0 && v <= 999, "Condition code");
    return v;
  };
  const day = (v) => {
    if (v == null) return null;
    check(v === 0 || v === 1, "Day/night flag");
    return v === 1;
  };
  const epoch = (v, optional = false) => {
    if (v == null && optional) return null;
    check(Number.isSafeInteger(v) && v > 0, "Forecast timestamp");
    return v;
  };
  const c = d.current;
  check(c && d.hourly && d.daily, "Forecast sections");
  const current = {
    time: epoch(c.time),
    temperature: n(c.temperature_2m, -150, 100),
    feelsLike: n(c.apparent_temperature, -150, 150),
    humidity: n(c.relative_humidity_2m, 0, 100),
    wind: n(c.wind_speed_10m, 0),
    code: code(c.weather_code),
    isDay: day(c.is_day),
  };
  const h = d.hourly;
  check(
    Array.isArray(h.time) && h.time.length >= 1 && h.time.length <= 384,
    "Hourly count",
  );
  for (const k of [
    "temperature_2m",
    "precipitation",
    "precipitation_probability",
    "weather_code",
    "is_day",
  ])
    check(
      Array.isArray(h[k]) && h[k].length === h.time.length,
      "Hourly columns",
    );
  const hours = h.time.map((t, i) => ({
    time: epoch(t),
    temperature: n(h.temperature_2m[i], -150, 100),
    rain: n(h.precipitation[i], 0),
    probability: n(h.precipitation_probability[i], 0, 100),
    code: code(h.weather_code[i]),
    isDay: day(h.is_day[i]),
  }));
  check(
    hours.every((v, i) => !i || v.time - hours[i - 1].time === 3600),
    "Hourly timing",
  );
  check(
    current.time >= hours[0].time && current.time <= hours.at(-1).time + 3600,
    "Current time",
  );
  const q = d.daily;
  check(
    Array.isArray(q.time) && q.time.length >= 1 && q.time.length <= 16,
    "Daily count",
  );
  for (const k of [
    "temperature_2m_min",
    "temperature_2m_max",
    "precipitation_sum",
    "precipitation_probability_max",
    "sunrise",
    "sunset",
    "weather_code",
  ])
    check(
      Array.isArray(q[k]) && q[k].length === q.time.length,
      "Daily columns",
    );
  const days = q.time.map((t, i) => {
    const low = n(q.temperature_2m_min[i], -150, 100),
      high = n(q.temperature_2m_max[i], -150, 100);
    check(low == null || high == null || low <= high, "Daily range");
    return {
      time: epoch(t),
      low,
      high,
      rain: n(q.precipitation_sum[i], 0),
      probability: n(q.precipitation_probability_max[i], 0, 100),
      sunrise: epoch(q.sunrise[i], true),
      sunset: epoch(q.sunset[i], true),
      code: code(q.weather_code[i]),
    };
  });
  for (let i = 1; i < days.length; i++) {
    const previous = new Date(dateKey(days[i - 1].time, place.zone));
    previous.setUTCDate(previous.getUTCDate() + 1);
    check(
      previous.toISOString().slice(0, 10) === dateKey(days[i].time, place.zone),
      "Daily dates",
    );
  }
  return { place, current, hours, days };
}
export function dateKey(epoch, zone) {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: zone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(new Date(epoch * 1000));
  return ["year", "month", "day"]
    .map((k) => parts.find((p) => p.type === k).value)
    .join("-");
}
export function time(
  epoch,
  zone,
  options = { hour: "2-digit", minute: "2-digit", hour12: false },
) {
  return new Intl.DateTimeFormat("en-GB", {
    timeZone: zone,
    ...options,
  }).format(new Date(epoch * 1000));
}
export function temp(value, f = false) {
  return value == null
    ? "—"
    : Math.round(f ? (value * 9) / 5 + 32 : value) + "°";
}
export function amount(value, unit = "") {
  return value == null ? "—" : value.toFixed(1) + unit;
}
export function hoursFor(f, day) {
  return day == null
    ? f.hours.filter((h) => h.time >= f.current.time).slice(0, 24)
    : f.hours.filter(
        (h) => dateKey(h.time, f.place.zone) === dateKey(day, f.place.zone),
      );
}
export function forecastUrl(place) {
  const q = new URLSearchParams({
    latitude: place.latitude,
    longitude: place.longitude,
    timezone: place.zone,
    timeformat: "unixtime",
    forecast_days: 7,
    temperature_unit: "celsius",
    wind_speed_unit: "kmh",
    precipitation_unit: "mm",
    current:
      "temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,cloud_cover,wind_speed_10m",
    hourly:
      "temperature_2m,precipitation_probability,precipitation,weather_code,is_day",
    daily:
      "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_sum,precipitation_probability_max",
  });
  return "https://api.open-meteo.com/v1/forecast?" + q;
}
export function frameReport(intervals) {
  if (!intervals.length) return null;
  const sorted = [...intervals].sort((a, b) => a - b),
    p = (n) => sorted[Math.max(0, Math.ceil(n * sorted.length) - 1)],
    base = p(0.5);
  return {
    schema: "skycanvas.browser-frames/v1",
    sampleCount: intervals.length,
    p50Ms: base,
    p95Ms: p(0.95),
    meanCadenceHz:
      1000 / (intervals.reduce((a, b) => a + b, 0) / intervals.length),
    intervalsOver1_5Median: intervals.filter((v) => v > base * 1.5).length,
    notes:
      "requestAnimationFrame cadence in this browser. This is not an Android frame benchmark.",
  };
}
