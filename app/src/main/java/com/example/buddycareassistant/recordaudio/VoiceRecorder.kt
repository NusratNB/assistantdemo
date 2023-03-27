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
import android.text.format.Time

/**
 * Created by George Konovalov on 11/16/2019.
 */
class VoiceRecorder(private val ctx: Context, config: VadConfig? ) {
    private val vad: Vad?
    private var audioRecord: AudioRecord? = null
    private var audioRecordForSaving: AudioRecord? = null
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private var thread: Thread? = null
    private var isListening = false
    private var bufferSize = 0
    @Volatile
    private var isRecording = false
    private var minBufferSizeForAudioSaving = -2
    private val numberOfChannels = 1
    private var outputFile: File? = null
    val time = Time()
    private var speechTime: Long = 0
    private var noiseTime: Long = 0
    private val  differenceTime= 3000

    init {
        vad = Vad(config)

    }
    fun start(outputFile: File) {
        stop()

        audioRecordForSaving = createAudioRecordForSaving()
        audioRecord = createAudioRecord()
        if (audioRecord != null) {
            isListening = true
            audioRecord!!.startRecording()
            audioRecordForSaving!!.startRecording()
            thread = Thread(ProcessVoice())
            thread!!.start()
            vad!!.start()
            isRecording = true
            Thread{
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
        if (audioRecordForSaving != null){
            try {
                audioRecordForSaving!!.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error stop AudioRecord for saving file ", e)
            }
            audioRecordForSaving = null
        }
        vad?.stop()
        isRecording = false
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

    private fun createAudioRecordForSaving(): AudioRecord?{
        try {
            minBufferSizeForAudioSaving = bufferSizeForAudioSaving()
            if (minBufferSizeForAudioSaving == AudioRecord.ERROR_BAD_VALUE){
                return null
            }
            ActivityCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
            val audioRecordForSaving = AudioRecord(MediaRecorder.AudioSource.MIC,
                vad!!.config.sampleRate.value,
                PCM_CHANNEL,
                PCM_ENCODING_BIT,
                minBufferSizeForAudioSaving)

            if (audioRecordForSaving.state == AudioRecord.STATE_INITIALIZED) {
                return audioRecordForSaving
            } else {
                audioRecordForSaving.release()
            }
        }catch (e: IllegalArgumentException){
            Log.e(TAG, "Error can't create AudioRecord for saving Audio file", e)
        }
        return null
    }


    private inner class ProcessVoice : Runnable {
        override fun run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            while (!Thread.interrupted() && isListening && audioRecord != null) {
                val buffer = ShortArray(vad!!.config.frameSize.value * numberOfChannels * 2)
//                val buffer = ShortArray(bufferSize())
                Log.d("vadTest", "numberOfChannels: $numberOfChannels")
                Log.d("vadTest", "buffer size: " + buffer.size)
                val read = audioRecord!!.read(buffer, 0, buffer.size)
                detectSpeech(buffer)
            }
        }

        private fun detectSpeech(buffer: ShortArray) {
            vad!!.addContinuousSpeechListener(buffer, object : VadListener {
                override fun onSpeechDetected() {
                    time.setToNow()
                    speechTime = System.currentTimeMillis()
                    Log.d("audioVolumeTest", "Speech detected")
                    Log.d("sampleRate", "sample rate: " + vad.config.sampleRate.value)
                }

                override fun onNoiseDetected() {
                    time.setToNow()
                    noiseTime = System.currentTimeMillis()
                    if ((noiseTime - speechTime).toInt() >= differenceTime && (noiseTime - speechTime) != noiseTime){
                        stop()
                        Log.d("audioVolumeTest", "Recording stopped")
                        Log.d("audioVolumeTest", "Difference: " + (noiseTime - speechTime).toString())
                    }
                    Log.d("audioVolumeTest", "Noise detected ")
                }
            })
        }
    }


    private fun writeAudioDataToFile(outputFile: File) {
        val data = ByteArray(minBufferSizeForAudioSaving)
        val outputStream = FileOutputStream(outputFile)
        while (isListening) {
            val read = audioRecordForSaving?.read(data, 0, minBufferSizeForAudioSaving) ?: 0
            if (read > 0) {
                outputStream.write(data, 0, read)
            }
        }
        outputStream.close()
    }

    private fun bufferSizeForAudioSaving(): Int {
//        Log.d("scoTest", "bufferSize " + (AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2).toString())
        return (AudioRecord.getMinBufferSize(vad!!.config.sampleRate.value, PCM_CHANNEL, PCM_ENCODING_BIT) * 2)
    }

    companion object {
        private const val PCM_CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val PCM_ENCODING_BIT = AudioFormat.ENCODING_PCM_16BIT
        private val TAG = VoiceRecorder::class.java.simpleName
    }
}

