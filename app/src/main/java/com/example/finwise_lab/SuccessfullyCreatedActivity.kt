package com.example.finwise_lab

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton

class SuccessfullyCreatedActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_successfully_created)
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.successLayout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize views
        val successIcon = findViewById<View>(R.id.ivSuccess)
        val continueButton = findViewById<MaterialButton>(R.id.btnContinue)

        // Set initial state for success icon
        successIcon.alpha = 0f
        successIcon.scaleX = 0f
        successIcon.scaleY = 0f

        // Animate only the success icon
        val iconAnimator = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(successIcon, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(successIcon, "scaleX", 0f, 1f),
                ObjectAnimator.ofFloat(successIcon, "scaleY", 0f, 1f)
            )
            duration = 800
            interpolator = OvershootInterpolator(2f)
        }

        // Start the animation
        iconAnimator.start()

        // Set up continue button click listener
        continueButton.setOnClickListener {
            startActivity(Intent(this, SignInActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }
    }
}