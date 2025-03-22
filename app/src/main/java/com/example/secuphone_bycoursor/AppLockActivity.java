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
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.secuphone_bycoursor.services.AppLockService;

public class AppLockActivity extends AppCompatActivity {

    private LinearLayout snapchatApp;
    private LinearLayout facebookApp;
    private LinearLayout tiktokApp;
    private LinearLayout whatsappApp;
    
    private ImageView snapchatLockIcon;
    private ImageView facebookLockIcon;
    private ImageView tiktokLockIcon;
    private ImageView whatsappLockIcon;
    
    private CardView pinSetupCard;
    private EditText pinInput;
    private EditText confirmPinInput;
    private Button setPinButton;
    
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
        
        // Setup back navigation
        ImageButton menuButton = findViewById(R.id.menu_button);
        menuButton.setOnClickListener(v -> finish());
        
        // Initialize app views
        snapchatApp = findViewById(R.id.snapchat_app);
        facebookApp = findViewById(R.id.facebook_app);
        tiktokApp = findViewById(R.id.tiktok_app);
        whatsappApp = findViewById(R.id.whatsapp_app);
        
        // Find lock icons
        snapchatLockIcon = snapchatApp.findViewById(R.id.snapchat_lock_icon);
        facebookLockIcon = facebookApp.findViewById(R.id.facebook_lock_icon);
        tiktokLockIcon = tiktokApp.findViewById(R.id.tiktok_lock_icon);
        whatsappLockIcon = whatsappApp.findViewById(R.id.whatsapp_lock_icon);
        
        // Initialize PIN setup card
        pinSetupCard = findViewById(R.id.pin_setup_card);
        pinInput = findViewById(R.id.pin_input);
        confirmPinInput = findViewById(R.id.confirm_pin_input);
        setPinButton = findViewById(R.id.set_pin_button);
        
        // Load current lock statuses
        loadLockStatuses();
        
        // Set initial lock states
        updateLockIcons();
        
        // Setup app click listeners
        setupAppClickListeners();
        
        // Setup PIN button listener
        setPinButton.setOnClickListener(this::onSetPinClicked);
        
        // Check if we have necessary permissions
        checkAndRequestPermissions();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // Check if PIN is set and update UI
        boolean isPinSet = AppLockService.isPinSet(this);
        if (isPinSet) {
            pinSetupCard.setVisibility(View.GONE);
            // Reload lock statuses in case they were changed elsewhere
            loadLockStatuses();
            updateLockIcons();
        } else {
            pinSetupCard.setVisibility(View.VISIBLE);
            // Show a toast to guide the user if this is not the first load
            if (snapchatLockIcon != null) {
                Toast.makeText(this, R.string.setup_pin_first, Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void loadLockStatuses() {
        isSnapchatLocked = AppLockService.isAppLocked(this, PACKAGE_SNAPCHAT);
        isFacebookLocked = AppLockService.isAppLocked(this, PACKAGE_FACEBOOK);
        isTiktokLocked = AppLockService.isAppLocked(this, PACKAGE_TIKTOK);
        isWhatsappLocked = AppLockService.isAppLocked(this, PACKAGE_WHATSAPP);
    }
    
    private void setupAppClickListeners() {
        snapchatApp.setOnClickListener(v -> toggleAppLock(PACKAGE_SNAPCHAT, isSnapchatLocked));
        facebookApp.setOnClickListener(v -> toggleAppLock(PACKAGE_FACEBOOK, isFacebookLocked));
        tiktokApp.setOnClickListener(v -> toggleAppLock(PACKAGE_TIKTOK, isTiktokLocked));
        whatsappApp.setOnClickListener(v -> toggleAppLock(PACKAGE_WHATSAPP, isWhatsappLocked));
    }
    
    private void toggleAppLock(String packageName, boolean currentLockState) {
        // Only allow toggling if PIN is set
        if (AppLockService.isPinSet(this)) {
            boolean newLockState = !currentLockState;
            
            // Update lock state
            AppLockService.setAppLockStatus(this, packageName, newLockState);
            
            // Update the UI
            loadLockStatuses();
            updateLockIcons();
            
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
            pinInput.requestFocus();
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
    
    private void updateLockIcons() {
        // Update lock icons based on current state
        updateLockIcon(snapchatLockIcon, isSnapchatLocked);
        updateLockIcon(facebookLockIcon, isFacebookLocked);
        updateLockIcon(tiktokLockIcon, isTiktokLocked);
        updateLockIcon(whatsappLockIcon, isWhatsappLocked);
    }
    
    private void updateLockIcon(ImageView lockIcon, boolean isLocked) {
        if (lockIcon != null) {
            lockIcon.setImageResource(isLocked ? R.drawable.ic_lock_closed : R.drawable.ic_lock_open);
            lockIcon.setContentDescription(isLocked ? getString(R.string.locked) : getString(R.string.unlocked));
        }
    }
    
    private void onSetPinClicked(View view) {
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
        pinSetupCard.setVisibility(View.GONE);
        
        // Check permissions and start service if needed
        checkAndRequestPermissions();
    }
    
    private void startAppLockService() {
        Intent serviceIntent = new Intent(this, AppLockService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }
    
    private boolean hasUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, 
                android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }
    
    private boolean hasOverlayPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || 
               Settings.canDrawOverlays(this);
    }
    
    private void checkAndRequestPermissions() {
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
    }
    
    private void showUsageAccessPermissionDialog() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.usage_access_required)
            .setMessage(R.string.usage_access_required)
            .setPositiveButton(R.string.grant_usage_access, (dialog, which) -> {
                Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                usageAccessLauncher.launch(intent);
            })
            .setNegativeButton(R.string.cancel, (dialog, which) -> {
                Toast.makeText(this, R.string.usage_access_required, Toast.LENGTH_SHORT).show();
            })
            .setCancelable(false)
            .show();
    }
    
    private void showOverlayPermissionDialog() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.overlay_permission_required)
            .setMessage(R.string.overlay_permission_required)
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
    }
} 