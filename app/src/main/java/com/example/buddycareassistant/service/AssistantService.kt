package com.example.buddycareassistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.*
import android.media.*
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.text.format.Time
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.buddycareassistant.MainActivity
import com.example.buddycareassistant.R
import com.example.buddycareassistant.googlespeechservices.GoogleServices
import com.example.buddycareassistant.recordaudio.AudioRecorder
import com.example.buddycareassistant.recordaudio.VoiceRecorder
import com.example.buddycareassistant.storemessages.MessageStorage
import com.konovalov.vad.VadConfig
import java.io.File
import java.io.IOException

open class AssistantService : Service() {
    private var FOREGROUND_CHANNEL_ID = "ASSISTANT_CHANNEL"
    private lateinit var mNotificationManager: NotificationManager
    private lateinit var mNotificationBuilder: NotificationCompat.Builder
    private lateinit var mPreferences: SharedPreferences
    private lateinit var recorder: AudioRecorder
    private lateinit var audioManager: AudioManager
    private val time = Time()
    lateinit var outputFile: File
    lateinit var pathToSavingAudio: File
    private lateinit var pathToRecords: File
    private val TAG ="BuddyCareAssistant: " + this::class.java.simpleName
    private var isRecordingAvailable = true
    private var bluetoothHeadset: BluetoothHeadset? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var deviceBluetooth: BluetoothDevice? = null
    private var playingAvailable: Boolean = false
    private var isRecorderAvailable = true
    private lateinit var messageStorage: MessageStorage
    private lateinit var googlestt: GoogleServices
    private var audioFilePath = ""
    private var mediaPlayer: MediaPlayer = MediaPlayer()
    private var mediaPlayerSilence: MediaPlayer = MediaPlayer()
    private var isMediaPlayerInitialized = true
    private var isMediaPlayerSilenceInitialized = true
    private val BEGINNING_ALERT = "alerts/Beginning_alt.mp3"
    private val END_ALERT = "alerts/End.mp3"
    private lateinit var soundPool: SoundPool
    private lateinit var soundPoolEndNotification: SoundPool
    private lateinit var prevSentAudio: File
    private var prevRecAudio = ""
    private var soundId = 0
    var isNeverClova: Boolean = false
    private val DEFAULT_SAMPLE_RATE = VadConfig.SampleRate.SAMPLE_RATE_16K
    private val DEFAULT_FRAME_SIZE = VadConfig.FrameSize.FRAME_SIZE_160
    private val DEFAULT_MODE = VadConfig.Mode.VERY_AGGRESSIVE
    private val DEFAULT_SILENCE_DURATION = 500
    private val DEFAULT_VOICE_DURATION = 500
    private var config: VadConfig? = null
    private lateinit var voiceRecorder: VoiceRecorder

    override fun onCreate() {
        super.onCreate()
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        googlestt = GoogleServices(this, assets)
        mNotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        mPreferences = getSharedPreferences("buddycare_assistant", MODE_PRIVATE)
        recorder = AudioRecorder(this)
        pathToRecords = File(externalCacheDir?.absoluteFile, "AudioRecord")
        if (!pathToRecords.exists()) {
            pathToRecords.mkdir()
        }
        pathToSavingAudio = File(externalCacheDir?.absoluteFile, "SavedRecordings")
        if (!pathToSavingAudio.exists()) {
            pathToSavingAudio.mkdir()
            initGPT3Settings()
        }

        val filterBluetoothConnection = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED)
        }
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        registerReceiver(bluetoothDisconnectReceiver, filterBluetoothConnection)

        isNeverClova = !mPreferences.getString("language_model", "gpt-3").equals("gpt-3")
        messageStorage = MessageStorage(this)
        setupBluetoothHeadset()
        val filter = IntentFilter(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)
        registerReceiver(AudioStateReceiver(), filter)

        config = VadConfig.newBuilder()
            .setSampleRate(DEFAULT_SAMPLE_RATE)
            .setFrameSize(DEFAULT_FRAME_SIZE)
            .setMode(DEFAULT_MODE)
            .setSilenceDurationMillis(DEFAULT_SILENCE_DURATION)
            .setVoiceDurationMillis(DEFAULT_VOICE_DURATION)
            .build()

        voiceRecorder = VoiceRecorder(this, config)

    }

    fun setupBluetoothHeadset() {
        bluetoothAdapter?.getProfileProxy(this, profileListener, BluetoothProfile.HEADSET)
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HEADSET) {
                bluetoothHeadset = proxy as BluetoothHeadset
                val connectedDevices = bluetoothHeadset?.connectedDevices
                for (conDevice in connectedDevices!!){
                    if (conDevice.address.slice(0..7) == "78:02:B7"){
                        deviceBluetooth = conDevice
                        Log.i(TAG, "Connected bluetooth device information. MAC: ${deviceBluetooth!!.address}, NAME: ${deviceBluetooth!!.name}")
                    }
                }
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HEADSET) {
                bluetoothHeadset = null
                deviceBluetooth = null
            }
        }
    }

    private val bluetoothDisconnectReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (BluetoothDevice.ACTION_ACL_DISCONNECTED == action || BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED == action) {
                Log.d(TAG, "Device is disconnected")
                handleDisconnection()
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                if (device != null && deviceBluetooth != null && device.address == deviceBluetooth?.address) {
//                    handleDisconnection()
                }
            }
        }
    }


    inner class AudioStateReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT)
            if (intent.action == BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, -1)
                Log.d(TAG, "service current state: $state")
                if (state == BluetoothHeadset.STATE_AUDIO_CONNECTED) {
                    if (isRecordingAvailable) {
                        sendBroadcast(Intent(MainActivity.RECORDING_STATE).apply {
                            putExtra("isRecording", true)
                        })
                        startVoiceCommand()
                        isRecordingAvailable = false
                    }
                } else if (state == BluetoothHeadset.STATE_AUDIO_DISCONNECTED) {
                    if (!isRecordingAvailable) {
                        stopRecordingAndPlayNotification()
                    }
                }
            }
        }
    }

//    private fun enableAudioManagerSCO(){
//        audioManager.startBluetoothSco()
//        audioManager.isSpeakerphoneOn = false
//        audioManager.isBluetoothScoOn = true
//
//    }
//
//    private fun disableAudioManagerSCO(){
//        audioManager.stopBluetoothSco()
//        audioManager.isSpeakerphoneOn = true
//        audioManager.isBluetoothScoOn = false
//
//    }

    private fun handleDisconnection() {
        // Close the BluetoothHeadset proxy
        stopPlayer()
        closeBluetoothConnection()
        Toast.makeText(this, "Bluetooth headset connection lost", Toast.LENGTH_SHORT).show()
        Log.i(TAG, "handelDisconnection: Bluetooth headset connection lost")
        Log.i(TAG, "handelDisconnection: Due to Bluetooth headset disconnection, the recorder is stopped!")
    }

    private fun closeBluetoothConnection() {
        if (bluetoothHeadset != null) {
            bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HEADSET, bluetoothHeadset)
            bluetoothHeadset = null
        }
        deviceBluetooth = null
    }

    private fun playAudio() {
        if (playingAvailable) {
            if (audioFilePath.isNotEmpty()) {
                val assetManager = this.assets
                val firstFileDescriptor = assetManager.openFd("silenceShort.mp3")
                if (!isMediaPlayerInitialized) {
                    mediaPlayer = MediaPlayer()
                }
                if (!isMediaPlayerSilenceInitialized) {
                    mediaPlayerSilence = MediaPlayer()
                }
                mediaPlayer.reset()
                mediaPlayer.setDataSource(
                    firstFileDescriptor.fileDescriptor,
                    firstFileDescriptor.startOffset,
                    firstFileDescriptor.length
                )
                mediaPlayer.prepare()
                mediaPlayer.setOnCompletionListener {
                    mediaPlayerSilence.reset()
                    mediaPlayerSilence.setDataSource(audioFilePath)
                    mediaPlayerSilence.prepare()
                    mediaPlayerSilence.start()
                }
                mediaPlayer.start()
                playingAvailable = false

            }
            if (prevSentAudio.path.isNotEmpty() == true){
                prevSentAudio.delete()
            }
            prevRecAudio = audioFilePath.toString()

        } else {
            Toast.makeText(this, "No record found", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopPlayer() {
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
//        disableAudioManagerSCO()
    }

//    private fun playAudioThroughBluetoothDevice() {
//        audioManager.let { manager ->
//            val audioDevices = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
//            for (deviceInfo in audioDevices) {
//                if (deviceInfo.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
//                    deviceInfo.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
//
//                    // Set the audio output to the Bluetooth device
//                    val audioAttributes = AudioAttributes.Builder()
//                        .setUsage(AudioAttributes.USAGE_MEDIA)
//                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
//                        .build()
//                    manager.setRouting(
//                        AudioManager.MODE_NORMAL,
//                        deviceInfo.id,
//                        AudioManager.Routing.USER)
//                    break
//                }
//            }
//        }
//    }
    private fun startRecording() {
        bluetoothHeadset?.startVoiceRecognition(deviceBluetooth)
        time.setToNow()
        val audioName = time.format("%Y%m%d%H%M%S") + ".pcm"
        outputFile = File(pathToRecords, audioName)
//        disableAudioManagerSCO()
        recorder.start(outputFile)
        isRecordingAvailable = false
    }

    private fun stopRecording() {
        bluetoothHeadset?.stopVoiceRecognition(deviceBluetooth)
        recorder.stop()
        isRecordingAvailable = true
    }
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID_FOREGROUND_SERVICE, prepareNotification())
        val action = intent.action
        Log.d("action in foreground: ", "$action")
        if (action == START_ACTION) {
            startVoiceCommand()
        } else if (action == STOP_ACTION) {
            stopRecording()
        } else if (action == START_VOICE_COMMAND_ACTION) {
            startVoiceCommand()
        }
        return START_NOT_STICKY
    }
    private fun playRecordStartedNotification(){
        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
            mediaPlayer.reset()
            isMediaPlayerInitialized = false
        }
        if (mediaPlayerSilence.isPlaying) {
            mediaPlayerSilence.stop()
            mediaPlayerSilence.reset()
            isMediaPlayerSilenceInitialized = false
        }
//        enableAudioManagerSCO()
        soundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .build()

        soundPool.setOnLoadCompleteListener { soundPool, sampleId, status ->
            if (status == 0) {
                soundPool.play(sampleId, 0.9f, 0.9f, 1, 0, 1f)
            }
        }
        try {
            val assetFileDescriptor = assets.openFd(BEGINNING_ALERT)
            soundId = soundPool.load(assetFileDescriptor, 1)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

//    private fun playRecordEndNotification(){
//        if (!isRecordingAvailable) {
//            stopRecording()
//            isRecorderAvailable = true
//            playingAvailable = true
//        }
////        enableAudioManagerSCO()
//        soundPoolEndNotification = SoundPool.Builder()
//            .setMaxStreams(1)
//            .build()
//
//        soundPoolEndNotification.setOnLoadCompleteListener { soundPooll, sampleId, status ->
//            if (status == 0) {
//                soundPooll.play(sampleId, 0.9f, 0.9f, 1, 0, 1f)
//            }
//        }
//        try {
//            val assetFileEndNotification = assets.openFd(END_ALERT)
//            val soundIdEndNotification = soundPoolEndNotification.load(assetFileEndNotification, 1)
//        } catch (e: IOException){
//            e.printStackTrace()
//        }
//    }
    private fun stopRecordingAndPlayNotification(){

        stopRecording()
        sendBroadcast(Intent(MainActivity.RECORDING_STATE).apply {
            putExtra("isRecording", false)
        })
        isRecordingAvailable = true
        playingAvailable = true
//        playRecordEndNotification()

        try {
            assistantDemoHelper()
//            Handler().postDelayed({
//                assistantDemoHelper()
//            }, 1500)

        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
    fun startVoiceCommand() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
            mediaPlayer.reset()
            isMediaPlayerInitialized = false
        }
        if (mediaPlayerSilence.isPlaying) {
            mediaPlayerSilence.stop()
            mediaPlayerSilence.reset()
            isMediaPlayerSilenceInitialized = false
        }
        playRecordStartedNotification()
        try {
            Handler().postDelayed({
                startRecording()
            }, 1800)

        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun assistantDemoHelper() {
        if (!isRecordingAvailable) {
            stopRecording()
//            btnRecord.text = "Start"
            isRecorderAvailable = true
            // audioRecorder.audioName
            playingAvailable = true
        }
        val fileName = outputFile
        if (fileName.path.isNotEmpty()) {
            Thread {
                val ttt = googlestt.getSTTText(fileName.path)
                Log.d("ddd googlestt", ttt)
                sendBroadcast(Intent(MainActivity.ASSISTANT_RESPONSE_STATE).apply {
                    putExtra("isReceived", false)
                    putExtra("data", ttt)
                })
                Thread {
                    if (isNeverClova) {

                        googlestt.getResponseClovaStudio(ttt) { responseFromNaverClova ->
//                                    Thread{
                            val time = Time()
                            time.setToNow()
                            audioFilePath =
                                pathToSavingAudio.toString() + "/" + time.format("%Y%m%d%H%M%S")
                                    .toString() + ".mp3"
                            prevSentAudio = fileName
                            Log.d("pathToSavingAudio", pathToSavingAudio.toString())
                            Log.d("audioFilePath", audioFilePath)
                            googlestt.googletts(audioFilePath, responseFromNaverClova)
                            if (prevRecAudio.toString().isNotEmpty()){
                                val ff = File(prevRecAudio)
                                ff.delete()
                            }
                            sendBroadcast(Intent(MainActivity.ASSISTANT_RESPONSE_STATE).apply {
                                putExtra("isReceived", true)
                                putExtra("isNeverClova", true)
                                putExtra("data", ttt)
                                putExtra("responseText", responseFromNaverClova)
                                putExtra("isRecording", isRecordingAvailable)
                            })
                            playAudio()
                        }
                    } else {
                        var korToEng = googlestt.googleTranslatorKoreanToEnglish(ttt)

                        Log.d("ddd korToEng", korToEng)
                        val gpt3Settings = getGPT3Settings()
                        if (korToEng == "") {
                            korToEng = "I don't understand, can you repeat."
                            val engToKor = googlestt.googleTranslatorEnglishToKorean(korToEng)
                            val time = Time()
                            time.setToNow()
                            audioFilePath =
                                pathToSavingAudio.toString() + "/" + time.format("%Y%m%d%H%M%S")
                                    .toString() + ".mp3"
                            prevSentAudio = fileName
                            Log.d("pathToSavingAudio", pathToSavingAudio.toString())
                            Log.d("audioFilePath", audioFilePath)

                            googlestt.googletts(audioFilePath, engToKor)
                            Log.d("ddd googletts", audioFilePath)

                            if (prevRecAudio.isNotEmpty()){
                                val ff = File(prevRecAudio)
                                ff.delete()
                            }
                            sendBroadcast(Intent(MainActivity.ASSISTANT_RESPONSE_STATE).apply {
                                putExtra("isReceived", true)
                                putExtra("isNeverClova", false)
                                putExtra("data", ttt)
                                putExtra("responseText", engToKor)
                            })
                            playAudio()
                        } else {
                            googlestt.getResponseGPT3(gpt3Settings, korToEng) { responseFromGPT3 ->
                                val engToKor =
                                    googlestt.googleTranslatorEnglishToKorean(responseFromGPT3)
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
                                audioFilePath =
                                    "$pathToSavingAudio/" + time.format("%Y%m%d%H%M%S")
                                        .toString() + ".mp3"
                                prevSentAudio = fileName
                                Log.d("pathToSavingAudio", pathToSavingAudio.toString())
                                Log.d("audioFilePath", audioFilePath)

                                googlestt.googletts(audioFilePath, engToKor)
                                Log.d("ddd googletts", audioFilePath)

                                if (prevRecAudio.isNotEmpty()){
                                    val ff = File(prevRecAudio)
                                    ff.delete()
                                }
                                sendBroadcast(Intent(MainActivity.ASSISTANT_RESPONSE_STATE).apply {
                                    putExtra("isReceived", true)
                                    putExtra("isNeverClova", false)
                                    putExtra("data", ttt)
                                    putExtra("responseText", engToKor)
                                })
                                playAudio()
                            }
                        }
                    }
                }.start()
            }.start()

        } else {
            Toast.makeText(this, "Record audio", Toast.LENGTH_SHORT).show()
        }
    }

    private fun prepareNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            mNotificationManager.getNotificationChannel(FOREGROUND_CHANNEL_ID) == null
        ) {
            // The user-visible name of the channel.
            val name: CharSequence = "Bluetooth service"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val mChannel = NotificationChannel(FOREGROUND_CHANNEL_ID, name, importance)
            mChannel.setSound(null, null)
            mChannel.enableVibration(false)
            mNotificationManager.createNotificationChannel(mChannel)
        }
        mNotificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
        } else {
            NotificationCompat.Builder(this)
        }

        mNotificationBuilder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setAutoCancel(true)
        mNotificationBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        return mNotificationBuilder.build()
    }

    private val mBinder: IBinder = LocalBinder()

    inner class LocalBinder : Binder() {
        val service: AssistantService
            get() {
                return this@AssistantService
            }
    }

    override fun onBind(intent: Intent?): IBinder {
        return mBinder
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(AudioStateReceiver())
        soundPool.release()
        bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HEADSET, bluetoothHeadset)
        unregisterReceiver(bluetoothDisconnectReceiver)

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

        val gpt3Settings = mapOf(
            "model" to model, "max_tokens" to max_tokens, "temperature" to temperature,
            "top_p" to top_p, "n" to n, "stream" to stream, "logprobs" to logprobs,
            "frequency_penalty" to frequency_penalty, "presence_penalty" to presence_penalty,
            "chatWindowSize" to chatWindowSize, "tokensCheckBox" to tokensInfo
        )
        return gpt3Settings
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
    companion object {
        const val NOTIFICATION_ID_FOREGROUND_SERVICE = 8466503
        const val START_ACTION = "START_ACTION"
        const val STOP_ACTION = "STOP_ACTION"
        const val START_VOICE_COMMAND_ACTION = "START_VOICE_COMMAND_ACTION"
    }
}