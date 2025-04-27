package com.example.secuphone.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppPermissionScanner {
    private static final String TAG = "AppPermissionScanner";
    
    private static final String CAMERA_PERMISSION = Manifest.permission.CAMERA;
    private static final String MICROPHONE_PERMISSION = Manifest.permission.RECORD_AUDIO;
    private static final String[] LOCATION_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    public static class AppScanResult {
        public List<ApplicationInfo> appList;
        public Map<String, Boolean> hasCameraPermission = new HashMap<>();
        public Map<String, Boolean> hasMicrophonePermission = new HashMap<>();
        public Map<String, Boolean> hasLocationPermission = new HashMap<>();
        public int totalAppsWithSensitivePermissions;

        public AppScanResult(List<ApplicationInfo> appList,
                             Map<String, Boolean> hasCameraPermission,
                             Map<String, Boolean> hasMicrophonePermission,
                             Map<String, Boolean> hasLocationPermission,
                             int totalAppsWithSensitivePermissions) {
            this.appList = appList;
            this.hasCameraPermission = hasCameraPermission;
            this.hasMicrophonePermission = hasMicrophonePermission;
            this.hasLocationPermission = hasLocationPermission;
            this.totalAppsWithSensitivePermissions = totalAppsWithSensitivePermissions;
        }
    }

    public static AppScanResult scanInstalledApps(Context context) {
        try {
            PackageManager packageManager = context.getPackageManager();
            List<ApplicationInfo> allApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);
            List<ApplicationInfo> nonSystemApps = new ArrayList<>();
            
            // Filter out system apps
            for (ApplicationInfo app : allApps) {
                if ((app.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                    nonSystemApps.add(app);
                }
            }
    
            // Sort apps alphabetically by name
            Collections.sort(nonSystemApps, (a1, a2) -> {
                String name1 = a1.loadLabel(packageManager).toString();
                String name2 = a2.loadLabel(packageManager).toString();
                return name1.compareToIgnoreCase(name2);
            });
    
            List<ApplicationInfo> appsWithSensitivePermissions = new ArrayList<>();
            Map<String, Boolean> hasCameraPermission = new HashMap<>();
            Map<String, Boolean> hasMicrophonePermission = new HashMap<>();
            Map<String, Boolean> hasLocationPermission = new HashMap<>();
            int totalAppsWithSensitivePermissions = 0;
    
            // Check app permissions
            for (ApplicationInfo app : nonSystemApps) {
                try {
                    String packageName = app.packageName;
                    
                    // Check if permissions are granted in manifest
                    boolean hasCamera = hasPermissionInManifest(context, packageName, CAMERA_PERMISSION);
                    boolean hasMicrophone = hasPermissionInManifest(context, packageName, MICROPHONE_PERMISSION);
                    boolean hasLocation = hasLocationPermissionInManifest(context, packageName);
    
                    hasCameraPermission.put(packageName, hasCamera);
                    hasMicrophonePermission.put(packageName, hasMicrophone);
                    hasLocationPermission.put(packageName, hasLocation);
    
                    if (hasCamera || hasMicrophone || hasLocation) {
                        appsWithSensitivePermissions.add(app);
                        totalAppsWithSensitivePermissions++;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error checking app permissions: " + app.packageName, e);
                }
            }
    
            return new AppScanResult(
                    appsWithSensitivePermissions,
                    hasCameraPermission,
                    hasMicrophonePermission,
                    hasLocationPermission,
                    totalAppsWithSensitivePermissions
            );
        } catch (Exception e) {
            Log.e(TAG, "Error scanning apps", e);
            return new AppScanResult(
                new ArrayList<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                0
            );
        }
    }
    
    /**
     * Refresh permission status for a specific app
     * @return true if app still has sensitive permissions, false otherwise
     */
    public static boolean refreshAppPermissions(Context context, String packageName, 
                                            Map<String, Boolean> cameraPermissions,
                                            Map<String, Boolean> micPermissions,
                                            Map<String, Boolean> locationPermissions) {
        try {
            // Check permissions
            boolean hasCamera = hasPermissionInManifest(context, packageName, CAMERA_PERMISSION);
            boolean hasMic = hasPermissionInManifest(context, packageName, MICROPHONE_PERMISSION);
            boolean hasLocation = hasLocationPermissionInManifest(context, packageName);
            
            // Update permission maps
            cameraPermissions.put(packageName, hasCamera);
            micPermissions.put(packageName, hasMic);
            locationPermissions.put(packageName, hasLocation);
            
            // Return whether the app still has any sensitive permissions
            return hasCamera || hasMic || hasLocation;
        } catch (Exception e) {
            Log.e(TAG, "Error refreshing permissions for " + packageName, e);
            return false;
        }
    }

    /**
     * Check if the app has the specified permission in its manifest
     */
    private static boolean hasPermissionInManifest(Context context, String packageName, String permission) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(
                    packageName, PackageManager.GET_PERMISSIONS);
            
            if (packageInfo.requestedPermissions != null) {
                for (String requestedPermission : packageInfo.requestedPermissions) {
                    if (permission.equals(requestedPermission)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking permission for " + packageName, e);
        }
        return false;
    }

    /**
     * Check if the app has any location permission in its manifest
     */
    private static boolean hasLocationPermissionInManifest(Context context, String packageName) {
        for (String permission : LOCATION_PERMISSIONS) {
            if (hasPermissionInManifest(context, packageName, permission)) {
                return true;
            }
        }
        return false;
    }
} 