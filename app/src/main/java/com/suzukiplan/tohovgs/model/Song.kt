/**
 * ©2022, SUZUKI PLAN
 * License: https://github.com/suzukiplan/tohovgs4-android/blob/master/LICENSE.txt
 */
package com.suzukiplan.tohovgs.model

import android.content.Context
import com.google.gson.annotations.SerializedName

data class Song(
    @SerializedName("name") val name: String,
    @SerializedName("english") val english: String?,
    @SerializedName("mml") val mml: String,
    @SerializedName("loop") val loop: Int,
    var parentAlbum: Album? = null,
    var playing: Boolean = false,
    var needReload: Boolean = false,
) {
    val nameE: String get() = english ?: name
    fun readMML(context: Context?): ByteArray? {
        val inputStream = context?.assets?.open("mml/${mml}.mml") ?: return null
        val result = inputStream.readBytes()
        inputStream.close()
        return result
    }
}