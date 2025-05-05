package com.example.secuphone.admin;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.example.secuphone.utils.SecurityUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manager class for App Lock functionality using Device Administrator APIs
 */
public class AppLockManager {
    private static final String TAG = "AppLockManager";
    private static final String PREF_LOCKED_APPS = "locked_apps";
    private static final String PREF_PIN_SET = "pin_set";
    private static final String PREF_PIN_HASH = "pin_hash";
    private static final String PREF_PIN_SALT = "pin_salt";
    private static final String PREF_PIN_CODE = "pin_code"; // Для обратной совместимости
    private static final String PREF_LAST_ACTIVE_APP = "last_active_app";
    private static final String PREF_APPS_BY_CATEGORY_PREFIX = "apps_category_";
    
    // Предпочтения для категорий приложений
    private static final String PREF_APP_CATEGORIES = "app_categories";
    
    // Предопределенные категории приложений
    public static final String CATEGORY_SOCIAL = "social";
    public static final String CATEGORY_FINANCE = "finance";
    public static final String CATEGORY_GAMES = "games";
    public static final String CATEGORY_SHOPPING = "shopping";
    public static final String CATEGORY_MESSAGING = "messaging";
    
    private static AppLockManager instance;
    
    private final Context context;
    private final DevicePolicyManager dpm;
    private final ComponentName adminComponent;
    
    /**
     * Get singleton instance of AppLockManager
     */
    public static synchronized AppLockManager getInstance(Context context) {
        if (instance == null) {
            instance = new AppLockManager(context.getApplicationContext());
        }
        return instance;
    }
    
    public AppLockManager(Context context) {
        this.context = context;
        dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(context, SecuPhoneDeviceAdmin.class);
    }
    
    /**
     * Check if this app is a device admin
     */
    public boolean isAdminActive() {
        return dpm != null && dpm.isAdminActive(adminComponent);
    }
    
    /**
     * Request to become a device admin
     */
    public Intent getAdminRequestIntent() {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, 
                context.getString(com.example.secuphone.R.string.admin_request_message));
        return intent;
    }
    
    /**
     * Установить PIN-код для блокировки с использованием безопасного хеширования
     */
    public boolean setPin(String pin) {
        try {
            if (pin == null || pin.trim().isEmpty()) {
                Log.e(TAG, "Attempted to set empty PIN");
                return false;
            }
            
            Log.d(TAG, "Setting PIN with secure hashing, pin length: " + pin.length());
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            
            // Генерируем соль для хеширования PIN-кода
            String salt = SecurityUtils.generateSalt();
            // Хешируем PIN-код с использованием соли
            String hashedPin = SecurityUtils.hashPassword(pin, salt);
            
            // Сохраняем хеш PIN-кода и соль
            prefs.edit()
                .putString(PREF_PIN_HASH, hashedPin)
                .putString(PREF_PIN_SALT, salt)
                .putBoolean(PREF_PIN_SET, true)
                .apply();
            
            // Проверяем, что данные сохранены
            boolean pinSet = prefs.getBoolean(PREF_PIN_SET, false);
            boolean hashStored = prefs.contains(PREF_PIN_HASH);
            boolean saltStored = prefs.contains(PREF_PIN_SALT);
            
            Log.d(TAG, "PIN set successfully: " + pinSet + 
                  ", Hash stored: " + hashStored + 
                  ", Salt stored: " + saltStored);
            
            // Проверяем, что можем правильно проверить PIN
            return pinSet && hashStored && saltStored && verifyPin(pin);
        } catch (Exception e) {
            Log.e(TAG, "Error setting PIN", e);
            return false;
        }
    }
    
    /**
     * Проверить, установлен ли PIN-код
     */
    public boolean isPinSet() {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean isPinSet = prefs.getBoolean(PREF_PIN_SET, false);
            boolean hasHash = prefs.contains(PREF_PIN_HASH) && !prefs.getString(PREF_PIN_HASH, "").isEmpty();
            boolean hasSalt = prefs.contains(PREF_PIN_SALT) && !prefs.getString(PREF_PIN_SALT, "").isEmpty();
            
            // Для обратной совместимости проверяем и старый способ хранения PIN
            boolean hasLegacyPin = prefs.contains(PREF_PIN_CODE) && !prefs.getString(PREF_PIN_CODE, "").isEmpty();
            
            Log.d(TAG, "PIN is " + (isPinSet ? "set" : "not set") + 
                  ", Has hash: " + hasHash + 
                  ", Has salt: " + hasSalt + 
                  ", Has legacy PIN: " + hasLegacyPin);
            
            return isPinSet && ((hasHash && hasSalt) || hasLegacyPin);
        } catch (Exception e) {
            Log.e(TAG, "Error checking if PIN is set", e);
            return false;
        }
    }
    
    /**
     * Проверить PIN-код с использованием безопасного хеширования
     */
    public boolean verifyPin(String enteredPin) {
        try {
            if (enteredPin == null || enteredPin.trim().isEmpty()) {
                return false;
            }
            
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            
            // Проверяем, есть ли хеш и соль для нового метода
            if (prefs.contains(PREF_PIN_HASH) && prefs.contains(PREF_PIN_SALT)) {
                String storedHash = prefs.getString(PREF_PIN_HASH, "");
                String storedSalt = prefs.getString(PREF_PIN_SALT, "");
                
                if (storedHash.isEmpty() || storedSalt.isEmpty()) {
                    Log.e(TAG, "Stored hash or salt is empty");
                    return false;
                }
                
                // Проверяем PIN с использованием хеша и соли
                boolean isValid = SecurityUtils.verifyPassword(enteredPin, storedHash, storedSalt);
                Log.d(TAG, "PIN verification result (secure method): " + isValid);
                return isValid;
            } 
            // Обратная совместимость со старым методом
            else if (prefs.contains(PREF_PIN_CODE)) {
                String savedPin = prefs.getString(PREF_PIN_CODE, "");
                
                if (savedPin.isEmpty()) {
                    Log.e(TAG, "Saved PIN is empty");
                    return false;
                }
                
                boolean isValid = savedPin.equals(enteredPin);
                
                // Если PIN верный, мигрируем на новый метод хранения
                if (isValid) {
                    Log.d(TAG, "PIN verified with legacy method, migrating to secure storage");
                    setPin(enteredPin);
                    // Удаляем старый PIN
                    prefs.edit().remove(PREF_PIN_CODE).apply();
                }
                
                Log.d(TAG, "PIN verification result (legacy method): " + isValid);
                return isValid;
            }
            
            Log.e(TAG, "No PIN storage method found");
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error verifying PIN", e);
            return false;
        }
    }
    
    /**
     * Add an app to the locked apps list
     */
    public void addLockedApp(String packageName) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            
            // Get current set of locked apps - create a new HashSet to ensure we can modify it
            Set<String> currentLockedApps = prefs.getStringSet(PREF_LOCKED_APPS, new HashSet<>());
            Set<String> lockedApps = new HashSet<>(currentLockedApps != null ? currentLockedApps : new HashSet<>());
            
            // Add new app to locked list
            lockedApps.add(packageName);
            
            // Get editor and apply changes
            SharedPreferences.Editor editor = prefs.edit();
            editor.putStringSet(PREF_LOCKED_APPS, lockedApps);
            editor.apply();
            
            Log.d(TAG, "Added " + packageName + " to locked apps list. Total locked apps: " + lockedApps.size());
        } catch (Exception e) {
            Log.e(TAG, "Error adding app to locked apps", e);
        }
    }
    
    /**
     * Remove an app from the locked apps list
     */
    public void removeLockedApp(String packageName) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            
            // Get current set of locked apps - create a new HashSet to ensure we can modify it
            Set<String> currentLockedApps = prefs.getStringSet(PREF_LOCKED_APPS, new HashSet<>());
            Set<String> lockedApps = new HashSet<>(currentLockedApps != null ? currentLockedApps : new HashSet<>());
            
            // Remove app from locked list
            lockedApps.remove(packageName);
            
            // Get editor and apply changes
            SharedPreferences.Editor editor = prefs.edit();
            editor.putStringSet(PREF_LOCKED_APPS, lockedApps);
            editor.apply();
            
            Log.d(TAG, "Removed " + packageName + " from locked apps list. Remaining locked apps: " + lockedApps.size());
        } catch (Exception e) {
            Log.e(TAG, "Error removing app from locked apps", e);
        }
    }
    
    /**
     * Check if an app is locked
     */
    public boolean isAppLocked(String packageName) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            Set<String> lockedApps = prefs.getStringSet(PREF_LOCKED_APPS, new HashSet<>());
            boolean isLocked = lockedApps != null && lockedApps.contains(packageName);
            Log.d(TAG, "Checking if " + packageName + " is locked: " + isLocked);
            return isLocked;
        } catch (Exception e) {
            Log.e(TAG, "Error checking if app is locked", e);
            return false;
        }
    }
    
    /**
     * Get all locked apps
     */
    public Set<String> getLockedApps() {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            Set<String> lockedApps = prefs.getStringSet(PREF_LOCKED_APPS, new HashSet<>());
            return lockedApps != null ? new HashSet<>(lockedApps) : new HashSet<>();
        } catch (Exception e) {
            Log.e(TAG, "Error getting locked apps", e);
            return new HashSet<>();
        }
    }
    
    /**
     * Get list of installed user apps (non-system)
     */
    public List<ApplicationInfo> getUserInstalledApps() {
        List<ApplicationInfo> userApps = new ArrayList<>();
        PackageManager pm = context.getPackageManager();
        
        try {
            List<ApplicationInfo> installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            
            for (ApplicationInfo app : installedApps) {
                // Filter out system apps and our own app
                if ((app.flags & ApplicationInfo.FLAG_SYSTEM) == 0 &&
                    !app.packageName.equals(context.getPackageName())) {
                    userApps.add(app);
                }
            }
            
            Log.d(TAG, "Found " + userApps.size() + " user installed apps");
            return userApps;
        } catch (Exception e) {
            Log.e(TAG, "Error getting user installed apps", e);
            return userApps;
        }
    }
    
    /**
     * Lock device immediately
     */
    public void lockDevice() {
        try {
            if (dpm != null && isAdminActive()) {
                dpm.lockNow();
                Log.d(TAG, "Device locked");
            } else {
                Log.e(TAG, "Cannot lock device - not an active admin");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error locking device", e);
        }
    }
    
    /**
     * Добавить приложение в категорию
     */
    public void addAppToCategory(String packageName, String category) {
        try {
            if (packageName == null || category == null) {
                Log.e(TAG, "Package name or category is null");
                return;
            }
            
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            
            // Получаем текущие категории приложений (формат: "категория:пакет1,пакет2,...")
            Set<String> appCategories = prefs.getStringSet(PREF_APP_CATEGORIES, new HashSet<>());
            Set<String> updatedCategories = new HashSet<>(appCategories != null ? appCategories : new HashSet<>());
            
            // Ищем нужную категорию
            String categoryEntry = null;
            for (String entry : updatedCategories) {
                if (entry.startsWith(category + ":")) {
                    categoryEntry = entry;
                    break;
                }
            }
            
            // Если категория уже существует, обновляем ее
            if (categoryEntry != null) {
                updatedCategories.remove(categoryEntry);
                
                // Добавляем приложение, если его еще нет
                if (!categoryEntry.contains(packageName)) {
                    categoryEntry = categoryEntry + "," + packageName;
                }
                
                updatedCategories.add(categoryEntry);
            } else {
                // Если категории нет, создаем новую
                updatedCategories.add(category + ":" + packageName);
            }
            
            // Сохраняем обновленные данные
            prefs.edit()
                 .putStringSet(PREF_APP_CATEGORIES, updatedCategories)
                 .apply();
            
            Log.d(TAG, "Added " + packageName + " to category " + category);
        } catch (Exception e) {
            Log.e(TAG, "Error adding app to category", e);
        }
    }
    
    /**
     * Удалить приложение из категории
     */
    public void removeAppFromCategory(String packageName, String category) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            
            // Получаем текущие категории приложений
            Set<String> appCategories = prefs.getStringSet(PREF_APP_CATEGORIES, new HashSet<>());
            Set<String> updatedCategories = new HashSet<>(appCategories != null ? appCategories : new HashSet<>());
            
            // Ищем нужную категорию
            String categoryEntry = null;
            for (String entry : updatedCategories) {
                if (entry.startsWith(category + ":")) {
                    categoryEntry = entry;
                    break;
                }
            }
            
            // Если категория найдена, обновляем ее
            if (categoryEntry != null) {
                updatedCategories.remove(categoryEntry);
                
                // Разбиваем строку категории и удаляем приложение
                String[] parts = categoryEntry.split(":");
                if (parts.length == 2) {
                    String[] apps = parts[1].split(",");
                    StringBuilder newAppList = new StringBuilder();
                    
                    for (String app : apps) {
                        if (!app.equals(packageName) && !app.isEmpty()) {
                            if (newAppList.length() > 0) {
                                newAppList.append(",");
                            }
                            newAppList.append(app);
                        }
                    }
                    
                    // Если остались приложения, добавляем категорию обратно
                    if (newAppList.length() > 0) {
                        updatedCategories.add(category + ":" + newAppList.toString());
                    }
                }
                
                // Сохраняем обновленные данные
                prefs.edit()
                     .putStringSet(PREF_APP_CATEGORIES, updatedCategories)
                     .apply();
                
                Log.d(TAG, "Removed " + packageName + " from category " + category);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error removing app from category", e);
        }
    }
    
    /**
     * Получить все приложения в категории
     */
    public Set<String> getAppsInCategory(String category) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            Set<String> appCategories = prefs.getStringSet(PREF_APP_CATEGORIES, new HashSet<>());
            Set<String> appsInCategory = new HashSet<>();
            
            // Ищем нужную категорию
            for (String entry : appCategories) {
                if (entry.startsWith(category + ":")) {
                    String[] parts = entry.split(":");
                    if (parts.length == 2 && !parts[1].isEmpty()) {
                        String[] apps = parts[1].split(",");
                        for (String app : apps) {
                            if (!app.isEmpty()) {
                                appsInCategory.add(app);
                            }
                        }
                    }
                    break;
                }
            }
            
            return appsInCategory;
        } catch (Exception e) {
            Log.e(TAG, "Error getting apps in category", e);
            return new HashSet<>();
        }
    }
    
    /**
     * Получить все категории приложений
     */
    public Set<String> getAllCategories() {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            Set<String> appCategories = prefs.getStringSet(PREF_APP_CATEGORIES, new HashSet<>());
            Set<String> categories = new HashSet<>();
            
            for (String entry : appCategories) {
                String[] parts = entry.split(":");
                if (parts.length > 0) {
                    categories.add(parts[0]);
                }
            }
            
            return categories;
        } catch (Exception e) {
            Log.e(TAG, "Error getting all categories", e);
            return new HashSet<>();
        }
    }
    
    /**
     * Сбрасывает все настройки блокировки приложений.
     */
    public void resetAllSettings() {
        try {
            Log.d(TAG, "Resetting all app lock settings");
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            SharedPreferences.Editor editor = prefs.edit();
            editor.remove(PREF_PIN_SET)
                    .remove(PREF_PIN_HASH)
                    .remove(PREF_PIN_SALT)
                    .remove(PREF_PIN_CODE) // Удаляем и старый ключ для совместимости
                    .remove(PREF_LOCKED_APPS)
                    .remove(PREF_LAST_ACTIVE_APP)
                    .remove(PREF_APP_CATEGORIES);
            
            // Удаляем все категории приложений
            String[] categories = {
                CATEGORY_SOCIAL,
                CATEGORY_FINANCE,
                CATEGORY_GAMES,
                CATEGORY_SHOPPING,
                CATEGORY_MESSAGING
            };
            
            for (String category : categories) {
                editor.remove(PREF_APPS_BY_CATEGORY_PREFIX + category);
            }
            
            editor.apply();
            
            // Также удаляем права администратора, если они были предоставлены
            if (dpm != null && adminComponent != null && isAdminActive()) {
                dpm.removeActiveAdmin(adminComponent);
            }
            
            Log.d(TAG, "All app lock settings have been reset");
        } catch (Exception e) {
            Log.e(TAG, "Error resetting app lock settings", e);
        }
    }
} 