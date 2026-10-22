import fs from "node:fs/promises";
import path from "node:path";
import { chromium } from "playwright";
const root = path.resolve(import.meta.dirname, "..");
const directory = path.join(root, "docs/screenshots");
const font = (
  await fs.readFile(path.join(root, "demo/assets/Manrope.ttf"))
).toString("base64");
const uri = async (name) =>
  "data:image/png;base64," +
  (await fs.readFile(path.join(directory, name + ".png"))).toString("base64");
const browser = await chromium.launch({ args: ["--no-sandbox"] });
try {
  for (const kind of ["native", "companion"]) {
    const page = await browser.newPage({
      viewport: { width: 1560, height: 1120 },
    });
    const files =
      kind === "native"
        ? ["android-today", "android-places", "android-studio-coast"]
        : ["companion-studio-night", "mobile-today"];
    const imgs = await Promise.all(files.map(uri));
    const common = `<style>@font-face{font-family:M;src:url(data:font/ttf;base64,${font})}*{box-sizing:border-box}body{margin:0;font-family:M;background:#f1f5f8;color:#193754;padding:44px 65px 32px}header{display:flex;align-items:end;justify-content:space-between;margin-bottom:31px}small{font-size:12px;letter-spacing:2.5px}h1{font-size:41px;letter-spacing:-1.2px;margin:10px 0 7px}header p{font-size:14px;color:#54728a;margin:0}aside{font-size:12px;line-height:1.9;text-align:right;color:#54728a}footer{display:flex;justify-content:space-between;margin-top:24px;font-size:12px;color:#54728a}.row{display:flex;gap:58px;align-items:start;justify-content:center}.phone{width:388px;background:#fff;border:7px solid #193754;border-radius:29px;overflow:hidden;box-shadow:0 17px 45px #19375412}.phone img{display:block;width:100%;height:812px;object-fit:cover;object-position:top}.desktop{width:1050px;border:1px solid #cad8e6;border-radius:22px;overflow:hidden;background:#fff;box-shadow:0 17px 45px #19375412}.desktop img{display:block;width:100%;height:808px;object-fit:cover;object-position:top}.companion .phone{width:303px}.companion .phone img{height:640px}figcaption{text-align:center;font-size:12px;letter-spacing:1px;margin-top:17px;color:#54728a}figure{margin:0}</style>`;
    const title =
      kind === "native"
        ? "An illustrated day. Built for Android."
        : "A little closer to the sky.";
    const desc =
      kind === "native"
        ? "Six cities, original landscapes and a forecast to return to."
        : "Six original city landscapes, in an interactive weather companion.";
    const label =
      kind === "native"
        ? "NATIVE ANDROID · KOTLIN + COMPOSE"
        : "INTERACTIVE BROWSER COMPANION";
    const row =
      kind === "native"
        ? imgs
            .map(
              (src, i) =>
                `<figure><div class="phone"><img src="${src}"></div><figcaption>${["TODAY / ISFAHAN", "PLACES / SIX CITY FORECASTS", "STUDIO / BANDAR ABBAS AT NIGHT"][i]}</figcaption></figure>`,
            )
            .join("")
        : `<figure><div class="desktop"><img src="${imgs[0]}"></div><figcaption>LANDSCAPE STUDIO / BANDAR ABBAS AT NIGHT</figcaption></figure><figure><div class="phone"><img src="${imgs[1]}"></div><figcaption>TODAY / TEHRAN</figcaption></figure>`;
    await page.setContent(
      `${common}<header><div><small>SKYCANVAS / VERSION 2.0.0</small><h1>${title}</h1><p>${desc}</p></div><aside>${label}<br>Actual application screenshots</aside></header><div class="row ${kind}">${row}</div><footer><span>Isfahan · Shiraz · Tehran · Tabriz · Mashhad · Bandar Abbas</span><span>sarvenaz-m.github.io/SkyCanvas</span></footer>`,
    );
    await page.evaluate(() => document.fonts.ready);
    await page.screenshot({
      path: path.join(
        directory,
        kind === "native"
          ? "skycanvas-showcase.png"
          : "skycanvas-companion-showcase.png",
      ),
      fullPage: true,
    });
    await page.close();
  }
  console.log(
    "Updated native and companion showcase boards from current screenshots.",
  );
} finally {
  await browser.close();
}
