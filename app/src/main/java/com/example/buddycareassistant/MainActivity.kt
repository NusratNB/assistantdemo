package com.example.buddycareassistant

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.*
import android.provider.Settings
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
import java.io.IOException


class MainActivity : AppCompatActivity() {
    private lateinit var btnRecord: Button
    private lateinit var btnPlay: Button
    private lateinit var btnStopAudio: Button
    private lateinit var btnInfo: Button
    private lateinit var  btnNewChatRoom: Button
    private lateinit var btnSettings: Button
    private lateinit var btnBluetoothControl: Button
    private lateinit var fullAudioPath: File
    private lateinit var pathToRecords: File
    private lateinit var pathToSavingAudio: File
    private lateinit var pathToSavingMessagesMainActivity: File
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
    private val REQUEST_ENABLE_BT = 1
    private val PERMISSION_REQUEST_CODE = 2
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothHeadset: BluetoothHeadset? = null
    private val DEFAULT_SAMPLE_RATE = VadConfig.SampleRate.SAMPLE_RATE_16K
    private val DEFAULT_FRAME_SIZE = VadConfig.FrameSize.FRAME_SIZE_160
    private val DEFAULT_MODE = VadConfig.Mode.VERY_AGGRESSIVE
    private val DEFAULT_SILENCE_DURATION = 500
    private val DEFAULT_VOICE_DURATION = 500
    private var config: VadConfig? = null
    private lateinit var voiceRecorder: VoiceRecorder
    private var deviceBluetooth: BluetoothDevice? = null
    private var isRecordingAvailable = true
    private val TAG = this::class.java.simpleName
    private val targetDeviceName =  "DT AudioBF19"
    private val BEGINNING_ALERT = "alerts/Beginning.mp3"
    private var isVoiceCommandReceived = false
    private lateinit var wakeLock: PowerManager.WakeLock
    private val mPreferences by lazy {
        getSharedPreferences("assistant_demo", MODE_PRIVATE)
    }
    private var foregroundBleService: AssistantService? = null
    private val serviceConnection by lazy {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                foregroundBleService = (service as AssistantService.LocalBinder).service
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                foregroundBleService = null
            }
        }
    }
    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HEADSET) {
                bluetoothHeadset = proxy as BluetoothHeadset
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HEADSET) {
                bluetoothHeadset = null
            }
        }
    }

    private val audioStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT)
            if (intent.action == BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, -1)
                Log.d(TAG, "current state: $state")
//                if (state == BluetoothHeadset.STATE_AUDIO_CONNECTED) {
//                    if (isRecordingAvailable) {
//                        bluetoothHeadset?.startVoiceRecognition(deviceBluetooth)
//                        btnRecord.text = "Recording"
//                        startRecording()
//                        isRecordingAvailable = false
//                    }
//                } else if (state == BluetoothHeadset.STATE_AUDIO_DISCONNECTED) {
//                    if (!isRecordingAvailable){
//                        bluetoothHeadset?.stopVoiceRecognition(deviceBluetooth)
//                        stopRecording()
//                        btnRecord.text = "Record"
//                        isRecordingAvailable = true
//                        playingAvailable = true
//                        assistantDemoHelper()
//                    }
//                }
            }
        }
    }


    //    @RequiresApi(Build.VERSION_CODES.S)
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        startService(Intent(this, AssistantService::class.java))
        bindService(Intent(this, AssistantService::class.java), serviceConnection, BIND_AUTO_CREATE)
        googlestt = GoogleServices(this, assets)
        radioGroupLM = findViewById(R.id.radGroupLMType)
//        recorder = AudioRecorder(this)

        radioGroupLM.setOnCheckedChangeListener { radioGroup, id ->

            if(id == R.id.radBtnGPT3) {
                mPreferences.edit().putString("language_model", "gpt-3").apply()
                foregroundBleService?.isNeverClova = false
            } else {
                mPreferences.edit().putString("language_model", "naver_clova").apply()
                foregroundBleService?.isNeverClova = true
            }
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { // get permission
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.MODIFY_AUDIO_SETTINGS,
                Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.MODIFY_PHONE_STATE, Manifest.permission.WAKE_LOCK),200);
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::PartialWakeLock")

        config = VadConfig.newBuilder()
            .setSampleRate(DEFAULT_SAMPLE_RATE)
            .setFrameSize(DEFAULT_FRAME_SIZE)
            .setMode(DEFAULT_MODE)
            .setSilenceDurationMillis(DEFAULT_SILENCE_DURATION)
            .setVoiceDurationMillis(DEFAULT_VOICE_DURATION)
            .build()

        voiceRecorder = VoiceRecorder(this, config)

        messageStorage = MessageStorage(this)
        btnNewChatRoom = findViewById(R.id.btnNewChatRoom)

        btnNewChatRoom.setOnClickListener {
            val gptPromptFileName = "GPTPrompt.txt"
            pathToSavingMessagesMainActivity = File(this.externalCacheDir?.absolutePath, "Messages")
            if (!pathToSavingMessagesMainActivity.exists()){
                pathToSavingMessagesMainActivity.mkdir()
            }
            val tempText = """
            {
              "model": "gpt-3.5-turbo",
              "messages": [
                        {"role": "system", "content": "You are a helpful friend."}
                    ],
               "max_tokens": 200,
               "temperature": 1.0,
               "top_p": 1.0,
               "n": 1,
               "stream": false,
               "frequency_penalty":0.0,
               "presence_penalty":0.6
            }
        """
            val file = File(pathToSavingMessagesMainActivity, gptPromptFileName)
            messageStorage.saveGptPrompt(tempText)

            Toast.makeText(this, "New Chat room has been created", Toast.LENGTH_LONG).show()
        }

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            return
        }

//        if (!bluetoothAdapter!!.isEnabled) {
//            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
//            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
//        } else {
//            setupBluetoothHeadset()
//        }

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
//        audioManager = applicationContext.getSystemService(AUDIO_SERVICE) as AudioManager

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
        requestDisableBatteryOptimization()
        btnRecord.setOnClickListener {
//            recorder.stop()
            toggleVoiceRecognition()

//            recorder.stopRecording()
            btnRecord.text = "Record"
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
            val intent = Intent(this, AssistantService::class.java)
            intent.action = AssistantService.START_VOICE_COMMAND_ACTION
            startService(intent)
        //    foregroundBleService?.startVoiceCommand()
//            if (mediaPlayer.isPlaying){
//                mediaPlayer.stop()
//                mediaPlayer.reset()
//                isMediaPlayerInitialized = false
//            }
//            if (mediaPlayerSilence.isPlaying){
//                mediaPlayerSilence.stop()
//                mediaPlayerSilence.reset()
//                isMediaPlayerSilenceInitialized = false
//            }
//            isVoiceCommandReceived = true
//
//            try {
//                val assetFileDescriptor = assets.openFd(BEGINNING_ALERT)
//                mediaPlayer = MediaPlayer().apply {
//                    setDataSource(assetFileDescriptor.fileDescriptor, assetFileDescriptor.startOffset, assetFileDescriptor.length)
//                    setOnCompletionListener {
//                        // Call assistantDemoHelper() function after the audio has finished playing
//                        toggleVoiceRecognition()
//                    }
//                    prepare()
//                    start()
//                }
//            } catch (e: IOException) {
//                e.printStackTrace()
//            }
        }
    }

    private fun requestDisableBatteryOptimization() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        }
    }


    @RequiresApi(Build.VERSION_CODES.S)
    private fun toggleVoiceRecognition() {
        ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT)
        val connectedDevices = bluetoothHeadset?.connectedDevices
        deviceBluetooth = connectedDevices!!.get(0)
//        for (dd in connectedDevices!!){
//            if (dd.name == targetDeviceName){
//                deviceBluetooth = dd
//            }
//        }
        if (isRecordingAvailable){
            bluetoothHeadset?.startVoiceRecognition(deviceBluetooth)
            btnRecord.text = "Recording"
            time.setToNow()

            val audioName = time.format("%Y%m%d%H%M%S") + ".pcm"
            outputFile = File(pathToRecords, audioName)
            startRecording()
            isRecordingAvailable = false
        } else{
            bluetoothHeadset?.stopVoiceRecognition(deviceBluetooth)
            stopRecording()
            btnRecord.text = "Record"
            isRecordingAvailable = true
            playingAvailable = true
            assistantDemoHelper()
        }
    }
    private fun assistantDemoHelper(){
        if (!isRecordingAvailable){
            stopRecording()
            btnRecord.text = "Start"
            isRecorderAvailable = true
            // audioRecorder.audioName
            playingAvailable = true
        }
        fileName = foregroundBleService?.outputFile!!
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

    private fun setupBluetoothHeadset() {
        bluetoothAdapter?.getProfileProxy(this, profileListener, BluetoothProfile.HEADSET)
    }

//    override fun onResume() {
//        super.onResume()
//        val filter = IntentFilter(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)
//
//        registerReceiver(audioStateReceiver, filter)
//    }
//
//
//
//    override fun onPause() {
//        super.onPause()
//        unregisterReceiver(audioStateReceiver)
////        unregisterReceiver(mBlu)
//    }
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
            setupBluetoothHeadset()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onDestroy() {
        super.onDestroy()
//        mediaSessionManager.removeOnActiveSessionsChangedListener(activeSessionsChangedListener)
        bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HEADSET, bluetoothHeadset)
//        unregisterReceiver(audioStateChangedReceiver)
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
        gpt3SettingsPreferences.putString("chatWindowSize", "5")
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
        val chatWindowSize = mPreferences.getString("chatWindowSize", "5")
        val tokensInfo = mPreferences.getString("tokensCheckBox", "false")

        val gpt3Settings = mapOf("model" to model, "max_tokens" to max_tokens, "temperature" to temperature,
                        "top_p" to top_p, "n" to n, "stream" to stream, "logprobs" to logprobs,
                        "frequency_penalty" to frequency_penalty, "presence_penalty" to presence_penalty,
                        "chatWindowSize" to chatWindowSize, "tokensCheckBox" to tokensInfo)

        return gpt3Settings
    }
}
