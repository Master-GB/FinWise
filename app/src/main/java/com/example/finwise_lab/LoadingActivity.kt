package com.example.finwise_lab

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ProgressBar
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.finwise_lab.database.DatabaseHelper

class LoadingActivity : AppCompatActivity() {
    private lateinit var progressBar: ProgressBar
    private var progressStatus = 0
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var dbHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set status bar color
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = ContextCompat.getColor(this, R.color.primary)
        
        setContentView(R.layout.activity_loading)
        
        // Handle edge-to-edge
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.loading)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        dbHelper = DatabaseHelper(this)
        progressBar = findViewById(R.id.progressBar)
        
        // Start progress bar animation
        Thread {
            while (progressStatus < 100) {
                progressStatus += 1
                handler.post {
                    progressBar.progress = progressStatus
                }
                try {
                    Thread.sleep(50)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
            handler.post {
                // Check login state and user existence
                val isLoggedIn = try {
                    dbHelper.isLoggedIn()
                } catch (e: android.database.sqlite.SQLiteException) {
                    false
                }
                if (isLoggedIn) {
                    // User is signed in, go directly to HomeActivity
                    startActivity(Intent(this, HomeActivity::class.java))
                } else {
                    val users = dbHelper.getAllUsers()
                    if (users.isNotEmpty()) {
                        // Users exist, go to SignInActivity
                        startActivity(Intent(this, SignInActivity::class.java))
                    } else {
                        // No users registered, go to onboarding
                        startActivity(Intent(this, Onboarding01Activity::class.java))
                    }
                }
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            }
        }.start()
    }
}