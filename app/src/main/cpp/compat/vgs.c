/* Copyright 2012, Suzuki Plan.
 *----------------------------------------------------------------------------
 * [呼称] VGS(Vide Game Sound) Version 1.00
 * [概要] 独自ソフトウェア・シンセサイザー VG-Sound
 * [備考] カーネル種別に依存しない処理を実装する(Windows/Android共用)
 *        ただし、C標準関数は全てのカーネルで使えるものとする.
 *----------------------------------------------------------------------------
 */
#include <stdio.h>
#include <string.h>
#include "vge.h"
#include "vgeint.h"
#include "vgsdec.h"

int _bstop;

/*
 *----------------------------------------------------------------------------
 * サウンド情報のバッファリング
 *----------------------------------------------------------------------------
 */
void vgsbuf(char *buf, size_t size) {
    static int an;
    an = 1 - an;
    memset(buf, 0, size);
    if (_pause || vge_getmute()) return;
    if (_bstop) return;
    vgsdec_execute(_psg, buf, size);
}
