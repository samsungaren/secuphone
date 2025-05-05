package com.example.secuphone.services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.example.secuphone.LockScreenActivity;
import com.example.secuphone.utils.AppLockPreferences;

/**
 * Accessibility service that monitors app launches to enforce app locking
 */
public class AppLockAccessibilityService extends AccessibilityService {

    private static final String TAG = "AppLockAccessibilityService";
    private String currentPackage = "";
    private String lastPackage = "";
    private String lastLockedPackage = ""; // Track which app was last locked
    private AppLockPreferences appLockPreferences;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isProcessingEvent = false; // Prevent recursive processing
    private static final int CHECK_DELAY_MS = 50; // Short delay to ensure app is visible
    private boolean lockScreenShown = false; // Track if lock screen is currently showing

    @Override
    public void onCreate() {
        super.onCreate();
        appLockPreferences = new AppLockPreferences(this);
        Log.d(TAG, "AppLockAccessibilityService created");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (isProcessingEvent) {
            return; // Prevent recursive processing
        }

        try {
            isProcessingEvent = true;
            
            // We're only interested in window state changes - when apps are opened
            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                if (event.getPackageName() == null || event.getPackageName().toString().isEmpty()) {
                    isProcessingEvent = false;
                    return;
                }
                
                currentPackage = event.getPackageName().toString();
                String className = event.getClassName() != null ? event.getClassName().toString() : "";
                
                // Log for debugging
                Log.d(TAG, "Window changed - Package: " + currentPackage + ", Class: " + className);
                
                // Skip non-activity windows and dialogs
                if (className.contains("Dialog") || 
                    className.contains("Popup") || 
                    className.contains("Toast")) {
                    isProcessingEvent = false;
                    return;
                }
                
                // Special handling for our own app
                if (currentPackage.equals(getPackageName())) {
                    // If we're showing the LockScreenActivity, set flag and preserve lastPackage
                    if (className.contains("LockScreenActivity")) {
                        Log.d(TAG, "In LockScreenActivity for app: " + lastPackage);
                        lockScreenShown = true;
                        isProcessingEvent = false;
                        return;
                    }
                    
                    // If we're in our own app but not the lock screen, update lastPackage
                    // but don't interfere with the lock screen flow
                    lastPackage = currentPackage;
                    isProcessingEvent = false;
                    return;
                }
                
                // Skip system UI elements and launchers
                if (isSystemComponent(currentPackage)) {
                    lastPackage = currentPackage;
                    isProcessingEvent = false;
                    return;
                }
                
                // Reset lock screen shown flag if we're moving to a different app
                if (!currentPackage.equals(lastPackage)) {
                    lockScreenShown = false;
                }
                                
                // Skip if it's the same package we're already in and lock screen isn't showing
                if (currentPackage.equals(lastPackage) && !lockScreenShown) {
                    isProcessingEvent = false;
                    return;
                }
                
                // Check if PIN is set - if not, we don't need to do anything
                if (!appLockPreferences.isPinSet()) {
                    lastPackage = currentPackage;
                    isProcessingEvent = false;
                    return;
                }
                
                // CRITICAL CHECK: Is this app locked?
                if (appLockPreferences.isAppLocked(currentPackage)) {
                    Log.d(TAG, "LOCKED APP DETECTED: " + currentPackage);
                    
                    // Store for reference
                    lastLockedPackage = currentPackage;
                    
                    // Check if we need to show the lock screen (not already showing)
                    if (!lockScreenShown) {
                        Log.d(TAG, "Showing lock screen for: " + currentPackage);
                        
                        // Show lock screen immediately
                        showLockScreen(currentPackage);
                        
                        // Set flag to indicate lock screen is showing
                        lockScreenShown = true;
                    }
                } else {
                    // App is not locked, reset lock screen flag
                    lockScreenShown = false;
                }
                
                // Update the last package for the next check
                lastPackage = currentPackage;
            }
        } finally {
            isProcessingEvent = false;
        }
    }
    
    /**
     * Check if the package is a system component we should ignore
     */
    private boolean isSystemComponent(String packageName) {
        return packageName.contains("systemui") || 
               packageName.contains("launcher") ||
               packageName.equals("android") ||
               packageName.contains("huawei") ||
               packageName.contains("samsung") ||
               packageName.contains("miui") ||
               packageName.contains("settings");
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "AppLockAccessibilityService interrupted");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        
        // Set flags for better detection
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        
        // Very short timeout for faster response
        info.notificationTimeout = 0; // immediate
        
        // Add flags to improve detection capability
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                     AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        
        setServiceInfo(info);
        
        Log.i(TAG, "AppLockAccessibilityService connected and configured");
    }
    
    /**
     * Show lock screen for the specified package
     */
    private void showLockScreen(String packageName) {
        try {
            // Get application label for display
            PackageManager pm = getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            String appName = pm.getApplicationLabel(appInfo).toString();
            
            // Create intent with STRONG flags to ensure it shows on top
            Intent intent = new Intent(this, LockScreenActivity.class);
            
            // These flags are crucial to ensure the lock screen appears on top
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            intent.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            
            // Pass the package info
            intent.putExtra("package_name", packageName);
            intent.putExtra("app_name", appName);
            
            // Launch with high priority
            startActivity(intent);
            
            Log.i(TAG, "Lock screen launched for app: " + packageName);
            
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Error getting app info: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error showing lock screen: " + e.getMessage());
        }
    }
} 