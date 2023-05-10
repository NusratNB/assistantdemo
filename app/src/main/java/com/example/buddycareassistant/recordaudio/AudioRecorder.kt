package com.example.buddycareassistant.recordaudio

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.app.ActivityCompat
import com.example.buddycareassistant.utils.LogUtil
import java.io.File
import java.io.FileOutputStream


class AudioRecorder(private val ctx: Context) {

    private val TAG ="BuddyCareAssistant: " + this::class.java.simpleName
    private val logger = LogUtil

    private val audioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private var audioRecord: AudioRecord? = null
    private var isRecording = false



    fun start(outputFile: File) {
        ActivityCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
        audioRecord = AudioRecord(audioSource, sampleRate, channelConfig, audioFormat, bufferSize())
//        Log.i(TAG, "Is audioRecord is initialized: ${audioRecord?.state == AudioRecord.STATE_INITIALIZED}")
        logger.i(ctx, TAG, "Is audioRecord is initialized: ${audioRecord?.state == AudioRecord.STATE_INITIALIZED}")

        if (audioRecord?.state == AudioRecord.STATE_INITIALIZED){
//            Log.d(TAG, "Recording is started.")
            logger.d(ctx, TAG, "Recording is started.")
            audioRecord?.startRecording()
        }
        isRecording = true
        Thread {
            writeAudioDataToFile(outputFile)
        }.start()
    }

    fun stop() {
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        isRecording = false
        logger.i(ctx, TAG, "Record has been stopped")
    }

    private fun writeAudioDataToFile(outputFile: File) {
        val data = ByteArray(bufferSize())
        val outputStream = FileOutputStream(outputFile)
        logger.i(ctx, TAG, "Writing audio to the file has been started.")
        while (isRecording) {
            val read = audioRecord?.read(data, 0, bufferSize()) ?: 0
            if (read > 0) {
                outputStream.write(data, 0, read)
            }
        }
        outputStream.close()
        logger.i(ctx, TAG, "Writing audio to the file has been finished")
    }

    private fun bufferSize(): Int {
        return AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2
    }
}