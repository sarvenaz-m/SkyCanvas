import fs from "node:fs/promises";
import path from "node:path";
import http from "node:http";
import { chromium } from "playwright";

const root = path.resolve(import.meta.dirname, "..");
const html = await fs.readFile(path.join(root, "demo/SkyCanvas.html"));
const server = http.createServer((req, res) => {
  res.writeHead(200, { "Content-Type": "text/html" });
  res.end(html);
});
await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
const browser = await chromium.launch({ args: ["--no-sandbox"] });
const context = await browser.newContext({
  viewport: { width: 1200, height: 900 },
  reducedMotion: "no-preference",
  recordVideo: {
    dir: path.join(root, "build/motion-capture"),
    size: { width: 1200, height: 900 },
  },
});
try {
  const page = await context.newPage();
  await page.goto(`http://127.0.0.1:${server.address().port}`);
  await page.waitForFunction(
    () => !document.getElementById("refresh").disabled,
  );
  await page
    .getByRole("navigation")
    .getByRole("button", { name: "Studio", exact: true })
    .click();
  await page.evaluate(() => document.fonts.ready);
  await page.mouse.move(1180, 880);
  await page.waitForTimeout(3500);
  await page.getByRole("button", { name: "Night", exact: true }).click();
  await page.mouse.move(1180, 880);
  await page.waitForTimeout(5000);
  await page
    .locator(".studio-scene")
    .screenshot({
      path: path.join(root, "docs/screenshots/animated-night.png"),
      animations: "allow",
    });
  await page.getByRole("button", { name: "Day", exact: true }).click();
  await page.mouse.move(1180, 880);
  await page.waitForTimeout(3000);
  await page
    .locator(".studio-scene")
    .screenshot({
      path: path.join(root, "docs/screenshots/animated-day.png"),
      animations: "allow",
    });
  const video = page.video();
  await context.close();
  console.log(await video.path());
} finally {
  await browser.close();
  await new Promise((resolve) => server.close(resolve));
}
