package com.example.buddycareassistant

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.buddycareassistant.googlespeechservices.GoogleServices
import com.example.buddycareassistant.gpt3settings.GPT3SettingsActivity
import com.example.buddycareassistant.service.AssistantService
import com.example.buddycareassistant.storemessages.MessageStorage
import com.example.buddycareassistant.utils.LogUtil
import java.io.File


class MainActivity : AppCompatActivity() {
    private lateinit var pathToSavingMessagesMainActivity: File
    private lateinit var googlestt: GoogleServices
    private lateinit var handler: Handler
    private lateinit var messageStorage: MessageStorage
    private val REQUEST_ENABLE_BT = 1
    private lateinit var logger: LogUtil
    private lateinit var tvLanguage: TextView
    private lateinit var tvRecordingStatus: TextView
    private lateinit var rvAssistant: RecyclerView
    private val assistantAdapter by lazy { AssistantChatAdapter() }
    private lateinit var ivSettings: ImageView
    private lateinit var ivClear: ImageView
    private lateinit var ivStop: ImageView
    private lateinit var startChat: FrameLayout

    private val mPreferences by lazy {
        getSharedPreferences("assistant_demo", MODE_PRIVATE)
    }
    private val mPreferenceGPTSettings by lazy {
        getSharedPreferences("buddycare_assistant", MODE_PRIVATE)
    }
    private val TAG ="BuddyCareAssistant: " + this::class.java.simpleName
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
        setContentView(R.layout.activity_main2)
        startService(Intent(this, AssistantService::class.java))
        bindService(Intent(this, AssistantService::class.java), serviceConnection, BIND_AUTO_CREATE)
        googlestt = GoogleServices(this, assets)
        tvLanguage = findViewById(R.id.tvLanguage)
        ivSettings = findViewById(R.id.ivSettings)
        ivClear = findViewById(R.id.ivClear)
        ivStop = findViewById(R.id.ivStop)
        tvRecordingStatus = findViewById(R.id.tvRecordingStatus)
        startChat = findViewById(R.id.startChat)
        rvAssistant = findViewById(R.id.rvChat)
        rvAssistant.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, true)
        rvAssistant.adapter = assistantAdapter

        ivStop.setOnClickListener {
            foregroundBleService?.stopPlayer()
        }
        startChat.setOnClickListener {
            onNewIntent(Intent("android.intent.action.VOICE_COMMAND"))
        }
        ivSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }


        ivClear.setOnClickListener {
            messageStorage.clearMessages()
            assistantAdapter.items.clear()
            assistantAdapter.notifyDataSetChanged()
            val systemRoleContent = mPreferenceGPTSettings.getString("systemRoleContent", "You are a helpful friend.").toString()
            val gptPromptFileName = "GPTPrompt.txt"
            pathToSavingMessagesMainActivity = File(this.externalCacheDir?.absolutePath, "Messages")
            if (!pathToSavingMessagesMainActivity.exists()){
                pathToSavingMessagesMainActivity.mkdir()
            }
            val tempText = """
            {
              "model": "gpt-3.5-turbo",
              "messages": [
                        {"role": "system", "content": "$systemRoleContent"}
                    ],
               "max_tokens": 1000,
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
            logger.i(this, TAG, "New chat room has been created")

            Toast.makeText(this, "New Chat room has been created", Toast.LENGTH_LONG).show()
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { // get permission
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.MODIFY_AUDIO_SETTINGS,
                Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_COARSE_LOCATION,
              Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.INTERNET, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.MODIFY_PHONE_STATE),200);
        }
        logger = LogUtil
        messageStorage = MessageStorage(this)
        assistantAdapter.items.addAll(messageStorage.retrieveUserAssistantMessages().reversed())
        handler = Handler()
        onNewIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(uiReceiver, IntentFilter().apply {
            addAction(RECORDING_STATE)
            addAction(ASSISTANT_RESPONSE_STATE)
        })
        tvLanguage.text = "Language: ${mPreferences.getString("language", "Korean")}"

        if(messageStorage.retrieveUserAssistantMessages().isEmpty()) {
            assistantAdapter.items.clear()
            assistantAdapter.notifyDataSetChanged()
        }
    }

    override fun onDestroy() {
        unregisterReceiver(uiReceiver)
        super.onDestroy()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

//        Log.d("scoTest", intent?.action.toString())
        logger.d(this, TAG, "Received state from watch: ${intent?.action}")

        if(intent?.action == "android.intent.action.VOICE_COMMAND") {
            val intent = Intent(this, AssistantService::class.java)
            intent.action = AssistantService.START_VOICE_COMMAND_ACTION
            startService(intent)
        }
    }

    fun turnOnScreen() {
        // turn on screen
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
//            Log.d("ssss", "intent " + intent?.action);
            if (intent?.action.equals(RECORDING_STATE)) {
                val isRecording = intent?.getBooleanExtra("isRecording", false) ?: false
                val isRecordingEnabled = intent?.getBooleanExtra("isRecordingEnabled", false)?:false
                if (isRecording) {
                    tvRecordingStatus.text = "Recording..."
                    startChat.setBackgroundResource(R.drawable.bg_mic_recording)
                } else {
                    if (isRecordingEnabled){
                        tvRecordingStatus.text = ""
                        startChat.setBackgroundResource(R.drawable.bg_mic)
                        val requestItem = Pair(false, "....")
                        assistantAdapter.items.add(0, requestItem)
                        assistantAdapter.notifyDataSetChanged()
                    } else {
                        tvRecordingStatus.text = ""
                        startChat.setBackgroundResource(R.drawable.bg_mic)
                        if (assistantAdapter.items.isNotEmpty() && assistantAdapter.items.first().second.equals("....")) {
                            assistantAdapter.items.removeFirst()
                            assistantAdapter.notifyDataSetChanged()
                        }
                    }
                }
            } else if (intent?.action.equals(ASSISTANT_RESPONSE_STATE)) {
                val isReceived = intent?.getBooleanExtra("isReceived", false)
                val isNeverClova = intent?.getBooleanExtra("isNeverClova", false)
                val data = intent?.getStringExtra("data")
                val responseText = intent?.getStringExtra("responseText")
                val isRecording = intent?.getStringExtra("isRecording")

                if (isReceived == true) {
                    if (assistantAdapter.items.isNotEmpty()){
                        assistantAdapter.items.removeFirst()
                    }
                    val requestItem = Pair(false, data!!)
                    val responseItem = Pair(true, responseText!!)
                    assistantAdapter.items.add(0, requestItem)
                    assistantAdapter.items.add(0, responseItem)
                    assistantAdapter.notifyDataSetChanged()
                } else {
//                    txtSent.text ="Sent: $data"
//                    txtReceived.text = "Received: Not received yet..."
                }
            }
        }
    }

    companion object {
        const val RECORDING_STATE = "RECORDING_STATE"
        const val ASSISTANT_RESPONSE_STATE = "ASSISTANT_RESPONSE_STATE"
    }
}
