package com.example.googlesttdemo

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.AssetFileDescriptor
import android.content.res.AssetManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import com.example.googlesttdemo.spectoimage.RecordWavMaster
import com.example.googlesttdemo.wavreader.FileFormatNotSupportedException
import com.example.googlesttdemo.wavreader.WavFile
import com.example.googlesttdemo.wavreader.WavFileException
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import com.example.googlesttdemo.spectoimage.GoogleSTT


class MainActivity : AppCompatActivity() {
    private lateinit var btnRecord: Button
    private lateinit var btnPlay: Button
    private lateinit var btnInference: Button
    private lateinit var fullAudioPath: File
    private lateinit var pathToRecords: File
    private lateinit var fileName: File
    private var playingAvailable: Boolean = false
    private lateinit var txtResult: TextView


    private val requestPermission: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()){
                permissions ->
            permissions.entries.forEach{
                val permissionName = it.key
                val isGranted = it.value
                if (isGranted){
                    Toast.makeText(this@MainActivity, "Permission is granted",
                        Toast.LENGTH_SHORT).show()
                }else{
                    if (permissionName== Manifest.permission.READ_EXTERNAL_STORAGE){
                        Toast.makeText(this@MainActivity, "Storage reading denied",
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val googlestt = GoogleSTT()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { // get permission
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO),200);
            requestStoragePermission()
        }
        pathToRecords = File(externalCacheDir?.absoluteFile, "AudioRecord" )
        if (!pathToRecords.exists()){
            pathToRecords.mkdir()
        }
        val audioRecorder = RecordWavMaster(this, pathToRecords.toString())
        var recording = true
        btnRecord = findViewById(R.id.btnRecord)
        btnRecord.text = "Start"
        btnRecord.setOnClickListener{

            if (recording){
                audioRecorder.recordWavStart()
                btnRecord.text = "Recording"
                recording = false


            }else{
                audioRecorder.recordWavStop()
                btnRecord.text = "Start"
                recording = true
                fileName = audioRecorder.audioName
                playingAvailable = true
            }


        }
        btnPlay = findViewById(R.id.btnPlay)
        btnPlay.text = "Play"
        btnPlay.setOnClickListener{
            if (playingAvailable){
                audioRecorder.startPlaying(this, 1, fileName)

            } else{
                Toast.makeText(this, "No record found", Toast.LENGTH_SHORT).show()
            }
//            try {
//                if (playingAvailable){
//                    audioRecorder.startPlaying(this, 1, fileName)
//                }
//            } catch (e: Exception){
//                Toast.makeText(this, "Please record audio", Toast.LENGTH_SHORT).show()
//            }
            btnPlay.text = "Play"


        }
        txtResult = findViewById(R.id.txtResult)
        btnInference = findViewById(R.id.btnPredict)
        btnInference.setOnClickListener {
//            val audioData = notNormAudio/32768f
//            for (xx in audioData?.get(0)?.indices!!){
//                Log.d("wavfile", audioData[0][xx].toString())
////                normAudioData?.set(xx, audioData[0][xx]/32767f)
//            }


            if (fileName.path.isNotEmpty()){

                val audioData = readMagnitudeValuesFromFile(fileName.path,-1, -1, 0 )
//                val normAudioData = audioData?.size?.let { it1 -> FloatArray(it1) }
                val ttt = googlestt.getSTTText(assets,fileName.path)
                txtResult.setText(ttt)




            }else{
                Toast.makeText(this, "Record audio", Toast.LENGTH_SHORT).show()
            }

        }

    }
    @Throws(IOException::class, WavFileException::class, FileFormatNotSupportedException::class)
    fun readMagnitudeValuesFromFile(
        path: String,
        sampleRate: Int,
        readDurationInSeconds: Int,
        offsetDuration: Int
    ): Array<FloatArray>? {
        if (!path.endsWith(".wav")) {
            throw FileFormatNotSupportedException(
                "File format not supported. jLibrosa currently supports audio processing of only .wav files"
            )
        }
        val sourceFile = File(path)
        var wavFile: WavFile? = null
        wavFile = WavFile.openWavFile(sourceFile)
        var mNumFrames = wavFile.numFrames.toInt()
        var mSampleRate = wavFile.sampleRate.toInt()
        val mChannels = wavFile.numChannels
        val totalNoOfFrames = mNumFrames
        val frameOffset = offsetDuration * mSampleRate
        var tobeReadFrames = readDurationInSeconds * mSampleRate
        if (tobeReadFrames > totalNoOfFrames - frameOffset) {
            tobeReadFrames = totalNoOfFrames - frameOffset
        }
        if (readDurationInSeconds != -1) {
            mNumFrames = tobeReadFrames
            wavFile.numFrames = mNumFrames.toLong()
        }
        if (sampleRate != -1) {
            mSampleRate = sampleRate
        }

        // Read the magnitude values across both the channels and save them as part of
        // multi-dimensional array
        val buffer = Array(mChannels) {
            FloatArray(
                mNumFrames
            )
        }
        var readFrameCount: Long = 0
        // for (int i = 0; i < loopCounter; i++) {
        readFrameCount = wavFile.readFrames(buffer, mNumFrames, frameOffset)
        // }
        wavFile?.close()
        return buffer
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) { // handle user response to permission request
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 200 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permission to record audio granted", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Permission to record audio denied", Toast.LENGTH_LONG).show()

        }
    }

    private fun showRationalDialog( title: String, message: String){
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton("Cancel"){ dialog, _->
                dialog.dismiss()
            }
        builder.create().show()
    }
    private fun requestStoragePermission(){
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
        ){
            showRationalDialog("Storage Permission",
                "Need permission for storage")
        }else{
            requestPermission.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
    }





}