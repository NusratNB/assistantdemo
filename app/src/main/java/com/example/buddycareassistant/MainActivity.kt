package com.example.buddycareassistant

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioManager
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
    private lateinit var radioGroupLM: RadioGroup
    private lateinit var googlestt: GoogleServices
    private lateinit var handler: Handler
    private lateinit var messageStorage: MessageStorage
    var audioManager: AudioManager? = null
    val time = Time()
    var isRecorderAvailable = true
    private val REQUEST_ENABLE_BT = 1
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothHeadset: BluetoothHeadset? = null
    private val DEFAULT_SAMPLE_RATE = VadConfig.SampleRate.SAMPLE_RATE_16K
    private val DEFAULT_FRAME_SIZE = VadConfig.FrameSize.FRAME_SIZE_160
    private val DEFAULT_MODE = VadConfig.Mode.VERY_AGGRESSIVE
    private val DEFAULT_SILENCE_DURATION = 500
    private val DEFAULT_VOICE_DURATION = 500
    private var config: VadConfig? = null
//    private lateinit var voiceRecorder: VoiceRecorder
    private var deviceBluetooth: BluetoothDevice? = null
    private var isRecordingAvailable = true
    private val TAG ="BuddyCareAssistant: " + this::class.java.simpleName
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
                foregroundBleService?.isNeverClova =
                    !mPreferences.getString("language_model", "gpt-3").equals("gpt-3")
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                foregroundBleService = null
            }
        }
    }
    private val uiReceiver by lazy { UIReceiver() }

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
            Manifest.permission.MODIFY_PHONE_STATE, Manifest.permission.WAKE_LOCK, Manifest.permission.ACCESS_NETWORK_STATE),200);
        }

//        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
//        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::PartialWakeLock")

//        config = VadConfig.newBuilder()
//            .setSampleRate(DEFAULT_SAMPLE_RATE)
//            .setFrameSize(DEFAULT_FRAME_SIZE)
//            .setMode(DEFAULT_MODE)
//            .setSilenceDurationMillis(DEFAULT_SILENCE_DURATION)
//            .setVoiceDurationMillis(DEFAULT_VOICE_DURATION)
//            .build()
//
//        voiceRecorder = VoiceRecorder(this, config)

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

//        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
//
//        if (bluetoothAdapter == null) {
//            // Device doesn't support Bluetooth
//            return
//        }

//        pathToRecords = File(externalCacheDir?.absoluteFile, "AudioRecord" )
//        if (!pathToRecords.exists()){
//            pathToRecords.mkdir()
//        }
//        bluetoothController = BluetoothControllerImpl(this)
//
//        pathToSavingAudio = File(externalCacheDir?.absoluteFile, "SavedRecordings" )
//        if (!pathToSavingAudio.exists()){
//            pathToSavingAudio.mkdir()
//            initGPT3Settings()
//        }
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
//        requestDisableBatteryOptimization()

        btnStopAudio.setOnClickListener {
            foregroundBleService?.stopPlayer()
        }

        if(mPreferences.getString("language_model", "gpt-3").equals("gpt-3")){
            radioGroupLM.check(R.id.radBtnGPT3)
        } else {
            radioGroupLM.check(R.id.radBtnNaverClova)
        }
        onNewIntent(intent)
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

    override fun onResume() {
        super.onResume()
        registerReceiver(uiReceiver, IntentFilter().apply {
            addAction(RECORDING_STATE)
            addAction(ASSISTANT_RESPONSE_STATE)
        })
    }

    override fun onPause() {
        unregisterReceiver(uiReceiver)
        super.onPause()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        Log.d("scoTest", intent?.action.toString())

        if(intent?.action == "android.intent.action.VOICE_COMMAND") {
            val intent = Intent(this, AssistantService::class.java)
            intent.action = AssistantService.START_VOICE_COMMAND_ACTION
            startService(intent)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
            foregroundBleService?.setupBluetoothHeadset()
        }
    }



    inner class UIReceiver: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action.equals(RECORDING_STATE)) {
                val isRecording = intent?.getBooleanExtra("isRecording", false) ?: false
                if (isRecording) {
                     btnRecord.text = "Recording"
                } else {
                    btnRecord.text = "Record"
                }
            } else if (intent?.action.equals(ASSISTANT_RESPONSE_STATE)) {
                val isReceived = intent?.getBooleanExtra("isReceived", false)
                val isNeverClova = intent?.getBooleanExtra("isNeverClova", false)
                val data = intent?.getStringExtra("data")
                val responseText = intent?.getStringExtra("responseText")
                val isRecording = intent?.getStringExtra("isRecording")

                if (isReceived == true) {
                    if (isNeverClova == true) {
                        txtSent.text ="Sent: $data"
                        txtReceived.text = "Received: $responseText"
                    } else {
                        txtSent.text ="Sent: $data"
                        txtReceived.text = "Received: $responseText"
                    }
                } else {
                    txtSent.text ="Sent: $data"
                    txtReceived.text = "Received: Not received yet..."
                }
            }
        }
    }

    companion object {
        const val RECORDING_STATE = "RECORDING_STATE"
        const val ASSISTANT_RESPONSE_STATE = "ASSISTANT_RESPONSE_STATE"
    }
}
