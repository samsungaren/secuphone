package com.example.secuphone.services;

import android.app.ActivityManager;
  import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import com.example.secuphone.LockScreenActivity;
import com.example.secuphone.MainActivity;
import com.example.secuphone.R;
import com.example.secuphone.utils.SecurityUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Фоновый сервис для отслеживания и блокировки приложений.
 * Улучшенная версия с оптимизацией расхода батареи и защитой от обхода.
 */
public class AppLockService extends Service {

    private static final String TAG = "AppLockService";
    
    // Интервал проверки приложений в мс (более короткий для быстрого отклика)
    private static final int CHECK_INTERVAL_MS = 200;
    
    // Интервал проверки в режиме экономии батареи (более длинный)
    private static final int CHECK_INTERVAL_LOW_POWER_MS = 500;
    
    // Ключи для SharedPreferences
    private static final String PREFS_LOCKED_APPS = "locked_apps";
    private static final String PREFS_PIN_HASH = "pin_hash";
    private static final String PREFS_PIN_SALT = "pin_salt";
    private static final String PREFS_PIN_SET = "pin_set";
    private static final String PREFS_UNLOCKED_APPS = "temp_unlocked_apps";
    private static final String PREFS_LAST_UNLOCKED_TIME = "last_unlocked_time";
    
    // Константы для foreground сервиса
    private static final String NOTIFICATION_CHANNEL_ID = "app_lock_channel";
    private static final int NOTIFICATION_ID = 1001;
    
    // Константа для таймаута разблокировки приложений (30 минут)
    private static final long UNLOCK_TIMEOUT_MS = 30 * 60 * 1000;
    
    // Действие для перезапуска сервиса
    public static final String ACTION_RESTART_SERVICE = "com.example.secuphone.RESTART_APP_LOCK_SERVICE";
    
    // Runtime переменные
    private String lastApp = "";
    private String currentApp = "";
    private Handler mainHandler;
    private SharedPreferences preferences;
    private Set<String> lockedApps;
    private Set<String> tempUnlockedApps;
    private ScheduledExecutorService executorService;
    private PowerManager.WakeLock wakeLock;
    private boolean isScreenOn = true;
    private BroadcastReceiver screenStateReceiver;
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Инициализация основных компонентов
        mainHandler = new Handler(Looper.getMainLooper());
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        lockedApps = preferences.getStringSet(PREFS_LOCKED_APPS, new HashSet<>());
        tempUnlockedApps = preferences.getStringSet(PREFS_UNLOCKED_APPS, new HashSet<>());
        executorService = Executors.newSingleThreadScheduledExecutor();
        
        // Очистка временных разблокировок по таймауту
        cleanupExpiredUnlocks();
        
        // Настройка WakeLock для надежной работы
        setupWakeLock();
        
        // Регистрация ресивера для отслеживания состояния экрана
        registerScreenStateReceiver();
        
        Log.d(TAG, "AppLockService создан с " + lockedApps.size() + " заблокированными приложениями");
        
        // Запуск в режиме Foreground Service (обязательно для Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(NOTIFICATION_ID, createNotification());
        }
    }

    /**
     * Настройка WakeLock для предотвращения остановки сервиса системой
     */
    private void setupWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SecuPhone:AppLockServiceWakeLock"
        );
        wakeLock.acquire(10 * 60 * 1000L); // 10 минут максимум, затем автоматически освобождается
    }
    
    /**
     * Регистрация ресивера для отслеживания состояния экрана
     */
    private void registerScreenStateReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        
        screenStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                    isScreenOn = true;
                    // Перезапуск проверок с коротким интервалом
                    restartMonitoring(false);
                } else if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    isScreenOn = false;
                    // Переключение на режим экономии батареи
                    restartMonitoring(true);
                }
            }
        };
        
        registerReceiver(screenStateReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Обработка действия перезапуска если пришло
        if (intent != null && ACTION_RESTART_SERVICE.equals(intent.getAction())) {
            Log.d(TAG, "Получен запрос на перезапуск сервиса");
            stopAppMonitoring();
            startAppMonitoring();
        } else {
            // Запуск основного цикла мониторинга
            startAppMonitoring();
            
            // Настройка перезапуска на случай, если сервис будет убит системой
            setupServiceRestart();
        }
        
        return START_STICKY; // Сервис будет перезапущен системой если убит
    }
    
    /**
     * Настройка перезапуска сервиса через AlarmManager
     */
    private void setupServiceRestart() {
        Intent restartIntent = new Intent(this, AppLockService.class);
        restartIntent.setAction(ACTION_RESTART_SERVICE);
        
        PendingIntent pendingIntent = PendingIntent.getService(
                this, 
                0, 
                restartIntent, 
                PendingIntent.FLAG_IMMUTABLE
        );
        
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        
        // Установка регулярного перезапуска каждые 15 минут для подстраховки
        alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 15 * 60 * 1000,
                15 * 60 * 1000,
                pendingIntent
        );
    }
    
    /**
     * Создание уведомления для режима Foreground Service
     */
    private Notification createNotification() {
        // Создание канала для Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "App Lock Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Необходимо для работы блокировки приложений");
            channel.setShowBadge(false);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
        
        // Настройка действия при нажатии на уведомление
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        
        // Создание и возврат уведомления
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
        return null; // Мы не предоставляем привязку для этого сервиса
    }
    
    /**
     * Очистка временных разблокировок по таймауту
     */
    private void cleanupExpiredUnlocks() {
        long lastUnlockTime = preferences.getLong(PREFS_LAST_UNLOCKED_TIME, 0);
        long currentTime = System.currentTimeMillis();
        
        // Если прошло больше UNLOCK_TIMEOUT_MS с момента последней разблокировки
        if (currentTime - lastUnlockTime > UNLOCK_TIMEOUT_MS) {
            // Очищаем список временно разблокированных приложений
            preferences.edit()
                    .putStringSet(PREFS_UNLOCKED_APPS, new HashSet<>())
                    .apply();
            tempUnlockedApps.clear();
        }
    }
    
    /**
     * Перезапуск мониторинга с заданным режимом экономии батареи
     */
    private void restartMonitoring(boolean lowPowerMode) {
        stopAppMonitoring();
        startAppMonitoring(lowPowerMode);
    }
    
    /**
     * Остановка мониторинга приложений
     */
    private void stopAppMonitoring() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }
    
    /**
     * Запуск мониторинга приложений с опциональным режимом экономии батареи
     */
    private void startAppMonitoring() {
        startAppMonitoring(false);
    }
    
    /**
     * Запуск мониторинга приложений с опциональным режимом экономии батареи
     */
    private void startAppMonitoring(boolean lowPowerMode) {
        // Создаем новый executor если предыдущий был остановлен
        if (executorService == null || executorService.isShutdown()) {
            executorService = Executors.newSingleThreadScheduledExecutor();
        }
        
        // Выбор интервала в зависимости от режима экономии
        final int interval = lowPowerMode ? CHECK_INTERVAL_LOW_POWER_MS : CHECK_INTERVAL_MS;
        
        executorService.scheduleWithFixedDelay(() -> {
            try {
                // Проверка наличия PIN-кода (без него блокировка бессмысленна)
                boolean isPinSet = preferences.getBoolean(PREFS_PIN_SET, false);
                if (!isPinSet) {
                    return;
                }
                
                // Обновление списка заблокированных приложений
                lockedApps = preferences.getStringSet(PREFS_LOCKED_APPS, new HashSet<>());
                tempUnlockedApps = preferences.getStringSet(PREFS_UNLOCKED_APPS, new HashSet<>());
                
                // Выходим если нечего блокировать
                if (lockedApps.isEmpty()) {
                    return;
                }
                
                // Не делаем проверку когда экран выключен для экономии батареи
                if (!isScreenOn && lowPowerMode) {
                    return;
                }
                
                // Получение текущего активного приложения
                currentApp = getForegroundApp();
                
                // Проверка нужно ли отображать экран блокировки
                if (shouldShowLockScreen(currentApp)) {
                    Log.d(TAG, "Обнаружено заблокированное приложение: " + currentApp);
                    showLockScreen(currentApp);
                }
                
                // Обновление последнего известного приложения
                if (currentApp != null && !currentApp.equals(lastApp)) {
                    lastApp = currentApp;
                }
            } catch (Exception e) {
                Log.e(TAG, "Ошибка при мониторинге приложений", e);
            }
        }, 0, interval, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Проверка, нужно ли показывать экран блокировки для данного приложения
     */
    private boolean shouldShowLockScreen(String packageName) {
        // Проверка базовых условий
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }
        
        // Если приложение не в списке заблокированных, блокировка не нужна
        if (!lockedApps.contains(packageName)) {
            return false;
        }
        
        // Если приложение временно разблокировано, пропускаем его
        if (tempUnlockedApps.contains(packageName)) {
            return false;
        }
        
        // Если переходим на другое приложение, нужно показать блокировку
        return !packageName.equals(lastApp);
    }
    
    /**
     * Получение текущего активного приложения с улучшенной точностью
     */
    private String getForegroundApp() {
        String foregroundApp = null;
        
        try {
            // Используем UsageEvents вместо UsageStats для более точной информации
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                UsageStatsManager usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
                long endTime = System.currentTimeMillis();
                long beginTime = endTime - 5000; // Проверяем последние 5 секунд
                
                // Получаем события использования приложений
                UsageEvents usageEvents = usageStatsManager.queryEvents(beginTime, endTime);
                
                // Отслеживаем последнее событие MOVE_TO_FOREGROUND
                UsageEvents.Event lastForegroundEvent = null;
                UsageEvents.Event event = new UsageEvents.Event();
                
                while (usageEvents.hasNextEvent()) {
                    usageEvents.getNextEvent(event);
                    
                    // Android 11+ (API 30+) использует константу ACTIVITY_RESUMED вместо MOVE_TO_FOREGROUND
                    int eventType = event.getEventType();
                    if (eventType == UsageEvents.Event.MOVE_TO_FOREGROUND || 
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && eventType == UsageEvents.Event.ACTIVITY_RESUMED)) {
                        lastForegroundEvent = event;
                    }
                }
                
                if (lastForegroundEvent != null) {
                    foregroundApp = lastForegroundEvent.getPackageName();
                }
                
                // Если UsageEvents не дал результата, пробуем с UsageStats
                if (foregroundApp == null) {
                    List<UsageStats> stats = usageStatsManager.queryUsageStats(
                            UsageStatsManager.INTERVAL_DAILY, beginTime, endTime);
                    
                    if (stats != null && !stats.isEmpty()) {
                        SortedMap<Long, UsageStats> sortedMap = new TreeMap<>();
                        for (UsageStats usageStats : stats) {
                            if (usageStats.getLastTimeUsed() > 0) {
                                sortedMap.put(usageStats.getLastTimeUsed(), usageStats);
                            }
                        }
                        
                        if (!sortedMap.isEmpty()) {
                            foregroundApp = sortedMap.get(sortedMap.lastKey()).getPackageName();
                        }
                    }
                }
            } else {
                // Для старых Android версий используем ActivityManager
                ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                List<ActivityManager.RunningAppProcessInfo> tasks = am.getRunningAppProcesses();
                if (tasks != null && !tasks.isEmpty()) {
                    foregroundApp = tasks.get(0).processName;
                }
            }
            
            // Фильтрация системных приложений и нашего собственного приложения
            if (foregroundApp != null && (
                    foregroundApp.equals(getPackageName()) ||
                    foregroundApp.contains("launcher") ||
                    foregroundApp.contains("systemui") ||
                    foregroundApp.equals("android") ||
                    foregroundApp.startsWith("com.android") ||
                    foregroundApp.contains("inputmethod"))) {
                return null;
            }
            
            return foregroundApp;
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при определении активного приложения", e);
            return null;
        }
    }
    
    /**
     * Отображение экрана блокировки для заданного приложения
     */
    private void showLockScreen(String packageName) {
        mainHandler.post(() -> {
            try {
                Intent lockIntent = new Intent(AppLockService.this, LockScreenActivity.class);
                lockIntent.putExtra("package_name", packageName);
                lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(lockIntent);
                Log.d(TAG, "Запущен экран блокировки для: " + packageName);
            } catch (Exception e) {
                Log.e(TAG, "Ошибка при отображении экрана блокировки", e);
            }
        });
    }
    
    @Override
    public void onDestroy() {
        Log.d(TAG, "AppLockService уничтожается, попытка перезапуска");
        super.onDestroy();
        
        // Освобождение ресурсов
        if (executorService != null) {
            executorService.shutdownNow();
        }
        
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        
        // Отписка от ресивера
        if (screenStateReceiver != null) {
            try {
                unregisterReceiver(screenStateReceiver);
            } catch (Exception e) {
                Log.e(TAG, "Ошибка при отписке от ресивера", e);
            }
        }
        
        // Самовосстановление - запуск сервиса снова
        Intent restartService = new Intent(getApplicationContext(), AppLockService.class);
        restartService.setAction(ACTION_RESTART_SERVICE);
        startService(restartService);
    }
    
    // ------ Статические методы для управления блокировкой приложений ------
    
    /**
     * Проверка, заблокировано ли приложение
     */
    public static boolean isAppLocked(Context context, String packageName) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> lockedApps = prefs.getStringSet(PREFS_LOCKED_APPS, new HashSet<>());
        return lockedApps.contains(packageName);
    }
    
    /**
     * Установка статуса блокировки для приложения
     */
    public static void setAppLockStatus(Context context, String packageName, boolean locked) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> lockedApps = new HashSet<>(prefs.getStringSet(PREFS_LOCKED_APPS, new HashSet<>()));
        
        if (locked) {
            lockedApps.add(packageName);
        } else {
            lockedApps.remove(packageName);
            
            // Если приложение было временно разблокировано, удаляем его из списка
            Set<String> tempUnlockedApps = new HashSet<>(prefs.getStringSet(PREFS_UNLOCKED_APPS, new HashSet<>()));
            if (tempUnlockedApps.contains(packageName)) {
                tempUnlockedApps.remove(packageName);
                prefs.edit().putStringSet(PREFS_UNLOCKED_APPS, tempUnlockedApps).apply();
            }
        }
        
        prefs.edit().putStringSet(PREFS_LOCKED_APPS, lockedApps).apply();
        Log.d(TAG, "Статус блокировки приложения обновлен. Пакет: " + packageName + ", Заблокирован: " + locked);
    }
    
    /**
     * Временная разблокировка приложения (до таймаута)
     */
    public static void tempUnlockApp(Context context, String packageName) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> tempUnlockedApps = new HashSet<>(prefs.getStringSet(PREFS_UNLOCKED_APPS, new HashSet<>()));
        
        tempUnlockedApps.add(packageName);
        
        prefs.edit()
                .putStringSet(PREFS_UNLOCKED_APPS, tempUnlockedApps)
                .putLong(PREFS_LAST_UNLOCKED_TIME, System.currentTimeMillis())
                .apply();
        
        Log.d(TAG, "Приложение временно разблокировано: " + packageName);
    }
    
    /**
     * Проверка, установлен ли PIN-код для блокировки
     */
    public static boolean isPinSet(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(PREFS_PIN_SET, false) && 
               prefs.contains(PREFS_PIN_HASH) && 
               prefs.contains(PREFS_PIN_SALT);
    }
    
    /**
     * Установка PIN-кода для блокировки с безопасным хешированием
     */
    public static void setPin(Context context, String pin) {
        if (pin == null || pin.isEmpty()) {
            throw new IllegalArgumentException("PIN не может быть пустым");
        }
        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        
        // Генерация соли и хеша пароля
        String salt = SecurityUtils.generateSalt();
        String hashedPin = SecurityUtils.hashPassword(pin, salt);
        
        // Сохранение хеша и соли
        prefs.edit()
             .putString(PREFS_PIN_HASH, hashedPin)
             .putString(PREFS_PIN_SALT, salt)
             .putBoolean(PREFS_PIN_SET, true)
             .apply();
        
        Log.d(TAG, "PIN-код установлен и хеширован");
    }
    
    /**
     * Проверка PIN-кода
     */
    public static boolean verifyPin(Context context, String pin) {
        if (pin == null || pin.isEmpty()) {
            return false;
        }
        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        
        // Получение сохраненного хеша и соли
        String savedHash = prefs.getString(PREFS_PIN_HASH, "");
        String salt = prefs.getString(PREFS_PIN_SALT, "");
        
        if (savedHash.isEmpty() || salt.isEmpty()) {
            return false;
        }
        
        // Хеширование введенного PIN-кода с той же солью
        String hashedPin = SecurityUtils.hashPassword(pin, salt);
        
        // Сравнение хешей
        return savedHash.equals(hashedPin);
    }
    
    /**
     * Сброс всех настроек блокировки приложений
     */
    public static void resetAppLockSettings(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        
        prefs.edit()
             .remove(PREFS_PIN_HASH)
             .remove(PREFS_PIN_SALT)
             .remove(PREFS_PIN_SET)
             .remove(PREFS_LOCKED_APPS)
             .remove(PREFS_UNLOCKED_APPS)
             .remove(PREFS_LAST_UNLOCKED_TIME)
             .apply();
        
        Log.d(TAG, "Настройки блокировки приложений сброшены");
    }
    
    /**
     * Получение списка всех заблокированных приложений
     */
    public static Set<String> getLockedApps(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return new HashSet<>(prefs.getStringSet(PREFS_LOCKED_APPS, new HashSet<>()));
    }
} 