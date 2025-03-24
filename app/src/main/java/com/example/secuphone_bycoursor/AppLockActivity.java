package com.example.secuphone_bycoursor;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
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

import com.example.secuphone_bycoursor.services.AppLockService;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

public class AppLockActivity extends AppCompatActivity {

    private LinearLayout snapchatApp;
    private LinearLayout facebookApp;
    private LinearLayout tiktokApp;
    private LinearLayout whatsappApp;
    
    private SwitchMaterial snapchatLockSwitch;
    private SwitchMaterial facebookLockSwitch;
    private SwitchMaterial tiktokLockSwitch;
    private SwitchMaterial whatsappLockSwitch;
    
    private CardView pinSetupCard;
    private TextInputEditText pinInput;
    private TextInputEditText confirmPinInput;
    private Button setPinButton;
    
    private LinearLayout changePinOption;
    private LinearLayout autoLockOption;
    
    // Feature status UI elements
    private ImageView featureStatusIcon;
    private TextView featureStatusText;
    private TextView featureStatusDetail;
    
    private boolean isSnapchatLocked = false;
    private boolean isFacebookLocked = false;
    private boolean isTiktokLocked = false;
    private boolean isWhatsappLocked = false;
    
    private static final String PACKAGE_SNAPCHAT = "com.snapchat.android";
    private static final String PACKAGE_FACEBOOK = "com.facebook.katana";
    private static final String PACKAGE_TIKTOK = "com.zhiliaoapp.musically";
    private static final String PACKAGE_WHATSAPP = "com.whatsapp";
    
    private ActivityResultLauncher<Intent> usageAccessLauncher;
    private ActivityResultLauncher<Intent> overlayPermissionLauncher;
    
    private ImageView usageStatsIcon;
    private ImageView overlayIcon;
    private Button usageStatsButton;
    private Button overlayButton;

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
        
        // Register activity result launchers
        usageAccessLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> checkAndRequestPermissions()
        );
        
        overlayPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> checkAndRequestPermissions()
        );
        
        initializeViews();
        setupListeners();
        loadLockStatuses();
        updateLockSwitches();
        checkAndRequestPermissions();
        updateFeatureStatus();
    }
    
    private void initializeViews() {
        try {
            // Initialize back navigation
            ImageButton backButton = findViewById(R.id.back_button);
            if (backButton != null) {
                backButton.setOnClickListener(v -> finish());
            }
            
            // Setup info button
            ImageButton infoButton = findViewById(R.id.info_button);
            if (infoButton != null) {
                infoButton.setOnClickListener(v -> showInfoDialog());
            }
        
        // Initialize app views
        snapchatApp = findViewById(R.id.snapchat_app);
        facebookApp = findViewById(R.id.facebook_app);
        tiktokApp = findViewById(R.id.tiktok_app);
        whatsappApp = findViewById(R.id.whatsapp_app);
        
            // Find lock switches
            snapchatLockSwitch = findViewById(R.id.snapchat_lock_switch);
            facebookLockSwitch = findViewById(R.id.facebook_lock_switch);
            tiktokLockSwitch = findViewById(R.id.tiktok_lock_switch);
            whatsappLockSwitch = findViewById(R.id.whatsapp_lock_switch);
        
        // Initialize PIN setup card
        pinSetupCard = findViewById(R.id.pin_setup_card);
        pinInput = findViewById(R.id.pin_input);
        confirmPinInput = findViewById(R.id.confirm_pin_input);
        setPinButton = findViewById(R.id.set_pin_button);
        
            // Initialize security settings
            changePinOption = findViewById(R.id.change_pin_option);
            autoLockOption = findViewById(R.id.auto_lock_option);
            
            // Initialize feature status views
            featureStatusIcon = findViewById(R.id.feature_status_icon);
            featureStatusText = findViewById(R.id.feature_status_text);
            featureStatusDetail = findViewById(R.id.feature_status_detail);
            
            // Initialize permission related views
            usageStatsIcon = findViewById(R.id.usage_stats_icon);
            overlayIcon = findViewById(R.id.overlay_icon);
            usageStatsButton = findViewById(R.id.usage_stats_button);
            overlayButton = findViewById(R.id.overlay_button);
        } catch (Exception e) {
            Toast.makeText(this, "Error initializing views: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void setupListeners() {
        try {
            // Setup PIN button listener
            if (setPinButton != null) {
                setPinButton.setOnClickListener(this::onSetPinClicked);
            }
        
        // Setup app click listeners
        setupAppClickListeners();
        
            // Setup security settings listeners
            setupSecuritySettingsListeners();
        } catch (Exception e) {
            Toast.makeText(this, "Error setting up listeners: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        try {
        // Check if PIN is set and update UI
        boolean isPinSet = AppLockService.isPinSet(this);
            if (pinSetupCard != null) {
                pinSetupCard.setVisibility(isPinSet ? View.GONE : View.VISIBLE);
            }
            
            // Reload lock statuses if PIN is set
        if (isPinSet) {
            loadLockStatuses();
                updateLockSwitches();
            }
            
            // Update permission statuses
            updatePermissionStatus();
            
            // Update feature status
            updateFeatureStatus();
        } catch (Exception e) {
            Toast.makeText(this, "Error in onResume: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void updateFeatureStatus() {
        try {
            boolean isPinSet = AppLockService.isPinSet(this);
            boolean hasUsageStats = hasUsageStatsPermission();
            boolean hasOverlay = hasOverlayPermission();
            boolean hasAnyLockedApps = isSnapchatLocked || isFacebookLocked || isTiktokLocked || isWhatsappLocked;
            
            // Update status UI based on current state
            if (!isPinSet) {
                // PIN not set - inactive
                if (featureStatusIcon != null) {
                    featureStatusIcon.setImageResource(R.drawable.ic_error);
                    featureStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.error_red));
                }
                if (featureStatusText != null) {
                    featureStatusText.setText(R.string.feature_inactive);
                }
                if (featureStatusDetail != null) {
                    featureStatusDetail.setText(R.string.feature_pin_needed);
                }
            } else if (!hasUsageStats || !hasOverlay) {
                // Permissions missing - inactive
                if (featureStatusIcon != null) {
                    featureStatusIcon.setImageResource(R.drawable.ic_error);
                    featureStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.warning_amber));
                }
                if (featureStatusText != null) {
                    featureStatusText.setText(R.string.feature_inactive);
                }
                if (featureStatusDetail != null) {
                    featureStatusDetail.setText(R.string.feature_permission_needed);
                }
            } else if (!hasAnyLockedApps) {
                // No apps locked - ready but not active
                if (featureStatusIcon != null) {
                    featureStatusIcon.setImageResource(R.drawable.ic_check_circle);
                    featureStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.accent_primary));
                }
                if (featureStatusText != null) {
                    featureStatusText.setText(R.string.feature_inactive);
                }
                if (featureStatusDetail != null) {
                    featureStatusDetail.setText(R.string.choose_apps_to_lock);
                }
        } else {
                // Active and protecting apps
                if (featureStatusIcon != null) {
                    featureStatusIcon.setImageResource(R.drawable.ic_check_circle);
                    featureStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.success_green));
                }
                if (featureStatusText != null) {
                    featureStatusText.setText(R.string.feature_active);
                }
                if (featureStatusDetail != null) {
                    featureStatusDetail.setText(R.string.feature_active_detail);
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error updating feature status: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void loadLockStatuses() {
        try {
        isSnapchatLocked = AppLockService.isAppLocked(this, PACKAGE_SNAPCHAT);
        isFacebookLocked = AppLockService.isAppLocked(this, PACKAGE_FACEBOOK);
        isTiktokLocked = AppLockService.isAppLocked(this, PACKAGE_TIKTOK);
        isWhatsappLocked = AppLockService.isAppLocked(this, PACKAGE_WHATSAPP);
        } catch (Exception e) {
            Toast.makeText(this, "Error loading lock statuses: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void setupAppClickListeners() {
        try {
            // Only set up listeners if the views exist
            if (snapchatApp != null) {
                snapchatApp.setOnClickListener(v -> {
                    if (AppLockService.isPinSet(this) && snapchatLockSwitch != null) {
                        snapchatLockSwitch.setChecked(!snapchatLockSwitch.isChecked());
                        toggleAppLock(PACKAGE_SNAPCHAT, isSnapchatLocked);
                    } else {
                        Toast.makeText(this, R.string.setup_pin_first, Toast.LENGTH_SHORT).show();
                    }
                });
            }
            
            if (facebookApp != null) {
                facebookApp.setOnClickListener(v -> {
                    if (AppLockService.isPinSet(this) && facebookLockSwitch != null) {
                        facebookLockSwitch.setChecked(!facebookLockSwitch.isChecked());
                        toggleAppLock(PACKAGE_FACEBOOK, isFacebookLocked);
                    } else {
                        Toast.makeText(this, R.string.setup_pin_first, Toast.LENGTH_SHORT).show();
                    }
                });
            }
            
            if (tiktokApp != null) {
                tiktokApp.setOnClickListener(v -> {
                    if (AppLockService.isPinSet(this) && tiktokLockSwitch != null) {
                        tiktokLockSwitch.setChecked(!tiktokLockSwitch.isChecked());
                        toggleAppLock(PACKAGE_TIKTOK, isTiktokLocked);
                    } else {
                        Toast.makeText(this, R.string.setup_pin_first, Toast.LENGTH_SHORT).show();
                    }
                });
            }
            
            if (whatsappApp != null) {
                whatsappApp.setOnClickListener(v -> {
                    if (AppLockService.isPinSet(this) && whatsappLockSwitch != null) {
                        whatsappLockSwitch.setChecked(!whatsappLockSwitch.isChecked());
                        toggleAppLock(PACKAGE_WHATSAPP, isWhatsappLocked);
                    } else {
                        Toast.makeText(this, R.string.setup_pin_first, Toast.LENGTH_SHORT).show();
                    }
                });
            }
            
            // Setup change listeners for the switches if they exist
            if (snapchatLockSwitch != null) {
                snapchatLockSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (buttonView.isPressed()) {  // Only react to user input, not programmatic changes
                        if (AppLockService.isPinSet(this)) {
                            toggleAppLock(PACKAGE_SNAPCHAT, isSnapchatLocked);
                        } else {
                            buttonView.setChecked(!isChecked);  // Revert switch state
                            Toast.makeText(this, R.string.setup_pin_first, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
            
            if (facebookLockSwitch != null) {
                facebookLockSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (buttonView.isPressed()) {
                        if (AppLockService.isPinSet(this)) {
                            toggleAppLock(PACKAGE_FACEBOOK, isFacebookLocked);
                        } else {
                            buttonView.setChecked(!isChecked);
                            Toast.makeText(this, R.string.setup_pin_first, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
            
            if (tiktokLockSwitch != null) {
                tiktokLockSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (buttonView.isPressed()) {
                        if (AppLockService.isPinSet(this)) {
                            toggleAppLock(PACKAGE_TIKTOK, isTiktokLocked);
                        } else {
                            buttonView.setChecked(!isChecked);
                            Toast.makeText(this, R.string.setup_pin_first, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
            
            if (whatsappLockSwitch != null) {
                whatsappLockSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (buttonView.isPressed()) {
                        if (AppLockService.isPinSet(this)) {
                            toggleAppLock(PACKAGE_WHATSAPP, isWhatsappLocked);
                        } else {
                            buttonView.setChecked(!isChecked);
                            Toast.makeText(this, R.string.setup_pin_first, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error setting up app click listeners: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void setupSecuritySettingsListeners() {
        try {
            if (changePinOption != null) {
                changePinOption.setOnClickListener(v -> {
                    if (AppLockService.isPinSet(this)) {
                        showChangePinDialog();
                    } else {
                        Toast.makeText(this, R.string.setup_pin_first, Toast.LENGTH_SHORT).show();
                        if (pinInput != null) {
                            pinInput.requestFocus();
                        }
                    }
                });
            }
            
            if (autoLockOption != null) {
                autoLockOption.setOnClickListener(v -> {
                    if (AppLockService.isPinSet(this)) {
                        showAutoLockOptionsDialog();
                    } else {
                        Toast.makeText(this, R.string.setup_pin_first, Toast.LENGTH_SHORT).show();
                        if (pinInput != null) {
                            pinInput.requestFocus();
                        }
                    }
                });
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error setting up security settings listeners: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void toggleAppLock(String packageName, boolean currentLockState) {
        try {
        // Only allow toggling if PIN is set
        if (AppLockService.isPinSet(this)) {
            boolean newLockState = !currentLockState;
            
            // Update lock state
            AppLockService.setAppLockStatus(this, packageName, newLockState);
            
            // Update the UI
            loadLockStatuses();
                updateLockSwitches();
                
                // Update feature status
                updateFeatureStatus();
            
            // Give user feedback
            String appName = getAppName(packageName);
            String status = newLockState ? getString(R.string.locked) : getString(R.string.unlocked);
            Toast.makeText(this, appName + " " + status, Toast.LENGTH_SHORT).show();
            
            // Make sure the service is running if we've locked an app
            if (newLockState) {
                startAppLockService();
            }
        } else {
            // Direct user's attention to the PIN setup
            Toast.makeText(this, R.string.setup_pin_first, Toast.LENGTH_SHORT).show();
                if (pinInput != null) {
            pinInput.requestFocus();
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error toggling app lock: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showChangePinDialog() {
        // Implementation for changing PIN
        Toast.makeText(this, R.string.coming_soon, Toast.LENGTH_SHORT).show();
    }
    
    private void showAutoLockOptionsDialog() {
        // Implementation for auto-lock options
        Toast.makeText(this, R.string.coming_soon, Toast.LENGTH_SHORT).show();
    }
    
    private void showInfoDialog() {
        try {
            new AlertDialog.Builder(this)
                .setTitle(R.string.app_lock_info)
                .setMessage(R.string.app_lock_explanation)
                .setPositiveButton(R.string.ok, null)
                .show();
        } catch (Exception e) {
            Toast.makeText(this, "Error showing info dialog: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private String getAppName(String packageName) {
        switch (packageName) {
            case PACKAGE_SNAPCHAT:
                return getString(R.string.snapchat);
            case PACKAGE_FACEBOOK:
                return getString(R.string.facebook);
            case PACKAGE_TIKTOK:
                return getString(R.string.tiktok);
            case PACKAGE_WHATSAPP:
                return getString(R.string.whatsapp);
            default:
                return "App";
        }
    }
    
    private void updateLockSwitches() {
        try {
            // Update switch states based on current lock state
            if (snapchatLockSwitch != null) {
                snapchatLockSwitch.setChecked(isSnapchatLocked);
            }
            if (facebookLockSwitch != null) {
                facebookLockSwitch.setChecked(isFacebookLocked);
            }
            if (tiktokLockSwitch != null) {
                tiktokLockSwitch.setChecked(isTiktokLocked);
            }
            if (whatsappLockSwitch != null) {
                whatsappLockSwitch.setChecked(isWhatsappLocked);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error updating lock switches: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void onSetPinClicked(View view) {
        try {
            if (pinInput == null || confirmPinInput == null) {
                Toast.makeText(this, "Error: PIN input fields not found", Toast.LENGTH_SHORT).show();
                return;
            }
            
        String pin = pinInput.getText().toString();
        String confirmPin = confirmPinInput.getText().toString();
        
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
        AppLockService.setPin(this, pin);
        Toast.makeText(this, R.string.pin_set_success, Toast.LENGTH_SHORT).show();
        
        // Hide PIN setup card
            if (pinSetupCard != null) {
        pinSetupCard.setVisibility(View.GONE);
            }
        
        // Check permissions and start service if needed
        checkAndRequestPermissions();
            
            // Update feature status
            updateFeatureStatus();
        } catch (Exception e) {
            Toast.makeText(this, "Error setting PIN: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void updatePermissionStatus() {
        try {
            // Update UI based on permission status
            boolean hasUsageStats = hasUsageStatsPermission();
            boolean hasOverlay = hasOverlayPermission();
            
            // Update usage stats permission if the view exists
            if (usageStatsIcon != null) {
                usageStatsIcon.setImageResource(hasUsageStats ? 
                        R.drawable.ic_check_circle : R.drawable.ic_error);
                usageStatsIcon.setColorFilter(ContextCompat.getColor(this, hasUsageStats ? 
                        R.color.success_green : R.color.error_red));
            }
            
            if (usageStatsButton != null) {
                usageStatsButton.setVisibility(hasUsageStats ? View.GONE : View.VISIBLE);
            }
            
            // Update overlay permission if the view exists
            if (overlayIcon != null) {
                overlayIcon.setImageResource(hasOverlay ? 
                        R.drawable.ic_check_circle : R.drawable.ic_error);
                overlayIcon.setColorFilter(ContextCompat.getColor(this, hasOverlay ? 
                        R.color.success_green : R.color.error_red));
            }
            
            if (overlayButton != null) {
                overlayButton.setVisibility(hasOverlay ? View.GONE : View.VISIBLE);
            }
            
            // Set button click listeners if the buttons exist
            if (usageStatsButton != null) {
                usageStatsButton.setOnClickListener(v -> {
                    Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                    usageAccessLauncher.launch(intent);
                });
            }
            
            if (overlayButton != null) {
                overlayButton.setOnClickListener(v -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, 
                            Uri.parse("package:" + getPackageName()));
                    overlayPermissionLauncher.launch(intent);
                });
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error updating permission status: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void startAppLockService() {
        try {
        Intent serviceIntent = new Intent(this, AppLockService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error starting app lock service: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private boolean hasUsageStatsPermission() {
        try {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            if (appOps == null) {
                return false;
            }
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, 
                android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            Toast.makeText(this, "Error checking usage stats permission: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return false;
        }
    }
    
    private boolean hasOverlayPermission() {
        try {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || 
               Settings.canDrawOverlays(this);
        } catch (Exception e) {
            Toast.makeText(this, "Error checking overlay permission: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return false;
        }
    }
    
    private void checkAndRequestPermissions() {
        try {
            // Update the permission status UI
            updatePermissionStatus();
            
        // Check for usage stats permission
        if (!hasUsageStatsPermission()) {
            showUsageAccessPermissionDialog();
            return;
        }
        
        // Check for overlay permission
        if (!hasOverlayPermission()) {
            showOverlayPermissionDialog();
            return;
        }
        
        // If all permissions are granted and PIN is set, start the service
        if (AppLockService.isPinSet(this)) {
            startAppLockService();
            }
            
            // Update feature status
            updateFeatureStatus();
        } catch (Exception e) {
            Toast.makeText(this, "Error checking permissions: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showUsageAccessPermissionDialog() {
        try {
        new AlertDialog.Builder(this)
            .setTitle(R.string.usage_access_required)
                .setMessage(R.string.usage_access_explanation)
            .setPositiveButton(R.string.grant_usage_access, (dialog, which) -> {
                Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                usageAccessLauncher.launch(intent);
            })
            .setNegativeButton(R.string.cancel, (dialog, which) -> {
                Toast.makeText(this, R.string.usage_access_required, Toast.LENGTH_SHORT).show();
            })
            .setCancelable(false)
            .show();
        } catch (Exception e) {
            Toast.makeText(this, "Error showing usage access dialog: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            
            // Try to launch the settings directly if the dialog fails
            try {
                Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                startActivity(intent);
            } catch (Exception e2) {
                Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void showOverlayPermissionDialog() {
        try {
        new AlertDialog.Builder(this)
            .setTitle(R.string.overlay_permission_required)
                .setMessage(R.string.overlay_permission_explanation)
            .setPositiveButton(R.string.grant_overlay_permission, (dialog, which) -> {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, 
                        Uri.parse("package:" + getPackageName()));
                overlayPermissionLauncher.launch(intent);
            })
            .setNegativeButton(R.string.cancel, (dialog, which) -> {
                Toast.makeText(this, R.string.overlay_permission_required, Toast.LENGTH_SHORT).show();
            })
            .setCancelable(false)
            .show();
        } catch (Exception e) {
            Toast.makeText(this, "Error showing overlay permission dialog: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            
            // Try to launch the settings directly if the dialog fails
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, 
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e2) {
                Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show();
            }
        }
    }
} 