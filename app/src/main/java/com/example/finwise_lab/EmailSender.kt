package com.example.finwise_lab

import android.os.AsyncTask
import android.util.Log

class EmailSender(
    private val onEmailSent: () -> Unit,
    private val onError: (String) -> Unit
) {
    fun sendEmail(recipientEmail: String, subject: String, messageBody: String) {
        // Simulate email sending
        Log.d("EmailSender", "Sending email to: $recipientEmail")
        Log.d("EmailSender", "Subject: $subject")
        Log.d("EmailSender", "Message: $messageBody")
        
        // Simulate delay
        Thread.sleep(2000)
        
        // Always succeed for testing purposes
        onEmailSent()
    }
} 