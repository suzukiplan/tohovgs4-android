/**
 * ©2022, SUZUKI PLAN
 * License: https://github.com/suzukiplan/tohovgs4-android/blob/master/LICENSE.txt
 */
#include <jni.h>
#include <android/bitmap.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <cstring>
#include "vgsdec.h"
#include "vgsmml.h"
#include "vge.h"
#include "android_fopen.h"
#include "audio.hpp"

static jobject android_java_asset_manager = nullptr;
static VgsAudioSystem *audioSystem = nullptr;
static pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;

extern "C" JNIEXPORT jlong JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_createDecoder(JNIEnv *, jclass) {
    return (jlong) vgsdec_create_context();
}

extern "C" JNIEXPORT void JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_releaseDecoder(JNIEnv *, jclass, jlong context) {
    if (context) {
        vgsdec_release_context((void *) context);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_load(JNIEnv *env, jclass, jlong context, jbyteArray mml_) {
    if (context) {
        jbyte *mml = env->GetByteArrayElements(mml_, nullptr);
        auto size = (uint32_t) env->GetArrayLength(mml_);
        if (mml) {
            VgsMmlErrorInfo err{};
            VgsBgmData *data = vgsmml_compile_from_memory2(mml, size, &err);
            if (data) {
                vgsdec_load_bgm_from_memory((void *) context, data->data, data->size);
                vgsmml_free_bgm_data(data);
            }
            env->ReleaseByteArrayElements(mml_, mml, 0);
        }
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_loopCount(JNIEnv *, jclass, jlong context) {
    int result = 0;
    if (context) {
        pthread_mutex_lock(&mutex);
        result = vgsdec_get_value((void *) context, VGSDEC_REG_LOOP_COUNT);
        pthread_mutex_unlock(&mutex);
    }
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_fadeout(JNIEnv *, jclass, jlong context) {
    if (context) {
        pthread_mutex_lock(&mutex);
        vgsdec_set_value((void *) context, VGSDEC_REG_FADEOUT, 100);
        pthread_mutex_unlock(&mutex);
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_isPlaying(JNIEnv *, jclass, jlong context) {
    jboolean result = JNI_FALSE;
    if (context) {
        pthread_mutex_lock(&mutex);
        result = vgsdec_get_value((void *) context, VGSDEC_REG_PLAYING) ? JNI_TRUE : JNI_FALSE;
        pthread_mutex_unlock(&mutex);
    }
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_getTimeLength(JNIEnv *, jclass, jlong context) {
    jint result = 0;
    if (context) {
        pthread_mutex_lock(&mutex);
        result = vgsdec_get_value((void *) context, VGSDEC_REG_TIME_LENGTH);
        pthread_mutex_unlock(&mutex);
    }
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_getTime(JNIEnv *, jclass, jlong context) {
    jint result = 0;
    if (context) {
        pthread_mutex_lock(&mutex);
        result = vgsdec_get_value((void *) context, VGSDEC_REG_TIME);
        pthread_mutex_unlock(&mutex);
    }
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_seek(JNIEnv *, jclass, jlong context, jint progress) {
    if (context) {
        pthread_mutex_lock(&mutex);
        vgsdec_set_value((void *) context, VGSDEC_REG_TIME, progress);
        pthread_mutex_unlock(&mutex);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_kobushi(JNIEnv *, jclass, jlong context, jint kobushi) {
    if (context) {
        pthread_mutex_lock(&mutex);
        vgsdec_set_value((void *) context, VGSDEC_REG_KOBUSHI, kobushi);
        pthread_mutex_unlock(&mutex);
    }
}

static void safeReleaseAudioSystem() {
    pthread_mutex_lock(&mutex);
    if (audioSystem) {
        delete audioSystem;
        audioSystem = nullptr;
    }
    pthread_mutex_unlock(&mutex);
}

extern "C" JNIEXPORT void JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_startPlay(JNIEnv *, jclass, jlong context) {
    static void *currentContext = (void *) context;
    safeReleaseAudioSystem();
    audioSystem = new VgsAudioSystem(22050, 16, 1, 8192, [](char *buf, size_t size) {
        pthread_mutex_lock(&mutex);
        vgsdec_execute(currentContext, buf, size);
        pthread_mutex_unlock(&mutex);
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_endPlay(JNIEnv *, jclass) {
    safeReleaseAudioSystem();
}

extern "C" JNIEXPORT void JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_compatCleanUp(JNIEnv *env, jclass) {
    safeReleaseAudioSystem();
    tohovgs_cleanUp();
    if (android_java_asset_manager != nullptr) {
        env->DeleteGlobalRef(android_java_asset_manager);
        android_java_asset_manager = nullptr;
    }
    memset(&_touch, 0, sizeof(_touch));
}

extern "C" JNIEXPORT void JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_compatAllocate(JNIEnv *env,
                                                   jclass,
                                                   jint nTitle,
                                                   jint nSong,
                                                   jobject assetManager) {
    android_java_asset_manager = env->NewGlobalRef(assetManager);
    android_fopen_set_asset_manager(AAssetManager_fromJava(env, android_java_asset_manager));
    tohovgs_allocate(nTitle, nSong);
    audioSystem = new VgsAudioSystem(22050, 16, 1, 800, vgsbuf);
}

extern "C" JNIEXPORT void JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_compatLoadGraphic(JNIEnv *env, jclass, jint n,
                                                      jbyteArray data_) {
    jbyte *data = env->GetByteArrayElements(data_, nullptr);
    vge_gload(n & 0xFF, (const unsigned char *) data);
    env->ReleaseByteArrayElements(data_, data, 0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_compatLoadKanji(JNIEnv *env, jclass, jbyteArray data_) {
    jbyte *data = env->GetByteArrayElements(data_, nullptr);
    auto size = (uint32_t) env->GetArrayLength(data_);
    tohovgs_loadKanji(data, size);
    env->ReleaseByteArrayElements(data_, data, 0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_compatAddTitle(JNIEnv *env, jclass,
                                                   jint index,
                                                   jint id,
                                                   jint songNum,
                                                   jbyteArray title_,
                                                   jbyteArray copyright_) {
    auto title = env->GetByteArrayElements(title_, nullptr);
    auto titleSize = (uint32_t) env->GetArrayLength(title_);
    auto copyright = env->GetByteArrayElements(copyright_, nullptr);
    auto copyrightSize = (uint32_t) env->GetArrayLength(copyright_);
    tohovgs_setTitle(index, id, songNum, title, titleSize, copyright, copyrightSize);
    env->ReleaseByteArrayElements(copyright_, copyright, 0);
    env->ReleaseByteArrayElements(title_, title, 0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_compatAddSong(JNIEnv *env, jclass,
                                                  jint index,
                                                  jint id,
                                                  jint no,
                                                  jint loop,
                                                  jint col,
                                                  jbyteArray mmlPath_,
                                                  jbyteArray titleJ_,
                                                  jbyteArray titleE_) {
    //__android_log_print(ANDROID_LOG_DEBUG, "TOHOVGS", "addSong:%04X-%d", id, no);
    auto titleJ = env->GetByteArrayElements(titleJ_, nullptr);
    auto titleSizeJ = (uint32_t) env->GetArrayLength(titleJ_);
    auto titleE = env->GetByteArrayElements(titleE_, nullptr);
    auto titleSizeE = (uint32_t) env->GetArrayLength(titleE_);
    auto mmlPath = env->GetByteArrayElements(mmlPath_, nullptr);
    auto mmlPathSize = (uint32_t) env->GetArrayLength(mmlPath_);
    tohovgs_setSong(index,
                    id,
                    no,
                    loop,
                    col,
                    mmlPath,
                    mmlPathSize,
                    titleJ,
                    titleSizeJ,
                    titleE,
                    titleSizeE);
    env->ReleaseByteArrayElements(mmlPath_, mmlPath, 0);
    env->ReleaseByteArrayElements(titleJ_, titleJ, 0);
    env->ReleaseByteArrayElements(titleE_, titleE, 0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_compatTick(JNIEnv *env, jclass, jobject bitmap) {
    unsigned short *pixels;
    memset(_vram.sp, 0, sizeof(_vram.sp));
    vge_tick();
    if (AndroidBitmap_lockPixels(env, bitmap, (void **) &pixels) < 0) return;
    int index = 0;
    for (int y = 0; y < 320; y++) {
        for (int x = 0; x < 240; x++) {
            pixels[index] = _vram.pal[_vram.sp[index]];
            index++;
        }
    }
    AndroidBitmap_unlockPixels(env, bitmap);
}

extern "C" JNIEXPORT void JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_compatOnTouch(JNIEnv *,
                                                  jclass,
                                                  jint cx,
                                                  jint cy,
                                                  jint dx,
                                                  jint dy) {
    _touch.s = 1;
    _touch.cx = cx;
    _touch.cy = cy;
    _touch.dx += dx;
    _touch.dy += dy;
}

extern "C" JNIEXPORT void JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_compatOnReleaseTouch(JNIEnv *, jclass) {
    _touch.s = 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_compatOnFling(JNIEnv *, jclass, jint fx, jint fy) {
    g_flingX += fx;
    g_flingY += fy;
}

extern "C" JNIEXPORT void JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_compatSetPreference(JNIEnv *, jclass,
                                                        jint currentTitleId,
                                                        jint loop,
                                                        jint base,
                                                        jint infinity,
                                                        jint kobushi,
                                                        jint localeId,
                                                        jint listType) {
    tohovgs_setPreference(currentTitleId, loop, base, infinity, kobushi, localeId, listType);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_compatGetCurrentTitleId(JNIEnv *, jclass) {
    return tohovgs_getPreference()->currentTitleId;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_compatGetLoop(JNIEnv *, jclass) {
    return tohovgs_getPreference()->loop;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_compatGetBase(JNIEnv *, jclass) {
    return tohovgs_getPreference()->base;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_compatGetInfinity(JNIEnv *, jclass) {
    return tohovgs_getPreference()->infinity;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_compatGetKobushi(JNIEnv *, jclass) {
    return tohovgs_getPreference()->kobushi;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_compatGetLocaleId(JNIEnv *, jclass) {
    return tohovgs_getPreference()->localeId;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_compatGetListType(JNIEnv *, jclass) {
    return tohovgs_getPreference()->listType;
}
