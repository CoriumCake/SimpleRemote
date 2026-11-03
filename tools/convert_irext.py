#!/usr/bin/env python3
"""Convert the IRext open IR database (https://github.com/irext/database, MIT) into app assets.

Usage:
    # 1. get the data (Git LFS files)
    curl -LO https://media.githubusercontent.com/media/irext/database/HEAD/db/irext_db_<date>_sqlite3.db
    curl -LO https://media.githubusercontent.com/media/irext/database/HEAD/binaries/irext-binaries_<date>.zip
    unzip irext-binaries_<date>.zip
    # 2. build the host probe (runs the same C decoder the app ships, with sanitizers)
    gcc -O1 -g -fsanitize=address,undefined -fno-sanitize-recover=undefined -w \
        -Iapp/src/main/cpp/irext/include tools/irext_probe.c app/src/main/cpp/irext/*.c -o /tmp/irext_probe
    # 3. convert
    python3 tools/convert_irext.py irext_db.sqlite3 irext-binaries_<date>/ /tmp/irext_probe

Every AC binary is probed in its own subprocess: any binary that crashes, trips a sanitizer,
fails to parse, or cannot produce codes is left out, so it can never crash the app.

Output (overwritten):
    app/src/main/assets/ac_irext/index.json
    app/src/main/assets/ac_irext/<remote_map>.bin
"""
import json
import re
import shutil
import sqlite3
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app" / "src" / "main" / "assets" / "ac_irext"

MODES = ["cool", "heat", "auto", "fan_only", "dry"]  # IRext t_ac_mode order
SPEEDS = ["auto", "low", "mid", "high"]  # IRext t_ac_wind_speed order
TEMP_BASE = 16  # IRext temperature index 0 == 16 °C


def display_brand(name_en, name, name_tw):
    n = (name_en or "").strip() or (name or "").strip() or (name_tw or "").strip()
    n = re.sub(r"[-_]+", " ", n).strip()
    # "MITSUBISHI Electric" -> "Mitsubishi Electric", keep short acronyms like LG / TCL / AUX
    words = [w.capitalize() if (w.isupper() and len(w) > 4) else w for w in n.split()]
    return " ".join(words)


def probe(probe_bin, path):
    try:
        r = subprocess.run([probe_bin, str(path)], capture_output=True, text=True, timeout=60)
    except subprocess.TimeoutExpired:
        return None, "timeout"
    if r.returncode != 0:
        tail = (r.stderr.strip().splitlines() or ["?"])[-1][:120]
        return None, f"exit {r.returncode}: {tail}"
    try:
        return json.loads(r.stdout.strip().splitlines()[-1]), None
    except Exception as e:  # noqa: BLE001
        return None, f"bad output: {e}"


def main():
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    db, bins, probe_bin = Path(sys.argv[1]), Path(sys.argv[2]), sys.argv[3]
    con = sqlite3.connect(db)
    rows = con.execute(
        """select r.remote_map, r.sub_cate, r.priority, r.brand_id, b.name_en, b.name, b.name_tw
           from remote_index r left join brand b on b.id = r.brand_id
           where r.category_id = 1 and r.status = 1
           order by r.brand_id, coalesce(r.priority, 999), r.id"""
    ).fetchall()

    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)

    jobs = []
    for remote_map, sub_cate, priority, brand_id, name_en, name, name_tw in rows:
        path = bins / f"irda_{remote_map}.bin"
        if path.exists():
            jobs.append((remote_map, display_brand(name_en, name, name_tw), path))

    with ThreadPoolExecutor(max_workers=8) as pool:
        results = list(pool.map(lambda j: probe(probe_bin, j[2]), jobs))

    index, skipped, seen_hash, seen_ids = [], [], {}, set()
    leaders = []
    for (remote_map, brand, path), (res, err) in zip(jobs, results):
        if err or not res or not res.get("ok"):
            skipped.append((remote_map, err or (res or {}).get("err", "?")))
            continue
        total, failed = res["total"], res["failed"]
        if total == 0 or failed * 10 > total or res["offLen"] <= 0:
            skipped.append((remote_map, f"decodes {total - failed}/{total} states, off={res['offLen']}"))
            continue
        if res["durMax"] > 2_000_000:
            skipped.append((remote_map, f"frame too long ({res['durMax']} us)"))
            continue
        data = path.read_bytes()
        # Identical binaries are listed under several brands; keep one file, many index entries.
        key = hash(data)
        file_id = seen_hash.setdefault(key, remote_map)
        if file_id == remote_map:
            (OUT / f"{remote_map}.bin").write_bytes(data)

        caps = {}
        for m, mode in enumerate(MODES):
            if not res["modes"] & (1 << m):
                continue
            tmin, tmax = res["tmin"][m], res["tmax"][m]
            sp = res["speeds"][m]
            caps[mode] = {
                "temps": [] if tmin < 0 else list(range(TEMP_BASE + tmin, TEMP_BASE + tmax + 1)),
                "fans": [s for i, s in enumerate(SPEEDS) if sp & (1 << i)],
                "swing": res["swings"][m] != 0,
            }
        if not caps:
            skipped.append((remote_map, "no modes"))
            continue
        leaders.append(res["leader"])
        all_temps = [t for c in caps.values() for t in c["temps"]] or [24]
        # The same remote binary is listed under many brands, so the id must include the brand.
        bkey = "".join(ch for ch in brand.lower() if ch.isalnum()) or brand
        uid = f"irext:{bkey}:{remote_map}"
        if uid in seen_ids:
            continue
        seen_ids.add(uid)
        index.append({
            "id": uid,
            "source": "irext",
            "file": file_id,
            "manufacturer": brand,
            "models": [],
            "encoding": "irext",
            "minTemp": min(all_temps),
            "maxTemp": max(all_temps),
            "precision": 1,
            "fahrenheit": False,
            "modes": list(caps),
            "fanModes": [s for s in SPEEDS if any(s in c["fans"] for c in caps.values())],
            "swingModes": ["swing", "fixed"] if any(c["swing"] for c in caps.values()) else [],
            "extras": [],
            "caps": caps,
        })

    (OUT / "index.json").write_text(json.dumps(index, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    leaders.sort()
    brands = len({re.sub(r"[^0-9a-z]", "", e["manufacturer"].lower()) or e["manufacturer"] for e in index})
    files = len(list(OUT.glob("*.bin")))
    print(f"wrote {len(index)} AC remotes ({brands} brands, {files} unique binaries) to {OUT}")
    if leaders:
        print(f"median leader pulse: {leaders[len(leaders) // 2]} us")
    print(f"skipped {len(skipped)}")
    for name, why in skipped[:60]:
        print(f"  skipped {name}: {why}")


if __name__ == "__main__":
    main()
