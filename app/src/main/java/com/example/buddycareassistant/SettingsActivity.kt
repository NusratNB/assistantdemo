package com.example.buddycareassistant

import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import java.util.prefs.Preferences

class SettingsActivity: AppCompatActivity() {
    private lateinit var spMemoryQuality: Spinner
    private lateinit var spLanguage: Spinner
    private lateinit var spConversationalStyle: Spinner
    private lateinit var spGender: Spinner
    private lateinit var btnSave: Button

    private val memoryQualities = arrayOf("Low", "Medium", "High")
    private val languages = arrayOf("Korean", "English")
    private val conversationalStyles = arrayOf("More Creative", "More Balanced", "More Precise")
    private val genders = arrayOf("Male", "Female")

    private val pref by lazy { getSharedPreferences("assistant", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        spMemoryQuality = findViewById(R.id.spMemoryQuality)
        spLanguage = findViewById(R.id.spLanguage)
        spConversationalStyle = findViewById(R.id.spConversationalStyle)
        spGender = findViewById(R.id.spGender)
        btnSave = findViewById(R.id.btnSave)

        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        spMemoryQuality.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
            memoryQualities)
        spLanguage.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
            languages)
        spConversationalStyle.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
            conversationalStyles)
        spGender.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
            genders)

        spMemoryQuality.setSelection(pref.getInt("memory_quality_position", 0))
        spLanguage.setSelection(pref.getInt("language_position", 0))
        spConversationalStyle.setSelection(pref.getInt("conversational_style", 0))
        spGender.setSelection(pref.getInt("gender_position", 0))

        btnSave.setOnClickListener {
            val memoryQualityPosition = spMemoryQuality.selectedItemPosition
            val languagePosition = spMemoryQuality.selectedItemPosition
            val conversationalStylePosition = spMemoryQuality.selectedItemPosition
            val genderPosition = spMemoryQuality.selectedItemPosition
            pref.edit()
                .putInt("memory_quality_position", memoryQualityPosition)
                .putInt("language_position", languagePosition)
                .putInt("conversational_style", conversationalStylePosition)
                .putInt("gender_position", genderPosition)
                .apply()

            finish()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
        }
        return super.onOptionsItemSelected(item)
    }
}