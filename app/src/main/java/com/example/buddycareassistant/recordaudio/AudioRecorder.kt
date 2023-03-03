package com.example.buddycareassistant.recordaudio

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import java.io.File
import java.io.FileOutputStream

class AudioRecorder(private val ctx: Context) {

    private val audioSource = MediaRecorder.AudioSource.VOICE_CALL
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private var audioRecord: AudioRecord? = null
    private val recorder = MediaRecorder()
    private var isRecording = false

    @RequiresApi(Build.VERSION_CODES.O)
    fun startRecording(outputFile: File) {

        recorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
        recorder.setOutputFile(outputFile)
        recorder.prepare()
        recorder.start()
    }
    fun stopRecording() {
        recorder.stop()
        recorder.release()
    }

    fun start(outputFile: File) {
        ActivityCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
        audioRecord = AudioRecord(audioSource, sampleRate, channelConfig, audioFormat, bufferSize())
        audioRecord?.startRecording()
        Log.d("scoTest ", "AudioRecorder file path $outputFile")

        isRecording = true
        Thread(Runnable {
            writeAudioDataToFile(outputFile)
        }).start()
    }

    fun stop() {
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        isRecording = false
    }

    private fun writeAudioDataToFile(outputFile: File) {
        val data = ByteArray(bufferSize())
        val outputStream = FileOutputStream(outputFile)
        while (isRecording) {
            val read = audioRecord?.read(data, 0, bufferSize()) ?: 0
            if (read > 0) {
                outputStream.write(data, 0, read)
            }
        }
        outputStream.close()
    }

    private fun bufferSize(): Int {
//        Log.d("scoTest", "bufferSize " + (AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2).toString())
        return AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2
    }
}