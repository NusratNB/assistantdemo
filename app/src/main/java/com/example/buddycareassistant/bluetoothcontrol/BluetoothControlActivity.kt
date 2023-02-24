package com.example.buddycareassistant.bluetoothcontrol

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.buddycareassistant.R
import java.io.IOException
import java.util.*

class BluetoothControlActivity : AppCompatActivity() {
    private lateinit var btnTurnOnBluetooth: Button
    private lateinit var btnTurnOfBluetooth: Button
    private lateinit var btnConnectBluetooth: Button
    private lateinit var btnDisconnectBluetooth: Button


    private lateinit var bluetoothAdapter: BluetoothAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth_control)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        btnConnectBluetooth = findViewById(R.id.btnConnectBluetooth)
        btnDisconnectBluetooth = findViewById(R.id.btnDisconnectBluetooth)
        btnTurnOnBluetooth = findViewById(R.id.btnTurnOnBluetooth)
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
}