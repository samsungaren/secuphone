package com.example.secuphone.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.secuphone.FindPhoneActivity;
import com.example.secuphone.R;
import com.example.secuphone.models.LocationData;
import com.example.secuphone.utils.DeviceUtils;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Locale;

/**
 * Foreground service for tracking device location in the background
 */
public class LocationTrackingService extends Service {
    private static final String TAG = "LocationTrackingService";
    private static final int NOTIFICATION_ID = 12345;
    private static final String CHANNEL_ID = "location_tracking_channel";
    private static final long UPDATE_INTERVAL = 10000; // 10 seconds
    private static final long FASTEST_INTERVAL = 5000; // 5 seconds

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private FirebaseUser currentUser;
    private DatabaseReference locationsRef;
    private boolean isTracking = false;
    private BatteryLevelReceiver batteryReceiver;
    private static boolean isServiceRunning = false;

    /**
     * BroadcastReceiver to monitor battery level changes
     */
    private class BatteryLevelReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                
                if (level != -1 && scale != -1) {
                    float batteryPercentage = level * 100 / (float) scale;
                    String batteryLevel = String.format(Locale.US, "%.0f%%", batteryPercentage);
                    Log.d(TAG, "Battery level updated: " + batteryLevel);
                    
                    // Update battery level in Firebase if location tracking is active
                    if (isTracking && fusedLocationClient != null && currentUser != null) {
                        updateBatteryLevelInFirebase(batteryLevel);
                    }
                }
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate");
        
        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        
        // Configure location request
        locationRequest = new LocationRequest();
        locationRequest.setInterval(UPDATE_INTERVAL);
        locationRequest.setFastestInterval(FASTEST_INTERVAL);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        
        // Create location callback
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    updateLocationInFirebase(location);
                }
            }
        };
        
        // Get current user
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "No user is currently signed in");
            stopSelf();
            return;
        }
        
        // Set up Firebase reference
        locationsRef = FirebaseDatabase.getInstance().getReference("locations").child(currentUser.getUid());
        
        // Create notification channel for foreground service
        createNotificationChannel();
        
        // Register battery level receiver
        batteryReceiver = new BatteryLevelReceiver();
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryReceiver, intentFilter);
        
        isServiceRunning = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service onStartCommand");
        
        if (currentUser == null) {
            Log.e(TAG, "Cannot start service: no user is signed in");
            stopSelf();
            return START_NOT_STICKY;
        }
        
        // Start as a foreground service
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        
        // Start location updates
        startLocationUpdates();
        
        // If killed, restart
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service onDestroy");
        
        // Stop location updates
        stopLocationUpdates();
        
        // Unregister battery receiver
        if (batteryReceiver != null) {
            try {
                unregisterReceiver(batteryReceiver);
                batteryReceiver = null;
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering battery receiver", e);
            }
        }
        
        isServiceRunning = false;
    }
    
    /**
     * Create notification channel for foreground service (required for Android 8.0+)
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.tracking_notification_channel),
                    NotificationManager.IMPORTANCE_LOW);
            
            channel.setDescription(getString(R.string.tracking_notification_channel_description));
            channel.setSound(null, null);
            channel.setShowBadge(false);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    /**
     * Build the notification for the foreground service
     */
    private Notification buildNotification() {
        Intent notificationIntent = new Intent(this, FindPhoneActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.tracking_notification_title))
                .setContentText(getString(R.string.tracking_notification_text))
                .setSmallIcon(R.drawable.ic_shield_check)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
    
    /**
     * Start receiving location updates
     */
    private void startLocationUpdates() {
        if (isTracking) {
            return; // Already tracking
        }
        
        try {
            // Use high accuracy settings
            locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            locationRequest.setFastestInterval(5000); // 5 seconds
            locationRequest.setSmallestDisplacement(10); // 10 meters
            
            fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
            );
            isTracking = true;
            Log.d(TAG, "Location updates started with high accuracy");
        } catch (SecurityException e) {
            Log.e(TAG, "Error starting location updates", e);
        }
    }
    
    /**
     * Stop receiving location updates
     */
    private void stopLocationUpdates() {
        if (!isTracking) {
            return; // Not tracking
        }
        
        fusedLocationClient.removeLocationUpdates(locationCallback);
        isTracking = false;
        Log.d(TAG, "Location updates stopped");
    }
    
    /**
     * Update location data in Firebase
     */
    private void updateLocationInFirebase(Location location) {
        if (location == null || currentUser == null) {
            return;
        }
        
        // Get device ID
        String deviceId = DeviceUtils.getDeviceId(this);
        
        // Get battery level
        String batteryLevel = DeviceUtils.getBatteryLevel(this);
        
        // Create location data
        LocationData locationData = new LocationData(
                location.getLatitude(),
                location.getLongitude(),
                System.currentTimeMillis(),
                deviceId,
                location.getAccuracy(),
                location.getAltitude(),
                location.getSpeed(),
                batteryLevel
        );
        
        // Update Firebase
        locationsRef.child(deviceId).setValue(locationData.toMap())
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Location updated in Firebase"))
                .addOnFailureListener(e -> Log.e(TAG, "Error updating location in Firebase", e));
    }
    
    /**
     * Update only the battery level in Firebase
     */
    private void updateBatteryLevelInFirebase(String batteryLevel) {
        if (currentUser == null) return;
        
        String deviceId = DeviceUtils.getDeviceId(this);
        
        locationsRef.child(deviceId).child("batteryLevel").setValue(batteryLevel)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update battery level", e));
    }
    
    /**
     * Check if service is currently running
     */
    public static boolean isServiceRunning() {
        return isServiceRunning;
    }
} 
