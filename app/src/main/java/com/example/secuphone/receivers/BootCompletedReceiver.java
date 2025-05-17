package com.example.secuphone.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.secuphone.admin.RemoteLockDeviceAdmin;
import com.example.secuphone.utils.RemoteLockManager;

/**
 * Broadcast receiver that starts necessary services when device boots up
 */
public class BootCompletedReceiver extends BroadcastReceiver {
    private static final String TAG = "BootCompletedReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "Device boot completed, starting services");
            
            // Check if device admin is active
            if (RemoteLockDeviceAdmin.isAdminActive(context)) {
                // Start the Remote Lock Service
                RemoteLockManager.getInstance(context).startRemoteLockService();
                Log.d(TAG, "Remote Lock Service started after boot");
            }
            
            // Could add other services to start here
        }
    }
} 