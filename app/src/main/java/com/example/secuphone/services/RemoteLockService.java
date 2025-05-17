package com.example.secuphone.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.secuphone.FindPhoneActivity;
import com.example.secuphone.R;
import com.example.secuphone.admin.RemoteLockDeviceAdmin;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

/**
 * Service that listens for remote lock commands from Firebase
 * and locks the device when triggered.
 */
public class RemoteLockService extends Service {
    private static final String TAG = "RemoteLockService";
    private static final String CHANNEL_ID = "RemoteLockChannel";
    private static final int NOTIFICATION_ID = 3002;
    
    private DevicePolicyManager devicePolicyManager;
    private ComponentName adminComponent;
    private ValueEventListener lockCommandListener;
    private ValueEventListener deviceSpecificCommandListener;
    private DatabaseReference commandsRef;
    private DatabaseReference deviceCommandsRef;
    private FirebaseUser currentUser;
    private String deviceId;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Remote Lock Service created");
        
        // Initialize device policy manager
        devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, RemoteLockDeviceAdmin.class);
        
        // Get this device's unique ID
        deviceId = android.provider.Settings.Secure.getString(
                getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
        
        // Create notification channel for foreground service
        createNotificationChannel();
        
        // Start foreground service with notification
        startForeground(NOTIFICATION_ID, createNotification());
        
        // Initialize Firebase and listeners
        startListeningForLockCommands();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Remote Lock Service started");
        return START_STICKY;
    }
    
    /**
     * Start listening for lock commands from Firebase
     */
    private void startListeningForLockCommands() {
        // Get current Firebase user
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        
        if (currentUser == null) {
            Log.e(TAG, "No user logged in, stopping service");
            stopSelf();
            return;
        }
        
        Log.d(TAG, "Starting to listen for lock commands for user: " + currentUser.getUid());
        Log.d(TAG, "This device ID: " + deviceId);
        
        String userId = currentUser.getUid();
        commandsRef = FirebaseDatabase.getInstance()
                .getReference()
                .child("commands")
                .child(userId);
        
        // Listen for general lock commands (for backward compatibility)
        setupGeneralLockListener();
        
        // Listen for device-specific lock commands
        setupDeviceSpecificLockListener();
        
        // Listen for last_lock_target field (additional method to ensure commands are received)
        setupLastLockTargetListener();
    }
    
    /**
     * Setup listener for general lock commands (all devices)
     */
    private void setupGeneralLockListener() {
        if (lockCommandListener != null) {
            commandsRef.child("lock").removeEventListener(lockCommandListener);
        }
        
        lockCommandListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && Boolean.TRUE.equals(snapshot.getValue(Boolean.class))) {
                    Log.d(TAG, "Received general lock command");
                    
                    // Get message if available
                    commandsRef.child("message").addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot messageSnapshot) {
                            final String message;
                            if (messageSnapshot.exists()) {
                                message = messageSnapshot.getValue(String.class);
                                Log.d(TAG, "Lock message: " + message);
                            } else {
                                message = null;
                            }
                            
                            // Get sender to avoid locking self if command was sent by this device
                            commandsRef.child("sender").addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot senderSnapshot) {
                                    String senderId = null;
                                    if (senderSnapshot.exists()) {
                                        senderId = senderSnapshot.getValue(String.class);
                                        Log.d(TAG, "Sender ID: " + senderId);
                                    }
                                    
                                    // Don't lock if the command was sent by this device
                                    if (deviceId.equals(senderId)) {
                                        Log.d(TAG, "Ignoring lock command sent by this device");
                                        return;
                                    }
                                    
                                    // Lock the device
                                    lockDevice(message);
                                    
                                    // Reset the lock command in Firebase
                                    commandsRef.child("lock").setValue(false);
                                }
                                
                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    Log.e(TAG, "Error getting sender ID: " + error.getMessage());
                                }
                            });
                        }
                        
                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Log.e(TAG, "Error getting lock message: " + error.getMessage());
                        }
                    });
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error listening for lock commands: " + error.getMessage());
            }
        };
        
        commandsRef.child("lock").addValueEventListener(lockCommandListener);
    }
    
    /**
     * Setup listener for device-specific lock commands
     */
    private void setupDeviceSpecificLockListener() {
        // Setup path for this specific device
        deviceCommandsRef = commandsRef.child("devices").child(deviceId);
        
        // Remove any existing listener
        if (deviceSpecificCommandListener != null) {
            deviceCommandsRef.child("lock").removeEventListener(deviceSpecificCommandListener);
        }
        
        deviceSpecificCommandListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && Boolean.TRUE.equals(snapshot.getValue(Boolean.class))) {
                    Log.d(TAG, "Received device-specific lock command for this device");
                    
                    // Get message if available
                    try {
                        deviceCommandsRef.child("message").addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                final String lockMessage;
                                if (dataSnapshot.exists()) {
                                    lockMessage = dataSnapshot.getValue(String.class);
                                    Log.d(TAG, "Lock message: " + lockMessage);
                                } else {
                                    lockMessage = null;
                                }
                                
                                // Get sender to avoid locking self if command was sent by this device
                                deviceCommandsRef.child("sender").addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot senderSnapshot) {
                                        String senderId = null;
                                        if (senderSnapshot.exists()) {
                                            senderId = senderSnapshot.getValue(String.class);
                                            Log.d(TAG, "Sender ID: " + senderId);
                                        }
                                        
                                        // Don't lock if the command was sent by this device
                                        if (deviceId.equals(senderId)) {
                                            Log.d(TAG, "Ignoring lock command sent by this device");
                                            return;
                                        }
                                        
                                        // Lock the device
                                        lockDevice(lockMessage);
                                        
                                        // Reset the lock command in Firebase
                                        deviceCommandsRef.child("lock").setValue(false);
                                        
                                        // Send confirmation back to Firebase
                                        FirebaseDatabase.getInstance()
                                                .getReference()
                                                .child("lock_status")
                                                .child(currentUser.getUid())
                                                .child(deviceId)
                                                .child("locked")
                                                .setValue(true);
                                    }
                                    
                                    @Override
                                    public void onCancelled(@NonNull DatabaseError error) {
                                        Log.e(TAG, "Error getting sender ID: " + error.getMessage());
                                    }
                                });
                            }
                            
                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                Log.e(TAG, "Error getting lock message: " + error.getMessage());
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Error setting up message listener: " + e.getMessage());
                    }
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error listening for device-specific lock commands: " + error.getMessage());
            }
        };
        
        deviceCommandsRef.child("lock").addValueEventListener(deviceSpecificCommandListener);
    }
    
    /**
     * Setup listener for the last_lock_target field
     */
    private void setupLastLockTargetListener() {
        commandsRef.child("last_lock_target").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String targetDeviceId = snapshot.getValue(String.class);
                    Log.d(TAG, "Last lock target updated: " + targetDeviceId);
                    
                    // Check if this device is the target
                    if (deviceId.equals(targetDeviceId)) {
                        Log.d(TAG, "This device is the target of a lock command");
                        
                        // Check the specific device path for lock command details
                        commandsRef.child("devices").child(deviceId).child("lock")
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot lockSnapshot) {
                                    if (lockSnapshot.exists() && Boolean.TRUE.equals(lockSnapshot.getValue(Boolean.class))) {
                                        Log.d(TAG, "Confirmed lock command is set to true");
                                        
                                        // Get message and sender from the device path
                                        commandsRef.child("devices").child(deviceId).child("message")
                                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                                @Override
                                                public void onDataChange(@NonNull DataSnapshot messageSnapshot) {
                                                    final String lockMessage = messageSnapshot.exists() ? 
                                                        messageSnapshot.getValue(String.class) : null;
                                                    
                                                    commandsRef.child("devices").child(deviceId).child("sender")
                                                        .addListenerForSingleValueEvent(new ValueEventListener() {
                                                            @Override
                                                            public void onDataChange(@NonNull DataSnapshot senderSnapshot) {
                                                                String senderId = senderSnapshot.exists() ? 
                                                                    senderSnapshot.getValue(String.class) : null;
                                                                
                                                                // Don't lock if command was sent by this device
                                                                if (deviceId.equals(senderId)) {
                                                                    Log.d(TAG, "Ignoring lock command sent by this device");
                                                                    return;
                                                                }
                                                                
                                                                // Lock the device
                                                                Log.d(TAG, "Locking device from last_lock_target listener");
                                                                lockDevice(lockMessage);
                                                                
                                                                // Reset the lock command
                                                                commandsRef.child("devices").child(deviceId).child("lock").setValue(false);
                                                                
                                                                // Send confirmation
                                                                FirebaseDatabase.getInstance()
                                                                    .getReference()
                                                                    .child("lock_status")
                                                                    .child(currentUser.getUid())
                                                                    .child(deviceId)
                                                                    .child("locked")
                                                                    .setValue(true);
                                                            }
                                                            
                                                            @Override
                                                            public void onCancelled(@NonNull DatabaseError error) {
                                                                Log.e(TAG, "Error getting sender ID: " + error.getMessage());
                                                            }
                                                        });
                                                }
                                                
                                                @Override
                                                public void onCancelled(@NonNull DatabaseError error) {
                                                    Log.e(TAG, "Error getting lock message: " + error.getMessage());
                                                }
                                            });
                                    }
                                }
                                
                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    Log.e(TAG, "Error checking lock command: " + error.getMessage());
                                }
                            });
                    }
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error listening for last lock target: " + error.getMessage());
            }
        });
    }
    
    /**
     * Lock the device with DevicePolicyManager
     */
    private void lockDevice(String message) {
        if (!devicePolicyManager.isAdminActive(adminComponent)) {
            Log.e(TAG, "Cannot lock device: device admin is not active");
            return;
        }
        
        try {
            // Create a runnable to lock the device
            Runnable lockRunnable = () -> {
                try {
                    // If Android version supports setting a lock screen message, do it
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && message != null && !message.isEmpty()) {
                        // Use the standard lockNow method - the LockTaskPolicy isn't available in this Android version
                        devicePolicyManager.lockNow();
                        Log.d(TAG, "Device locked successfully with message: " + message);
                    } else {
                        // Just lock with default policy
                        devicePolicyManager.lockNow();
                        Log.d(TAG, "Device locked successfully with standard policy");
                    }
                    
                    // Update lock status in shared preferences (for UI updates)
                    getSharedPreferences("lock_status", MODE_PRIVATE)
                        .edit()
                        .putBoolean("device_locked", true)
                        .putLong("lock_time", System.currentTimeMillis())
                        .apply();
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error locking device: " + e.getMessage());
                }
            };
            
            // Run on main thread after a short delay to avoid ANR
            new android.os.Handler(getMainLooper()).postDelayed(lockRunnable, 500);
        } catch (Exception e) {
            Log.e(TAG, "Error preparing to lock device: " + e.getMessage());
        }
    }
    
    /**
     * Create the notification channel for Android O+
     */
    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Remote Lock Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Channel for remote lock service notifications");
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
    
    /**
     * Create notification for foreground service
     */
    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, FindPhoneActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.remote_lock_active))
                .setContentText(getString(R.string.remote_lock_service_running))
                .setSmallIcon(R.drawable.ic_lock)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
    
    @Override
    public void onDestroy() {
        Log.d(TAG, "Remote Lock Service destroyed");
        
        // Remove Firebase listeners
        if (commandsRef != null && lockCommandListener != null) {
            commandsRef.child("lock").removeEventListener(lockCommandListener);
        }
        
        if (deviceCommandsRef != null && deviceSpecificCommandListener != null) {
            deviceCommandsRef.child("lock").removeEventListener(deviceSpecificCommandListener);
        }
        
        super.onDestroy();
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
} 