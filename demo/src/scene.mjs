import cityArtwork from "../../data/city-scenes.json" with { type: "json" };

/** Geometry is shared with the native Kotlin renderer; each city has its own landmarks. */
export function cityScene(placeId = "isfahan") {
  return cityArtwork.cities[placeId] || cityArtwork.cities.isfahan;
}
function cityGroups(city) {
  const palette = { ...cityArtwork.palette, ...city.palette };
  return city.groups
    .map((group) => {
      const shapes = group.shapes
        .map((shape) => {
          const [day, night] = palette[shape.tone];
          return `<path class="city-art color-layer${shape.stroke ? " art-stroke" : ""}" style="--art-day:${day};--art-night:${night}" d="${shape.d}"${shape.stroke ? ` stroke-width="${shape.stroke}" stroke-linecap="round" stroke-linejoin="round"` : ""}${shape.opacity != null ? ` opacity="${shape.opacity}"` : ""}/>`;
        })
        .join("");
      const [x, y] = group.pivot || [500, 500];
      return `<g data-landmark="${group.id}"${group.nightOnly ? ' class="night-detail"' : ""}><g${group.motion ? ` class="city-motion city-${group.motion}" style="transform-origin:${x}px ${y}px"` : ""}>${shapes}</g></g>`;
    })
    .join("");
}
export function sceneSvg(
  scene = "clear",
  day = true,
  id = "scene",
  placeId = "isfahan",
) {
  const city = cityScene(placeId);
  const cloud = (x, y, scale) =>
    `<g transform="translate(${x} ${y}) scale(${scale})"><rect x="-70" width="190" height="36" rx="18"/><circle r="30"/><circle cx="60" cy="2" r="24"/></g>`;
  const stars = Array.from(
    { length: 31 },
    (_, i) =>
      `<circle class="star-twinkle" style="animation-duration:${6 + (i % 4)}s;animation-delay:-${(i * 0.37).toFixed(2)}s" cx="${(((i * 47) % 97) + 1) * 10}" cy="${(((i * 29) % 33) + 3) * 6.5}" r="${i % 4 ? 1.5 : 2.7}"/>`,
  ).join("");
  const ripples = Array.from(
    { length: 8 },
    (_, i) =>
      `<path class="water-ripple" style="animation-delay:-${i * 0.8}s;animation-duration:${6 + (i % 3)}s" d="M${60 + i * 120} ${city.waterY + (i % 3) * 17}h90"/>`,
  ).join("");
  const reflection = Array.from({ length: 9 }, (_, i) => {
    const width = 35 + i * 9;
    return `<path class="water-shimmer" style="animation-delay:-${i * 0.5}s;opacity:${0.18 - i * 0.012}" d="M${775 - width / 2} ${city.waterY - 10 + i * 7}h${width}"/>`;
  }).join("");
  const rain =
    scene === "rain" || scene === "storm"
      ? `<g class="rain-fall rain-color color-layer" stroke-opacity=".52" stroke-width="2">${Array.from({ length: 26 }, (_, i) => `<path d="M${((i * 17) % 97) * 10} ${180 + ((i * 37) % 380)}l-10 26"/>`).join("")}</g>`
      : "";
  const snow =
    scene === "snow"
      ? `<g class="snow-fall" fill="#fff" opacity=".85">${Array.from({ length: 31 }, (_, i) => `<circle cx="${((i * 23) % 97) * 10}" cy="${65 + ((i * 61) % 500)}" r="${i % 2 ? 3 : 4}"/>`).join("")}</g>`
      : "";
  const fog =
    scene === "fog"
      ? `<g class="fog-drift" fill="#fff" opacity=".25">${Array.from({ length: 5 }, (_, i) => `<rect x="${(i % 2) * 150}" y="${299 + i * 49}" width="850" height="11" rx="6"/>`).join("")}</g>`
      : "";
  return `<svg class="landscape ${day ? "is-day" : "is-night"}" data-scene="${scene}" data-city="${placeId}" viewBox="0 0 1000 650" role="img" aria-label="${sceneLabel(city, scene, day)}" preserveAspectRatio="xMidYMax slice">
<defs><clipPath id="${id}-water"><path d="${city.waterMask}"/></clipPath><linearGradient id="${id}" x2="0" y2="1"><stop class="sky-top color-layer"/><stop class="sky-bottom color-layer" offset="1"/></linearGradient></defs>
<rect width="1000" height="650" fill="url(#${id})"/>
<g class="sun-layer"><g class="sun-float"><circle class="sun-glow" cx="770" cy="163" r="130" fill="#ffe3a3" opacity=".3"/><circle cx="770" cy="163" r="75" fill="#ffdb8e"/></g></g>
<g class="night-stars" fill="#fff">${stars}</g>
<g class="moon-layer"><g class="moon-float"><path d="M780 111A52 52 0 1 0 827 185A49 49 0 0 1 780 111Z" fill="#f9e9c4"/></g></g>
${cityGroups(city)}
<g clip-path="url(#${id}-water)"><g fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round">${ripples}</g>
<g class="reflection color-layer" fill="none" stroke-width="2.4" stroke-linecap="round">${reflection}</g></g>
<g class="cloud-near color-layer"><g class="cloud-drift">${cloud(220, 195, 1)}</g></g>
<g class="cloud-far color-layer" opacity=".8"><g class="cloud-drift cloud-drift-far">${cloud(690, 280, 0.7)}</g></g>
${scene === "cloud" || scene === "storm" ? `<g class="cloud-near color-layer"><g class="cloud-drift cloud-drift-high">${cloud(660, 104, 1.2)}</g></g>` : ""}${rain}${snow}${fog}
${scene === "storm" ? '<path d="M600 182L560 260L610 254L580 338L680 228L620 234Z" fill="#ffde95"/>' : ""}</svg>`;
}
function sceneLabel(city, scene, day) {
  return `Illustrated ${city.name} ${scene} landscape, ${day ? "day" : "night"}. ${city.landmark}. Decorative scene.`;
}
/** Keep the SVG mounted so palette transitions never restart its motion. */
export function setSceneDaylight(svg, day) {
  svg.classList.toggle("is-day", day);
  svg.classList.toggle("is-night", !day);
  svg.setAttribute(
    "aria-label",
    sceneLabel(cityScene(svg.dataset.city), svg.dataset.scene, day),
  );
}
