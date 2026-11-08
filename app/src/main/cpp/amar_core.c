#include "amar_core.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <ctype.h>
#include <android/log.h>

#define TAG "AmarNativeCore"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#define FNV_OFFSET_BASIS 14695981039346656037ULL
#define FNV_PRIME 1099511628211ULL

static void to_lower_string(const char *src, char *dst, size_t max_len) {
    if (!src || !dst || max_len == 0) return;
    size_t i = 0;
    while (src[i] && i < max_len - 1) {
        dst[i] = (char)tolower((unsigned char)src[i]);
        i++;
    }
    dst[i] = '\0';
}

JNIEXPORT jstring JNICALL
Java_com_amar_gallery_util_NativeEngine_initEngine(JNIEnv *env, jobject thiz) {
    (void)thiz;
#if defined(__arm64__) || defined(__aarch64__)
    const char *arch = "ARM64-v8a (64-bit Ultra Fast SIMD/NEON)";
#elif defined(__arm__)
    const char *arch = "ARMv7 (32-bit Fast)";
#elif defined(__x86_64__)
    const char *arch = "x86_64 (64-bit Ultra Fast)";
#else
    const char *arch = "Universal Native Core";
#endif

    LOGI("Amar Native C Engine loaded and initialized. Architecture: %s", arch);
    char buf[128];
    snprintf(buf, sizeof(buf), "Amar C Core Active [%s]", arch);
    return (*env)->NewStringUTF(env, buf);
}

JNIEXPORT jlong JNICALL
Java_com_amar_gallery_util_NativeEngine_fastHash64(JNIEnv *env, jobject thiz, jbyteArray data, jint length) {
    (void)thiz;
    if (data == NULL || length <= 0) {
        return 0;
    }

    jbyte *bytes = (*env)->GetByteArrayElements(env, data, NULL);
    if (bytes == NULL) {
        return 0;
    }

    uint64_t hash = FNV_OFFSET_BASIS;
    for (int i = 0; i < length; ++i) {
        hash ^= (uint8_t)bytes[i];
        hash *= FNV_PRIME;
    }

    (*env)->ReleaseByteArrayElements(env, data, bytes, JNI_ABORT);
    return (jlong)hash;
}

JNIEXPORT jint JNICALL
Java_com_amar_gallery_util_NativeEngine_classifyMedia(JNIEnv *env, jobject thiz, jstring jmime, jstring jfilename) {
    (void)thiz;
    char mime[128] = {0};
    char fname[256] = {0};

    if (jmime != NULL) {
        const char *m = (*env)->GetStringUTFChars(env, jmime, NULL);
        if (m != NULL) {
            to_lower_string(m, mime, sizeof(mime));
            (*env)->ReleaseStringUTFChars(env, jmime, m);
        }
    }

    if (jfilename != NULL) {
        const char *f = (*env)->GetStringUTFChars(env, jfilename, NULL);
        if (f != NULL) {
            to_lower_string(f, fname, sizeof(fname));
            (*env)->ReleaseStringUTFChars(env, jfilename, f);
        }
    }

    // Check GIF / Animation first
    if (strstr(mime, "gif") || strstr(fname, ".gif")) {
        return KIND_ANIMATION;
    }

    // Check Photo
    if (strncmp(mime, "image/", 6) == 0 ||
        strstr(fname, ".jpg") || strstr(fname, ".jpeg") ||
        strstr(fname, ".png") || strstr(fname, ".webp") ||
        strstr(fname, ".heic") || strstr(fname, ".heif") ||
        strstr(fname, ".bmp") || strstr(fname, ".dng") ||
        strstr(fname, ".cr2") || strstr(fname, ".nef") ||
        strstr(fname, ".arw") || strstr(fname, ".raw") ||
        strstr(fname, ".tif") || strstr(fname, ".tiff") ||
        strstr(fname, ".jfif") || strstr(fname, ".svg") ||
        strstr(fname, ".ico")) {
        return KIND_PHOTO;
    }

    // Check Video
    if (strncmp(mime, "video/", 6) == 0 ||
        strstr(fname, ".mp4") || strstr(fname, ".mkv") ||
        strstr(fname, ".webm") || strstr(fname, ".mov") ||
        strstr(fname, ".avi") || strstr(fname, ".3gp") ||
        strstr(fname, ".flv") || strstr(fname, ".ts") ||
        strstr(fname, ".wmv") || strstr(fname, ".m4v") ||
        strstr(fname, ".mpg") || strstr(fname, ".mpeg") ||
        strstr(fname, ".vob") || strstr(fname, ".ogv")) {
        return KIND_VIDEO;
    }

    // Check Audio
    if (strncmp(mime, "audio/", 6) == 0 ||
        strstr(fname, ".mp3") || strstr(fname, ".m4a") ||
        strstr(fname, ".ogg") || strstr(fname, ".opus") ||
        strstr(fname, ".flac") || strstr(fname, ".wav") ||
        strstr(fname, ".aac")) {
        return KIND_AUDIO;
    }

    return KIND_DOCUMENT;
}

JNIEXPORT jstring JNICALL
Java_com_amar_gallery_util_NativeEngine_formatDateBucket(JNIEnv *env, jobject thiz, jlong epoch_seconds) {
    (void)thiz;
    if (epoch_seconds <= 0) {
        return (*env)->NewStringUTF(env, "Other");
    }

    time_t raw_time = (time_t)epoch_seconds;
    struct tm item_tm;
    localtime_r(&raw_time, &item_tm);

    time_t now_time = time(NULL);
    struct tm now_tm;
    localtime_r(&now_time, &now_tm);

    char buf[64] = {0};

    if (item_tm.tm_year == now_tm.tm_year &&
        item_tm.tm_yday == now_tm.tm_yday) {
        snprintf(buf, sizeof(buf), "Today");
    } else if (item_tm.tm_year == now_tm.tm_year &&
               item_tm.tm_yday == now_tm.tm_yday - 1) {
        snprintf(buf, sizeof(buf), "Yesterday");
    } else if (item_tm.tm_year == now_tm.tm_year) {
        strftime(buf, sizeof(buf), "%a, %e %b", &item_tm);
    } else {
        strftime(buf, sizeof(buf), "%b %Y", &item_tm);
    }

    return (*env)->NewStringUTF(env, buf);
}

JNIEXPORT jstring JNICALL
Java_com_amar_gallery_util_NativeEngine_formatFileSize(JNIEnv *env, jobject thiz, jlong bytes) {
    (void)thiz;
    char buf[64] = {0};
    if (bytes <= 0) {
        snprintf(buf, sizeof(buf), "0 B");
    } else if (bytes < 1024) {
        snprintf(buf, sizeof(buf), "%ld B", (long)bytes);
    } else if (bytes < 1024 * 1024) {
        snprintf(buf, sizeof(buf), "%.1f KB", (double)bytes / 1024.0);
    } else if (bytes < 1024 * 1024 * 1024) {
        snprintf(buf, sizeof(buf), "%.1f MB", (double)bytes / (1024.0 * 1024.0));
    } else {
        snprintf(buf, sizeof(buf), "%.2f GB", (double)bytes / (1024.0 * 1024.0 * 1024.0));
    }

    return (*env)->NewStringUTF(env, buf);
}

JNIEXPORT jbyteArray JNICALL
Java_com_amar_gallery_util_NativeEngine_processMinithumb(JNIEnv *env, jobject thiz, jbyteArray raw_bytes) {
    (void)thiz;
    if (raw_bytes == NULL) {
        return NULL;
    }

    jsize len = (*env)->GetArrayLength(env, raw_bytes);
    if (len <= 2) {
        return NULL;
    }

    jbyte *bytes = (*env)->GetByteArrayElements(env, raw_bytes, NULL);
    if (bytes == NULL) {
        return NULL;
    }

    // Check if it already begins with JPEG SOI marker 0xFF 0xD8
    if ((uint8_t)bytes[0] == 0xFF && (uint8_t)bytes[1] == 0xD8) {
        (*env)->ReleaseByteArrayElements(env, raw_bytes, bytes, JNI_ABORT);
        return raw_bytes;
    }

    // Standard Telegram minithumbnail header bytes
    static const uint8_t JPEG_HEADER[] = {
        0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F', 0x00, 0x01, 0x01, 0x00, 0x00, 0x01,
        0x00, 0x01, 0x00, 0x00
    };
    static const size_t HDR_LEN = sizeof(JPEG_HEADER);

    jbyteArray result = (*env)->NewByteArray(env, (jsize)(len + HDR_LEN));
    if (result != NULL) {
        (*env)->SetByteArrayRegion(env, result, 0, (jsize)HDR_LEN, (const jbyte *)JPEG_HEADER);
        (*env)->SetByteArrayRegion(env, result, (jsize)HDR_LEN, len, bytes);
    }

    (*env)->ReleaseByteArrayElements(env, raw_bytes, bytes, JNI_ABORT);
    return result;
}

