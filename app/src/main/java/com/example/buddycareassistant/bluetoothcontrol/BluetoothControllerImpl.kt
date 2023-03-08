package com.example.buddycareassistant.bluetoothcontrol

import android.content.Context
import android.util.Log

class BluetoothControllerImpl(context: Context?) :

    CustomBluetoothController(context) {
    private val TAG = "BluetoothController"

    override fun onHeadsetDisconnected() {
        Log.d(TAG, "Bluetooth headset disconnected")
    }

    override fun onHeadsetConnected() {
        Log.d(TAG, "Bluetooth headset connected")

    }

    override fun onScoAudioDisconnected() {
        Log.d(TAG, "Bluetooth sco audio finished")

    }

    override fun onScoAudioConnected() {
        Log.d(TAG, "Bluetooth sco audio started")
    }
}