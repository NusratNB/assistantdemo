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

        val model = mPreferences.getString("model", "text-curie-001")
        val max_tokens = mPreferences.getString("max_tokens", "1000")
        val temperature = mPreferences.getString("temperature", "0")
        val top_p = mPreferences.getString("top_p", "1")
        val n = mPreferences.getString("n", "1")
        val stream = mPreferences.getString("stream", "false")
        val logprobs = mPreferences.getString("logprobs", "null")
        val frequency_penalty = mPreferences.getString("frequency_penalty", "0")
        val presence_penalty = mPreferences.getString("presence_penalty", "0.6")

        /* Default-settings
        model = 'text-curie-001
        max_tokens = 1000
        temperature = 0
        top_p = 1
        n = 1
        stream = false
        logprobs = null
        frequency_penalty=0
        presence_penalty=0.6
        */

        val gpt3ModelOptions = arrayOf("text-curie-001", "text-davinci-003",  "text-babbage-001", "text-ada-001")
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
        txtTemperature.text = "temperature: $temperature"


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
        txtTopP.text = "top_p: $top_p"

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
        txtPresencePenalty.text = "presence_penalty: $presence_penalty"

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

        txtFrequencyPenalty.text = "frequency_penalty: $frequency_penalty"

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

        txtModel.text = "model: $model"
        txtMaxTokens.text = "max_tokens: $max_tokens"
        txtN.text = "n: $n"
        txtStream.text = "stream: $stream"
        txtLogProbs.text = "logprobs: $logprobs"

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
                .putString("model", spModelSelectedItem)
                .putString("max_tokens", etMaxTokensInpText)
                .putString("temperature", progressValueTemperature.toString())
                .putString("top_p", progressValueTopP.toString())
                .putString("n", etNInpText)
                .putString("stream", spStreamSelectedItem)
                .putString("logprobs", spLogProbsSelectedItem)
                .putString("frequency_penalty", skFrequencyPenaltyValue.toString())
                .putString("presence_penalty", skPresencePenaltyValue.toString())
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


    }

}