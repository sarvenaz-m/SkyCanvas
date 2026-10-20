# Verification record

Version 1.0.0 was checked on 19 October 2026 and the animation update was checked again on 20 October 2026. The reports describe the work performed on this source package; they do not stand in for physical-device validation.

| Check | Result | Evidence |
| --- | --- | --- |
| Kotlin domain rules | 15 passed | `verification/native-tests.json` |
| Repository behavior | 9 passed | Same report |
| HTTP boundary | 4 passed | Same report |
| Room persistence and reopen | 1 passed | Same report |
| Actual Compose activity journeys | 8 passed | Same report; captured Android views |
| Native drawing layers | 4 passed | Controlled-phase bitmaps from the production Canvas renderer |
| JavaScript data rules | 17 passed | `verification/javascript-tests.tap` |
| Browser journeys and layout | 35 checks passed | `verification/browser-tests.json` |
| Day/night animation behavior | 6 checks passed | `verification/motion-tests.json` |
| Automated browser accessibility | No violations in 16 tested states | Four tabs at 1440, 768, 390 and 320 CSS pixels |
| Android lint | 0 errors, 0 warnings | `verification/android-lint.json` |
| APK builds | Debug and optimized preview succeeded | `verification/apk-inspection.json` describes the preview |
| Forecast provenance | Four hashes and matching Android assets verified | `data/provenance.json`; `scripts/record_forecasts.py` |

The pinned dependency set is retained for this release. The lint result below is taken from the final animation build rather than copied from the earlier release preparation.

## What the tests cover

Domain tests check the real recorded responses, Tehran's half-hour offset, local-date selection, missing values, unit conversion, weather-code groups and nearest-rank timing summaries. Invalid units, grids, columns, timestamps, probabilities and daily ranges are rejected.

Repository and HTTP tests cover successful persistence, offline and malformed responses, storage failure, corrupt-cache recovery, refresh serialization, cancellation, HTTP status handling, payload limits and rate-limit cooldown. The Room test closes and reopens an actual database.

Android journeys use Robolectric API 35 with native graphics to open `MainActivity`. They exercise all four tabs, city switching, a failed refresh retaining its previous record, a successful refresh, Fahrenheit, scene studies that leave forecast data unchanged and an empty timing panel that does not invent measurements. The additional motion-control journey checks that its setting survives navigation and leaves forecast values intact. Their Room database is real; network and preference boundaries are controlled test doubles. The screenshots are actual Compose output in that environment.

Four renderer tests draw the same production sky, celestial, landscape and atmosphere functions into native bitmaps. They compare clear daylight and night at different phase values, deterministic fixed frames and an intermediate daylight palette. These isolate drawing from Robolectric's display-capture timing; they do not measure display cadence or physical-device animation.

Browser checks exercise IndexedDB persistence, restored preferences, successful refresh, offline refresh, malformed JSON, HTTP 429, a delayed obsolete request, inaccessible storage, keyboard hour selection, reduced motion and a standalone offline file. Network error cases use intercepted test responses; product screenshots use the bundled public records. Accessibility scans use axe's WCAG 2.1 AA rule set. Passing an automated scan does not replace screen-reader testing.

Additional motion checks compare moving daylight layers, a night star, the intermediate day/night palette and still screenshots with motion disabled. They confirm the SVG stays mounted and weather values remain intact. System reduced motion removes every decorative animation and transition.

The browser timing run records 240 actual animation callbacks. The captured report is included without converting it into an Android performance claim.

## APK inspection

The preview has package ID `dev.sarvenaz.skycanvas`, version code 1 and version name 1.0.0. Its compiled manifest declares minimum API 26 and target API 36. It is optimized and is not marked debuggable, but it uses a development signing certificate. The release build type leaves signing to the owner.

The APK signature verifies. ZIP alignment checks pass, including 16 KiB native-library alignment, and all bundled ELF load segments have at least 16 KiB alignment. Internet is the only Android platform permission requested. AndroidX also contributes an app-private signature permission for dynamic receivers.

## Repeating the checks

The Gradle wrapper uses Gradle 8.13 with a pinned distribution checksum. The Android build expects JDK 17 and Android API 36. The companion checks use Node 22 and the lockfile's Playwright and axe versions.

```sh
./gradlew :core:weather:test :core:data:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assemblePreview
python3 scripts/record_forecasts.py --verify
npm run build:demo
npm test
npm run test:browser
npm run test:motion
```

The committed verification workflow repeats these gates. It does not publish a website or distribute an application. Rebuilding a development-signed APK on another machine uses that machine's development key, so its checksum is not expected to match the delivered APK.

## Device work still open

Physical-phone installation and launch, manual TalkBack use, large-font and display scaling, landscape/tablet layouts, network changes during background/resume, extended animation and scrolling, battery use and thermal behavior remain device-validation work. Native frame export is implemented, but no physical-device timing report is included. The app's forecast should be treated as model information, with its receive time visible.
