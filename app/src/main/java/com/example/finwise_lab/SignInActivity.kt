package com.example.finwise_lab

import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import com.google.android.material.textfield.TextInputEditText
import com.example.finwise_lab.database.DatabaseHelper

class SignInActivity : AppCompatActivity() {
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnSignIn: Button
    private lateinit var tvSignUp: TextView
    private lateinit var tvForgotPassword: TextView
    private lateinit var dbHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_sign_in)
        

        dbHelper = DatabaseHelper(this)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.signIn)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnSignIn = findViewById(R.id.btnSignIn)
        tvSignUp = findViewById(R.id.tvSignUp)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)


        val signUpText = "Don't have an account? Sign Up"
        val signUpSpannableString = SpannableString(signUpText)
        

        val signUpStartIndex = signUpText.indexOf("Sign Up")
        val signUpEndIndex = signUpStartIndex + "Sign Up".length
        
        signUpSpannableString.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(this, R.color.primary)),
            signUpStartIndex,
            signUpEndIndex,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        
        signUpSpannableString.setSpan(
            UnderlineSpan(),
            signUpStartIndex,
            signUpEndIndex,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        

        val signUpClickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                startActivity(Intent(this@SignInActivity, SignUpActivity::class.java))
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            }

            
            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.isUnderlineText = true
                ds.color = ContextCompat.getColor(this@SignInActivity, R.color.primary)
            }
        }
        
        signUpSpannableString.setSpan(
            signUpClickableSpan,
            signUpStartIndex,
            signUpEndIndex,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        
        tvSignUp.text = signUpSpannableString
        tvSignUp.movementMethod = LinkMovementMethod.getInstance()

        val forgotPasswordText = "Forgot Password?"
        val forgotPasswordSpannableString = SpannableString(forgotPasswordText)
        

        forgotPasswordSpannableString.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(this, R.color.primary)),
            0,
            forgotPasswordText.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        
        forgotPasswordSpannableString.setSpan(
            UnderlineSpan(),
            0,
            forgotPasswordText.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        val forgotPasswordClickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                startActivity(Intent(this@SignInActivity, ForgotPassword01Activity::class.java))
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
            
            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.isUnderlineText = true
                ds.color = ContextCompat.getColor(this@SignInActivity, R.color.primary)
            }
        }
        
        forgotPasswordSpannableString.setSpan(
            forgotPasswordClickableSpan,
            0,
            forgotPasswordText.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        
        tvForgotPassword.text = forgotPasswordSpannableString
        tvForgotPassword.movementMethod = LinkMovementMethod.getInstance()


        btnSignIn.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            checkCredentials(email, password)
        }

        findViewById<View>(R.id.btnBackSI).setOnClickListener {
            startActivity(Intent(this, GetStartActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }
    }

    private fun checkCredentials(email: String, password: String) {
        try {

            val user = dbHelper.getUser(email)

            if (user != null && user.password == password) {

                Toast.makeText(this, "Sign in successful!", Toast.LENGTH_SHORT).show()

                dbHelper.setLoggedIn(email, true)
                dbHelper.setHasSeenOnboarding(email, true)

                startActivity(Intent(this, HomeActivity::class.java))
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            } else {
                Toast.makeText(this, "Invalid email or password", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error checking credentials", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }
}