/**
 * ©2022, SUZUKI PLAN
 * License: https://github.com/suzukiplan/tohovgs4-android/blob/master/LICENSE.txt
 */
package com.suzukiplan.tohovgs

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.suzukiplan.TOHOVGS.R

class AskDialog : DialogFragment() {
    companion object {
        fun start(from: MainActivity, message: String, listener: Listener) {
            from.executeWhileResume {
                val dialog = AskDialog()
                dialog.arguments = Bundle()
                dialog.arguments?.putString("message", message)
                dialog.arguments?.putString("yes", from.getString(R.string.ok))
                dialog.arguments?.putString("no", from.getString(R.string.cancel))
                dialog.listener = listener
                dialog.show(from.supportFragmentManager, null)
            }
        }

        fun start(
            from: MainActivity,
            message: String,
            yes: String,
            no: String,
            listener: Listener
        ) {
            from.executeWhileResume {
                val dialog = AskDialog()
                dialog.arguments = Bundle()
                dialog.arguments?.putString("message", message)
                dialog.arguments?.putString("yes", yes)
                dialog.arguments?.putString("no", no)
                dialog.listener = listener
                dialog.show(from.supportFragmentManager, null)
            }
        }
    }

    interface Listener {
        fun onClick(isYes: Boolean)
    }

    private var listener: Listener? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            // Use the Builder class for convenient dialog construction
            val message = requireArguments().getString("message")
            val yes = requireArguments().getString("yes")
            val no = requireArguments().getString("no")
            val builder = AlertDialog.Builder(it)
            builder.setMessage(message)
                .setPositiveButton(yes) { _, _ -> listener?.onClick(true) }
                .setNegativeButton(no) { _, _ -> listener?.onClick(false) }
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}