# Design notes

The visual starting point is Isfahan, extended in version 2 to six individual city landscapes. Isfahan keeps its bridge and turquoise dome. Shiraz has a Hafezieh-inspired pavilion and cypress garden; Tehran has the Azadi arch and Alborz; Tabriz has the El Goli pavilion and lake; Mashhad has a golden dome and courtyard; Bandar Abbas has a lenj and palms by the Persian Gulf. These are original, schematic illustrations, with no stock photographs or employer assets. A muted blue, warm paper background and sand-colored accents leave space for the weather itself.

The hierarchy answers three questions in order: what the model says now, when that information was received, and how the next hours may change. Recorded and saved states have text labels as well as color. Temperature, rainfall and probability keep their units beside the value. Null data uses a dash or an unavailable label.

Day and night palettes are available for clear, cloudy, foggy, rainy, snowy and stormy scenes. Daylight has slow cloud drift, a gentle sun halo and moving water highlights. Night adds staggered star brightness and a reflected moonlight path. Studio blends between the palettes instead of cutting abruptly. A night illustration follows the forecast flag; Studio provides a separate way to inspect all of them. The landscape is a schematic view, not a live camera or a map.

Manrope is bundled locally. Text uses dark navy on pale surfaces and light text on night scenes. Android controls use Material components, and the companion provides visible focus rings, semantic navigation and a keyboard-operable hour slider. Automated browser contrast and accessibility checks cover four widths, including 320 CSS pixels. Manual Android screen-reader review and large-font review remain part of device validation.

Screenshots under `docs/screenshots` are captures of the running interfaces. The Android views were rendered by the actual Compose activity in Robolectric; the companion views were captured in Chromium. Showcase images arrange those captures and do not represent a physical-device photograph.

## One artwork source

`data/city-scenes.json` contains the paths, city descriptions, palette pairs and motion groups. The identical file is bundled with Android assets. Kotlin parses paths once and draws them in Compose; the browser renders those paths as SVG. Both preserve the landmarks' aspect ratio and share the same daylight/night colours. Static geometry and moving groups are drawn in separate native layers. City studies have their own selection and leave the active weather record untouched.

The README's daylight and night boards use actual scene crops from the rebuilt companion. `scripts/capture-gallery.mjs` reproduces the individual city captures, gallery boards and browser screenshots.
