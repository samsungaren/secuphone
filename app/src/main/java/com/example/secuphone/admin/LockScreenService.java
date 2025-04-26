package com.example.secuphone.admin;

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

import com.example.secuphone.AppLockActivity;
import com.example.secuphone.LockScreenActivity;
import com.example.secuphone.R;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private static final int CHECK_INTERVAL_MS = 1000;
    private static final int NOTIFICATION_ID = 1002;
    private static final String NOTIFICATION_CHANNEL_ID = "lock_screen_service";
    
    private String currentForegroundApp = "";
    private String lastForegroundApp = "";
    private AppLockManager appLockManager;
    private ScheduledExecutorService executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    // Кэш для состояния блокировки приложений
    private Map<String, Boolean> appLockStateCache = new HashMap<>();
    
    // Список приложений, для которых недавно показан LockScreen
    private Set<String> recentlyLockedApps = new HashSet<>();
    // Таймаут перед повторным показом экрана блокировки (5 секунд)
    private static final long RELOCK_TIMEOUT_MS = 5000;
    
    // Время последнего обновления кэша
    private long lastCacheUpdateTime = 0;
    // Интервал обновления кэша (30 секунд)
    private static final long CACHE_UPDATE_INTERVAL_MS = 30000;
    
    @Override
    public void onCreate() {
        try {
            super.onCreate();
            appLockManager = new AppLockManager(this);
            executor = Executors.newSingleThreadScheduledExecutor();
            
            // Инициализируем кэш при запуске
            updateLockStateCache();
            
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
     * Обновляет кэш состояния блокировки приложений
     */
    private void updateLockStateCache() {
        try {
            Set<String> lockedApps = appLockManager.getLockedApps();
            appLockStateCache.clear();
            
            for (String packageName : lockedApps) {
                appLockStateCache.put(packageName, true);
            }
            
            lastCacheUpdateTime = System.currentTimeMillis();
            Log.d(TAG, "Updated lock state cache with " + appLockStateCache.size() + " locked apps");
        } catch (Exception e) {
            Log.e(TAG, "Error updating lock state cache", e);
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
                    
                    // Периодически обновляем кэш состояния блокировки
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastCacheUpdateTime > CACHE_UPDATE_INTERVAL_MS) {
                        updateLockStateCache();
                    }
                    
                    // Get current foreground app
                    currentForegroundApp = getForegroundApp();
                    
                    // Skip if no app detected
                    if (currentForegroundApp == null) {
                        return;
                    }
                    
                    // Только если сменилось приложение
                    if (!currentForegroundApp.equals(lastForegroundApp)) {
                        Log.d(TAG, "Detected foreground app change to: " + currentForegroundApp);
                        
                        // Update last app
                        lastForegroundApp = currentForegroundApp;
                        
                        // Очищаем метку о недавно показанном экране блокировки для нового приложения
                        boolean recentlyLocked = recentlyLockedApps.contains(currentForegroundApp);
                        
                        // Если приложение недавно блокировалось, пропускаем повторную проверку
                        if (recentlyLocked) {
                            Log.d(TAG, "App " + currentForegroundApp + " was recently locked, skipping check");
                            return;
                        }
                        
                        // Проверяем состояние блокировки сначала из кэша
                        Boolean isLocked = appLockStateCache.get(currentForegroundApp);
                        
                        // Если нет в кэше, проверяем напрямую и добавляем в кэш
                        if (isLocked == null) {
                            isLocked = appLockManager.isAppLocked(currentForegroundApp);
                            appLockStateCache.put(currentForegroundApp, isLocked);
                        }
                        
                        if (isLocked) {
                            Log.d(TAG, "Detected locked app: " + currentForegroundApp);
                            // Добавляем в список недавно заблокированных
                            recentlyLockedApps.add(currentForegroundApp);
                            
                            // Показываем экран блокировки
                            showLockScreen(currentForegroundApp);
                            
                            // Через RELOCK_TIMEOUT_MS удаляем из списка недавно заблокированных
                            mainHandler.postDelayed(() -> {
                                recentlyLockedApps.remove(currentForegroundApp);
                                Log.d(TAG, "Removed " + currentForegroundApp + " from recently locked list");
                            }, RELOCK_TIMEOUT_MS);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error during app monitoring", e);
                }
            }, 1000, CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS); // Небольшая задержка при старте
            
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
            long beginTime = endTime - 5000; // Последние 5 секунд (было 10)
            
            // Query apps used in last 5 seconds
            List<UsageStats> stats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY, beginTime, endTime);
            
            if (stats == null || stats.isEmpty()) {
                return null;
            }
            
            // Find most recently used app
            SortedMap<Long, UsageStats> sortedMap = new TreeMap<>();
            for (UsageStats usageStats : stats) {
                // Добавляем только приложения с временем использования > 0
                if (usageStats.getLastTimeUsed() > 0) {
                    sortedMap.put(usageStats.getLastTimeUsed(), usageStats);
                }
            }
            
            if (sortedMap.isEmpty()) {
                return null;
            }
            
            String packageName = sortedMap.get(sortedMap.lastKey()).getPackageName();
            
            // Фильтрация системных приложений и самого SecuPhone
            if (packageName.equals(getPackageName()) ||
                    packageName.contains("launcher") ||
                    packageName.contains("systemui") ||
                    packageName.contains("android") ||
                    packageName.contains("inputmethod") ||
                    packageName.equals("com.example.secuphone_bycoursor.LockScreenActivity")) {
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
                    lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(lockIntent);
                    Log.d(TAG, "Lock screen activity started for: " + packageName);
                } catch (Exception e) {
                    Log.e(TAG, "Error showing lock screen: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error preparing lock screen", e);
        }
    }
} 