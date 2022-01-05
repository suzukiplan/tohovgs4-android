/*
  ©2022, SUZUKI PLAN
  License: https://github.com/suzukiplan/tohovgs4-android/blob/master/LICENSE.txt
 */
package com.suzukiplan.tohovgs.api;

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
}
