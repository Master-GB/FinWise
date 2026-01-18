package com.example.finwise_lab

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import android.view.LayoutInflater
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class MyAccountActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyAccountBinding
    private var selectedImageUri: Uri? = null
    private val gson = Gson()
    private val USER_DATA_FILE = "users.json"

    private val getContent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                binding.ivProfilePicture.setImageURI(uri)
                saveProfilePicture(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = ContextCompat.getColor(this, R.color.primary)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
        }
        
        binding = ActivityMyAccountBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        loadUserData()
        setupClickListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        
        binding.btnBack.setOnClickListener {
            onBackPressed()
        }

    }

    private fun loadUserData() {
        val dbHelper = com.example.finwise_lab.database.DatabaseHelper(this)
        val email = dbHelper.getLoggedInUserEmail()
        if (email == null) {
            Toast.makeText(this, "No logged-in user", Toast.LENGTH_SHORT).show()
            return
        }
        val user = dbHelper.getUser(email)
        if (user != null) {
            binding.tvName.text = user.username
            binding.tvEmail.text = user.email
            binding.tvPhone.text = user.phone
        } else {
            Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
        }

        val picFile = File(filesDir, "profile_picture_${email}.jpg")
        if (picFile.exists()) binding.ivProfilePicture.setImageURI(Uri.fromFile(picFile))
    }

    private fun setupClickListeners() {
        binding.layoutName.setOnClickListener { showEditDialog("Name", binding.tvName.text.toString()) { updateName(it) } }
        binding.layoutEmail.setOnClickListener { showEditDialog("Email", binding.tvEmail.text.toString()) { updateEmail(it) } }
        binding.layoutPhone.setOnClickListener { showEditDialog("Phone", binding.tvPhone.text.toString()) { updatePhone(it) } }
        
        binding.tvChangePhoto.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            getContent.launch(intent)
        }
    }

    private fun showEditDialog(title: String, currentValue: String, onSave: (String) -> Unit) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_field, null)
        val editText = view.findViewById<TextInputEditText>(R.id.editText)
        editText.setText(currentValue)

        MaterialAlertDialogBuilder(this)
            .setTitle("Edit $title")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val newValue = editText.text.toString()
                if (newValue.isNotBlank()) {
                    onSave(newValue)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateName(newName: String) {
        val dbHelper = com.example.finwise_lab.database.DatabaseHelper(this)
        val email = dbHelper.getLoggedInUserEmail()
        if (email == null) {
            Toast.makeText(this, "No logged-in user", Toast.LENGTH_SHORT).show()
            return
        }
        val updated = dbHelper.updateUsername(email, newName)
        if (updated > 0) {
            binding.tvName.text = newName
            Toast.makeText(this, "Name updated successfully", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Failed to update name", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateEmail(newEmail: String) {
        val dbHelper = com.example.finwise_lab.database.DatabaseHelper(this)
        val oldEmail = dbHelper.getLoggedInUserEmail()
        if (oldEmail == null) {
            Toast.makeText(this, "No logged-in user", Toast.LENGTH_SHORT).show()
            return
        }
        val updated = dbHelper.updateUserEmail(oldEmail, newEmail)
        if (updated > 0) {
            binding.tvEmail.text = newEmail
            Toast.makeText(this, "Email updated successfully", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Failed to update email", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePhone(newPhone: String) {
        val dbHelper = com.example.finwise_lab.database.DatabaseHelper(this)
        val email = dbHelper.getLoggedInUserEmail()
        if (email == null) {
            Toast.makeText(this, "No logged-in user", Toast.LENGTH_SHORT).show()
            return
        }
        val updated = dbHelper.updateUserPhone(email, newPhone)
        if (updated > 0) {
            binding.tvPhone.text = newPhone
            Toast.makeText(this, "Phone updated successfully", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Failed to update phone", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveProfilePicture(uri: Uri) {
        try {
            // Get current user's email from SQLite
            val dbHelper = com.example.finwise_lab.database.DatabaseHelper(this)
            val currentUserEmail = dbHelper.getLoggedInUserEmail()

            // Create a file in the app's private storage
            val file = File(filesDir, "profile_picture_${currentUserEmail}.jpg")
            
            // Copy the selected image to the app's private storage
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }

            Toast.makeText(this, "Profile picture updated successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error saving profile picture: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
} 