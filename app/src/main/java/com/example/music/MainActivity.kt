package com.example.music

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import java.io.IOException


class MainActivity : Activity() {
    private var streamButton: Button? = null
    private var playButton: ImageButton? = null
    private var isPlaying = false
    private var playTime: TextView? = null
    private var audioStreamer: StreamingMediaPlayer? = null

    fun MediaPlayer() {}

    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        this.setContentView(R.layout.activity_main)
        initControls()
    }

    private fun initControls() {
        playTime = findViewById<View>(R.id.playTime) as TextView
        streamButton = findViewById<View>(R.id.button_stream) as Button
        streamButton!!.setOnClickListener { this.startStreamingAudio() }
        playButton = findViewById<View>(R.id.button_play) as ImageButton
        playButton!!.isEnabled = false
        playButton!!.setOnClickListener {
            if (this.audioStreamer?.getMediaPlayer()?.isPlaying() == true) {
                this.audioStreamer?.getMediaPlayer()?.pause()
                this.playButton!!.setImageResource(R.drawable.button_pause)
            } else {
                this.audioStreamer?.getMediaPlayer()?.start()
                this.audioStreamer?.startPlayProgressUpdater()
                this.playButton!!.setImageResource(R.drawable.button_play)
            }
            this.isPlaying = !this.isPlaying
        }
    }

    private fun startStreamingAudio() {
        try {
            val progressBar = findViewById<View>(R.id.progress_bar) as SeekBar
            if (audioStreamer != null) {
                audioStreamer!!.interrupt()
            }
            audioStreamer =
                StreamingMediaPlayer(this, playButton, streamButton, progressBar, playTime)
            audioStreamer!!.startStreaming("https://play.kekedj.com/2021kekedj/202102/20210228/%E4%B8%A4%E5%8F%AA%E8%80%81%E8%99%8E(DjFR3NZ%20Mix)%E5%84%BF%E6%AD%8C.mp3", 5208L, 216L)//读取音乐的url
            streamButton!!.isEnabled = false
        } catch (var2: IOException) {
            Log.e(this.javaClass.name, "Error starting to stream audio.", var2)
        }
    }
}