package com.example.secuphone_bycoursor.admin;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import com.example.secuphone_bycoursor.R;

public class SecuPhoneDeviceAdmin extends DeviceAdminReceiver {
    
    private static final String TAG = "AppLockDeviceAdmin";
    private static final String PREF_ADMIN_ACTIVE = "admin_active";
    
    /**
     * Called when the user enables device admin for this app
     */
    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        // Store the fact that admin is enabled
        setAdminActive(context, true);
        Toast.makeText(context, R.string.admin_enabled_success, Toast.LENGTH_SHORT).show();
    }
    
    /**
     * Called when the user disables device admin for this app
     */
    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        // Update preferences to reflect admin is disabled
        setAdminActive(context, false);
        Toast.makeText(context, R.string.admin_disabled_message, Toast.LENGTH_SHORT).show();
    }
    
    /**
     * Save the admin active state to preferences
     */
    private void setAdminActive(Context context, boolean active) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putBoolean(PREF_ADMIN_ACTIVE, active).apply();
    }
    
    /**
     * Check if admin is active
     */
    public static boolean isAdminActive(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(PREF_ADMIN_ACTIVE, false);
    }
} 