package com.example.secuphone.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import java.util.UUID;

/**
 * Utility class to get device information and identifiers
 */
public class DeviceUtils {
    private static final String TAG = "DeviceUtils";
    private static final String PREF_NAME = "device_prefs";
    private static final String DEVICE_ID_KEY = "device_id";
    private static final String DEVICE_NAME_KEY = "device_name";
    
    /**
     * Get a unique device identifier, or create one if it doesn't exist
     */
    public static String getDeviceId(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        
        String deviceId = preferences.getString(DEVICE_ID_KEY, null);
        if (deviceId == null) {
            // Generate a new device ID
            deviceId = generateDeviceId(context);
            
            // Save it for future use
            preferences.edit().putString(DEVICE_ID_KEY, deviceId).apply();
        }
        
        return deviceId;
    }
    
    /**
     * Generate a unique device identifier using a mix of Android ID and UUID
     */
    @SuppressLint("HardwareIds")
    private static String generateDeviceId(Context context) {
        String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        
        // In case of a problematic Android ID, use a random UUID instead
        if (androidId == null || androidId.isEmpty() || androidId.equals("9774d56d682e549c")) {
            return UUID.randomUUID().toString();
        }
        
        // Combine Android ID with a random UUID for extra uniqueness
        return androidId + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * Get or set a custom device name
     */
    public static String getDeviceName(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        
        String deviceName = preferences.getString(DEVICE_NAME_KEY, null);
        if (deviceName == null) {
            // Use default device model name
            deviceName = getDeviceModelName();
            
            // Save it for future use
            preferences.edit().putString(DEVICE_NAME_KEY, deviceName).apply();
        }
        
        return deviceName;
    }
    
    /**
     * Set a custom device name
     */
    public static void setDeviceName(Context context, String deviceName) {
        if (deviceName == null || deviceName.isEmpty()) {
            return;
        }
        
        SharedPreferences preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        preferences.edit().putString(DEVICE_NAME_KEY, deviceName).apply();
    }
    
    /**
     * Get the device model name
     */
    public static String getDeviceModelName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        
        if (model.startsWith(manufacturer)) {
            return capitalize(model);
        } else {
            return capitalize(manufacturer) + " " + model;
        }
    }
    
    /**
     * Get the current battery level as a percentage string
     */
    public static String getBatteryLevel(Context context) {
        IntentFilter iFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, iFilter);
        
        int level = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : -1;
        int scale = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1) : -1;
        
        float batteryPercentage = level * 100 / (float) scale;
        return String.format("%.0f%%", batteryPercentage);
    }
    
    /**
     * Helper method to capitalize the first letter of a string
     */
    private static String capitalize(String s) {
        if (s == null || s.length() == 0) {
            return "";
        }
        
        char first = s.charAt(0);
        if (Character.isUpperCase(first)) {
            return s;
        } else {
            return Character.toUpperCase(first) + s.substring(1);
        }
    }
} 