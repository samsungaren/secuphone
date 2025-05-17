package com.example.secuphone.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
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
 * Manager class for device registration and management with Firebase
 */
public class DeviceRegistrationManager {
    private static final String TAG = "DeviceRegistrationMgr";
    private static final String PREFS_NAME = "DeviceRegistrationPrefs";
    private static final String DEVICE_ID_KEY = "device_id";
    private static final String USERS_PATH = "users";
    private static final String DEVICES_PATH = "devices";
    private static final long ONLINE_THRESHOLD_MS = 5 * 60 * 1000; // 5 minutes

    private static DeviceRegistrationManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private String deviceId;
    private DatabaseReference userDevicesRef;
    private ValueEventListener devicesListener;

    /**
     * Get the singleton instance
     */
    public static synchronized DeviceRegistrationManager getInstance(Context context) {
        if (instance == null) {
            instance = new DeviceRegistrationManager(context.getApplicationContext());
        }
        return instance;
    }

    private DeviceRegistrationManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.deviceId = getOrCreateDeviceId();
    }

    /**
     * Get or create a unique device ID
     */
    private String getOrCreateDeviceId() {
        String storedId = prefs.getString(DEVICE_ID_KEY, null);
        if (storedId != null && !storedId.isEmpty()) {
            return storedId;
        }

        // Generate a new device ID based on Android ID
        String androidId = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ANDROID_ID);
        
        // Store it for future use
        prefs.edit().putString(DEVICE_ID_KEY, androidId).apply();
        return androidId;
    }

    /**
     * Register this device with Firebase
     */
    public void registerDevice(OnCompleteListener<Void> listener) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "Cannot register device: User not logged in");
            if (listener != null) {
                listener.onComplete(Tasks.forException(new Exception("User not logged in")));
            }
            return;
        }

        String userId = currentUser.getUid();
        userDevicesRef = FirebaseDatabase.getInstance()
                .getReference()
                .child(USERS_PATH)
                .child(userId)
                .child(DEVICES_PATH);

        // Create device info
        Map<String, Object> deviceInfo = new HashMap<>();
        deviceInfo.put("model", Build.MODEL);
        deviceInfo.put("manufacturer", Build.MANUFACTURER);
        deviceInfo.put("name", Build.DEVICE);
        deviceInfo.put("last_seen", System.currentTimeMillis());
        deviceInfo.put("online", true);

        // Register device
        userDevicesRef.child(deviceId).setValue(deviceInfo)
                .addOnCompleteListener(listener);
    }

    /**
     * Update device online status
     */
    public void updateOnlineStatus() {
        if (userDevicesRef == null) {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser == null) {
                Log.e(TAG, "Cannot update status: User not logged in");
                return;
            }

            String userId = currentUser.getUid();
            userDevicesRef = FirebaseDatabase.getInstance()
                    .getReference()
                    .child(USERS_PATH)
                    .child(userId)
                    .child(DEVICES_PATH);
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("last_seen", System.currentTimeMillis());
        updates.put("online", true);

        userDevicesRef.child(deviceId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Online status updated"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update online status", e));
    }

    /**
     * Get all devices for the current user
     */
    public void getUserDevices(OnCompleteListener<DataSnapshot> listener) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "Cannot get devices: User not logged in");
            if (listener != null) {
                listener.onComplete(Tasks.forException(new Exception("User not logged in")));
            }
            return;
        }

        String userId = currentUser.getUid();
        DatabaseReference userDevicesRef = FirebaseDatabase.getInstance()
                .getReference()
                .child(USERS_PATH)
                .child(userId)
                .child(DEVICES_PATH);

        userDevicesRef.get().addOnCompleteListener(listener);
    }

    /**
     * Start listening for device updates
     */
    public void startListeningForDeviceUpdates(DeviceUpdateListener listener) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "Cannot listen for updates: User not logged in");
            return;
        }

        String userId = currentUser.getUid();
        DatabaseReference userDevicesRef = FirebaseDatabase.getInstance()
                .getReference()
                .child(USERS_PATH)
                .child(userId)
                .child(DEVICES_PATH);

        if (devicesListener != null) {
            userDevicesRef.removeEventListener(devicesListener);
        }

        devicesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (listener != null) {
                    listener.onDevicesUpdated(snapshot);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Device updates listener cancelled", error.toException());
            }
        };

        userDevicesRef.addValueEventListener(devicesListener);
    }

    /**
     * Stop listening for device updates
     */
    public void stopListeningForDeviceUpdates() {
        if (userDevicesRef != null && devicesListener != null) {
            userDevicesRef.removeEventListener(devicesListener);
            devicesListener = null;
        }
    }

    /**
     * Check if a device is online based on last_seen timestamp
     */
    public boolean isDeviceOnline(long lastSeenTimestamp) {
        long now = System.currentTimeMillis();
        return (now - lastSeenTimestamp) < ONLINE_THRESHOLD_MS;
    }

    /**
     * Get the device ID
     */
    public String getDeviceId() {
        return deviceId;
    }

    /**
     * Interface for device update callbacks
     */
    public interface DeviceUpdateListener {
        void onDevicesUpdated(DataSnapshot devicesSnapshot);
    }
} 