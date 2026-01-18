package com.example.finwise_lab

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class Onboarding02Activity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_onboarding02)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.onbording02)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Set up next button
        findViewById<Button>(R.id.btnNext).setOnClickListener {
            startActivity(Intent(this, Onboarding03Activity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }

        // Set up back button
        findViewById<ImageButton>(R.id.btnBackC).setOnClickListener {
            startActivity(Intent(this, Onboarding01Activity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }

        // Set up skip button
        findViewById<Button>(R.id.btnSkip).setOnClickListener {
            startActivity(Intent(this, Onboarding03Activity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }
    }
}