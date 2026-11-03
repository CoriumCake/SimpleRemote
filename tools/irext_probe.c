/*
 * Host-side probe for IRext AC binaries (build-time only, not shipped).
 *
 * Opens one .bin, prints its capabilities and decodes every state combination,
 * so tools/convert_irext.py can drop binaries that fail or crash before they reach the app.
 *
 * Output: one JSON object on stdout.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "ir_decode.h"

static int decode(UINT8 *bin, UINT16 len, UINT8 key, int power, int mode, int temp, int speed, int swing,
                  UINT16 *out) {
    t_remote_ac_status st;
    memset(&st, 0, sizeof st);
    st.ac_power = (t_ac_power) power;
    st.ac_mode = (t_ac_mode) mode;
    st.ac_temp = (t_ac_temperature) temp;
    st.ac_wind_speed = (t_ac_wind_speed) speed;
    st.ac_wind_dir = (t_ac_swing) swing;
    UINT16 n = ir_decode_combo(REMOTE_CATEGORY_AC, SUB_CATEGORY_QUATERNARY, bin, len, key, out, &st);
    return (n > USER_DATA_SIZE) ? 0 : n;
}

int main(int argc, char **argv) {
    if (argc != 2) { fprintf(stderr, "usage: %s file.bin\n", argv[0]); return 2; }
    FILE *f = fopen(argv[1], "rb");
    if (!f) { printf("{\"ok\":false,\"err\":\"open\"}\n"); return 1; }
    static UINT8 bin[65536];
    size_t len = fread(bin, 1, sizeof bin, f);
    fclose(f);

    if (ir_binary_open(REMOTE_CATEGORY_AC, SUB_CATEGORY_QUATERNARY, bin, (UINT16) len) != IR_DECODE_SUCCEEDED) {
        printf("{\"ok\":false,\"err\":\"parse\"}\n");
        return 1;
    }
    UINT8 modes = 0;
    get_supported_mode(&modes);
    INT8 tmin[AC_MODE_MAX], tmax[AC_MODE_MAX];
    UINT8 speeds[AC_MODE_MAX], swings[AC_MODE_MAX];
    for (int m = 0; m < AC_MODE_MAX; m++) {
        tmin[m] = tmax[m] = -1; speeds[m] = swings[m] = 0;
        if (!(modes & (1 << m))) continue;
        get_temperature_range((UINT8) m, &tmin[m], &tmax[m]);
        get_supported_wind_speed((UINT8) m, &speeds[m]);
        get_supported_swing((UINT8) m, &swings[m]);
    }
    ir_close();

    static UINT16 a[USER_DATA_SIZE], b[USER_DATA_SIZE];
    long total = 0, failed = 0;
    int minLen = 1 << 30, maxLen = 0, swingDiffers = -1;
    long durMax = 0;
    int leader = -1;

    int offLen = decode(bin, (UINT16) len, KEY_AC_POWER, AC_POWER_OFF, AC_MODE_COOL, AC_TEMP_24, AC_WS_AUTO, AC_SWING_OFF, a);

    for (int m = 0; m < AC_MODE_MAX; m++) {
        if (!(modes & (1 << m))) continue;
        int t0 = tmin[m] < 0 ? AC_TEMP_24 : tmin[m], t1 = tmax[m] < 0 ? AC_TEMP_24 : tmax[m];
        for (int t = t0; t <= t1; t++)
            for (int s = 0; s < AC_WS_MAX; s++) {
                if (speeds[m] != 0 && !(speeds[m] & (1 << s))) continue;
                if (speeds[m] == 0 && s != AC_WS_AUTO) continue;
                for (int w = 0; w < AC_SWING_MAX; w++) {
                    int n = decode(bin, (UINT16) len, KEY_AC_POWER, AC_POWER_ON, m, t, s, w, a);
                    total++;
                    if (n <= 0) { failed++; continue; }
                    if (n < minLen) minLen = n;
                    if (n > maxLen) maxLen = n;
                    long d = 0;
                    for (int i = 0; i < n; i++) d += a[i];
                    if (d > durMax) durMax = d;
                    if (leader < 0) leader = a[0];
                }
            }
    }
    /* Does the swing key change the frame? */
    {
        int m = (modes & 1) ? AC_MODE_COOL : __builtin_ctz(modes ? modes : 1);
        int na = decode(bin, (UINT16) len, KEY_AC_WIND_SWING, AC_POWER_ON, m, AC_TEMP_24, AC_WS_AUTO, AC_SWING_ON, a);
        int nb = decode(bin, (UINT16) len, KEY_AC_WIND_FIX, AC_POWER_ON, m, AC_TEMP_24, AC_WS_AUTO, AC_SWING_OFF, b);
        if (na > 0 && nb > 0) swingDiffers = (na != nb || memcmp(a, b, na * sizeof(UINT16)) != 0);
    }

    /* Representative power-on frame, used to group remotes by protocol family. */
    int fm = (modes & 1) ? AC_MODE_COOL : __builtin_ctz(modes ? modes : 1);
    int fn = decode(bin, (UINT16) len, KEY_AC_POWER, AC_POWER_ON, fm, AC_TEMP_24, AC_WS_AUTO, AC_SWING_OFF, b);
    printf("{\"ok\":true,\"frame\":[");
    for (int i = 0; i < fn; i++) printf("%s%d", i ? "," : "", b[i]);
    printf("],\"modes\":%d,\"tmin\":[", modes);
    for (int m = 0; m < AC_MODE_MAX; m++) printf("%s%d", m ? "," : "", tmin[m]);
    printf("],\"tmax\":[");
    for (int m = 0; m < AC_MODE_MAX; m++) printf("%s%d", m ? "," : "", tmax[m]);
    printf("],\"speeds\":[");
    for (int m = 0; m < AC_MODE_MAX; m++) printf("%s%d", m ? "," : "", speeds[m]);
    printf("],\"swings\":[");
    for (int m = 0; m < AC_MODE_MAX; m++) printf("%s%d", m ? "," : "", swings[m]);
    printf("],\"total\":%ld,\"failed\":%ld,\"offLen\":%d,\"minLen\":%d,\"maxLen\":%d,\"durMax\":%ld,"
           "\"leader\":%d,\"swingDiffers\":%d}\n",
           total, failed, offLen, maxLen ? minLen : 0, maxLen, durMax, leader, swingDiffers);
    return 0;
}
