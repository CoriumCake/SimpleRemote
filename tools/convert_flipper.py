"""Convert Flipper-IRDB (CC0, https://github.com/Lucaslhm/Flipper-IRDB) into the app's
generic device remotes: assets/ir/index.json + assets/ir/r/<id>.json.

Each remote file: {"b": [[label, key, signal], ...]}
  key     canonical button ("power", "vol_up", "1", ...) or "" for extras
  signal  ["NECext", address, command]  (parsed, encoded at runtime by IrProtocols.kt)
          [frequency, [mark, space, ...]] (raw µs)

usage: python3 -I tools/convert_flipper.py <Flipper-IRDB checkout>
"""
import hashlib
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app" / "src" / "main" / "assets" / "ir"

# Flipper folder -> app category (AC is handled by the dedicated AC databases)
CATEGORY = {
    "TVs": "tv", "Monitors": "tv", "Touchscreen_Displays": "tv", "Digital_Signs": "tv",
    "Cable_Boxes": "stb", "DVB-T": "stb", "TV_Tuner": "stb", "Converters": "stb",
    "Streaming_Devices": "streaming", "Multimedia": "streaming", "Consoles": "streaming", "Computers": "streaming",
    "Projectors": "projector",
    "Fans": "fan",
    "SoundBars": "audio", "Speakers": "audio", "Audio_and_Video_Receivers": "audio", "CD_Players": "audio",
    "MiniDisc": "audio", "Car_Multimedia": "audio", "Head_Units": "audio",
    "Blu-Ray": "disc", "DVD_Players": "disc", "VCR": "disc", "Laserdisc": "disc",
    "LED_Lighting": "light",
    "Cameras": "camera", "CCTV": "camera",
    "Air_Purifiers": "air_purifier", "Humidifiers": "air_purifier", "Dust_Collectors": "air_purifier",
    "Heaters": "heater", "Fireplaces": "heater",
    "Vacuum_Cleaners": "vacuum", "Window_cleaners": "vacuum",
    "Toys": "other", "Clocks": "other", "KVM": "other", "Bidet": "other", "Picture_Frames": "other",
    "Videoconferencing": "other", "Whiteboards": "other", "Miscellaneous": "other",
    "Handicap_Ceiling_Lifts": "other",
}
PROTOCOLS = {"NEC", "NECext", "NEC42", "NEC42ext", "Samsung32", "RC5", "RC5X", "RC6", "SIRC", "SIRC15",
             "SIRC20", "Kaseikyo", "RCA", "Pioneer"}
SUPPORTED_MAX_HZ = 60_000

# canonical key -> accepted spellings after norm() (lowercase, + -> "plus", - -> "minus", no separators)
ALIASES = {
    "power": ["power", "pwr", "powertoggle", "onoff", "poweronoff", "standby", "powerbutton", "pow", "power1"],
    "power_on": ["poweron", "on", "pon", "discreteon", "turnon"],
    "power_off": ["poweroff", "off", "poff", "discreteoff", "turnoff"],
    "vol_up": ["volplus", "volup", "volumeplus", "volumeup", "vplus", "volu", "volumeupplus", "voladd", "volinc"],
    "vol_down": ["volminus", "voldown", "voldn", "volumeminus", "volumedown", "vminus", "vold", "voldec"],
    "mute": ["mute", "muting", "audiomute", "volmute"],
    "ch_up": ["chplus", "chup", "channelplus", "channelup", "progplus", "pplus", "prplus", "chnext", "cup"],
    "ch_down": ["chminus", "chdown", "chdn", "channelminus", "channeldown", "progminus", "pminus", "prminus",
                "chprev", "cdown"],
    "up": ["up", "arrowup", "cursorup", "navup", "dpadup", "upkey"],
    "down": ["down", "arrowdown", "cursordown", "navdown", "dpaddown", "downkey", "dn"],
    "left": ["left", "arrowleft", "cursorleft", "navleft", "dpadleft", "leftkey"],
    "right": ["right", "arrowright", "cursorright", "navright", "dpadright", "rightkey"],
    "ok": ["ok", "enter", "select", "confirm", "okenter", "center"],
    "menu": ["menu", "setup", "settings", "mainmenu"],
    "back": ["back", "return", "ret", "backspace"],
    "exit": ["exit", "quit", "close"],
    "home": ["home", "smarthub", "homemenu"],
    "input": ["input", "source", "src", "av", "tvav", "inputs", "inputselect", "sourceinput", "avtv"],
    "info": ["info", "display", "disp", "information"],
    "guide": ["guide", "epg"],
    "play": ["play"],
    "pause": ["pause"],
    "play_pause": ["playpause", "playstop"],
    "stop": ["stop"],
    "ff": ["ff", "fastforward", "fwd", "forward", "ffwd", "fastfwd"],
    "rew": ["rew", "rewind", "rw", "fastrewind", "fr"],
    "next": ["next", "skipplus", "nexttrack", "skipnext", "skipfwd"],
    "prev": ["prev", "previous", "skipminus", "prevtrack", "skipprev", "skipback"],
    "record": ["record", "rec"],
    "speed": ["speed", "fanspeed", "wind", "windspeed", "fan"],
    "speed_up": ["speedplus", "speedup", "fanplus", "fanup"],
    "speed_down": ["speedminus", "speeddown", "fanminus", "fandown"],
    "oscillate": ["oscillate", "osc", "oscillation", "swing", "rotate", "rotation", "turn", "swivel"],
    "timer": ["timer", "time", "sleep", "sleeptimer"],
    "mode": ["mode", "modes", "windmode", "naturalwind", "wind type"],
    "light": ["light", "lamp", "led", "lights"],
    "bright_up": ["brightplus", "brightnessplus", "brightnessup", "brightup", "dimplus", "lighter"],
    "bright_down": ["brightminus", "brightnessminus", "brightnessdown", "brightdown", "dimminus", "dim", "darker"],
    "temp_up": ["tempplus", "tempup", "temperatureup", "heatup"],
    "temp_down": ["tempminus", "tempdown", "temperaturedown", "heatdown"],
}
for d in "0123456789":
    ALIASES[d] = [d, f"num{d}", f"key{d}", f"digit{d}", f"btn{d}", f"number{d}"]
KEY_OF = {spelling: key for key, spellings in ALIASES.items() for spelling in spellings}
TEST_ORDER = ["power", "power_off", "power_on", "mute", "vol_up", "play", "light", "speed", "ok"]


def norm(name):
    s = name.lower().strip().replace("+", "plus").replace("-", "minus")
    return re.sub(r"[^a-z0-9]", "", s)


def parse_file(path):
    sigs, cur = [], {}
    for line in path.read_text(errors="replace").splitlines():
        line = line.strip()
        if line.startswith("#"):
            if cur:
                sigs.append(cur)
            cur = {}
            continue
        if ":" in line:
            k, v = line.split(":", 1)
            cur[k.strip()] = v.strip()
    if cur:
        sigs.append(cur)
    return [s for s in sigs if "name" in s and "type" in s]


def le_int(hexbytes):
    return int.from_bytes(bytes(int(b, 16) for b in hexbytes.split()), "little")


def to_signal(s):
    if s["type"] == "parsed":
        if s.get("protocol") not in PROTOCOLS:
            return None
        try:
            return [s["protocol"], le_int(s["address"]), le_int(s["command"])]
        except (ValueError, KeyError):
            return None
    if s["type"] == "raw":
        try:
            freq = int(float(s.get("frequency", "38000")))
            data = [int(x) for x in s["data"].split()]
        except (ValueError, KeyError):
            return None
        if not data or freq > SUPPORTED_MAX_HZ or freq < 20_000:
            return None
        if len(data) % 2 == 0:
            data = data[:-1]  # trailing gap
        if sum(data) > 1_900_000 or any(v <= 0 for v in data):
            return None
        return [freq, data]
    return None


def group_key(sig):
    if isinstance(sig[0], str):
        return f"{sig[0]}:{sig[1]}:{sig[2]}"
    return "raw:" + ",".join(str(round(v / 150)) for v in sig[1][:80])


def clean_model(stem, brand):
    m = stem
    for p in (brand, brand.replace(" ", "_"), brand.replace(" ", "")):
        if m.lower().startswith(p.lower()):
            m = m[len(p):]
            break
    m = re.sub(r"[_]+", " ", m).strip(" -_")
    return m or stem.replace("_", " ")


def convert_folder(root, folder_names_to_cat):
    remotes = []
    for folder, cat in folder_names_to_cat.items():
        base = root / folder
        if not base.is_dir():
            continue
        for path in sorted(base.rglob("*.ir")):
            rel = path.relative_to(base).parts
            brand = rel[0] if len(rel) > 1 else re.split(r"[_ ]", path.stem)[0]
            brand = brand.replace("_", " ").strip()
            buttons, used_keys = [], set()
            for s in parse_file(path):
                sig = to_signal(s)
                if sig is None:
                    continue
                label = s["name"].replace("_", " ").strip()
                key = KEY_OF.get(norm(s["name"]), "")
                if key in used_keys:
                    key = ""  # second copy of the same function stays available as an extra
                used_keys.add(key)
                buttons.append([label, key, sig])
            if not buttons:
                continue
            remotes.append({"cat": cat, "brand": brand, "model": clean_model(path.stem, brand),
                            "buttons": buttons, "folder": folder})
    return remotes


def main():
    root = Path(sys.argv[1])
    remotes = convert_folder(root, CATEGORY)

    # unify brand spellings ("SAMSUNG", "Samsung") to the most common one
    spell = defaultdict(Counter)
    for r in remotes:
        spell[norm(r["brand"])][r["brand"]] += 1
    for r in remotes:
        r["brand"] = spell[norm(r["brand"])].most_common(1)[0][0]

    (OUT / "r").mkdir(parents=True, exist_ok=True)
    for old in (OUT / "r").glob("fl_*.json"):
        old.unlink()
    index, seen = [], set()
    for r in remotes:
        payload = json.dumps({"b": r["buttons"]}, separators=(",", ":"))
        digest = hashlib.md5((r["cat"] + payload).encode()).hexdigest()[:12]
        if digest in seen:
            continue
        seen.add(digest)
        rid = f"fl_{digest}"
        (OUT / "r" / f"{rid}.json").write_text(payload)
        keyed = {b[1]: b[2] for b in r["buttons"] if b[1]}
        test_key = next((k for k in TEST_ORDER if k in keyed), None)
        test_sig = keyed[test_key] if test_key else r["buttons"][0][2]
        index.append({
            "id": rid, "cat": r["cat"], "brand": r["brand"], "model": r["model"], "src": "flipper",
            "test": test_key or "", "testLabel": "" if test_key else r["buttons"][0][0],
            "g": hashlib.md5(group_key(test_sig).encode()).hexdigest()[:10],
            "n": len(r["buttons"]),
        })
    index.sort(key=lambda e: (e["cat"], e["brand"].lower(), -e["n"]))
    (OUT / "flipper_index.json").write_text(json.dumps(index, separators=(",", ":"), ensure_ascii=False))
    by_cat = Counter(e["cat"] for e in index)
    print(f"{len(index)} remotes, {len({(e['cat'], e['brand']) for e in index})} brand/category pairs:", dict(by_cat))
    unmatched = Counter(norm(b[0]) for r in remotes for b in r["buttons"] if not b[1])
    print("top extras:", unmatched.most_common(40))


if __name__ == "__main__":
    main()
