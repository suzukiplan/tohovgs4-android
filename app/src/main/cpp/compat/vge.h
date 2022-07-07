/**
 * ©2022, SUZUKI PLAN
 * License: https://github.com/suzukiplan/tohovgs4-android/blob/master/LICENSE.txt
 */
#ifndef _INCLUDE_VGE_H
#define _INCLUDE_VGE_H

#ifdef __cplusplus
extern "C" {
#endif

#define MAXSLOT 2
#define XSIZE 240
#define YSIZE 320

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

struct Preferences {
    int currentTitleId;
    int base;
    int infinity;
    int loop;
    int kobushi;
    int localeId;
    int listType;
    int isFirst;
};

extern struct _VRAM _vram;
extern struct _SLOT _slot[MAXSLOT];
extern struct _TOUCH _touch;
extern short *TONE1[85];
extern short *TONE2[85];
extern short *TONE3[85];
extern short *TONE4[85];
extern void *_psg;
extern int g_flingY;
extern int g_flingX;
extern int compat_master_volume;

void tohovgs_cleanUp();

void tohovgs_allocate(int nTitle, int nSong);

void tohovgs_loadKanji(const void *data, size_t size);

void tohovgs_setTitle(int index,
                      int id,
                      int songNum,
                      void *title,
                      size_t titleSize,
                      void *copyright,
                      size_t copyrightSize);

void tohovgs_setSong(int index,
                     int id,
                     int no,
                     int loop,
                     int col,
                     void *mmlPath,
                     size_t mmlPathSize,
                     void *titleJ,
                     size_t titleSizeJ,
                     void *titleE,
                     size_t titleSizeE);

void tohovgs_setPreference(int currentTitleId,
                           int loop,
                           int base,
                           int infinity,
                           int kobushi,
                           int localeId,
                           int listType);

struct Preferences *tohovgs_getPreference();

int vge_tick();

int vge_gload(unsigned char n, const unsigned char *bin);

void vge_putSP(unsigned char n, int sx, int sy, int xs, int ys, int dx, int dy);

void vge_putSPM(unsigned char n, int sx, int sy, int xs, int ys, int dx, int dy, unsigned char c);

void vge_pixelSP(int x, int y, unsigned char c);

void vge_lineSP(int fx, int fy, int tx, int ty, unsigned char c);

void vge_boxSP(int fx, int fy, int tx, int ty, unsigned char c);

void vge_boxfSP(int fx, int fy, int tx, int ty, unsigned char c);

void vge_touch(int *s, int *cx, int *cy, int *dx, int *dy);

void vge_bplay(const char *mmlPath);

void vge_bstop();

void vge_bresume();

void vge_restartCurrentSong();

void vgsbuf(char *buf, size_t size);

#ifdef __cplusplus
};
#endif

#endif
