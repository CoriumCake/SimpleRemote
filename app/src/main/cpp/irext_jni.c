/*
 * JNI bridge to the IRext AC decoder (app/src/main/cpp/irext, MIT licensed).
 * The decoder keeps global state, so the Kotlin side serialises every call.
 */
#include <jni.h>
#include <string.h>
#include "ir_decode.h"

JNIEXPORT jintArray JNICALL
Java_com_simpleremote_ir_IrextNative_decodeAc(JNIEnv *env, jclass clazz, jbyteArray binary, jint key,
                                              jint power, jint mode, jint temp, jint speed, jint swing) {
    (void) clazz;
    jsize len = (*env)->GetArrayLength(env, binary);
    if (len <= 0 || len > 65535) return NULL;
    jbyte *bytes = (*env)->GetByteArrayElements(env, binary, NULL);
    if (bytes == NULL) return NULL;

    t_remote_ac_status st;
    memset(&st, 0, sizeof st);
    st.ac_power = (t_ac_power) power;
    st.ac_mode = (t_ac_mode) mode;
    st.ac_temp = (t_ac_temperature) temp;
    st.ac_wind_speed = (t_ac_wind_speed) speed;
    st.ac_wind_dir = (t_ac_swing) swing;

    static UINT16 out[USER_DATA_SIZE];
    UINT16 n = ir_decode_combo(REMOTE_CATEGORY_AC, SUB_CATEGORY_QUATERNARY,
                               (UINT8 *) bytes, (UINT16) len, (UINT8) key, out, &st);
    (*env)->ReleaseByteArrayElements(env, binary, bytes, JNI_ABORT);

    /* ir_decode_combo returns (UINT16)-1 on bad arguments. */
    if (n == 0 || n > USER_DATA_SIZE) return NULL;

    jintArray result = (*env)->NewIntArray(env, n);
    if (result == NULL) return NULL;
    jint tmp[USER_DATA_SIZE];
    for (int i = 0; i < n; i++) tmp[i] = out[i];
    (*env)->SetIntArrayRegion(env, result, 0, n, tmp);
    return result;
}

/* Non-AC remotes (TV, STB, fan, ...): category/sub_category from the IRext index, key from ir_decode.h. */
JNIEXPORT jintArray JNICALL
Java_com_simpleremote_ir_IrextNative_decodeKey(JNIEnv *env, jclass clazz, jbyteArray binary,
                                               jint category, jint sub, jint key) {
    (void) clazz;
    if (category <= REMOTE_CATEGORY_AC || category >= REMOTE_CATEGORY_NEXT || key < 0 || key > 255) return NULL;
    jsize len = (*env)->GetArrayLength(env, binary);
    if (len <= 0 || len > 65535) return NULL;
    jbyte *bytes = (*env)->GetByteArrayElements(env, binary, NULL);
    if (bytes == NULL) return NULL;

    t_remote_ac_status st;
    memset(&st, 0, sizeof st);
    static UINT16 out[USER_DATA_SIZE];
    UINT16 n = ir_decode_combo((UINT8) category, (UINT8) sub, (UINT8 *) bytes, (UINT16) len, (UINT8) key, out, &st);
    (*env)->ReleaseByteArrayElements(env, binary, bytes, JNI_ABORT);
    if (n == 0 || n > USER_DATA_SIZE) return NULL;

    jintArray result = (*env)->NewIntArray(env, n);
    if (result == NULL) return NULL;
    jint tmp[USER_DATA_SIZE];
    for (int i = 0; i < n; i++) tmp[i] = out[i];
    (*env)->SetIntArrayRegion(env, result, 0, n, tmp);
    return result;
}
