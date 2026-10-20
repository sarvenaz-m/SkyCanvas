import fs from "node:fs/promises";
import path from "node:path";
const root = path.resolve(import.meta.dirname, "..");
const provenance = JSON.parse(
  await fs.readFile(path.join(root, "data/provenance.json"), "utf8"),
);
const snapshots = {};
for (const record of provenance.snapshots)
  snapshots[record.placeId] = {
    placeId: record.placeId,
    payload: await fs.readFile(
      path.join(root, "data", record.placeId + ".json"),
      "utf8",
    ),
    fetchedAt: Math.floor(Date.parse(record.retrievedAt) / 1000),
    origin: "RECORDED",
  };
const font =
  "data:font/ttf;base64," +
  (await fs.readFile(path.join(root, "demo/assets/Manrope.ttf"))).toString(
    "base64",
  );
const css = (
  await fs.readFile(path.join(root, "demo/src/style.css"), "utf8")
).replace("__FONT__", font);
const chunks = await Promise.all(
  ["model", "scene", "app"].map((name) =>
    fs.readFile(path.join(root, "demo/src", name + ".mjs"), "utf8"),
  ),
);
const script = chunks
  .map((s) =>
    s
      .replace(/^import[\s\S]*?from\s+["'][^"']+["'];?\s*\n/gm, "")
      .replace(/^export /gm, ""),
  )
  .join("\n");
let html = await fs.readFile(path.join(root, "demo/src/template.html"), "utf8");
html = html
  .replace("__STYLE__", css)
  .replace(
    "__DATA__",
    JSON.stringify({ snapshots, provenance }).replaceAll("</", "<\\/"),
  )
  .replace("__SCRIPT__", script);
await fs.writeFile(path.join(root, "index.html"), html);
await fs.writeFile(path.join(root, "demo/SkyCanvas.html"), html);
console.log("Offline demo:", Buffer.byteLength(html), "bytes");
