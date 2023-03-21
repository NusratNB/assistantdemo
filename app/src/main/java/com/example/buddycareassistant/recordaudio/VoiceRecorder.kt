package com.example.buddycareassistant.recordaudio

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.util.Log
import androidx.core.app.ActivityCompat
import com.konovalov.vad.Vad
import com.konovalov.vad.VadConfig
import com.konovalov.vad.VadListener
import java.io.File
import java.io.FileOutputStream

/**
 * Created by George Konovalov on 11/16/2019.
 */
class VoiceRecorder(private val ctx: Context, config: VadConfig? ) {
    private val vad: Vad?
    private var audioRecord: AudioRecord? = null
    private var thread: Thread? = null
    private var isListening = false
    private var bufferSize = 0

    init {
        vad = Vad(config)

    }

    fun updateConfig(config: VadConfig?) {
        vad!!.config = config
    }

    fun start(outputFile: File) {
        stop()

        audioRecord = createAudioRecord()
        if (audioRecord != null) {
            isListening = true
            audioRecord!!.startRecording()
            thread = Thread(ProcessVoice())
            thread!!.start()
            vad!!.start()
            Thread {
                writeAudioDataToFile(outputFile)
            }.start()
        } else {
            Log.w(TAG, "Failed start Voice Recorder!")
        }
    }

    fun stop() {
        isListening = false
        if (thread != null) {
            thread!!.interrupt()
            thread = null
        }
        if (audioRecord != null) {
            try {
                audioRecord!!.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error stop AudioRecord ", e)
            }
            audioRecord = null
        }
        vad?.stop()
    }

    private fun createAudioRecord(): AudioRecord? {
        try {
            val minBufSize = AudioRecord.getMinBufferSize(
                vad!!.config.sampleRate.value,
                PCM_CHANNEL,
                PCM_ENCODING_BIT
            )
            if (minBufSize == AudioRecord.ERROR_BAD_VALUE) {
                return null
            }
            ActivityCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                vad.config.sampleRate.value,
                PCM_CHANNEL,
                PCM_ENCODING_BIT,
                minBufSize
            )
            if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                return audioRecord
            } else {
                audioRecord.release()
            }
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Error can't create AudioRecord ", e)
        }
        return null
    }

    private val numberOfChannels: Int
        private get() {
            when (PCM_CHANNEL) {
                AudioFormat.CHANNEL_IN_MONO -> return 1
                AudioFormat.CHANNEL_IN_STEREO -> return 2
            }
            return 1
        }

    private inner class ProcessVoice : Runnable {
        override fun run() {
//            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            while (!Thread.interrupted() && isListening && audioRecord != null) {
                val buffer = ShortArray(vad!!.config.frameSize.value * numberOfChannels * 2)
                Log.d("vadTest", "numberOfChannels: " + numberOfChannels)
                Log.d("vadTest", "buffer size: " + buffer.size)
                audioRecord!!.read(buffer, 0, buffer.size)
                detectSpeech(buffer)
            }
        }

        private fun detectSpeech(buffer: ShortArray) {
            vad!!.addContinuousSpeechListener(buffer, object : VadListener {
                override fun onSpeechDetected() {
                    Log.d("audioVolumeTest", "Speech detected")
                    Log.d("sampleRate", "sample rate: " + vad.config.sampleRate.value)
                }

                override fun onNoiseDetected() {
                    Log.d("audioVolumeTest", "Noise detected")
                }
            })
        }
    }

    private fun writeAudioDataToFile(outputFile: File) {
        bufferSize = (vad?.config?.frameSize?.value )!! * numberOfChannels * 2
        val data = ByteArray(bufferSize)
        val outputStream = FileOutputStream(outputFile)
        while (isListening) {
            val read = audioRecord?.read(data, 0, bufferSize) ?: 0
            if (read > 0) {
                outputStream.write(data, 0, read)
            }
        }
        outputStream.close()
    }

    companion object {
        private const val PCM_CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val PCM_ENCODING_BIT = AudioFormat.ENCODING_PCM_16BIT
        private val TAG = VoiceRecorder::class.java.simpleName
    }
}