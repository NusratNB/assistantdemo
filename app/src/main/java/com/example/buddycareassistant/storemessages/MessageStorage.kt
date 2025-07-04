package com.example.buddycareassistant.storemessages

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.buddycareassistant.utils.LogUtil
import org.json.JSONObject
import java.io.*
import java.nio.charset.Charset

class MessageStorage(private val context: Context) {

    private val fileName = "GPTMessages.txt"
    private val gptPromptFileName = "GPTPrompt.txt"
    private lateinit var pathToSavingMessages: File
    private val TAG ="BuddyCareAssistant: " + this::class.java.simpleName
    private val logger = LogUtil

    fun storeMessages(messages: List<Pair<String, String>>) {
        pathToSavingMessages = File(context.externalCacheDir?.absolutePath, "Messages")
        if (!pathToSavingMessages.exists()){
            pathToSavingMessages.mkdir()
        }
        val file = "$pathToSavingMessages/$fileName"
        val existingMessages = readTextFile()
        val stringBuilder = StringBuilder()

        if (existingMessages.isNotEmpty()){
            logger.d(context,TAG, "length: ${existingMessages.size}")
            for (currentMessage in  existingMessages){
//                val currentMessage = existingMessages[i]
                stringBuilder.append(
                    "$currentMessage\n"
                )
                logger.d(context,TAG, "currentMessage: $currentMessage")
            }
        }
        messages.forEach { (user, message) ->
            stringBuilder.append("\n$message\n$user\n")
        }

        try {
            FileOutputStream(file).use { outputStream ->
                outputStream.write(stringBuilder.toString().toByteArray(Charset.defaultCharset()))
            }

        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun retrieveUserAssistantMessages(): List<Pair<Boolean, String>> {
        pathToSavingMessages = File(context.externalCacheDir?.absolutePath, "Messages")
        if (!pathToSavingMessages.exists()){
            pathToSavingMessages.mkdir()
        }
        val result = mutableListOf<Pair<Boolean, String>>()
        val data = readTextFile()
        var message = ""
        var lastOneUser: Boolean? = null
        data.forEach {
            if (it.startsWith("User:")) {
                lastOneUser = true
                if (message != "")
                    result.add(Pair(true, message))
                message = ""
                message += it.substring(startIndex = 5)
            } else if (it.startsWith("Assistant:")) {
                lastOneUser = false
                if (message != "")
                    result.add(Pair(false, message))
                message = ""
                message += it.substring(startIndex = 10)
            } else {
                message += it + if(message != "") "\n" else ""
            }
        }
        if (message != "") {
            if (lastOneUser == true) {
                result.add(Pair(false, message))
            } else if (lastOneUser == false) {
                result.add(Pair(true, message))
            }
        }

        return result
    }

    fun clearMessages() {
        pathToSavingMessages = File(context.externalCacheDir?.absolutePath, "Messages")
        if (pathToSavingMessages.exists()){
            pathToSavingMessages.deleteRecursively()
        }
    }

    private fun readTextFile(): List<String> {
        val file = File(pathToSavingMessages, fileName)
        if (file.exists()){

            val bufferedReader = BufferedReader(file.reader())
            val dataList = mutableListOf<String>()
            bufferedReader.useLines { lines ->
                lines.forEach { line ->
                    dataList.add(line)
                    Log.d("txtFile", "line: $line")
                }
            }
            return dataList
        }
       return emptyList()
    }

    fun saveGptPrompt(prompt:String){
        pathToSavingMessages = File(context.externalCacheDir?.absolutePath, "Messages")
        if (!pathToSavingMessages.exists()){
            pathToSavingMessages.mkdir()
        }
        val file = "$pathToSavingMessages/$gptPromptFileName"
        try {
            val fileWriter = FileWriter(file)
            fileWriter.write(prompt)
            fileWriter.close()
            logger.i(context, TAG, "Text saved successfully to $file.")
        } catch (e: IOException) {
            logger.i(context,TAG, "Error occurred while saving the text: ${e.message}")
        }
    }

    fun readGptPrompt(): JSONObject {
        pathToSavingMessages = File(context.externalCacheDir?.absolutePath, "Messages")
        if (!pathToSavingMessages.exists()){
            pathToSavingMessages.mkdir()
        }
        val tempText = """
            {
              "model": "gpt-3.5-turbo",
              "messages": [
                        {"role": "system", "content": "You are a helpful friend."}
                    ]
                    ,
               "max_tokens": 1000,
               "temperature": 1.0,
               "top_p": 1.0,
               "n": 1,
               "stream": false,
               "frequency_penalty":0.0,
               "presence_penalty":0.6
            }
        """
        val file = File(pathToSavingMessages, gptPromptFileName)
        if (!file.exists()){
            saveGptPrompt(tempText)
        }

        var jsonObject = JSONObject()
        try {
            val bufferedReader = BufferedReader(FileReader(file))
            val content = bufferedReader.readText()

            // Parse the text content into a JSON object using a JSONObject
            jsonObject = JSONObject(content)

        }catch (e: Exception) {
            println("Error occurred while converting the text file to JSON: ${e.message}")
        }
        return jsonObject

    }

//    fun retrieveMessages(): List<Pair<String, String>> {
//        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
//
//        if (!file.exists()) return emptyList()
//
//        return try {
//            FileInputStream(file).use { inputStream ->
//                val messageList = mutableListOf<Pair<String, String>>()
//                inputStream.bufferedReader().useLines { lines ->
//                    lines.forEach { line ->
//                        val separatorIndex = line.indexOf(":")
//                        if (separatorIndex > 0) {
//                            val user = line.substring(0, separatorIndex).trim()
//                            val message = line.substring(separatorIndex + 1).trim()
//                            messageList.add(Pair(user, message))
//                        }
//                    }
//                }
//                messageList
//            }
//        } catch (e: IOException) {
//            e.printStackTrace()
//            emptyList()
//        }
//    }
}
