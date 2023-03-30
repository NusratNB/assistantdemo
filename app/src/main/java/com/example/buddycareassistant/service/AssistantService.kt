package com.example.buddycareassistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.text.format.Time
import androidx.core.app.NotificationCompat
import com.example.buddycareassistant.R
import com.example.buddycareassistant.recordaudio.AudioRecorder
import java.io.File

open class AssistantService : Service() {
    private var FOREGROUND_CHANNEL_ID = "ASSISTANT_CHANNEL"
    private lateinit var mNotificationManager: NotificationManager
    private lateinit var mNotificationBuilder: NotificationCompat.Builder
    private lateinit var mPreferences: SharedPreferences
    private lateinit var recorder: AudioRecorder
    private val time = Time()
    private lateinit var outputFile: File
    private lateinit var pathToRecords: File

    override fun onCreate() {
        super.onCreate()
        mNotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        mPreferences = getSharedPreferences("buddycare_assistant", MODE_PRIVATE)
        recorder = AudioRecorder(this)
        pathToRecords = File(externalCacheDir?.absoluteFile, "AudioRecord" )
        if (!pathToRecords.exists()){
            pathToRecords.mkdir()
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID_FOREGROUND_SERVICE, prepareNotification())
        val action = intent.action
        if (action == START_ACTION) {
            time.setToNow()
            val audioName = time.format("%Y%m%d%H%M%S") + ".pcm"
            outputFile = File(pathToRecords, audioName)
            recorder.start(outputFile)
        } else if (action == STOP_ACTION) {
            recorder.stop()
        }
        return START_NOT_STICKY
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
    }

    companion object {
        const val NOTIFICATION_ID_FOREGROUND_SERVICE = 8466503
        const val START_ACTION = "START_ACTION"
        const val STOP_ACTION = "STOP_ACTION"
    }
}