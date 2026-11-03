LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := irext
LOCAL_CFLAGS := -DBOARD_ANDROID -w
LOCAL_LDFLAGS += -Wl,-z,max-page-size=16384
LOCAL_C_INCLUDES := $(LOCAL_PATH)/irext/include
LOCAL_SRC_FILES := irext_jni.c \
                   irext/ir_decode.c \
                   irext/ir_tv_control.c \
                   irext/ir_ac_apply.c \
                   irext/ir_ac_build_frame.c \
                   irext/ir_ac_parse_parameter.c \
                   irext/ir_ac_parse_forbidden_info.c \
                   irext/ir_ac_parse_frame_info.c \
                   irext/ir_ac_binary_parse.c \
                   irext/ir_ac_control.c \
                   irext/ir_utils.c
LOCAL_LDLIBS := -llog
include $(BUILD_SHARED_LIBRARY)

# IRremoteESP8266 (LGPL-2.1, app/src/main/cpp/irremoteesp8266) built as its own shared
# library. UNIT_TEST + SWIGLIB make IRsend record timings instead of driving a GPIO.
include $(CLEAR_VARS)
LOCAL_MODULE := irac
LOCAL_CPPFLAGS := -DUNIT_TEST -DSWIGLIB -D_IR_LOCALE_=en-AU -std=gnu++11 -fexceptions -frtti -w -Os
LOCAL_LDFLAGS += -Wl,-z,max-page-size=16384
LOCAL_C_INCLUDES := $(LOCAL_PATH)/irremoteesp8266
LOCAL_SRC_FILES := esp_jni.cpp esp_core.cpp $(patsubst $(LOCAL_PATH)/%,%,$(wildcard $(LOCAL_PATH)/irremoteesp8266/*.cpp))
include $(BUILD_SHARED_LIBRARY)
