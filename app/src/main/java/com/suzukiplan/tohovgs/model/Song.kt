/**
 * ©2022, SUZUKI PLAN
 * License: https://github.com/suzukiplan/tohovgs4-android/blob/master/LICENSE.txt
 */
package com.suzukiplan.tohovgs.model

import android.content.Context
import com.google.gson.annotations.SerializedName
import com.suzukiplan.tohovgs.MainActivity
import java.io.File
import java.io.FileNotFoundException

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

    fun readMML(mainActivity: MainActivity?, done: (mml: ByteArray?) -> Unit) {
        mainActivity?.executeAsync {
            try {
                val inputStream = mainActivity.assets?.open("mml/${mml}.mml")
                if (null == inputStream) {
                    done.invoke(getDownloadFile(mainActivity).readBytes())
                } else {
                    val result = inputStream.readBytes()
                    inputStream.close()
                    done.invoke(result)
                }
            } catch (e: FileNotFoundException) {
                done.invoke(getDownloadFile(mainActivity).readBytes())
            }
        }
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