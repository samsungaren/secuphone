package com.example.secuphone.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.util.HashSet;
import java.util.Set;

/**
 * Utility class to manage app lock preferences
 */
public class AppLockPreferences {
    private static final String TAG = "AppLockPreferences";
    
    // Preference keys
    private static final String PREF_PIN = "app_lock_pin";
    private static final String PREF_PIN_SET = "app_lock_pin_set";
    private static final String PREF_LOCKED_APPS = "app_lock_locked_apps";
    private static final String PREF_TEMP_UNLOCK_PREFIX = "app_lock_temp_unlock_";
    
    // Increased temporary unlock duration to 10 minutes (600,000 ms)
    // This ensures the app remains unlocked for a reasonable time after PIN entry
    private static final long TEMP_UNLOCK_DURATION = 600000; // 10 minutes
    
    private final SharedPreferences preferences;
    private final Context context;
    
    public AppLockPreferences(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = PreferenceManager.getDefaultSharedPreferences(context);
    }
    
    /**
     * Set PIN for app lock
     */
    public boolean setPin(String pin) {
        try {
            if (pin == null || pin.isEmpty()) {
                return false;
            }
            
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString(PREF_PIN, pin);
            editor.putBoolean(PREF_PIN_SET, true);
            editor.apply();
            
            Log.i(TAG, "PIN set successfully");
            return isPinSet();
        } catch (Exception e) {
            Log.e(TAG, "Error setting PIN", e);
            return false;
        }
    }
    
    /**
     * Check if PIN is set
     */
    public boolean isPinSet() {
        return preferences.getBoolean(PREF_PIN_SET, false) && 
               preferences.contains(PREF_PIN) && 
               !preferences.getString(PREF_PIN, "").isEmpty();
    }
    
    /**
     * Verify entered PIN
     */
    public boolean verifyPin(String enteredPin) {
        if (!isPinSet()) {
            return false;
        }
        
        String savedPin = preferences.getString(PREF_PIN, "");
        return savedPin.equals(enteredPin);
    }
    
    /**
     * Add app to locked apps list
     */
    public void addLockedApp(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return;
        }
        
        Set<String> lockedApps = getLockedApps();
        
        // Only add if not already present
        if (!lockedApps.contains(packageName)) {
            lockedApps.add(packageName);
            
            SharedPreferences.Editor editor = preferences.edit();
            editor.putStringSet(PREF_LOCKED_APPS, lockedApps);
            editor.apply();
            
            Log.i(TAG, "App locked: " + packageName);
        }
    }
    
    /**
     * Remove app from locked apps list
     */
    public void removeLockedApp(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return;
        }
        
        Set<String> lockedApps = getLockedApps();
        
        // Only remove if present
        if (lockedApps.contains(packageName)) {
            lockedApps.remove(packageName);
            
            SharedPreferences.Editor editor = preferences.edit();
            editor.putStringSet(PREF_LOCKED_APPS, lockedApps);
            editor.apply();
            
            // Also clear any temporary unlock
            clearTemporaryUnlock(packageName);
            
            Log.i(TAG, "App unlocked: " + packageName);
        }
    }
    
    /**
     * Get set of locked apps
     */
    public Set<String> getLockedApps() {
        Set<String> defaultSet = new HashSet<>();
        // Create a new HashSet to avoid shared reference issues
        return new HashSet<>(preferences.getStringSet(PREF_LOCKED_APPS, defaultSet));
    }
    
    /**
     * Check if an app is locked
     */
    public boolean isAppLocked(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }
        
        // First check if temporarily unlocked
        if (isTemporarilyUnlocked(packageName)) {
            return false;
        }
        
        // Check if it's in the locked apps set
        boolean isLocked = getLockedApps().contains(packageName);
        
        if (isLocked) {
            Log.d(TAG, "App is locked: " + packageName);
        }
        
        return isLocked;
    }
    
    /**
     * Set temporary unlock for an app
     */
    public void setTemporaryUnlock(String packageName, long timestamp) {
        if (packageName == null || packageName.isEmpty()) {
            return;
        }
        
        SharedPreferences.Editor editor = preferences.edit();
        editor.putLong(PREF_TEMP_UNLOCK_PREFIX + packageName, timestamp);
        editor.apply();
        
        Log.i(TAG, "Temporarily unlocked app: " + packageName + " until " + (timestamp + TEMP_UNLOCK_DURATION));
    }
    
    /**
     * Check if an app is temporarily unlocked
     */
    public boolean isTemporarilyUnlocked(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }
        
        long unlockTime = preferences.getLong(PREF_TEMP_UNLOCK_PREFIX + packageName, 0);
        if (unlockTime == 0) {
            return false;
        }
        
        // Check if the unlock is still valid
        long currentTime = System.currentTimeMillis();
        long timeRemaining = (unlockTime + TEMP_UNLOCK_DURATION) - currentTime;
        boolean valid = timeRemaining > 0;
        
        if (valid) {
            Log.d(TAG, "App is temporarily unlocked: " + packageName + 
                  " for " + (timeRemaining / 1000) + " more seconds");
        } else {
            // Clean up expired unlock
            clearTemporaryUnlock(packageName);
            Log.d(TAG, "Temporary unlock expired for: " + packageName);
        }
        
        return valid;
    }
    
    /**
     * Clear temporary unlock for an app
     */
    public void clearTemporaryUnlock(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return;
        }
        
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove(PREF_TEMP_UNLOCK_PREFIX + packageName);
        editor.apply();
    }
    
    /**
     * Clear all app lock settings
     */
    public void clearAppLockSettings() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove(PREF_PIN);
        editor.remove(PREF_PIN_SET);
        editor.remove(PREF_LOCKED_APPS);
        editor.apply();
        
        Log.i(TAG, "All app lock settings cleared");
    }
} 