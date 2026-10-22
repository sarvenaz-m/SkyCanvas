# Architecture

SkyCanvas has three Android modules. A small, explicit dependency graph keeps the data path easy to inspect and lets tests replace a single boundary without replacing the screen being tested.

## State and rendering

`SkyViewModel` exposes an immutable `SkyState` through `StateFlow`. Compose collects it with the activity lifecycle. Actions describe user intent: select a place, refresh, choose an hour, change units or explore a scene. The repository owns weather records; preferences own the selected place, units and motion setting.

Selecting a city cancels its predecessor's load and refresh jobs. A monotonically increasing generation token also checks completion, so a delayed result cannot replace the active city's screen. Saved records for the other cities remain available in the Places view.

The landscape uses separate Canvas layers for the gradient sky, celestial motion, bridge and mountains, and water and weather. Continuous animation reads its phase inside drawing; static geometry does not need to be rebuilt for each animation frame. A 1.6-second daylight interpolation blends the palette when Studio changes from day to night. Decorative motion runs only while the lifecycle is resumed and motion is enabled. Android's zero animator-duration scale also stops it. No remote image requests are needed.

## From response to saved forecast

1. Open the city's saved Room row and decode it against the expected place.
2. If the row is missing or corrupt, use the corresponding recorded public response. Keep its original receive time and recorded origin.
3. On Android, refresh a record that is at least 30 minutes old or is recorded. The browser companion waits for **Get latest** so its initial view works entirely offline.
4. Accept JSON only after the HTTP status, size, units, location, time grid and numeric bounds pass validation.
5. Save payload, receive time and origin together in a Room upsert. Publish the result after persistence succeeds.

Open and refresh use the same per-city mutex. An initial seed cannot overwrite a refresh that has just completed. A storage failure is reported as a failure to save, rather than silently promising a forecast will survive the next launch. Room schema version 1 is exported under `core/data/schemas`; no destructive migration fallback is enabled.

The cache has no arbitrary eviction in version 2: there are six fixed places and one response per place. Raw payload size is capped at 1 MiB. Display units are derived from the original Celsius values; changing to Fahrenheit never changes the stored source.

## Network behavior

`ForecastApi` is the narrow suspend-function boundary. `OpenMeteoApi` uses cancellable OkHttp calls with an 8-second connection timeout, 10-second read timeout and 15-second total call timeout. Automatic connection retries are disabled. A repository attempt throttle prevents immediate repeat requests. An HTTP 429 starts a shared cooldown using bounded `Retry-After` seconds or an HTTP date. Other failures wait for another user action or a later app visit; there is no polling loop.

Cancellation propagates through the request. HTTP errors, unavailable storage, malformed JSON and missing fields have separate outcomes. A failed refresh never changes the last successful receive time. User-facing messages explain what remains available.

## Forecast contract

`core:weather` is a plain Kotlin module. It validates the place grid, IANA time zone, declared units, aligned arrays, increasing hourly UTC timestamps and consecutive local forecast dates. Null remains missing. Non-finite values, invalid probabilities and reversed daily temperature ranges are rejected. Unknown weather codes receive a neutral unavailable label.

API epoch seconds are instants, and labels are formatted in the place's `Asia/Tehran` zone. Selecting a day uses local calendar dates rather than the host computer's time zone. Hourly rainfall is the amount for the preceding hour. The daily condition describes the most severe code in the day, rather than a promise of continuous conditions.

## Browser companion

The companion has its own strict decoder, covered by JavaScript tests. It stores response records in IndexedDB and preferences in localStorage. A write is accepted only when the IndexedDB transaction completes. Storage denial falls back to an in-memory recorded forecast with a visible message. The HTML embeds all fonts, scene code and forecast records; it has no CDN dependency.

The Studio day/night switch keeps the same SVG mounted, allowing color and opacity transitions while ongoing movement continues. All decorative layers stop when motion is disabled; reduced motion also removes the daylight transition.

Charts resize to their actual container width. The hour control works with touch, pointer and keyboard, and keeps keyboard focus while its selection changes. Reduced-motion preferences are respected. Scene studies and browser frame reports are labelled separately from forecast data and Android measurements.

## Local data and requests

The app requests Internet permission only. It does not request location, contacts or account access. There is no analytics SDK, account system or application backend. Refreshing sends the selected city's public coordinates to Open-Meteo; the API operator also receives ordinary network information such as the requesting IP address. Frame reports remain local until the user exports a file. Android backup is disabled for the app's saved state.
