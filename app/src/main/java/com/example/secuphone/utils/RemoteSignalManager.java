package com.example.secuphone.utils;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages remote signal functionality
 */
public class RemoteSignalManager {
    private static final String TAG = "RemoteSignalManager";
    private static final String DB_SIGNALS_PATH = "signals";
    private static final String DB_URL = "https://secuphone-f2660-default-rtdb.firebaseio.com/";

    private static RemoteSignalManager instance;
    private final FirebaseDatabase database;
    private final DatabaseReference signalsRef;
    private ValueEventListener signalListener;

    /**
     * Interface for signal command callbacks
     */
    public interface SignalCommandListener {
        void onSignalCommand(int durationSeconds, int volumeLevel);
        void onSignalStop();
    }

    private RemoteSignalManager() {
        try {
            // Use explicit database URL to ensure correct instance
            database = FirebaseDatabase.getInstance(DB_URL);
            signalsRef = database.getReference(DB_SIGNALS_PATH);
            
            // Remove the persistence enabled call since it's now in the Application class
            // database.setPersistenceEnabled(true);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Firebase database", e);
            throw new RuntimeException("Failed to initialize Firebase database", e);
        }
    }

    /**
     * Get the singleton instance
     */
    public static synchronized RemoteSignalManager getInstance() {
        if (instance == null) {
            instance = new RemoteSignalManager();
        }
        return instance;
    }

    /**
     * Check if user is authenticated
     * @return true if user is logged in, false otherwise
     */
    private boolean isUserAuthenticated() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        return currentUser != null;
    }

    /**
     * Send signal command to a remote device
     *
     * @param deviceId       Target device ID
     * @param durationSeconds Signal duration in seconds
     * @param volumeLevel    Volume level (0-100)
     * @param listener       Callback for operation result
     */
    public void sendSignalCommand(String deviceId, int durationSeconds, int volumeLevel, 
                                 OnCompleteListener<Void> listener) {
        // Validate parameters
        if (deviceId == null || deviceId.isEmpty()) {
            Log.e(TAG, "Cannot send signal: Invalid device ID");
            if (listener != null) {
                listener.onComplete(Tasks.forException(new IllegalArgumentException("Invalid device ID")));
            }
            return;
        }
        
        // Check if user is authenticated
        if (!isUserAuthenticated()) {
            Log.e(TAG, "Cannot send signal: User not authenticated");
            if (listener != null) {
                listener.onComplete(Tasks.forException(new SecurityException("User not authenticated")));
            }
            return;
        }

        try {
            Map<String, Object> signalData = new HashMap<>();
            signalData.put("type", "start");
            signalData.put("durationSeconds", durationSeconds);
            signalData.put("volumeLevel", volumeLevel);
            signalData.put("timestamp", System.currentTimeMillis());
            signalData.put("senderUid", FirebaseAuth.getInstance().getCurrentUser().getUid());

            DatabaseReference deviceSignalRef = signalsRef.child(deviceId);
            deviceSignalRef.setValue(signalData)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Signal command sent successfully to device: " + deviceId);
                        if (listener != null) {
                            listener.onComplete(Tasks.forResult(null));
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to send signal command", e);
                        if (listener != null) {
                            listener.onComplete(Tasks.forException(e));
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error sending signal command", e);
            if (listener != null) {
                listener.onComplete(Tasks.forException(e));
            }
        }
    }

    /**
     * Send stop signal command to a remote device
     *
     * @param deviceId Target device ID
     * @param listener Callback for operation result
     */
    public void sendStopSignalCommand(String deviceId, OnCompleteListener<Void> listener) {
        // Validate parameters
        if (deviceId == null || deviceId.isEmpty()) {
            Log.e(TAG, "Cannot send stop signal: Invalid device ID");
            if (listener != null) {
                listener.onComplete(Tasks.forException(new IllegalArgumentException("Invalid device ID")));
            }
            return;
        }
        
        // Check if user is authenticated
        if (!isUserAuthenticated()) {
            Log.e(TAG, "Cannot send stop signal: User not authenticated");
            if (listener != null) {
                listener.onComplete(Tasks.forException(new SecurityException("User not authenticated")));
            }
            return;
        }

        try {
            Map<String, Object> signalData = new HashMap<>();
            signalData.put("type", "stop");
            signalData.put("timestamp", System.currentTimeMillis());
            signalData.put("senderUid", FirebaseAuth.getInstance().getCurrentUser().getUid());

            DatabaseReference deviceSignalRef = signalsRef.child(deviceId);
            deviceSignalRef.setValue(signalData)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Stop signal command sent successfully to device: " + deviceId);
                        if (listener != null) {
                            listener.onComplete(Tasks.forResult(null));
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to send stop signal command", e);
                        if (listener != null) {
                            listener.onComplete(Tasks.forException(e));
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error sending stop signal command", e);
            if (listener != null) {
                listener.onComplete(Tasks.forException(e));
            }
        }
    }

    /**
     * Listen for signal commands for this device
     *
     * @param deviceId Target device ID (this device)
     * @param listener Callback for signal commands
     */
    public void listenForSignalCommands(String deviceId, final SignalCommandListener listener) {
        // Validate parameters
        if (deviceId == null || deviceId.isEmpty()) {
            Log.e(TAG, "Cannot listen for signals: Invalid device ID");
            return;
        }
        
        if (listener == null) {
            Log.e(TAG, "Cannot listen for signals: Listener is null");
            return;
        }

        try {
            DatabaseReference deviceSignalRef = signalsRef.child(deviceId);

            // Remove previous listener if exists
            if (signalListener != null) {
                deviceSignalRef.removeEventListener(signalListener);
            }

            signalListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (!dataSnapshot.exists()) {
                        return;
                    }

                    try {
                        String type = dataSnapshot.child("type").getValue(String.class);
                        if (type == null) {
                            return;
                        }

                        if (type.equals("start")) {
                            // Use safe getValue method with default values
                            Integer durationSeconds = getValue(dataSnapshot, "durationSeconds", 30);
                            Integer volumeLevel = getValue(dataSnapshot, "volumeLevel", 100);
                            listener.onSignalCommand(durationSeconds, volumeLevel);
                        } else if (type.equals("stop")) {
                            listener.onSignalStop();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing signal command", e);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.e(TAG, "Error listening for signal commands", databaseError.toException());
                }
            };

            deviceSignalRef.addValueEventListener(signalListener);
            Log.d(TAG, "Started listening for signal commands for device: " + deviceId);
        } catch (Exception e) {
            Log.e(TAG, "Error setting up signal command listener", e);
        }
    }

    /**
     * Stop listening for signal commands
     *
     * @param deviceId Device ID
     */
    public void stopListeningForSignalCommands(String deviceId) {
        try {
            if (signalListener != null && deviceId != null && !deviceId.isEmpty()) {
                DatabaseReference deviceSignalRef = signalsRef.child(deviceId);
                deviceSignalRef.removeEventListener(signalListener);
                signalListener = null;
                Log.d(TAG, "Stopped listening for signal commands for device: " + deviceId);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping signal command listener", e);
        }
    }
    
    /**
     * Safely get a value from a DataSnapshot with a default value if not found
     */
    private <T> T getValue(DataSnapshot dataSnapshot, String key, T defaultValue) {
        if (!dataSnapshot.hasChild(key)) {
            return defaultValue;
        }
        
        try {
            T value = (T) dataSnapshot.child(key).getValue();
            return value != null ? value : defaultValue;
        } catch (Exception e) {
            Log.e(TAG, "Error getting value for key: " + key, e);
            return defaultValue;
        }
    }
} 