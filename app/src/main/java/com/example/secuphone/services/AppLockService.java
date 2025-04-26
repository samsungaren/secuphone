package com.example.secuphone.services;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import com.example.secuphone.LockScreenActivity;
import com.example.secuphone.MainActivity;
import com.example.secuphone.R;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AppLockService extends Service {

    private static final String TAG = "AppLockService";
    private static final int CHECK_INTERVAL_MS = 300;
    private static final String PREFS_LOCKED_APPS = "locked_apps";
    private static final String PREFS_PIN_SET = "pin_set";
    private static final String PREFS_PIN_CODE = "pin_code";
    private static final String NOTIFICATION_CHANNEL_ID = "app_lock_channel";
    private static final int NOTIFICATION_ID = 1001;
    
    private String lastApp = "";
    private Handler handler;
    private SharedPreferences preferences;
    private Set<String> lockedApps;
    private ScheduledExecutorService executorService;
    
    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler();
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        lockedApps = preferences.getStringSet(PREFS_LOCKED_APPS, new HashSet<>());
        executorService = Executors.newSingleThreadScheduledExecutor();
        
        Log.d(TAG, "AppLockService created with locked apps: " + lockedApps);
        
        // For Android O and above, start as a foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(NOTIFICATION_ID, createNotification());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Schedule regular app checking
        startAppMonitoring();
        return START_STICKY;
    }
    
    private Notification createNotification() {
        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "App Lock Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Required for app locking functionality");
            channel.setShowBadge(false);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
        
        // Create intent for tapping the notification
        Intent intent = new Intent(this, MainActivity.class);
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
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    private void startAppMonitoring() {
        executorService.scheduleWithFixedDelay(() -> {
            // Check if PIN is set
            boolean isPinSet = preferences.getBoolean(PREFS_PIN_SET, false);
            if (!isPinSet) {
                return; // Don't monitor if no PIN is set
            }
            
            // Refresh locked apps list in case it changed
            lockedApps = preferences.getStringSet(PREFS_LOCKED_APPS, new HashSet<>());
            
            // Don't continue if there are no locked apps
            if (lockedApps.isEmpty()) {
                return;
            }
            
            String currentApp = getCurrentAppPackage();
            if (currentApp != null && !currentApp.equals(lastApp)) {
                lastApp = currentApp;
                
                // Check if current app is in locked list
                if (lockedApps.contains(currentApp)) {
                    Log.d(TAG, "Locked app detected: " + currentApp);
                    showLockScreen(currentApp);
                }
            }
        }, 0, CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }
    
    private String getCurrentAppPackage() {
        String currentApp = null;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            UsageStatsManager usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            long endTime = System.currentTimeMillis();
            long beginTime = endTime - 1000 * 10; // Check last 10 seconds
            
            SortedMap<Long, UsageStats> usageStats = new TreeMap<>();
            List<UsageStats> stats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY, beginTime, endTime);
            
            if (stats != null) {
                for (UsageStats usageStat : stats) {
                    usageStats.put(usageStat.getLastTimeUsed(), usageStat);
                }
                
                if (!usageStats.isEmpty()) {
                    currentApp = usageStats.get(usageStats.lastKey()).getPackageName();
                }
            }
        } else {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> tasks = am.getRunningAppProcesses();
            if (tasks != null && !tasks.isEmpty()) {
                currentApp = tasks.get(0).processName;
            }
        }
        
        // Filter out system apps and our own app
        if (currentApp != null && 
            (currentApp.equals("com.example.secuphone_bycoursor") || 
             currentApp.startsWith("com.android") || 
             currentApp.startsWith("android") ||
             currentApp.equals("android"))) {
            return null;
        }
        
        // Only return the app if it's in our locked apps list
        if (currentApp != null && lockedApps.contains(currentApp)) {
            return currentApp;
        }
        
        return null;
    }
    
    private void showLockScreen(String packageName) {
        handler.post(() -> {
            Intent lockIntent = new Intent(AppLockService.this, LockScreenActivity.class);
            lockIntent.putExtra("package_name", packageName);
            lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(lockIntent);
        });
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }
    
    public static boolean isAppLocked(Context context, String packageName) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> lockedApps = prefs.getStringSet(PREFS_LOCKED_APPS, new HashSet<>());
        return lockedApps.contains(packageName);
    }
    
    public static void setAppLockStatus(Context context, String packageName, boolean locked) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> lockedApps = new HashSet<>(prefs.getStringSet(PREFS_LOCKED_APPS, new HashSet<>()));
        
        if (locked) {
            lockedApps.add(packageName);
        } else {
            lockedApps.remove(packageName);
        }
        
        prefs.edit().putStringSet(PREFS_LOCKED_APPS, lockedApps).apply();
        Log.d(TAG, "App lock status updated. Package: " + packageName + ", Locked: " + locked);
    }
    
    public static boolean isPinSet(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(PREFS_PIN_SET, false);
    }
    
    public static void setPin(Context context, String pin) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit()
             .putString(PREFS_PIN_CODE, pin)
             .putBoolean(PREFS_PIN_SET, true)
             .apply();
    }
    
    public static boolean verifyPin(Context context, String pin) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String savedPin = prefs.getString(PREFS_PIN_CODE, "");
        return savedPin.equals(pin);
    }
} 