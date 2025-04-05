package com.example.secuphone_bycoursor.services;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraManager;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.secuphone_bycoursor.AntiSpyActivity;
import com.example.secuphone_bycoursor.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AntiSpyService extends Service {
    private static final String TAG = "AntiSpyService";
    private static final int NOTIFICATION_ID = 3457;
    private static final String CHANNEL_ID = "anti_spy_channel";
    
    // Preference keys
    private static final String PREF_MIC_PROTECTION = "mic_protection_enabled";
    private static final String PREF_CAMERA_PROTECTION = "camera_protection_enabled";
    private static final String PREF_LOCATION_PROTECTION = "location_protection_enabled";
    private static final String PREF_BLOCKED_APPS = "anti_spy_blocked_apps";
    
    // Service state
    private boolean isMicProtectionEnabled = false;
    private boolean isCameraProtectionEnabled = false;
    private boolean isLocationProtectionEnabled = false;
    
    // Managers
    private AudioManager audioManager;
    private CameraManager cameraManager;
    private LocationManager locationManager;
    private PackageManager packageManager;
    
    // Handler for periodic checks
    private Handler handler = new Handler();
    private Runnable monitorRunnable;
    
    // Store blocked apps
    private Set<String> blockedApps = new HashSet<>();
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Initialize managers
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        packageManager = getPackageManager();
        
        // Set up monitoring runnable
        monitorRunnable = new Runnable() {
            @Override
            public void run() {
                monitorSensitiveHardware();
                handler.postDelayed(this, 2000); // Check every 2 seconds
            }
        };
        
        // Load blocked apps
        loadBlockedApps();
        
        Log.d(TAG, "AntiSpyService created");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "AntiSpyService started");
        
        // Load preferences
        loadPreferences();
        
        // Start foreground service with notification
        startForeground(NOTIFICATION_ID, createNotification());
        
        // Start monitoring
        startMonitoring();
        
        return START_STICKY;
    }
    
    private void loadPreferences() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        isMicProtectionEnabled = prefs.getBoolean(PREF_MIC_PROTECTION, false);
        isCameraProtectionEnabled = prefs.getBoolean(PREF_CAMERA_PROTECTION, false);
        isLocationProtectionEnabled = prefs.getBoolean(PREF_LOCATION_PROTECTION, false);
        
        Log.d(TAG, "Loaded preferences - Mic: " + isMicProtectionEnabled + 
                 ", Camera: " + isCameraProtectionEnabled + 
                 ", Location: " + isLocationProtectionEnabled);
    }
    
    private void loadBlockedApps() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        blockedApps = prefs.getStringSet(PREF_BLOCKED_APPS, new HashSet<>());
    }
    
    private void saveBlockedApp(String packageName) {
        if (!blockedApps.contains(packageName)) {
            blockedApps.add(packageName);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            prefs.edit().putStringSet(PREF_BLOCKED_APPS, blockedApps).apply();
        }
    }
    
    private Notification createNotification() {
        // Create the notification channel (for Android O and above)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Anti-Spy Protection",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Monitors and blocks unauthorized access to hardware");
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
        
        // Create intent for when notification is tapped
        Intent notificationIntent = new Intent(this, AntiSpyActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        
        // Build the notification
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Anti-Spy Protection Active")
                .setContentText(getEnabledProtectionsText())
                .setSmallIcon(R.drawable.ic_shield_check)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }
    
    private String getEnabledProtectionsText() {
        List<String> enabledProtections = new ArrayList<>();
        if (isMicProtectionEnabled) enabledProtections.add("Microphone");
        if (isCameraProtectionEnabled) enabledProtections.add("Camera");
        if (isLocationProtectionEnabled) enabledProtections.add("Location");
        
        if (enabledProtections.isEmpty()) {
            return "No protections enabled";
        } else {
            return "Protecting: " + String.join(", ", enabledProtections);
        }
    }
    
    private void startMonitoring() {
        handler.post(monitorRunnable);
    }
    
    private void stopMonitoring() {
        handler.removeCallbacks(monitorRunnable);
    }
    
    private void monitorSensitiveHardware() {
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> runningApps = activityManager.getRunningAppProcesses();
        List<ActivityManager.RunningServiceInfo> runningServices = activityManager.getRunningServices(Integer.MAX_VALUE);
        
        String foregroundApp = null;
        
        // Get foreground application
        if (runningApps != null) {
            for (ActivityManager.RunningAppProcessInfo processInfo : runningApps) {
                if (processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    foregroundApp = processInfo.processName;
                    break;
                }
            }
        }
        
        // Check if an app is trying to access protected hardware
        if (foregroundApp != null && !foregroundApp.equals(getPackageName())) {
            boolean shouldBlock = false;
            
            // Check microphone access
            if (isMicProtectionEnabled && isMicrophoneInUse()) {
                Log.d(TAG, "Microphone being accessed by: " + foregroundApp);
                shouldBlock = true;
            }
            
            // Check camera access
            if (isCameraProtectionEnabled && isCameraInUse()) {
                Log.d(TAG, "Camera being accessed by: " + foregroundApp);
                shouldBlock = true;
            }
            
            // Check location access
            if (isLocationProtectionEnabled && isLocationInUse()) {
                Log.d(TAG, "Location being accessed by: " + foregroundApp);
                shouldBlock = true;
            }
            
            if (shouldBlock) {
                blockAccess(foregroundApp);
                saveBlockedApp(foregroundApp);
            }
        }
    }
    
    private boolean isMicrophoneInUse() {
        // Check if microphone is muted - if it's not, an app might be using it
        return audioManager.isMicrophoneMute();
    }
    
    private boolean isCameraInUse() {
        // This is a simplified check - a more robust implementation would 
        // use the camera2 API to check if cameras are in use
        try {
            String[] cameraIds = cameraManager.getCameraIdList();
            for (String cameraId : cameraIds) {
                // In a real implementation, we'd need to attempt to open each camera
                // and see if it's already in use
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking camera: " + e.getMessage());
        }
        
        // For simplicity in this prototype, return false
        // A real implementation would need more sophisticated camera state detection
        return false;
    }
    
    private boolean isLocationInUse() {
        // Check if location services are enabled
        boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        
        // For this prototype, we'll assume if location is enabled, it might be in use
        // A real implementation would need to detect actual location requests
        return gpsEnabled || networkEnabled;
    }
    
    private void blockAccess(String packageName) {
        Log.d(TAG, "Blocking access for: " + packageName);
        
        // Mute microphone if it's being protected
        if (isMicProtectionEnabled) {
            audioManager.setMicrophoneMute(true);
        }
        
        // Create a notification to alert user
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel alertChannel = new NotificationChannel(
                    "anti_spy_alert",
                    "Anti-Spy Alerts",
                    NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(alertChannel);
        }
        
        Notification alert = new NotificationCompat.Builder(this, "anti_spy_alert")
                .setContentTitle("Hardware Access Blocked!")
                .setContentText("An app was trying to access protected hardware: " + packageName)
                .setSmallIcon(R.drawable.ic_shield_check)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build();
        
        notificationManager.notify(NOTIFICATION_ID + 1, alert);
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        stopMonitoring();
        Log.d(TAG, "AntiSpyService destroyed");
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
} 