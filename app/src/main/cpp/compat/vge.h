#ifndef _INCLUDE_VGE_H
#define _INCLUDE_VGE_H

#ifdef __cplusplus
extern "C" {
#endif

#define XSIZE 240
#define YSIZE 320

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
                           int localeId);

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

unsigned char vge_getmute();

void vge_bplay(const char *mmlPath);

void vge_bstop();

void vge_bresume();

#ifdef __cplusplus
};
#endif

#endif
