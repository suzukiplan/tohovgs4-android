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
    @SerializedName("parentAlbumId") var parentAlbumId: String?,
    var parentAlbum: Album? = null,
    var status: Status? = null,
    var needReload: Boolean = false,
) {
    enum class Status {
        Stop,
        Play,
        Pause,
    }

    val nameE: String get() = english ?: name

    fun getDownloadFile(context: Context?) = File("${context?.filesDir?.path}/$mml.mml")

    fun checkExistMML(context: Context?): Boolean {
        try {
            val assetInputStream = context?.assets?.open("mml/${mml}.mml")
            if (null != assetInputStream) {
                assetInputStream.close()
                return true
            }
        } catch (e: Exception) {
        }
        return getDownloadFile(context).exists()
    }

    fun readMML(context: Context?): ByteArray? {
        try {
            val inputStream = context?.assets?.open("mml/${mml}.mml") ?: return null
            val result = inputStream.readBytes()
            inputStream.close()
            return result
        } catch (e: java.lang.Exception) {
        }
        val file = getDownloadFile(context) ?: return null
        return file.readBytes()
    }

    fun pathMML(context: Context?): String {
        try {
            val inputStream = context?.assets?.open("mml/${mml}.mml")
            if (null != inputStream) {
                inputStream.close()
                return "mml/$mml.mml"
            }
        } catch (e: java.lang.Exception) {
        }
        return getDownloadFile(context).path
    }
}