package com.example.buddycareassistant.googlespeechservices

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import android.widget.Toast
import com.example.buddycareassistant.storemessages.MessageStorage
import com.example.buddycareassistant.utils.LogUtil
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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue


class GoogleServices(val context: Context, private val assetManager: AssetManager ) {

    private val TAG ="BuddyCareAssistant: " + this::class.java.simpleName
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
    private val TIMEOUT_IN_SECONDS: Int = 60
    private val messageStorage: MessageStorage = MessageStorage(context)
    private val logger = LogUtil
    private var languageSTT = "ko-KR"
    private var languageTTS = "ko-KR"
    private var increaseVolumeFactor = 1f


    fun getSTTText(audioURI: String, language:String, recordedChangedAudioName:String, pathToRecords:File): String {

        val audioInputStream = FileInputStream(audioURI)
        val audioBytes = audioInputStream.readBytes()
        logger.i(context, TAG, "input audio path: $audioURI")
        val audioFloats = byteToInt(audioBytes)
//        val audioNormalizedFloatArray = increaseAudioVolume(audioFloats)// loudnessNormalizer.audioNormalization(loudnessThreshold, audioFloats)
        val audioNormalizedByteArray = shortArrayToByteArray(audioFloats)

        Thread{
            saveByteArrayAsPcmFile(context, audioNormalizedByteArray, File(pathToRecords, recordedChangedAudioName))
        }.start()

        val speechClient = SpeechClient.create(
            SpeechSettings.newBuilder()
                .setCredentialsProvider { GoogleCredentials.fromStream(ByteArrayInputStream(oauthKey.toByteArray())) }
                .build())
        if (language=="Korean"){
            languageSTT = "ko-KR"
        } else{
            languageSTT = "en-US"
        }

        val config =
            RecognitionConfig.newBuilder()
                .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                .setSampleRateHertz(16000)
                .setLanguageCode(languageSTT)
                .build()
        val audio = RecognitionAudio.newBuilder()
            .setContent(ByteString.copyFrom(audioNormalizedByteArray))
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
//        Log.d(TAG, "Google STT result: $transcription")
        logger.d(context, TAG, "Google STT result: $transcription")

        speechClient.close()
        speechClient.close()
        return transcription
    }

    private fun byteToInt(bytes: ByteArray): IntArray {
        val slicedData = byteArrayToShortArray(bytes) //byteArrayToIntArray(bytes)
//        for (i in bytes.indices){
//            slicedData[i] = bytes[i].toInt()
//            Log.d("shortData", slicedData[i].toString())
//        }
        val maxValueOfAudio = slicedData.map {   it.absoluteValue }.maxOf { it }
        increaseVolumeFactor = (0.7 * (32767/maxValueOfAudio)).toFloat()
        logger.d(context, TAG, "Recorded audio increasing factor: $increaseVolumeFactor")
        val increasedVolume = slicedData.map { (it*increaseVolumeFactor) }
        val finalAudio = IntArray(increasedVolume.size)
        for(i in increasedVolume.indices){
            var currentElement = increasedVolume[i].toInt()
            if (currentElement<-32768){
                currentElement = -32768
            } else if (currentElement>(32767)){
                currentElement = 32767
            }

            finalAudio[i] = currentElement
        }
        return finalAudio
    }

    private fun byteArrayToShortArray(byteArray: ByteArray): IntArray {
        val buffer = ByteBuffer.wrap(byteArray).order(ByteOrder.LITTLE_ENDIAN)
        val shortArray = IntArray(byteArray.size / 2)

        for (i in shortArray.indices) {
            shortArray[i] = buffer.short.toInt()
        }

        return shortArray
    }

    private fun shortArrayToByteArray(intArray: IntArray): ByteArray {
        val buffer = ByteBuffer.allocate(intArray.size * 2).order(ByteOrder.LITTLE_ENDIAN)

        for (i in intArray.indices) {
            buffer.putShort(intArray[i].toShort())
        }

        return buffer.array()
    }

    fun googletts(pathToAudio: String, inputText:String, language: String, gender: String){

        languageTTS = if (language=="Korean"){
            "ko-KR"
        }else{
            "en-US"
        }

        val transportChannelProvider = TextToSpeechSettings.defaultGrpcTransportProviderBuilder()
            .setMaxInboundMessageSize(1024 * 1024 * 100) // Set max message size to 100 MB
            .build()

        val speechClient = TextToSpeechClient.create(
            TextToSpeechSettings.newBuilder().setCredentialsProvider {  GoogleCredentials.fromStream(ByteArrayInputStream(oauthKey.toByteArray()))  }
                .setTransportChannelProvider(transportChannelProvider)
                .build()
        )
        val input = SynthesisInput.newBuilder().setText(inputText).build()

        if (languageTTS == "ko-KR"){
                languageTTS = "ko-KR"

            val voice = VoiceSelectionParams.newBuilder()
                .setLanguageCode(languageTTS)
                .setSsmlGenderValue(SsmlVoiceGender.FEMALE_VALUE)
                .build()
            val audioConfig = AudioConfig.newBuilder()
                .setAudioEncoding(AudioEncoding.MP3).build()

            val response = speechClient.synthesizeSpeech(input, voice, audioConfig)
            val audioContents = response.audioContent
            FileOutputStream(pathToAudio).use { out ->
                out.write(audioContents.toByteArray())
                out.close()
            }
            logger.d(context, TAG, "gender: $gender language: $language ")
        } else if (languageTTS == "en-US"){

            val voice = VoiceSelectionParams.newBuilder()
                .setLanguageCode(languageTTS)
                .setSsmlGenderValue(SsmlVoiceGender.MALE_VALUE)
                .build()

            val audioConfig = AudioConfig.newBuilder()
                .setAudioEncoding(AudioEncoding.MP3).build()

            val response = speechClient.synthesizeSpeech(input, voice, audioConfig)
            val audioContents = response.audioContent
            FileOutputStream(pathToAudio).use { out ->
                out.write(audioContents.toByteArray())
                out.close()
            }
            logger.d(context, TAG, "gender: $gender language: $language ")
        }

        speechClient.close()
    }
    private fun saveByteArrayAsPcmFile(context: Context, byteArray: ByteArray, fileName: File) {
        // Create a File instance with the desired path and file name
        val file = File(fileName.path)

        // Use a FileOutputStream to write the ByteArray to the file
        FileOutputStream(file).use { fos ->
            fos.write(byteArray)
        }
    }


    fun googleTranslatorKoreanToEnglish(inputText: String, language: String): String{

        var  resultText = ""

        if (language=="Korean"){
            inputStream = assetManager.open(oauthKeyName)
            val myCredentials = GoogleCredentials.fromStream(inputStream)

            val translateOptions = TranslateOptions.newBuilder().setCredentials(myCredentials).build()

            val translate = translateOptions.service
            val translation: Translation = translate.translate(
                inputText,
                Translate.TranslateOption.sourceLanguage("ko"),
                Translate.TranslateOption.targetLanguage("en")
            )
            resultText =  translation.translatedText
        } else {
            resultText = inputText
        }

        return resultText
    }

    private fun removeSubstring(tempString: String, translatedText: String): String {
        return if (translatedText.contains(tempString)) {
            translatedText.replace(tempString, "")
        } else {
            translatedText
        }
    }

    fun googleTranslatorEnglishToKorean(inputText: String, language: String): String{

        var resultText = ""

        if (language == "Korean"){
            inputStream = assetManager.open(oauthKeyName)
            val myCredentials = GoogleCredentials.fromStream(inputStream)

            val translateOptions = TranslateOptions.newBuilder().setCredentials(myCredentials).build()

            val translate = translateOptions.service
            val translation: Translation = translate.translate(
                inputText,
                Translate.TranslateOption.sourceLanguage("en"),
                Translate.TranslateOption.targetLanguage("ko")
            )

            val translatedText = translation.translatedText
            val tempString = "&quot;"
            resultText = removeSubstring(tempString, translatedText)
        } else{
            resultText = inputText
        }

        return resultText
    }

    fun getResponseGPT3(gpt3Settings: Map<String, String?>, inputText: String, memoryQuality: String, conversational:String, callback: (String) -> Unit){
        val API_KEY = "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"
        val host = "api.openai.com"

        val model = gpt3Settings["model"].toString()
        val max_tokens = gpt3Settings["max_tokens"]?.toInt()
        var temperature = gpt3Settings["temperature"]?.toFloat()
        var top_p = gpt3Settings["top_p"]?.toFloat()
        val n = gpt3Settings["n"]?.toInt()
        val stream = gpt3Settings["stream"].toBoolean()
        val logprobs = gpt3Settings["logprobs"]
        val frequency_penalty = gpt3Settings["frequency_penalty"]?.toFloat()
        val presence_penalty = gpt3Settings["presence_penalty"]?.toFloat()
        val tokensInfo = gpt3Settings["tokensCheckBox"]?.toBoolean()
        var chatWindowSize = gpt3Settings["chatWindowSize"]?.toInt()
        if (logprobs != "null"){
            logprobs?.toInt()
        }

        if (conversational == "More Creative"){
            temperature = 0.8f
            top_p = 0.9f
        }else if (conversational == "More Balanced"){
            temperature = 0.6f
            top_p = 0.8f
        }else {
            temperature = 0.3f
            top_p = 0.5f
        }
//        Log.d("tokensInfo", tokensInfo.toString())


        val newRequestMessageJson = JSONObject()


        val promptJsonInit = messageStorage.readGptPrompt()
        val previousMessagesArray = promptJsonInit.getJSONArray("messages")
        previousMessagesArray.put(newRequestMessageJson)
        promptJsonInit.put("model", model)
        promptJsonInit.put("max_tokens", max_tokens)
        promptJsonInit.put("temperature", temperature)
        promptJsonInit.put("top_p", top_p)
        promptJsonInit.put("n", n)
        promptJsonInit.put("stream", stream)
        promptJsonInit.put("frequency_penalty", frequency_penalty)
        promptJsonInit.put("presence_penalty", presence_penalty)

        chatWindowSize = when (memoryQuality) {
            "Low" -> {
                5
            }
            "Medium" -> {
                15
            }
            else -> 30
        }

        val newSlicedChatMessages = JSONArray()
        val tempPrevMessages = promptJsonInit.getJSONArray("messages")
//        Log.d("ChatTest", "tempPrevMessages $tempPrevMessages")
        logger.d(context, TAG, "tempPrevMessages $tempPrevMessages")
        logger.d(context, TAG, "tempPrevMessages length: ${tempPrevMessages.length()}; memoryQuality: $memoryQuality   chatWindowSize: $chatWindowSize")
//        Log.d("ChatTest", "tempPrevMessages length: ${tempPrevMessages.length()}; memoryQuality: $memoryQuality   chatWindowSize: $chatWindowSize")
        if ((tempPrevMessages.length() - 1) > (2* chatWindowSize)){
            val startIndex = tempPrevMessages.length() - 1 - (2 * chatWindowSize)
            newSlicedChatMessages.put(tempPrevMessages.get(0))
            for ( i in startIndex until tempPrevMessages.length()){
                newSlicedChatMessages.put(tempPrevMessages.get(i))
            }
            promptJsonInit.put("messages", newSlicedChatMessages)
        }

        if (tokensInfo==true){
            newRequestMessageJson.put("role", "user")
            newRequestMessageJson.put("content", "$inputText. Make your response less than $max_tokens tokens")

        } else{
            newRequestMessageJson.put("role", "user")
            newRequestMessageJson.put("content", inputText)
        }

        val prompt = promptJsonInit.toString()
//            """
//        {
//          "model": "$model",
//          "messages": $promptJsonInit,
//           "max_tokens": $max_tokens,
//           "temperature": $temperature,
//           "top_p": $top_p,
//           "n": $n,
//           "stream": $stream,
//           "frequency_penalty":$frequency_penalty,
//           "presence_penalty":$presence_penalty
//        }
//    """

        val promptJson = JSONObject(prompt)


//        Log.d("ddd gpt3 prompt", prompt)
        logger.d(context, TAG, "GPT Prompt: $prompt")
        val url = "https://$host/v1/chat/completions"
        Log.d("gpt url:", url)
        logger.d(context, TAG, "gpt url: $url")
        val client = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_IN_SECONDS.toLong(), TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_IN_SECONDS.toLong(), TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_IN_SECONDS.toLong(), TimeUnit.SECONDS)
            .build()
        val requestBody = RequestBody.create("application/json".toMediaTypeOrNull(), prompt)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $API_KEY")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
//                println("Request failed: $e")
                logger.e(context, TAG, "Request failed: $e")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseCode = response.code
                val responseMessage = response.message
                val responseBody = response.body?.source()
                if (responseCode != 200 || responseBody == null) {
//                    Log.e("GPT3 Code Err: ", "$responseCode $responseMessage: $responseBody")
                    logger.e(context, TAG, "GPT3 Code Err: $responseCode $responseMessage: $responseBody")

                }
                try {

                    val json = JSONObject(responseBody!!.readUtf8())

                    val result = json.get("choices")
//                    Log.d(TAG, "GPT3 result: $result")
                    logger.d(context, TAG, "GPT3 result: $result")
                    val resultJson = JSONArray(result.toString())
                    val text = JSONObject(resultJson.get(0).toString())
                    val resText = text["message"]
                    val content = JSONObject(resText.toString())
                    responseGPT3 = content["content"].toString().trim()

                    val assistantResponse = JSONObject()
                    assistantResponse.put("role", "assistant")
                    assistantResponse.put("content", responseGPT3)
                    val messagesArray = promptJson.getJSONArray("messages")
                    messagesArray.put(assistantResponse)
                    messageStorage.saveGptPrompt(promptJson.toString())

//                    Log.d(TAG, "GPT3 content: $content")
                    logger.d(context, TAG, "GPT3 content: $content")
                } catch (e: Exception) {
//                    Log.d(TAG, "GPT3 Error: " + e.stackTraceToString() )
                    logger.d(context, TAG, "GPT3 Error: " + e.stackTraceToString() )
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
//        Log.d(TAG, "Naver Clova completionRequest: $completionRequest")
        logger.d(context, TAG, "Naver Clova completionRequest: $completionRequest")

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
//                Log.e(TAG, "Request failed: $e")
                logger.e(context, TAG, "Request failed: $e")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseCode = response.code
                val responseMessage = response.message
                val responseBody = response.body?.source()
                if (responseCode != 200 || responseBody == null) {
//                    Log.e(TAG, "Error: $responseCode $responseMessage: $responseBody")
                    logger.e(context, TAG, "Error: $responseCode $responseMessage: $responseBody")

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