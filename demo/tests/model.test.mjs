import { test } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import {
  places,
  condition,
  decode,
  dateKey,
  time,
  temp,
  hoursFor,
  frameReport,
  forecastUrl,
} from "../src/model.mjs";
const raw = fs.readFileSync(
  new URL("../../data/isfahan.json", import.meta.url),
  "utf8",
);
const place = places[0],
  clone = () => JSON.parse(raw);
test("all four public forecasts match their requested place", () => {
  for (const p of places) {
    const f = decode(
      fs.readFileSync(
        new URL(`../../data/${p.id}.json`, import.meta.url),
        "utf8",
      ),
      p,
    );
    assert.equal(f.hours.length, 168);
    assert.equal(f.days.length, 7);
  }
});
test("recorded current values are decoded without invented readings", () => {
  const f = decode(raw, place);
  assert.equal(f.current.temperature, 27.5);
  assert.equal(f.current.humidity, 13);
});
test("Tehran half-hour offset is independent of browser timezone", () => {
  assert.equal(
    time(Date.parse("2026-10-19T00:00Z") / 1000, place.zone),
    "03:30",
  );
});
test("upcoming hours begin at the current model time", () => {
  const f = decode(raw, place),
    h = hoursFor(f, null);
  assert.equal(h.length, 24);
  assert.ok(h.every((v) => v.time >= f.current.time));
});
test("day selection follows local dates", () => {
  const f = decode(raw, place);
  for (const d of f.days) {
    const h = hoursFor(f, d.time);
    assert.equal(h.length, 24);
    assert.ok(
      h.every(
        (v) => dateKey(v.time, place.zone) === dateKey(d.time, place.zone),
      ),
    );
  }
});
test("null temperatures stay unavailable", () => {
  const d = clone();
  d.current.temperature_2m = null;
  assert.equal(decode(JSON.stringify(d), place).current.temperature, null);
  assert.equal(temp(null), "—");
});
test("unexpected units are rejected", () => {
  const d = clone();
  d.hourly_units.temperature_2m = "°F";
  assert.throws(() => decode(JSON.stringify(d), place));
});
test("a different grid is rejected", () =>
  assert.throws(() => decode(raw, places[3])));
test("mismatched columns are rejected", () => {
  const d = clone();
  d.hourly.precipitation.pop();
  assert.throws(() => decode(JSON.stringify(d), place));
});
test("duplicate timestamps are rejected", () => {
  const d = clone();
  d.hourly.time[1] = d.hourly.time[0];
  assert.throws(() => decode(JSON.stringify(d), place));
});
test("invalid precipitation probability is rejected", () => {
  const d = clone();
  d.hourly.precipitation_probability[0] = 101;
  assert.throws(() => decode(JSON.stringify(d), place));
});
test("negative rain is rejected", () => {
  const d = clone();
  d.hourly.precipitation[0] = -1;
  assert.throws(() => decode(JSON.stringify(d), place));
});
test("reversed daily ranges are rejected", () => {
  const d = clone();
  d.daily.temperature_2m_min[0] = 90;
  assert.throws(() => decode(JSON.stringify(d), place));
});
test("WMO groups include freezing precipitation and heavy thunderstorms", () => {
  assert.equal(condition(67).scene, "rain");
  assert.equal(condition(86).scene, "snow");
  assert.equal(condition(97).scene, "storm");
  assert.ok(condition(99).label.includes("hail"));
  assert.equal(condition(null).label, "Conditions unavailable");
});
test("unit conversion leaves source data untouched", () => {
  const f = decode(raw, place);
  assert.equal(temp(f.current.temperature, true), "82°");
  assert.equal(f.current.temperature, 27.5);
});
test("frame report uses measured samples and nearest-rank percentiles", () => {
  const r = frameReport([16, 16, 17, 32]);
  assert.equal(r.sampleCount, 4);
  assert.equal(r.p50Ms, 16);
  assert.equal(r.p95Ms, 32);
  assert.equal(r.intervalsOver1_5Median, 1);
  assert.equal(frameReport([]), null);
});
test("API request fixes units and uses epoch timestamps", () => {
  const url = new URL(forecastUrl(place));
  assert.equal(url.searchParams.get("timeformat"), "unixtime");
  assert.equal(url.searchParams.get("temperature_unit"), "celsius");
  assert.equal(url.searchParams.get("timezone"), "Asia/Tehran");
});
