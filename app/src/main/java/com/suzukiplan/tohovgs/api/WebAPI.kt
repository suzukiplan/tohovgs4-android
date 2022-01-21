/**
 * ©2022, SUZUKI PLAN
 * License: https://github.com/suzukiplan/tohovgs4-android/blob/master/LICENSE.txt
 */
package com.suzukiplan.tohovgs.api

import com.suzukiplan.tohovgs.BuildConfig
import com.suzukiplan.tohovgs.MainActivity
import com.suzukiplan.tohovgs.model.Song
import com.suzukiplan.tohovgs.model.SongList
import okhttp3.OkHttpClient
import okhttp3.Request

class WebAPI(private val mainActivity: MainActivity) {
    private val client = OkHttpClient.Builder().build()

    fun check(version: String, done: (updatable: Boolean?) -> Unit) = getAsync("/songlist.ver") {
        done(if (null != it) version < it else null)
    }

    fun downloadSongList(done: (songList: SongList?) -> Unit) = getAsync("/songlist.json") {
        if (null != it) {
            done(mainActivity.gson.fromJson(it, SongList::class.java))
        } else done(null)
    }

    fun downloadMML(song: Song) = getSync("/${song.mml}.mml")

    private fun getAsync(path: String, done: (body: String?) -> Unit) =
        mainActivity.executeAsync { done(getSync(path)) }

    private fun getSync(path: String): String? {
        Logger.d("GET $path")
        val response = client.newCall(
            Request.Builder().url("${BuildConfig.API_SERVER_BASE_URI}$path").build()
        ).execute()
        val body = response.body?.string()
        return if (response.code != 200 || null == body) {
            Logger.e("GET $path failed (${response.code})")
            null
        } else {
            Logger.e("GET $path succeed (${body.length} bytes)")
            body
        }
    }
}