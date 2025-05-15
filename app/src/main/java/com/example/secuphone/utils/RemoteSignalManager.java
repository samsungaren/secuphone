package com.example.secuphone.utils;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
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
        database = FirebaseDatabase.getInstance();
        signalsRef = database.getReference(DB_SIGNALS_PATH);
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
     * Send signal command to a remote device
     *
     * @param deviceId       Target device ID
     * @param durationSeconds Signal duration in seconds
     * @param volumeLevel    Volume level (0-100)
     * @param listener       Callback for operation result
     */
    public void sendSignalCommand(String deviceId, int durationSeconds, int volumeLevel, 
                                 OnCompleteListener<Void> listener) {
        Map<String, Object> signalData = new HashMap<>();
        signalData.put("type", "start");
        signalData.put("durationSeconds", durationSeconds);
        signalData.put("volumeLevel", volumeLevel);
        signalData.put("timestamp", System.currentTimeMillis());

        DatabaseReference deviceSignalRef = signalsRef.child(deviceId);
        deviceSignalRef.setValue(signalData)
                .addOnCompleteListener(listener);
    }

    /**
     * Send stop signal command to a remote device
     *
     * @param deviceId Target device ID
     * @param listener Callback for operation result
     */
    public void sendStopSignalCommand(String deviceId, OnCompleteListener<Void> listener) {
        Map<String, Object> signalData = new HashMap<>();
        signalData.put("type", "stop");
        signalData.put("timestamp", System.currentTimeMillis());

        DatabaseReference deviceSignalRef = signalsRef.child(deviceId);
        deviceSignalRef.setValue(signalData)
                .addOnCompleteListener(listener);
    }

    /**
     * Listen for signal commands for this device
     *
     * @param deviceId Target device ID (this device)
     * @param listener Callback for signal commands
     */
    public void listenForSignalCommands(String deviceId, final SignalCommandListener listener) {
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
                        int durationSeconds = dataSnapshot.child("durationSeconds").getValue(Integer.class);
                        int volumeLevel = dataSnapshot.child("volumeLevel").getValue(Integer.class);
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
    }

    /**
     * Stop listening for signal commands
     *
     * @param deviceId Device ID
     */
    public void stopListeningForSignalCommands(String deviceId) {
        if (signalListener != null) {
            DatabaseReference deviceSignalRef = signalsRef.child(deviceId);
            deviceSignalRef.removeEventListener(signalListener);
            signalListener = null;
        }
    }
} 