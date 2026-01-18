package com.example.finwise_lab

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import com.google.android.material.button.MaterialButton
import java.util.Random

class VerificationCodeActivity : AppCompatActivity() {
    private lateinit var etCode1: EditText
    private lateinit var etCode2: EditText
    private lateinit var etCode3: EditText
    private lateinit var etCode4: EditText
    private lateinit var etCode5: EditText
    private lateinit var btnVerify: MaterialButton
    private lateinit var btnBack: ImageButton
    private lateinit var tvEmail: TextView
    private lateinit var tvResend: TextView
    private var email: String = ""
    private var verificationCode: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_verification_code)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.verificationCode)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Get email and verification code from intent
        email = intent.getStringExtra("email") ?: ""
        verificationCode = intent.getStringExtra("verificationCode") ?: ""

        // Initialize views
        etCode1 = findViewById(R.id.etCode1)
        etCode2 = findViewById(R.id.etCode2)
        etCode3 = findViewById(R.id.etCode3)
        etCode4 = findViewById(R.id.etCode4)
        etCode5 = findViewById(R.id.etCode5)
        btnVerify = findViewById(R.id.btnVerify)
        btnBack = findViewById(R.id.btnBackVC)
        tvEmail = findViewById(R.id.tvEmail)
        tvResend = findViewById(R.id.tvResend)

        // Set masked email text
        tvEmail.text = maskEmail(email)

        // Style the Resend text
        val resendText = "Didn't receive the code? Resend"
        val resendSpannableString = SpannableString(resendText)
        
        // Make "Didn't receive the code?" black
        resendSpannableString.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(this, R.color.black)),
            0,
            resendText.indexOf("Resend"),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        
        // Make "Resend" blue and underlined
        val resendStartIndex = resendText.indexOf("Resend")
        val resendEndIndex = resendStartIndex + "Resend".length
        
        resendSpannableString.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(this, R.color.primary)),
            resendStartIndex,
            resendEndIndex,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        
        resendSpannableString.setSpan(
            UnderlineSpan(),
            resendStartIndex,
            resendEndIndex,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        
        // Make "Resend" clickable
        val resendClickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                // Handle resend click here
                Toast.makeText(this@VerificationCodeActivity, "Resend code clicked", Toast.LENGTH_SHORT).show()
            }
            
            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.isUnderlineText = true
                ds.color = ContextCompat.getColor(this@VerificationCodeActivity, R.color.primary)
            }
        }
        
        resendSpannableString.setSpan(
            resendClickableSpan,
            resendStartIndex,
            resendEndIndex,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        
        tvResend.text = resendSpannableString
        tvResend.movementMethod = LinkMovementMethod.getInstance()

        // Set up back button
        btnBack.setOnClickListener {
            onBackPressed()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // Set up code text watchers and focus change listeners
        setupEditTextListeners()

        // Set up verify button
        btnVerify.setOnClickListener {
            val enteredCode = getFullCode()

            if (enteredCode.isEmpty()) {
                Toast.makeText(this, "Please enter the verification code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (enteredCode.length != 5) {
                Toast.makeText(this, "Please enter a valid 5-digit code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (enteredCode == verificationCode) {
                // Code is correct, navigate to reset password screen
                val intent = Intent(this, ResetPasswordActivity::class.java).apply {
                    putExtra("email", email)
                }
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            } else {
                Toast.makeText(this, "Invalid verification code", Toast.LENGTH_SHORT).show()
            }
        }

        // Set up resend code
        tvResend.setOnClickListener {
            // Generate new verification code
            val newCode = generateVerificationCode()
            verificationCode = newCode

            // Send verification code via email
            val emailSender = EmailSender(
                onEmailSent = {
                    runOnUiThread {
                        // Clear all input fields
                        etCode1.text.clear()
                        etCode2.text.clear()
                        etCode3.text.clear()
                        etCode4.text.clear()
                        etCode5.text.clear()
                        etCode1.requestFocus()

                        Toast.makeText(this, "New verification code sent to your email", Toast.LENGTH_LONG).show()
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
                Your new verification code is: $newCode
                
                If you didn't request this, please ignore this email.
                
                Best regards,
                FinWise Team
            """.trimIndent()

            // Log the verification code
            Log.d("VerificationCode", "New verification code generated: $newCode")
            Log.d("VerificationCode", "Sending verification code to: $email")

            emailSender.sendEmail(email, subject, messageBody)
            Toast.makeText(this, "Sending new verification code to your email...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateVerificationCode(): String {
        val random = Random()
        return (10000 + random.nextInt(90000)).toString() // Generates a 5-digit number
    }

    private fun setupEditTextListeners() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val code = getFullCode()
                btnVerify.isEnabled = code.length == 5
                btnVerify.alpha = if (code.length == 5) 1f else 0.5f
            }
        }

        val editTexts = listOf(etCode1, etCode2, etCode3, etCode4, etCode5)
        
        editTexts.forEachIndexed { index, editText ->
            editText.addTextChangedListener(textWatcher)
            
            // Set up focus change listener
            editText.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    (v as EditText).setSelection(editText.text.length)
                }
            }

            // Set up key listener for backspace
            editText.setOnKeyListener { v, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN) {
                    if (editText.text.isEmpty() && index > 0) {
                        editTexts[index - 1].requestFocus()
                        editTexts[index - 1].setSelection(editTexts[index - 1].text.length)
                    }
                }
                false
            }

            // Set up text change listener for auto-move
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (s?.length == 1 && index < editTexts.size - 1) {
                        editTexts[index + 1].requestFocus()
                    }
                }
            })
        }
    }

    private fun getFullCode(): String {
        return etCode1.text.toString() +
                etCode2.text.toString() +
                etCode3.text.toString() +
                etCode4.text.toString() +
                etCode5.text.toString()
    }

    private fun maskEmail(email: String): String {
        if (email.isEmpty()) return ""
        
        val atIndex = email.indexOf('@')
        if (atIndex <= 3) return email // If email is too short to mask
        
        val prefix = email.substring(0, atIndex)
        val domain = email.substring(atIndex)
        
        val maskedPrefix = if (prefix.length > 3) {
            "xx" + prefix.substring(prefix.length - 3)
        } else {
            prefix
        }
        
        return maskedPrefix + domain
    }
}