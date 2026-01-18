package com.example.finwise_lab

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.example.finwise_lab.database.DatabaseHelper
import java.util.Random

class ForgotPassword01Activity : AppCompatActivity() {
    private lateinit var etEmail: TextInputEditText
    private lateinit var btnContinue: MaterialButton
    private lateinit var btnBack: ImageButton
    private lateinit var dbHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_forgot_password01)
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.forgotPassword01)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize database helper
        dbHelper = DatabaseHelper(this)
        // Initialize views
        etEmail = findViewById(R.id.etEmail)
        btnContinue = findViewById(R.id.btnContinue)

        // Set up back button
        findViewById<View>(R.id.btnBackFP).setOnClickListener {
            startActivity(Intent(this, SignInActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }

        // Set up email text watcher
        etEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val email = s.toString().trim()
                val isValid = Patterns.EMAIL_ADDRESS.matcher(email).matches()
                btnContinue.isEnabled = isValid
                btnContinue.alpha = if (isValid) 1f else 0.5f
            }
        })

        // Set up continue button
        btnContinue.setOnClickListener {
            val email = etEmail.text.toString().trim()
            
            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Check if email exists in stored user data
            if (!isEmailRegistered(email)) {
                Toast.makeText(this, "This email is not registered", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Toast.makeText(this, "This email is not registered", Toast.LENGTH_SHORT).show()
            // Generate verification code
            val verificationCode = generateVerificationCode()
            
            // Send verification code via email
            val emailSender = EmailSender(
                onEmailSent = {
                    runOnUiThread {
                        Toast.makeText(this, "Verification code sent to your email", Toast.LENGTH_LONG).show()
                        // Navigate to verification code screen
                        val intent = Intent(this, VerificationCodeActivity::class.java).apply {
                            putExtra("email", email)
                            putExtra("verificationCode", verificationCode)
                        }
                        startActivity(intent)
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                        finish()
                    }
                },
                onError = { error ->
                    runOnUiThread {
                        Toast.makeText(this, "Failed to send email: $error", Toast.LENGTH_LONG).show()
                    }
                }
            )

            val subject = "FinWise Password Reset Verification"
            val messageBody = """
                Hello,
                
                You have requested to reset your password for your FinWise account.
                Your verification code is: $verificationCode
                
                If you didn't request this, please ignore this email.
                
                Best regards,
                FinWise Team
            """.trimIndent()

            emailSender.sendEmail(email, subject, messageBody)
            Toast.makeText(this, "Sending verification code to your email...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isEmailRegistered(email: String): Boolean {
        return dbHelper.checkEmailExists(email)
    }

    private fun generateVerificationCode(): String {
        val random = Random()
        return (10000 + random.nextInt(90000)).toString() // Generates a 5-digit number
    }
}