/**
 * ©2022, SUZUKI PLAN
 * License: https://github.com/suzukiplan/tohovgs4-android/blob/master/LICENSE.txt
 */
package com.suzukiplan.tohovgs.api

import com.suzukiplan.tohovgs.BuildConfig
import com.suzukiplan.tohovgs.MainActivity
import com.suzukiplan.tohovgs.model.Song
import com.suzukiplan.tohovgs.model.SongList
import okhttp3.*
import java.io.IOException

class WebAPI(private val mainActivity: MainActivity) {
    private val client = OkHttpClient.Builder().build()
    val lastStatusCode: Int get() = internalLastStatusCode
    private var internalLastStatusCode = 0

    fun check(version: String, done: (updatable: Boolean?) -> Unit) = getAsync("/songlist.ver") {
        val server = it?.substring(0, 10)
        val result = if (null != server) version < server else null
        Logger.d("songlist.json: client=$version, server=$server, updatable=$result")
        done(result)
    }

    fun downloadSongList(done: (songList: SongList?) -> Unit) = getAsync("/songlist.json") {
        if (null != it) {
            done(mainActivity.gson.fromJson(it, SongList::class.java))
        } else done(null)
    }

    fun downloadMML(song: Song): String? = getSync("/${song.mml}.mml")

    private fun makeRequest(path: String) =
        Request.Builder().url("${BuildConfig.API_SERVER_BASE_URI}$path").build()

    private fun getAsync(path: String, done: (body: String?) -> Unit) {
        Logger.d("GET $path <async>")
        mainActivity.executeAsync {
            client.newCall(makeRequest(path)).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Logger.e("Client error: $e")
                    internalLastStatusCode = -1
                    done(null)
                }

                override fun onResponse(call: Call, response: Response) {
                    internalLastStatusCode = response.code
                    if (response.code != 200) {
                        Logger.e("Server error: ${response.code}")
                        done(null)
                    } else {
                        val body = response.body?.string()
                        Logger.e("GET $path succeed (${body?.length} bytes)")
                        done(body)
                    }
                }
            })
        }
    }

    private fun getSync(path: String): String? {
        Logger.d("GET $path")
        val response = client.newCall(makeRequest(path)).execute()
        val body = response.body?.string()
        internalLastStatusCode = response.code
        return if (response.code != 200 || null == body) {
            Logger.e("GET $path failed (${response.code})")
            null
        } else {
            Logger.e("GET $path succeed (${body.length} bytes)")
            body
        }
    }
}