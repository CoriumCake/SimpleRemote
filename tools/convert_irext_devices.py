#!/usr/bin/env python3
"""Convert the IRext non-AC remotes (TV, set-top box, fan, projector, ...) into app assets.

Same inputs as tools/convert_irext.py (IRext database, MIT licensed):
    gcc -O1 -g -fsanitize=address,undefined -fno-sanitize-recover=undefined -w \
        -Iapp/src/main/cpp/irext/include tools/irext_device_probe.c app/src/main/cpp/irext/*.c -o /tmp/irext_device_probe
    python3 -I tools/convert_irext_devices.py irext_db.sqlite3 irext-binaries_<date>/ /tmp/irext_device_probe

Every binary is decoded key by key in its own subprocess with sanitizers; binaries that crash or
fail are dropped, and only keys that decoded cleanly are listed, so the app never decodes an
unverified key. Chinese cable-operator set-top boxes (listed by city, no brand) are skipped.

Output (overwritten):
    app/src/main/assets/ir/irext_index.json    DeviceEntry list (see data/Devices.kt)
    app/src/main/assets/ir_irext/<remote_map>.bin
"""
import hashlib
import json
import re
import shutil
import sqlite3
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app" / "src" / "main" / "assets"
OUT_BIN = ASSETS / "ir_irext"
OUT_INDEX = ASSETS / "ir" / "irext_index.json"

# IRext category id -> app category id (data/Devices.kt DeviceCategory)
CATEGORY = {2: "tv", 3: "stb", 4: "streaming", 5: "stb", 6: "disc", 7: "fan", 8: "projector",
            9: "audio", 10: "light", 11: "stb", 12: "vacuum", 13: "air_purifier", 14: "fan"}
MAX_US = 1_900_000


def display_brand(name_en, name, name_tw):
    n = (name_en or "").strip() or (name or "").strip() or (name_tw or "").strip()
    n = re.sub(r"[-_]+", " ", n).strip()
    words = [w.capitalize() if (w.isupper() and len(w) > 4) else w for w in n.split()]
    return " ".join(words)


def probe(probe_bin, cat, sub, path):
    try:
        r = subprocess.run([probe_bin, str(cat), str(sub), str(path)], capture_output=True, text=True,
                           timeout=60, env={"ASAN_OPTIONS": "detect_leaks=0"})
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
        """select r.remote_map, r.category_id, r.sub_cate, b.name_en, b.name, b.name_tw
           from remote_index r join brand b on b.id = r.brand_id
           where r.category_id > 1 and r.status = 1 and r.brand_id > 0
           order by r.category_id, r.brand_id, coalesce(r.priority, 999), r.id"""
    ).fetchall()

    jobs = []
    for remote_map, cat, sub, name_en, name, name_tw in rows:
        brand = display_brand(name_en, name, name_tw)
        if cat not in CATEGORY or not brand or not brand.isascii():
            continue
        path = bins / f"irda_{remote_map}.bin"
        if path.exists():
            jobs.append((remote_map, cat, sub or 1, brand, path))

    with ThreadPoolExecutor(max_workers=8) as pool:
        results = list(pool.map(lambda j: probe(probe_bin, j[1], j[2], j[4]), jobs))

    if OUT_BIN.exists():
        shutil.rmtree(OUT_BIN)
    OUT_BIN.mkdir(parents=True)

    index, skipped = [], []
    file_of_content = {}       # binary sha1 -> stored file name
    seen_remote = set()        # (category, brand, key-hash signature): same remote listed twice
    for (remote_map, cat, sub, brand, path), (res, err) in zip(jobs, results):
        if err or not res or not res.get("ok"):
            skipped.append((remote_map, err or "?"))
            continue
        # Keys a remote lacks decode to one filler code; real remotes may share a code between two
        # keys (D-pad left == volume down), so only a code used by 3+ keys counts as filler.
        uses = {}
        for _, _, _, h in res["keys"]:
            uses[h] = uses.get(h, 0) + 1
        keys = [(key, h) for key, n, dur, h in res["keys"] if dur <= MAX_US and (uses[h] < 3 or key == 0)]
        if not keys:
            skipped.append((remote_map, "no keys"))
            continue
        app_cat = CATEGORY[cat]
        bkey = re.sub(r"[^0-9a-z]", "", brand.lower())
        sig = (app_cat, bkey, tuple(keys))
        if sig in seen_remote:
            continue
        seen_remote.add(sig)

        data = path.read_bytes()
        file_id = file_of_content.setdefault(hashlib.sha1(data).hexdigest(), remote_map)
        if file_id == remote_map:
            (OUT_BIN / f"{remote_map}.bin").write_bytes(data)

        test_key, test_hash = next(((k, h) for k, h in keys if k == 0), keys[0])
        index.append({
            "id": f"ix_{bkey}_{remote_map}",
            "cat": app_cat,
            "brand": brand,
            "src": "irext",
            "test": "power" if test_key == 0 else "",
            "testLabel": "",  # not power -> the first listed key is tested
            "g": f"ix{test_hash:x}",
            "n": len(keys),
            "file": file_id,
            "sub": sub,
            "ic": cat,
            "k": [k for k, _ in keys],
        })

    ids = [e["id"] for e in index]
    assert len(ids) == len(set(ids)), "duplicate ids"
    OUT_INDEX.write_text(json.dumps(index, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    by_cat = {}
    for e in index:
        by_cat[e["cat"]] = by_cat.get(e["cat"], 0) + 1
    files = len(list(OUT_BIN.glob("*.bin")))
    size = sum(p.stat().st_size for p in OUT_BIN.glob("*.bin"))
    print(f"wrote {len(index)} remotes ({files} binaries, {size // 1024} KB): {by_cat}")
    print(f"skipped {len(skipped)}: {skipped[:8]}")


if __name__ == "__main__":
    main()
