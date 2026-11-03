"""Extra AC code sets for brands the big databases miss (currently Saijo Denki).

Writes SmartIR-style tables with raw signed timings (+mark / -space, microseconds) to
app/src/main/assets/ac_extra/ so the app plays them like any SmartIR remote.

Sources
  * AirBridge (MIT, (c) 2016-2025 Werapol Bejranonda, Pathawee Somsak)
    https://github.com/bejranonda/AirBridge  airbridge/ir_protocols.h + ir_remote.cpp
    Thai-market AC protocol templates. saijo_64 / saijo_128 are encoded exactly like
    IRRemote::send_ir -> get_checksum -> bit_to_ps_then_send.
  * cpe_air_remote, https://github.com/Rapeepat29/cpe_air_remote
    firmware/rmutt_esp32_air/SaijoDenkiAC.cpp: frames captured from a real Saijo Denki
    remote (72 bit, LSB first). The repository has no license file; captured IR codes are
    functional data, but check before redistributing the app.

usage: python3 tools/build_extra.py
"""
import json
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "ac_extra"

# --- AirBridge templates (verbatim from ir_protocols.h, remote_0801 / remote_0802) ---------
SAIJO_64 = "saijo_64,CDF=MSB,CDL=64,APSCNT=3,APSP=[0][32][32],APL=[9100][600][9100],ASL=[4400][31300][4400],BPL=600,OSL=1650,ZSL=500,CHT=none,CHP=0,ACH=0,CHL=0,PWP=8,PWL=1,ON=1,OFF=0,MDP=13,MDL=3,AUTO=100,COOL=010,DRY=000,TMPP=16,TMPL=8,T30=10111100,T29=10111010,T28=10111000,T27=10110110,T26=10110100,T25=10110010,T24=10110000,T23=10101110,T22=10101100,T21=10101010,T20=10101000,T19=10100110,T18=10100100,T17=10100010,T16=10100000,T15=10011110,FNP=9,FNL=3,FA=000,F1=011,F2=010,F3=001,VNP=12,VNL=1,VA=0,VR=1,PFB=0110011010011110101100110100100010011001011000010100110010110111,"
SAIJO_128 = "saijo_128,CDF=MSB,CDL=128,APSCNT=7,APSP=[0][32][32][64][64][96][96],APL=[9300][660][9300][660][9300][660][9300],ASL=[4480][40000][4480][60000][4480][40000][4480],BPL=630,OSL=1680,ZSL=600,CHT=none,CHP=0,ACH=0,CHL=0,PWP=72,PWL=1,ON=1,OFF=0,MDP=88,MDL=8,COOL=00000000,TMPP=80,TMPL=8,T30=10111100,T29=10111010,T28=10111000,T27=10110110,T26=10110100,T25=10110010,T24=10110000,T23=10101110,T22=10101100,T21=10101010,T20=10101000,T19=10100110,T18=10100100,T17=10100010,T16=10100000,T15=10011110,FNP=73,FNL=3,FA=000,F3=011,F2=010,F1=001,VNP=76,VNL=2,VA=00,VR=10,PFB=01100110100110101011110000000000100110010110010101000011111111110101010110011011101111000000000010101010011001000100001111111111,"


def parse(t):
    d = {}
    for part in t.split(",")[1:]:
        if "=" in part:
            k, v = part.split("=", 1)
            d[k] = v
    return d


def nib_not(b):  # get_F_minus
    return "".join("1" if c == "0" else "0" for c in b)


def put(bits, pos, val):
    return bits[:pos] + val + bits[pos + len(val):]


def checksum(name, b):
    if name == "saijo_64":
        b = b[:40]
        for i in range(2, 6):
            b += nib_not(b[i * 4:i * 4 + 4])
        return b + "10110111"
    if name == "saijo_128":
        return (b[0:8] + b[72:79] + "0" + b[80:88] + b[24:40]
                + nib_not(b[72:76]) + nib_not(b[76:79] + "0") + nib_not(b[80:84]) + nib_not(b[84:88])
                + b[56:104]
                + nib_not(b[72:76]) + nib_not(b[76:80]) + nib_not(b[80:84]) + nib_not(b[84:88]) + "11111111")
    raise ValueError(name)


def brackets(s):
    return [int(x) for x in s.strip("[]").split("][")] if s else []


def airbridge_frame(tpl, power, mode_bits, temp, fan_bits, vane_bits):
    name = tpl.split(",")[0]
    d = parse(tpl)
    b = d["PFB"]
    b = put(b, int(d["PWP"]), d["ON"] if power else d["OFF"])
    b = put(b, int(d["MDP"]), mode_bits)
    b = put(b, int(d["TMPP"]), d[f"T{temp}"])
    b = put(b, int(d["FNP"]), fan_bits)
    b = put(b, int(d["VNP"]), vane_bits)
    b = checksum(name, b)
    assert len(b) == int(d["CDL"]), (name, len(b))
    pos, apl, asl = brackets(d["APSP"]), brackets(d["APL"]), brackets(d["ASL"])
    bm, one, zero = int(d["BPL"]), int(d["OSL"]), int(d["ZSL"])
    out = []
    for i, bit in enumerate(b):
        while pos and pos[0] == i:
            # bit_to_ps_then_send trims extra marks by 100 µs and stretches extra spaces by 80 µs
            out += [apl.pop(0) - 100, -(asl.pop(0) + 80)]
            pos.pop(0)
        out += [bm, -(one if bit == "1" else zero)]
    out.append(bm)
    return out


def airbridge_remote(tpl, model):
    d = parse(tpl)
    modes = {m: d[k] for m, k in (("auto", "AUTO"), ("cool", "COOL"), ("dry", "DRY")) if k in d}
    fans = {f: d[k] for f, k in (("auto", "FA"), ("low", "F1"), ("mid", "F2"), ("high", "F3")) if k in d}
    swings = {s: d[k] for s, k in (("swing", "VA"), ("fixed", "VR")) if k in d}
    temps = range(16, 31)
    commands = {
        mode: {fan: {sw: {str(t): airbridge_frame(tpl, 1, mb, t, fb, vb) for t in temps}
                     for sw, vb in swings.items()}
               for fan, fb in fans.items()}
        for mode, mb in modes.items()
    }
    off = airbridge_frame(tpl, 0, modes.get("cool", next(iter(modes.values()))), 25, fans["auto"], next(iter(swings.values())))
    return {
        "manufacturer": "Saijo Denki", "supportedModels": [model], "supportedController": "Raw",
        "commandsEncoding": "Raw", "minTemperature": 16, "maxTemperature": 30, "precision": 1,
        "operationModes": list(modes), "fanModes": list(fans), "swingModes": list(swings),
        "commands": {"off": off, **commands},
    }


# --- cpe_air_remote captures ------------------------------------------------------------------
CPE_TIMING = dict(hm=9050, hs=4550, bm=650, one=1670, zero=550)
CPE = {
    "off": [0x05, 0x00, 0x2D, 0x80, 0x54, 0x91, 0x54, 0xFF, 0xAD],
    "on": [0x05, 0x09, 0x2D, 0x80, 0x94, 0x91, 0x94, 0xFF, 0xC4],
    "dry": [0x05, 0x09, 0x95, 0xC0, 0x20, 0xD1, 0x22, 0x00, 0x25],
    "fan_only": [0x05, 0x09, 0x95, 0xC0, 0x22, 0xD1, 0x20, 0x00, 0x0D],
    18: [0x05, 0x09, 0x25, 0x80, 0x78, 0x95, 0x78, 0xFF, 0x65],
    19: [0x05, 0x09, 0x65, 0x80, 0x78, 0x95, 0x78, 0xFF, 0x05],
    20: [0x05, 0x09, 0x15, 0x80, 0x78, 0x95, 0x78, 0xFF, 0x55],
    21: [0x05, 0x09, 0x55, 0x80, 0x78, 0x95, 0x78, 0xFF, 0x35],
    22: [0x05, 0x09, 0x35, 0x80, 0x78, 0x95, 0x78, 0xFF, 0x55],
    23: [0x05, 0x09, 0x75, 0x80, 0x78, 0x95, 0x78, 0xFF, 0x35],
    24: [0x05, 0x09, 0x8D, 0x80, 0xB8, 0x95, 0xB8, 0xFF, 0xE9],
    25: [0x05, 0x09, 0x4D, 0x80, 0xB8, 0x95, 0xB8, 0xFF, 0x69],
    26: [0x05, 0x09, 0x2D, 0x80, 0xB8, 0x95, 0xB8, 0xFF, 0x29],
    27: [0x05, 0x09, 0x6D, 0x80, 0xB8, 0x95, 0xB8, 0xFF, 0x69],
    28: [0x05, 0x09, 0x1D, 0x80, 0xB8, 0x95, 0xB8, 0xFF, 0x09],
    29: [0x05, 0x09, 0x5D, 0x80, 0xB8, 0x95, 0xB8, 0xFF, 0x59],
}


def cpe_frame(data):
    t = CPE_TIMING
    out = [t["hm"], -t["hs"]]
    for byte in data:
        for i in range(8):  # LSB first
            out += [t["bm"], -(t["one"] if byte >> i & 1 else t["zero"])]
    out.append(t["bm"])
    return out


def cpe_remote():
    return {
        "manufacturer": "Saijo Denki", "supportedModels": ["72-bit (captured remote)"], "supportedController": "Raw",
        "commandsEncoding": "Raw", "minTemperature": 18, "maxTemperature": 29, "precision": 1,
        "operationModes": ["cool", "dry", "fan_only"], "fanModes": ["auto"], "swingModes": [],
        "commands": {
            "off": cpe_frame(CPE["off"]), "on": cpe_frame(CPE["on"]),
            "cool": {"auto": {str(t): cpe_frame(CPE[t]) for t in range(18, 30)}},
            "dry": {"auto": cpe_frame(CPE["dry"])},
            "fan_only": {"auto": cpe_frame(CPE["fan_only"])},
        },
    }


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    remotes = {
        "saijo_72": (cpe_remote(), "github.com/Rapeepat29/cpe_air_remote"),
        "saijo_64": (airbridge_remote(SAIJO_64, "saijo_64 (AirBridge)"), "github.com/bejranonda/AirBridge"),
        "saijo_128": (airbridge_remote(SAIJO_128, "saijo_128 (AirBridge)"), "github.com/bejranonda/AirBridge"),
    }
    index = []
    for name, (r, origin) in remotes.items():
        cmds = r["commands"]
        device = {
            "id": f"extra:{name}", "manufacturer": r["manufacturer"], "models": r["supportedModels"],
            "encoding": "raw", "minTemp": r["minTemperature"], "maxTemp": r["maxTemperature"],
            "precision": 1, "modes": r["operationModes"], "fanModes": r["fanModes"], "swingModes": r["swingModes"],
            "off": cmds["off"], **({"on": cmds["on"]} if "on" in cmds else {}),
            "commands": {k: v for k, v in cmds.items() if k not in ("off", "on")},
        }
        (OUT / f"{name}.json").write_text(json.dumps(device, separators=(",", ":")))
        index.append({k: device[k] for k in ("id", "manufacturer", "models", "encoding", "minTemp", "maxTemp",
                                              "precision", "modes", "fanModes", "swingModes")}
                     | {"source": "github", "file": name, "origin": origin})
        print(name, "frame us:", sum(abs(v) for v in device["off"]), "pulses:", len(device["off"]))
    (OUT / "index.json").write_text(json.dumps(index, separators=(",", ":")))


if __name__ == "__main__":
    main()
