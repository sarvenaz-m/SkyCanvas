/** Original landscape geometry. CSS keeps its sky, light and water in separate motion layers. */
export function sceneSvg(scene = "clear", day = true, id = "scene") {
  const cloud = (x, y, scale) =>
    `<g transform="translate(${x} ${y}) scale(${scale})"><rect x="-70" width="190" height="36" rx="18"/><circle r="30"/><circle cx="60" cy="2" r="24"/></g>`;
  const stars = Array.from(
    { length: 31 },
    (_, i) =>
      `<circle class="star-twinkle" style="animation-duration:${6 + (i % 4)}s;animation-delay:-${(i * 0.37).toFixed(2)}s" cx="${(((i * 47) % 97) + 1) * 10}" cy="${(((i * 29) % 33) + 3) * 6.5}" r="${i % 4 ? 1.5 : 2.7}"/>`,
  ).join("");
  const arches = Array.from({ length: 13 }, (_, i) => {
    const x = 80 + i * 65;
    return `<path class="arch color-layer" d="M${x} 572V533Q${x + 20} 493 ${x + 40} 533V572Z"/>${i % 3 === 0 ? `<path class="bridge-light" d="M${x + 20} 540V554" stroke="#f2c47a" stroke-width="2"/>` : ""}`;
  }).join("");
  const ripples = Array.from(
    { length: 8 },
    (_, i) =>
      `<path class="water-ripple" style="animation-delay:-${i * 0.8}s;animation-duration:${6 + (i % 3)}s" d="M${60 + i * 120} ${592 + (i % 3) * 17}h90"/>`,
  ).join("");
  const reflection = Array.from({ length: 9 }, (_, i) => {
    const width = 35 + i * 9;
    return `<path class="water-shimmer" style="animation-delay:-${i * 0.5}s;opacity:${0.18 - i * 0.012}" d="M${775 - width / 2} ${582 + i * 7}h${width}"/>`;
  }).join("");
  const trees = [35, 960]
    .map(
      (x) =>
        `<path class="tree color-layer" d="M${x} 377C${x - 50} 488 ${x - 40} 559 ${x} 566C${x + 40} 559 ${x + 40} 468 ${x} 377Z"/><path class="stone-stroke color-layer" d="M${x} 533V611" stroke-width="8"/>`,
    )
    .join("");
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
  return `<svg class="landscape ${day ? "is-day" : "is-night"}" data-scene="${scene}" viewBox="0 0 1000 650" role="img" aria-label="Illustrated ${scene} landscape, ${day ? "day" : "night"}. Decorative scene." preserveAspectRatio="xMidYMax slice">
<defs><linearGradient id="${id}" x2="0" y2="1"><stop class="sky-top color-layer"/><stop class="sky-bottom color-layer" offset="1"/></linearGradient></defs>
<rect width="1000" height="650" fill="url(#${id})"/>
<g class="sun-layer"><g class="sun-float"><circle class="sun-glow" cx="770" cy="163" r="130" fill="#ffe3a3" opacity=".3"/><circle cx="770" cy="163" r="75" fill="#ffdb8e"/></g></g>
<g class="night-stars" fill="#fff">${stars}</g>
<g class="moon-layer"><g class="moon-float"><path d="M780 111A52 52 0 1 0 827 185A49 49 0 0 1 780 111Z" fill="#f9e9c4"/></g></g>
<path class="mountain color-layer" d="M0 410L120 338L210 377L390 260L550 370L700 286L890 384L1000 325V650H0Z"/>
<path class="land color-layer" d="M0 481L190 423L350 475L530 397L780 462L1000 403V650H0Z"/>
${scene === "snow" ? '<path d="M280 332L390 260L490 338L400 319L360 325Z" fill="#eaf2f8"/>' : ""}
<rect class="stone color-layer" x="300" y="423" width="160" height="98"/><path class="dome color-layer" d="M280 423C300 358 360 358 380 325C400 358 460 358 480 423Z"/><path class="stone-stroke color-layer" d="M380 325V305" stroke-width="4"/>
${[265, 490].map((x) => `<rect class="stone color-layer" x="${x}" y="345" width="18" height="176"/><circle class="dome color-layer" cx="${x + 9}" cy="348" r="15"/>`).join("")}
<path class="river color-layer" d="M0 579C350 507 640 559 1000 520V650H0Z"/>
<path class="stone color-layer" d="M60 500H940V569H60Z"/><path class="bridge-cap color-layer" d="M40 491H960V503H40Z"/>${arches}
<g fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round">${ripples}</g>
<g class="reflection color-layer" fill="none" stroke-width="2.4" stroke-linecap="round">${reflection}</g>${trees}
<g class="cloud-near color-layer"><g class="cloud-drift">${cloud(220, 195, 1)}</g></g>
<g class="cloud-far color-layer" opacity=".8"><g class="cloud-drift cloud-drift-far">${cloud(690, 280, 0.7)}</g></g>
${scene === "cloud" || scene === "storm" ? `<g class="cloud-near color-layer"><g class="cloud-drift cloud-drift-high">${cloud(660, 104, 1.2)}</g></g>` : ""}${rain}${snow}${fog}
${scene === "storm" ? '<path d="M600 182L560 260L610 254L580 338L680 228L620 234Z" fill="#ffde95"/>' : ""}</svg>`;
}

/** Keep the SVG mounted so its palette can transition without restarting its motion. */
export function setSceneDaylight(svg, day) {
  svg.classList.toggle("is-day", day);
  svg.classList.toggle("is-night", !day);
  svg.setAttribute(
    "aria-label",
    `Illustrated ${svg.dataset.scene} landscape, ${day ? "day" : "night"}. Decorative scene.`,
  );
}
