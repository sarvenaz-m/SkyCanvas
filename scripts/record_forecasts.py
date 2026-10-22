#!/usr/bin/env python3
"""Verify the release records, or record fresh forecasts in a new directory."""

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import sys
import urllib.request

ROOT = Path(__file__).resolve().parent.parent


def digest(payload):
    return hashlib.sha256(payload).hexdigest()


def verify():
    manifest = json.loads((ROOT / "data/provenance.json").read_text())
    assets = ROOT / "app/src/main/assets"
    for record in manifest["snapshots"]:
        name = record["placeId"] + ".json"
        payload = (ROOT / "data" / name).read_bytes()
        if digest(payload) != record["sha256"]:
            raise ValueError(f"Source digest differs: {name}")
        if payload != (assets / "snapshots" / name).read_bytes():
            raise ValueError(f"Android asset differs: {name}")
        print(f"Verified {record['placeId']}: source digest and Android asset")
    if (ROOT / "data/provenance.json").read_bytes() != (assets / "provenance.json").read_bytes():
        raise ValueError("Android provenance differs from the source manifest")


    for name in ["places.json", "city-scenes.json"]:
        if (ROOT / "data" / name).read_bytes() != (assets / name).read_bytes():
            raise ValueError(f"Android asset differs: {name}")


def record_into(destination):
    destination = destination.resolve()
    # An existing directory may contain records from an earlier run.
    destination.mkdir(parents=True, exist_ok=False)
    manifest = json.loads((ROOT / "data/provenance.json").read_text())
    for record in manifest["snapshots"]:
        request = urllib.request.Request(
            record["url"], headers={"User-Agent": "SkyCanvas/2.0.0 forecast-recording"}
        )
        with urllib.request.urlopen(request, timeout=20) as response:
            if "json" not in response.headers.get_content_type():
                raise ValueError("Expected a JSON response")
            payload = response.read(1_048_577)
            if len(payload) > 1_048_576:
                raise ValueError("Forecast exceeds the payload limit")
        json.loads(payload)  # Keep response bytes unchanged; reject invalid JSON.
        record["retrievedAt"] = datetime.now(timezone.utc).isoformat()
        record["sha256"] = digest(payload)
        (destination / (record["placeId"] + ".json")).write_bytes(payload)
        print(f"Recorded {record['placeId']}")
    (destination / "provenance.json").write_text(json.dumps(manifest, indent=2) + "\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    choice = parser.add_mutually_exclusive_group(required=True)
    choice.add_argument("--verify", action="store_true")
    choice.add_argument("--output-dir", type=Path)
    args = parser.parse_args()
    if args.verify:
        verify()
    else:
        record_into(args.output_dir)


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, KeyError) as error:
        print(f"Forecast records: {error}", file=sys.stderr)
        sys.exit(1)
