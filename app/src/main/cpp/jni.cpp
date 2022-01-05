/**
 * ©2022, SUZUKI PLAN
 * License: https://github.com/suzukiplan/tohovgs4-android/blob/master/LICENSE.txt
 */
#include <jni.h>
#include "vgsdec.h"
#include "vgsmml.h"

extern "C" JNIEXPORT jlong JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_createDecoder(JNIEnv *env, jclass) {
    return (jlong) vgsdec_create_context();
}

extern "C" JNIEXPORT void JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_releaseDecoder(JNIEnv *env, jclass, jlong context) {
    if (context) {
        vgsdec_release_context((void *) context);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_load(JNIEnv *env, jclass, jlong context, jbyteArray mml_) {
    if (context) {
        jbyte *mml = env->GetByteArrayElements(mml_, 0);
        size_t size = (uint32_t) env->GetArrayLength(mml_);
        if (mml) {
            VgsMmlErrorInfo err;
            VgsBgmData *data = vgsmml_compile_from_memory2(mml, size, &err);
            if (data) {
                vgsdec_load_bgm_from_memory((void *) context, data->data, data->size);
                vgsmml_free_bgm_data(data);
            }
            env->ReleaseByteArrayElements(mml_, mml, 0);
        }
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_decode(JNIEnv *env, jclass, jlong context, jbyteArray buf_) {
    if (context) {
        jbyte *buf = env->GetByteArrayElements(buf_, 0);
        size_t size = (uint32_t) env->GetArrayLength(buf_);
        if (buf) {
            vgsdec_execute((void *) context, buf, size);
            env->ReleaseByteArrayElements(buf_, buf, 0);
        }
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_loopCount(JNIEnv *, jclass, jlong context) {
    if (context) {
        return vgsdec_get_value((void *) context, VGSDEC_REG_LOOP_COUNT);
    }
    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_fadeout(JNIEnv *, jclass, jlong context) {
    if (context) {
        vgsdec_set_value((void *) context, VGSDEC_REG_FADEOUT, 100);
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_isPlaying(JNIEnv *, jclass, jlong context) {
    if (context) {
        return vgsdec_get_value((void *) context, VGSDEC_REG_PLAYING) ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_getTimeLength(JNIEnv *, jclass, jlong context) {
    if (context) {
        return vgsdec_get_value((void *) context, VGSDEC_REG_TIME_LENGTH);
    } else {
        return 0;
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_getTime(JNIEnv *, jclass, jlong context) {
    if (context) {
        return vgsdec_get_value((void *) context, VGSDEC_REG_TIME);
    } else {
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_suzukiplan_tohovgs_api_JNI_seek(JNIEnv *, jclass, jlong context, jint progress) {
    if (context) {
        vgsdec_set_value((void *) context, VGSDEC_REG_TIME, progress);
    }
}