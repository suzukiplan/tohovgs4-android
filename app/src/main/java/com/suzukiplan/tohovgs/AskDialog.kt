/**
 * ©2022, SUZUKI PLAN
 * License: https://github.com/suzukiplan/tohovgs4-android/blob/master/LICENSE.txt
 */
package com.suzukiplan.tohovgs

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

class AskDialog : DialogFragment() {
    companion object {
        fun start(from: MainActivity, message: String, listener: Listener) {
            val dialog = AskDialog()
            dialog.arguments = Bundle()
            dialog.arguments?.putString("message", message)
            dialog.listener = listener
            dialog.show(from.supportFragmentManager, null)
        }
    }

    interface Listener {
        fun onClick(isYes: Boolean)
    }

    private var listener: Listener? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            // Use the Builder class for convenient dialog construction
            val builder = AlertDialog.Builder(it)
            builder.setMessage(requireArguments().getString("message"))
                .setPositiveButton(R.string.ok) { _, _ -> listener?.onClick(true) }
                .setNegativeButton(R.string.cancel) { _, _ -> listener?.onClick(false) }
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}