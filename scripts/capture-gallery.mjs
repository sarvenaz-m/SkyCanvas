import fs from "node:fs/promises";
import path from "node:path";
import http from "node:http";
import { chromium } from "playwright";

const root = path.resolve(import.meta.dirname, "..");
const directory = path.join(root, "docs/screenshots");
const html = await fs.readFile(path.join(root, "index.html"));
const art = JSON.parse(
  await fs.readFile(path.join(root, "data/city-scenes.json"), "utf8"),
);
const server = http.createServer((req, res) => {
  res.setHeader("Content-Type", "text/html");
  res.end(html);
});
await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
const browser = await chromium.launch({ args: ["--no-sandbox"] });
const url = `http://127.0.0.1:${server.address().port}`;
const errors = [];
try {
  const page = await browser.newPage({
    viewport: { width: 1440, height: 1100 },
    reducedMotion: "reduce",
  });
  page.on("pageerror", (e) => errors.push(e.message));
  await page.goto(url);
  await page.waitForFunction(
    () => !document.getElementById("refresh").disabled,
  );
  await page.evaluate(() => document.fonts.ready);
  await page.locator("#nav-studio").click();
  for (const [id, city] of Object.entries(art.cities)) {
    await page.locator(`#studio-city-${id}`).click();
    for (const light of ["day", "night"]) {
      await page.locator(`#daylight-${light}`).click();
      await page
        .locator(".studio-scene")
        .screenshot({ path: path.join(directory, `city-${id}-${light}.png`) });
    }
  }
  await page.locator("#studio-city-isfahan").click();
  await page.locator("#daylight-day").click();
  await page.screenshot({
    path: path.join(directory, "companion-studio.png"),
    fullPage: true,
  });
  await page.locator("#studio-city-bandar-abbas").click();
  await page.locator("#daylight-night").click();
  await page.screenshot({
    path: path.join(directory, "companion-studio-night.png"),
    fullPage: true,
  });
  await page.locator("#nav-places").click();
  await page.screenshot({
    path: path.join(directory, "companion-places.png"),
    fullPage: true,
  });
  await page
    .getByRole("button", { name: "Open Tehran forecast", exact: true })
    .click();
  await page.screenshot({
    path: path.join(directory, "companion-today.png"),
    fullPage: true,
  });
  await page.locator("#nav-hours").click();
  await page.screenshot({
    path: path.join(directory, "companion-forecast.png"),
    fullPage: true,
  });
  // Galleries contain unchanged crops from the actual production scene, arranged for the README.
  for (const light of ["day", "night"]) {
    const font = (
      await fs.readFile(path.join(root, "demo/assets/Manrope.ttf"))
    ).toString("base64");
    let cards = "";
    for (const [id, city] of Object.entries(art.cities)) {
      const picture = (
        await fs.readFile(path.join(directory, `city-${id}-${light}.png`))
      ).toString("base64");
      cards += `<article><img src="data:image/png;base64,${picture}"><div><b>${city.name}</b><span>${city.landmark}</span></div></article>`;
    }
    const gallery = await browser.newPage({
      viewport: { width: 1440, height: 1110 },
      deviceScaleFactor: 1,
    });
    await gallery.setContent(
      `<style>@font-face{font-family:M;src:url(data:font/ttf;base64,${font})}*{box-sizing:border-box}body{margin:0;background:${light === "day" ? "#f3f6f8" : "#121f36"};color:${light === "day" ? "#193754" : "#f5ead2"};font-family:M;padding:52px 52px 34px}header{display:flex;justify-content:space-between;align-items:end;margin-bottom:32px}.k{font-size:12px;letter-spacing:3px;text-transform:uppercase;opacity:.7}h1{font-size:43px;letter-spacing:-1.3px;margin:10px 0 5px}header p{font-size:14px;opacity:.75}header aside{text-align:right;font-size:13px;line-height:1.8;opacity:.7}.grid{display:grid;grid-template-columns:repeat(3,1fr);gap:24px}article{border-radius:20px;overflow:hidden;background:${light === "day" ? "#fff" : "#1c2c46"};border:1px solid ${light === "day" ? "#dce5ed" : "#2c3b55"}}img{width:100%;height:280px;object-fit:cover;display:block}article div{padding:17px 20px 19px}b{font-size:20px;display:block}span{display:block;font-size:12px;opacity:.7;margin-top:5px}footer{font-size:12px;display:flex;justify-content:space-between;opacity:.65;margin-top:27px}</style><header><div><div class=k>SKYCANVAS / 2.0.0</div><h1>${light === "day" ? "Six cities. A little daylight." : "When the cities turn to night."}</h1><p>Original landscapes, each with a sense of place.</p></div><aside>${light === "day" ? "THE DAYLIGHT COLLECTION" : "THE NIGHT COLLECTION"}<br>Captured from the interactive companion</aside></header><div class=grid>${cards}</div><footer><div>Isfahan · Shiraz · Tehran · Tabriz · Mashhad · Bandar Abbas</div><div>sarvenaz-m.github.io/SkyCanvas</div></footer>`,
    );
    await gallery.evaluate(() => document.fonts.ready);
    await gallery.screenshot({
      path: path.join(directory, `cities-${light}-v2.png`),
      fullPage: true,
    });
    await gallery.close();
  }
  await page.setViewportSize({ width: 390, height: 844 });
  for (const [tab, name] of [
    ["places", "places"],
    ["today", "today"],
    ["hours", "forecast"],
    ["studio", "studio"],
  ]) {
    await page.locator(`#nav-${tab}`).click();
    if (tab === "studio") {
      await page.locator("#studio-city-shiraz").click();
      await page.locator("#daylight-day").click();
    }
    await page.screenshot({
      path: path.join(directory, `mobile-${name}.png`),
      fullPage: true,
    });
  }
  for (const light of ["day", "night"])
    await fs.copyFile(
      path.join(directory, `city-isfahan-${light}.png`),
      path.join(directory, `animated-${light}.png`),
    );
  if (errors.length) throw Error(errors.join("\n"));
  console.log(
    "Captured 12 city scenes, two galleries, five desktop and four mobile views.",
  );
} finally {
  await browser.close();
  await new Promise((resolve) => server.close(resolve));
}
