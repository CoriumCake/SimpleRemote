// Host-side probe for the IRremoteESP8266 bridge (app/src/main/cpp/esp_core.cpp).
//
// For every protocol the common A/C API supports, and every remote model of it, prints one
// JSON line describing what the protocol really supports (modes / temperatures / fans /
// swing whose frames differ), the power-on frame, and whether IRrecv decodes that frame
// back to the same protocol (round-trip self-check).
//
// build: see tools/convert_esp.py
#include <cstdio>
#include <cstring>
#include <map>
#include <string>
#include <vector>
#include "esp_core.h"
#include "IRrecv.h"
#include "IRutils.h"

static const int MAXN = 4096;

// Remote model enums (IRsend.h) that change the encoding; -1 = protocol has no models.
static std::map<std::string, std::vector<int>> kModels = {
    {"FUJITSU_AC", {1, 2, 3, 4, 5, 6}}, {"GREE", {1, 2, 3}}, {"MIRAGE", {1, 2}},
    {"PANASONIC_AC", {1, 2, 3, 4, 5, 6}}, {"SHARP_AC", {1, 2, 3}}, {"TCL112AC", {1, 2}},
    {"VOLTAS", {1}}, {"WHIRLPOOL_AC", {1, 2}}, {"LG", {1, 5}}, {"LG2", {2, 3, 4}},
    {"ARGO", {1, 2}}, {"TOSHIBA_AC", {0, 1}}, {"HAIER_AC176", {1, 2}},
    {"HITACHI_AC1", {1, 2}},
};

static const char *kModeNames[] = {"auto", "cool", "heat", "dry", "fan_only"};
static const int kModeVals[] = {0, 1, 2, 3, 4};
static const char *kFanNames[] = {"auto", "low", "mid", "high"};
static const int kFanVals[] = {0, 2, 3, 4};

struct Frame { std::vector<int> t; int freq = 0; };

static bool enc(int p, int model, int power, int mode, int temp, int fan, int swing, Frame *f,
                int prev_power = 0, int prev_swing = -2) {
  static int buf[MAXN];
  int freq = 0;
  int n = esp_encode(p, model, power, mode, temp, fan, swing, prev_power,
                     prev_swing == -2 ? swing : prev_swing, buf, MAXN, &freq);
  if (n <= 0) return false;
  f->t.assign(buf, buf + n);
  f->freq = freq;
  return true;
}

static std::string decodeName(const Frame &f) {
  static uint16_t raw[MAXN + 2];
  decode_results r;
  memset(&r, 0, sizeof r);
  r.decode_type = UNKNOWN;
  raw[0] = 0;
  int n = 0;
  for (int v : f.t) {
    int a = v < 0 ? -v : v;
    raw[++n] = (a / kRawTick > 65535) ? 65535 : a / kRawTick;
  }
  raw[++n] = 60000 / kRawTick;  // closing gap
  r.rawbuf = raw;
  r.rawlen = n + 1;
  IRrecv rx(0, MAXN + 2);
  if (!rx.decode(&r)) return "";
  return typeToString(r.decode_type);
}

int main() {
  char name[64];
  for (int p = 0; p <= esp_last_protocol(); p++) {
    if (!esp_is_supported(p)) continue;
    esp_protocol_name(p, name, sizeof name);
    std::vector<int> models = kModels.count(name) ? kModels[name] : std::vector<int>{-1};
    for (int model : models) {
      Frame base;
      bool ok = enc(p, model, 1, 1, 24, 0, -1, &base);
      printf("{\"protocol\":%d,\"name\":\"%s\",\"model\":%d,\"ok\":%s", p, name, model, ok ? "true" : "false");
      if (!ok) { printf("}\n"); continue; }
      long us = 0;
      for (int v : base.t) us += v < 0 ? -v : v;
      Frame off;
      bool has_off = enc(p, model, 0, 1, 24, 0, -1, &off, 1);
      printf(",\"freq\":%d,\"us\":%ld,\"n\":%zu,\"decoded\":\"%s\",\"off\":%s", base.freq, us,
             base.t.size(), decodeName(base).c_str(), has_off && off.t != base.t ? "true" : "false");
      long max_us = us;
      // Capabilities: a setting is supported when changing it changes the frame.
      printf(",\"caps\":{");
      bool first_mode = true;
      for (int mi = 0; mi < 5; mi++) {
        Frame fm;
        if (!enc(p, model, 1, kModeVals[mi], 24, 0, -1, &fm)) continue;
        if (kModeVals[mi] != 1 && fm.t == base.t) continue;  // same as cool -> not a real mode
        // temperatures: group 16..30 by identical frames
        std::vector<Frame> ft(31);
        std::vector<int> temps;
        for (int t = 16; t <= 30; t++) enc(p, model, 1, kModeVals[mi], t, 0, -1, &ft[t]);
        for (int t = 16; t <= 30; t++) {
          if (ft[t].t.empty()) continue;
          long d = 0; for (int v : ft[t].t) d += v < 0 ? -v : v;
          if (d > max_us) max_us = d;
          int lo = t, hi = t;
          while (lo > 16 && ft[lo - 1].t == ft[t].t) lo--;
          while (hi < 30 && ft[hi + 1].t == ft[t].t) hi++;
          if (lo == 16 && hi == 30) break;  // temperature ignored in this mode
          int pick = (lo <= 24) ? hi : lo;
          if (pick == t) temps.push_back(t);
        }
        std::vector<std::string> fans;
        Frame f0;
        enc(p, model, 1, kModeVals[mi], 24, 0, -1, &f0);
        for (int fi = 0; fi < 4; fi++) {
          Frame ff;
          if (!enc(p, model, 1, kModeVals[mi], 24, kFanVals[fi], -1, &ff)) continue;
          if (fi > 0 && ff.t == f0.t) continue;
          bool dup = false;
          for (int fj = 1; fj < fi; fj++) {
            Frame fo; enc(p, model, 1, kModeVals[mi], 24, kFanVals[fj], -1, &fo);
            if (fo.t == ff.t) dup = true;
          }
          if (!dup) fans.push_back(kFanNames[fi]);
        }
        if (fans.size() == 1) fans.clear();
        Frame fs;
        bool swing = enc(p, model, 1, kModeVals[mi], 24, 0, 0, &fs, 1, -1) && fs.t != f0.t;
        printf("%s\"%s\":{\"temps\":[", first_mode ? "" : ",", kModeNames[mi]);
        for (size_t i = 0; i < temps.size(); i++) printf("%s%d", i ? "," : "", temps[i]);
        printf("],\"fans\":[");
        for (size_t i = 0; i < fans.size(); i++) printf("%s\"%s\"", i ? "," : "", fans[i].c_str());
        printf("],\"swing\":%s}", swing ? "true" : "false");
        first_mode = false;
      }
      printf("},\"max_us\":%ld,\"frame\":[", max_us);
      for (size_t i = 0; i < base.t.size(); i++) printf("%s%d", i ? "," : "", base.t[i] < 0 ? -base.t[i] : base.t[i]);
      printf("]}\n");
    }
  }
  return 0;
}
