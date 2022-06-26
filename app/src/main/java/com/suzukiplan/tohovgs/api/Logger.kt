/**
 * ©2022, SUZUKI PLAN
 * License: https://github.com/suzukiplan/tohovgs4-android/blob/master/LICENSE.txt
 */
package com.suzukiplan.tohovgs.api

import android.util.Log
import com.suzukiplan.TOHOVGS.BuildConfig

class Logger {
    companion object {
        private const val tag = "TOHOVGS"

        fun d(message: String) {
            if (BuildConfig.DEBUG) Log.d(tag, message)
        }

        fun e(message: String) {
            if (BuildConfig.DEBUG) Log.e(tag, message)
        }
    }
}