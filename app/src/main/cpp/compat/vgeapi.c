/*
 * Copyright 2012, Suzuki Plan.
 * License: https://github.com/suzukiplan/tohovgs4-android/blob/master/LICENSE.txt
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "vge.h"
#include "vgsdec.h"
#include "vgsmml.h"
#include "android_fopen.h"

/* グローバル変数の実体宣言 */
struct _VRAM _vram;
struct _SLOT _slot[MAXSLOT];
struct _TOUCH _touch;
void *_psg;
static int fs_bstop;
int compat_master_volume = 100;

/* 内部関数 */
static int gclip(unsigned char n, int *sx, int *sy, int *xs, int *ys, int *dx, int *dy);

static void pixel(unsigned char *p, int x, int y, unsigned char c);

static void line(unsigned char *p, int fx, int fy, int tx, int ty, unsigned char c);

static void boxf(unsigned char *p, int fx, int fy, int tx, int ty, unsigned char c);

int vge_gload(unsigned char n, const unsigned char *bin) {
    int gSize;
    int i;
    if ('S' != bin[0] || 'Z' != bin[1]) {
        return -1;
    }
    _slot[n].xs = bin[2] + 1;
    _slot[n].ys = bin[3] + 1;
    gSize = (_slot[n].xs) * (_slot[n].ys);

    /* パレット情報を読み込む */
    bin += 4;
    memcpy(_vram.pal, bin, sizeof(_vram.pal));

    /* パレットを ARGB8888 -> RGB565 に変換 */
    for (i = 0; i < 256; i++) {
        unsigned int r = (_vram.pal[i] & 0x00F80000) >> 8;
        unsigned int g = (_vram.pal[i] & 0x0000FC00) >> 5;
        unsigned int b = (_vram.pal[i] & 0x000000F8) >> 3;
        _vram.pal[i] = r | g | b;
    }

    /* 画像データを読み込む */
    bin += sizeof(_vram.pal);
    memcpy(_slot[n].dat, bin, gSize);

    return 0;
}

void vge_putSP(unsigned char n, int sx, int sy, int xs, int ys, int dx, int dy) {
    int i, j;
    int posT;
    int posF;
    if (gclip(n, &sx, &sy, &xs, &ys, &dx, &dy)) {
        return;
    }
    /* 透明色以外のピクセルを1ピクセルづつ設定 */
    posT = dy * XSIZE + dx;
    posF = sy * _slot[n].xs + sx;
    for (j = 0; j < ys; j++) {
        for (i = 0; i < xs; i++) {
            if (_slot[n].dat[posF]) {
                _vram.sp[(posT & 0x1ffff)] = _slot[n].dat[posF];
            }
            posT++;
            posF++;
        }
        posT += XSIZE - xs;
        posF += _slot[n].xs - xs;
    }
}

void vge_putSPM(unsigned char n, int sx, int sy, int xs, int ys, int dx, int dy, unsigned char c) {
    int i, j;
    int posT;
    int posF;
    if (gclip(n, &sx, &sy, &xs, &ys, &dx, &dy)) {
        return;
    }
    /* 透明色以外のピクセルを1ピクセルづつ設定 */
    posT = dy * XSIZE + dx;
    posF = sy * _slot[n].xs + sx;
    for (j = 0; j < ys; j++) {
        for (i = 0; i < xs; i++) {
            if (_slot[n].dat[posF]) {
                _vram.sp[(posT & 0x1ffff)] = c;
            }
            posT++;
            posF++;
        }
        posT += XSIZE - xs;
        posF += _slot[n].xs - xs;
    }
}

static int gclip(unsigned char n, int *sx, int *sy, int *xs, int *ys, int *dx, int *dy) {
    /* モロにはみだしてないか？ */
    if ((*sx) < 0 || _slot[n].xs < (*sx) + (*xs) || (*sy) < 0 || _slot[n].ys < (*sy) + (*ys) ||
        (*dx) + (*xs) < 0 || XSIZE <= *dx || (*dy) + (*ys) < 0 ||
        YSIZE <= *dy) {
        return -1; /* モロはみだし刑事 */
    }
    /* 左側のクリッピング処理  */
    if ((*dx) < 0) {
        (*sx) -= (*dx);
        (*xs) += (*dx);
        (*dx) = 0;
    }
    /* 右側のクリッピング処理  */
    if (XSIZE < (*dx) + (*xs)) {
        (*xs) -= ((*dx) + (*xs)) - XSIZE;
    }
    /* 上側のクリッピング処理  */
    if ((*dy) < 0) {
        (*sy) -= (*dy);
        (*ys) += (*dy);
        (*dy) = 0;
    }
    /* 下側のクリッピング処理  */
    if (YSIZE < (*dy) + (*ys)) {
        (*ys) -= ((*dy) + (*ys)) - YSIZE;
    }
    return 0;
}

void vge_pixelSP(int x, int y, unsigned char c) {
    pixel(_vram.sp, x, y, c);
}

static inline void pixel(unsigned char *p, int x, int y, unsigned char c) {
    if (0 <= x && x < XSIZE && 0 <= y && y < YSIZE) {
        p[y * XSIZE + x] = c;
    }
}

void vge_lineSP(int fx, int fy, int tx, int ty, unsigned char c) {
    line(_vram.sp, fx, fy, tx, ty, c);
}

static inline void line(unsigned char *p, int fx, int fy, int tx, int ty, unsigned char c) {
    int idx, idy;
    int w;
    idx = tx - fx;
    idy = ty - fy;
    if (!idx || !idy) {
        /* form -> to変換 */
        if (tx < fx) {
            w = fx;
            fx = tx;
            tx = w;
        }
        if (ty < fy) {
            w = fy;
            fy = ty;
            ty = w;
        }
        if (0 == idy) {
            /* 水平線(Y方向増分なし) ... 最も高速な線描画 */
            for (; fx <= tx; fx++) {
                pixel(p, fx, fy, c);
            }
        } else {
            /* 垂直線(X方向増分なし) ... 二番目に高速な線描画 */
            for (; fy <= ty; fy++) {
                pixel(p, fx, fy, c);
            }
        }
        return;
    }
}

void vge_boxSP(int fx, int fy, int tx, int ty, unsigned char c) {
    vge_lineSP(fx, fy, tx, fy, c);
    vge_lineSP(fx, ty, tx, ty, c);
    vge_lineSP(fx, fy, fx, ty, c);
    vge_lineSP(tx, fy, tx, ty, c);
}

void vge_boxfSP(int fx, int fy, int tx, int ty, unsigned char c) {
    boxf(_vram.sp, fx, fy, tx, ty, c);
}

static inline void boxf(unsigned char *p, int fx, int fy, int tx, int ty, unsigned char c) {
    int w;
    /* form -> to変換 */
    if (tx < fx) {
        w = fx;
        fx = tx;
        tx = w;
    }
    if (ty < fy) {
        w = fy;
        fy = ty;
        ty = w;
    }
    /* 範囲外描画の抑止 */
    if (XSIZE <= fx || YSIZE <= fy || tx < 0 || ty < 0) {
        return;
    }
    /* クリッピング */
    if (fx < 0) {
        fx = 0;
    }
    if (fy < 0) {
        fy = 0;
    }
    if (XSIZE <= tx) {
        tx = XSIZE - 1;
    }
    if (YSIZE <= ty) {
        ty = YSIZE - 1;
    }
    /* Xの描画サイズを予め求めておく */
    w = tx - fx;
    w++;
    /* 描画 */
    for (; fy <= ty; fy++) {
        memset(&p[fy * XSIZE + fx], c, w);
    }
}

void vge_touch(int *s, int *cx, int *cy, int *dx, int *dy) {
    *s = _touch.s;
    *cx = _touch.cx;
    *cy = _touch.cy;
    *dx = _touch.dx;
    *dy = _touch.dy;
    _touch.px = _touch.cx;
    _touch.py = _touch.cy;
    _touch.dx = 0;
    _touch.dy = 0;
}

void vge_bplay(const char *mmlPath) {
    struct VgsMmlErrorInfo err;
    struct VgsBgmData *data;
    static unsigned char mmlDataBuffer[1048576];
    int mmlSize = 0;
    int readSize;
    memset(mmlDataBuffer, 0, sizeof(mmlDataBuffer));
    FILE *fp = NULL;
    if (0 == strncmp(mmlPath, "mml/", 4)) {
        fp = android_fopen(mmlPath, "rb");
    } else {
        fp = fopen(mmlPath, "rb");
    }
    if (!fp) return;
    do {
        readSize = fread(&mmlDataBuffer[mmlSize], 1, 1024, fp);
        mmlSize += readSize;
    } while (0 < readSize);
    mmlSize++;
    fclose(fp);
    data = vgsmml_compile_from_memory(mmlDataBuffer, mmlSize, &err);
    vgsdec_load_bgm_from_memory(_psg, data->data, data->size);
    vgsmml_free_bgm_data(data);
    vgsdec_set_value(_psg, VGSDEC_REG_RESET, 1);
    vgsdec_set_value(_psg, VGSDEC_REG_SYNTHESIS_BUFFER, 1);
    vgsdec_set_value(_psg, VGSDEC_REG_TIME, 0);
    fs_bstop = 0;
}

void vge_bstop() {
    fs_bstop = 1;
}

void vge_bresume() {
    fs_bstop = 0;
}

void vge_restartCurrentSong() {
    vgsdec_set_value(_psg, VGSDEC_REG_TIME, 0);
}

void vgsbuf(char *buf, size_t size) {
    memset(buf, 0, size);
    if (fs_bstop) return;
    vgsdec_execute(_psg, buf, size);
    if (100 != compat_master_volume) {
        short *sbuf = (short *) buf;
        for (size_t i = 0; i < size / 2; i++) {
            int w = (int) sbuf[i];
            w *= compat_master_volume;
            w /= 100;
            if (w < -32768) w = -32768;
            else if (32767 < w) w = 32767;
            sbuf[i] = (short) w;
        }
    }
}
