package com.example.secuphone.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.example.secuphone.models.PasswordEntry;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Utility class for managing password entries with encryption
 */
public class PasswordManager {
    private static final String TAG = "PasswordManager";
    private static final String PREFS_NAME = "encrypted_password_prefs";
    private static final String PASSWORDS_KEY = "passwords";
    private static final String MASTER_PASSWORD_HASH_KEY = "master_password_hash";
    private static final String MASTER_PASSWORD_SALT_KEY = "master_password_salt";
    
    private SharedPreferences encryptedPrefs;
    private Gson gson;
    private boolean isAuthenticated = false;
    
    public PasswordManager(Context context) {
        gson = new Gson();
        try {
            // Create a master key for encryption
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                    MasterKey.DEFAULT_MASTER_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(MasterKey.DEFAULT_AES_GCM_MASTER_KEY_SIZE)
                    .build();
            
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyGenParameterSpec(spec)
                    .build();
            
            // Create encrypted SharedPreferences
            encryptedPrefs = EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Error initializing PasswordManager", e);
            // Fallback to regular SharedPreferences (not encrypted)
            encryptedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }
    
    /**
     * Check if a master password has been set up
     */
    public boolean isMasterPasswordSet() {
        return encryptedPrefs.getString(MASTER_PASSWORD_HASH_KEY, null) != null &&
               encryptedPrefs.getString(MASTER_PASSWORD_SALT_KEY, null) != null;
    }
    
    /**
     * Set up a new master password
     */
    public boolean setMasterPassword(String masterPassword) {
        try {
            // Generate a random salt
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[16];
            random.nextBytes(salt);
            String saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP);
            
            // Generate a hash of the password with the salt
            String passwordHash = hashPassword(masterPassword, salt);
            
            // Save the hash and salt
            encryptedPrefs.edit()
                    .putString(MASTER_PASSWORD_HASH_KEY, passwordHash)
                    .putString(MASTER_PASSWORD_SALT_KEY, saltBase64)
                    .apply();
            
            // Mark as authenticated
            isAuthenticated = true;
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error setting master password", e);
            return false;
        }
    }
    
    /**
     * Verify the master password
     */
    public boolean verifyMasterPassword(String masterPassword) {
        try {
            String storedHash = encryptedPrefs.getString(MASTER_PASSWORD_HASH_KEY, null);
            String saltBase64 = encryptedPrefs.getString(MASTER_PASSWORD_SALT_KEY, null);
            
            if (storedHash == null || saltBase64 == null) {
                // Master password not set up yet
                return false;
            }
            
            byte[] salt = Base64.decode(saltBase64, Base64.NO_WRAP);
            String inputHash = hashPassword(masterPassword, salt);
            
            isAuthenticated = storedHash.equals(inputHash);
            return isAuthenticated;
        } catch (Exception e) {
            Log.e(TAG, "Error verifying master password", e);
            return false;
        }
    }
    
    /**
     * Get all saved password entries
     */
    public List<PasswordEntry> getAllPasswords() {
        if (!isAuthenticated) {
            // Return empty list if not authenticated
            return new ArrayList<>();
        }
        
        String json = encryptedPrefs.getString(PASSWORDS_KEY, null);
        if (json == null) {
            return new ArrayList<>();
        }
        
        TypeToken<List<PasswordEntry>> typeToken = new TypeToken<List<PasswordEntry>>() {};
        List<PasswordEntry> passwords = gson.fromJson(json, typeToken.getType());
        return passwords != null ? passwords : new ArrayList<>();
    }
    
    /**
     * Save a new password entry
     */
    public boolean savePassword(PasswordEntry entry) {
        if (!isAuthenticated) {
            return false;
        }
        
        if (entry.getId() == null) {
            entry.setId(UUID.randomUUID().toString());
        }
        
        List<PasswordEntry> passwords = getAllPasswords();
        
        // Check if entry with ID already exists
        for (int i = 0; i < passwords.size(); i++) {
            if (passwords.get(i).getId().equals(entry.getId())) {
                passwords.set(i, entry); // Update existing entry
                return savePasswordList(passwords);
            }
        }
        
        // Add new entry
        passwords.add(entry);
        return savePasswordList(passwords);
    }
    
    /**
     * Delete a password entry by ID
     */
    public boolean deletePassword(String id) {
        if (!isAuthenticated) {
            return false;
        }
        
        List<PasswordEntry> passwords = getAllPasswords();
        boolean removed = passwords.removeIf(entry -> entry.getId().equals(id));
        
        if (removed) {
            return savePasswordList(passwords);
        }
        return false;
    }
    
    /**
     * Get a password entry by ID
     */
    public PasswordEntry getPasswordById(String id) {
        if (!isAuthenticated) {
            return null;
        }
        
        List<PasswordEntry> passwords = getAllPasswords();
        
        for (PasswordEntry entry : passwords) {
            if (entry.getId().equals(id)) {
                return entry;
            }
        }
        
        return null;
    }
    
    /**
     * Helper method to hash a password with salt using SHA-256
     */
    private String hashPassword(String password, byte[] salt) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(salt);
        byte[] hashedPassword = md.digest(password.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(hashedPassword, Base64.NO_WRAP);
    }
    
    /**
     * Helper method to save the password list to encrypted storage
     */
    private boolean savePasswordList(List<PasswordEntry> passwords) {
        String json = gson.toJson(passwords);
        return encryptedPrefs.edit()
                .putString(PASSWORDS_KEY, json)
                .commit();
    }
    
    /**
     * Clear authentication state
     */
    public void logout() {
        isAuthenticated = false;
    }
    
    /**
     * Check if user is authenticated with master password
     */
    public boolean isAuthenticated() {
        return isAuthenticated;
    }
} 