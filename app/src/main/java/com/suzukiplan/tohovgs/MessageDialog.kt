/**
 * ©2022, SUZUKI PLAN
 * License: https://github.com/suzukiplan/tohovgs4-android/blob/master/LICENSE.txt
 */
package com.suzukiplan.tohovgs

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.suzukiplan.tohovgs.api.Logger

class MessageDialog : DialogFragment() {
    companion object {
        fun start(from: MainActivity, message: String) {
            from.executeWhileResume {
                val dialog = MessageDialog()
                dialog.arguments = Bundle()
                dialog.arguments?.putString("message", message)
                dialog.show(from.supportFragmentManager, null)
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = AlertDialog.Builder(it)
            builder.setMessage(requireArguments().getString("message"))
                .setPositiveButton(R.string.ok) { _, _ -> Logger.d("OK clicked") }
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}