package com.example.buddycareassistant

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.text.format.Time
import android.util.Log
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.buddycareassistant.bluetoothcontrol.BluetoothControlActivity
import com.example.buddycareassistant.googlespeechservices.GoogleServices
import com.example.buddycareassistant.gpt3documentation.ParametersInfoActivity
import com.example.buddycareassistant.gpt3settings.GPT3SettingsActivity
import com.example.buddycareassistant.recordaudio.AudioRecorder
import java.io.File


class MainActivity : AppCompatActivity() {
    private lateinit var btnRecord: Button
    private lateinit var btnPlay: Button
    private lateinit var btnInference: Button
    private lateinit var btnInfo: Button
    private lateinit var btnSettings: Button
    private lateinit var btnBluetoothControl: Button
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
    private lateinit var recorder: AudioRecorder
    private lateinit var outputFile: File
    private lateinit var googlestt: GoogleServices
    private lateinit var handler: Handler
    var audioManager: AudioManager? = null
    private val RECORDING_TIME = 5000
    val time = Time()
    var recording = true
    private val mPreferences by lazy {
        getSharedPreferences("assistant_demo", MODE_PRIVATE)
    }


    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        googlestt = GoogleServices(assets)
        radioGroupLM = findViewById(R.id.radGroupLMType)
        recorder = AudioRecorder(this)

        radioGroupLM.setOnCheckedChangeListener { radioGroup, id ->

            if(id == R.id.radBtnGPT3) {
                mPreferences.edit().putString("language_model", "gpt-3").apply()

            } else {

                mPreferences.edit().putString("language_model", "naver_clova").apply()

            }
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { // get permission
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.MODIFY_AUDIO_SETTINGS,
                Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_COARSE_LOCATION),200);
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
        btnBluetoothControl = findViewById(R.id.btnBluetoothControl)
        btnBluetoothControl.setOnClickListener {
            startActivity(Intent(this@MainActivity, BluetoothControlActivity::class.java))
        }

        handler = Handler()
        btnRecord = findViewById(R.id.btnRecord)
        btnRecord.text = "Start"


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

        }
        txtReceived = findViewById(R.id.txtReceived)
        txtSent = findViewById(R.id.txtSent)
        btnInference = findViewById(R.id.btnPredict)
        btnInfo = findViewById(R.id.btnInfo)
        btnInfo.setOnClickListener {
            startActivity(Intent(this@MainActivity, ParametersInfoActivity::class.java))
        }
        btnSettings = findViewById(R.id.btnSettings)
        btnSettings.setOnClickListener {
            startActivity(Intent(this@MainActivity, GPT3SettingsActivity::class.java))
        }

        if(mPreferences.getString("language_model", "gpt-3").equals("gpt-3")){
            radioGroupLM.check(R.id.radBtnGPT3)
        } else {
            radioGroupLM.check(R.id.radBtnNaverClova)
        }
//        Handler().postDelayed({
//            checkIntent(intent)
//        }, 2000)
        onNewIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if(intent?.action == "android.intent.action.VOICE_COMMAND") {
            time.setToNow()
            val runnable = Runnable {
                assistantDemoHelper()
            }
            val audioName = time.format("%Y%m%d%H%M%S") + ".pcm"
            outputFile = File(pathToRecords, audioName)
            recorder.start(outputFile)
            btnRecord.text = "Recording"
            recording = false

            handler.postDelayed(runnable, RECORDING_TIME.toLong())



            btnRecord.setOnClickListener{
                assistantDemo()
            }
        }
    }

    private fun assistantDemo() {

        if (recording) {
//            enableVoiceRecord()
            time.setToNow()
            val audioName = time.format("%Y%m%d%H%M%S") + ".pcm"
            outputFile = File(pathToRecords, audioName)
            recorder.start(outputFile)
            btnRecord.text = "Recording"
            recording = false

        } else {
            assistantDemoHelper()
        }
    }

    private fun assistantDemoHelper(){
//        disableVoiceRecord()
        recorder.stop()
        btnRecord.text = "Start"
        recording = true
        fileName = outputFile// audioRecorder.audioName
        playingAvailable = true

        if (fileName.path.isNotEmpty()){
            Thread {
                val ttt = googlestt.getSTTText(fileName.path)
                Log.d("ddd googlestt", ttt)
                runOnUiThread {
                    txtSent.text ="Sent: $ttt"
                    txtReceived.text = "Received: Not received yet..."
                }
                Thread {
                    if(radioGroupLM.checkedRadioButtonId==R.id.radBtnNaverClova){

                        googlestt.getResponseClovaStudio(ttt){ responseFromNaverClova->
//                                    Thread{
                            val time = Time()
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
                            runOnUiThread {
                                txtSent.text ="Sent: $ttt"
                                txtReceived.text = "Received: $responseFromNaverClova"
                            }
                            playAudio()
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
                            playAudio()
                        }
                    }
                }.start()

            }.start()
        }else{
            Toast.makeText(this, "Record audio", Toast.LENGTH_SHORT).show()
        }
    }




    private fun playAudio(){
        if (playingAvailable){
            Log.d("rrrr playingAvailable", playingAvailable.toString())
            Log.d("rrrr audioFilePath", audioFilePath)
            if (audioFilePath.isNotEmpty()){
                Log.d("rrrr audioFilePath", audioFilePath)
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
        gpt3SettingsPreferences.putString("language_model", "gpt-3")
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


}