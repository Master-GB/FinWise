package com.example.finwise_lab.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.finwise_lab.models.User

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "FinWiseDB"
        private const val DATABASE_VERSION = 3

        private const val TABLE_USERS = "users"
        private const val COLUMN_ID = "id"
        private const val COLUMN_USERNAME = "username"
        private const val COLUMN_EMAIL = "email"
        private const val COLUMN_PASSWORD = "password"
        private const val COLUMN_PHONE = "phone"


        private const val TABLE_SESSION = "session"
        private const val COLUMN_USER_EMAIL = "user_email"
        private const val COLUMN_IS_LOGGED_IN = "is_logged_in"
        private const val COLUMN_HAS_SEEN_ONBOARDING = "has_seen_onboarding"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val CREATE_USERS_TABLE = ("CREATE TABLE " + TABLE_USERS + "("
                + COLUMN_ID + " TEXT PRIMARY KEY,"
                + COLUMN_USERNAME + " TEXT,"
                + COLUMN_EMAIL + " TEXT UNIQUE,"
                + COLUMN_PASSWORD + " TEXT,"
                + COLUMN_PHONE + " TEXT"
                + ")")
        db.execSQL(CREATE_USERS_TABLE)

        val CREATE_SESSION_TABLE = ("CREATE TABLE " + TABLE_SESSION + "("
                + COLUMN_USER_EMAIL + " TEXT PRIMARY KEY,"
                + COLUMN_IS_LOGGED_IN + " INTEGER DEFAULT 0,"
                + COLUMN_HAS_SEEN_ONBOARDING + " INTEGER DEFAULT 0"
                + ")")
        db.execSQL(CREATE_SESSION_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {

        if (oldVersion < 2) {
            val CREATE_SESSION_TABLE = ("CREATE TABLE IF NOT EXISTS " + TABLE_SESSION + "(" +
                    COLUMN_USER_EMAIL + " TEXT PRIMARY KEY," +
                    COLUMN_IS_LOGGED_IN + " INTEGER DEFAULT 0," +
                    COLUMN_HAS_SEEN_ONBOARDING + " INTEGER DEFAULT 0" +
                    ")")
            db.execSQL(CREATE_SESSION_TABLE)
        }

        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE $TABLE_USERS ADD COLUMN $COLUMN_PHONE TEXT")
        }
    }

    fun addUser(user: User): Long {
        val db = this.writableDatabase
        val values = ContentValues()
        
        values.put(COLUMN_ID, user.id)
        values.put(COLUMN_USERNAME, user.username)
        values.put(COLUMN_EMAIL, user.email)
        values.put(COLUMN_PASSWORD, user.password)
        values.put(COLUMN_PHONE, user.phone)

        val success = db.insert(TABLE_USERS, null, values)
        db.close()
        return success
    }

    fun getUser(email: String): User? {
        val db = this.readableDatabase
        var user: User? = null

        val cursor = db.query(
            TABLE_USERS,
            arrayOf(COLUMN_ID, COLUMN_USERNAME, COLUMN_EMAIL, COLUMN_PASSWORD, COLUMN_PHONE),
            "$COLUMN_EMAIL = ?",
            arrayOf(email),
            null,
            null,
            null
        )

        if (cursor.moveToFirst()) {
            user = User(
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USERNAME)),
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EMAIL)),
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PASSWORD)),
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PHONE))
            )
        }
        cursor.close()
        db.close()
        return user
    }

    fun checkEmailExists(email: String): Boolean {
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_USERS,
            arrayOf(COLUMN_EMAIL),
            "$COLUMN_EMAIL = ?",
            arrayOf(email),
            null,
            null,
            null
        )
        val exists = cursor.count > 0
        cursor.close()
        db.close()
        return exists
    }

    fun updateUserPassword(email: String, newPassword: String): Int {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(COLUMN_PASSWORD, newPassword)
        val rowsAffected = db.update(TABLE_USERS, values, "$COLUMN_EMAIL = ?", arrayOf(email))
        db.close()
        return rowsAffected
    }

    fun updateUserPhone(email: String, newPhone: String): Int {
        val db = this.writableDatabase
        val values = ContentValues().apply { put(COLUMN_PHONE, newPhone) }
        val rows = db.update(TABLE_USERS, values, "$COLUMN_EMAIL = ?", arrayOf(email))
        db.close()
        return rows
    }

    fun updateUsername(email: String, newUsername: String): Int {
        val db = this.writableDatabase
        val values = ContentValues().apply { put(COLUMN_USERNAME, newUsername) }
        val rows = db.update(TABLE_USERS, values, "$COLUMN_EMAIL = ?", arrayOf(email))
        db.close()
        return rows
    }

    fun updateUserEmail(oldEmail: String, newEmail: String): Int {
        val db = this.writableDatabase
        // Update users table
        val values = ContentValues().apply { put(COLUMN_EMAIL, newEmail) }
        val rows = db.update(TABLE_USERS, values, "$COLUMN_EMAIL = ?", arrayOf(oldEmail))
        // Update session table if exists
        val sessionValues = ContentValues().apply { put(COLUMN_USER_EMAIL, newEmail) }
        db.update(TABLE_SESSION, sessionValues, "$COLUMN_USER_EMAIL = ?", arrayOf(oldEmail))
        db.close()
        return rows
    }

    fun getAllUsers(): List<User> {
        val users = mutableListOf<User>()
        val selectQuery = "SELECT * FROM $TABLE_USERS"
        val db = this.readableDatabase
        val cursor = db.rawQuery(selectQuery, null)

        if (cursor.moveToFirst()) {
            do {
                val user = User(
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USERNAME)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EMAIL)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PASSWORD)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PHONE))
                )
                users.add(user)
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return users
    }

    fun setLoggedIn(email: String, isLoggedIn: Boolean) {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(COLUMN_USER_EMAIL, email)
        values.put(COLUMN_IS_LOGGED_IN, if (isLoggedIn) 1 else 0)

        // Try to update first
        val rowsAffected = db.update(TABLE_SESSION, values, "$COLUMN_USER_EMAIL = ?", arrayOf(email))

        // If no rows were updated, insert new row
        if (rowsAffected == 0) {
            db.insert(TABLE_SESSION, null, values)
        }
        db.close()
    }

    fun isLoggedIn(): Boolean {
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_SESSION,
            arrayOf(COLUMN_IS_LOGGED_IN),
            "$COLUMN_IS_LOGGED_IN = ?",
            arrayOf("1"),
            null,
            null,
            null
        )
        val isLoggedIn = cursor.count > 0
        cursor.close()
        db.close()
        return isLoggedIn
    }

    fun getLoggedInUserEmail(): String? {
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_SESSION,
            arrayOf(COLUMN_USER_EMAIL),
            "$COLUMN_IS_LOGGED_IN = ?",
            arrayOf("1"),
            null,
            null,
            null
        )
        var email: String? = null
        if (cursor.moveToFirst()) {
            email = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USER_EMAIL))
        }
        cursor.close()
        db.close()
        return email
    }

    fun setHasSeenOnboarding(email: String, hasSeen: Boolean) {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(COLUMN_USER_EMAIL, email)
        values.put(COLUMN_HAS_SEEN_ONBOARDING, if (hasSeen) 1 else 0)

        // Try to update first
        val rowsAffected = db.update(TABLE_SESSION, values, "$COLUMN_USER_EMAIL = ?", arrayOf(email))

        // If no rows were updated, insert new row
        if (rowsAffected == 0) {
            db.insert(TABLE_SESSION, null, values)
        }
        db.close()
    }

    fun hasSeenOnboarding(email: String): Boolean {
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_SESSION,
            arrayOf(COLUMN_HAS_SEEN_ONBOARDING),
            "$COLUMN_USER_EMAIL = ?",
            arrayOf(email),
            null,
            null,
            null
        )
        var hasSeen = false
        if (cursor.moveToFirst()) {
            hasSeen = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_HAS_SEEN_ONBOARDING)) == 1
        }
        cursor.close()
        db.close()
        return hasSeen
    }
}
