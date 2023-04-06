package com.example.buddycareassistant

import android.Manifest
import android.annotation.SuppressLint
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
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.buddycareassistant.bluetoothcontrol.BluetoothControlActivity
import com.example.buddycareassistant.bluetoothcontrol.BluetoothControllerImpl
import com.example.buddycareassistant.googlespeechservices.GoogleServices
import com.example.buddycareassistant.gpt3documentation.ParametersInfoActivity
import com.example.buddycareassistant.gpt3settings.GPT3SettingsActivity
import com.example.buddycareassistant.recordaudio.VoiceRecorder
import com.example.buddycareassistant.service.AssistantService
import com.example.buddycareassistant.storemessages.MessageStorage
import com.konovalov.vad.VadConfig
import java.io.File


class MainActivity : AppCompatActivity() {
    private lateinit var btnRecord: Button
    private lateinit var btnPlay: Button
    private lateinit var btnStopAudio: Button
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
    private var mediaPlayer: MediaPlayer = MediaPlayer()
    private var mediaPlayerSilence: MediaPlayer =MediaPlayer()
    private var isMediaPlayerInitialized = true
    private var isMediaPlayerSilenceInitialized = true
    private lateinit var radioGroupLM: RadioGroup
//    private lateinit var recorder: AudioRecorder
    private lateinit var outputFile: File
    private lateinit var googlestt: GoogleServices
    private lateinit var handler: Handler
    private lateinit var messageStorage: MessageStorage
    var audioManager: AudioManager? = null
    private lateinit var bluetoothController: BluetoothControllerImpl
    val time = Time()
    var isRecorderAvailable = true
    private lateinit var storeAndRetrieveMessages: StoreAndRetrieveMessages
    private val DEFAULT_SAMPLE_RATE = VadConfig.SampleRate.SAMPLE_RATE_16K
    private val DEFAULT_FRAME_SIZE = VadConfig.FrameSize.FRAME_SIZE_160
    private val DEFAULT_MODE = VadConfig.Mode.VERY_AGGRESSIVE
    private val DEFAULT_SILENCE_DURATION = 500
    private val DEFAULT_VOICE_DURATION = 500
    private var config: VadConfig? = null
    private lateinit var voiceRecorder: VoiceRecorder
    private val mPreferences by lazy {
        getSharedPreferences("assistant_demo", MODE_PRIVATE)
    }


//    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        googlestt = GoogleServices(this, assets)
        radioGroupLM = findViewById(R.id.radGroupLMType)
//        recorder = AudioRecorder(this)

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

        config = VadConfig.newBuilder()
            .setSampleRate(DEFAULT_SAMPLE_RATE)
            .setFrameSize(DEFAULT_FRAME_SIZE)
            .setMode(DEFAULT_MODE)
            .setSilenceDurationMillis(DEFAULT_SILENCE_DURATION)
            .setVoiceDurationMillis(DEFAULT_VOICE_DURATION)
            .build()

        voiceRecorder = VoiceRecorder(this, config)

        messageStorage = MessageStorage(this)

        Log.d("scoTest", "MODIFY_AUDIO_SETTINGS: " + (ActivityCompat.checkSelfPermission(this, Manifest.permission.MODIFY_AUDIO_SETTINGS) == PackageManager.PERMISSION_GRANTED).toString())
        pathToRecords = File(externalCacheDir?.absoluteFile, "AudioRecord" )
        if (!pathToRecords.exists()){
            pathToRecords.mkdir()
        }
        bluetoothController = BluetoothControllerImpl(this)

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



        txtReceived = findViewById(R.id.txtReceived)
        txtSent = findViewById(R.id.txtSent)
        btnStopAudio = findViewById(R.id.btnStopAudio)
        btnInfo = findViewById(R.id.btnInfo)

        btnInfo.setOnClickListener {
            startActivity(Intent(this@MainActivity, ParametersInfoActivity::class.java))
        }
        btnSettings = findViewById(R.id.btnSettings)
        btnSettings.setOnClickListener {
            startActivity(Intent(this@MainActivity, GPT3SettingsActivity::class.java))
        }
        btnRecord.setOnClickListener {
//            recorder.stop()
            stopRecording()

//            recorder.stopRecording()
            btnRecord.text = "Start"
            isRecorderAvailable = true
        }
        btnStopAudio.setOnClickListener {
            if (mediaPlayer.isPlaying){
                mediaPlayer.stop()
                mediaPlayer.reset()
                isMediaPlayerInitialized = false
            }
            if (mediaPlayerSilence.isPlaying){
                mediaPlayerSilence.stop()
                mediaPlayerSilence.reset()
                isMediaPlayerSilenceInitialized = false
            }
        }

        btnPlay = findViewById(R.id.btnPlay)
        btnPlay.text = "Play"
        btnPlay.setOnClickListener{
            if (playingAvailable){
                if (audioFilePath.isNotEmpty()){
                    val assetManager = this.assets
                    val firstFileDescriptor = assetManager.openFd("silence.mp3")
                    if (!isMediaPlayerInitialized){
                        mediaPlayer = MediaPlayer()
                    }
                    mediaPlayer.reset()
                    mediaPlayer.setDataSource(firstFileDescriptor.fileDescriptor, firstFileDescriptor.startOffset, firstFileDescriptor.length)
                    mediaPlayer.prepare()
                    mediaPlayer.setOnCompletionListener {
                        val mediaPlayerSilence = MediaPlayer()
                        mediaPlayerSilence.setDataSource(audioFilePath)
                        mediaPlayerSilence.prepare()
                        mediaPlayerSilence.start()
                    }
                    mediaPlayer.start()

                }
    //                if (prevSentAudio?.path?.isNotEmpty() == true){
    //                    prevSentAudio?.delete()
    //                }

            } else{
                Toast.makeText(this, "No record found", Toast.LENGTH_SHORT).show()
            }

        }


        if(mPreferences.getString("language_model", "gpt-3").equals("gpt-3")){
            radioGroupLM.check(R.id.radBtnGPT3)
        } else {
            radioGroupLM.check(R.id.radBtnNaverClova)
        }
        onNewIntent(intent)
    }

    private fun stopRecording() {
        val intent = Intent(this@MainActivity, AssistantService::class.java)
            .apply { action = AssistantService.STOP_ACTION }
        //            startService(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun startRecording() {
        val intent = Intent(this@MainActivity, AssistantService::class.java )
            .apply { action = AssistantService.START_ACTION }
//                startService(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

//    @RequiresApi(Build.VERSION_CODES.S)
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("txtSentText", txtSent.text.toString())
        outState.putString("txtReceivedText", txtReceived.text.toString())
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        txtSent.text = savedInstanceState.getString("txtSentText")
        txtReceived.text = savedInstanceState.getString("txtReceivedText")
    }



    @RequiresApi(Build.VERSION_CODES.S)
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        Log.d("scoTest", intent?.action.toString())

        if(intent?.action == "android.intent.action.VOICE_COMMAND") {
            if (mediaPlayer.isPlaying){
                mediaPlayer.stop()
                mediaPlayer.reset()
                isMediaPlayerInitialized = false
            }
            if (mediaPlayerSilence.isPlaying){
                mediaPlayerSilence.stop()
                mediaPlayerSilence.reset()
                isMediaPlayerSilenceInitialized = false
            }

            assistantDemo()
        }
    }

//    @RequiresApi(Build.VERSION_CODES.P)
    private fun checkIntent() {
        if (intent.action != "android.intent.action.VOICE_COMMAND" && !isRecorderAvailable) {
//            enableVoiceRecord()
//            recorder.stop()

            stopRecording()

            isRecorderAvailable = true
        }
    }


    private val mBluetoothScoReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.O)
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
            Log.d("scoTest", "ANDROID Audio SCO state: $state")
            time.setToNow()
            Log.d("scoTest Time", time.format("%Y%m%d%H%M%S"))
            Log.d("scoTest", "state " + state + " " +
                    audioManager!!.isBluetoothScoOn + " " + audioManager!!.isSpeakerphoneOn
            )
            if (AudioManager.SCO_AUDIO_STATE_CONNECTED == state) {
                Log.d("scoTest", "Recording started")
                isRecorderAvailable = false
                time.setToNow()

                val audioName = time.format("%Y%m%d%H%M%S") + ".pcm"
                outputFile = File(pathToRecords, audioName)
//                recorder.start(outputFile)

                startRecording()

//                recorder.startRecording(outputFile)
//                voiceRecorder.start(outputFile)
                btnRecord.text = "Recording"


            } else if (AudioManager.SCO_AUDIO_STATE_DISCONNECTED == state) {
                if (!isRecorderAvailable) {
                    Log.d("scoTest", "Disconnected, disable sco")
                    disableVoiceRecord()
                    isRecorderAvailable = true
                    assistantDemoHelper()
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun enableVoiceRecord() {
        val intentFilter = IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        this.registerReceiver( mBluetoothScoReceiver, intentFilter)
        audioManager = this.getSystemService(AudioManager::class.java)
        audioManager!!.mode = AudioManager.MODE_NORMAL
        audioManager!!.isBluetoothScoOn = true
        audioManager!!.startBluetoothSco()

        audioManager!!.isSpeakerphoneOn = false;


    }

    @SuppressLint("WrongConstant")
    fun disableVoiceRecord() {
        try {
            this.unregisterReceiver(mBluetoothScoReceiver)
        } catch (_: Exception) {
        }
        // Stop Bluetooth SCO.
        audioManager!!.mode = AudioManager.MODE_NORMAL
        audioManager!!.isBluetoothScoOn = false
        audioManager!!.stopBluetoothSco()

        // Start Speaker.
        audioManager!!.isSpeakerphoneOn = true
    }

//    @RequiresApi(Build.VERSION_CODES.S)
    private fun assistantDemo() {

        if (isRecorderAvailable) {
            enableVoiceRecord()
//            start()

        } else {
//            stop()
            assistantDemoHelper()
        }
    }

//    @RequiresApi(Build.VERSION_CODES.M)
    @RequiresApi(Build.VERSION_CODES.M)
    private fun assistantDemoHelper(){
//        recorder.stop()
        stopRecording()

//        recorder.stopRecording()
//        voiceRecorder.stop()
        btnRecord.text = "Start"
        isRecorderAvailable = true
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
//                            prevSentAudio = fileName
                            Log.d("pathToSavingAudio", pathToSavingAudio.toString())
                            Log.d("audioFilePath", audioFilePath)
                            googlestt.googletts(audioFilePath, responseFromNaverClova)
//                            if (prevRecAudio.isNotEmpty()){
//                                val ff = File(prevRecAudio)
//                                ff.delete()
//                            }
                            runOnUiThread {
                                txtSent.text ="Sent: $ttt"
                                txtReceived.text = "Received: $responseFromNaverClova"
                            }
                            playAudio()
                        }
                    }else{
                        var korToEng = googlestt.googleTranslatorKoreanToEnglish(ttt)

                        Log.d("ddd korToEng", korToEng)
                        val gpt3Settings = getGPT3Settings()
                        if (korToEng == ""){
                            korToEng = "I don't understand, can you repeat."
                            val engToKor = googlestt.googleTranslatorEnglishToKorean(korToEng)
                            val time = Time()
                            time.setToNow()
                            audioFilePath = pathToSavingAudio.toString() + "/" + time.format("%Y%m%d%H%M%S").toString()+".mp3"
//                            prevSentAudio = fileName
                            Log.d("pathToSavingAudio", pathToSavingAudio.toString())
                            Log.d("audioFilePath", audioFilePath)

                            googlestt.googletts(audioFilePath, engToKor)
                            Log.d("ddd googletts", audioFilePath)

//                            if (prevRecAudio.isNotEmpty()){
//                                val ff = File(prevRecAudio)
//                                ff.delete()
//                            }
                            runOnUiThread {
                                txtReceived.text = "Received: $engToKor"
                            }
                            playAudio()
                        }else{
                            googlestt.getResponseGPT3(gpt3Settings, korToEng){ responseFromGPT3->
                                val engToKor = googlestt.googleTranslatorEnglishToKorean(responseFromGPT3)
                                val userMessage = "User:$korToEng"
                                val gptMessage = "Assistant:$responseFromGPT3"
                                Log.d("messages", "User: $userMessage; gpt: $gptMessage")

                                val messages = listOf(
                                    Pair(userMessage, gptMessage)
                                )
                                messageStorage.storeMessages(messages)

                                Log.d("ddd engToKor", engToKor)

                                val time = Time()
                                time.setToNow()
                                audioFilePath = pathToSavingAudio.toString() + "/" + time.format("%Y%m%d%H%M%S").toString()+".mp3"
//                            prevSentAudio = fileName
                                Log.d("pathToSavingAudio", pathToSavingAudio.toString())
                                Log.d("audioFilePath", audioFilePath)

                                googlestt.googletts(audioFilePath, engToKor)
                                Log.d("ddd googletts", audioFilePath)

//                            if (prevRecAudio.isNotEmpty()){
//                                val ff = File(prevRecAudio)
//                                ff.delete()
//                            }
                                runOnUiThread {
                                    txtReceived.text = "Received: " + engToKor
                                }
                                playAudio()
                            }
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
                val firstFileDescriptor = assetManager.openFd("silenceShort.mp3")
                if (!isMediaPlayerInitialized){
                    mediaPlayer = MediaPlayer()
                }
                if (!isMediaPlayerSilenceInitialized){
                    mediaPlayerSilence = MediaPlayer()
                }
                mediaPlayer.reset()
                mediaPlayer.setDataSource(firstFileDescriptor.fileDescriptor, firstFileDescriptor.startOffset, firstFileDescriptor.length)
                mediaPlayer.prepare()
                mediaPlayer.setOnCompletionListener {
                    mediaPlayerSilence.reset()
                    mediaPlayerSilence.setDataSource(audioFilePath)
                    mediaPlayerSilence.prepare()
                    mediaPlayerSilence.start()
                }
                mediaPlayer.start()

            }
//            if (prevSentAudio?.path?.isNotEmpty() == true){
//                prevSentAudio?.delete()
//            }

        } else{
            Toast.makeText(this, "No record found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initGPT3Settings(){
        val gpt3SettingsPreferences = mPreferences.edit()
        gpt3SettingsPreferences.putString("model", "gpt-3.5-turbo")
        gpt3SettingsPreferences.putString("max_tokens", "200")
        gpt3SettingsPreferences.putString("temperature", "1")
        gpt3SettingsPreferences.putString("top_p", "1")
        gpt3SettingsPreferences.putString("n", "1")
        gpt3SettingsPreferences.putString("stream", "false")
        gpt3SettingsPreferences.putString("logprobs", "null")
        gpt3SettingsPreferences.putString("frequency_penalty", "0")
        gpt3SettingsPreferences.putString("presence_penalty", "0.6")
        gpt3SettingsPreferences.putString("language_model", "gpt-3")
        gpt3SettingsPreferences.putString("tokensCheckBox", "false")
        gpt3SettingsPreferences.apply()


    }

    private fun getGPT3Settings(): Map<String, String?> {
        val model = mPreferences.getString("model", "gpt-3.5-turbo")
        val max_tokens = mPreferences.getString("max_tokens", "200")
        val temperature = mPreferences.getString("temperature", "1")
        val top_p = mPreferences.getString("top_p", "1")
        val n = mPreferences.getString("n", "1")
        val stream = mPreferences.getString("stream", "false")
        val logprobs = mPreferences.getString("logprobs", "null")
        val frequency_penalty = mPreferences.getString("frequency_penalty", "0")
        val presence_penalty = mPreferences.getString("presence_penalty", "0.6")
        val tokensInfo = mPreferences.getString("tokensCheckBox", "false")

        val gpt3Settings = mapOf("model" to model, "max_tokens" to max_tokens, "temperature" to temperature,
                        "top_p" to top_p, "n" to n, "stream" to stream, "logprobs" to logprobs,
                        "frequency_penalty" to frequency_penalty, "presence_penalty" to presence_penalty, "tokensCheckBox" to tokensInfo)

        return gpt3Settings
    }


}