package com.example.secuphone_bycoursor.utils;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.secuphone_bycoursor.R;
import com.example.secuphone_bycoursor.StoragePermissionActivity;

/**
 * Utility class to manage permissions across the app
 */
public class PermissionManager {

    private static final String PREF_STORAGE_PERMISSION_REQUESTED = "storage_permission_requested";
    private static final String PREF_CAMERA_PERMISSION_REQUESTED = "camera_permission_requested";
    private static final String PREF_STORAGE_RATIONALE_SHOWN = "storage_rationale_shown";
    private static final int REQUEST_STORAGE_PERMISSION = 100;
    private static final int REQUEST_CAMERA_PERMISSION = 101;

    private final AppCompatActivity activity;
    private final SharedPreferences preferences;
    
    private ActivityResultLauncher<Intent> storagePermissionLauncher;
    private ActivityResultLauncher<String> directStoragePermissionLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;
    private OnPermissionResultListener listener;

    public interface OnPermissionResultListener {
        void onPermissionGranted();
        void onPermissionDenied();
    }

    public PermissionManager(AppCompatActivity activity) {
        this.activity = activity;
        this.preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        
        // Initialize storage permission launcher (for custom UI approach)
        storagePermissionLauncher = activity.registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == AppCompatActivity.RESULT_OK) {
                    if (listener != null) {
                        listener.onPermissionGranted();
                    }
                } else {
                    if (listener != null) {
                        listener.onPermissionDenied();
                    }
                }
            }
        );
        
        // Initialize direct storage permission launcher (for simplified approach)
        directStoragePermissionLauncher = activity.registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    if (listener != null) {
                        listener.onPermissionGranted();
                    }
                } else {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(activity, getStoragePermission())) {
                        showStorageRationaleDialog();
                    } else {
                        showStoragePermissionDeniedDialog();
                    }
                }
            }
        );
        
        // Initialize camera permission launcher
        cameraPermissionLauncher = activity.registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    if (listener != null) {
                        listener.onPermissionGranted();
                    }
                } else {
                    showCameraPermissionDeniedDialog();
                }
            }
        );
    }

    /**
     * Check if storage permission is granted, if not show permission UI
     * @param listener Callback listener for permission result
     */
    public void checkStoragePermission(OnPermissionResultListener listener) {
        this.listener = listener;
        
        if (StoragePermissionActivity.hasStoragePermission(activity)) {
            if (listener != null) {
                listener.onPermissionGranted();
            }
        } else {
            // Check if we should show the rationale or go directly to system dialog
            boolean hasRequested = preferences.getBoolean(PREF_STORAGE_PERMISSION_REQUESTED, false);
            
            if (!hasRequested) {
                // Show our custom permission UI first time
                Intent intent = new Intent(activity, StoragePermissionActivity.class);
                storagePermissionLauncher.launch(intent);
                
                // Save that we've requested permission
                preferences.edit().putBoolean(PREF_STORAGE_PERMISSION_REQUESTED, true).apply();
            } else {
                // On subsequent requests, use direct permission request
                requestDirectStoragePermission();
            }
        }
    }
    
    /**
     * Simplified method to check and request storage permission directly
     * Bypasses the custom UI for faster permission granting
     * 
     * @param listener Callback listener for permission result
     */
    public void requestDirectStoragePermission() {
        if (StoragePermissionActivity.hasStoragePermission(activity)) {
            if (listener != null) {
                listener.onPermissionGranted();
            }
        } else {
            // Check if we should show a quick rationale dialog
            boolean rationaleShown = preferences.getBoolean(PREF_STORAGE_RATIONALE_SHOWN, false);
            
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, getStoragePermission()) 
                    && !rationaleShown) {
                // Show a quick rationale dialog
                new AlertDialog.Builder(activity)
                    .setTitle(R.string.storage_permission)
                    .setMessage(R.string.storage_permission_explanation)
                    .setPositiveButton(R.string.grant_permission, (dialog, which) -> {
                        preferences.edit().putBoolean(PREF_STORAGE_RATIONALE_SHOWN, true).apply();
                        directStoragePermissionLauncher.launch(getStoragePermission());
                    })
                    .setNegativeButton(R.string.not_now, (dialog, which) -> {
                        if (listener != null) {
                            listener.onPermissionDenied();
                        }
                    })
                    .setCancelable(false)
                    .show();
            } else {
                // Launch the permission request directly
                directStoragePermissionLauncher.launch(getStoragePermission());
            }
        }
    }
    
    /**
     * Check if camera permission is granted, if not request it
     * @param listener Callback listener for permission result
     */
    public void checkCameraPermission(OnPermissionResultListener listener) {
        this.listener = listener;
        
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) 
                == PackageManager.PERMISSION_GRANTED) {
            if (listener != null) {
                listener.onPermissionGranted();
            }
        } else {
            // Request camera permission
            boolean hasRequested = preferences.getBoolean(PREF_CAMERA_PERMISSION_REQUESTED, false);
            
            if (!hasRequested) {
                // First time request
                preferences.edit().putBoolean(PREF_CAMERA_PERMISSION_REQUESTED, true).apply();
            }
            
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }
    
    /**
     * Check if both camera and storage permissions are granted
     * Requests both permissions if needed
     * 
     * @param listener Callback listener for permission result
     */
    public void checkCameraAndStoragePermissions(OnPermissionResultListener listener) {
        this.listener = listener;
        
        // First check camera permission
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) 
                == PackageManager.PERMISSION_GRANTED) {
            // Camera permission granted, now check storage
            checkStoragePermission(listener);
        } else {
            // Request camera permission first
            checkCameraPermission(new OnPermissionResultListener() {
                @Override
                public void onPermissionGranted() {
                    // Now check storage permission
                    checkStoragePermission(listener);
                }
                
                @Override
                public void onPermissionDenied() {
                    if (listener != null) {
                        listener.onPermissionDenied();
                    }
                }
            });
        }
    }
    
    /**
     * Get the appropriate storage permission based on Android version
     */
    private String getStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return Manifest.permission.READ_EXTERNAL_STORAGE;
        } else {
            return Manifest.permission.WRITE_EXTERNAL_STORAGE;
        }
    }
    
    private void showStorageRationaleDialog() {
        new AlertDialog.Builder(activity)
            .setTitle(R.string.storage_permission)
            .setMessage(R.string.storage_permission_explanation)
            .setPositiveButton(R.string.grant_permission, (dialog, which) -> {
                directStoragePermissionLauncher.launch(getStoragePermission());
            })
            .setNegativeButton(R.string.not_now, (dialog, which) -> {
                if (listener != null) {
                    listener.onPermissionDenied();
                }
            })
            .setCancelable(false)
            .show();
    }
    
    private void showStoragePermissionDeniedDialog() {
        new AlertDialog.Builder(activity)
            .setTitle(R.string.permission_denied)
            .setMessage(R.string.permission_denied_explanation)
            .setPositiveButton(R.string.open_settings, (dialog, which) -> {
                // Open app settings
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
                intent.setData(uri);
                activity.startActivity(intent);
                
                if (listener != null) {
                    listener.onPermissionDenied();
                }
            })
            .setNegativeButton(R.string.not_now, (dialog, which) -> {
                if (listener != null) {
                    listener.onPermissionDenied();
                }
            })
            .setCancelable(false)
            .show();
    }
    
    private void showCameraPermissionDeniedDialog() {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.permission_denied)
                .setMessage(R.string.camera_permission_required)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (listener != null) {
                        listener.onPermissionDenied();
                    }
                })
                .setCancelable(false)
                .show();
    }
    
    /**
     * Reset the storage permission requested flag (for testing)
     */
    public void resetStoragePermissionRequested() {
        preferences.edit()
            .putBoolean(PREF_STORAGE_PERMISSION_REQUESTED, false)
            .putBoolean(PREF_STORAGE_RATIONALE_SHOWN, false)
            .apply();
    }
    
    /**
     * Reset the camera permission requested flag (for testing)
     */
    public void resetCameraPermissionRequested() {
        preferences.edit().putBoolean(PREF_CAMERA_PERMISSION_REQUESTED, false).apply();
    }
    
    /**
     * Static helper method to check if camera permission is granted
     * 
     * @param activity The activity context
     * @return true if permission is granted, false otherwise
     */
    public static boolean hasCameraPermission(AppCompatActivity activity) {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) 
                == PackageManager.PERMISSION_GRANTED;
    }
} 