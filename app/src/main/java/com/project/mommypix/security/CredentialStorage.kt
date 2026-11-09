package com.project.mommypix.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Manages secure storage of API credentials backed by the Android Keystore.
 * Never logs credentials.
 */
class CredentialStorage(context: Context) {

    private val sharedPreferences: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize EncryptedSharedPreferences, using fallback storage", e)
        context.getSharedPreferences(FALLBACK_PREFS_FILE, Context.MODE_PRIVATE)
    }

    fun saveCredentials(userId: String, apiKey: String) {
        sharedPreferences.edit()
            .putString(KEY_USER_ID, userId.trim())
            .putString(KEY_API_KEY, apiKey.trim())
            .apply()
    }

    fun getUserId(): String? {
        val id = sharedPreferences.getString(KEY_USER_ID, null)
        return if (id.isNullOrBlank()) null else id
    }

    fun getApiKey(): String? {
        val key = sharedPreferences.getString(KEY_API_KEY, null)
        return if (key.isNullOrBlank()) null else key
    }

    fun hasCredentials(): Boolean {
        return !getUserId().isNullOrBlank() && !getApiKey().isNullOrBlank()
    }

    fun clearCredentials() {
        sharedPreferences.edit()
            .remove(KEY_USER_ID)
            .remove(KEY_API_KEY)
            .apply()
    }

    companion object {
        private const val TAG = "CredentialStorage"
        private const val SECURE_PREFS_FILE = "mommypix_secure_prefs"
        private const val FALLBACK_PREFS_FILE = "mommypix_secure_fallback"
        private const val KEY_USER_ID = "enc_user_id"
        private const val KEY_API_KEY = "enc_api_key"
    }
}

