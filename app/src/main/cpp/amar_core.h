#ifndef AMAR_CORE_H
#define AMAR_CORE_H

#include <jni.h>
#include <stdint.h>
#include <stddef.h>

#define KIND_UNKNOWN    0
#define KIND_PHOTO      1
#define KIND_VIDEO      2
#define KIND_ANIMATION  3
#define KIND_AUDIO      4
#define KIND_DOCUMENT   5

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jstring JNICALL
Java_com_amar_gallery_util_NativeEngine_initEngine(JNIEnv *env, jobject thiz);

JNIEXPORT jlong JNICALL
Java_com_amar_gallery_util_NativeEngine_fastHash64(JNIEnv *env, jobject thiz, jbyteArray data, jint length);

JNIEXPORT jint JNICALL
Java_com_amar_gallery_util_NativeEngine_classifyMedia(JNIEnv *env, jobject thiz, jstring jmime, jstring jfilename);

JNIEXPORT jstring JNICALL
Java_com_amar_gallery_util_NativeEngine_formatDateBucket(JNIEnv *env, jobject thiz, jlong epoch_seconds);

JNIEXPORT jstring JNICALL
Java_com_amar_gallery_util_NativeEngine_formatFileSize(JNIEnv *env, jobject thiz, jlong bytes);

JNIEXPORT jbyteArray JNICALL
Java_com_amar_gallery_util_NativeEngine_processMinithumb(JNIEnv *env, jobject thiz, jbyteArray raw_bytes);

#ifdef __cplusplus
}
#endif

#endif // AMAR_CORE_H

