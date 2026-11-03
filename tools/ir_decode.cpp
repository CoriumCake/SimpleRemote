// Decodes IR frames with IRremoteESP8266's IRrecv — an independent check of IrProtocols.kt.
// stdin:  one frame per line: "<tag> <t1> <t2> ..." (µs, alternating mark/space)
// stdout: "<tag> <PROTOCOL> <bits> <value hex> <address hex> <command hex>"
// build: like tools/esp_probe.cpp (see tools/convert_esp.py), replacing esp_probe.cpp/esp_core.cpp
#include <cstdio>
#include <iostream>
#include <sstream>
#include <string>
#include <vector>
#include "IRrecv.h"
#include "IRutils.h"

int main() {
  std::string line;
  static uint16_t raw[4100];
  while (std::getline(std::cin, line)) {
    std::istringstream in(line);
    std::string tag;
    in >> tag;
    int n = 0, v;
    raw[0] = 0;
    while (in >> v && n < 4096) raw[++n] = (v / kRawTick > 65535) ? 65535 : v / kRawTick;
    raw[++n] = 60000 / kRawTick;
    decode_results r = {};
    r.decode_type = UNKNOWN;
    r.rawbuf = raw;
    r.rawlen = n + 1;
    IRrecv rx(0, 4100);
    bool ok = rx.decode(&r);
    printf("%s %s %d %llx %x %x\n", tag.c_str(), ok ? typeToString(r.decode_type).c_str() : "NONE", r.bits,
           static_cast<unsigned long long>(r.value), r.address, r.command);
  }
  return 0;
}
