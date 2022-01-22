/**
 * ©2022, SUZUKI PLAN
 * License: https://github.com/suzukiplan/tohovgs4-android/blob/master/LICENSE.txt
 */
package com.suzukiplan.tohovgs.model

import android.content.Context
import com.google.gson.annotations.SerializedName
import java.io.File

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

    fun getDownloadFile(context: Context?) = File("${context?.filesDir?.path}/$mml.mml")

    fun checkExistMML(context: Context?): Boolean {
        val assetInputStream = context?.assets?.open("mml/${mml}.mml")
        if (null != assetInputStream) {
            assetInputStream.close()
            return true
        }
        val file = getDownloadFile(context)
        return file.exists() && file.isFile && 0L < file.length()
    }

    fun readMML(context: Context?): ByteArray? {
        val inputStream = context?.assets?.open("mml/${mml}.mml") ?: return null
        val result = inputStream.readBytes()
        inputStream.close()
        return result
    }
}