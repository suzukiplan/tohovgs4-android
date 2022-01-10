/*
  ©2022, SUZUKI PLAN
  License: https://github.com/suzukiplan/tohovgs4-android/blob/master/LICENSE.txt
 */
package com.suzukiplan.tohovgs.api;

import android.content.res.AssetManager;
import android.graphics.Bitmap;

public class JNI {
    public static native long createDecoder();

    public static native void releaseDecoder(long context);

    public static native void load(long context, byte[] mml);

    public static native void decode(long context, byte[] buf);

    public static native int loopCount(long context);

    public static native void fadeout(long context);

    public static native boolean isPlaying(long context);

    public static native int getTimeLength(long context);

    public static native int getTime(long context);

    public static native void seek(long context, int progress);

    public static native void compatCleanUp();

    public static native void compatAllocate(int nTitle, int nSong, AssetManager assetManager);

    public static native void compatLoadGraphic(int n, byte[] data);

    public static native void compatLoadKanji(byte[] data);

    public static native void compatAddTitle(int index, int id, int songNum, byte[] title, byte[] copyright);

    public static native void compatAddSong(int index, int id, int no, int loop, int col, byte[] mmlPath, byte[] title);

    public static native void compatTick(Bitmap bitmap);

    public static native void compatOnTouch(int cx, int cy, int dx, int dy);

    public static native void compatOnReleaseTouch();

    public static native void compatOnFling(int fx, int fy);
}
