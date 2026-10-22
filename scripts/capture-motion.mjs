import fs from "node:fs/promises";
import path from "node:path";
import http from "node:http";
import { execFileSync } from "node:child_process";
import { chromium } from "playwright";

const root = path.resolve(import.meta.dirname, "..");
const html = await fs.readFile(path.join(root, "index.html"));
const server = http.createServer((req, res) => {
  res.setHeader("Content-Type", "text/html");
  res.end(html);
});
await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
const browser = await chromium.launch({ args: ["--no-sandbox"] });
const context = await browser.newContext({
  viewport: { width: 1200, height: 820 },
  reducedMotion: "no-preference",
  recordVideo: {
    dir: path.join(root, "build/motion-capture"),
    size: { width: 1200, height: 820 },
  },
});
let video;
try {
  const page = await context.newPage();
  await page.goto(`http://127.0.0.1:${server.address().port}`);
  await page.waitForFunction(
    () => !document.getElementById("refresh").disabled,
  );
  await page.locator("#nav-studio").click();
  await page.evaluate(() => document.fonts.ready);
  const scenes = [];
  for (const id of [
    "isfahan",
    "shiraz",
    "tehran",
    "tabriz",
    "mashhad",
    "bandar-abbas",
  ]) {
    await page.locator(`#studio-city-${id}`).click();
    scenes.push(
      await page
        .locator(".studio-scene .landscape")
        .evaluate((el) => el.outerHTML),
    );
  }
  // Arrange the actual production SVGs; every path, colour and animation is unchanged.
  await page.evaluate((scenes) => {
    document.body.insertAdjacentHTML(
      "beforeend",
      `<style>body{background:#f3f6f8}.motion-board{position:fixed;inset:0;background:#f3f6f8;padding:27px 34px;z-index:1000}.motion-title{display:flex;justify-content:space-between;align-items:center;margin-bottom:20px;color:#193754}.motion-title h1{font-size:29px;letter-spacing:-1px}.motion-title p{font-size:12px}.motion-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:18px}.motion-card{border:1px solid #d6e2ec;border-radius:17px;overflow:hidden;background:white}.motion-viewport{height:263px;position:relative}.motion-card b{display:block;padding:11px 16px;font-size:15px;color:#193754}.motion-footer{font-size:11px;margin-top:18px;color:#51718a}.motion-board.is-night-board{background:#121f36}.is-night-board .motion-title,.is-night-board .motion-footer{color:#f2dfbd}.is-night-board .motion-card{background:#1c2c46;border-color:#364358}.is-night-board .motion-card b{color:#f2dfbd}.motion-board,.motion-card{transition:background 1.6s ease}</style><div class="motion-board"><div class="motion-title"><div><p>SKYCANVAS / VERSION 2</p><h1>Six cities. Day into night.</h1></div><p>Original landscapes · actual animated renderer</p></div><div class="motion-grid">${scenes.map((svg, i) => `<div class="motion-card"><div class="motion-viewport">${svg.replaceAll("studio-sky", `motion-sky-${i}`)}</div><b>${["Isfahan", "Shiraz", "Tehran", "Tabriz", "Mashhad", "Bandar Abbas"][i]}</b></div>`).join("")}</div><div class="motion-footer">sarvenaz-m.github.io/SkyCanvas · clouds, light, water and a sense of place</div></div>`,
    );
  }, scenes);
  await page.mouse.move(1195, 815);
  video = page.video();
  await page.waitForTimeout(2500);
  await page.evaluate(() => {
    document.querySelector(".motion-board").classList.add("is-night-board");
    for (const svg of document.querySelectorAll(".motion-board .landscape")) {
      svg.classList.remove("is-day");
      svg.classList.add("is-night");
    }
  });
  await page.waitForTimeout(2500);
  await page.waitForTimeout(2300);
  await page.evaluate(() => {
    document.querySelector(".motion-board").classList.remove("is-night-board");
    for (const svg of document.querySelectorAll(".motion-board .landscape")) {
      svg.classList.add("is-day");
      svg.classList.remove("is-night");
    }
  });
  await page.waitForTimeout(2600);
  await context.close();
  const input = await video.path();
  const output = path.join(root, "docs/day-night-motion.mp4");
  execFileSync("ffmpeg", [
    "-hide_banner",
    "-loglevel",
    "error",
    "-y",
    "-sseof",
    "-9.9",
    "-i",
    input,
    "-an",
    "-c:v",
    "libx264",
    "-pix_fmt",
    "yuv420p",
    "-crf",
    "23",
    "-movflags",
    "+faststart",
    output,
  ]);
  const gif = path.join(root, "docs/screenshots/day-night-motion.gif");
  execFileSync("ffmpeg", [
    "-hide_banner",
    "-loglevel",
    "error",
    "-y",
    "-sseof",
    "-9.9",
    "-i",
    input,
    "-filter_complex",
    "fps=6,scale=840:-1:flags=lanczos,split[s0][s1];[s0]palettegen=max_colors=96[p];[s1][p]paletteuse=dither=bayer:bayer_scale=3",
    "-loop",
    "0",
    gif,
  ]);
  console.log("Recorded the six-city production scene montage as MP4 and GIF.");
} finally {
  await browser.close();
  await new Promise((resolve) => server.close(resolve));
}
