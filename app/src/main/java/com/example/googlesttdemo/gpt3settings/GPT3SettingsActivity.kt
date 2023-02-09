package com.example.googlesttdemo.gpt3settings

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.Spinner
import com.example.googlesttdemo.R
import com.google.android.material.textfield.TextInputEditText

class GPT3SettingsActivity : AppCompatActivity() {

    private lateinit var spModel: Spinner
    private lateinit var etMaxTokens: TextInputEditText
    private lateinit var skTemperature: SeekBar
    private lateinit var etTopP: TextInputEditText
    private lateinit var etN: TextInputEditText
    private lateinit var spStream: Spinner
    private lateinit var spLogProbs: Spinner
    private lateinit var skPresencePenalty: SeekBar
    private lateinit var skFrequencyPenalty: SeekBar
    private lateinit var btnSaveSettings: Button


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gpt3_settings)

        spModel = findViewById(R.id.spModel)
        etMaxTokens = findViewById(R.id.etMaxTokens)
        skTemperature = findViewById(R.id.skTemperature)
        etTopP = findViewById(R.id.etTopP)
        etN = findViewById(R.id.etN)
        spStream = findViewById(R.id.spStream)
        spLogProbs = findViewById(R.id.spLogProbs)
        skPresencePenalty = findViewById(R.id.skPresencePenalty)
        skFrequencyPenalty = findViewById(R.id.skFrequencyPenalty)
        btnSaveSettings = findViewById(R.id.btnSettings)
    }
}