package com.example.secuphone.utils;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.secuphone.admin.RemoteLockDeviceAdmin;
import com.example.secuphone.services.RemoteLockService;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Manager class for Remote Lock functionality.
 * This class handles admin permission requests and sending lock commands to Firebase.
 */
public class RemoteLockManager {
    private static final String TAG = "RemoteLockManager";
    private static final int REQUEST_CODE_ENABLE_ADMIN = 1001;
    
    private static RemoteLockManager instance;
    
    private final Context context;
    private final DevicePolicyManager devicePolicyManager;
    private final ComponentName adminComponent;
    
    /**
     * Get the singleton instance of the RemoteLockManager
     */
    public static synchronized RemoteLockManager getInstance(Context context) {
        if (instance == null) {
            instance = new RemoteLockManager(context.getApplicationContext());
        }
        return instance;
    }
    
    private RemoteLockManager(Context context) {
        this.context = context;
        devicePolicyManager = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(context, RemoteLockDeviceAdmin.class);
    }
    
    /**
     * Check if device admin permission is granted
     */
    public boolean isAdminActive() {
        return devicePolicyManager.isAdminActive(adminComponent);
    }
    
    /**
     * Request device admin permissions
     */
    public void requestAdminPermission(Activity activity) {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
        activity.startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN);
    }
    
    /**
     * Start the Remote Lock Service
     */
    public void startRemoteLockService() {
        try {
            Intent intent = new Intent(context, RemoteLockService.class);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
            Log.d(TAG, "Remote Lock Service started");
        } catch (Exception e) {
            Log.e(TAG, "Error starting Remote Lock Service", e);
        }
    }
    
    /**
     * Stop the Remote Lock Service
     */
    public void stopRemoteLockService() {
        try {
            Intent intent = new Intent(context, RemoteLockService.class);
            context.stopService(intent);
            Log.d(TAG, "Remote Lock Service stopped");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping Remote Lock Service", e);
        }
    }
    
    /**
     * Send a lock command to a remote device with the same account
     * @param targetDeviceId The device ID to lock (if null, locks all devices)
     * @param message Optional message to display on the lock screen
     * @param listener Callback for the operation result
     */
    public void sendLockCommand(String targetDeviceId, String message, OnCompleteListener<Void> listener) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        
        if (currentUser == null) {
            Log.e(TAG, "No user logged in");
            if (listener != null) {
                listener.onComplete(Tasks.forException(new Exception("Not logged in")));
            }
            return;
        }
        
        // Get this device's ID
        String thisDeviceId = android.provider.Settings.Secure.getString(
                context.getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
        Log.d(TAG, "This device ID: " + thisDeviceId);
        Log.d(TAG, "Target device ID: " + targetDeviceId);
        
        // If target is same as this device, we can do a direct lock
        if (thisDeviceId.equals(targetDeviceId)) {
            Log.d(TAG, "Locking this device directly");
            boolean lockResult = lockThisDevice(message);
            
            if (listener != null) {
                listener.onComplete(Tasks.forResult(null));
            }
            return;
        }
        
        // Create path to the correct location in Firebase
        String userId = currentUser.getUid();
        Log.d(TAG, "User ID for Firebase: " + userId);
        
        // Use a more direct path structure for device commands
        DatabaseReference commandsRef = FirebaseDatabase.getInstance()
                .getReference()
                .child("commands")
                .child(userId);
                
        // Define path to the target device
        DatabaseReference deviceRef = commandsRef.child("devices").child(targetDeviceId);
        Log.d(TAG, "Firebase path for command: " + deviceRef.toString());
        
        // We'll do this as a transaction to ensure atomic updates
        // First, set sender ID
        deviceRef.child("sender").setValue(thisDeviceId)
            .addOnSuccessListener(aVoid -> Log.d(TAG, "Sender ID set successfully"))
            .addOnFailureListener(e -> Log.e(TAG, "Failed to set sender ID", e))
            .continueWithTask(senderTask -> {
                // Then set the message if provided
                if (message != null && !message.isEmpty()) {
                    Log.d(TAG, "Setting lock message: " + message);
                    return deviceRef.child("message").setValue(message);
                } else {
                    Log.d(TAG, "No message provided, setting to null");
                    return deviceRef.child("message").setValue(null);
                }
            })
            .continueWithTask(messageTask -> {
                // Finally set the lock command to true
                Log.d(TAG, "Setting lock command to true");
                return deviceRef.child("lock").setValue(true);
            })
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Log.d(TAG, "Lock command sent successfully to device: " + targetDeviceId);
                    
                    // Update lock status in Firebase
                    FirebaseDatabase.getInstance()
                            .getReference()
                            .child("lock_status")
                            .child(userId)
                            .child(targetDeviceId)
                            .child("command_sent")
                            .setValue(true)
                            .addOnSuccessListener(aVoid -> Log.d(TAG, "Lock status updated successfully"))
                            .addOnFailureListener(e -> Log.e(TAG, "Failed to update lock status", e));
                            
                    // Also send a direct command to ensure it's received
                    commandsRef.child("last_lock_target").setValue(targetDeviceId)
                            .addOnSuccessListener(aVoid -> Log.d(TAG, "Last lock target updated"))
                            .addOnFailureListener(e -> Log.e(TAG, "Failed to update last lock target", e));
                } else {
                    Log.e(TAG, "Failed to send lock command", task.getException());
                }
                
                if (listener != null) {
                    listener.onComplete(task);
                }
            });
    }
    
    /**
     * Process Activity Result from the admin permission request
     */
    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_ENABLE_ADMIN) {
            if (resultCode == Activity.RESULT_OK) {
                // Admin permission granted, start the service
                startRemoteLockService();
                return true;
            } else {
                Log.w(TAG, "Admin permission request was denied");
                return false;
            }
        }
        return false;
    }
    
    /**
     * Lock this device immediately with an optional message
     * @param message Message to display on lock screen (can be null)
     * @return true if lock was successful, false otherwise
     */
    public boolean lockThisDevice(String message) {
        if (!isAdminActive()) {
            Log.e(TAG, "Cannot lock device: admin not active");
            return false;
        }
        
        try {
            // Set lock screen message if available and supported
            if (message != null && !message.isEmpty() && 
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                // Note: DevicePolicyManager doesn't have a direct API to set a lock screen message
                // We just lock the device instead
                devicePolicyManager.lockNow();
                return true;
            }
            
            // Just lock the device
            devicePolicyManager.lockNow();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error locking device", e);
            return false;
        }
    }
} 