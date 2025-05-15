package com.example.secuphone.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.secuphone.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class to manage location permissions for different Android versions
 */
public class LocationPermissionManager {

    private static final String TAG = "LocationPermissionMgr";
    
    // Permission request codes
    public static final int REQUEST_LOCATION_PERMISSION = 1001;
    public static final int REQUEST_BACKGROUND_LOCATION = 1002;
    
    private final Context context;
    
    public LocationPermissionManager(Context context) {
        this.context = context;
    }
    
    /**
     * Check if we have the necessary location permissions
     */
    public boolean hasLocationPermissions() {
        boolean hasFineLocation = ContextCompat.checkSelfPermission(context, 
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        
        boolean hasCoarseLocation = ContextCompat.checkSelfPermission(context, 
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        
        return hasFineLocation && hasCoarseLocation;
    }
    
    /**
     * Check if we have background location permission (Android 10+)
     */
    public boolean hasBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ContextCompat.checkSelfPermission(context, 
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
        // Before Android 10, foreground location implies background location access
        return hasLocationPermissions();
    }
    
    /**
     * Check for notification permission (required for Android 13+ foreground services)
     */
    public boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context,
                    Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return true; // Automatically granted on pre-Android 13
    }
    
    /**
     * Request all required location permissions
     */
    public void requestLocationPermissions(Activity activity) {
        List<String> permissionsToRequest = new ArrayList<>();
        
        // Check fine and coarse location
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        
        // Notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        
        // Request permissions
        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(
                    activity,
                    permissionsToRequest.toArray(new String[0]),
                    REQUEST_LOCATION_PERMISSION
            );
        }
    }
    
    /**
     * Request background location permission - this should be a separate step
     * with clear UI explaining why it's needed, as per Android guidelines
     */
    public void requestBackgroundLocationPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) 
                    != PackageManager.PERMISSION_GRANTED) {
                
                // Show educational UI explaining the need for background location
                new AlertDialog.Builder(activity)
                        .setTitle(R.string.background_location_needed)
                        .setMessage(R.string.background_location_rationale)
                        .setPositiveButton(R.string.allow, (dialog, which) -> {
                            ActivityCompat.requestPermissions(
                                    activity,
                                    new String[] { Manifest.permission.ACCESS_BACKGROUND_LOCATION },
                                    REQUEST_BACKGROUND_LOCATION
                            );
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .create()
                        .show();
            }
        }
    }
    
    /**
     * Opens the app settings to allow the user to enable permissions manually
     */
    public void openAppSettings(Activity activity) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
        intent.setData(uri);
        activity.startActivity(intent);
    }
    
    /**
     * Helper method to handle permission results for location permissions
     */
    public boolean handlePermissionResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        boolean allGranted = true;
        
        if (requestCode == REQUEST_LOCATION_PERMISSION || requestCode == REQUEST_BACKGROUND_LOCATION) {
            if (grantResults.length > 0) {
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        allGranted = false;
                        break;
                    }
                }
            } else {
                allGranted = false;
            }
        }
        
        Log.d(TAG, "Permission result for request " + requestCode + ": " + (allGranted ? "Granted" : "Denied"));
        return allGranted;
    }
} 