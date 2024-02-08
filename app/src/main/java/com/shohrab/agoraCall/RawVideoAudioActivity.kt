package com.shohrab.agoraCall

import android.view.View
import android.widget.Button

class RawVideoAudioActivity : BasicImplementationActivity() {
    private lateinit var rawVideoAudioManager: RawVideoAudioManager
    private var isZoomed = false

    // Override the UI layout


    override fun initializeAgoraManager() {
        // Instantiate an object of the PlayMediaManager
        rawVideoAudioManager = RawVideoAudioManager(this)
        agoraManager = rawVideoAudioManager

        // Set up a listener for updating the UI
        agoraManager.setListener(agoraManagerListener)
    }

    override fun join() {
        rawVideoAudioManager.joinChannelWithToken()
    }

    fun zoom(view: View){
        isZoomed = !isZoomed
        rawVideoAudioManager.setZoom(isZoomed)

        val button: Button = view as Button
        if (isZoomed) {
            button.text ="Zoom Out"
        } else {
            button.text = "Zoom In"
        }
    }
}