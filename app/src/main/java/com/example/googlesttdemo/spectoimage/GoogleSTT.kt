package com.example.googlesttdemo.spectoimage

import android.content.res.AssetManager
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.cloud.speech.v1.RecognitionAudio
import com.google.cloud.speech.v1.RecognitionConfig
import com.google.cloud.speech.v1.SpeechClient

class GoogleSTT {
    val SCOPE = listOf("https://www.googleapis.com/auth/cloud-platform")
    //
//    @Throws(Exception::class)
//    @JvmStatic
    fun getSTTText(assetManager: AssetManager, audioURI: String): String {
        lateinit var alternative: String
        val oauthKeyName = "client_secret_267918257418-i97jb55jmj9gktrg8qrbs7inpq3irb0s.apps.googleusercontent.com.json"
        val inputStream = assetManager.open(oauthKeyName)
        // Instantiates a client

        System.setProperty("GOOGLE_APPLICATION_CREDENTIALS", oauthKeyName)
//        val credential = GoogleCredential.fromStream(inputStream)


        val speechClient = SpeechClient.create()
//        val gcsUri = "gs://cloud-samples-data/speech/brooklyn_bridge.raw"
        val config =
            RecognitionConfig.newBuilder()
                .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                .setSampleRateHertz(16000)
                .setLanguageCode("ko-KO")
                .build()
        val audio =
            RecognitionAudio.newBuilder().setUri(audioURI).build()

        val response = speechClient.recognize(config, audio)
        val results = response.resultsList
        for (result in results) {
            // There can be several alternative transcripts for a given chunk of speech. Just use the
            // first (most likely) one here.
            alternative =
                result.alternativesList[0].toString()

//            System.out.printf("Transcription: %s%n", alternative.transcript)        }
        }
        return alternative
    }
}