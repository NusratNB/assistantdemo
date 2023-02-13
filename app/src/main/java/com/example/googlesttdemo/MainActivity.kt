package com.example.googlesttdemo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.googlesttdemo.gpt3documentation.ParametersInfoActivity
import com.example.googlesttdemo.gpt3settings.GPT3SettingsActivity
import com.example.googlesttdemo.spectoimage.GoogleServices
import com.example.googlesttdemo.spectoimage.RecordWavMaster
import com.example.googlesttdemo.wavreader.FileFormatNotSupportedException
import com.example.googlesttdemo.wavreader.WavFile
import com.example.googlesttdemo.wavreader.WavFileException
import java.io.File
import java.io.IOException


class MainActivity : AppCompatActivity() {
    private lateinit var btnRecord: Button
    private lateinit var btnPlay: Button
    private lateinit var btnInference: Button
    private lateinit var btnInfo: Button
    private lateinit var btnSettings: Button
    private lateinit var fullAudioPath: File
    private lateinit var pathToRecords: File
    private lateinit var pathToSavingAudio: File
    private lateinit var txtSent: TextView
    private lateinit var txtReceived: TextView
    private lateinit var fileName: File
    private var playingAvailable: Boolean = false
    private var prevSentAudio: File? = null
    private var prevRecAudio = ""
    private var audioFilePath = ""
    private var isRecorderInitilized = false
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var radioGroupLM: RadioGroup
    private lateinit var audioRecorder: RecordWavMaster
    private val mPreferences by lazy {
        getSharedPreferences("assistant_demo", MODE_PRIVATE)
    }

    private val requestPermission: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()){
                permissions ->
            permissions.entries.forEach{
                val permissionName = it.key
                val isGranted = it.value
                if (isGranted){
                    Toast.makeText(this@MainActivity, "Permission is granted",
                        Toast.LENGTH_SHORT).show()
                }else{
                    if (permissionName== Manifest.permission.READ_EXTERNAL_STORAGE){
                        Toast.makeText(this@MainActivity, "Storage reading denied",
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val googlestt = GoogleServices(assets)
        radioGroupLM = findViewById(R.id.radGroupLMType)


        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { // get permission
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE),200);
        }
        pathToRecords = File(externalCacheDir?.absoluteFile, "AudioRecord" )
        if (!pathToRecords.exists()){
            pathToRecords.mkdir()
        }

        pathToSavingAudio = File(externalCacheDir?.absoluteFile, "SavedRecordings" )
        if (!pathToSavingAudio.exists()){
            pathToSavingAudio.mkdir()
            initGPT3Settings()
        }


        var recording = true
        btnRecord = findViewById(R.id.btnRecord)
        btnRecord.text = "Start"
        audioRecorder = RecordWavMaster()

        btnRecord.setOnClickListener{

            if (!isRecorderInitilized){
                audioRecorder.initRecorder(this, pathToRecords.toString())
                isRecorderInitilized = true
            }

            if (recording){
                audioRecorder.recordWavStart()
                btnRecord.text = "Recording"
                recording = false

            }else{
                audioRecorder.recordWavStop()
                btnRecord.text = "Start"
                recording = true
                fileName = audioRecorder.audioName
                playingAvailable = true
            }


        }
        btnPlay = findViewById(R.id.btnPlay)
        btnPlay.text = "Play"
        btnPlay.setOnClickListener{
            if (playingAvailable){
                if (audioFilePath.isNotEmpty()){
                    val assetManager = this.assets
                    val firstFileDescriptor = assetManager.openFd("silence.mp3")
                    mediaPlayer = MediaPlayer()
                    mediaPlayer!!.setDataSource(firstFileDescriptor.fileDescriptor, firstFileDescriptor.startOffset, firstFileDescriptor.length)
                    mediaPlayer!!.prepare()
                    mediaPlayer!!.setOnCompletionListener {
                        val mediaPlayerSilence = MediaPlayer()
                        mediaPlayerSilence.setDataSource(audioFilePath)
                        mediaPlayerSilence.prepare()
                        mediaPlayerSilence.start()
                    }
                    mediaPlayer!!.start()

                }
                if (prevSentAudio?.path?.isNotEmpty() == true){
                    prevSentAudio?.delete()
                }

            } else{
                Toast.makeText(this, "No record found", Toast.LENGTH_SHORT).show()
            }
            btnPlay.text = "Play"

        }
        txtReceived = findViewById(R.id.txtReceived)
        txtSent = findViewById(R.id.txtSent)
        btnInference = findViewById(R.id.btnPredict)
        btnInference.setOnClickListener {

            if (fileName.path.isNotEmpty()){
                Thread {
                    val ttt = googlestt.getSTTText(fileName.path)
                    Log.d("ddd googlestt", ttt)
                    runOnUiThread {
                        txtSent.text ="Sent: " + ttt
                        txtReceived.text = "Received: Not received yet..."
                    }
                    Thread {
                        if(radioGroupLM.checkedRadioButtonId==R.id.radBtnNaverClova){

                            googlestt.getResponseClovaStudio(ttt){ responseFromNaverClova->
                                Thread{
                                    val time = android.text.format.Time()
                                    time.setToNow()
                                    audioFilePath = pathToSavingAudio.toString() + "/" + time.format("%Y%m%d%H%M%S").toString()+".mp3"
                                    prevSentAudio = fileName
                                    Log.d("pathToSavingAudio", pathToSavingAudio.toString())
                                    Log.d("audioFilePath", audioFilePath)
                                    googlestt.googletts(audioFilePath, responseFromNaverClova)
                                    if (prevRecAudio.isNotEmpty()){
                                        val ff = File(prevRecAudio)
                                        ff.delete()
                                    }


                                }.start()
                                runOnUiThread {
                                    txtSent.text ="Sent: " + ttt
                                    txtReceived.text = "Received: " + responseFromNaverClova
                                }

                            }
                        }else{
                            val korToEng = googlestt.googleTranslatorKoreanToEnglish(ttt)
                            Log.d("ddd korToEng", korToEng)
                            val gpt3Settings = getGPT3Settings()
                            googlestt.getResponseGPT3(gpt3Settings, korToEng){ responseFromGPT3->
                                val engToKor = googlestt.googleTranslatorEnglishToKorean(responseFromGPT3)
                                Log.d("ddd gpt3", responseFromGPT3)
                                Log.d("ddd engToKor", engToKor)

                                val time = android.text.format.Time()
                                time.setToNow()
                                audioFilePath = pathToSavingAudio.toString() + "/" + time.format("%Y%m%d%H%M%S").toString()+".mp3"
                                prevSentAudio = fileName
                                Log.d("pathToSavingAudio", pathToSavingAudio.toString())
                                Log.d("audioFilePath", audioFilePath)

                                googlestt.googletts(audioFilePath, engToKor)
                                Log.d("ddd googletts", audioFilePath)

                                if (prevRecAudio.isNotEmpty()){
                                    val ff = File(prevRecAudio)
                                    ff.delete()
                                }
                                runOnUiThread {
                                    txtReceived.text = "Received: " + engToKor
                                }
                            }
                        }
                    }.start()
                }.start()
            }else{
                Toast.makeText(this, "Record audio", Toast.LENGTH_SHORT).show()
            }

        }
        btnInfo = findViewById(R.id.btnInfo)
        btnInfo.setOnClickListener {
            startActivity(Intent(this@MainActivity, ParametersInfoActivity::class.java))
        }
        btnSettings = findViewById(R.id.btnSettings)
        btnSettings.setOnClickListener {
//            Toast.makeText(this, "Not Implemented yet", Toast.LENGTH_LONG).show()
            startActivity(Intent(this@MainActivity, GPT3SettingsActivity::class.java))
        }

    }

    private fun initGPT3Settings(){
        val gpt3SettingsPreferences = mPreferences.edit()
        gpt3SettingsPreferences.putString("model", "text-curie-001")
        gpt3SettingsPreferences.putString("max_tokens", "1000")
        gpt3SettingsPreferences.putString("temperature", "0")
        gpt3SettingsPreferences.putString("top_p", "1")
        gpt3SettingsPreferences.putString("n", "1")
        gpt3SettingsPreferences.putString("stream", "false")
        gpt3SettingsPreferences.putString("logprobs", "null")
        gpt3SettingsPreferences.putString("frequency_penalty", "0")
        gpt3SettingsPreferences.putString("presence_penalty", "0.6")
        gpt3SettingsPreferences.apply()


    }

    private fun getGPT3Settings(): Map<String, String?> {
        val model = mPreferences.getString("model", "text-curie-001")
        val max_tokens = mPreferences.getString("max_tokens", "1000")
        val temperature = mPreferences.getString("temperature", "0")
        val top_p = mPreferences.getString("top_p", "1")
        val n = mPreferences.getString("n", "1")
        val stream = mPreferences.getString("stream", "false")
        val logprobs = mPreferences.getString("logprobs", "null")
        val frequency_penalty = mPreferences.getString("frequency_penalty", "0")
        val presence_penalty = mPreferences.getString("presence_penalty", "0.6")

        val gpt3Settings = mapOf("model" to model, "max_tokens" to max_tokens, "temperature" to temperature,
                        "top_p" to top_p, "n" to n, "stream" to stream, "logprobs" to logprobs,
                        "frequency_penalty" to frequency_penalty, "presence_penalty" to presence_penalty)

        return gpt3Settings
    }

    @Throws(IOException::class, WavFileException::class, FileFormatNotSupportedException::class)
    fun readMagnitudeValuesFromFile(
        path: String,
        sampleRate: Int,
        readDurationInSeconds: Int,
        offsetDuration: Int
    ): Array<FloatArray>? {
        if (!path.endsWith(".wav")) {
            throw FileFormatNotSupportedException(
                "File format not supported. jLibrosa currently supports audio processing of only .wav files"
            )
        }
        val sourceFile = File(path)
        var wavFile: WavFile? = null
        wavFile = WavFile.openWavFile(sourceFile)
        var mNumFrames = wavFile.numFrames.toInt()
        var mSampleRate = wavFile.sampleRate.toInt()
        val mChannels = wavFile.numChannels
        val totalNoOfFrames = mNumFrames
        val frameOffset = offsetDuration * mSampleRate
        var tobeReadFrames = readDurationInSeconds * mSampleRate
        if (tobeReadFrames > totalNoOfFrames - frameOffset) {
            tobeReadFrames = totalNoOfFrames - frameOffset
        }
        if (readDurationInSeconds != -1) {
            mNumFrames = tobeReadFrames
            wavFile.numFrames = mNumFrames.toLong()
        }
        if (sampleRate != -1) {
            mSampleRate = sampleRate
        }

        // Read the magnitude values across both the channels and save them as part of
        // multi-dimensional array
        val buffer = Array(mChannels) {
            FloatArray(
                mNumFrames
            )
        }
        var readFrameCount: Long = 0
        // for (int i = 0; i < loopCounter; i++) {
        readFrameCount = wavFile.readFrames(buffer, mNumFrames, frameOffset)
        // }
        wavFile?.close()
        return buffer
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) { // handle user response to permission request
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 200 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permission to record audio granted", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Permission to record audio denied", Toast.LENGTH_LONG).show()

        }
    }

    private fun showRationalDialog( title: String, message: String){
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton("Cancel"){ dialog, _->
                dialog.dismiss()
            }
        builder.create().show()
    }
    private fun requestStoragePermission(){
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
        ){
            showRationalDialog("Storage Permission",
                "Need permission for storage")
        }else{
            requestPermission.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
    }
}