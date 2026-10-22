import fs from "node:fs/promises";
import path from "node:path";
import http from "node:http";
import assert from "node:assert/strict";
import { chromium } from "playwright";
import { places } from "../demo/src/model.mjs";
const root = path.resolve(import.meta.dirname, "..");
const html = await fs.readFile(path.join(root, "index.html"));
const server = http.createServer((req, res) => {
  res.setHeader("Content-Type", "text/html");
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
try {
  const page = await browser.newPage({
    viewport: { width: 1280, height: 960 },
    reducedMotion: "no-preference",
  });
  const errors = [];
  page.on("pageerror", (e) => errors.push(e.message));
  await page.goto(url);
  await page.waitForFunction(
    () => !document.getElementById("refresh").disabled,
  );
  const landscapes = [];
  for (const place of places) {
    await page.locator("#nav-places").click();
    assert.equal(await page.locator(".place-card").count(), 6);
    assert.equal(
      await page
        .locator(`[data-value="${place.id}"] .landscape`)
        .getAttribute("data-city"),
      place.id,
    );
    await page
      .getByRole("button", { name: `Open ${place.name} forecast`, exact: true })
      .click();
    await page.waitForFunction(
      () => !document.getElementById("refresh").disabled,
    );
    assert.equal(
      await page.locator(".hero .landscape").getAttribute("data-city"),
      place.id,
    );
    assert.equal(await page.locator(".hero h2").innerText(), place.name);
    landscapes.push(
      await page
        .locator(".hero [data-landmark]")
        .first()
        .getAttribute("data-landmark"),
    );
    pass(
      `${place.name}: Places and Today display the city's own landscape and forecast`,
    );
  }
  assert.equal(new Set(landscapes).size, 6);
  await page.reload();
  await page.waitForFunction(
    () => !document.getElementById("refresh").disabled,
  );
  assert.equal(
    await page.locator(".hero .landscape").getAttribute("data-city"),
    "bandar-abbas",
  );
  const temperature = await page.locator(".hero .temperature").innerText();
  pass("the new coastal city survives a saved preference and reload");
  await page.locator("#nav-studio").click();
  for (const place of places) {
    await page.locator(`#studio-city-${place.id}`).click();
    const svg = page.locator(".studio-scene .landscape");
    assert.equal(await svg.getAttribute("data-city"), place.id);
    for (const light of ["day", "night"]) {
      await page.locator(`#daylight-${light}`).click();
      // Stop the palette transition before comparing two visible animation frames.
      await page.waitForTimeout(1750);
      const before = await svg.screenshot({ animations: "allow" });
      await page.waitForTimeout(240);
      const after = await svg.screenshot({ animations: "allow" });
      assert.notDeepEqual(before, after, `${place.name} ${light} should move`);
      assert.ok(
        (await svg.getAttribute("aria-label")).includes(
          `${place.name} clear landscape, ${light}.`,
        ),
      );
    }
    for (const weather of ["cloud", "fog", "rain", "snow", "storm", "clear"]) {
      await page.locator(`#scene-${weather}`).click();
      assert.equal(await svg.getAttribute("data-city"), place.id);
      assert.equal(await svg.getAttribute("data-scene"), weather);
    }
    await page
      .getByRole("checkbox", { name: "Gentle motion", exact: true })
      .uncheck();
    assert.equal(
      await svg.evaluate((el) => el.getAnimations({ subtree: true }).length),
      0,
    );
    const frozen = await svg.screenshot({ animations: "allow" });
    await page.waitForTimeout(150);
    assert.deepEqual(await svg.screenshot({ animations: "allow" }), frozen);
    await page
      .getByRole("checkbox", { name: "Gentle motion", exact: true })
      .check();
    pass(
      `${place.name}: both light states move, all weather studies retain the city, and motion can stop`,
    );
  }
  await page.locator("#nav-today").click();
  assert.equal(await page.locator(".hero h2").innerText(), "Bandar Abbas");
  assert.equal(
    await page.locator(".hero .temperature").innerText(),
    temperature,
  );
  pass(
    "Studio city studies leave the selected weather record and temperature intact",
  );
  const reduced = await browser.newPage({
    viewport: { width: 320, height: 740 },
    reducedMotion: "reduce",
  });
  await reduced.goto(url);
  await reduced.waitForFunction(
    () => !document.getElementById("refresh").disabled,
  );
  await reduced.locator("#nav-studio").click();
  for (const place of places) {
    await reduced.locator(`#studio-city-${place.id}`).click();
    assert.equal(
      await reduced
        .locator(".landscape")
        .evaluate((el) => el.getAnimations({ subtree: true }).length),
      0,
    );
    assert.ok(
      await reduced.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    );
  }
  pass(
    "all six Studio cities fit a 320px viewport and obey system reduced motion",
  );
  assert.deepEqual(errors, []);
  await fs.writeFile(
    path.join(root, "docs/verification/city-tests.json"),
    JSON.stringify(
      {
        version: "2.0.0",
        executedAt: new Date().toISOString(),
        browser: browser.version(),
        checkCount: checks.length,
        checks,
      },
      null,
      2,
    ) + "\n",
  );
} finally {
  await browser.close();
  await new Promise((resolve) => server.close(resolve));
}
