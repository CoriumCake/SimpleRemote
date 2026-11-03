#ifndef SIMPLEREMOTE_ESP_CORE_H_
#define SIMPLEREMOTE_ESP_CORE_H_

#ifdef __cplusplus
extern "C" {
#endif

/// 1 if IRac can build frames for this decode_type_t.
int esp_is_supported(int protocol);
/// Protocol name (e.g. "DAIKIN2") into out; returns its length.
int esp_protocol_name(int protocol, char *out, int max);
int esp_last_protocol(void);

/// Signed timings (+mark / -space, µs) for the given state. Enum values are stdAc's
/// (opmode_t, fanspeed_t, swingv_t). Returns the number of timings, or < 0 on failure.
int esp_encode(int protocol, int model, int power, int mode, int temp, int fan, int swingv,
               int prev_power, int prev_swingv, int *out, int max, int *freq);

#ifdef __cplusplus
}
#endif

#endif  // SIMPLEREMOTE_ESP_CORE_H_
