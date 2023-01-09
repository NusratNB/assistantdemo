package com.example.googlesttdemo.spectoimage


import com.google.cloud.texttospeech.v1.*
import java.io.FileOutputStream


class GoogleTextToSpeech{

    fun googletts(pathToAudio:String){
        // Instantiates a client
        val speechClient = TextToSpeechClient.create()
        val input = SynthesisInput.newBuilder().setText("What time is it now?").build()
        val voice = VoiceSelectionParams.newBuilder()
            .setLanguageCode("en-US")
            .setSsmlGender(SsmlVoiceGender.NEUTRAL)
            .build()
        val audioConfig = AudioConfig.newBuilder()
            .setAudioEncoding(AudioEncoding.MP3).build()

        val response = speechClient.synthesizeSpeech(input, voice, audioConfig)

        val audioContents = response.audioContent
        FileOutputStream("output.mp3").use { out ->
            out.write(audioContents.toByteArray())
            println("Audio content written to file \"output2.mp3\"")
        }
    }
}