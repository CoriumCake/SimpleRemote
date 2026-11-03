// JNI bridge to IRremoteESP8266's common A/C API (see esp_core.cpp).
// The capture buffer is a global, so the Kotlin side serialises every call.
#include <jni.h>
#include "esp_core.h"

static const int kMax = 4096;

extern "C" JNIEXPORT jintArray JNICALL
Java_com_simpleremote_ir_EspNative_encodeAc(JNIEnv *env, jclass, jint protocol, jint model,
                                            jint power, jint mode, jint temp, jint fan,
                                            jint swingv, jint prevPower, jint prevSwingv) {
  static int buf[kMax + 1];
  int freq = 0;
  int n = esp_encode(protocol, model, power, mode, temp, fan, swingv, prevPower, prevSwingv,
                     buf + 1, kMax, &freq);
  if (n <= 0) return nullptr;
  buf[0] = freq;  // first element is the carrier frequency, the rest signed µs timings
  jintArray out = env->NewIntArray(n + 1);
  if (out == nullptr) return nullptr;
  env->SetIntArrayRegion(out, 0, n + 1, buf);
  return out;
}
