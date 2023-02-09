package com.example.googlesttdemo.spectoimage

import android.content.res.AssetManager
import android.util.Log
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.speech.v1.RecognitionAudio
import com.google.cloud.speech.v1.RecognitionConfig
import com.google.cloud.speech.v1.SpeechClient
import com.google.cloud.speech.v1.SpeechSettings
import com.google.cloud.texttospeech.v1.*
import com.google.cloud.translate.Translate
import com.google.cloud.translate.TranslateOptions
import com.google.cloud.translate.Translation
import com.google.protobuf.ByteString
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.*


class GoogleServices(private val assetManager: AssetManager ) {



    private fun readFromInputStream(inputStream: InputStream): String {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val stringBuilder = StringBuilder()

        var line: String? = reader.readLine()
        while (line != null) {
            stringBuilder.append(line)
            line = reader.readLine()
        }

        reader.close()
        return stringBuilder.toString()
    }


    private var responseText = ""
    private var responseGPT3 = ""
    private val oauthKeyName = "credentials/caremeta-daea7ac2a8c7.json"
    private var inputStream = assetManager.open(oauthKeyName)
    private val oauthKey = readFromInputStream(inputStream)
//    val credentials = GoogleCredentials.fromJson(oauthKey)


    fun getSTTText(audioURI: String): String {

        val audioInputStream = FileInputStream(audioURI)
        val audioBytes = audioInputStream.readBytes()



        val speechClient = SpeechClient.create(
            SpeechSettings.newBuilder()
                .setCredentialsProvider { GoogleCredentials.fromStream(ByteArrayInputStream(oauthKey.toByteArray())) }
                .build())
        val config =
            RecognitionConfig.newBuilder()
                .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                .setSampleRateHertz(16000)
                .setLanguageCode("ko-KR")
                .build()
        val audio = RecognitionAudio.newBuilder()
            .setContent(ByteString.copyFrom(audioBytes))
            .build()

        val response = speechClient.recognize(config, audio)
        val results = response.resultsList
        var transcription = ""
        for (result in results) {
            // Appends the transcription of the audio file
            for (alternative in result.alternativesList) {
                transcription += "${alternative.transcript} "
            }
        }
        Log.d("Google STT result: ", transcription)
        speechClient.close()
        return transcription
    }

    fun googletts(pathToAudio: String, inputText:String){

        val speechClient = TextToSpeechClient.create(
            TextToSpeechSettings.newBuilder().setCredentialsProvider {  GoogleCredentials.fromStream(ByteArrayInputStream(oauthKey.toByteArray()))  }.build()
        )
        val input = SynthesisInput.newBuilder().setText(inputText).build()
        val voice = VoiceSelectionParams.newBuilder()
            .setLanguageCode("ko-KR")
            .setSsmlGender(SsmlVoiceGender.NEUTRAL)
            .build()

        val audioConfig = AudioConfig.newBuilder()
            .setAudioEncoding(AudioEncoding.MP3).build()

        val response = speechClient.synthesizeSpeech(input, voice, audioConfig)

        val audioContents = response.audioContent
        FileOutputStream(pathToAudio).use { out ->
            out.write(audioContents.toByteArray())
            out.close()

        }
        speechClient.close()
    }

    fun googleTranslatorKoreanToEnglish(inputText: String): String{

        inputStream = assetManager.open(oauthKeyName)
        val myCredentials = GoogleCredentials.fromStream(inputStream)

        val translateOptions = TranslateOptions.newBuilder().setCredentials(myCredentials).build()

        val translate = translateOptions.service
        val translation: Translation = translate.translate(
            inputText,
            Translate.TranslateOption.sourceLanguage("ko"),
            Translate.TranslateOption.targetLanguage("en")
        )
        val resultText =  translation.translatedText


        return resultText
    }

    fun googleTranslatorEnglishToKorean(inputText: String): String{

        inputStream = assetManager.open(oauthKeyName)
        val myCredentials = GoogleCredentials.fromStream(inputStream)

        val translateOptions = TranslateOptions.newBuilder().setCredentials(myCredentials).build()

        val translate = translateOptions.service
        val translation: Translation = translate.translate(
            inputText,
            Translate.TranslateOption.sourceLanguage("en"),
            Translate.TranslateOption.targetLanguage("ko")
        )
        val resultText =  translation.translatedText
        return resultText
    }

    fun getResponseGPT3(inputText: String, callback: (String) -> Unit){
        val API_KEY = "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"
        val host = "api.openai.com"
//    val prompt = "Hello, I'd like to have a conversation with you."
        val prompt = """
        {
          "model": "text-davinci-003",
          "prompt": "\n\nHuman:$inputText\nAI:",
          "max_tokens": 1500,
          "temperature": 0,
          "top_p": 1,
          "frequency_penalty":0,
          "presence_penalty":0.6,
          "stop":  ["\nHuman:", "\nAI:"]
        }
    """
        Log.d("ddd gpt3 prompt", prompt)
        val url = "https://$host/v1/completions"
        val client = OkHttpClient()
        val requestBody = RequestBody.create("application/json".toMediaTypeOrNull(), prompt)

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $API_KEY")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                println("Request failed: $e")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseCode = response.code
                val responseMessage = response.message
                val responseBody = response.body?.source()
                if (responseCode != 200 || responseBody == null) {
                    Log.e("GPT3 Code Err: ", "$responseCode $responseMessage: $responseBody")

                }
                try {
                    val json = JSONObject(responseBody!!.readUtf8())
                    val result = json.get("choices")
                    Log.d("GPT3 result", result.toString())
                    val resultJson = JSONArray(result.toString())
                    val text = JSONObject(resultJson.get(0).toString())
                    val ressText = text["text"]
                    responseGPT3 = ressText.toString().trim()
                    Log.d("GPT3", ressText.toString())
                } catch (e: Exception) {
                    Log.d("GPT3 Error ",e.stackTraceToString() )
                }
                callback(responseGPT3)

            }
        })

    }




    fun getResponseClovaStudio(inputText: String, callback: (String) -> Unit){
        val host = "clovastudio.apigw.ntruss.com"
        val apiKey = "NTA0MjU2MWZlZTcxNDJiY+iKD5C//ZinLvJkGG2+pGY3yebzfgfa8eYfiCSbuAk88oUuMiZewKsNwbO3LUvjR8m9OL4gnXtKoYhXPqo/NQ+IR2yQDmv/hmjtk5i9l5muyJgBLrigC5GTQtuVspjdKv1FiXYQA8rI4oVPyqrx3OltxeW9kIeGRb54uAeKsHSk2GHT/mIEHmV3VPFegPT+jA=="
        val apiKeyPrimaryVal = "G1OJGTVOHaC2N36FfkKjBSFsztvqmLMNxznm0VcO"
        val requestId = "1d30c0a7a3fb4951b75c14ab55090de6"
        val requestedText = "###A:$inputText###B:"



        val completionRequest = """
            {
            "text": "$requestedText",
            "maxTokens": 300,
            "temperature": 0.85,
            "topK": 4,
            "topP": 0.8,
            "repeatPenalty": 5.0,
            "start": "",
            "restart": "",
            "stopBefore": ["<|endoftext|>"],
            "includeTokens": false,
            "includeAiFilters": true,
            "includeProbs": false
                }
        """
        Log.d("completionRequest", completionRequest)

        val url = "https://$host/testapp/v1/tasks/n2av10my/completions/LK-B"

        val client = OkHttpClient()

        val requestBody = RequestBody.create("application/json".toMediaTypeOrNull(), completionRequest)

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("X-NCP-CLOVASTUDIO-API-KEY", apiKey)
            .addHeader("X-NCP-APIGW-API-KEY", apiKeyPrimaryVal)
            .addHeader("X-NCP-CLOVASTUDIO-REQUEST-ID", requestId)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                println("Request failed: $e")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseCode = response.code
                val responseMessage = response.message
                val responseBody = response.body?.source()
                if (responseCode != 200 || responseBody == null) {
                    Log.e("Error: ", "$responseCode $responseMessage: $responseBody")

                }
                try {
                    val json = JSONObject(responseBody!!.readUtf8())
                    val result = json.get("result")
                    val resultJson = JSONObject(result.toString())
                    val text = resultJson.get("text")
                    responseText = text.toString()
                } catch (e: JSONException) {
                    Log.e("Json Error: ", e.toString())
                }
                callback(responseText)

            }
        })

    }
}