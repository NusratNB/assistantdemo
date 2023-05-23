package com.example.buddycareassistant

import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import com.example.buddycareassistant.utils.LogUtil

class SettingsActivity: AppCompatActivity() {
    private lateinit var spMemoryQuality: Spinner
    private lateinit var spLanguage: Spinner
    private lateinit var spConversationalStyle: Spinner
    private lateinit var spGender: Spinner
    private lateinit var btnSave: Button
    private lateinit var logger: LogUtil
    private val TAG ="BuddyCareAssistant: " + this::class.java.simpleName

    private val memoryQualities = arrayOf("Low", "Medium", "High")
    private val languages = arrayOf("Korean", "English")
    private val conversationalStyles = arrayOf("More Creative", "More Balanced", "More Precise")
    private val genders = arrayOf("Male", "Female")

    private val pref by lazy { getSharedPreferences("assistant_demo", MODE_PRIVATE) }

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
        logger = LogUtil

        spMemoryQuality.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
            memoryQualities)
        spLanguage.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
            languages)
        spConversationalStyle.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
            conversationalStyles)
        spGender.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
            genders)

        spMemoryQuality.setSelection(memoryQualities.indexOf(pref.getString("memory_quality", "Low")))
        spLanguage.setSelection(languages.indexOf(pref.getString("language", "Korean")))
        spConversationalStyle.setSelection(conversationalStyles.indexOf(pref.getString("conversational", "More Creative")))
        spGender.setSelection(genders.indexOf(pref.getString("gender", "Female")))

        btnSave.setOnClickListener {
            pref.edit()
                .putString("memory_quality", spMemoryQuality.selectedItem.toString())
                .putString("language", spLanguage.selectedItem.toString())
                .putString("conversational", spConversationalStyle.selectedItem.toString())
                .putString("gender", spGender.selectedItem.toString())
                .apply()

            finish()

            logger.i(this, TAG, "Saved settings: memory_quality = ${spMemoryQuality.selectedItem} language = ${spLanguage.selectedItem}" +
                    " conversational = ${spConversationalStyle.selectedItem} gender = ${spGender.selectedItem}")
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
        }
        return super.onOptionsItemSelected(item)
    }
}