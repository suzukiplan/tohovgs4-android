/**
 * ©2022, SUZUKI PLAN
 * License: https://github.com/suzukiplan/tohovgs4-android/blob/master/LICENSE.txt
 */
package com.suzukiplan.tohovgs.model

import com.google.gson.annotations.SerializedName

data class Album(
    @SerializedName("albumId") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("defaultLocked") val defaultLocked: Boolean,
    @SerializedName("songs") val songs: List<Song>,
)
