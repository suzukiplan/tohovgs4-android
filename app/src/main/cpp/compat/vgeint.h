/* Copyright 2012, Suzuki Plan.
   *----------------------------------------------------------------------------
   * [概要] VGE内部のデータ宣言（vge.cpp/vgeapi.c間で共有する情報群）
   *----------------------------------------------------------------------------
   */

#define MAXSLOT 2

#ifdef __cplusplus
extern "C" {
#endif

struct _VRAM {
    unsigned int pal[256];     /* パレット領域 */
    unsigned char sp[0x20000]; /* スプライト領域 */
};

struct _SLOT {
    int xs;
    int ys;
    unsigned char dat[65536];
};

struct _TOUCH {
    int s;  /* 1:タッチ中, 0:リリース中 */
    int dx; /* X方向移動ドット数 */
    int dy; /* Y方向移動ドット数 */
    int cx; /* 現在のX座標 */
    int cy; /* 現在のY座標 */
    int px; /* 直前のX座標 */
    int py; /* 直前のY座標 */
};

extern struct _VRAM _vram;
extern struct _SLOT _slot[MAXSLOT];
extern struct _TOUCH _touch;
extern unsigned char _mute;
extern unsigned char _pause;
extern short *TONE1[85];
extern short *TONE2[85];
extern short *TONE3[85];
extern short *TONE4[85];
extern void *_psg;
extern int _bstop;

void vgsbuf(char *buf, size_t size);

#ifdef __cplusplus
};
#endif
