// Turns an AC state into IR timings with IRremoteESP8266's common A/C API (IRac).
// Shared by the JNI bridge (esp_jni.cpp) and the host probe (tools/esp_probe.cpp).
//
// The library is built with UNIT_TEST + SWIGLIB, so IRsend::mark()/space() append to the
// global timingList instead of driving an LED (patched to store spaces as negative values
// and to remember the carrier in espCaptureFreq).
#include <vector>
#include "esp_core.h"
#include "IRac.h"
#include "IRutils.h"

extern std::vector<int> timingList;
extern uint32_t espCaptureFreq;

int esp_is_supported(int protocol) {
  return IRac::isProtocolSupported(static_cast<decode_type_t>(protocol)) ? 1 : 0;
}

int esp_protocol_name(int protocol, char *out, int max) {
  String s = typeToString(static_cast<decode_type_t>(protocol));
  int n = 0;
  for (; n < max - 1 && n < static_cast<int>(s.size()); n++) out[n] = s[n];
  out[n] = 0;
  return n;
}

int esp_last_protocol(void) { return kLastDecodeType; }

int esp_encode(int protocol, int model, int power, int mode, int temp, int fan, int swingv,
               int prev_power, int prev_swingv, int *out, int max, int *freq) {
  stdAc::state_t s;
  s.protocol = static_cast<decode_type_t>(protocol);
  s.model = static_cast<int16_t>(model);
  s.power = power != 0;
  s.mode = static_cast<stdAc::opmode_t>(mode);
  s.degrees = static_cast<float>(temp);
  s.celsius = true;
  s.fanspeed = static_cast<stdAc::fanspeed_t>(fan);
  s.swingv = static_cast<stdAc::swingv_t>(swingv);
  s.swingh = stdAc::swingh_t::kOff;
  s.beep = true;
  // The previous state lets IRac send toggle bits (power on Daikin128, swing on Coolix/Midea…)
  // only when that setting actually changed.
  stdAc::state_t prev = s;
  prev.power = prev_power != 0;
  prev.swingv = static_cast<stdAc::swingv_t>(prev_swingv);

  timingList.clear();
  espCaptureFreq = 38000;
  IRac ac(0);
  if (!ac.sendAc(s, &prev)) return -1;

  // Merge consecutive marks/spaces and drop zero-length entries; output starts with a mark.
  int n = 0;
  for (int v : timingList) {
    if (v == 0) continue;
    if (n == 0 && v < 0) continue;
    if (n > 0 && ((out[n - 1] > 0) == (v > 0))) { out[n - 1] += v; continue; }
    if (n >= max) return -2;
    out[n++] = v;
  }
  if (n > 0 && out[n - 1] < 0) n--;  // trailing gap is not needed
  *freq = static_cast<int>(espCaptureFreq);
  return n;
}
