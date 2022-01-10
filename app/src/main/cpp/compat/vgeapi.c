/* Copyright 2012, Suzuki Plan.
 *----------------------------------------------------------------------------
 * [呼称] VGE(Vide Game Engine) Version 1.00
 * [概要] Windows/Androidのクロス開発を実現するためのエンジン. (QVGA専用)
 * [備考] カーネル種別に依存しない処理を実装する(Windows/Android共用)
 *        ただし、C標準関数は全てのカーネルで使えるものとする.
 *----------------------------------------------------------------------------
 */
#ifdef _WIN32
#include <Windows.h>
#endif

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "vge.h"
#include "vgeint.h"
#include "vgsdec.h"
#include "vgsmml.h"
#include "android_fopen.h"

/* グローバル変数の実体宣言 */
struct _VRAM _vram;
struct _SLOT _slot[MAXSLOT];
struct _TOUCH _touch;
unsigned char _mute;
unsigned char _pause;
void *_psg;

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

/*
 *----------------------------------------------------------------------------
 * [VGE-API] vge_putSP: スロットデータをスプライト領域へ転送する
 * 引数: vge_putBGと同じ
 * 解説: スプライトは、一度表示したら消える妖精のような存在
 *----------------------------------------------------------------------------
 */
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

/*
 *----------------------------------------------------------------------------
 * [VGE-API] vge_putSPM: スロットデータ(mask)をスプライト領域へ転送する
 * 引数: vge_putBGと同じ + maskカラー(c)
 * 解説: スプライトは、一度表示したら消える妖精のような存在
 *----------------------------------------------------------------------------
 */
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

/*
 *----------------------------------------------------------------------------
 * vge_putBG/vge_putSP共通のクリッピング処理
 *----------------------------------------------------------------------------
 */
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

/*
 *----------------------------------------------------------------------------
 * [VGE-API] vge_pixelSP: スプライト面にピクセルを描画する
 *----------------------------------------------------------------------------
 * 引数:
 * - x [I] X座標
 * - y [I] Y座標
 * - c [I] パレット番号
 *----------------------------------------------------------------------------
 */
void vge_pixelSP(int x, int y, unsigned char c) {
    pixel(_vram.sp, x, y, c);
}

/*
 *----------------------------------------------------------------------------
 * vge_pixelBG, vge_pixelSPの内部処理
 *----------------------------------------------------------------------------
 */
static inline void pixel(unsigned char *p, int x, int y, unsigned char c) {
    if (0 <= x && x < XSIZE && 0 <= y && y < YSIZE) {
        p[y * XSIZE + x] = c;
    }
}

/*
 *----------------------------------------------------------------------------
 * [VGE-API] vge_lineBG: BG面にラインを描画する
 *----------------------------------------------------------------------------
 * 引数:
 * - fx [I] X座標(基点)
 * - fy [I] Y座標(基点)
 * - tx [I] X座標(終点)
 * - ty [I] Y座標(終点)
 * - c [I] パレット番号
 *----------------------------------------------------------------------------
 */
void vge_lineSP(int fx, int fy, int tx, int ty, unsigned char c) {
    line(_vram.sp, fx, fy, tx, ty, c);
}

/*
 *----------------------------------------------------------------------------
 * vge_lineBGとvge_lineSPの共通処理
 *----------------------------------------------------------------------------
 */
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

/*
 *----------------------------------------------------------------------------
 * [VGE-API] vge_boxBG: スプライト面にボックスを描画する
 *----------------------------------------------------------------------------
 * 引数:
 * - fx [I] X座標(基点)
 * - fy [I] Y座標(基点)
 * - tx [I] X座標(終点)
 * - ty [I] Y座標(終点)
 * - c [I] パレット番号
 *----------------------------------------------------------------------------
 */
void vge_boxSP(int fx, int fy, int tx, int ty, unsigned char c) {
    vge_lineSP(fx, fy, tx, fy, c);
    vge_lineSP(fx, ty, tx, ty, c);
    vge_lineSP(fx, fy, fx, ty, c);
    vge_lineSP(tx, fy, tx, ty, c);
}

/*
 *----------------------------------------------------------------------------
 * [VGE-API] vge_boxfSP: スプライト面に塗りつぶしボックスを描画する
 *----------------------------------------------------------------------------
 * 引数:
 * - fx [I] X座標(基点)
 * - fy [I] Y座標(基点)
 * - tx [I] X座標(終点)
 * - ty [I] Y座標(終点)
 * - c [I] パレット番号
 *----------------------------------------------------------------------------
 */
void vge_boxfSP(int fx, int fy, int tx, int ty, unsigned char c) {
    boxf(_vram.sp, fx, fy, tx, ty, c);
}

/*
 *----------------------------------------------------------------------------
 * vge_lineBGとvge_lineSPの共通処理
 *----------------------------------------------------------------------------
 */
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

/*
 *----------------------------------------------------------------------------
 * [VGE-API] タッチパネルの状態を取得
 *----------------------------------------------------------------------------
 * 引数:
 * - s [O] タッチパネルを押した状態か否か（非タッチ0, タッチ中: フレーム数）
 * - cx [O] 現在のX座標（非タッチ中は不定）
 * - cy [O] 現在のY座標（非タッチ中は不定）
 * - dx [O] X方向の移動距離（非タッチ中は不定）
 * - dy [O] Y方向の移動距離（非タッチ中は不定）
 *----------------------------------------------------------------------------
 */
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

/*
 *----------------------------------------------------------------------------
 * [VGE-API] vge_getmute: 音を消す / 鳴らすの設定を取得
 *----------------------------------------------------------------------------
 * 戻り値: 非0=ミュート、0=発音
 *----------------------------------------------------------------------------
 */
unsigned char vge_getmute() {
    return _mute;
}

/*
 *----------------------------------------------------------------------------
 * [VGE-API] vge_bplay: BGMを演奏する
 *----------------------------------------------------------------------------
 * 引数:
 * - n [I] スロット番号
 *----------------------------------------------------------------------------
 */
void vge_bplay(const char *mmlPath) {
    struct VgsMmlErrorInfo err;
    struct VgsBgmData *data;
    static unsigned char mmlDataBuffer[1048576];
    int mmlSize = 0;
    int readSize;
    memset(mmlDataBuffer, 0, sizeof(mmlDataBuffer));
    FILE *fp = android_fopen(mmlPath, "rb");
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
    _bstop = 0;
}

/*
 *----------------------------------------------------------------------------
 * [VGE-API] vge_bstop: BGMの演奏を中断する
 *----------------------------------------------------------------------------
 */
void vge_bstop() {
    _bstop = 1;
}

/*
 *----------------------------------------------------------------------------
 * [VGE-API] vge_bresume: BGMの演奏を中断したところから再開する
 *----------------------------------------------------------------------------
 */
void vge_bresume() {
    _bstop = 0;
}
