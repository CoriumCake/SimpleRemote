/*
 * Host-side probe for IRext non-AC binaries (TV, STB, fan, ...). Build-time only, not shipped.
 *
 * Decodes every key of one binary with the same C decoder the app ships, so
 * tools/convert_irext_devices.py keeps only binaries and keys that decode cleanly
 * (built with -fsanitize=address,undefined, a crash drops the whole binary).
 *
 * usage:  irext_device_probe <category> <sub_category> file.bin [key]
 *         with [key], prints that key's timings instead (for checking with tools/ir_decode)
 * output: {"ok":true,"keys":[[key, timings, duration_us, fnv1a_hash], ...]}
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "ir_decode.h"

int main(int argc, char **argv) {
    if (argc != 4 && argc != 5) { fprintf(stderr, "usage: %s category sub file.bin\n", argv[0]); return 2; }
    int cat = atoi(argv[1]), sub = atoi(argv[2]);
    FILE *f = fopen(argv[3], "rb");
    if (!f) { printf("{\"ok\":false,\"err\":\"open\"}\n"); return 1; }
    static UINT8 bin[65536];
    size_t len = fread(bin, 1, sizeof bin, f);
    fclose(f);

    static UINT16 out[USER_DATA_SIZE];
    t_remote_ac_status st;
    if (argc == 5) {
        memset(&st, 0, sizeof st);
        UINT16 n = ir_decode_combo((UINT8) cat, (UINT8) sub, bin, (UINT16) len, (UINT8) atoi(argv[4]), out, &st);
        if (n > USER_DATA_SIZE) n = 0;
        printf("%s:%s", argv[3], argv[4]);
        for (int i = 0; i < n; i++) printf(" %u", out[i]);
        printf("\n");
        return 0;
    }
    printf("{\"ok\":true,\"keys\":[");
    int first = 1;
    for (int key = 0; key < 24; key++) {
        memset(&st, 0, sizeof st);
        memset(out, 0, sizeof out);
        UINT16 n = ir_decode_combo((UINT8) cat, (UINT8) sub, bin, (UINT16) len, (UINT8) key, out, &st);
        if (n == 0 || n > USER_DATA_SIZE) continue;
        long dur = 0;
        int zero = 0;
        unsigned int h = 2166136261u;
        for (int i = 0; i < n; i++) {
            dur += out[i];
            if (out[i] == 0) zero = 1;
            /* hash rounded to 50 us so tiny decoder jitter does not split groups */
            unsigned int v = (out[i] + 25) / 50;
            h = (h ^ v) * 16777619u;
        }
        if (zero) continue;
        printf("%s[%d,%d,%ld,%u]", first ? "" : ",", key, n, dur, h);
        first = 0;
    }
    printf("]}\n");
    return 0;
}
