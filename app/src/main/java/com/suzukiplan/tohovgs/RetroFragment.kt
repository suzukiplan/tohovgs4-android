package com.suzukiplan.tohovgs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

class RetroFragment : Fragment() {
    companion object {
        fun create(): RetroFragment = RetroFragment()
    }

    private lateinit var surfaceView: View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        val view = inflater.inflate(R.layout.fragment_retro, container, false)
        surfaceView = view.findViewById(R.id.surface_view)
        return view
    }
}