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
    @SerializedName("appleId") val appleId: String?,
    @SerializedName("name") val name: String,
    @SerializedName("english") val english: String?,
    @SerializedName("mml") val mml: String,
    @SerializedName("ver") private val verRaw: Int?,
    @SerializedName("loop") val loop: Int,
    @SerializedName("parentAlbumId") var parentAlbumId: String?,
    var parentAlbum: Album? = null,
    var status: Status? = null,
    var needReload: Boolean = false,
    var primaryUsage: PrimaryUsage = PrimaryUsage.Assets,
) {
    enum class Status {
        Stop,
        Play,
        Pause,
    }

    enum class PrimaryUsage {
        Assets,
        Files,
    }

    val nameE: String get() = english ?: name

    val ver: Int get() = verRaw ?: 0

    val appleMusicURL: String?
        get() = if (null != appleId && null != parentAlbum?.appleId) {
            "https://music.apple.com/jp/album/${parentAlbum?.appleId}?i=$appleId"
        } else {
            null
        }

    fun getDownloadFile(context: Context?) = File("${context?.filesDir?.path}/$mml.mml")

    fun checkExistMML(context: Context?): Boolean {
        try {
            val assetInputStream = context?.assets?.open("mml/${mml}.mml")
            if (null != assetInputStream) {
                assetInputStream.close()
                return true
            }
        } catch (_: Exception) {
        }
        return getDownloadFile(context).exists()
    }

    fun readMML(mainActivity: MainActivity?, done: (mml: ByteArray?) -> Unit) {
        mainActivity?.executeAsync {
            when (primaryUsage) {
                PrimaryUsage.Assets -> {
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
                PrimaryUsage.Files -> {
                    val downloadFile = getDownloadFile(mainActivity)
                    if (downloadFile.exists()) {
                        done.invoke(downloadFile.readBytes())
                    } else {
                        val inputStream = mainActivity.assets?.open("mml/${mml}.mml")
                        val result = inputStream?.readBytes()
                        inputStream?.close()
                        done.invoke(result)
                    }
                }
            }
        }
    }

    fun pathMML(context: Context?): String {
        when (primaryUsage) {
            PrimaryUsage.Assets -> {
                try {
                    val inputStream = context?.assets?.open("mml/${mml}.mml")
                    if (null != inputStream) {
                        inputStream.close()
                        return "mml/$mml.mml"
                    }
                } catch (_: java.lang.Exception) {
                }
                return getDownloadFile(context).path
            }
            PrimaryUsage.Files -> {
                val downloadFile = getDownloadFile(context)
                return if (downloadFile.exists()) {
                    downloadFile.path
                } else {
                    "mml/$mml.mml"
                }
            }
        }
    }
}