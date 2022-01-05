/**
 * ©2022, SUZUKI PLAN
 * License: https://github.com/suzukiplan/tohovgs4-android/blob/master/LICENSE.txt
 */
package com.suzukiplan.tohovgs.api

import android.app.job.JobParameters
import android.app.job.JobService

class MusicJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        Logger.d("onStartJob: id=${params?.jobId}")
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        Logger.d("onStopJob: id=${params?.jobId}")
        return true
    }
}
