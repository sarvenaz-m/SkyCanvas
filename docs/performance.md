# Frame measurements

Animation needs to be measured where it runs. SkyCanvas therefore keeps Android window measurements and browser cadence measurements in separate reports. Neither is presented as a universal frame-rate guarantee.

## Android

`WindowFrameSampler` listens to the activity's `Window.FrameMetrics` on a dedicated handler thread. It copies each duration during the callback because the platform reuses the metrics object. A bounded window retains the latest 600 valid measurements; first-draw frames are excluded. The Studio panel publishes a summary after every 30 callbacks and when sampling stops.

The exported JSON contains the device model, Android API, capture time, sample count, nearest-rank p50 and p95 of `TOTAL_DURATION`, and the number of samples over their reported budget. On API 31 and above the budget comes from `DEADLINE` when valid; earlier versions fall back to the display's reported refresh period. The callback's missed-report count is recorded separately. **Missed metrics callbacks are not a count of dropped display frames.**

Reset clears the sample window. Export uses Android's document picker and writes the JSON locally. No timing file is bundled as if it came from a phone: physical-device measurements have not been captured for this release.

For a repeatable device session, record model, OS, display refresh rate, power mode, thermal conditions and app build. Let the screen settle, reset the window, then use the same scene and interaction sequence for each run. Compare daylight and night scenes, precipitation motion, chart scrubbing and reduced motion. Retain the exported report with that context. Battery use, thermal behavior and sustained scrolling need separate device runs.

## Browser

The companion measures 240 visible `requestAnimationFrame` intervals after ten warm-up intervals. It stops if the document becomes hidden. Its report includes viewport, scene, motion preferences, capture time, nearest-rank p50/p95, mean callback cadence and intervals longer than 1.5 times the median. The threshold is descriptive; it is not an Android jank definition.

The actual captured report is in [verification/browser-frame-pacing.json](verification/browser-frame-pacing.json). It comes from the tested headless Chromium session and does not measure a physical screen, touch latency, GPU completion or Android performance. The panel displays no measurements until a run completes.

## Rendering choices

Compose separates static landscape geometry from animated celestial and water/weather layers, and reads animation state in drawing. A shared 24-second phase drives gentle native motion; the scene palette changes only during the short daylight transition. Motion is paused outside the resumed lifecycle. The companion pauses decorative animation when hidden. Both have a user motion switch, and respect their platform's reduced-motion setting. Scene artwork uses code-native shapes rather than remote images, so a missing image download cannot stall the first view.

Platform reference: [Android FrameMetrics](https://developer.android.com/reference/android/view/FrameMetrics).
