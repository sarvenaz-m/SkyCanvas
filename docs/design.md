# Design notes

The visual starting point is Isfahan, seen through a small set of simple shapes. A bridge, dome, mountains and cypress trees create a recognizable setting without tracing an employer's artwork or relying on a stock photograph. A muted blue, warm paper background and sand-colored accents leave space for the weather itself.

The hierarchy answers three questions in order: what the model says now, when that information was received, and how the next hours may change. Recorded and saved states have text labels as well as color. Temperature, rainfall and probability keep their units beside the value. Null data uses a dash or an unavailable label.

Day and night palettes are available for clear, cloudy, foggy, rainy, snowy and stormy scenes. Daylight has slow cloud drift, a gentle sun halo and moving water highlights. Night adds staggered star brightness and a reflected moonlight path. Studio blends between the palettes instead of cutting abruptly. A night illustration follows the forecast flag; Studio provides a separate way to inspect all of them. The landscape is a schematic view, not a live camera or a map.

Manrope is bundled locally. Text uses dark navy on pale surfaces and light text on night scenes. Android controls use Material components, and the companion provides visible focus rings, semantic navigation and a keyboard-operable hour slider. Automated browser contrast and accessibility checks cover four widths, including 320 CSS pixels. Manual Android screen-reader review and large-font review remain part of device validation.

Screenshots under `docs/screenshots` are captures of the running interfaces. The Android views were rendered by the actual Compose activity in Robolectric; the companion views were captured in Chromium. Showcase images arrange those captures and do not represent a physical-device photograph.
