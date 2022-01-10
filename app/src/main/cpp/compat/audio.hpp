#ifndef INCLUDE_AUDIO_HPP
#define INCLUDE_AUDIO_HPP

#include <pthread.h>
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
#include <unistd.h>
#include <stdio.h>
#include <stdlib.h>
#include <cstring>
#include "vgeint.h"

#define SOUND_BUFFER_SIZE 735

class VgsAudioSystem {
private:
    struct SL {
        SLObjectItf slEngObj;
        SLEngineItf slEng;
        SLObjectItf slMixObj;
        SLObjectItf slPlayObj;
        SLPlayItf slPlay;
        SLAndroidSimpleBufferQueueItf slBufQ;
    };

    pthread_mutex_t mutex{};
    struct SL sl{};
    int sampling;
    int bit;
    SLuint32 channel;
    char buffer[2][SOUND_BUFFER_SIZE]{};
    int bufferLatch;

public:
    VgsAudioSystem(int sampling, int bit, int channel) {
        pthread_mutex_init(&mutex, NULL);
        this->sampling = sampling;
        this->bit = bit;
        this->channel = (SLuint32) channel;
        init_sl();
    }

    ~VgsAudioSystem() {
        lock();
        if (sl.slBufQ) {
            (*sl.slBufQ)->Clear(sl.slBufQ);
        }
        if (sl.slPlay) {
            (*sl.slPlay)->SetPlayState(sl.slPlay, SL_PLAYSTATE_STOPPED);
        }
        sl.slBufQ = NULL;
        unlock();

        if (sl.slPlayObj) {
            (*sl.slPlayObj)->Destroy(sl.slPlayObj);
            sl.slPlayObj = NULL;
        }

        if (sl.slMixObj) {
            (*sl.slMixObj)->Destroy(sl.slMixObj);
            sl.slMixObj = NULL;
        }

        if (sl.slEngObj) {
            (*sl.slEngObj)->Destroy(sl.slEngObj);
            sl.slEngObj = NULL;
        }

        pthread_mutex_destroy(&mutex);
    }

    void lock() {
        pthread_mutex_lock(&mutex);
    }

    void unlock() {
        pthread_mutex_unlock(&mutex);
    }

    static void callback(SLAndroidSimpleBufferQueueItf bq, void *c) {
        VgsAudioSystem *context = (VgsAudioSystem *) c;
        struct timespec ts = {0, 0};
        context->lock();
        if (!context->isEnded()) {
            (*bq)->Enqueue(bq, context->buffer[context->bufferLatch], SOUND_BUFFER_SIZE);
            context->bufferLatch = 1 - context->bufferLatch;
            vgsbuf(context->buffer[context->bufferLatch], SOUND_BUFFER_SIZE);
        }
        context->unlock();
    }

    bool isEnded() {
        return NULL == sl.slBufQ;
    }

private:
    int init_sl() {
        SLresult res;
        const SLInterfaceID ids[1] = {SL_IID_ENVIRONMENTALREVERB};
        const SLboolean req[1] = {SL_BOOLEAN_FALSE};

        res = slCreateEngine(&sl.slEngObj, 0, NULL, 0, NULL, NULL);
        if (SL_RESULT_SUCCESS != res) {
            __android_log_print(ANDROID_LOG_ERROR, "AudioSystem", "slCreateEngine failed: %08X",
                                res);
            return -1;
        }
        res = (*sl.slEngObj)->Realize(sl.slEngObj, SL_BOOLEAN_FALSE);
        if (SL_RESULT_SUCCESS != res) {
            __android_log_print(ANDROID_LOG_ERROR, "AudioSystem", "Realize/EngObj failed: %08X",
                                res);
            return -1;
        }
        res = (*sl.slEngObj)->GetInterface(sl.slEngObj, SL_IID_ENGINE, &sl.slEng);
        if (SL_RESULT_SUCCESS != res) {
            __android_log_print(ANDROID_LOG_ERROR, "AudioSystem",
                                "GetInterface/SL_IID_ENGINE failed: %08x", res);
            return -1;
        }
        res = (*sl.slEng)->CreateOutputMix(sl.slEng, &sl.slMixObj, 1, ids, req);
        if (SL_RESULT_SUCCESS != res) {
            __android_log_print(ANDROID_LOG_ERROR, "AudioSystem", "CreateOutputMix failed: %08X",
                                res);
            return -1;
        }
        res = (*sl.slMixObj)->Realize(sl.slMixObj, SL_BOOLEAN_FALSE);
        if (SL_RESULT_SUCCESS != res) {
            __android_log_print(ANDROID_LOG_ERROR, "AudioSystem", "Realize/MixObj failed: %08X",
                                res);
            return -1;
        }
        if (init_sl2()) {
            return -1;
        }
        return 0;
    }

    int init_sl2() {
        SLresult res;
        SLDataFormat_PCM format_pcm = {
                SL_DATAFORMAT_PCM,           /* PCM */
                1,                           /* 1ch */
                SL_SAMPLINGRATE_22_05,       /* 22050Hz */
                SL_PCMSAMPLEFORMAT_FIXED_16, /* 16bit */
                SL_PCMSAMPLEFORMAT_FIXED_16, /* 16bit */
                SL_SPEAKER_FRONT_CENTER,     /* center */
                SL_BYTEORDER_LITTLEENDIAN    /* little-endian */
        };
        SLDataLocator_AndroidSimpleBufferQueue loc_bufq = {SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE,
                                                           2};
        SLDataSource aSrc = {&loc_bufq, &format_pcm};
        SLDataLocator_OutputMix loc_outmix = {SL_DATALOCATOR_OUTPUTMIX, sl.slMixObj};
        SLDataSink aSnk = {&loc_outmix, NULL};
        const SLInterfaceID ids[2] = {SL_IID_ANDROIDSIMPLEBUFFERQUEUE, SL_IID_EFFECTSEND};
        const SLboolean req[2] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE};

        switch (channel) {
            case 1:
                format_pcm.numChannels = 1;
                format_pcm.channelMask = SL_SPEAKER_FRONT_CENTER;
                break;
            case 2:
                format_pcm.numChannels = 2;
                format_pcm.channelMask = SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT;
                break;
            default:
                __android_log_print(ANDROID_LOG_ERROR, "AudioSystem", "invalid channel number: %d",
                                    channel);
                return -1;
        }
        switch (sampling) {
            case 8000:
                format_pcm.samplesPerSec = SL_SAMPLINGRATE_8;
                break;
            case 11025:
                format_pcm.samplesPerSec = SL_SAMPLINGRATE_11_025;
                break;
            case 22050:
                format_pcm.samplesPerSec = SL_SAMPLINGRATE_22_05;
                break;
            case 24000:
                format_pcm.samplesPerSec = SL_SAMPLINGRATE_24;
                break;
            case 32000:
                format_pcm.samplesPerSec = SL_SAMPLINGRATE_32;
                break;
            case 44100:
                format_pcm.samplesPerSec = SL_SAMPLINGRATE_44_1;
                break;
            case 48000:
                format_pcm.samplesPerSec = SL_SAMPLINGRATE_48;
                break;
            case 64000:
                format_pcm.samplesPerSec = SL_SAMPLINGRATE_64;
                break;
            case 88200:
                format_pcm.samplesPerSec = SL_SAMPLINGRATE_88_2;
                break;
            case 96000:
                format_pcm.samplesPerSec = SL_SAMPLINGRATE_96;
                break;
            case 192000:
                format_pcm.samplesPerSec = SL_SAMPLINGRATE_192;
                break;
            default:
                __android_log_print(ANDROID_LOG_ERROR, "AudioSystem", "invalid sampling rate: %d",
                                    sampling);
                return -1;
        }

        switch (bit) {
            case 8:
                format_pcm.bitsPerSample = SL_PCMSAMPLEFORMAT_FIXED_8;
                format_pcm.containerSize = SL_PCMSAMPLEFORMAT_FIXED_8;
                break;
            case 16:
                format_pcm.bitsPerSample = SL_PCMSAMPLEFORMAT_FIXED_16;
                format_pcm.containerSize = SL_PCMSAMPLEFORMAT_FIXED_16;
                break;
            case 20:
                format_pcm.bitsPerSample = SL_PCMSAMPLEFORMAT_FIXED_20;
                format_pcm.containerSize = SL_PCMSAMPLEFORMAT_FIXED_32;
                break;
            case 24:
                format_pcm.bitsPerSample = SL_PCMSAMPLEFORMAT_FIXED_24;
                format_pcm.containerSize = SL_PCMSAMPLEFORMAT_FIXED_32;
                break;
            case 28:
                format_pcm.bitsPerSample = SL_PCMSAMPLEFORMAT_FIXED_28;
                format_pcm.containerSize = SL_PCMSAMPLEFORMAT_FIXED_32;
                break;
            case 32:
                format_pcm.bitsPerSample = SL_PCMSAMPLEFORMAT_FIXED_32;
                format_pcm.containerSize = SL_PCMSAMPLEFORMAT_FIXED_32;
                break;
            default:
                __android_log_print(ANDROID_LOG_ERROR, "AudioSystem", "invalid bit rate: %d", bit);
                return -1;
        }

        res = (*sl.slEng)->CreateAudioPlayer(sl.slEng, &sl.slPlayObj, &aSrc, &aSnk, 2, ids, req);
        if (SL_RESULT_SUCCESS != res) {
            __android_log_print(ANDROID_LOG_ERROR, "AudioSystem", "CreateAudioPlayer failed: %08X",
                                res);
            return -1;
        }
        res = (*sl.slPlayObj)->Realize(sl.slPlayObj, SL_BOOLEAN_FALSE);
        if (SL_RESULT_SUCCESS != res) {
            __android_log_print(ANDROID_LOG_ERROR, "AudioSystem",
                                "Realize/AudioPlayer failed: %08X",
                                res);
            return -1;
        }
        res = (*sl.slPlayObj)->GetInterface(sl.slPlayObj, SL_IID_PLAY, &sl.slPlay);
        if (SL_RESULT_SUCCESS != res) {
            __android_log_print(ANDROID_LOG_ERROR, "AudioSystem",
                                "GetInterface/SL_IID_PLAY failed: %08X", res);
            return -1;
        }
        res = (*sl.slPlayObj)->GetInterface(sl.slPlayObj, SL_IID_ANDROIDSIMPLEBUFFERQUEUE,
                                            &sl.slBufQ);
        if (SL_RESULT_SUCCESS != res) {
            __android_log_print(ANDROID_LOG_ERROR, "AudioSystem",
                                "GetInterface/SL_IID_ANDROIDSIMPLEBUFFERQUEUE failed: %08X", res);
            return -1;
        }
        return startPlaying();
    }

    int startPlaying() {
        SLresult res;
        if (!sl.slBufQ || !sl.slPlay) {
            return -1;
        }
        res = (*sl.slBufQ)->RegisterCallback(sl.slBufQ, callback, this);
        if (SL_RESULT_SUCCESS != res) {
            __android_log_print(ANDROID_LOG_ERROR, "AudioSystem", "RegisterCallback failed: %08X",
                                res);
            return -1;
        }
        res = (*sl.slPlay)->SetPlayState(sl.slPlay, SL_PLAYSTATE_PLAYING);
        if (SL_RESULT_SUCCESS != res) {
            __android_log_print(ANDROID_LOG_ERROR, "AudioSystem",
                                "SetPlayState/PLAYING failed: %08X",
                                res);
            return -1;
        }
        bufferLatch = 1;
        vgsbuf(buffer[0], SOUND_BUFFER_SIZE);
        vgsbuf(buffer[1], SOUND_BUFFER_SIZE);
        res = (*sl.slBufQ)->Enqueue(sl.slBufQ, buffer[0], SOUND_BUFFER_SIZE);
        if (SL_RESULT_SUCCESS != res) {
            __android_log_print(ANDROID_LOG_ERROR, "AudioSystem", "Enqueue first data failed: %08X",
                                res);
        }
        return 0;
    }
};

#endif
