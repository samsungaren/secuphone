package com.example.secuphone_bycoursor.admin;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.secuphone_bycoursor.AppLockActivity;
import com.example.secuphone_bycoursor.LockScreenActivity;
import com.example.secuphone_bycoursor.R;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Background service that monitors current app and shows lock screen when needed
 */
public class LockScreenService extends Service {

    private static final String TAG = "LockScreenService";
    private static final int CHECK_INTERVAL_MS = 500;
    private static final int NOTIFICATION_ID = 1002;
    private static final String NOTIFICATION_CHANNEL_ID = "lock_screen_service";
    
    private String currentForegroundApp = "";
    private String lastForegroundApp = "";
    private AppLockManager appLockManager;
    private ScheduledExecutorService executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    @Override
    public void onCreate() {
        try {
            super.onCreate();
            appLockManager = new AppLockManager(this);
            executor = Executors.newSingleThreadScheduledExecutor();
            
            Log.d(TAG, "Lock Screen Service created");
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
        }
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            Log.d(TAG, "LockScreenService started with intent: " + (intent != null ? intent.toString() : "null"));
            
            // Start as foreground service with notification
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    Notification notification = createNotification();
                    startForeground(NOTIFICATION_ID, notification);
                    Log.d(TAG, "Started as foreground service with notification");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to start as foreground service", e);
                    // Continue without being foreground if it fails
                }
            }
            
            // Start app monitoring
            startMonitoring();
            return START_STICKY;
        } catch (Exception e) {
            Log.e(TAG, "Error in onStartCommand", e);
            return START_NOT_STICKY;
        }
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdownNow();
        }
        Log.d(TAG, "Lock Screen Service destroyed");
    }
    
    /**
     * Create notification for foreground service
     */
    private Notification createNotification() {
        try {
            // Create notification channel for Android O+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        "App Lock Service",
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription("Monitors apps and shows lock screen");
                channel.setShowBadge(false);
                
                NotificationManager manager = getSystemService(NotificationManager.class);
                if (manager != null) {
                    manager.createNotificationChannel(channel);
                }
            }
            
            // Create intent for notification click
            Intent intent = new Intent(this, AppLockActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
            
            // Build notification
            return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(getString(R.string.app_lock_active))
                    .setSmallIcon(R.drawable.ic_lock_closed)
                    .setContentIntent(pendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build();
        } catch (Exception e) {
            Log.e(TAG, "Error creating notification, using fallback", e);
            // Create a fallback simple notification in case of any errors
            return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle("App Lock")
                    .setContentText("App Lock Service Running")
                    .setSmallIcon(android.R.drawable.ic_lock_lock)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build();
        }
    }
    
    /**
     * Start monitoring for app changes
     */
    private void startMonitoring() {
        try {
            Log.d(TAG, "Starting app monitoring");
            
            executor.scheduleWithFixedDelay(() -> {
                try {
                    // Check if PIN is set and admin is active
                    if (!appLockManager.isPinSet() || !appLockManager.isAdminActive()) {
                        Log.d(TAG, "PIN not set or admin not active, skipping check");
                        return;
                    }
                    
                    // Get current foreground app
                    currentForegroundApp = getForegroundApp();
                    
                    // Skip if no app detected or same as last check
                    if (currentForegroundApp == null || currentForegroundApp.equals(lastForegroundApp)) {
                        return;
                    }
                    
                    Log.d(TAG, "Detected foreground app change: " + currentForegroundApp);
                    
                    // Update last app
                    lastForegroundApp = currentForegroundApp;
                    
                    // Check if this app is locked
                    boolean isLocked = appLockManager.isAppLocked(currentForegroundApp);
                    Log.d(TAG, "App " + currentForegroundApp + " is " + (isLocked ? "locked" : "not locked"));
                    
                    if (isLocked) {
                        Log.d(TAG, "Detected locked app: " + currentForegroundApp);
                        showLockScreen(currentForegroundApp);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error during app monitoring", e);
                }
            }, 0, CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting monitoring", e);
        }
    }
    
    /**
     * Get current foreground app package name
     */
    private String getForegroundApp() {
        try {
            // Get usage stats
            UsageStatsManager usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            long endTime = System.currentTimeMillis();
            long beginTime = endTime - 10000; // Last 10 seconds
            
            // Query apps used in last 10 seconds
            List<UsageStats> stats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY, beginTime, endTime);
            
            if (stats == null || stats.isEmpty()) {
                return null;
            }
            
            // Find most recently used app
            SortedMap<Long, UsageStats> sortedMap = new TreeMap<>();
            for (UsageStats usageStats : stats) {
                sortedMap.put(usageStats.getLastTimeUsed(), usageStats);
            }
            
            if (sortedMap.isEmpty()) {
                return null;
            }
            
            String packageName = sortedMap.get(sortedMap.lastKey()).getPackageName();
            
            // Skip our own app and system UI
            if (packageName.equals(getPackageName()) ||
                    packageName.contains("launcher") ||
                    packageName.contains("systemui") ||
                    packageName.contains("android")) {
                return null;
            }
            
            return packageName;
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting foreground app", e);
            return null;
        }
    }
    
    /**
     * Show lock screen for locked app
     */
    private void showLockScreen(String packageName) {
        try {
            Log.d(TAG, "Attempting to show lock screen for: " + packageName);
            
            mainHandler.post(() -> {
                try {
                    Intent lockIntent = new Intent(this, LockScreenActivity.class);
                    lockIntent.putExtra("package_name", packageName);
                    lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    lockIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(lockIntent);
                    Log.d(TAG, "Launched lock screen for " + packageName);
                } catch (Exception e) {
                    Log.e(TAG, "Error showing lock screen", e);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in showLockScreen", e);
        }
    }
} 