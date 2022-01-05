/**
 * ©2022, SUZUKI PLAN
 * License: https://github.com/suzukiplan/tohovgs4-android/blob/master/LICENSE.txt
 */
package com.suzukiplan.tohovgs.api

import com.suzukiplan.tohovgs.BuildConfig

class Constants {
    companion object {
        val bannerAdsId = if (BuildConfig.DEBUG) {
            "ca-app-pub-3940256099942544/6300978111"
        } else {
            BuildConfig.ADS_ID_BANNER
        }
        val rewardAdsId = if (BuildConfig.DEBUG) {
            "ca-app-pub-3940256099942544/5224354917"
        } else {
            BuildConfig.ADS_ID_REWARD
        }
        val interstitialAdsId = if (BuildConfig.DEBUG) {
            "ca-app-pub-3940256099942544/1033173712"
        } else {
            BuildConfig.ADS_ID_INTERSTITIAL
        }
    }
}