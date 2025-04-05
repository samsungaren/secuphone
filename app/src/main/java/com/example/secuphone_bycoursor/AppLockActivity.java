package com.example.secuphone_bycoursor;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;

import com.example.secuphone_bycoursor.admin.AppLockManager;
import com.example.secuphone_bycoursor.admin.LockScreenService;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class AppLockActivity extends AppCompatActivity {

    private static final String TAG = "AppLockActivity";
    
    private TextInputEditText pinInput;
    private TextInputEditText confirmPinInput;
    private Button setPinButton;
    private Button enableAdminButton;
    private CardView pinSetupCard;
    private LinearLayout appListContainer;
    private TextView noAppsText;
    
    // Feature status UI elements
    private ImageView featureStatusIcon;
    private TextView featureStatusText;
    private TextView featureStatusDetail;
    private ImageView adminPermissionIcon;
    
    private AppLockManager appLockManager;
    private ActivityResultLauncher<Intent> adminRequestLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_app_lock);
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.app_lock_layout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        
        // Initialize AppLockManager
        appLockManager = new AppLockManager(this);
        
        // Register activity launcher for device admin request
        adminRequestLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> updateAdminStatus()
        );
        
        initializeViews();
        setupListeners();
        updateAdminStatus();
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
            
            // Initialize PIN setup
        pinSetupCard = findViewById(R.id.pin_setup_card);
        pinInput = findViewById(R.id.pin_input);
        confirmPinInput = findViewById(R.id.confirm_pin_input);
        setPinButton = findViewById(R.id.set_pin_button);
        
            // Initialize admin permission button
            enableAdminButton = findViewById(R.id.enable_admin_button);
            adminPermissionIcon = findViewById(R.id.admin_permission_icon);
            
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
            // Setup PIN button listener
                setPinButton.setOnClickListener(this::onSetPinClicked);
        
            // Setup admin button listener
            enableAdminButton.setOnClickListener(v -> requestAdminPermission());
        
        } catch (Exception e) {
            Log.e(TAG, "Error setting up listeners", e);
            Toast.makeText(this, "Error setting up UI: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        try {
            // Check admin status
            updateAdminStatus();
            
            // Check if PIN is set
            boolean isPinSet = appLockManager.isPinSet();
                pinSetupCard.setVisibility(isPinSet ? View.GONE : View.VISIBLE);
            
            // Load apps if PIN is set and admin is active
            if (isPinSet && appLockManager.isAdminActive()) {
                loadInstalledApps();
            }
            
            // Update feature status
            updateFeatureStatus();
            
        } catch (Exception e) {
            Log.e(TAG, "Error in onResume", e);
            Toast.makeText(this, "Error refreshing UI: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void onSetPinClicked(View view) {
        try {
            String pin = pinInput.getText().toString().trim();
            String confirmPin = confirmPinInput.getText().toString().trim();
            
            Log.d(TAG, "Setting PIN: pin length " + pin.length() + ", confirm length " + confirmPin.length());
        
        if (pin.isEmpty() || confirmPin.isEmpty()) {
            Toast.makeText(this, R.string.enter_pin, Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (!pin.equals(confirmPin)) {
            Toast.makeText(this, R.string.pins_dont_match, Toast.LENGTH_SHORT).show();
            pinInput.setText("");
            confirmPinInput.setText("");
            return;
        }
        
        // Save PIN
        boolean pinSetSuccess = false;
        try {
            appLockManager.setPin(pin);
            pinSetSuccess = appLockManager.isPinSet();
            Log.d(TAG, "PIN set successfully: " + pinSetSuccess);
        } catch (Exception e) {
            Log.e(TAG, "Error setting PIN in AppLockManager", e);
            pinSetSuccess = false;
        }
        
        if (pinSetSuccess) {
        Toast.makeText(this, R.string.pin_set_success, Toast.LENGTH_SHORT).show();
        
        // Hide PIN setup card
        pinSetupCard.setVisibility(View.GONE);
                
            // Request admin permissions if not yet granted
            if (!appLockManager.isAdminActive()) {
                requestAdminPermission();
            } else {
                // Load apps if admin is already active
                loadInstalledApps();
            }
            
            // Update feature status
            updateFeatureStatus();
        } else {
            Toast.makeText(this, "Failed to set PIN. Please try again.", Toast.LENGTH_LONG).show();
        }
        
    } catch (Exception e) {
        Log.e(TAG, "Error setting PIN", e);
        Toast.makeText(this, "Error setting PIN: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }
}
    
    private void updateAdminStatus() {
        boolean isAdminActive = appLockManager.isAdminActive();
        
        // Update admin permission icon
        adminPermissionIcon.setImageResource(isAdminActive ? 
            R.drawable.ic_check_circle : R.drawable.ic_error);
        adminPermissionIcon.setColorFilter(ContextCompat.getColor(this, isAdminActive ? 
            R.color.success_green : R.color.error_red));
            
        // Update admin button visibility
        enableAdminButton.setVisibility(isAdminActive ? View.GONE : View.VISIBLE);
        
        // Update feature status
        updateFeatureStatus();
    }
    
    private void requestAdminPermission() {
        try {
            Intent intent = appLockManager.getAdminRequestIntent();
            adminRequestLauncher.launch(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error requesting device admin", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void loadInstalledApps() {
        try {
            // Clear existing apps
            if (appListContainer != null) {
                appListContainer.removeAllViews();
            }
            
            // Get installed apps
            List<ApplicationInfo> userApps = appLockManager.getUserInstalledApps();
            
            if (userApps.isEmpty()) {
                if (noAppsText != null) {
                    noAppsText.setVisibility(View.VISIBLE);
                }
                return;
            }
            
            if (noAppsText != null) {
                noAppsText.setVisibility(View.GONE);
            }
            
            // Получаем категории приложений
            Set<String> socialApps = appLockManager.getAppsInCategory(AppLockManager.CATEGORY_SOCIAL);
            Set<String> financeApps = appLockManager.getAppsInCategory(AppLockManager.CATEGORY_FINANCE);
            Set<String> gameApps = appLockManager.getAppsInCategory(AppLockManager.CATEGORY_GAMES);
            Set<String> shoppingApps = appLockManager.getAppsInCategory(AppLockManager.CATEGORY_SHOPPING);
            Set<String> messagingApps = appLockManager.getAppsInCategory(AppLockManager.CATEGORY_MESSAGING);
            
            // Создаем разделы по категориям
            if (!socialApps.isEmpty()) {
                addCategoryHeader("Social Media Apps");
                for (ApplicationInfo app : userApps) {
                    if (socialApps.contains(app.packageName)) {
                        addAppToList(app);
                    }
                }
            }
            
            if (!financeApps.isEmpty()) {
                addCategoryHeader("Financial Apps");
                for (ApplicationInfo app : userApps) {
                    if (financeApps.contains(app.packageName)) {
                        addAppToList(app);
                    }
                }
            }
            
            if (!gameApps.isEmpty()) {
                addCategoryHeader("Games");
                for (ApplicationInfo app : userApps) {
                    if (gameApps.contains(app.packageName)) {
                        addAppToList(app);
                    }
                }
            }
            
            if (!shoppingApps.isEmpty()) {
                addCategoryHeader("Shopping Apps");
                for (ApplicationInfo app : userApps) {
                    if (shoppingApps.contains(app.packageName)) {
                        addAppToList(app);
                    }
                }
            }
            
            if (!messagingApps.isEmpty()) {
                addCategoryHeader("Messaging Apps");
                for (ApplicationInfo app : userApps) {
                    if (messagingApps.contains(app.packageName)) {
                        addAppToList(app);
                    }
                }
            }
            
            // Добавляем остальные приложения в раздел "Other Apps"
            Set<String> categorizedApps = new HashSet<>();
            categorizedApps.addAll(socialApps);
            categorizedApps.addAll(financeApps);
            categorizedApps.addAll(gameApps);
            categorizedApps.addAll(shoppingApps);
            categorizedApps.addAll(messagingApps);
            
            boolean hasOtherApps = false;
            for (ApplicationInfo app : userApps) {
                if (!categorizedApps.contains(app.packageName)) {
                    if (!hasOtherApps) {
                        addCategoryHeader("Other Apps");
                        hasOtherApps = true;
                    }
                    addAppToList(app);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading installed apps", e);
            Toast.makeText(this, "Error loading apps: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void addAppToList(ApplicationInfo appInfo) {
        try {
            PackageManager pm = getPackageManager();
            View appRow = getLayoutInflater().inflate(R.layout.item_app_lock, appListContainer, false);
            
            // Set app details
            ImageView appIcon = appRow.findViewById(R.id.app_icon);
            TextView appName = appRow.findViewById(R.id.app_name);
            SwitchMaterial lockSwitch = appRow.findViewById(R.id.app_lock_switch);
            
            if (appIcon == null || appName == null || lockSwitch == null) {
                Log.e(TAG, "One or more views not found in app_lock item layout");
                return;
            }
            
            // Set icon and name
            appIcon.setImageDrawable(pm.getApplicationIcon(appInfo));
            appName.setText(pm.getApplicationLabel(appInfo));
            
            // Set switch state
            String packageName = appInfo.packageName;
            boolean isLocked = appLockManager.isAppLocked(packageName);
            lockSwitch.setChecked(isLocked);
            
            // Set switch listener
            lockSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                try {
                    if (buttonView.isPressed()) {
                        // Update lock status
                        if (isChecked) {
                            try {
                                appLockManager.addLockedApp(packageName);
                                Log.d(TAG, "Added app to locked list: " + packageName);
                                
                                // Start the lock screen service if not running
                                startLockScreenService();
        } catch (Exception e) {
                                Log.e(TAG, "Error locking app", e);
                                Toast.makeText(AppLockActivity.this, 
                                    "Error locking app: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                buttonView.setChecked(false);
                                return;
                            }
                        } else {
                            appLockManager.removeLockedApp(packageName);
                            Log.d(TAG, "Removed app from locked list: " + packageName);
                        }
                        
                        // Show toast message
                        String appLabel = pm.getApplicationLabel(appInfo).toString();
                        String statusMessage = appLabel + " " + 
                            (isChecked ? getString(R.string.locked) : getString(R.string.unlocked));
                        Toast.makeText(this, statusMessage, Toast.LENGTH_SHORT).show();
                        
                        // Update feature status
                        updateFeatureStatus();
                    }
        } catch (Exception e) {
                    Log.e(TAG, "Error toggling app lock state for " + packageName, e);
                    Toast.makeText(AppLockActivity.this, 
                        "Error locking app: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    // Reset switch to previous state
                    buttonView.setChecked(!isChecked);
                }
            });
            
            // Add to container
            appListContainer.addView(appRow);
            
        } catch (Exception e) {
            Log.e(TAG, "Error adding app to list", e);
        }
    }
    
    private void updateFeatureStatus() {
        try {
            boolean isPinSet = appLockManager.isPinSet();
            boolean isAdminActive = appLockManager.isAdminActive();
            boolean hasLockedApps = !appLockManager.getLockedApps().isEmpty();
            
            // Update status icon and text
            if (!isPinSet) {
                // PIN not set - inactive
                featureStatusIcon.setImageResource(R.drawable.ic_error);
                featureStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.error_red));
                featureStatusText.setText(R.string.feature_inactive);
                featureStatusDetail.setText(R.string.feature_pin_needed);
            } else if (!isAdminActive) {
                // Admin not active - inactive
                featureStatusIcon.setImageResource(R.drawable.ic_error);
                featureStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.warning_amber));
                featureStatusText.setText(R.string.feature_inactive);
                featureStatusDetail.setText(R.string.admin_request_message);
            } else if (!hasLockedApps) {
                // No apps locked - ready but not active
                featureStatusIcon.setImageResource(R.drawable.ic_check_circle);
                featureStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.accent_primary));
                featureStatusText.setText(R.string.feature_inactive);
                featureStatusDetail.setText(R.string.choose_apps_to_lock);
            } else {
                // Active and protecting apps
                featureStatusIcon.setImageResource(R.drawable.ic_check_circle);
                featureStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.success_green));
                featureStatusText.setText(R.string.feature_active);
                featureStatusDetail.setText(R.string.feature_active_detail);
                
                // Start the lock screen service
                startLockScreenService();
            }
            
            // Update app list visibility
            if (noAppsText != null) {
                noAppsText.setVisibility((isPinSet && isAdminActive) ? View.GONE : View.VISIBLE);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error updating feature status", e);
        }
    }
    
    private void startLockScreenService() {
        try {
            // Create intent for the service
            Intent serviceIntent = new Intent(this, LockScreenService.class);
            
            // Add flags to ensure service starts properly
            serviceIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            
            // Start the service as a foreground service
            Log.d(TAG, "Starting lock screen service as foreground service");
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            
            Log.d(TAG, "Lock screen service start command sent");
        } catch (Exception e) {
            Log.e(TAG, "Error starting lock screen service", e);
            Toast.makeText(this, "Error starting lock service: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showInfoDialog() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.app_lock_info)
            .setMessage(R.string.app_lock_explanation)
            .setPositiveButton(R.string.ok, null)
            .show();
    }
    
    /**
     * Добавляет заголовок категории в список приложений
     */
    private void addCategoryHeader(String categoryName) {
        try {
            View header = getLayoutInflater().inflate(R.layout.item_category_header, appListContainer, false);
            TextView headerText = header.findViewById(R.id.category_header_text);
            
            if (headerText != null) {
                headerText.setText(categoryName);
            }
            
            appListContainer.addView(header);
        } catch (Exception e) {
            Log.e(TAG, "Error adding category header", e);
        }
    }
} 