#ifndef _INCLUDE_VGEAPI_H
#define _INCLUDE_VGEAPI_H

#ifdef __cplusplus
extern "C" {
#endif

void vge_putSP(unsigned char n,int sx,int sy,int xs,int ys,int dx,int dy);
void vge_putSPM(unsigned char n,int sx,int sy,int xs,int ys,int dx,int dy,unsigned char c);
void vge_pixelSP(int x,int y,unsigned char c);
void vge_lineSP(int fx,int fy,int tx,int ty,unsigned char c);
void vge_boxSP(int fx,int fy,int tx,int ty,unsigned char c);
void vge_boxfSP(int fx,int fy,int tx,int ty,unsigned char c);
void vge_touch(int* s,int* cx,int* cy,int* dx,int* dy);
unsigned char vge_getmute();
void vge_bplay(unsigned char n);
void vge_bstop();
void vge_bresume();
const char* vge_getdata(unsigned char n,unsigned int* size);
int vge_rand();

#ifdef __cplusplus
};
#endif

#endif