package com.example.buddycareassistant.gpt3settings

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.*
import com.example.buddycareassistant.R
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
    private lateinit var btnResetDefault: Button
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
        btnResetDefault = findViewById(R.id.btnResetDefault)
        txtModel = findViewById(R.id.txtModel)
        txtMaxTokens = findViewById(R.id.txtMaxTokens)
        txtTemperature = findViewById(R.id.txtTemperature)
        txtTopP = findViewById(R.id.txtTopP)
        txtN = findViewById(R.id.txtN)
        txtStream = findViewById(R.id.txtStream)
        txtLogProbs = findViewById(R.id.txtLogProbs)
        txtPresencePenalty = findViewById(R.id.txtPresencePenalty)
        txtFrequencyPenalty = findViewById(R.id.txtFrequencyPenalty)

        val model = mPreferences.getString("model", "gpt-3.5-turbo")
        val max_tokens = mPreferences.getString("max_tokens", "1000")
        val temperature = mPreferences.getString("temperature", "0")
        val top_p = mPreferences.getString("top_p", "1")
        val n = mPreferences.getString("n", "1")
        val stream = mPreferences.getString("stream", "false")
        val logprobs = mPreferences.getString("logprobs", "null")
        val frequency_penalty = mPreferences.getString("frequency_penalty", "0")
        val presence_penalty = mPreferences.getString("presence_penalty", "0.6")

        /* Default-settings
        model = 'gpt-4
        max_tokens = 1000
        temperature = 0
        top_p = 1
        n = 1
        stream = false
        logprobs = null
        frequency_penalty=0
        presence_penalty=0.6
        "gpt-4", "gpt-4-0314", "gpt-4-32k", "gpt-4-32k-0314",
        */

        val gpt3ModelOptions = arrayOf("gpt-3.5-turbo", "gpt-3.5-turbo-0301")
        val streamOptions = arrayOf(false, true)
        val logprobsOptions = arrayOf("null", "1", "2", "3", "4", "5")
        val adapterGPT3Models = ArrayAdapter(this, android.R.layout.simple_spinner_item, gpt3ModelOptions)
        val adapterStream = ArrayAdapter(this, android.R.layout.simple_spinner_item, streamOptions)
        val adapterLogprobs = ArrayAdapter(this, android.R.layout.simple_spinner_item, logprobsOptions)

        val settedModelIndex = gpt3ModelOptions.indexOf(model)
        val settedStreamIndex = streamOptions.indexOf(stream.toBoolean())
        val settedLogProbsIndex = logprobsOptions.indexOf(logprobs)

        spModel.adapter = adapterGPT3Models
        spModel.setSelection(settedModelIndex)
        spStream.adapter = adapterStream
        spStream.setSelection(settedStreamIndex)

        spLogProbs.adapter = adapterLogprobs
        spLogProbs.setSelection(settedLogProbsIndex)

        skTemperature.max = 100
        if (temperature != null) {
            skTemperature.progress = (temperature.toFloat()*100).toInt()
        }else{
            skTemperature.progress = 0
        }
        txtTemperature.text = "temperature: ${temperature?.toFloat()}"


        var progressValueTemperature = temperature?.toFloat()

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
        if (top_p != null) {
            skTopP.progress = (top_p.toFloat()*100).toInt()
        }else{
            skTopP.progress = 0
        }

        var progressValueTopP = top_p?.toFloat()
        txtTopP.text = "top_p: ${top_p?.toFloat()}"

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

        if (presence_penalty != null){
            skPresencePenalty.progress = ((presence_penalty.toFloat().plus(2.0)).times(100)).toInt()
        } else{
            skPresencePenalty.progress = 0
        }


        var skPresencePenaltyValue = presence_penalty?.toFloat()
        txtPresencePenalty.text = "presence_penalty: ${presence_penalty?.toFloat()}"

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

        if (frequency_penalty != null){
            skFrequencyPenalty.progress = ((frequency_penalty.toFloat().plus(2.0)).times(100)).toInt()
        } else{
            skFrequencyPenalty.progress = 0
        }

        var skFrequencyPenaltyValue = frequency_penalty?.toFloat()

        txtFrequencyPenalty.text = "frequency_penalty: ${frequency_penalty?.toFloat()}"

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

        etN.setText(n)
        etMaxTokens.setText(max_tokens)


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

            Toast.makeText(this@GPT3SettingsActivity, "Settings updated", Toast.LENGTH_SHORT).show()

//            Log.d("gpt3Set spModelSelectedItem", spModelSelectedItem)
//            Log.d("gpt3Set spStreamSelectedItem", spStreamSelectedItem)
//            Log.d("gpt3Set spLogProbsSelectedItem", spLogProbsSelectedItem)
//            Log.d("gpt3Set etMaxTokensInpText", etMaxTokensInpText)
//            Log.d("gpt3Set progressValueTemperature", progressValueTemperature.toString())
//            Log.d("gpt3Set progressValueTopP", progressValueTopP.toString())
//            Log.d("gpt3Set skPresencePenaltyValue", skPresencePenaltyValue.toString())
//            Log.d("gpt3Set skFrequencyPenaltyValue", skFrequencyPenaltyValue.toString())
//            Log.d("gpt3Set etNInpText", etNInpText)


        }
        btnResetDefault.setOnClickListener {
            val gpt3SettingsPreferences = mPreferences.edit()
            gpt3SettingsPreferences.putString("model", "gpt-3.5-turbo")
            gpt3SettingsPreferences.putString("max_tokens", "1000")
            gpt3SettingsPreferences.putString("temperature", "0")
            gpt3SettingsPreferences.putString("top_p", "1")
            gpt3SettingsPreferences.putString("n", "1")
            gpt3SettingsPreferences.putString("stream", "false")
            gpt3SettingsPreferences.putString("logprobs", "null")
            gpt3SettingsPreferences.putString("frequency_penalty", "0")
            gpt3SettingsPreferences.putString("presence_penalty", "0.6")
            gpt3SettingsPreferences.apply()

            val model = mPreferences.getString("model", "gpt-3.5-turbo")
            val max_tokens = mPreferences.getString("max_tokens", "1000")
            val temperature = mPreferences.getString("temperature", "0")
            val top_p = mPreferences.getString("top_p", "1")
            val n = mPreferences.getString("n", "1")
            val stream = mPreferences.getString("stream", "false")
            val logprobs = mPreferences.getString("logprobs", "null")
            val frequency_penalty = mPreferences.getString("frequency_penalty", "0")
            val presence_penalty = mPreferences.getString("presence_penalty", "0.6")


            txtModel.text = "model: $model"
            txtMaxTokens.text = "max_tokens: $max_tokens"
            txtN.text = "n: $n"
            txtStream.text = "stream: $stream"
            txtLogProbs.text = "logprobs: $logprobs"
            txtPresencePenalty.text = "presence_penalty: ${presence_penalty?.toFloat()}"
            txtTemperature.text = "temperature: ${temperature?.toFloat()}"
            txtFrequencyPenalty.text = "frequency_penalty: ${frequency_penalty?.toFloat()}"
            txtTopP.text = "top_p: ${top_p?.toFloat()}"

            etN.setText(n)
            etMaxTokens.setText(max_tokens)

            if (frequency_penalty != null){
                skFrequencyPenalty.progress = ((frequency_penalty.toFloat().plus(2.0)).times(100)).toInt()
            } else{
                skFrequencyPenalty.progress = 0
            }

            if (presence_penalty != null){
                skPresencePenalty.progress = ((presence_penalty.toFloat().plus(2.0)).times(100)).toInt()
            } else{
                skPresencePenalty.progress = 0
            }

            if (top_p != null) {
                skTopP.progress = (top_p.toFloat()*100).toInt()
            }else{
                skTopP.progress = 0
            }

            if (temperature != null) {
                skTemperature.progress = (temperature.toFloat()*100).toInt()
            }else{
                skTemperature.progress = 0
            }

            val settedModelIndex = gpt3ModelOptions.indexOf(model)
            val settedStreamIndex = streamOptions.indexOf(stream.toBoolean())
            val settedLogProbsIndex = logprobsOptions.indexOf(logprobs)

            spStream.setSelection(settedStreamIndex)
            spModel.setSelection(settedModelIndex)
            spLogProbs.setSelection(settedLogProbsIndex)

            Toast.makeText(this@GPT3SettingsActivity, "Default settings restored", Toast.LENGTH_SHORT).show()


        }


    }

}