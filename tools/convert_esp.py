"""Index the IRremoteESP8266 A/C protocols (app/src/main/cpp/irremoteesp8266, LGPL-2.1).

The app generates frames at runtime through the library's common A/C API (JNI, esp_core.cpp),
so only an index is written: one code set per (brand, protocol, remote model), with the
capabilities the host probe measured (a setting counts only if changing it changes the frame).
Brands come from the "Brand: X, Model: Y (PROTOCOL)" lines in the library's headers — the same
source as its SupportedProtocols.md.

build the probe (from the repo root, S = any scratch dir):
  E=app/src/main/cpp/irremoteesp8266
  for f in $E/*.cpp; do g++ -c -O1 -w -DUNIT_TEST -DSWIGLIB -std=gnu++11 -I$E $f -o $S/$(basename $f .cpp).o; done
  g++ -O1 -w -DUNIT_TEST -DSWIGLIB -std=gnu++11 -I$E -Iapp/src/main/cpp tools/esp_probe.cpp \
      app/src/main/cpp/esp_core.cpp $S/*.o -o $S/esp_probe
usage: python3 tools/convert_esp.py $S/esp_probe
writes app/src/main/assets/ac_esp/index.json
"""
import json
import re
import subprocess
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LIB = ROOT / "app" / "src" / "main" / "cpp" / "irremoteesp8266"
OUT = ROOT / "app" / "src" / "main" / "assets" / "ac_esp"

# Remote model enums (IRsend.h) -> label
MODEL_NAMES = {
    "FUJITSU_AC": {1: "ARRAH2E", 2: "ARDB1", 3: "ARREB1E", 4: "ARJW2", 5: "ARRY4", 6: "ARREW4E"},
    "GREE": {1: "YAW1F", 2: "YBOFB", 3: "YX1FSF"},
    "MIRAGE": {1: "KKG9AC1", 2: "KKG29AC1"},
    "PANASONIC_AC": {1: "LKE", 2: "NKE", 3: "DKE", 4: "JKE", 5: "CKP", 6: "RKR"},
    "SHARP_AC": {1: "A907", 2: "A705", 3: "A903"},
    "TCL112AC": {1: "TAC09CHSD", 2: "GZ055BE1"},
    "VOLTAS": {1: "122LZF"},
    "WHIRLPOOL_AC": {1: "DG11J13A", 2: "DG11J191"},
    "LG": {1: "GE6711AR2853M", 5: "LG6711A20083V"},
    "LG2": {2: "AKB75215403", 3: "AKB74955603", 4: "AKB73757604"},
    "ARGO": {1: "SAC_WREM2", 2: "SAC_WREM3"},
    "TOSHIBA_AC": {0: "Remote A", 1: "Remote B"},
    "HAIER_AC176": {1: "V9014557_A", 2: "V9014557_B"},
    "HITACHI_AC1": {1: "R_LT0541_HTA_A", 2: "R_LT0541_HTA_B"},
}
# Frames that IRrecv reports under another, equivalent name (or has no decoder for).
DECODE_OK = {"TEKNOPOINT": {"TCL112AC"}, "TROTEC_3550": {"UNKNOWN", ""}}
# Brand spellings that should merge with the names the other databases use.
BRAND_ALIASES = {"Comfee (Midea)": "Comfee", "Mitsubishi Electric": "Mitsubishi Electric"}


def norm(s):
    return re.sub(r"[^a-z0-9]", "", s.lower())


def main():
    probe = sys.argv[1]
    rows = [json.loads(l) for l in subprocess.run([probe], capture_output=True, text=True, check=True,
                                                  env={"ASAN_OPTIONS": "detect_leaks=0"}).stdout.splitlines()]
    good = {}
    for r in rows:
        if not r["ok"]:
            print("skip (no frame):", r["name"], r["model"]); continue
        if r["decoded"] != r["name"] and r["decoded"] not in DECODE_OK.get(r["name"], ()):
            print("skip (round-trip):", r["name"], r["model"], "->", r["decoded"]); continue
        if r["max_us"] >= 2_000_000:
            print("skip (too long):", r["name"], r["model"]); continue
        good.setdefault(r["name"], []).append(r)

    enum_text = (LIB / "IRremoteESP8266.h").read_text()
    all_protocols = set(re.findall(r"^\s+([A-Z][A-Z0-9_]+),", enum_text[enum_text.index("enum decode_type_t"):], re.M))
    by_norm = {norm(p): p for p in good}

    # brand -> protocol -> {"models": set(model enum), "labels": [model strings]}
    brands = defaultdict(lambda: defaultdict(lambda: {"models": set(), "labels": []}))
    for h in sorted(LIB.glob("ir_*.h")):
        cpp = h.with_suffix(".cpp")
        if not cpp.exists():
            continue
        sends = re.findall(r"^void IRsend::send([A-Za-z0-9_]+)\(", cpp.read_text(), re.M)
        file_protos = sorted({by_norm[norm(s)] for s in sends if norm(s) in by_norm})
        if not file_protos:
            continue
        for brand, model in re.findall(r"^//\s+Brand:\s*([^,]+?),\s*Model:\s*(.+?)\s*$", h.read_text(), re.M):
            brand = BRAND_ALIASES.get(brand.strip(), brand.strip())
            tokens = set(re.findall(r"[A-Za-z0-9_]+", " ".join(re.findall(r"\(([^)]*)\)", model))))
            named = [p for p in file_protos if p in tokens]
            if not named and tokens & (all_protocols - set(file_protos)):
                continue  # another (non-A/C) protocol from the same file, e.g. a TV remote
            protos = named or file_protos
            label = re.sub(r"\s*\([^)]*\)\s*$", "", model).strip()
            for p in protos:
                wanted = {m for m, n in MODEL_NAMES.get(p, {}).items() if norm(n) in {norm(t) for t in tokens}}
                slot = brands[brand][p]
                slot["models"] |= wanted
                if label and label not in slot["labels"]:
                    slot["labels"].append(label)

    index = []
    for brand in sorted(brands, key=str.lower):
        protos = brands[brand]
        for p in sorted(protos, key=lambda k: -len(protos[k]["labels"])):
            slot = protos[p]
            variants = [r for r in good[p] if not slot["models"] or r["model"] in slot["models"]]
            for r in variants:
                caps = r["caps"]
                temps = sorted({t for c in caps.values() for t in c["temps"]})
                if not temps:
                    continue
                mname = MODEL_NAMES.get(p, {}).get(r["model"])
                proto_label = f"{p}" + (f" · {mname}" if mname else "")
                index.append({
                    "id": f"esp:{norm(brand)}:{p}:{r['model']}",
                    "source": "esp",
                    "manufacturer": brand,
                    "models": [proto_label] + slot["labels"][:8],
                    "encoding": "esp",
                    "minTemp": temps[0], "maxTemp": temps[-1],
                    "modes": list(caps),
                    "fanModes": [f for f in ("auto", "low", "mid", "high") if any(f in c["fans"] for c in caps.values())],
                    "swingModes": ["swing", "fixed"] if any(c["swing"] for c in caps.values()) else [],
                    "caps": caps,
                    "protocol": r["protocol"],
                    "protocolName": p,
                    "espModel": r["model"],
                })
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "index.json").write_text(json.dumps(index, separators=(",", ":")))
    print(f"{len(index)} code sets, {len({e['manufacturer'] for e in index})} brands, "
          f"{len(good)} protocols")


if __name__ == "__main__":
    main()
