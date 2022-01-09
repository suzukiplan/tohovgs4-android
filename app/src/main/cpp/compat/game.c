/* Main Program of the Touhou BGM on VGS */
/* (C)2013, SUZUKI PLAN */
/* Author: Y.Suzuki Apr-2013 */
#include <stdio.h>
#include <stdarg.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <ctype.h>
#include "vgeapi.h"
#include "vgeint.h"
#include "vgsdec.h"

/* Macro */
#define HIT_CHECK(X1, Y1, XS1, YS1, X2, Y2, XS2, YS2) (X1 < X2 + XS2 && X2 < X1 + XS1 && Y1 < Y2 + YS2 && Y2 < Y1 + YS1)
#define TITLE_NUM 10
#define SONG_NUM 107

/* Structure */
struct InputInf {
    int s;
    int cx;
    int cy;
    int dx;
    int dy;
};

/* song data */
struct SongData {
    int id;
    int no;
    int loop;
    int col;
    char text[64];
};

/* title data */
struct TitleData {
    int id;
    int songNum;
    char copyright[32];
    char title[80];
};

/* play list */
static struct TitleData g_title[TITLE_NUM];
static struct SongData g_listJ[SONG_NUM];
static struct SongData *g_list = g_listJ;

/* Proto types */
static void nextSong();

static void my_print(int x, int y, const char *msg, ...);

static void put_font_S(int x, int y, const char *msg, ...);

static unsigned short get_code(unsigned const char *sjis);

static void put_kanji(int x, int y, int col, const char *msg, ...);

/* Static variables */
int g_songChanged = 0;
extern int g_flingY;
extern int g_flingX;
static unsigned char *fs_kanji;
static int fs_musicCursor = -1;
static int fs_listType = 1;
static int fs_currentTitle = 4;
static char fs_msg[256];

struct Preferences {
    int currentTitleId;
    int base;
    int listType;
    int infy;
    int loop;
    int kobushi;
};
static struct Preferences PRF;

/*
 *----------------------------------------------------------------------------
 * Initialization
 *----------------------------------------------------------------------------
 */
int vge_init() {
    unsigned int sz;
    char *bin;
    int i, j;
    bin = (char *) vge_getdata(0, &sz);
    memcpy(g_listJ, bin, sizeof(g_listJ));
    g_list = g_listJ;
    bin = (char *) vge_getdata(2, &sz);
    memcpy(g_title, bin, sizeof(g_title));
    for (i = 0; i < SONG_NUM; i++) {
        for (j = 0; j < TITLE_NUM; j++) {
            if ((g_list[i].id & 0xFFFF00) >> 8 == g_title[j].id) {
                g_title[j].songNum++;
                break;
            }
        }
    }
    memset(fs_msg, 0, sizeof(fs_msg));
    strcpy(fs_msg, "Touhou BGM on VGS");
    fs_kanji = (unsigned char *) vge_getdata(255, &sz);
    return 0;
}

/*
 *----------------------------------------------------------------------------
 * Main
 *----------------------------------------------------------------------------
 */
int vge_loop() {
    static const char *tn[4] = {"TRI", "SAW", "SQ", "NOZ"};
    static int isFirst = 1;
    static double base = 4.0;
    static double baseX = 0.0;
    static double move = 0;
    static double moveX = 0;
    static int pageChange = 0;
    static int push = 0;
    static int pflag = 0;
    static int touch_off = 0;
    static int px, py;
    static int paused = 1;
    static int infy = 0;
    static int loop = 1;
    static int focus = 0;
    static int interval = 0;
    static int playwait = 0;
    static int whourai = 120;
    static int playing = 0;
    static int interval2 = 0;
    static int slide;
    static int slideX;
    static int touchSB = 0;
    static struct InputInf pi;
    static int touching = 0;
    static int selectTime = 0;
    static int selectSong = -1;
    static int kobushi;
    struct InputInf ci;
    int i, j, k;
    int ii;
    int iii;
    unsigned int u;
    int dp;
    int bmin;
    int songNum;
    int playingTitle;

    /* preferences */
    if (isFirst) {
        /* load preferences and setup */
        isFirst = 0;
        memset(&PRF, 0, sizeof(PRF));
        PRF.listType = 1;
        PRF.currentTitleId = 0x60;
        PRF.loop = 1;
        base = (double) PRF.base;
        g_list = g_listJ;
        for (i = 0; i < TITLE_NUM; i++) {
            if (g_title[i].id == PRF.currentTitleId) break;
        }
        if (i == TITLE_NUM) {
            fs_currentTitle = 0;
        } else {
            fs_currentTitle = i;
        }
        if (PRF.listType) {
            fs_listType = 1;
        } else {
            fs_listType = 0;
        }
        if (PRF.infy) {
            infy = 1;
        } else {
            infy = 0;
        }
        if (PRF.loop < 1) {
            PRF.loop = 1;
        } else if (3 < PRF.loop) {
            PRF.loop = 3;
        }
        loop = PRF.loop;
        kobushi = PRF.kobushi;
        if (0 != PRF.kobushi && 1 != PRF.kobushi) {
            PRF.kobushi = 0;
            kobushi = 0;
        }
    } else {
        /* store preferences */
        PRF.base = (int) base;
        PRF.currentTitleId = g_title[fs_currentTitle].id;
        PRF.listType = fs_listType;
        PRF.infy = infy;
        PRF.loop = loop;
        PRF.kobushi = kobushi;
    }

    /* calc bmin */
    if (fs_listType) {
        songNum = g_title[fs_currentTitle].songNum;
    } else {
        songNum = SONG_NUM;
    }
    bmin = -(songNum * 20 - 106 + fs_listType * 40);

    /* Play after wait */
    if (playwait) {
        playwait--;
        if (0 == playwait) {
            g_songChanged++;
            vge_bplay(g_list[fs_musicCursor].no);
            vgsdec_set_value(_psg, VGSDEC_REG_KOBUSHI, kobushi);
            focus = 1;
            whourai = 120;
            playing = 1;
        }
    }

    /* get touch state */
    vge_touch(&ci.s, &ci.cx, &ci.cy, &ci.dx, &ci.dy);
    if (ci.s) {
        focus = 0;
        vge_rand();
        pflag = 1;
        if (0 == touching) {
            if (!fs_listType && HIT_CHECK(ci.cx - 4, ci.cy - 4, 8, 8, 224, 130, 16, 190)) {
                touching = 1;
            } else if (fs_listType && HIT_CHECK(ci.cx - 4, ci.cy - 4, 8, 8, 0, 300, 240, 20)) {
                touching = 3;
            } else {
                touching = 2;
            }
        }
    } else if (pflag) {
        pflag = 0;
        push = 1;
        ci.s = 1;
        ci.cx = px;
        ci.cy = py;
        slide = 0;
        slideX = 0;
    } else {
        selectSong = -1;
        touching = 0;
        pflag = 0;
        push = 0;
        slide = 0;
        slideX = 0;
    }

    /* page change */
    if (fs_listType) {
        if (!pageChange) {
            if (80.0 < baseX) {
                pageChange = 1;
            } else if (baseX < -80.0) {
                pageChange = -1;
            }
        }
        if (pageChange) {
            if (0 < pageChange) {
                if (baseX < 240.0) {
                    double mv = (240 - baseX) / 6.66666;
                    if ((0 < mv && mv < 1.0) || (mv < 0 && -1.0 < mv)) {
                        baseX = 240;
                    } else {
                        baseX += mv;
                    }
                } else {
                    fs_currentTitle--;
                    if (fs_currentTitle < 0) {
                        fs_currentTitle = TITLE_NUM - 1;
                    }
                    pageChange = 0;
                    baseX = 0;
                    moveX = 0;
                    slideX = 0;
                }
            } else {
                if (-240 < baseX) {
                    double mv = (-240 - baseX) / 6.66666;
                    if ((0 < mv && mv < 1.0) || (mv < 0 && -1.0 < mv)) {
                        baseX = -240;
                    } else {
                        baseX += mv;
                    }
                } else {
                    fs_currentTitle++;
                    if (TITLE_NUM <= fs_currentTitle) {
                        fs_currentTitle = 0;
                    }
                    pageChange = 0;
                    baseX = 0;
                    moveX = 0;
                    slideX = 0;
                }
            }
        }
    }

    /* move list (left & right) */
    if (g_flingX) {
        if (fs_listType && abs(g_flingY) < abs(g_flingX) && 100 < abs(g_flingX)) {
            if (g_flingX < 0) {
                pageChange = -1;
            } else {
                pageChange = 1;
            }
        }
        g_flingX = 0;
    }
    if (!pageChange) {
        if (fs_listType) {
            if (0 == slide && 0 == slideX) {
                if (4 < abs(ci.dx) && abs(ci.dy) < abs(ci.dx)) {
                    slideX = 1;
                }
            }
        } else {
            baseX = 0;
            moveX = 0;
            slideX = 0;
        }
        if (slideX) {
            moveX += ci.cx - pi.cx;
            push = 0;
            touch_off = 1;
            slide = 0;
        } else {
            moveX = 0;
        }
        if (moveX) {
            double mv = moveX / 6.66666;
            if ((0 < mv && mv < 1.0) || (mv < 0 && -1.0 < mv)) mv = moveX;
            moveX -= mv;
            baseX += mv;
        } else if (baseX) {
            double mv = baseX / 6.66666;
            if ((0 < mv && mv < 1.0) || (mv < 0 && -1.0 < mv)) {
                baseX = 0;
            } else {
                baseX -= mv;
            }
        }
    }

    /* move list (up & down) */
    if (slide == 0) {
        if (4 < ci.dy || ci.dy < -4) {
            if (!slideX) {
                slide = 1;
            }
        }
    }
    if (slide) {
        move += ci.cy - pi.cy;
        push = 0;
        touch_off = 1;
    } else {
        move = 0;
    }
    if (move) {
        double mv = move / 6.66666;
        if ((0 < mv && mv < 1.0) || (mv < 0 && -1.0 < mv)) mv = move;
        move -= mv;
        base += mv;
        if (100 < base) {
            base = 100;
            move = 0;
            g_flingY = 0;
        } else if (base < bmin - 100) {
            base = bmin - 100;
            move = 0;
            g_flingY = 0;
        }
    }
    if (ci.s == 0 && touch_off) {
        touch_off = 0;
    }
    memcpy(&pi, &ci, sizeof(ci));

    /* Fling */
    if (touchSB) {
        touchSB--;
        g_flingY = 0;
    }
    if (ci.s == 0 && g_flingY) {
        double mv = ((double) g_flingY) / 6.0;
        if (g_flingY < 0 && -1 < mv) mv = g_flingY;
        if (0 < g_flingY && mv < 1) mv = g_flingY;
        base += mv;
        g_flingY -= (int) mv;
        if (100 < base) {
            base = 100;
            move = 0;
            g_flingY = 0;
        } else if (base < bmin - 100) {
            base = bmin - 100;
            move = 0;
            g_flingY = 0;
        }
    }

    /* Overscroll */
    if (ci.s == 0) {
        if (base < bmin) {
            double mv = (bmin - base) / 6.66666;
            if (mv < 1) {
                base = bmin;
            } else {
                base += mv;
            }
        }
        if (4 < base) {
            double mv = (base - 4) / 6.66666;
            if (mv < 1) {
                base = 4;
            } else {
                base -= mv;
            }
        }
    }

    /* Auto focus */
    if (focus) {
        if (fs_listType &&
            ((g_list[fs_musicCursor].id & 0xFFFF00) >> 8 != g_title[fs_currentTitle].id)) {
            playingTitle = (g_list[fs_musicCursor].id & 0xFFFF00) >> 8;
            if (playingTitle != g_title[fs_currentTitle].id) {
                /* check pop count of right */
                for (i = 0, iii = fs_currentTitle; playingTitle != g_title[iii].id; i++) {
                    iii++;
                    if (TITLE_NUM <= iii) iii = 0;
                }
                /* check pop count of left */
                for (ii = 0, iii = fs_currentTitle; playingTitle != g_title[iii].id; ii++) {
                    iii--;
                    if (iii < 0) iii = TITLE_NUM - 1;
                }
                /* set auto focus direction */
                if (ii < i) {
                    pageChange = 1;
                } else {
                    pageChange = -1;
                }
            }
        } else {
            if (fs_listType) {
                for (k = 0, i = 0; i < SONG_NUM; i++) {
                    if ((g_list[i].id & 0xFFFF00) >> 8 == g_title[fs_currentTitle].id) {
                        if (i == fs_musicCursor) break;
                        k++;
                    }
                }
                j = k * 20 + 130 + (int) base + 40;
                ii = 300;
            } else {
                k = fs_musicCursor;
                j = k * 20 + 130 + (int) base;
                ii = 320;
            }
            if (j < 130) {
                i = 130 - j;
                if (10 < i) i = 10;
                base += i;
            } else if (ii < j + 16) {
                i = j + 16 - ii;
                if (10 < i) i = 10;
                base -= i;
            } else {
                focus = 0;
            }
        }
    }

    for (iii = 0; iii < 2; iii++) {
        int bx = (int) baseX;
        int ct = fs_currentTitle;
        if (iii) {
            if (!fs_listType || 0 == baseX) {
                break;
            }
            if (0 < baseX) {
                bx -= 240;
                ct--;
                if (ct < 0) ct = TITLE_NUM - 1;
            } else {
                bx += 240;
                ct++;
                if (TITLE_NUM <= ct) ct = 0;
            }
        }
        if (fs_listType) {
            /* Draw song title */
            put_kanji(4 + bx, 134 + (int) base, 255, "%s", g_title[ct].title);
            put_kanji(236 + bx - ((int) strlen(g_title[ct].copyright)) * 4, 152 + (int) base, 255,
                      "%s", g_title[ct].copyright);
            if (g_title[ct].id == 0x60) {
                put_kanji((240 - (((int) strlen(fs_msg)) * 4)) / 2 + bx, 40 + (int) base, 255, "%s",
                          fs_msg);
            }
        }
        /* Draw music list */
        for (dp = 0, i = 0, ii = 0; i < SONG_NUM; i++) {
            if (fs_listType) {
                if ((g_list[i].id & 0xFFFF00) >> 8 != g_title[ct].id) {
                    continue;
                }
            }
            dp = fs_listType * 40 + (ii++) * 20 + 130 + (int) base;
            if (i < SONG_NUM && 114 < dp && dp < 320) {
                if (fs_musicCursor == i) {
                    vge_boxfSP(4 + bx, dp, 220 + fs_listType * 16 + bx, dp + 16, 75);
                    vge_boxSP(4 + bx, dp, 220 + fs_listType * 16 + bx, dp + 16, 111);
                } else {
                    if (ci.s && touch_off == 0 && 2 == touching &&
                        HIT_CHECK(ci.cx - 4, ci.cy - 4, 8, 8, bx, 130, 240, 190) &&
                        HIT_CHECK(4 + bx, dp, 216 + fs_listType * 16, 16, ci.cx - 4, ci.cy - 4, 8,
                                  8)) {
                        ci.s = 0;
                        if (selectSong != i) {
                            selectTime = 0;
                            selectSong = i;
                        } else {
                            selectTime++;
                        }
                        if (selectTime < 4) {
                            vge_boxfSP(4 + bx, dp, 220 + fs_listType * 16 + bx, dp + 16,
                                       g_list[i].col);
                            vge_boxSP(4 + bx, dp, 220 + fs_listType * 16 + bx, dp + 16, 105);
                        } else {
                            vge_boxfSP(4 + bx, dp, 220 + fs_listType * 16 + bx, dp + 16, 60);
                            vge_boxSP(4 + bx, dp, 220 + fs_listType * 16 + bx, dp + 16, 111);
                            if (push) {
                                push = 0;
                                ci.s = 0;
                                fs_musicCursor = i;
                                paused = 0;
                                vge_bstop();
                                playwait = 6;
                                playing = 0;
                            }
                        }
                    } else {
                        vge_boxfSP(4 + bx, dp, 220 + fs_listType * 16 + bx, dp + 16,
                                   g_list[i].col);
                        vge_boxSP(4 + bx, dp, 220 + fs_listType * 16 + bx, dp + 16, 105);
                    }
                }
                if (fs_listType) {
                    put_font_S(8 + bx, dp + 7, "%3d.", g_list[i].id & 0xff);
                } else {
                    put_font_S(8 + bx, dp + 7, "%3d.", ii);
                }
                put_kanji(27 + bx, dp + 4, 1, "%s", g_list[i].text);
                put_kanji(26 + bx, dp + 3, 255, "%s", g_list[i].text);
            }
        }
        dp += 20;
        my_print(4 + bx, dp + 5, "Composed by ZUN.");
        if (fs_listType) {
            put_kanji(8 + bx, dp + 15, 255,
                      "This app is an alternative fiction of the Touhou Project.");
            put_kanji(140 + bx, dp + 30, 255, "Arranged by Yoji Suzuki.");
            put_kanji(100 + bx, dp + 42, 255, "(c)2013, Presented by SUZUKI PLAN.");
        } else {
            put_kanji(4 + bx, dp + 15, 255,
                      "This app is an alternative fiction of Touhou Project.");
            vge_putSP(0, 0, 112, 136, 48, 4 + bx, dp + 30);
        }
    }

    /* Scroll bar */
    if (!fs_listType) {
        vge_boxfSP(224, 130, 240, 320, 103);
        if (1 == touching) {
            touchSB = 30;
            base = ci.cy - 142;
            base *= ((double) (-bmin * 100)) / 166.0;
            base /= 100;
            base = 4 - base;
            if (100 < base) {
                base = 100;
                move = 0;
                g_flingY = 0;
            } else if (base < bmin - 100) {
                base = bmin - 100;
                move = 0;
                g_flingY = 0;
            }
            i = (0 - (int) base + 4) * 100 / (-bmin) * 116 / 100;
            vge_boxfSP(225, 142 + i, 238, 192 + i, 56);
        } else {
            i = (0 - (int) base + 4) * 100 / (-bmin) * 116 / 100;
            vge_boxfSP(225, 142 + i, 238, 192 + i, 108);
        }

        /* Cursor(Top) */
        if (base >= 4) {
            vge_putSP(0, 208, 32, 16, 12, 224, 130);
        } else {
            if (1 == touching && HIT_CHECK(224, 130, 16, 12, ci.cx - 8, ci.cy - 8, 16, 16)) {
                touchSB = 30;
                vge_putSP(0, 176, 32, 16, 12, 224, 130);
                if (push) {
                    base = 4;
                    push = 0;
                }
            } else {
                vge_putSP(0, 144, 32, 16, 12, 224, 130);
            }
        }

        /* Cursor(Bottom) */
        if (base <= bmin) {
            vge_putSP(0, 224, 32, 16, 12, 224, 308);
        } else {
            if (1 == touching && HIT_CHECK(224, 308, 16, 12, ci.cx - 8, ci.cy - 8, 16, 16)) {
                touchSB = 30;
                vge_putSP(0, 192, 32, 16, 12, 224, 308);
                if (push) {
                    base = bmin;
                    push = 0;
                }
            } else {
                vge_putSP(0, 160, 32, 16, 12, 224, 308);
            }
        }
    }

    /* Title list */
    if (fs_listType) {
        vge_boxfSP(0, 300, 240, 320, 3);
        vge_lineSP(0, 300, 240, 300, 111);
        vge_lineSP(0, 302, 240, 302, 106);
        ii = (240 - TITLE_NUM * 8) / 2;
        j = ii - 1;
        for (i = 0; i < TITLE_NUM; i++, ii += 8) {
            vge_putSP(0, 216, 64, 8, 8, ii, 307);
            if (fs_currentTitle == i) {
                int bx = (int) baseX / 32;
                if (bx < -8)
                    bx = -8;
                else if (8 < bx)
                    bx = 8;
                vge_putSP(0, 224, 64, 8, 8, ii - bx, 307);
                vge_putSP(0, 224, 64, 8, 8, ii - bx + TITLE_NUM * 8, 307);
                vge_putSP(0, 224, 64, 8, 8, ii - bx - TITLE_NUM * 8, 307);
            }
        }
        vge_boxfSP(0, 307, j, 315, 3);
        vge_boxfSP(ii, 307, 240, 315, 3);
        /* left button */
        if (0 < pageChange) {
            vge_putSP(0, 16, 192, 16, 16, 0, 304);
        } else {
            if (pageChange) {
                vge_putSP(0, 0, 192, 16, 16, 0, 304);
            } else {
                if (0 == touch_off && 3 == touching &&
                    HIT_CHECK(ci.cx - 4, ci.cy - 4, 8, 8, 0, 304, 16, 16)) {
                    vge_putSP(0, 16, 192, 16, 16, 0, 304);
                    if (push) {
                        pageChange = 1;
                    }
                } else {
                    vge_putSP(0, 0, 192, 16, 16, 0, 304);
                }
            }
        }
        /* right button */
        if (pageChange < 0) {
            vge_putSP(0, 48, 192, 16, 16, 224, 304);
        } else {
            if (pageChange) {
                vge_putSP(0, 32, 192, 16, 16, 224, 304);
            } else {
                if (0 == touch_off && 3 == touching &&
                    HIT_CHECK(ci.cx - 4, ci.cy - 4, 8, 8, 224, 304, 16, 16)) {
                    vge_putSP(0, 48, 192, 16, 16, 224, 304);
                    if (push) {
                        pageChange = -1;
                    }
                } else {
                    vge_putSP(0, 32, 192, 16, 16, 224, 304);
                }
            }
        }
    }

    /* Draw play pannel */
    vge_boxfSP(0, 0, 240, 130, 3);
    my_print(22, 2, "Touhou BGM on VGS");

    /* RegBoard */
    vge_boxfSP(0, 14, 240, 105, 51);
    vge_lineSP(0, 13, 240, 13, 111);
    vge_lineSP(0, 106, 240, 106, 111);
    vge_lineSP(0, 108, 240, 108, 106);
    vge_lineSP(0, 129, 240, 129, 111);
    vge_lineSP(0, 127, 240, 127, 106);
    if (fs_musicCursor < 0) {
        put_font_S(8, 16, "INDEX     %05d", vgsdec_get_value(_psg, VGSDEC_REG_LENGTH));
    } else {
        if (g_list[fs_musicCursor].loop) {
            if (infy) {
                put_font_S(8, 16, "INDEX     %05d  PLAYING %d",
                           vgsdec_get_value(_psg, VGSDEC_REG_INDEX),
                           vgsdec_get_value(_psg, VGSDEC_REG_LOOP_COUNT) + 1);
            } else {
                if (vgsdec_get_value(_psg, VGSDEC_REG_LOOP_COUNT) < loop) {
                    put_font_S(8, 16, "INDEX     %05d  PLAYING %d OF %d",
                               vgsdec_get_value(_psg, VGSDEC_REG_INDEX),
                               vgsdec_get_value(_psg, VGSDEC_REG_LOOP_COUNT) + 1, loop);
                } else {
                    put_font_S(8, 16, "INDEX     %05d  FADEOUT",
                               vgsdec_get_value(_psg, VGSDEC_REG_INDEX));
                }
            }
        } else {
            put_font_S(8, 16, "INDEX     %05d  ACYCLIC SONG",
                       vgsdec_get_value(_psg, VGSDEC_REG_INDEX));
        }
    }
    if (0 == infy) {
        int ss;
        int sm;
        if (g_list[fs_musicCursor].loop) {
            int introLen = vgsdec_get_value(_psg, VGSDEC_REG_LOOP_TIME);
            int loopLen = vgsdec_get_value(_psg, VGSDEC_REG_TIME_LENGTH) - introLen;
            ss = introLen + loopLen * loop;
            ss += vgsdec_get_value(_psg, VGSDEC_REG_PLAYING) * 102400;
            ss -= vgsdec_get_value(_psg, VGSDEC_REG_TIME);
            ss -= vgsdec_get_value(_psg, VGSDEC_REG_LOOP_COUNT) * loopLen;
            ss /= 22050;
            if (ss < 0) ss = 0;
            sm = ss / 60;
            ss -= sm * 60;
        } else {
            ss = vgsdec_get_value(_psg, VGSDEC_REG_TIME_LENGTH);
            ss -= vgsdec_get_value(_psg, VGSDEC_REG_TIME);
            ss /= 22050;
            if (ss < 0) ss = 0;
            sm = ss / 60;
            ss -= sm * 60;
        }
        put_font_S(8, 24, "LEFT TIME %02d:%02d", sm, ss);
    } else {
        put_font_S(8, 24, "LEFT TIME INFINITY");
    }
    /* seek */
    vge_boxSP(12, 34, 235, 42, 53);
    vge_lineSP(12, 34, 12, 42, 48);
    vge_lineSP(12, 34, 235, 34, 48);
    vge_lineSP(16, 37, 229, 37, 53);
    vge_lineSP(16, 39, 229, 39, 48);
    vge_putSP(0, 232, 64, 8, 8, 4, 34);
    if (vgsdec_get_value(_psg, VGSDEC_REG_PLAYING)) {
        u = vgsdec_get_value(_psg, VGSDEC_REG_TIME) / 22050;
        ii = vgsdec_get_value(_psg, VGSDEC_REG_TIME_LENGTH) / 22050;
        ii = (int) (u * 212 / ii);
        vge_lineSP(16 + ii, 35, 16 + ii, 41, 109);
        vge_lineSP(17 + ii, 35, 17 + ii, 41, 103);

        if (0 == touch_off) {
            if (HIT_CHECK(ci.cx - 4, ci.cy - 4, 8, 8, 4, 34, 8, 8)) {
                vge_putSP(0, 240, 64, 8, 8, 4, 34);
                if (push) {
                    ci.s = 0;
                    paused = 0;
                    vge_bstop();
                    playwait = 6;
                    playing = 0;
                    push = 0;
                }
            }
            if (push && HIT_CHECK(ci.cx - 4, ci.cy - 4, 8, 8, 16, 26, 212, 24)) {
                i = ci.cx;
                i -= 16;
                if (i < 0) i = 0;
                if (212 < i) i = 212;
                vgsdec_set_value(_psg, VGSDEC_REG_TIME,
                                 vgsdec_get_value(_psg, VGSDEC_REG_TIME_LENGTH) * i / 212);
                push = 0;
            }
        }
    }

    /* piano */
    for (i = 0; i < 6; i++) {
        put_font_S(4, 46 + i * 10, "CH%d %s", i, tn[vgsdec_get_value(_psg, VGSDEC_REG_TONE_0 + i)]);
        vge_putSP(0, 0, 208, 200, 8, 36, 46 + i * 10);
        if (vgsdec_get_value(_psg, VGSDEC_REG_VOL_0 + i) != 0 ||
            vgsdec_get_value(_psg, VGSDEC_REG_KEYON_0 + i) != 0) {
            k = vgsdec_get_value(_psg, VGSDEC_REG_KEY_0 + i);
            switch (k % 12) {
                case 0:
                    j = 0;
                    ii = 0;
                    break;
                case 2:
                    j = 0;
                    ii = 1;
                    break;
                case 3:
                    j = 0;
                    ii = 2;
                    break;
                case 5:
                    j = 0;
                    ii = 3;
                    break;
                case 7:
                    j = 0;
                    ii = 4;
                    break;
                case 8:
                    j = 0;
                    ii = 5;
                    break;
                case 10:
                    j = 0;
                    ii = 6;
                    break;
                case 1:
                    j = 1;
                    ii = 0;
                    break;
                case 4:
                    j = 1;
                    ii = 2;
                    break;
                case 6:
                    j = 1;
                    ii = 3;
                    break;
                case 9:
                    j = 1;
                    ii = 5;
                    break;
                case 11:
                    j = 1;
                    ii = 6;
                    break;
                default:
                    j = 0;
                    ii = 0;
            }
            if (0 == j) {
                vge_boxfSP(k / 12 * 28 + ii * 4 + 36, 46 + i * 10, k / 12 * 28 + ii * 4 + 38,
                           52 + i * 10, 31);
            } else {
                vge_boxfSP(k / 12 * 28 + ii * 4 + 38, 46 + i * 10, k / 12 * 28 + ii * 4 + 40,
                           51 + i * 10, 31);
            }
        }
    }

    /* set X position of the buttons */
    if (fs_listType) {
        ii = 26;
    } else {
        ii = 0;
    }

    /* PLAY button */
    if (paused) {
        if (-1 == fs_musicCursor) {
            vge_putSP(0, 120, 32, 24, 12, 2 + ii, 112);
        } else {
            if (ci.s && touch_off == 0 &&
                HIT_CHECK(2 + ii, 92, 24, 32, ci.cx - 4, ci.cy - 4, 8, 8)) {
                vge_putSP(0, 48, 32, 24, 12, 2 + ii, 112);
                if (push) {
                    paused = 0;
                    vge_bresume();
                    playing = 1;
                }
            } else {
                vge_putSP(0, 0, 32, 24, 12, 2 + ii, 112);
            }
        }
    } else {
        /* PAUSE button */
        if (-1 == fs_musicCursor) {
            vge_putSP(0, 24, 32, 24, 12, 2 + ii, 112);
        } else {
            if (ci.s && touch_off == 0 &&
                HIT_CHECK(2 + ii, 92, 24, 32, ci.cx - 4, ci.cy - 4, 8, 8)) {
                vge_putSP(0, 72, 32, 24, 12, 2 + ii, 112);
                if (push) {
                    paused = 1;
                    vge_bstop();
                    playing = 0;
                }
            } else {
                vge_putSP(0, 24, 32, 24, 12, 2 + ii, 112);
            }
        }
    }

    /* INFINITE button */
    if (ci.s && touch_off == 0 && HIT_CHECK(28 + ii, 92, 24, 32, ci.cx - 4, ci.cy - 4, 8, 8)) {
        vge_putSP(0, 168, 80, 24, 12, 28 + ii, 112);
        if (push) {
            infy = 1 - infy;
        }
    } else {
        vge_putSP(0, 144 + (1 - infy) * 48, 80, 24, 12, 28 + ii, 112);
    }

    if (!infy) {
        /* LOOP COUNTER */
        if (-1 == fs_musicCursor || (0 <= fs_musicCursor && g_list[fs_musicCursor].loop)) {
            if (ci.s && touch_off == 0 &&
                HIT_CHECK(80 + ii, 92, 24, 32, ci.cx - 4, ci.cy - 4, 8, 8)) {
                vge_putSP(0, (loop - 1) * 24, 176, 24, 12, 80 + ii, 112);
                if (push) {
                    loop++;
                    if (3 < loop) loop = 1;
                }
            } else {
                vge_putSP(0, (loop - 1) * 24, 160, 24, 12, 80 + ii, 112);
            }
        }
    }

    /* KOBUSHI button */
    if (ci.s && touch_off == 0 && HIT_CHECK(212, 92, 24, 32, ci.cx - 4, ci.cy - 4, 8, 8)) {
        vge_putSP(0, 24, 216, 24, 12, 240 - 28, 112);
        if (push) {
            kobushi = 1 - kobushi;
            if (vgsdec_get_value(_psg, VGSDEC_REG_PLAYING)) {
                push = 0;
                ci.s = 0;
                paused = 0;
                vge_bstop();
                playwait = 6;
                playing = 0;
            }
        }
    } else {
        vge_putSP(0, 48 - kobushi * 48, 216, 24, 12, 212, 112);
    }

    // acyclic song
    if (-1 == vgsdec_get_value(_psg, VGSDEC_REG_LOOP_INDEX) &&
        !vgsdec_get_value(_psg, VGSDEC_REG_PLAYING)) {
        if (0 == interval2) {
            interval2 = 1;
        } else {
            interval2++;
            if (60 <= interval2) {
                if (infy == 0) {
                    nextSong();
                }
                paused = 0;
                playing = 0;
                playwait = 6;
                focus = 1;
                interval = 0;
                interval2 = 0;
            }
        }
    } else {
        interval2 = 0;
    }

    // cyclic songs
    if (-1 != fs_musicCursor && 0 == infy && g_list[fs_musicCursor].loop &&
        loop <= vgsdec_get_value(_psg, VGSDEC_REG_LOOP_COUNT)) {
        if (vgsdec_get_value(_psg, VGSDEC_REG_FADEOUT_COUNTER) == 0) {
            vgsdec_set_value(_psg, VGSDEC_REG_FADEOUT, 1);
            interval = 0;
        }
        if (100 <= vgsdec_get_value(_psg, VGSDEC_REG_FADEOUT_COUNTER)) {
            if (interval < 30) {
                interval++;
            } else {
                nextSong();
                paused = 0;
                vge_bstop();
                playing = 0;
                playwait = 6;
                focus = 1;
                interval = 0;
            }
        }
    }

    px = ci.cx;
    py = ci.cy;
    return 0;
}

/*
 *----------------------------------------------------------------------------
 * change to the next song
 *----------------------------------------------------------------------------
 */
static void nextSong() {
    int ct = fs_currentTitle;
    int songTop;

    /* calc songTop */
    if (fs_listType) {
        for (songTop = 0;; songTop++) {
            if ((g_list[songTop].id & 0xFFFF00) >> 8 == g_title[ct].id) {
                break;
            }
        }
    }

    /* secuencial mode */
    fs_musicCursor++;
    if (SONG_NUM <= fs_musicCursor) {
        fs_musicCursor = 0;
    }
}

/*
 *----------------------------------------------------------------------------
 * print 8x8 font
 *----------------------------------------------------------------------------
 */
static void my_print(int x, int y, const char *msg, ...) {
    char buf[256];
    int i;
    int c;
    int d;
    va_list args;

    va_start(args, msg);
    vsprintf(buf, msg, args);
    va_end(args);

    for (i = 0; '\0' != (c = (int) buf[i]); i++, x += 8) {
        c -= 0x20;
        c &= 0x7f;
        d = c >> 5;
        vge_putSPM(0, (c - (d << 5)) << 3, d << 3, 8, 8, x + 1, y + 1, 1);
        vge_putSP(0, (c - (d << 5)) << 3, d << 3, 8, 8, x, y);
    }
}

/*
 *----------------------------------------------------------------------------
 * print 4x8 font
 *----------------------------------------------------------------------------
 */
static void put_font_S(int x, int y, const char *msg, ...) {
    char buf[64];
    int i;
    char c;
    va_list args;

    va_start(args, msg);
    vsprintf(buf, msg, args);
    va_end(args);

    for (i = 0; '\0' != (c = buf[i]); i++) {
        if ('0' <= c && c <= '9') {
            c -= '0';
            vge_putSPM(0, c * 4, 24, 4, 8, x + i * 4 + 1, y + 1, 1);
            vge_putSP(0, c * 4, 24, 4, 8, x + i * 4, y);
        } else if ('A' <= c && c <= 'Z') {
            c -= 'A';
            vge_putSPM(0, 40 + c * 4, 24, 4, 8, x + i * 4 + 1, y + 1, 1);
            vge_putSP(0, 40 + c * 4, 24, 4, 8, x + i * 4, y);
        } else if ('.' == c) {
            vge_putSPM(0, 144, 24, 4, 8, x + i * 4 + 1, y + 1, 1);
            vge_putSP(0, 144, 24, 4, 8, x + i * 4, y);
        } else if (':' == c) {
            vge_putSPM(0, 148, 24, 4, 8, x + i * 4 + 1, y + 1, 1);
            vge_putSP(0, 148, 24, 4, 8, x + i * 4, y);
        }
    }
}

static unsigned short get_code(unsigned const char *sjis) {
    unsigned char jis[2];
    unsigned short ret;
    jis[0] = sjis[0];
    jis[1] = sjis[1];
    if (jis[0] <= 0x9f) {
        jis[0] -= 0x71;
    } else {
        jis[0] -= 0xb1;
    }
    jis[0] *= 2;
    jis[0]++;
    if (jis[1] >= 0x7F) {
        jis[1] -= 0x01;
    }
    if (jis[1] >= 0x9e) {
        jis[1] -= 0x7d;
        jis[0]++;
    } else {
        jis[1] -= 0x1f;
    }
    ret = (jis[0] - 0x21) * 94;
    ret += jis[1] - 0x21;
    return ret;
}

static void put_kanji(int x, int y, int col, const char *msg, ...) {
    char buf[256];
    int i, j, k;
    unsigned char c[2];
    int jis;
    unsigned char bin;
    va_list args;

    va_start(args, msg);
    vsprintf(buf, msg, args);
    va_end(args);

    for (i = 0; '\0' != (c[0] = (unsigned char) buf[i]); i++) {
        if (c[0] & 0x80) {
            c[1] = (unsigned char) buf[i + 1];
            jis = (int) get_code(c);
            jis *= 12;
            for (j = 0; j < 12; j++) {
                bin = fs_kanji[jis + j];
                for (k = 0; k < 8; k++, bin <<= 1) {
                    if (bin & 0x80) {
                        vge_pixelSP(x + k, y + j, col);
                    }
                }
            }
            x += 8;
            i++;
        } else {
            c[0] -= 0x20;
            vge_putSPM(255, c[0] % 16 * 4, c[0] / 16 * 12, 4, 12, x, y, col);
            x += 4;
        }
    }
}