package com.example.buddycareassistant.recordaudio

import ai.picovoice.cobra.Cobra
import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.app.ActivityCompat
import com.example.buddycareassistant.utils.LogUtil
import java.io.File
import java.io.FileOutputStream
import kotlin.math.log


class AudioRecorder(private val ctx: Context,
                    private val cobraVAD: Cobra,
                    private val muteCallback: (probablity: Float) -> Unit) {

    private val TAG ="BuddyCareAssistant: " + this::class.java.simpleName

    private val audioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private var audioRecordVAD: AudioRecord? = null
    private var audioRecordSaving: AudioRecord? = null
    private var logger: LogUtil = LogUtil
    private var isRecording = false
    private val minBufferSize = minBufferSize()
    private val bufferSize = bufferSize(minBufferSize)


    fun start(outputFile: File) {
        ActivityCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
        audioRecordVAD = AudioRecord(audioSource, cobraVAD.sampleRate, channelConfig, audioFormat, bufferSize)
        audioRecordSaving = AudioRecord(audioSource, cobraVAD.sampleRate, channelConfig, audioFormat, bufferSize)
        logger.i(ctx, TAG, "audioRecord for VAD is initialized")
        logger.i(ctx, TAG, "audioRecord for saving audio is initialized")

        if (audioRecordVAD?.state == AudioRecord.STATE_INITIALIZED){
            logger.i(ctx, TAG, "recording for VAD is started")
            audioRecordVAD?.startRecording()
        }
        if (audioRecordSaving?.state == AudioRecord.STATE_INITIALIZED){
            logger.i(ctx, TAG, "recording for saving audio is started")
            audioRecordSaving?.startRecording()
        }
        isRecording = true
        Thread {
            writeAudioDataToFile(outputFile)
        }.start()
    }

    fun stop() {
        audioRecordVAD?.stop()
        audioRecordSaving?.stop()
        audioRecordVAD?.release()
        audioRecordSaving?.release()
        logger.i(ctx, TAG, "audioRecord for VAD is stopped")
        logger.i(ctx, TAG, "audioRecord for saving audio is stopped")
        isRecording = false
        logger.i(ctx, TAG, "isRecording=${this.isRecording}")
    }

    private fun writeAudioDataToFile(outputFile: File) {

        val data = ByteArray(minBufferSize)

        val buffer = ShortArray(cobraVAD.frameLength)
        val outputStream = FileOutputStream(outputFile)
        while (isRecording) {
            if (audioRecordVAD!!.read(buffer, 0, buffer.size) == buffer.size){
                val voiceProbability: Float = cobraVAD.process(buffer)
                muteCallback.invoke(voiceProbability)
            }
            val read = audioRecordSaving?.read(data, 0, data.size) ?: 0
            if (read > 0) {
                outputStream.write(data, 0, read)
            }
        }
        logger.i(ctx, TAG, "FileOutputStream for saving audio is closed")
        outputStream.close()
    }

    private fun minBufferSize(): Int{
        return AudioRecord.getMinBufferSize(cobraVAD.sampleRate, channelConfig, audioFormat)
    }

    private fun bufferSize(minBufferSize: Int): Int {
        return (cobraVAD.sampleRate / 2).coerceAtLeast(minBufferSize)
    }
}