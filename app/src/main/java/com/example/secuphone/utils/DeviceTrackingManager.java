package com.example.secuphone.utils;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.secuphone.models.DeviceInfo;
import com.example.secuphone.models.LocationData;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Utility class to manage tracking of multiple devices under the same account
 */
public class DeviceTrackingManager {
    private static final String TAG = "DeviceTrackingManager";
    private static final String SIGNALS_PATH = "signals";
    
    private static DeviceTrackingManager instance;
    private final DatabaseReference signalsRef;
    private String currentUserId;
    private final Map<String, ValueEventListener> deviceListeners = new HashMap<>();
    private final Map<String, DeviceInfo> deviceInfoMap = new HashMap<>();
    private DeviceUpdateListener deviceUpdateListener;
    private final long OFFLINE_THRESHOLD_MS = TimeUnit.MINUTES.toMillis(1); // 1 minute timeout
    
    private DeviceTrackingManager() {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        this.signalsRef = database.getReference(SIGNALS_PATH);
        
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            this.currentUserId = currentUser.getUid();
        }
    }
    
    public static synchronized DeviceTrackingManager getInstance() {
        if (instance == null) {
            instance = new DeviceTrackingManager();
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
     * Start tracking all devices under the current user's account
     */
    public void startTrackingAllDevices(DeviceUpdateListener listener) {
        if (currentUserId == null) {
            Log.e(TAG, "Cannot track devices: user not logged in");
            return;
        }
        
        this.deviceUpdateListener = listener;
        
        // Clear existing listeners
        for (Map.Entry<String, ValueEventListener> entry : deviceListeners.entrySet()) {
            signalsRef.child(currentUserId).child(entry.getKey()).removeEventListener(entry.getValue());
        }
        deviceListeners.clear();
        
        // Listen for all devices under the current user
        DatabaseReference userSignalsRef = signalsRef.child(currentUserId);
        userSignalsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // First get all devices
                if (snapshot.exists()) {
                    for (DataSnapshot deviceSnapshot : snapshot.getChildren()) {
                        String deviceId = deviceSnapshot.getKey();
                        if (deviceId != null) {
                            startTrackingDevice(deviceId);
                        }
                    }
                }
                
                // Special case: if no devices are found, check if this device is registered
                if (!snapshot.hasChildren()) {
                    String deviceId = android.provider.Settings.Secure.ANDROID_ID;
                    startTrackingDevice(deviceId);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error getting devices", error.toException());
            }
        });
    }
    
    /**
     * Start tracking a specific device
     */
    public void startTrackingDevice(final String deviceId) {
        if (currentUserId == null) {
            Log.e(TAG, "Cannot track device: user not logged in");
            return;
        }
        
        if (deviceListeners.containsKey(deviceId)) {
            // Already tracking this device
            return;
        }
        
        DatabaseReference deviceRef = signalsRef.child(currentUserId).child(deviceId);
        ValueEventListener deviceListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    try {
                        Map<String, Object> deviceData = (Map<String, Object>) snapshot.getValue();
                        if (deviceData != null) {
                            DeviceInfo deviceInfo = new DeviceInfo();
                            deviceInfo.setDeviceId(deviceId);
                            
                            // Get location data
                            if (deviceData.containsKey("latitude")) {
                                deviceInfo.setLatitude(Double.parseDouble(deviceData.get("latitude").toString()));
                            }
                            if (deviceData.containsKey("longitude")) {
                                deviceInfo.setLongitude(Double.parseDouble(deviceData.get("longitude").toString()));
                            }
                            if (deviceData.containsKey("device_name")) {
                                deviceInfo.setDeviceName(deviceData.get("device_name").toString());
                            }
                            if (deviceData.containsKey("last_seen")) {
                                deviceInfo.setLastSeen(Long.parseLong(deviceData.get("last_seen").toString()));
                                // Check if device is online based on last_seen timestamp
                                long now = System.currentTimeMillis();
                                deviceInfo.setOnline((now - deviceInfo.getLastSeen()) < OFFLINE_THRESHOLD_MS);
                            }
                            
                            deviceInfoMap.put(deviceId, deviceInfo);
                            
                            // Notify listener about updated devices
                            if (deviceUpdateListener != null) {
                                deviceUpdateListener.onDevicesUpdated(new ArrayList<>(deviceInfoMap.values()));
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing device data", e);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error tracking device: " + deviceId, error.toException());
            }
        };
        
        deviceRef.addValueEventListener(deviceListener);
        deviceListeners.put(deviceId, deviceListener);
    }
    
    /**
     * Stop tracking all devices
     */
    public void stopTrackingAllDevices() {
        if (currentUserId == null) {
            return;
        }
        
        for (Map.Entry<String, ValueEventListener> entry : deviceListeners.entrySet()) {
            signalsRef.child(currentUserId).child(entry.getKey()).removeEventListener(entry.getValue());
        }
        
        deviceListeners.clear();
        deviceInfoMap.clear();
        deviceUpdateListener = null;
    }
    
    /**
     * Stop tracking a specific device
     */
    public void stopTrackingDevice(String deviceId) {
        if (currentUserId == null) {
            return;
        }
        
        ValueEventListener listener = deviceListeners.get(deviceId);
        if (listener != null) {
            signalsRef.child(currentUserId).child(deviceId).removeEventListener(listener);
            deviceListeners.remove(deviceId);
            deviceInfoMap.remove(deviceId);
            
            // Notify listener about updated devices
            if (deviceUpdateListener != null) {
                deviceUpdateListener.onDevicesUpdated(new ArrayList<>(deviceInfoMap.values()));
            }
        }
    }
    
    /**
     * Update location for the current device
     */
    public void updateDeviceLocation(String deviceId, double latitude, double longitude, 
                                   String deviceName, long timestamp) {
        if (currentUserId == null) {
            Log.e(TAG, "Cannot update location: user not logged in");
            return;
        }
        
        Map<String, Object> locationUpdate = new HashMap<>();
        locationUpdate.put("latitude", latitude);
        locationUpdate.put("longitude", longitude);
        locationUpdate.put("last_seen", timestamp);
        locationUpdate.put("device_name", deviceName);
        
        signalsRef.child(currentUserId).child(deviceId).updateChildren(locationUpdate)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Location updated for device: " + deviceId))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update location for device: " + deviceId, e));
    }
    
    /**
     * Check if any devices are being tracked
     */
    public boolean isTrackingDevices() {
        return !deviceListeners.isEmpty();
    }
    
    /**
     * Get all tracked devices
     */
    public List<DeviceInfo> getAllDevices() {
        return new ArrayList<>(deviceInfoMap.values());
    }
    
    /**
     * Get device info for a specific device
     */
    public DeviceInfo getDeviceInfo(String deviceId) {
        return deviceInfoMap.get(deviceId);
    }
    
    /**
     * Interface for device update callbacks
     */
    public interface DeviceUpdateListener {
        void onDevicesUpdated(List<DeviceInfo> devices);
    }
} 