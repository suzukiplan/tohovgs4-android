/**
 * ©2022, SUZUKI PLAN
 * License: https://github.com/suzukiplan/tohovgs4-android/blob/master/LICENSE.txt
 */
package com.suzukiplan.tohovgs.api

import android.content.Context
import android.content.SharedPreferences
import com.suzukiplan.tohovgs.model.Album
import com.suzukiplan.tohovgs.model.Song

class Settings(context: Context?) {
    private val preferences = context?.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var lastSelectedAlbumId: String?
        get() = preferences?.getString("last_selected_album_id", "th06")
        set(value) = save { editor -> editor.putString("last_selected_album_id", value) }

    var initialPositionSequential: Int
        get() = preferences?.getInt("initial_position_sequential", 0) ?: 0
        set(value) = save { editor -> editor.putInt("initial_position_sequential", value) }

    private fun save(saveProcedure: (editor: SharedPreferences.Editor) -> Unit) {
        val editor = preferences?.edit()
        if (null != editor) saveProcedure.invoke(editor)
        editor?.apply()
    }

    fun isLocked(song: Song): Boolean {
        val defaultLocked = song.parentAlbum?.defaultLocked ?: true
        return preferences?.getBoolean("locked_${song.mml}", defaultLocked) ?: defaultLocked
    }

    fun unlock(album: Album) {
        album.songs.forEach {
            if (isLocked(it)) {
                unlock(it)
            }
        }
    }

    fun lock(song: Song) {
        if (!isLocked(song)) {
            save { editor -> editor.putBoolean("locked_${song.mml}", true) }
            song.needReload = true
        }
    }

    private fun unlock(song: Song) {
        save { editor ->
            editor.putBoolean("locked_${song.mml}", false)
            song.needReload = true
        }
    }
}