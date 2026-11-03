#!/usr/bin/env python3
"""Convert SmartIR climate code files into SimpleRemote app assets.

Usage:
    git clone --depth 1 https://github.com/smartHomeHub/SmartIR.git /path/to/SmartIR
    python3 tools/convert_smartir.py /path/to/SmartIR

Output (overwritten):
    app/src/main/assets/ac/index.json     - lightweight list of every AC remote
    app/src/main/assets/ac/<id>.json      - full code table for one remote (loaded on demand)

SmartIR (https://github.com/smartHomeHub/SmartIR) is MIT licensed; see
app/src/main/assets/licenses/SmartIR-LICENSE.txt.

Normalisations applied so the app never has to guess:
  * Only Broadlink (base64) and ESPHome (raw signed µs array) encodings are kept.
  * Temperature keys are canonical: "16" for whole numbers, "16.5" otherwise.
  * ESPHome command strings are parsed into int arrays.
  * Top-level string commands other than "off"/"on" become `extras` (shown as extra buttons).
"""
import json
import re
import shutil
import sys
from pathlib import Path

SUPPORTED = {"Broadlink": "broadlink", "ESPHome": "raw"}
ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app" / "src" / "main" / "assets" / "ac"
LICENSE_OUT = ROOT / "app" / "src" / "main" / "assets" / "licenses" / "SmartIR-LICENSE.txt"


def load_lenient(path: Path):
    text = path.read_text(encoding="utf-8")
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        # A few files have trailing commas.
        return json.loads(re.sub(r",(\s*[}\]])", r"\1", text))


def canon_temp(key: str) -> str:
    v = float(key)
    return str(int(v)) if v == int(v) else str(v)


def is_number(key: str) -> bool:
    try:
        float(key)
        return True
    except ValueError:
        return False


def convert_code(code, enc):
    if enc == "raw":
        if isinstance(code, str):
            # Tolerate stray characters (e.g. a backtick typo in 7300.json).
            return [int(x) for x in re.findall(r"-?\d+", code)]
        return [int(x) for x in code]
    return code


def normalise(node, enc):
    """Recursively canonicalise temperature keys and convert leaf codes."""
    if isinstance(node, dict):
        out = {}
        for k, v in node.items():
            nk = canon_temp(k) if is_number(k) else k
            out[nk] = normalise(v, enc)
        return out
    return convert_code(node, enc)


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    src = Path(sys.argv[1])
    files = sorted((src / "codes" / "climate").glob("*.json"), key=lambda p: int(p.stem) if p.stem.isdigit() else 0)
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)

    index, skipped = [], []
    for f in files:
        try:
            d = load_lenient(f)
        except Exception as e:  # noqa: BLE001
            skipped.append((f.name, f"parse error: {e}"))
            continue
        ctrl = d.get("supportedController")
        enc = SUPPORTED.get(ctrl)
        if not enc:
            skipped.append((f.name, f"unsupported controller {ctrl}"))
            continue
        if ctrl == "Broadlink" and d.get("commandsEncoding", "Base64") != "Base64":
            skipped.append((f.name, f"unsupported encoding {d.get('commandsEncoding')}"))
            continue

        cmds = d["commands"]
        modes = [m for m in d.get("operationModes", []) if isinstance(cmds.get(m), dict)]
        if "off" not in cmds or not modes:
            skipped.append((f.name, "missing off or mode tables"))
            continue

        try:
            device = build_device(f"smartir:{f.stem}", d, cmds, modes, enc)
        except Exception as e:  # noqa: BLE001
            skipped.append((f.name, f"conversion error: {e}"))
            continue
        (OUT / f"{f.stem}.json").write_text(json.dumps(device, separators=(",", ":")), encoding="utf-8")
        index.append({k: device[k] for k in (
            "id", "manufacturer", "models", "encoding", "minTemp", "maxTemp", "precision",
            "fahrenheit", "modes", "fanModes", "swingModes")} | {"extras": sorted(device["extras"]), "source": "smartir", "file": f.stem})

    index.sort(key=lambda e: (e["manufacturer"].lower(), int(e["file"])))
    (OUT / "index.json").write_text(json.dumps(index, separators=(",", ":")), encoding="utf-8")
    LICENSE_OUT.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy(src / "LICENSE", LICENSE_OUT)

    brands = len({e["manufacturer"].lower() for e in index})
    print(f"wrote {len(index)} remotes ({brands} brands) to {OUT}")
    for name, why in skipped:
        print(f"skipped {name}: {why}")


def build_device(dev_id, d, cmds, modes, enc):
    extras = {k: convert_code(v, enc) for k, v in cmds.items()
              if not isinstance(v, dict) and k not in ("off", "on")}
    min_t, max_t = d["minTemperature"], d["maxTemperature"]
    return {
        "id": dev_id,
        "manufacturer": d["manufacturer"].strip(),
        "models": [m.strip() for m in d.get("supportedModels", []) if str(m).strip()],
        "encoding": enc,
        "minTemp": min_t,
        "maxTemp": max_t,
        "precision": d.get("precision", 1),
        "fahrenheit": min_t >= 50,
        "modes": modes,
        "fanModes": d.get("fanModes", []),
        "swingModes": d.get("swingModes", []),
        "off": convert_code(cmds["off"], enc),
        "on": convert_code(cmds["on"], enc) if "on" in cmds and not isinstance(cmds["on"], dict) else None,
        "extras": extras,
        "commands": {m: normalise(cmds[m], enc) for m in modes},
    }


if __name__ == "__main__":
    main()
