package com.example.buddycareassistant.bluetoothcontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.AUDIO_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import com.example.buddycareassistant.recordaudio.AudioRecorder

class VoiceRecordSCO (private val ctx: Context){
    private var audioManager: AudioManager? = null
    private val audioRecorder = AudioRecorder(ctx)
    private var isRecording = false



    fun enableVoiceRecord() {
        val intentFilter = IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        ctx.registerReceiver( mBluetoothScoReceiver, intentFilter)
        audioManager = ctx.applicationContext.getSystemService(AUDIO_SERVICE) as AudioManager?
        // Start Bluetooth SCO.
        // Start Bluetooth SCO.
        audioManager!!.mode = AudioManager.MODE_NORMAL
        audioManager!!.isBluetoothScoOn = true
        audioManager!!.startBluetoothSco()
        // Stop Speaker.
        // Stop Speaker.
        audioManager!!.isSpeakerphoneOn = false

    }

    fun disableVoiceRecord() {
        try {
            ctx.unregisterReceiver(mBluetoothScoReceiver)
        } catch (e: Exception) {
        }
        // Stop Bluetooth SCO.
        audioManager!!.stopBluetoothSco()
        audioManager!!.mode = AudioManager.MODE_NORMAL
        audioManager!!.isBluetoothScoOn = false
        // Start Speaker.
        audioManager!!.isSpeakerphoneOn = true
    }

    private val mBluetoothScoReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
            println("ANDROID Audio SCO state: $state")
            if (AudioManager.SCO_AUDIO_STATE_CONNECTED == state) {
                isRecording = true
//                speechRecognizer.startListening(speechRecognizerIntent)
            } else if (AudioManager.SCO_AUDIO_STATE_DISCONNECTED == state) {
                if (isRecording) {
                    disableVoiceRecord()
                    isRecording = false
                }
            }

        }
    }


}