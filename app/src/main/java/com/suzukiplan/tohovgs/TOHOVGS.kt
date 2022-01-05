/**
 * ©2022, SUZUKI PLAN
 * License: https://github.com/suzukiplan/tohovgs4-android/blob/master/LICENSE.txt
 */
package com.suzukiplan.tohovgs

import android.app.Application

class TOHOVGS : Application() {
    init {
        System.loadLibrary("native-lib")
    }
}