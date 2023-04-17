package com.example.buddycareassistant.service

import android.Manifest
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
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.PowerManager
import android.text.format.Time
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.startActivityForResult
import androidx.core.app.NotificationCompat
import com.example.buddycareassistant.MainActivity
import com.example.buddycareassistant.R
import com.example.buddycareassistant.googlespeechservices.GoogleServices
import com.example.buddycareassistant.recordaudio.AudioRecorder
import com.example.buddycareassistant.storemessages.MessageStorage
import java.io.File
import java.io.IOException

open class AssistantService : Service() {
    private var FOREGROUND_CHANNEL_ID = "ASSISTANT_CHANNEL"
    private lateinit var mNotificationManager: NotificationManager
    private lateinit var mNotificationBuilder: NotificationCompat.Builder
    private lateinit var mPreferences: SharedPreferences
    private lateinit var recorder: AudioRecorder
    private val time = Time()
    lateinit var outputFile: File
    lateinit var pathToSavingAudio: File
    private lateinit var pathToRecords: File
    private val TAG = this::class.java.simpleName
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
    private val BEGINNING_ALERT = "alerts/Beginning.mp3"
    private lateinit var soundPool: SoundPool
    private var soundId = 0
    private lateinit var wakeLock: PowerManager.WakeLock
    var isNeverClova: Boolean = false

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
//            initGPT3Settings()
        }
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(audioAttributes)
            .build()
        isNeverClova = !mPreferences.getString("language_model", "gpt-3").equals("gpt-3")

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock =
            powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AssistantService::WakeLock")
        wakeLock.acquire()

        messageStorage = MessageStorage(this)
        setupBluetoothHeadset()
        val filter = IntentFilter(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)
//        registerReceiver(audioStateReceiver, filter)
        registerReceiver(AudioStateReceiver(), filter)
    }

    fun setupBluetoothHeadset() {
        bluetoothAdapter?.getProfileProxy(this, profileListener, BluetoothProfile.HEADSET)
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HEADSET) {
                bluetoothHeadset = proxy as BluetoothHeadset
                val connectedDevices = bluetoothHeadset?.connectedDevices
                deviceBluetooth = connectedDevices!!.get(0)
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HEADSET) {
                bluetoothHeadset = null
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
                        //   bluetoothHeadset?.startVoiceRecognition(deviceBluetooth)
                        // btnRecord.text = "Recording"
                        sendBroadcast(Intent(MainActivity.RECORDING_STATE).apply {
                            putExtra("isRecording", true)
                        })
                        startRecording()
                        isRecordingAvailable = false
                    }
                } else if (state == BluetoothHeadset.STATE_AUDIO_DISCONNECTED) {
                    if (!isRecordingAvailable) {
                        //    bluetoothHeadset?.stopVoiceRecognition(deviceBluetooth)
                        stopRecording()
                        //btnRecord.text = "Record"
                        sendBroadcast(Intent(MainActivity.RECORDING_STATE).apply {
                            putExtra("isRecording", false)
                        })
                        isRecordingAvailable = true
                        playingAvailable = true
                        assistantDemoHelper()
                    }
                }
            }
        }
    }

//    private val audioStateReceiver = object : BroadcastReceiver() {
//        override fun onReceive(context: Context, intent: Intent) {
//            // ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT)
//            if (intent.action == BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED) {
//                val state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, -1)
//                Log.d(TAG, "service current state: $state")
//                if (state == BluetoothHeadset.STATE_AUDIO_CONNECTED) {
//                    if (isRecordingAvailable) {
//                     //   bluetoothHeadset?.startVoiceRecognition(deviceBluetooth)
//                        // btnRecord.text = "Recording"
//                        startRecording()
//                        isRecordingAvailable = false
//                    }
//                } else if (state == BluetoothHeadset.STATE_AUDIO_DISCONNECTED) {
//                    if (!isRecordingAvailable) {
//                    //    bluetoothHeadset?.stopVoiceRecognition(deviceBluetooth)
//                        stopRecording()
//                        //btnRecord.text = "Record"
//                        isRecordingAvailable = true
//                        playingAvailable = true
//                        assistantDemoHelper()
//                    }
//                }
//            }
//        }
//    }

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
//                runOnUiThread {
//                    txtSent.text ="Sent: $ttt"
//                    txtReceived.text = "Received: Not received yet..."
//                }
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
//                            prevSentAudio = fileName
                            Log.d("pathToSavingAudio", pathToSavingAudio.toString())
                            Log.d("audioFilePath", audioFilePath)
                            googlestt.googletts(audioFilePath, responseFromNaverClova)
//                            if (prevRecAudio.isNotEmpty()){
//                                val ff = File(prevRecAudio)
//                                ff.delete()
//                            }
//                            runOnUiThread {
//                                txtSent.text ="Sent: $ttt"
//                                txtReceived.text = "Received: $responseFromNaverClova"
//                            }
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
//                            prevSentAudio = fileName
                            Log.d("pathToSavingAudio", pathToSavingAudio.toString())
                            Log.d("audioFilePath", audioFilePath)

                            googlestt.googletts(audioFilePath, engToKor)
                            Log.d("ddd googletts", audioFilePath)

//                            if (prevRecAudio.isNotEmpty()){
//                                val ff = File(prevRecAudio)
//                                ff.delete()
//                            }
//                            runOnUiThread {
//                                txtReceived.text = "Received: $engToKor"
//                            }
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
//                            prevSentAudio = fileName
                                Log.d("pathToSavingAudio", pathToSavingAudio.toString())
                                Log.d("audioFilePath", audioFilePath)

                                googlestt.googletts(audioFilePath, engToKor)
                                Log.d("ddd googletts", audioFilePath)

//                            if (prevRecAudio.isNotEmpty()){
//                                val ff = File(prevRecAudio)
//                                ff.delete()
//                            }
//                                runOnUiThread {
//                                    txtReceived.text = "Received: " + engToKor
//                                }
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

//        }.start()
        } else {
            Toast.makeText(this, "Record audio", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playAudio() {
        if (playingAvailable) {
            Log.d("rrrr playingAvailable", playingAvailable.toString())
            Log.d("rrrr audioFilePath", audioFilePath)
            if (audioFilePath.isNotEmpty()) {
                Log.d("rrrr audioFilePath", audioFilePath)
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

            }
            //            if (prevSentAudio?.path?.isNotEmpty() == true){
            //                prevSentAudio?.delete()
            //            }

        } else {
            Toast.makeText(this, "No record found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startRecording() {
        bluetoothHeadset?.startVoiceRecognition(deviceBluetooth)
        time.setToNow()
        val audioName = time.format("%Y%m%d%H%M%S") + ".pcm"
        outputFile = File(pathToRecords, audioName)
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
            startRecording()
        } else if (action == STOP_ACTION) {
            stopRecording()
        } else if (action == START_VOICE_COMMAND_ACTION) {
            startVoiceCommand()
        }
        return START_NOT_STICKY
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
        try {
            val assetFileDescriptor = assets.openFd(BEGINNING_ALERT)
            time.setToNow()
            Log.d("timeMeasure", "Time before media player: ${System.currentTimeMillis()}")
            mediaPlayer = MediaPlayer().apply {
                setVolume(1f, 1f)
                setDataSource(
                    assetFileDescriptor.fileDescriptor,
                    assetFileDescriptor.startOffset,
                    assetFileDescriptor.length
                )
//                setOnCompletionListener {
//                    time.setToNow()
//                    Log.d("timeMeasure", "Time before startRecording(): ${System.currentTimeMillis()}")
//                }
                prepare()
                start()

            }
//            time.setToNow()
//            Log.d("timeMeasure", "Time before startRecording(): ${System.currentTimeMillis()}")
            Handler().postDelayed({
                startRecording()
            }, 1000)
        } catch (e: IOException) {
            e.printStackTrace()
        }
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
//        unregisterReceiver(audioStateReceiver)
        unregisterReceiver(AudioStateReceiver())
        wakeLock.release()
        bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HEADSET, bluetoothHeadset)
        super.onDestroy()
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
    }

    companion object {
        const val NOTIFICATION_ID_FOREGROUND_SERVICE = 8466503
        const val START_ACTION = "START_ACTION"
        const val STOP_ACTION = "STOP_ACTION"
        const val START_VOICE_COMMAND_ACTION = "START_VOICE_COMMAND_ACTION"
    }
}