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

    var compatCurrentTitleId: Int
        get() = preferences?.getInt("compat_current_title_id", 0x0010) ?: 0x0010
        set(value) = save { editor -> editor.putInt("compat_current_title_id", value) }

    var compatLoop: Int
        get() = preferences?.getInt("compat_loop", 1) ?: 1
        set(value) = save { editor -> editor.putInt("compat_loop", value) }

    var compatBase: Int
        get() = preferences?.getInt("compat_base", 0) ?: 0
        set(value) = save { editor -> editor.putInt("compat_base", value) }

    var compatInfinity: Int
        get() = preferences?.getInt("compat_infinity", 0) ?: 0
        set(value) = save { editor -> editor.putInt("compat_infinity", value) }

    var compatKobushi: Int
        get() = preferences?.getInt("compat_kobushi", 0) ?: 0
        set(value) = save { editor -> editor.putInt("compat_kobushi", value) }

    var compatLocaleId: Int
        get() = preferences?.getInt("compat_locale_id", 0) ?: 0
        set(value) = save { editor -> editor.putInt("compat_locale_id", value) }
}