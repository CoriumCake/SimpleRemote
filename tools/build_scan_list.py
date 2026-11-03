"""Build the "try all codes" scan order: one code set per AC protocol family.

An AC beeps for any valid frame of its own protocol even if the settings inside differ,
so testing one representative per family is enough. Families are found by decoding the
power-on frame of every code set into its leader timing, bit count and leading bytes
(which carry the manufacturer/protocol ID).

usage: python3 tools/build_scan_list.py <irext_probe binary> [<esp_probe binary>]
writes app/src/main/assets/scan.json  (list of code-set ids, most common family first)
"""
import base64
import json
import subprocess
import sys
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
HEAD_BITS = int(__import__("os").environ.get("HEAD_BITS", "16"))
ASSETS = ROOT / "app" / "src" / "main" / "assets"


def broadlink(b64):
    data = base64.b64decode(b64 + "=" * (-len(b64) % 4))
    if len(data) < 4 or data[0] != 0x26:
        return None
    n = data[2] | data[3] << 8
    out, i, end = [], 4, min(4 + n, len(data))
    while i < end:
        v = data[i]; i += 1
        if v == 0:
            if i + 1 >= end:
                break
            v = data[i] << 8 | data[i + 1]; i += 2
        out.append(round(v * 8192 / 269))
    return out


def signature(frame):
    """(leader mark, leader space, bits in first frame, first 24 bits) — coarse protocol fingerprint."""
    if not frame or len(frame) < 8:
        return None
    lead_m, lead_s = frame[0], frame[1]
    spaces = sorted(frame[3:min(len(frame), 200):2])
    if not spaces:
        return None
    short = spaces[len(spaces) // 4]
    long_ = max(s for s in spaces if s < 5 * short) if any(s < 5 * short for s in spaces) else short
    thr = (short + long_) / 2
    bits = []
    for s in frame[3::2]:
        if s > 2.5 * long_ and s > 2500:
            break  # inter-frame gap
        bits.append("1" if s > thr else "0")
    head = "".join(bits[:HEAD_BITS])
    return (round(lead_m / 600), round(lead_s / 600), len(bits) // 4, head)


def main():
    probe = sys.argv[1]
    irext = json.loads((ASSETS / "ac_irext" / "index.json").read_text())
    smartir = json.loads((ASSETS / "ac" / "index.json").read_text())

    files = sorted({e["file"] for e in irext})

    def run(f):
        r = subprocess.run([probe, str(ASSETS / "ac_irext" / f"{f}.bin")], capture_output=True, text=True, timeout=60)
        try:
            return f, json.loads(r.stdout.strip().splitlines()[-1])["frame"]
        except Exception:
            return f, None

    with ThreadPoolExecutor(8) as pool:
        frames = dict(pool.map(run, files))

    # family -> {"ids": [(priority, id)], "brands": set}
    fam = defaultdict(lambda: {"ids": [], "brands": set()})
    by_file = defaultdict(list)
    for e in irext:
        by_file[e["file"]].append(e)
    unknown = 0
    for f, entries in by_file.items():
        sig = signature(frames.get(f))
        if sig is None:
            sig = ("irext", f); unknown += 1
        brands = {e["manufacturer"].lower() for e in entries}
        fam[sig]["ids"].append((-len(brands), entries[0]["id"]))
        fam[sig]["brands"] |= brands

    # IRremoteESP8266: one representative per (protocol, model); preferred over everything else.
    if len(sys.argv) > 2:
        esp = json.loads((ASSETS / "ac_esp" / "index.json").read_text())
        rows = [json.loads(l) for l in subprocess.run([sys.argv[2]], capture_output=True, text=True,
                env={"ASAN_OPTIONS": "detect_leaks=0"}).stdout.splitlines()]
        frames_esp = {(r["protocol"], r["model"]): r.get("frame") for r in rows}
        for e in esp:
            sig = signature(frames_esp.get((e["protocol"], e["espModel"])))
            if sig is None:
                sig = ("esp", e["protocolName"], e["espModel"]); unknown += 1
            fam[sig]["ids"].append((-10_000, e["id"]))
            fam[sig]["brands"].add(e["manufacturer"].lower())

    for e in smartir:
        d = json.loads((ASSETS / "ac" / f"{e['file']}.json").read_text())
        code = d.get("off")
        try:
            frame = [abs(int(v)) for v in code] if isinstance(code, list) else broadlink(code)
        except Exception:
            frame = None
        sig = signature(frame) or ("smartir", e["id"])
        if sig[0] == "smartir":
            unknown += 1
        fam[sig]["ids"].append((1, e["id"]))  # IRext representatives preferred (state generated, verified)
        fam[sig]["brands"].add(e["manufacturer"].lower())

    order = sorted(fam.values(), key=lambda v: (-len(v["brands"]), min(v["ids"])))
    scan = [min(v["ids"])[1] for v in order]
    (ASSETS / "scan.json").write_text(json.dumps(scan))
    total = len(irext) + len(smartir) + (len(esp) if len(sys.argv) > 2 else 0)
    print(f"{total} code sets -> {len(scan)} protocol families ({unknown} ungrouped)")
    print("top families:", [(min(v["ids"])[1], len(v["brands"])) for v in order[:10]])


if __name__ == "__main__":
    main()
