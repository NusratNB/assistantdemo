package com.example.buddycareassistant.service

import ai.picovoice.cobra.Cobra
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.*
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.media.MediaPlayer
import android.media.SoundPool
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.*
import android.text.format.Time
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.buddycareassistant.MainActivity
import com.example.buddycareassistant.R
import com.example.buddycareassistant.googlespeechservices.GoogleServices
import com.example.buddycareassistant.recordaudio.AudioRecorder
import com.example.buddycareassistant.storemessages.MessageStorage
import com.example.buddycareassistant.utils.LogUtil
import java.io.File
import java.io.IOException
import java.util.*


open class AssistantService : Service() {
    private var FOREGROUND_CHANNEL_ID = "ASSISTANT_CHANNEL"
    private lateinit var mNotificationManager: NotificationManager
    private lateinit var mNotificationBuilder: NotificationCompat.Builder
    private lateinit var mPreferences: SharedPreferences
    private lateinit var pref: SharedPreferences
    private var recorder: AudioRecorder? = null
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
    private lateinit var googleServices: GoogleServices
    private var audioFilePath = ""
    private var memory_quality = "Low"
    private var language = "Korean"
    private var conversational = "More Creative"
    private var gender = "Male"
    private var mediaPlayerSilence: MediaPlayer = MediaPlayer()
    private var mediaPlayerResponse: MediaPlayer = MediaPlayer()
    private var isMediaPlayerSilenceInitialized = true
    private var isMediaPlayerResponceInitialized = true
    private val BEGINNING_ALERT = "alerts/Beginning_alt.mp3"
    private lateinit var soundPool: SoundPool
    private lateinit var prevSentAudio: File
    private var prevRecAudio = ""
    private var isRecordingEnabled = false
    private var soundId = 0
    var isNeverClova: Boolean = false
    private lateinit var ctx: Context
    private lateinit var logger: LogUtil
    private var recordedChangedAudioName = ""
    private lateinit var cobraVAD: Cobra
    private val cobraAccessKey = "pZNnAfYBzFJPPyNrSVx4KW5T8S7sRc1uIAlrUkf8LUlnzLUkAGlt+g=="
    private var lastMuteTime: Long = 0
    private val vadQueue = LinkedList<Float>()
    private var audioFocusResult:Int = 0
    private lateinit var mAudioAttributes: AudioAttributes

    override fun onCreate() {
        super.onCreate()
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        googleServices = GoogleServices(this, assets)
        mNotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        mPreferences = getSharedPreferences("buddycare_assistant", MODE_PRIVATE)
        pref = getSharedPreferences("assistant_demo", MODE_PRIVATE)

        pathToRecords = File(externalCacheDir?.absoluteFile, "AudioRecord")
        if (!pathToRecords.exists()) {
            pathToRecords.mkdir()
        }
        pathToSavingAudio = File(externalCacheDir?.absoluteFile, "SavedRecordings")
        if (!pathToSavingAudio.exists()) {
            pathToSavingAudio.mkdir()
            initGPT3Settings()
        }
        ctx = this
        logger = LogUtil
        cobraVAD = Cobra(cobraAccessKey)
        recorder = AudioRecorder(this, cobraVAD) {probablity ->
            vadQueue.addFirst(probablity)
            if (vadQueue.size > 120){
                checkSilence()
            }
        }

        mAudioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .build()


        val filterBluetoothConnection = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED)
        }
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
//        registerReceiver(bluetoothDisconnectReceiver, filterBluetoothConnection)


        isNeverClova = !mPreferences.getString("language_model", "gpt-3").equals("gpt-3")
        messageStorage = MessageStorage(this)
        setupBluetoothHeadset()
        val filter = IntentFilter(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)
        registerReceiver(AudioStateReceiver(), filter)
    }

    var focusChangeListener =  OnAudioFocusChangeListener { focusChange ->
            logger.i(ctx, TAG, "Audio focus: $focusChange")
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                // Pause playback
            } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                // Resume playback or raise it from duck volume
            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                closeChannelAndStopRecording()
                // Stop playback, because you've lost focus permanently.
                // This is likely because the user has started a new media player
                // that doesn't know how to duck, so we need to stop playback immediately.
            }
        }

    private fun checkSilence() {
        val elements = vadQueue.slice(IntRange(0, 120))
        var mins = 0
        elements.forEach { if (it < 0.25f) mins ++ }
        if (mins > 100) {
            vadQueue.clear()
            stopRecordingAndExecuteAssistantHelperFunc()
        }
    }

    fun setupBluetoothHeadset() {
        bluetoothAdapter?.getProfileProxy(this, profileListener, BluetoothProfile.HEADSET)
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HEADSET) {
                bluetoothHeadset = proxy as BluetoothHeadset
                ActivityCompat.checkSelfPermission(ctx, android.Manifest.permission.BLUETOOTH_CONNECT)
                val connectedDevices = bluetoothHeadset?.connectedDevices
                for (conDevice in connectedDevices!!){
                    if (conDevice.address.slice(0..7) == "78:02:B7"){
                        deviceBluetooth = conDevice
//                        Log.i(TAG, "Connected bluetooth device information. MAC: ${deviceBluetooth!!.address}, NAME: ${deviceBluetooth!!.name}")
                        logger.i(ctx, TAG, "Connected bluetooth device information. MAC: ${deviceBluetooth!!.address}, NAME: ${deviceBluetooth!!.name}")
                    }else{
                        Toast.makeText(ctx, "No device found", Toast.LENGTH_SHORT).show()
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
    inner class AudioStateReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, -1)
                logger.d(ctx, TAG, "BluetoothHeadset current state: $state")
                if (state == BluetoothHeadset.STATE_AUDIO_DISCONNECTED ) {
                    logger.i(context, TAG, "isRecordingAvailable: $isRecorderAvailable")
                    closeChannelAndStopRecording()

                }
            }
        }
    }

    private fun handleDisconnection() {
        // Close the BluetoothHeadset proxy
        stopPlayer()
        closeBluetoothConnection()
        Toast.makeText(this, "Bluetooth headset connection lost", Toast.LENGTH_SHORT).show()
//        Log.i(TAG, "handelDisconnection: Bluetooth headset connection lost")
//        Log.i(TAG, "handelDisconnection: Due to Bluetooth headset disconnection, the recorder is stopped!")
        logger.i(ctx, TAG, "handelDisconnection: Bluetooth headset connection lost")
        logger.i(ctx, TAG, "handelDisconnection: Due to Bluetooth headset disconnection, the recorder is stopped!")
    }

    private fun closeBluetoothConnection() {
        if (bluetoothHeadset != null) {
            bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HEADSET, bluetoothHeadset)
            bluetoothHeadset = null
        }
        deviceBluetooth = null
    }

    private fun closeChannelAndStopRecording(isFromApi: Boolean =true){
        isRecordingEnabled = false
        sendBroadcast(Intent(MainActivity.RECORDING_STATE).apply {
            putExtra("isRecording", false)
            putExtra("isRecordingEnabled", false)
        })
        val result: Int = audioManager.abandonAudioFocus(focusChangeListener)
        if (result==AudioManager.AUDIOFOCUS_REQUEST_GRANTED){
            logger.i(ctx, TAG, "Audio focus successfully abandoned")
        }else
            logger.i(ctx, TAG, "Warning: Audio focus not abandoned")
        releaseMediaPlayer()
        closeChannel()
    }

    private fun playAudio() {
        if (playingAvailable) {
            if (audioFilePath.isNotEmpty()) {
                val assetManager = this.assets
                val firstFileDescriptor = assetManager.openFd("silenceShort.mp3")
                checkMediaPlayersIsInitialized()
                mediaPlayerSilence.reset()
                mediaPlayerSilence.setDataSource(
                    firstFileDescriptor.fileDescriptor,
                    firstFileDescriptor.startOffset,
                    firstFileDescriptor.length
                )
                mediaPlayerSilence.prepare()
                mediaPlayerSilence.setOnCompletionListener {
                    val file = File(audioFilePath)
                    if (file.exists()) {
                        mediaPlayerResponse.reset()
                        mediaPlayerResponse.setDataSource(audioFilePath)
                        mediaPlayerResponse.setAudioAttributes(mAudioAttributes)
                        mediaPlayerResponse.setOnPreparedListener {
                            mediaPlayerResponse.start()
                            Log.d("TestingPhoneProfile", "MediaPlayer started")
                        }
                        mediaPlayerResponse.prepareAsync()
                        mediaPlayerResponse.setOnCompletionListener {
                            if (isRecordingEnabled){
                                playRecordStartedNotification()
                                Handler().postDelayed({
                                    startRecording()
                                }, 1500)
                            }
                        }
                        logger.i(ctx, TAG, "Playing audio in path: $audioFilePath")
                    }
                }
                mediaPlayerSilence.start()
                logger.i(ctx, TAG, "Playing silence")
                playingAvailable = false

            }else {
                logger.i(ctx, TAG, "No record found")
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this, "No record found", Toast.LENGTH_SHORT).show()
                }

            }
//            if (prevSentAudio.path.isNotEmpty() == true){
//                prevSentAudio.delete()
//            }
            prevRecAudio = audioFilePath

        }
    }



    fun stopPlayer() {
        if (isMediaPlayerSilenceInitialized){
            if (mediaPlayerSilence.isPlaying){
                mediaPlayerSilence.stop()
                mediaPlayerSilence.reset()
                isMediaPlayerSilenceInitialized = false
            }
        }
        if (isMediaPlayerResponceInitialized){
            if (mediaPlayerResponse.isPlaying){
                mediaPlayerResponse.stop()
                mediaPlayerResponse.reset()
                isMediaPlayerResponceInitialized = false
            }
        }


    }

    private fun releaseMediaPlayer(){
        stopPlayer()
        mediaPlayerSilence.release()
        mediaPlayerResponse.release()
        isMediaPlayerResponceInitialized = false
        isMediaPlayerSilenceInitialized = false
    }

    private fun startRecording() {
        if (isRecordingEnabled){
            sendBroadcast(Intent(MainActivity.RECORDING_STATE).apply {
                putExtra("isRecording", true)
            })
            time.setToNow()
            val audioName = time.format("%Y%m%d%H%M%S") + ".pcm"
            outputFile = File(pathToRecords, audioName)
            recordedChangedAudioName = "changed_$audioName"
            recorder?.start(outputFile)
            isRecordingAvailable = false
        }else{
            sendBroadcast(Intent(MainActivity.RECORDING_STATE).apply {
                putExtra("isRecordingEnabled", false)
            })
        }

    }

    private fun stopRecording() {
        recorder?.stop()
        isRecordingAvailable = true
    }

    private fun closeChannel(){
        ActivityCompat.checkSelfPermission(ctx, android.Manifest.permission.BLUETOOTH_CONNECT)
        bluetoothHeadset?.stopVoiceRecognition(deviceBluetooth)

    }
    private fun openChannel(){
        ActivityCompat.checkSelfPermission(ctx, android.Manifest.permission.BLUETOOTH_CONNECT)
        bluetoothHeadset?.startVoiceRecognition(deviceBluetooth)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID_FOREGROUND_SERVICE, prepareNotification())
        val action = intent.action
//        Log.d("action in foreground: ", "$action")
        logger.d(ctx, "action in foreground: ", "$action")
        if (action == START_ACTION) {
            openChannel()
            isRecordingEnabled = true
            startVoiceCommand()
        } else if (action == STOP_ACTION) {
            closeChannel()
            stopRecording()
        } else if (action == START_VOICE_COMMAND_ACTION) {
            openChannel()
            audioFocusResult = audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            if (audioFocusResult==AudioManager.AUDIOFOCUS_REQUEST_GRANTED){
                isRecordingEnabled = true
                startVoiceCommand()
            }else
                closeChannelAndStopRecording()

        }
        return START_NOT_STICKY
    }
    private fun playRecordStartedNotification(){
        if (mediaPlayerSilence.isPlaying) {
            mediaPlayerSilence.stop()
            mediaPlayerSilence.reset()
            isMediaPlayerSilenceInitialized = false
        }
        if (mediaPlayerResponse.isPlaying) {
            mediaPlayerResponse.stop()
            mediaPlayerResponse.reset()
            isMediaPlayerResponceInitialized = false
        }
//        val audioAttributes = AudioAttributes.Builder()
//            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
//            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
//            .build()

        soundPool = SoundPool.Builder()
            .setAudioAttributes(mAudioAttributes)
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
//        Log.i(TAG, "playRecordStartedNotification(): Notification is played")
        logger.i(ctx, TAG, "playRecordStartedNotification(): Notification is played")
    }
    private fun stopRecordingAndExecuteAssistantHelperFunc(){
        if (!isRecordingAvailable){
            stopRecording()
        }

        if (!isRecordingEnabled){
            sendBroadcast(Intent(MainActivity.RECORDING_STATE).apply {
                putExtra("isRecording", false)
                putExtra("isRecordingEnabled", false)
            })
        }

        isRecordingAvailable = true
        playingAvailable = true

        if (isRecordingEnabled){
            sendBroadcast(Intent(MainActivity.RECORDING_STATE).apply {
                putExtra("isRecording", false)
                putExtra("isRecordingEnabled", true)
            })
            try {
                assistantDemoHelper()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }


    }
    private fun startVoiceCommand() {
        checkMediaPlayersIsInitialized()
        if (mediaPlayerSilence.isPlaying) {
            mediaPlayerSilence.stop()
            mediaPlayerSilence.reset()
            isMediaPlayerSilenceInitialized = false
        }
        if (mediaPlayerResponse.isPlaying) {
            mediaPlayerResponse.stop()
            mediaPlayerResponse.reset()
            isMediaPlayerResponceInitialized = false
        }
        playRecordStartedNotification()
        val settings = getSettings()
        memory_quality = settings["memory_quality"].toString()
        language = settings["language"].toString()
        conversational = settings["conversational"].toString()
        gender = settings["gender"].toString()
        try {
            if (isRecordingEnabled){
                Handler().postDelayed({
                    startRecording()
                }, 1800)
            }


        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun checkMediaPlayersIsInitialized(){
        if (!isMediaPlayerSilenceInitialized) {
            mediaPlayerSilence = MediaPlayer()
        }
        if (!isMediaPlayerResponceInitialized) {
            mediaPlayerResponse = MediaPlayer()
        }
    }
    private fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        connectivityManager?.let {
            val networkCapabilities = it.getNetworkCapabilities(it.activeNetwork)
            networkCapabilities?.let { capabilities ->
                return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                        || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                        || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)
            }
        }
        return false
    }
    private fun assistantDemoHelper() {
        if (!isRecordingAvailable) {
            stopRecording()
            playingAvailable = true
        }
        val fileName = outputFile
        if (fileName.path.isNotEmpty()) {
            Thread {
                val googleSTTResult = googleServices.getSTTText(fileName.path, language, recordedChangedAudioName, pathToRecords)
                logger.d(ctx, TAG, "Google STT result: $googleSTTResult")
                sendBroadcast(Intent(MainActivity.ASSISTANT_RESPONSE_STATE).apply {
                    putExtra("isReceived", false)
                    putExtra("data", googleSTTResult)
                })
                Thread {
                    if (isNeverClova) {

                        googleServices.getResponseClovaStudio(googleSTTResult) { responseFromNaverClova ->
                            val time = Time()
                            time.setToNow()
                            audioFilePath =
                                "$pathToSavingAudio/" + time.format("%Y%m%d%H%M%S")
                                    .toString() + ".mp3"
                            prevSentAudio = fileName

                            logger.d(ctx, TAG, "pathToSavingAudio: $pathToSavingAudio")
                            logger.d(ctx, TAG, "audioFilePath, $audioFilePath")

                            googleServices.googletts(audioFilePath, responseFromNaverClova,language, gender)
                            if (prevRecAudio.toString().isNotEmpty()){
                                val ff = File(prevRecAudio)
                                ff.delete()
                            }
                            sendBroadcast(Intent(MainActivity.ASSISTANT_RESPONSE_STATE).apply {
                                putExtra("isReceived", true)
                                putExtra("isNeverClova", true)
                                putExtra("data", googleSTTResult)
                                putExtra("responseText", responseFromNaverClova)
                                putExtra("isRecording", isRecordingAvailable)
                            })
                            playAudio()
                        }
                    } else {
                        val korToEng = googleServices.googleTranslatorKoreanToEnglish(googleSTTResult, language)

//                        Log.d(TAG,"Korean to English translation: $korToEng" )
                        logger.d(ctx, TAG,"Korean to English translation: $korToEng")
                        val gpt3Settings = getGPT3Settings()
                        if (korToEng == "") {
                            closeChannelAndStopRecording(false)

                        } else {
                            googleServices.getResponseGPT3(gpt3Settings, korToEng, memory_quality, conversational) { responseFromGPT3 ->
                                // engToKor: Google Translator result
                                val engToKor =
                                    googleServices.googleTranslatorEnglishToKorean(responseFromGPT3, language)
                                val userMessage = "User:$googleSTTResult"
                                val gptMessage = "Assistant:$engToKor"
//                                Log.d(TAG, "Messages: User: $userMessage; gpt: $gptMessage")
                                logger.d(ctx, TAG, "Messages: User: $userMessage; gpt: $gptMessage")

                                val messages = listOf(
                                    Pair(gptMessage, userMessage)
                                )
                                messageStorage.storeMessages(messages)

//                                Log.d(TAG, "English to Korean translation: $engToKor")
                                logger.d(ctx, TAG, "English to Korean translation: $engToKor")

                                val time = Time()
                                time.setToNow()
                                audioFilePath =
                                    "$pathToSavingAudio/" + time.format("%Y%m%d%H%M%S")
                                        .toString() + ".mp3"
                                prevSentAudio = fileName
                                logger.d(ctx, TAG, "pathToSavingAudio: $pathToSavingAudio")
                                logger.d(ctx, TAG, "audioFilePath: $audioFilePath")

                                googleServices.googletts(audioFilePath, engToKor, language, gender)
//                                Log.d(TAG,"Google TTS file path: $audioFilePath")
                                logger.d(ctx, TAG,"Google TTS file path: $audioFilePath")

//                                if (prevRecAudio.isNotEmpty()){
//                                    val ff = File(prevRecAudio)
//                                    ff.delete()
//                                }
                                sendBroadcast(Intent(MainActivity.ASSISTANT_RESPONSE_STATE).apply {
                                    putExtra("isReceived", true)
                                    putExtra("isNeverClova", false)
                                    putExtra("data", googleSTTResult)
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
//        unregisterReceiver(bluetoothDisconnectReceiver)
    }

    private fun getSettings():Map<String, String?>{
        val memory_quality = pref.getString("memory_quality", "Low")
        val language = pref.getString("language", "Korean")
        val conversational = pref.getString("conversational", "More Creative")
        val gender = pref.getString("gender", "Male")

        val settings = mapOf("memory_quality" to memory_quality, "language" to language,
                        "conversational" to conversational, "gender" to gender)

        logger.i(ctx, TAG, "Settings: $settings")

        return settings
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
        val systemRoleContent = mPreferences.getString("systemRoleContent", "You are a helpful friend.")


        val gpt3Settings = mapOf(
            "model" to model, "max_tokens" to max_tokens, "temperature" to temperature,
            "top_p" to top_p, "n" to n, "stream" to stream, "logprobs" to logprobs,
            "frequency_penalty" to frequency_penalty, "presence_penalty" to presence_penalty,
            "chatWindowSize" to chatWindowSize, "tokensCheckBox" to tokensInfo, "systemRoleContent" to systemRoleContent
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
        gpt3SettingsPreferences.putString("systemRoleContent", "You are a helpful friend.")
        gpt3SettingsPreferences.apply()
    }
    companion object {
        const val NOTIFICATION_ID_FOREGROUND_SERVICE = 8466503
        const val START_ACTION = "START_ACTION"
        const val STOP_ACTION = "STOP_ACTION"
        const val START_VOICE_COMMAND_ACTION = "START_VOICE_COMMAND_ACTION"
    }
}