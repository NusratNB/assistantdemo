package com.example.buddycareassistant.bluetoothcontrol

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Bundle
import android.text.format.Time
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.buddycareassistant.R
import com.example.buddycareassistant.recordaudio.AudioRecorder
import java.io.File
import java.io.IOException
import java.util.*

class BluetoothControlActivity : AppCompatActivity() {
    private lateinit var btnTurnOnBluetooth: Button
    private lateinit var btnTurnOfBluetooth: Button
    private lateinit var btnConnectBluetooth: Button
    private lateinit var btnDisconnectBluetooth: Button
    private lateinit var btnSCOStartRecord: Button
    private lateinit var btnSCOStopRecord: Button
    private lateinit var btnPlayAudio: Button
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var pathToRecords: File
    private var isRecording = false

    private var audioManager: AudioManager? = null
    private lateinit var outputFile: File
    val time = Time()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth_control)

        pathToRecords = File(externalCacheDir?.absoluteFile, "AudioRecord" )
        if (!pathToRecords.exists()){
            pathToRecords.mkdir()
        }

        audioRecorder = AudioRecorder(this)
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        btnConnectBluetooth = findViewById(R.id.btnConnectBluetooth)
        btnDisconnectBluetooth = findViewById(R.id.btnDisconnectBluetooth)
        btnTurnOnBluetooth = findViewById(R.id.btnTurnOnBluetooth)
        btnSCOStartRecord = findViewById(R.id.btnSCOStartRecord)
        btnSCOStopRecord = findViewById(R.id.btnSCOStopRecord)
        btnPlayAudio = findViewById(R.id.btnPlayAudio)



        btnTurnOnBluetooth.setOnClickListener {
            if (!bluetoothAdapter.isEnabled) {
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH)
                bluetoothAdapter.enable()
            } else
                Toast.makeText(this, "Bluetooth On Failed", Toast.LENGTH_SHORT).show()
        }
        btnTurnOfBluetooth = findViewById(R.id.btnTurnOfBluetooth)
        btnTurnOfBluetooth.setOnClickListener {
            if (bluetoothAdapter.isEnabled) {
                bluetoothAdapter.disable()
            } else
                Toast.makeText(this, "Bluetooth Of Failed", Toast.LENGTH_SHORT).show()
        }

        btnConnectBluetooth.setOnClickListener {


// Display the list of available Bluetooth devices in a RecyclerView or ListView

            if (bluetoothAdapter.scanMode != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                val discoverableIntent =
                    Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                        putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                    }
                startActivity(discoverableIntent)
            }
            val deviceName = "QS39(ID-6370)" // Replace with the name of the device you want to connect to
            val devices = bluetoothAdapter.bondedDevices
            var selectedDevice: BluetoothDevice? = null
            for (device in devices) {
//                device.uuids
                Log.d("devices ${device.name}", "MAC: ${device.address} ")
                if (device.name == deviceName) {
                    selectedDevice = device
                    if (device.uuids.isNotEmpty()){
                        for (uu in selectedDevice.uuids){
                            Log.d("devices $selectedDevice", uu.toString())
                        }

                    }
                    else{
                        Log.d("devices", "UUID Error")
                    }
                    break
                }
            }
            val uuid = UUID.fromString(selectedDevice?.uuids?.get(0).toString())
            val socket: BluetoothSocket? = try {
                selectedDevice?.createRfcommSocketToServiceRecord(uuid)
            } catch (e: IOException) {
                null
            }
            socket?.connect()

            val isConnected = BluetoothAdapter.checkBluetoothAddress(selectedDevice?.address) && selectedDevice?.type != BluetoothDevice.DEVICE_TYPE_UNKNOWN
            if (isConnected) {
                Log.d("devices", "Name: ${selectedDevice?.name}, Address: ${selectedDevice?.address}")
            }

        }
        btnDisconnectBluetooth.setOnClickListener {


        }

    }

    fun enableVoiceRecord() {
        val intentFilter = IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        this.registerReceiver( mBluetoothScoReceiver, intentFilter)
        audioManager = this.applicationContext.getSystemService(AUDIO_SERVICE) as AudioManager?
        // Start Bluetooth SCO.
        // Start Bluetooth SCO.
        audioManager!!.mode = AudioManager.MODE_NORMAL
        audioManager!!.isBluetoothScoOn = true
        audioManager!!.startBluetoothSco()
        // Stop Speaker.
        // Stop Speaker.
        audioManager!!.isSpeakerphoneOn = false

    }

    fun disableVoiceRecord() {
        if (isRecording) {
            btnSCOStopRecord.isEnabled = false
            btnSCOStartRecord.isEnabled = true
            // Stop Media recorder
//            speechRecognizer.stopListening()
            audioRecorder.stop()
        }
        try {
            this.unregisterReceiver(mBluetoothScoReceiver)
        } catch (e: Exception) {
        }
        // Stop Bluetooth SCO.
        audioManager!!.stopBluetoothSco()
        audioManager!!.mode = AudioManager.MODE_NORMAL
        audioManager!!.isBluetoothScoOn = false
        // Start Speaker.
        audioManager!!.isSpeakerphoneOn = true
    }

    private val mBluetoothScoReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
            println("ANDROID Audio SCO state: $state")
            if (AudioManager.SCO_AUDIO_STATE_CONNECTED == state) {
                btnSCOStartRecord.isEnabled = false
                btnSCOStopRecord.isEnabled = true

                isRecording = true
                time.setToNow()
                val audioName = time.format("%Y%m%d%H%M%S") + ".pcm"
                outputFile = File(pathToRecords, audioName)
                audioRecorder.start(outputFile)

//                speechRecognizer.startListening(speechRecognizerIntent)
            } else if (AudioManager.SCO_AUDIO_STATE_DISCONNECTED == state) {
                if (isRecording) {
                    disableVoiceRecord()
                    isRecording = false
                }
            }

        }
    }


}