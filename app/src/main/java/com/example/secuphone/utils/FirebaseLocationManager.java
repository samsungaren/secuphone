package com.example.secuphone.utils;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.secuphone.models.LocationData;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

/**
 * Utility class to manage Firebase database operations for location tracking
 */
public class FirebaseLocationManager {
    private static final String TAG = "FirebaseLocationManager";
    private static final String LOCATIONS_PATH = "locations";
    private static final String LOST_DEVICES_PATH = "lostDevices";
    
    private static FirebaseLocationManager instance;
    private final DatabaseReference locationsRef;
    private final DatabaseReference lostDevicesRef;
    private String currentUserId;
    
    private FirebaseLocationManager() {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        this.locationsRef = database.getReference(LOCATIONS_PATH);
        this.lostDevicesRef = database.getReference(LOST_DEVICES_PATH);
        
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            this.currentUserId = currentUser.getUid();
        }
    }
    
    public static synchronized FirebaseLocationManager getInstance() {
        if (instance == null) {
            instance = new FirebaseLocationManager();
        }
        return instance;
    }
    
    /**
     * Update the current user ID when the user logs in
     */
    public void updateUserId(String userId) {
        this.currentUserId = userId;
    }
    
    /**
     * Updates location data in Firebase
     */
    public void updateLocationData(String deviceId, LocationData locationData, OnCompleteListener<Void> listener) {
        if (currentUserId == null) {
            Log.e(TAG, "Cannot update location data: user not logged in");
            return;
        }
        
        DatabaseReference userLocationRef = locationsRef.child(currentUserId).child(deviceId);
        userLocationRef.setValue(locationData.toMap())
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Location data updated successfully");
                    } else {
                        Log.e(TAG, "Failed to update location data", task.getException());
                    }
                    
                    if (listener != null) {
                        listener.onComplete(task);
                    }
                });
    }
    
    /**
     * Mark a device as lost or found
     */
    public void setDeviceLostStatus(String deviceId, boolean isLost, OnCompleteListener<Void> listener) {
        if (currentUserId == null) {
            Log.e(TAG, "Cannot update device status: user not logged in");
            return;
        }
        
        Task<Void> task;
        if (isLost) {
            // Add to lost devices list
            task = lostDevicesRef.child(currentUserId).child(deviceId).setValue(true);
            
            // Update isLost flag in location data
            locationsRef.child(currentUserId).child(deviceId).child("isLost").setValue(true);
        } else {
            // Remove from lost devices list
            task = lostDevicesRef.child(currentUserId).child(deviceId).removeValue();
            
            // Update isLost flag in location data
            locationsRef.child(currentUserId).child(deviceId).child("isLost").setValue(false);
        }
        
        task.addOnCompleteListener(listener);
    }
    
    /**
     * Listen for location updates for a specific device
     */
    public void listenForLocationUpdates(String deviceId, ValueEventListener listener) {
        if (currentUserId == null) {
            Log.e(TAG, "Cannot listen for updates: user not logged in");
            return;
        }
        
        locationsRef.child(currentUserId).child(deviceId).addValueEventListener(listener);
    }
    
    /**
     * Stop listening for location updates
     */
    public void stopListeningForUpdates(String deviceId, ValueEventListener listener) {
        if (currentUserId == null) {
            return;
        }
        
        locationsRef.child(currentUserId).child(deviceId).removeEventListener(listener);
    }
    
    /**
     * Get the last known location for a device
     */
    public void getLastKnownLocation(String deviceId, OnCompleteListener<DataSnapshot> listener) {
        if (currentUserId == null) {
            Log.e(TAG, "Cannot get location: user not logged in");
            if (listener != null) {
                listener.onComplete(null);
            }
            return;
        }
        
        locationsRef.child(currentUserId).child(deviceId).get().addOnCompleteListener(listener);
    }
    
    /**
     * Check if a device is currently marked as lost
     */
    public void isDeviceLost(String deviceId, OnCompleteListener<DataSnapshot> listener) {
        if (currentUserId == null) {
            Log.e(TAG, "Cannot check device status: user not logged in");
            if (listener != null) {
                listener.onComplete(null);
            }
            return;
        }
        
        lostDevicesRef.child(currentUserId).child(deviceId).get().addOnCompleteListener(listener);
    }
    
    /**
     * Get a list of all devices registered for the current user
     */
    public void getUserDevices(OnCompleteListener<DataSnapshot> listener) {
        if (currentUserId == null) {
            Log.e(TAG, "Cannot get user devices: user not logged in");
            if (listener != null) {
                listener.onComplete(null);
            }
            return;
        }
        
        locationsRef.child(currentUserId).get().addOnCompleteListener(listener);
    }
} 