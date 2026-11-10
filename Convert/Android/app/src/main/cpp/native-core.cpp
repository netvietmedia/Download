#include <jni.h>
#include <string>
#include <android/log.h>
#include <vector>

#define TAG "ShareNow_Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

// Bảng tra cứu CRC32 tĩnh siêu tốc
static uint32_t crc32_table[256];
static bool table_initialized = false;

static void init_crc32_table() {
    for (uint32_t i = 0; i < 256; i++) {
        uint32_t c = i;
        for (size_t j = 0; j < 8; j++) {
            if (c & 1) {
                c = 0xEDB88320L ^ (c >> 1);
            } else {
                c = c >> 1;
            }
        }
        crc32_table[i] = c;
    }
    table_initialized = true;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_ushareit_core_p2p_ChecksumVerifier_computeFastCrc32(
    JNIEnv* env,
    jobject /* this */,
    jbyteArray buffer,
    jint length
) {
    if (!table_initialized) {
        init_crc32_table();
    }

    jbyte* bytes = env->GetByteArrayElements(buffer, nullptr);
    if (!bytes) return 0;

    uint32_t crc = 0xFFFFFFFFL;
    for (int i = 0; i < length; i++) {
        crc = crc32_table[(crc ^ bytes[i]) & 0xFF] ^ (crc >> 8);
    }

    env->ReleaseByteArrayElements(buffer, bytes, JNI_ABORT);
    return (jlong)(crc ^ 0xFFFFFFFFL);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ushareit_app_ShareItApplication_getNativeEngineVersion(
    JNIEnv* env,
    jobject /* this */
) {
    return env->NewStringUTF("Share Now Native C++ Core Engine v2.0-HighPerformance");
}
