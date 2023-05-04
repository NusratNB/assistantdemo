package com.example.buddycareassistant

import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity: AppCompatActivity() {
    private lateinit var spMemoryQuality: Spinner
    private lateinit var spLanguage: Spinner
    private lateinit var spConversationalStyle: Spinner
    private lateinit var spGender: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        spMemoryQuality = findViewById(R.id.spMemoryQuality)
        spLanguage = findViewById(R.id.spLanguage)
        spConversationalStyle = findViewById(R.id.spConversationalStyle)
        spGender = findViewById(R.id.spGender)

        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        spMemoryQuality.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
            arrayOf("Low", "Medium", "High"))
        spLanguage.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
            arrayOf("Korean", "English"))
        spConversationalStyle.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
            arrayOf("More Creative", "More Balanced", "More Precise"))
        spGender.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
            arrayOf("Male", "Female"))
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
        }
        return super.onOptionsItemSelected(item)
    }
}