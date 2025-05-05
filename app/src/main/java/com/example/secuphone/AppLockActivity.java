package com.example.secuphone;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.example.secuphone.utils.AppLockPreferences;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class AppLockActivity extends AppCompatActivity {

    private static final String TAG = "AppLockActivity";
    private static final int PIN_SETUP_REQUEST_CODE = 100;
    
    private MaterialButton setPinButton;
    private MaterialButton accessibilityPermissionButton;
    private CardView pinSetupCard;
    private CardView permissionCard;
    private LinearLayout appListContainer;
    private TextView noAppsText;
    
    // Feature status UI elements
    private ImageView featureStatusIcon;
    private TextView featureStatusText;
    private TextView featureStatusDetail;
    
    private AppLockPreferences appLockPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_lock);
        
        // Initialize AppLockPreferences
        appLockPreferences = new AppLockPreferences(this);
        
        initializeViews();
        setupListeners();
        updateFeatureStatus();
    }
    
    private void initializeViews() {
        try {
            // Initialize back navigation
            ImageButton backButton = findViewById(R.id.back_button);
            backButton.setOnClickListener(v -> finish());
            
            // Setup info button
            ImageButton infoButton = findViewById(R.id.info_button);
            infoButton.setOnClickListener(v -> showInfoDialog());
            
            // Initialize PIN setup card and button
            pinSetupCard = findViewById(R.id.pin_setup_card);
            setPinButton = findViewById(R.id.set_pin_button);
            
            // Initialize permission card and button
            permissionCard = findViewById(R.id.permission_card);
            accessibilityPermissionButton = findViewById(R.id.accessibility_permission_button);
            
            // Initialize app list container
            appListContainer = findViewById(R.id.app_list_container);
            noAppsText = findViewById(R.id.no_apps_text);
            
            // Initialize feature status views
            featureStatusIcon = findViewById(R.id.feature_status_icon);
            featureStatusText = findViewById(R.id.feature_status_text);
            featureStatusDetail = findViewById(R.id.feature_status_detail);
            
        } catch (Exception e) {
            Log.e(TAG, "Error initializing views", e);
            Toast.makeText(this, "Error initializing UI: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void setupListeners() {
        try {
            // Setup PIN button to launch PinSetupActivity
            setPinButton.setOnClickListener(v -> openPinSetupScreen());
            
            // Setup accessibility permission button listener
            accessibilityPermissionButton.setOnClickListener(v -> openAccessibilitySettings());
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting up listeners", e);
            Toast.makeText(this, "Error setting up UI: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void openPinSetupScreen() {
        try {
            Intent intent = new Intent(this, PinSetupActivity.class);
            startActivityForResult(intent, PIN_SETUP_REQUEST_CODE);
        } catch (Exception e) {
            Log.e(TAG, "Error opening PIN setup screen", e);
            Toast.makeText(this, "Error opening PIN setup", Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == PIN_SETUP_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // PIN setup was successful
                Log.d(TAG, "PIN setup successful");
                // Update UI to reflect the new state
                updateFeatureStatus();
                // Load apps only if PIN is set and accessibility service is enabled
                if (appLockPreferences.isPinSet() && isAccessibilityServiceEnabled()) {
                    loadInstalledApps();
                }
            } else {
                Log.d(TAG, "PIN setup canceled or failed");
            }
        }
    }
    
    private void refreshUI() {
        // Check if PIN is set
        boolean isPinSet = appLockPreferences.isPinSet();
        pinSetupCard.setVisibility(isPinSet ? View.GONE : View.VISIBLE);
        
        // Check accessibility permission
        boolean hasAccessibilityPermission = isAccessibilityServiceEnabled();
        permissionCard.setVisibility(isPinSet && !hasAccessibilityPermission ? View.VISIBLE : View.GONE);
        
        // Load apps if PIN is set and accessibility is enabled
        if (isPinSet && hasAccessibilityPermission) {
            loadInstalledApps();
        } else {
            appListContainer.setVisibility(View.GONE);
            noAppsText.setVisibility(View.GONE);
        }
        
        // Update feature status
        updateFeatureStatus();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        refreshUI();
    }
    
    private boolean isAccessibilityServiceEnabled() {
        String serviceName = getPackageName() + "/com.example.secuphone.services.AppLockAccessibilityService";
        String enabledServices = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        
        return enabledServices != null && enabledServices.contains(serviceName);
    }
    
    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
        
        // Show a toast explaining what to do
        Toast.makeText(this, "Please enable SecuPhone App Lock in Accessibility Services", 
                Toast.LENGTH_LONG).show();
    }
    
    private void loadInstalledApps() {
        try {
            // Clear existing apps
            appListContainer.removeAllViews();
            
            // Get user-installed apps
            List<ApplicationInfo> userApps = getUserInstalledApps();
            
            if (userApps.isEmpty()) {
                noAppsText.setVisibility(View.VISIBLE);
                appListContainer.setVisibility(View.GONE);
                return;
            }
            
            noAppsText.setVisibility(View.GONE);
            appListContainer.setVisibility(View.VISIBLE);
            
            // Get currently locked apps
            Set<String> lockedApps = appLockPreferences.getLockedApps();
            
            // Add each app to the list
            for (ApplicationInfo appInfo : userApps) {
                addAppToList(appInfo, lockedApps.contains(appInfo.packageName));
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading apps", e);
            Toast.makeText(this, "Error loading apps: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private List<ApplicationInfo> getUserInstalledApps() {
        try {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            List<ApplicationInfo> userApps = new ArrayList<>();
            
            for (ApplicationInfo appInfo : installedApps) {
                // Filter out system apps and our own app
                if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0 && 
                    !appInfo.packageName.equals(getPackageName())) {
                    userApps.add(appInfo);
                }
            }
            
            // Sort apps by name
            Collections.sort(userApps, new Comparator<ApplicationInfo>() {
                @Override
                public int compare(ApplicationInfo a1, ApplicationInfo a2) {
                    return pm.getApplicationLabel(a1).toString()
                            .compareToIgnoreCase(pm.getApplicationLabel(a2).toString());
                }
            });
            
            return userApps;
        } catch (Exception e) {
            Log.e(TAG, "Error getting installed apps", e);
            return new ArrayList<>();
        }
    }
    
    private void addAppToList(ApplicationInfo appInfo, boolean isLocked) {
        try {
            LayoutInflater inflater = LayoutInflater.from(this);
            View appItemView = inflater.inflate(R.layout.item_app_lock, appListContainer, false);
            
            // Get views
            ImageView appIconView = appItemView.findViewById(R.id.app_icon);
            TextView appNameView = appItemView.findViewById(R.id.app_name);
            TextView appPackageView = appItemView.findViewById(R.id.app_package);
            CheckBox lockCheckBox = appItemView.findViewById(R.id.lock_checkbox);
            
            // Set app info
            PackageManager pm = getPackageManager();
            appIconView.setImageDrawable(pm.getApplicationIcon(appInfo));
            appNameView.setText(pm.getApplicationLabel(appInfo));
            appPackageView.setText(appInfo.packageName);
            
            // Set checkbox state without triggering listener
            lockCheckBox.setOnCheckedChangeListener(null);
            lockCheckBox.setChecked(isLocked);
            
            // Create an OnClickListener for the item
            View.OnClickListener itemClickListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean newState = !lockCheckBox.isChecked();
                    lockCheckBox.setChecked(newState);
                    
                    // Update locked status
                    updateAppLockStatus(appInfo, newState);
                }
            };
            
            // Apply the click listener to the entire item view
            appItemView.setOnClickListener(itemClickListener);
            
            // Set OnCheckedChangeListener for the checkbox
            lockCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (buttonView.isPressed()) {
                        // User directly interacted with the checkbox
                        updateAppLockStatus(appInfo, isChecked);
                    }
                }
            });
            
            // Add view to container
            appListContainer.addView(appItemView);
            
        } catch (Exception e) {
            Log.e(TAG, "Error adding app to list: " + appInfo.packageName, e);
        }
    }
    
    /**
     * Helper method to update app lock status and show feedback
     */
    private void updateAppLockStatus(ApplicationInfo appInfo, boolean isLocked) {
        try {
            String appName = getPackageManager().getApplicationLabel(appInfo).toString();
            
            if (isLocked) {
                appLockPreferences.addLockedApp(appInfo.packageName);
                Toast.makeText(this, appName + " " + getString(R.string.locked), Toast.LENGTH_SHORT).show();
            } else {
                appLockPreferences.removeLockedApp(appInfo.packageName);
                Toast.makeText(this, appName + " " + getString(R.string.unlocked), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating app lock status", e);
        }
    }
    
    private void updateFeatureStatus() {
        boolean isPinSet = appLockPreferences.isPinSet();
        boolean hasAccessibilityPermission = isAccessibilityServiceEnabled();
        
        if (!isPinSet) {
            // PIN not set
            featureStatusIcon.setImageResource(R.drawable.ic_error);
            featureStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.error_red));
            featureStatusText.setText(R.string.feature_not_active);
            featureStatusDetail.setText(R.string.pin_not_set);
        } else if (!hasAccessibilityPermission) {
            // PIN set but missing accessibility permission
            featureStatusIcon.setImageResource(R.drawable.ic_warning);
            featureStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.warning_yellow));
            featureStatusText.setText(R.string.feature_partially_active);
            featureStatusDetail.setText(R.string.missing_accessibility_permission);
        } else {
            // Feature fully active
            featureStatusIcon.setImageResource(R.drawable.ic_check_circle);
            featureStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.success_green));
            featureStatusText.setText(R.string.feature_active);
            featureStatusDetail.setText(R.string.app_lock_active);
        }
    }
    
    private void showInfoDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.app_lock_info_title)
                .setMessage(R.string.app_lock_info_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
} 