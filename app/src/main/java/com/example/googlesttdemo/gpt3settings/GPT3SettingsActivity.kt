package com.example.googlesttdemo.gpt3settings

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.*
import com.example.googlesttdemo.R
import com.google.android.material.textfield.TextInputEditText

class GPT3SettingsActivity : AppCompatActivity() {

    private lateinit var spModel: Spinner
    private lateinit var etMaxTokens: TextInputEditText
    private lateinit var skTemperature: SeekBar
    private lateinit var skTopP: SeekBar
    private lateinit var etN: TextInputEditText
    private lateinit var spStream: Spinner
    private lateinit var spLogProbs: Spinner
    private lateinit var skPresencePenalty: SeekBar
    private lateinit var skFrequencyPenalty: SeekBar
    private lateinit var btnSaveSettings: Button
    private lateinit var txtModel: TextView
    private lateinit var txtMaxTokens: TextView
    private lateinit var txtTemperature: TextView
    private lateinit var txtTopP: TextView
    private lateinit var txtN: TextView
    private lateinit var txtStream: TextView
    private lateinit var txtLogProbs: TextView
    private lateinit var txtPresencePenalty: TextView
    private lateinit var txtFrequencyPenalty: TextView
    private val mPreferences by lazy {
        getSharedPreferences("assistant_demo", MODE_PRIVATE)
    }


    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gpt3_settings)

        spModel = findViewById(R.id.spModel)
        etMaxTokens = findViewById(R.id.etMaxTokens)
        skTemperature = findViewById(R.id.skTemperature)
        skTopP = findViewById(R.id.skTopP)
        etN = findViewById(R.id.etN)
        spStream = findViewById(R.id.spStream)
        spLogProbs = findViewById(R.id.spLogProbs)
        skPresencePenalty = findViewById(R.id.skPresencePenalty)
        skFrequencyPenalty = findViewById(R.id.skFrequencyPenalty)
        btnSaveSettings = findViewById(R.id.btnSaveSettings)
        txtModel = findViewById(R.id.txtModel)
        txtMaxTokens = findViewById(R.id.txtMaxTokens)
        txtTemperature = findViewById(R.id.txtTemperature)
        txtTopP = findViewById(R.id.txtTopP)
        txtN = findViewById(R.id.txtN)
        txtStream = findViewById(R.id.txtStream)
        txtLogProbs = findViewById(R.id.txtLogProbs)
        txtPresencePenalty = findViewById(R.id.txtPresencePenalty)
        txtFrequencyPenalty = findViewById(R.id.txtFrequencyPenalty)




        val gpt3ModelOptions = arrayOf("text-davinci-003", "text-curie-001", "text-babbage-001", "text-ada-001")
        val streamOptions = arrayOf(false, true)
        val logprobsOptions = arrayOf("null", 1, 2, 3, 4, 5)
        val adapterGPT3Models = ArrayAdapter(this, android.R.layout.simple_spinner_item, gpt3ModelOptions)
        val adapterStream = ArrayAdapter(this, android.R.layout.simple_spinner_item, streamOptions)
        val adapterLogprobs = ArrayAdapter(this, android.R.layout.simple_spinner_item, logprobsOptions)

        spModel.adapter = adapterGPT3Models
        spStream.adapter = adapterStream
        spLogProbs.adapter = adapterLogprobs

        skTemperature.max = 100
        skTemperature.progress = 0

        var progressValueTemperature = 0f

        skTemperature.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                progressValueTemperature = progress.toFloat() / 100f
                txtTemperature.text = "temperature: ${"%.2f".format(progressValueTemperature)}"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Handle the start of a touch gesture here
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Handle the end of a touch gesture here
            }
        })

        skTopP.max = 100
        skTopP.progress = 0

        var progressValueTopP = 0f

        skTopP.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                progressValueTopP = progress.toFloat() / 100f
                txtTopP.text = "top_p: ${"%.2f".format(progressValueTopP)}"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Handle the start of a touch gesture here
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Handle the end of a touch gesture here
            }
        })

        skPresencePenalty.max = 400
        skPresencePenalty.progress = 0

        var skPresencePenaltyValue = 0f

        skPresencePenalty.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(p0: SeekBar?, progress: Int, p2: Boolean) {
                skPresencePenaltyValue = (progress.toFloat() - 200f) / 100f
                txtPresencePenalty.text = "presence_penalty: ${"%.2f".format(skPresencePenaltyValue)}"
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
            }

        })

        skFrequencyPenalty.max = 400
        skFrequencyPenalty.progress = 0

        var skFrequencyPenaltyValue = 0f

        skFrequencyPenalty.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(p0: SeekBar?, progress: Int, p2: Boolean) {
                skFrequencyPenaltyValue = (progress.toFloat() - 200f) / 100f
                txtFrequencyPenalty.text = "frequency_penalty: ${"%.2f".format(skFrequencyPenaltyValue)}"
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
            }
        })

        btnSaveSettings.setOnClickListener {
            val spModelSelectedItem = spModel.selectedItem.toString()
            val spStreamSelectedItem = spStream.selectedItem.toString()
            val spLogProbsSelectedItem = spLogProbs.selectedItem.toString()
            val etMaxTokensInpText = etMaxTokens.text.toString()
            val etNInpText = etN.text.toString()

            txtModel.text = "model: $spModelSelectedItem"
            txtMaxTokens.text = "max_tokens: $etMaxTokensInpText"
            txtTemperature.text = "temperature: $progressValueTemperature"
            txtTopP.text = "top_p: $progressValueTopP"
            txtN.text = "n: $etNInpText"
            txtStream.text = "stream: $spStreamSelectedItem"
            txtLogProbs.text = "logprobs: $spLogProbsSelectedItem"
            txtPresencePenalty.text = "presence_penalty: $skPresencePenaltyValue"
            txtFrequencyPenalty.text = "frequency_penalty: $skFrequencyPenaltyValue"

            mPreferences.edit()
                .putString("gpt3_model", spModelSelectedItem)
                .putString("gpt3_max_tokens", etMaxTokensInpText)
                .apply()
            Log.d("gpt3Set spModelSelectedItem", spModelSelectedItem)
            Log.d("gpt3Set spStreamSelectedItem", spStreamSelectedItem)
            Log.d("gpt3Set spLogProbsSelectedItem", spLogProbsSelectedItem)
            Log.d("gpt3Set etMaxTokensInpText", etMaxTokensInpText)
            Log.d("gpt3Set progressValueTemperature", progressValueTemperature.toString())
            Log.d("gpt3Set progressValueTopP", progressValueTopP.toString())
            Log.d("gpt3Set skPresencePenaltyValue", skPresencePenaltyValue.toString())
            Log.d("gpt3Set skFrequencyPenaltyValue", skFrequencyPenaltyValue.toString())
            Log.d("gpt3Set etNInpText", etNInpText)


        }

        val gpt3Model = mPreferences.getString("gpt3_model", null)
        if (gpt3Model != null) {
            spModel.setSelection(gpt3ModelOptions.indexOf(gpt3Model)) // saqlangan modelni positionini aniqlab uni spinnerga set qilamiz
        }

        val gpt3MaxTokens = mPreferences.getString("gpt3_max_tokens", "")
        etMaxTokens.setText(gpt3MaxTokens)
    }

}