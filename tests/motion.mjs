import fs from "node:fs/promises";
import path from "node:path";
import http from "node:http";
import assert from "node:assert/strict";
import { chromium } from "playwright";

const root = path.resolve(import.meta.dirname, "..");
const html = await fs.readFile(path.join(root, "index.html"));
const server = http.createServer((req, res) => {
  res.writeHead(200, { "Content-Type": "text/html" });
  res.end(html);
});
await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
const browser = await chromium.launch({ args: ["--no-sandbox"] });
const checks = [];
const pass = (name) => {
  checks.push(name);
  console.log("✓", name);
};
const url = `http://127.0.0.1:${server.address().port}`;
const openStudio = async (page) => {
  await page.goto(url);
  await page.waitForFunction(
    () => !document.getElementById("refresh").disabled,
  );
  await page
    .getByRole("navigation")
    .getByRole("button", { name: "Studio", exact: true })
    .click();
  await page.evaluate(() => document.fonts.ready);
};
const style = (page, selector, property) =>
  page
    .locator(selector)
    .first()
    .evaluate((el, property) => getComputedStyle(el)[property], property);
try {
  const context = await browser.newContext({
    viewport: { width: 1200, height: 900 },
  });
  const page = await context.newPage();
  await openStudio(page);
  const svg = page.locator(".studio-scene .landscape");
  assert.equal(await svg.getAttribute("data-scene"), "clear");
  const before = await style(page, ".studio-scene .water-ripple", "transform");
  const haloBefore = await style(page, ".studio-scene .sun-glow", "opacity");
  await page.waitForTimeout(900);
  assert.notEqual(
    await style(page, ".studio-scene .water-ripple", "transform"),
    before,
  );
  assert.notEqual(
    await style(page, ".studio-scene .sun-glow", "opacity"),
    haloBefore,
  );
  pass(
    "clear daylight animates water and sunlight without changing the forecast",
  );
  const identity = await svg.elementHandle();
  const dayColor = await style(page, ".studio-scene .sky-top", "stopColor");
  await page.getByRole("button", { name: "Night", exact: true }).click();
  assert.ok(
    await identity.evaluate(
      (el) => el === document.querySelector(".studio-scene .landscape"),
    ),
  );
  await page.waitForTimeout(350);
  const middleColor = await style(page, ".studio-scene .sky-top", "stopColor");
  const middleMoon = Number(
    await style(page, ".studio-scene .moon-layer", "opacity"),
  );
  assert.notEqual(middleColor, dayColor);
  assert.ok(middleMoon > 0 && middleMoon < 1);
  await page.waitForTimeout(1500);
  const nightColor = await style(page, ".studio-scene .sky-top", "stopColor");
  assert.notEqual(nightColor, middleColor);
  assert.notEqual(nightColor, dayColor);
  pass(
    "day and night keep the SVG mounted and pass through an intermediate palette",
  );
  const starBefore = await style(
    page,
    ".studio-scene .star-twinkle",
    "opacity",
  );
  await page.waitForTimeout(900);
  assert.notEqual(
    await style(page, ".studio-scene .star-twinkle", "opacity"),
    starBefore,
  );
  assert.equal(
    await svg.getAttribute("aria-label"),
    "Illustrated Isfahan clear landscape, night. Bridges of Isfahan. Decorative scene.",
  );
  pass("night stars twinkle while the scene retains a descriptive label");
  await page.getByRole("checkbox", { name: "Gentle motion" }).uncheck();
  const still = page.locator(".studio-scene");
  const stillBefore = await still.screenshot({ animations: "allow" });
  await page.waitForTimeout(900);
  assert.deepEqual(
    await still.screenshot({ animations: "allow" }),
    stillBefore,
  );
  assert.equal(
    await style(page, ".studio-scene .sun-layer", "transitionDuration"),
    "0s",
  );
  await page.getByRole("button", { name: "Day", exact: true }).click();
  assert.equal(await style(page, ".studio-scene .moon-layer", "opacity"), "0");
  pass(
    "Gentle motion off freezes the complete scene and makes daylight changes immediate",
  );
  await page
    .getByRole("navigation")
    .getByRole("button", { name: "Today", exact: true })
    .click();
  assert.equal(await page.locator(".hero-condition").innerText(), "Overcast");
  assert.equal(await page.locator(".hero .temperature").innerText(), "28°C");
  pass("scene controls leave recorded weather values intact");
  await context.close();
  const reduced = await browser.newContext({ reducedMotion: "reduce" });
  const r = await reduced.newPage();
  await openStudio(r);
  const animations = await r
    .locator(".studio-scene .landscape")
    .evaluate((el) => el.getAnimations({ subtree: true }).length);
  assert.equal(animations, 0);
  await r.getByRole("button", { name: "Night", exact: true }).click();
  assert.equal(await style(r, ".studio-scene .moon-layer", "opacity"), "1");
  pass(
    "system reduced motion disables every decorative layer and the day/night transition",
  );
  await reduced.close();
  await fs.writeFile(
    path.join(root, "docs/verification/motion-tests.json"),
    JSON.stringify(
      {
        version: "2.0.0",
        executedAt: new Date().toISOString(),
        browser: await browser.version(),
        checkCount: checks.length,
        checks,
        scope:
          "Actual visible browser animation; native checks are recorded separately.",
      },
      null,
      2,
    ) + "\n",
  );
} finally {
  await browser.close();
  await new Promise((resolve) => server.close(resolve));
}
