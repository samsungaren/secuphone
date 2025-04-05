package com.example.secuphone_bycoursor.admin;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manager class for App Lock functionality using Device Administrator APIs
 */
public class AppLockManager {
    private static final String TAG = "AppLockManager";
    private static final String PREF_LOCKED_APPS = "locked_apps";
    private static final String PREF_PIN_SET = "pin_set";
    private static final String PREF_PIN_CODE = "pin_code";
    
    private static AppLockManager instance;
    
    private final Context context;
    private final DevicePolicyManager dpm;
    private final ComponentName adminComponent;
    
    /**
     * Get singleton instance of AppLockManager
     */
    public static synchronized AppLockManager getInstance(Context context) {
        if (instance == null) {
            instance = new AppLockManager(context.getApplicationContext());
        }
        return instance;
    }
    
    public AppLockManager(Context context) {
        this.context = context;
        dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(context, SecuPhoneDeviceAdmin.class);
    }
    
    /**
     * Check if this app is a device admin
     */
    public boolean isAdminActive() {
        return dpm != null && dpm.isAdminActive(adminComponent);
    }
    
    /**
     * Request to become a device admin
     */
    public Intent getAdminRequestIntent() {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, 
                context.getString(com.example.secuphone_bycoursor.R.string.admin_request_message));
        return intent;
    }
    
    /**
     * Set PIN for app lock
     */
    public boolean setPin(String pin) {
        try {
            if (pin == null || pin.isEmpty()) {
                Log.e(TAG, "Cannot set empty PIN");
                return false;
            }
            
            Log.d(TAG, "Setting PIN in SharedPreferences, pin length: " + pin.length());
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            
            // First store the PIN
            prefs.edit()
                 .putString(PREF_PIN_CODE, pin)
                 .putBoolean(PREF_PIN_SET, true)
                 .apply();
            
            // Now verify it was stored properly
            boolean pinStored = prefs.contains(PREF_PIN_CODE);
            String storedPin = prefs.getString(PREF_PIN_CODE, "");
            boolean pinSet = prefs.getBoolean(PREF_PIN_SET, false);
            
            Log.d(TAG, "PIN set successfully: " + pinSet + ", PIN stored: " + pinStored + 
                  ", PIN value matches: " + pin.equals(storedPin));
            
            return pinSet && pinStored && pin.equals(storedPin);
        } catch (Exception e) {
            Log.e(TAG, "Error setting PIN", e);
            return false;
        }
    }
    
    /**
     * Check if PIN is set
     */
    public boolean isPinSet() {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean isPinSet = prefs.getBoolean(PREF_PIN_SET, false);
            String pin = prefs.getString(PREF_PIN_CODE, "");
            boolean hasPin = pin != null && !pin.isEmpty();
            
            Log.d(TAG, "PIN is " + (isPinSet ? "set" : "not set") + ", PIN value length: " + 
                  (pin != null ? pin.length() : 0) + ", hasPin: " + hasPin);
            
            return isPinSet && hasPin;
        } catch (Exception e) {
            Log.e(TAG, "Error checking if PIN is set", e);
            return false;
        }
    }
    
    /**
     * Verify entered PIN
     */
    public boolean verifyPin(String enteredPin) {
        try {
            if (enteredPin == null || enteredPin.isEmpty()) {
                Log.e(TAG, "Attempted to verify empty PIN");
                return false;
            }
            
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            String savedPin = prefs.getString(PREF_PIN_CODE, "");
            
            if (savedPin == null || savedPin.isEmpty()) {
                Log.e(TAG, "No PIN found in preferences");
                return false;
            }
            
            Log.d(TAG, "Verifying PIN, entered: " + enteredPin.length() + " chars, saved: " + savedPin.length() + " chars");
            boolean matches = savedPin.equals(enteredPin);
            Log.d(TAG, "PIN verification " + (matches ? "succeeded" : "failed"));
            return matches;
        } catch (Exception e) {
            Log.e(TAG, "Error verifying PIN", e);
            return false;
        }
    }
    
    /**
     * Add an app to the locked apps list
     */
    public void addLockedApp(String packageName) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            
            // Get current set of locked apps - create a new HashSet to ensure we can modify it
            Set<String> currentLockedApps = prefs.getStringSet(PREF_LOCKED_APPS, new HashSet<>());
            Set<String> lockedApps = new HashSet<>(currentLockedApps != null ? currentLockedApps : new HashSet<>());
            
            // Add new app to locked list
            lockedApps.add(packageName);
            
            // Get editor and apply changes
            SharedPreferences.Editor editor = prefs.edit();
            editor.putStringSet(PREF_LOCKED_APPS, lockedApps);
            editor.apply();
            
            Log.d(TAG, "Added " + packageName + " to locked apps list. Total locked apps: " + lockedApps.size());
            return; // Success
        } catch (Exception e) {
            Log.e(TAG, "Error adding app to locked apps", e);
            // Don't throw the exception - just log it and return
        }
    }
    
    /**
     * Remove an app from the locked apps list
     */
    public void removeLockedApp(String packageName) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            
            // Get current set of locked apps - create a new HashSet to ensure we can modify it
            Set<String> currentLockedApps = prefs.getStringSet(PREF_LOCKED_APPS, new HashSet<>());
            Set<String> lockedApps = new HashSet<>(currentLockedApps != null ? currentLockedApps : new HashSet<>());
            
            // Remove app from locked list
            lockedApps.remove(packageName);
            
            // Get editor and apply changes
            SharedPreferences.Editor editor = prefs.edit();
            editor.putStringSet(PREF_LOCKED_APPS, lockedApps);
            editor.apply();
            
            Log.d(TAG, "Removed " + packageName + " from locked apps list. Remaining locked apps: " + lockedApps.size());
        } catch (Exception e) {
            Log.e(TAG, "Error removing app from locked apps", e);
        }
    }
    
    /**
     * Check if an app is locked
     */
    public boolean isAppLocked(String packageName) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            Set<String> lockedApps = prefs.getStringSet(PREF_LOCKED_APPS, new HashSet<>());
            boolean isLocked = lockedApps != null && lockedApps.contains(packageName);
            Log.d(TAG, "Checking if " + packageName + " is locked: " + isLocked);
            return isLocked;
        } catch (Exception e) {
            Log.e(TAG, "Error checking if app is locked", e);
            return false;
        }
    }
    
    /**
     * Get all locked apps
     */
    public Set<String> getLockedApps() {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            Set<String> lockedApps = prefs.getStringSet(PREF_LOCKED_APPS, new HashSet<>());
            return lockedApps != null ? new HashSet<>(lockedApps) : new HashSet<>();
        } catch (Exception e) {
            Log.e(TAG, "Error getting locked apps", e);
            return new HashSet<>();
        }
    }
    
    /**
     * Get list of installed user apps (non-system)
     */
    public List<ApplicationInfo> getUserInstalledApps() {
        List<ApplicationInfo> userApps = new ArrayList<>();
        PackageManager pm = context.getPackageManager();
        
        try {
            List<ApplicationInfo> installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            
            for (ApplicationInfo app : installedApps) {
                // Filter out system apps and our own app
                if ((app.flags & ApplicationInfo.FLAG_SYSTEM) == 0 &&
                    !app.packageName.equals(context.getPackageName())) {
                    userApps.add(app);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting installed apps", e);
        }
        
        return userApps;
    }
    
    /**
     * Lock the device (requires admin permissions)
     */
    public void lockDevice() {
        if (isAdminActive()) {
            // Add a small delay to make sure any UI interactions complete
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    dpm.lockNow();
                } catch (SecurityException e) {
                    Log.e(TAG, "Failed to lock device", e);
                }
            }, 100);
        }
    }
} 