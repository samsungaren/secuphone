package com.example.secuphone.admin;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.example.secuphone.R;
import com.example.secuphone.services.RemoteLockService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;

/**
 * DeviceAdminReceiver for Remote Lock functionality.
 * This class handles device admin events specifically for remote locking features.
 */
public class RemoteLockDeviceAdmin extends DeviceAdminReceiver {

    private static final String TAG = "RemoteLockDeviceAdmin";
    private static final String PREFS_NAME = "RemoteLockPrefs";
    private static final String ADMIN_ACTIVE_KEY = "admin_active";

    /**
     * Called when the device admin is enabled
     */
    @Override
    public void onEnabled(@NonNull Context context, @NonNull Intent intent) {
        super.onEnabled(context, intent);
        Log.i(TAG, "Device admin enabled");
        
        // Store admin status
        setAdminActive(context, true);
        
        // Show toast indicating admin is enabled
        Toast.makeText(context, R.string.remote_lock_admin_enabled, Toast.LENGTH_SHORT).show();
        
        // Update Firebase status if user is logged in
        updateFirebaseStatus(context, true);
        
        // Start the Remote Lock Service
        try {
            Intent serviceIntent = new Intent(context, RemoteLockService.class);
            context.startService(serviceIntent);
        } catch (Exception e) {
            Log.e(TAG, "Error starting Remote Lock Service", e);
        }
    }

    /**
     * Called when the device admin is disabled
     */
    @Override
    public void onDisabled(@NonNull Context context, @NonNull Intent intent) {
        super.onDisabled(context, intent);
        Log.i(TAG, "Device admin disabled");
        
        // Store admin status
        setAdminActive(context, false);
        
        // Show toast indicating admin is disabled
        Toast.makeText(context, R.string.remote_lock_admin_disabled, Toast.LENGTH_SHORT).show();
        
        // Update Firebase status if user is logged in
        updateFirebaseStatus(context, false);
        
        // Stop the Remote Lock Service
        try {
            Intent serviceIntent = new Intent(context, RemoteLockService.class);
            context.stopService(serviceIntent);
        } catch (Exception e) {
            Log.e(TAG, "Error stopping Remote Lock Service", e);
        }
    }

    /**
     * Called after a password change
     */
    @Override
    public void onPasswordChanged(@NonNull Context context, @NonNull Intent intent) {
        super.onPasswordChanged(context, intent);
        // Could add password policy enforcement here
    }

    /**
     * Called when the device is locked by admin
     */
    @Override
    public void onLockTaskModeEntering(@NonNull Context context, @NonNull Intent intent, @NonNull String pkg) {
        super.onLockTaskModeEntering(context, intent, pkg);
        Toast.makeText(context, R.string.device_locked_remotely, Toast.LENGTH_LONG).show();
    }

    /**
     * Called when the device admin is disabled
     */
    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        // Message shown to user when they attempt to disable device admin
        return context.getString(R.string.remote_lock_explanation);
    }

    /**
     * Store admin active status in shared preferences
     */
    private void setAdminActive(Context context, boolean active) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putBoolean(ADMIN_ACTIVE_KEY, active);
        editor.apply();
    }

    /**
     * Update Firebase with the current admin status
     */
    private void updateFirebaseStatus(Context context, boolean adminActive) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            String userId = currentUser.getUid();
            
            // Get device ID
            String deviceId = android.provider.Settings.Secure.getString(
                    context.getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
            
            // Update admin status in Firebase
            FirebaseDatabase.getInstance()
                    .getReference()
                    .child("devices")
                    .child(userId)
                    .child(deviceId)
                    .child("admin_active")
                    .setValue(adminActive)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "Updated admin status in Firebase: " + adminActive);
                        } else {
                            Log.e(TAG, "Error updating admin status in Firebase", task.getException());
                        }
                    });
        }
    }

    /**
     * Check if device admin is active
     */
    public static boolean isAdminActive(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(ADMIN_ACTIVE_KEY, false);
    }
} 