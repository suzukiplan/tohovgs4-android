/**
 * ©2022, SUZUKI PLAN
 * License: https://github.com/suzukiplan/tohovgs4-android/blob/master/LICENSE.txt
 */
package com.suzukiplan.tohovgs

import android.app.Application
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.suzukiplan.TOHOVGS.BuildConfig

class TOHOVGS : Application() {
    init {
        System.loadLibrary("native-lib")
    }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                VmPolicy.Builder()
                    .penaltyLog()
                    .penaltyDeath()
                    .build()
            )
        }
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)   
    }
}