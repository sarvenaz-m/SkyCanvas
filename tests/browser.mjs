import fs from "node:fs/promises";
import path from "node:path";
import http from "node:http";
import assert from "node:assert/strict";
import { chromium } from "playwright";
import AxeBuilder from "@axe-core/playwright";
const root = path.resolve(import.meta.dirname, ".."),
  html = await fs.readFile(path.join(root, "index.html"));
const server = http.createServer((req, res) => {
  res.writeHead(200, { "Content-Type": "text/html" });
  res.end(html);
});
await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
const url = `http://127.0.0.1:${server.address().port}`,
  browser = await chromium.launch({ args: ["--no-sandbox"] });
const context = await browser.newContext({
    viewport: { width: 1440, height: 1100 },
  }),
  page = await context.newPage();
const errors = [],
  requests = [],
  checks = [],
  audits = [];
page.on("pageerror", (error) => errors.push(error.message));
page.on("request", (request) => requests.push(request.url()));
const pass = (name) => {
  checks.push(name);
  console.log("✓", name);
};
const nav = async (name) => {
  await page
    .getByRole("navigation")
    .getByRole("button", { name, exact: true })
    .click();
  await page.getByRole("heading", { level: 1 }).waitFor();
};
const ready = async () => {
  await page.getByRole("button", { name: "Get latest", exact: true }).waitFor();
  await page.waitForFunction(
    () => !document.getElementById("refresh").disabled,
  );
};
const cache = () =>
  page.evaluate(
    () =>
      new Promise((resolve, reject) => {
        const request = indexedDB.open("skycanvas-weather", 1);
        request.onsuccess = () => {
          const db = request.result,
            tx = db.transaction("forecasts"),
            q = tx.objectStore("forecasts").get("isfahan");
          q.onsuccess = () => {
            resolve(q.result);
            db.close();
          };
          q.onerror = () => reject(q.error);
        };
        request.onerror = () => reject(request.error);
      }),
  );
const record = JSON.parse(
  await fs.readFile(path.join(root, "data/isfahan.json"), "utf8"),
);
let apiMode = "success",
  apiCalls = 0,
  release = null;
await context.route("https://api.open-meteo.com/**", async (route) => {
  apiCalls++;
  const u = new URL(route.request().url());
  assert.equal(u.searchParams.get("timeformat"), "unixtime");
  assert.equal(u.searchParams.get("timezone"), "Asia/Tehran");
  if (apiMode === "offline") {
    await route.abort("internetdisconnected");
    return;
  }
  if (apiMode === "rate") {
    await route.fulfill({
      status: 429,
      headers: { "Content-Type": "application/json", "Retry-After": "60" },
      body: "{}",
    });
    return;
  }
  if (apiMode === "malformed") {
    await route.fulfill({
      headers: { "Content-Type": "application/json" },
      body: '{"error":true}',
    });
    return;
  }
  if (apiMode === "late") await new Promise((resolve) => (release = resolve));
  const payload = structuredClone(record);
  payload.current.temperature_2m = 30.2;
  try {
    await route.fulfill({
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
  } catch {}
});
try {
  await page.goto(url);
  await ready();
  await page.evaluate(() => document.fonts.ready);
  await page.getByText("RECORDED FORECAST", { exact: true }).waitFor();
  assert.equal(await page.locator(".hero .temperature").innerText(), "28°C");
  pass("real public Isfahan snapshot opens without a runtime API request");
  const first = await cache();
  assert.equal(first.origin, "RECORDED");
  assert.equal(JSON.parse(first.payload).current.temperature_2m, 27.5);
  pass("IndexedDB stores the actual payload with its original receive time");
  const range = page.getByRole("slider", { name: "Forecast hour" });
  await range.focus();
  await page.keyboard.press("ArrowRight");
  await page.keyboard.press("ArrowRight");
  await page.keyboard.press("ArrowRight");
  assert.equal(await range.inputValue(), "3");
  assert.equal(
    await range.evaluate((el) => el === document.activeElement),
    true,
  );
  pass("hour slider supports repeated keyboard selection without losing focus");
  await page
    .getByRole("button", { name: "Precipitation", exact: true })
    .click();
  await page
    .getByText(
      "Amounts and probabilities refer to the hour ending at the labelled time.",
    )
    .waitFor();
  pass(
    "precipitation amount keeps its mm axis separate from percentage chance",
  );
  await nav("Forecast");
  await page.getByRole("button", { name: "Tue 20", exact: true }).click();
  assert.equal(await page.locator(".hour-table tbody tr").count(), 24);
  pass("daily selection retains 24 hours in the place timezone");
  await nav("Places");
  await page.getByRole("button", { name: "Open Tehran forecast" }).click();
  await page.locator(".hero h2").filter({ hasText: "Tehran" }).waitFor();
  assert.equal(await page.locator(".hero .temperature").innerText(), "30°C");
  pass("place switching opens its own recorded forecast");
  await nav("Studio");
  await page.getByRole("button", { name: "Night", exact: true }).click();
  await page.getByRole("button", { name: "Rain", exact: true }).click();
  assert.equal(
    await page.locator(".studio-scene svg").getAttribute("aria-label"),
    "Illustrated rain landscape, night. Decorative scene.",
  );
  await nav("Today");
  assert.equal(await page.locator(".hero-condition").innerText(), "Clear sky");
  pass("scene studies change artwork independently of weather data");
  await nav("Studio");
  await page.getByRole("checkbox", { name: "Use Fahrenheit" }).check();
  await nav("Today");
  assert.equal(await page.locator(".hero .temperature").innerText(), "85°F");
  await page.reload();
  await ready();
  assert.equal(await page.locator(".hero .temperature").innerText(), "85°F");
  pass(
    "Fahrenheit preference survives reload without altering Celsius source data",
  );
  await nav("Places");
  await page.getByRole("button", { name: "Open Isfahan forecast" }).click();
  await ready();
  await nav("Studio");
  await page.getByRole("checkbox", { name: "Use Fahrenheit" }).uncheck();
  await page.getByRole("checkbox", { name: "Gentle motion" }).uncheck();
  await page.reload();
  await ready();
  await nav("Studio");
  assert.equal(
    await page
      .locator(".cloud-drift")
      .first()
      .evaluate((el) => getComputedStyle(el).animationName),
    "none",
  );
  await page.getByRole("checkbox", { name: "Gentle motion" }).check();
  await nav("Today");
  pass("motion preference is persisted and can stop decorative animation");
  await page.getByRole("button", { name: "Get latest", exact: true }).click();
  await page
    .locator(".notice")
    .getByText("Forecast updated and saved.", { exact: true })
    .waitFor();
  assert.equal(await page.locator(".hero .temperature").innerText(), "30°C");
  const updated = await cache();
  assert.equal(updated.origin, "NETWORK");
  assert.ok(updated.fetchedAt >= first.fetchedAt);
  await page.reload();
  await ready();
  assert.equal(await page.locator(".hero .temperature").innerText(), "30°C");
  pass(
    "successful API refresh commits payload and timestamp and survives reload",
  );
  apiMode = "offline";
  await page.getByRole("button", { name: "Get latest", exact: true }).click();
  await page
    .locator(".notice")
    .getByText("Could not reach the forecast service.", { exact: false })
    .waitFor();
  assert.deepEqual(await cache(), updated);
  assert.equal(await page.locator(".hero .temperature").innerText(), "30°C");
  pass(
    "offline refresh keeps the exact last successful payload and receive time",
  );
  await page.reload();
  await ready();
  apiMode = "malformed";
  await page.getByRole("button", { name: "Get latest", exact: true }).click();
  await page
    .locator(".notice")
    .getByText("The forecast response was unexpected.", { exact: false })
    .waitFor();
  assert.deepEqual(await cache(), updated);
  pass("malformed API responses cannot replace the saved forecast");
  await page.reload();
  await ready();
  apiMode = "rate";
  const beforeCalls = apiCalls;
  await page.getByRole("button", { name: "Get latest", exact: true }).click();
  await page
    .locator(".notice")
    .getByText("The weather service is busy.", { exact: false })
    .waitFor();
  await page.getByRole("button", { name: "Get latest", exact: true }).click();
  await page
    .locator(".notice")
    .getByText("The weather service asked us to wait.", { exact: false })
    .waitFor();
  assert.equal(apiCalls - beforeCalls, 1);
  assert.deepEqual(await cache(), updated);
  pass("Retry-After prevents another request during the server cooldown");
  await page.reload();
  await ready();
  apiMode = "late";
  await page.getByRole("button", { name: "Get latest", exact: true }).click();
  await page.waitForFunction(
    () => document.getElementById("refresh").textContent === "Updating…",
  );
  await nav("Places");
  await page.getByRole("button", { name: "Open Tehran forecast" }).click();
  while (!release) await new Promise((resolve) => setTimeout(resolve, 10));
  release();
  await ready();
  assert.equal(await page.locator(".hero h2").innerText(), "Tehran");
  assert.equal(await page.locator(".hero .temperature").innerText(), "30°C");
  pass("a cancelled late response cannot overwrite the selected place");
  await context.close();
  const visual = await browser.newContext({
      viewport: { width: 1440, height: 1100 },
    }),
    v = await visual.newPage();
  v.on("pageerror", (error) => errors.push(error.message));
  await v.goto(url);
  await v.waitForFunction(() => !document.getElementById("refresh").disabled);
  await v.evaluate(() => document.fonts.ready);
  for (const width of [1440, 768, 390, 320]) {
    await v.setViewportSize({ width, height: width === 1440 ? 1100 : 844 });
    for (const name of ["Today", "Forecast", "Places", "Studio"]) {
      await v
        .getByRole("navigation")
        .getByRole("button", { name, exact: true })
        .click();
      await v.getByRole("heading", { level: 1 }).waitFor();
      assert.ok(
        await v.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth + 1,
        ),
        `Overflow ${width} ${name}`,
      );
      const result = await new AxeBuilder({ page: v })
        .withTags(["wcag2a", "wcag2aa", "wcag21aa"])
        .analyze();
      audits.push({
        name: `${width}px ${name}`,
        violations: result.violations,
      });
      assert.deepEqual(
        result.violations.map((e) => ({
          id: e.id,
          nodes: e.nodes.map((n) => n.target),
        })),
        [],
        `${width}px ${name} accessibility`,
      );
      if (width === 1440 || width === 390)
        await v.screenshot({
          path: path.join(
            root,
            "docs/screenshots",
            `${width === 1440 ? "companion" : "mobile"}-${name.toLowerCase()}.png`,
          ),
          fullPage: width === 1440,
        });
      pass(`${width}px ${name} layout and accessibility`);
    }
  }
  await v.setViewportSize({ width: 1440, height: 1100 });
  await v
    .getByRole("navigation")
    .getByRole("button", { name: "Studio", exact: true })
    .click();
  await v.getByRole("button", { name: "Rain", exact: true }).click();
  await v.getByRole("button", { name: "Night", exact: true }).click();
  await v.screenshot({
    path: path.join(root, "docs/screenshots/companion-studio-night.png"),
    fullPage: true,
  });
  await v.getByRole("button", { name: "Measure motion", exact: true }).click();
  await v.waitForFunction(
    () =>
      document.querySelector('[data-action="export-frames"]')?.disabled ===
      false,
    {},
    { timeout: 30000 },
  );
  const [download] = await Promise.all([
    v.waitForEvent("download"),
    v.getByRole("button", { name: "Export", exact: true }).click(),
  ]);
  const report = JSON.parse(await fs.readFile(await download.path(), "utf8"));
  assert.equal(report.sampleCount, 240);
  assert.ok(report.p50Ms > 0 && report.p95Ms >= report.p50Ms);
  await fs.writeFile(
    path.join(root, "docs/verification/browser-frame-pacing.json"),
    JSON.stringify(report, null, 2) + "\n",
  );
  pass(
    "browser frame pacing is measured and exported from 240 real rAF intervals",
  );
  await visual.close();
  const unavailable = await browser.newContext();
  await unavailable.addInitScript(() =>
    Object.defineProperty(window, "indexedDB", {
      value: {
        open() {
          throw new DOMException("Storage disabled", "SecurityError");
        },
      },
    }),
  );
  const u = await unavailable.newPage();
  await u.goto(url);
  await u
    .getByText("Local storage is unavailable.", { exact: false })
    .waitFor();
  await u.getByRole("heading", { name: "Isfahan", exact: true }).waitFor();
  await unavailable.close();
  pass(
    "unavailable storage leaves the recorded forecast usable with a clear message",
  );
  const reduced = await browser.newContext({ reducedMotion: "reduce" }),
    r = await reduced.newPage();
  await r.goto(url);
  await r
    .getByRole("navigation")
    .getByRole("button", { name: "Studio", exact: true })
    .click();
  assert.equal(
    await r
      .locator(".cloud-drift")
      .first()
      .evaluate((el) => getComputedStyle(el).animationName),
    "none",
  );
  await reduced.close();
  pass("system reduced-motion preference takes priority");
  const offline = await browser.newContext({ offline: true }),
    o = await offline.newPage();
  await o.goto("file://" + path.join(root, "demo/SkyCanvas.html"));
  await o.getByRole("heading", { name: "Isfahan", exact: true }).waitFor();
  await offline.close();
  pass("standalone HTML opens without a network connection");
  assert.equal(
    requests.filter(
      (u) => !u.startsWith(url) && !u.startsWith("https://api.open-meteo.com/"),
    ).length,
    0,
  );
  assert.deepEqual(errors, []);
  pass("no page errors or external fonts and runtime dependencies");
  await fs.writeFile(
    path.join(root, "docs/verification/browser-tests.json"),
    JSON.stringify(
      {
        version: "1.0.0",
        executedAt: new Date().toISOString(),
        browser: "Chromium 134 / Playwright 1.51.1",
        checkCount: checks.length,
        checks,
        audits,
      },
      null,
      2,
    ) + "\n",
  );
} finally {
  await browser.close();
  server.close();
}
