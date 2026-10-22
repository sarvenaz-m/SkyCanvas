# SkyCanvas Android

**A little closer to the sky.**

SkyCanvas pairs a useful weather forecast with an original animated landscape for each city. Follow the arches of Isfahan, the gardens of Shiraz, Azadi beneath the Alborz, El Goli in Tabriz, the golden dome of Mashhad and the waterfront of Bandar Abbas. Each has its own daylight and night palette, with weather that follows the forecast.

<p>
  <a href="https://sarvenaz-m.github.io/SkyCanvas/">
    <img src="docs/assets/live-demo.svg" width="268" height="68" alt="Open Live Demo — explore six cities by day and night" />
  </a>
</p>

**Version 2.0.0** · Kotlin & Jetpack Compose · Six Iranian cities · Android 8.0+

![SkyCanvas version 2 running in the native Kotlin and Compose app](docs/screenshots/skycanvas-showcase.png)

## Six cities, in daylight

![Six original daylight landscapes in SkyCanvas version 2](docs/screenshots/cities-day-v2.png)

## A quieter kind of night

![The same six cities at night, with city lights, moonlight and distinct landmarks](docs/screenshots/cities-night-v2.png)

Clouds drift, sunlight breathes gently and highlights move across water. Shiraz has petals in the breeze; Tehran's windows glimmer; the fountains of Tabriz and Mashhad catch the light. On the Persian Gulf, a wooden lenj rocks beside swaying palms. Night brings staggered starlight and warm architectural lighting.

Studio's **Day** and **Night** controls blend the palette over 1.6 seconds. Choose any of the six cities and any weather study without changing the forecast you were viewing. **Gentle motion** freezes decorative movement, and the browser respects the system's reduced-motion preference.

![Six cities moving from daylight to night](docs/screenshots/day-night-motion.gif)

## Take a look

The [live demo](https://sarvenaz-m.github.io/SkyCanvas/) opens directly in your browser. The [offline companion](demo/SkyCanvas.html) includes its forecasts, artwork and font in one HTML file. **Get latest** requests a fresh forecast when the API is reachable. The companion shares the original artwork and data rules of the native app; its interface is a separate implementation.

![SkyCanvas version 2 — six city forecasts in the browser companion](docs/screenshots/companion-places.png)

<table>
  <tr>
    <td width="50%"><img src="docs/screenshots/companion-studio-night.png" alt="Bandar Abbas at night in the landscape studio" /></td>
    <td width="50%"><img src="docs/screenshots/companion-today.png" alt="Tehran forecast with its own Azadi illustration" /></td>
  </tr>
</table>

More desktop, mobile and individual city captures are in the [screenshot gallery](docs/screenshots/README.md).

## What you can explore

- **Today:** illustrated conditions, feels-like temperature, wind, humidity and a seven-day outlook.
- **Forecast:** temperature and precipitation charts, hour selection, daily ranges, sunrise and sunset. Rainfall in millimetres and precipitation chance in percent have separate meanings and labels.
- **Places:** a saved forecast for each city, with switching that cancels the previous request.
- **Studio:** six city landscapes, each with six weather studies in daylight or at night, temperature units, reduced motion and local frame measurements. Scene studies change the artwork independently of forecast data.

Forecasts are real public Open-Meteo responses. Bundled records carry their original retrieval times and a **Recorded forecast** label. They are model forecasts, not station observations. Refreshing validates a response before saving it; a failed request leaves the previous forecast and its timestamp intact.

## Under the surface

| Part | Responsibility |
| --- | --- |
| `app` | Compose screens, lifecycle-aware motion, ViewModel state and Android window frame measurements |
| `core:weather` | Kotlin models, strict forecast decoding, local time conversion and bounded timing summaries |
| `core:data` | OkHttp requests, Room persistence, DataStore preferences and refresh coordination |
| `demo` | Offline browser companion with IndexedDB, keyboard controls and a separate browser timing report |

The dependency graph is assembled explicitly. Coroutines keep network and database work away from the main thread. Per-city locks protect cache updates, request cancellation stops obsolete work, and a generation token keeps a late response from changing the selected city.

The stack is pinned: Kotlin 2.3.21, Compose BOM 2026.03.01, Room 2.8.4, DataStore 1.2.1, Coroutines 1.10.2, OkHttp 4.12.0, AGP 8.13.2 and Gradle 8.13. The Gradle wrapper includes its distribution checksum. The [architecture notes](docs/architecture.md) explain the choices and failure cases.

## What has been checked

The verification includes **43 Kotlin tests**, **17 JavaScript data tests**, **35 browser checks**, **6 motion checks** and **15 city checks**. These include 16 automated accessibility scans across four screen widths. The Kotlin journeys open the actual Compose activity with Robolectric and exercise navigation, persistence, refresh failures and scene controls. Kotlin renderer tests compare the production drawing layers at controlled phases and daylight levels, including all six cities. Both debug and optimized preview APKs are built. Reports and their scope are in [verification](docs/verification.md).

The app measures Android window frames on a running device and can export its report. Physical-device smoothness, battery use and manual TalkBack testing are still to be measured. Browser timing results describe the tested browser only. See [performance](docs/performance.md).

## Data and confidentiality

Weather data is supplied by [Open-Meteo](https://open-meteo.com/) under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Source URLs, receive times and SHA-256 hashes are retained in [data/provenance.json](data/provenance.json). The public API's non-commercial service terms are separate from the data licence; commercial use requires an appropriate service arrangement. Details are in [data sources](docs/data-sources.md).

SkyCanvas is an independent implementation. It contains no employer source code, private endpoints, credentials, customer records or internal design assets. The illustrations were created for this project, and the weather records come from the public source above. See [confidentiality](docs/confidentiality.md) for the project boundary.

Code and original artwork: [MIT](LICENSE). The bundled Manrope font retains its [SIL Open Font License](docs/licenses/Manrope-OFL.txt). Third-party notices are collected in [NOTICE](NOTICE).
