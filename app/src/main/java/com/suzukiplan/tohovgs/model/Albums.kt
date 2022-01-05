/**
 * ©2022, SUZUKI PLAN
 * License: https://github.com/suzukiplan/tohovgs4-android/blob/master/LICENSE.txt
 */
package com.suzukiplan.tohovgs.model

import com.google.gson.annotations.SerializedName

data class Albums(@SerializedName("albums") val albums: List<Album>)