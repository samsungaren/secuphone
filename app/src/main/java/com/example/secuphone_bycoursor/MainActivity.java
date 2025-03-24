package com.example.secuphone_bycoursor;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceManager;

import com.example.secuphone_bycoursor.services.AppLockService;
import com.example.secuphone_bycoursor.utils.PermissionManager;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButton;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private DrawerLayout drawerLayout;
    private PermissionManager permissionManager;
    private ProgressBar securityLevelProgress;
    private ProgressBar activeFeaturesProgress;
    private TextView activeFeaturesText;
    
    // Feature status UI elements
    private View vpnStatusIndicator;
    private TextView vpnStatusText;
    private View appLockStatusIndicator;
    private TextView appLockStatusText;
    private View urlCheckerStatusIndicator;
    private TextView urlCheckerStatusText;
    private View findPhoneStatusIndicator;
    private TextView findPhoneStatusText;
    private View hiddenFilesStatusIndicator;
    private TextView hiddenFilesStatusText;
    
    // Feature status tracking
    private boolean isVpnActive = false;
    private boolean isAppLockActive = false;
    private boolean isUrlCheckerActive = true; // Always active by default
    private boolean isFindPhoneActive = false;
    private boolean isHiddenFilesActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        
        // Initialize permission manager
        permissionManager = new PermissionManager(this);
        
        // Initialize UI components
        setupNavigationDrawer();
        initializeFeatureStatusUI(); // Initialize feature status UI elements first
        setupSecurityDashboard();
        setupMenuItemListeners();
        
        // Check for required permissions
        checkAppPermissions();
        
        // Handle back button press
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // Update feature statuses whenever we return to the main activity
        checkFeatureStatuses();
        updateFeatureStatusUI();
        updateSecurityLevel();
    }
    
    private void initializeFeatureStatusUI() {
        // VPN status
        vpnStatusIndicator = findViewById(R.id.vpn_status_indicator);
        vpnStatusText = findViewById(R.id.vpn_status_text);
        
        // App Lock status
        appLockStatusIndicator = findViewById(R.id.app_lock_status_indicator);
        appLockStatusText = findViewById(R.id.app_lock_status_text);
        
        // URL Checker status
        urlCheckerStatusIndicator = findViewById(R.id.url_checker_status_indicator);
        urlCheckerStatusText = findViewById(R.id.url_checker_status_text);
        
        // Find Phone status
        findPhoneStatusIndicator = findViewById(R.id.find_phone_status_indicator);
        findPhoneStatusText = findViewById(R.id.find_phone_status_text);
        
        // Hidden Files status
        hiddenFilesStatusIndicator = findViewById(R.id.hidden_files_status_indicator);
        hiddenFilesStatusText = findViewById(R.id.hidden_files_status_text);
    }
    
    private void checkFeatureStatuses() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        
        // VPN status - check if VPN service is running
        isVpnActive = prefs.getBoolean("vpn_active", false);
        
        // App Lock status - check if PIN is set and at least one app is locked
        isAppLockActive = AppLockService.isPinSet(this) && hasLockedApps();
        
        // URL Checker is always active by default
        isUrlCheckerActive = true;
        
        // Find Phone status - check if camera permission is granted
        isFindPhoneActive = permissionManager.hasCameraPermission() && permissionManager.hasStoragePermission();
        
        // Hidden Files status - check if storage permission is granted and feature is enabled
        isHiddenFilesActive = permissionManager.hasStoragePermission() && 
                              prefs.getBoolean("hidden_files_active", false);
    }
    
    private boolean hasLockedApps() {
        // Check if at least one app is locked
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        return !prefs.getStringSet("locked_apps", java.util.Collections.emptySet()).isEmpty();
    }
    
    private void updateFeatureStatusUI() {
        // Update VPN status
        updateStatusIndicator(vpnStatusIndicator, vpnStatusText, 
                isVpnActive, R.string.status_enabled, R.string.status_disabled);
        
        // Update App Lock status
        if (AppLockService.isPinSet(this)) {
            updateStatusIndicator(appLockStatusIndicator, appLockStatusText, 
                    isAppLockActive, R.string.status_enabled, R.string.status_disabled);
        } else {
            // PIN not set
            appLockStatusIndicator.setBackground(ContextCompat.getDrawable(this, R.drawable.feature_status_badge_inactive));
            appLockStatusText.setText(R.string.status_setup_needed);
        }
        
        // Update URL Checker status (always enabled)
        updateStatusIndicator(urlCheckerStatusIndicator, urlCheckerStatusText, 
                isUrlCheckerActive, R.string.status_enabled, R.string.status_disabled);
        
        // Update Find Phone status
        updateStatusIndicator(findPhoneStatusIndicator, findPhoneStatusText, 
                isFindPhoneActive, R.string.status_enabled, R.string.status_disabled);
        
        // Update Hidden Files status
        updateStatusIndicator(hiddenFilesStatusIndicator, hiddenFilesStatusText, 
                isHiddenFilesActive, R.string.status_enabled, R.string.status_disabled);
    }
    
    private void updateStatusIndicator(View indicator, TextView statusText, 
                                       boolean isActive, int enabledStringId, int disabledStringId) {
        if (isActive) {
            indicator.setBackground(ContextCompat.getDrawable(this, R.drawable.feature_status_badge_active));
            statusText.setText(enabledStringId);
        } else {
            indicator.setBackground(ContextCompat.getDrawable(this, R.drawable.feature_status_badge_inactive));
            statusText.setText(disabledStringId);
        }
    }
    
    private void setupNavigationDrawer() {
        drawerLayout = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);
        
        // Get reference to header view for accessing UI elements
        View headerView = navigationView.getHeaderView(0);
        
        // Get references to the guest and user UI containers
        LinearLayout guestUI = headerView.findViewById(R.id.guest_ui);
        LinearLayout userUI = headerView.findViewById(R.id.user_ui);
        
        // For demo, show guest UI by default
        guestUI.setVisibility(View.VISIBLE);
        userUI.setVisibility(View.GONE);
        
        // Set up sign in button
        MaterialButton signInButton = headerView.findViewById(R.id.sign_in_button);
        signInButton.setOnClickListener(v -> {
            Toast.makeText(this, "Sign In feature coming soon", Toast.LENGTH_SHORT).show();
            // For demo, toggle UI when clicked
            guestUI.setVisibility(View.GONE);
            userUI.setVisibility(View.VISIBLE);
        });
        
        // Set up sign up button
        MaterialButton signUpButton = headerView.findViewById(R.id.sign_up_button);
        signUpButton.setOnClickListener(v -> {
            Toast.makeText(this, "Sign Up feature coming soon", Toast.LENGTH_SHORT).show();
        });
        
        // Set up upgrade text click listener
        TextView upgradeText = headerView.findViewById(R.id.upgrade_text);
        upgradeText.setOnClickListener(v -> {
            Toast.makeText(this, "Upgrade feature coming soon", Toast.LENGTH_SHORT).show();
        });
        
        // Setup the navigation drawer functionality
        navigationView.setNavigationItemSelectedListener(this);
        
        // Toggle drawer with toolbar
        ImageButton menuButton = findViewById(R.id.menu_button);
        menuButton.setOnClickListener(v -> {
            drawerLayout.openDrawer(GravityCompat.START);
        });
    }
    
    private void setupSecurityDashboard() {
        // Initialize security level progress
        securityLevelProgress = findViewById(R.id.security_level_progress);
        activeFeaturesProgress = findViewById(R.id.active_features_progress);
        activeFeaturesText = findViewById(R.id.active_features_text);
        
        // Set initial values (these will be updated in onResume)
        securityLevelProgress.setProgress(0);
        activeFeaturesProgress.setProgress(0);
    }
    
    private void setupMenuItemListeners() {
        // Setup VPN Button
        findViewById(R.id.vpn_button).setOnClickListener(v -> {
            startActivity(new Intent(this, VPNActivity.class));
        });
        
        // Setup App Lock Button
        findViewById(R.id.app_lock_button).setOnClickListener(v -> {
            startActivity(new Intent(this, AppLockActivity.class));
        });
        
        // Setup URL Checker Button
        findViewById(R.id.url_checker_button).setOnClickListener(v -> {
            startActivity(new Intent(this, URLCheckerActivity.class));
        });
        
        // Setup Find Phone Button
        findViewById(R.id.find_phone_button).setOnClickListener(v -> {
            if (checkAppPermissions()) {
                // Start FindPhoneActivity (to be implemented)
                Toast.makeText(this, "Find Phone feature activated", Toast.LENGTH_SHORT).show();
            }
        });
        
        // Setup Hidden Files Button
        findViewById(R.id.hidden_files_button).setOnClickListener(v -> {
            Toast.makeText(this, "Hidden Files feature coming soon", Toast.LENGTH_SHORT).show();
        });
        
        // Setup Coming Soon Button
        findViewById(R.id.coming_soon_button).setOnClickListener(v -> {
            Toast.makeText(this, "More features coming soon!", Toast.LENGTH_SHORT).show();
        });
        
        // Setup Premium Feature Buttons
        setupPremiumFeatureButton(R.id.password_manager_button, "Password Manager");
        setupPremiumFeatureButton(R.id.secure_notes_button, "Secure Notes");
        setupPremiumFeatureButton(R.id.secure_cloud_button, "Secure Cloud Storage");
        setupPremiumFeatureButton(R.id.network_scanner_button, "Network Scanner");
        
        // Setup new Premium Feature Buttons
        setupPremiumFeatureButton(R.id.secure_file_sharing_button, "Secure File Sharing");
        setupPremiumFeatureButton(R.id.ad_blocker_button, "Ad Blocker");
        setupPremiumFeatureButton(R.id.qr_scanner_button, "QR Code Scanner");
    }
    
    /**
     * Sets up a premium feature button with a click listener that shows a premium upgrade dialog
     * @param buttonId The ID of the button to set up
     * @param featureName The name of the feature for the toast message
     */
    private void setupPremiumFeatureButton(int buttonId, String featureName) {
        findViewById(buttonId).setOnClickListener(v -> {
            showPremiumFeatureDialog(featureName);
        });
    }
    
    /**
     * Shows a dialog for premium features that requires upgrade
     * @param featureName The name of the premium feature
     */
    private void showPremiumFeatureDialog(String featureName) {
        new MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.premium_required))
            .setMessage(featureName + " " + getString(R.string.secret_feature_locked))
            .setPositiveButton(getString(R.string.upgrade_now), (dialog, which) -> {
                Toast.makeText(this, "Upgrade feature coming soon", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton(getString(R.string.cancel), null)
            .show();
    }
    
    /**
     * Check if the app has all required permissions on startup
     */
    private boolean checkAppPermissions() {
        // We'll proactively check for storage permissions on startup
        // since multiple features require it
        if (!permissionManager.hasStoragePermission()) {
            permissionManager.checkStoragePermission(new PermissionManager.OnPermissionResultListener() {
                @Override
                public void onPermissionGranted() {
                    // Update security level if needed
                    checkFeatureStatuses();
                    updateFeatureStatusUI();
                    updateSecurityLevel();
                }
                
                @Override
                public void onPermissionDenied() {
                    // That's okay, user can grant later when needed
                    updateSecurityLevel();
                }
            });
            return false;
        } else {
            checkFeatureStatuses();
            updateFeatureStatusUI();
            updateSecurityLevel();
            return true;
        }
    }
    
    /**
     * Calculate security level based on enabled features
     */
    private int calculateSecurityLevel() {
        int baseLevel = 20; // Base security level with no features
        int totalPoints = 0;
        
        // VPN adds 25 points
        if (isVpnActive) totalPoints += 25;
        
        // App Lock adds 20 points
        if (isAppLockActive) totalPoints += 20;
        
        // URL Checker adds 15 points
        if (isUrlCheckerActive) totalPoints += 15;
        
        // Find Phone adds 10 points
        if (isFindPhoneActive) totalPoints += 10;
        
        // Hidden Files adds 10 points
        if (isHiddenFilesActive) totalPoints += 10;
        
        return baseLevel + totalPoints;
    }
    
    /**
     * Count how many security features are currently active
     */
    private int countActiveFeatures() {
        int count = 0;
        
        if (isVpnActive) count++;
        if (isAppLockActive) count++;
        if (isUrlCheckerActive) count++;
        if (isFindPhoneActive) count++;
        if (isHiddenFilesActive) count++;
        
        return count;
    }
    
    /**
     * Update the security level progress bar based on enabled security features
     */
    private void updateSecurityLevel() {
        // Calculate security level based on active features
        int securityLevel = calculateSecurityLevel();
        securityLevelProgress.setProgress(securityLevel);
        
        int activeFeatures = countActiveFeatures();
        int totalFeatures = 8; // Updated total features (5 original + 3 premium)
        
        activeFeaturesProgress.setProgress(activeFeatures * (100 / totalFeatures)); // % per feature
        if (activeFeaturesText != null) {
            activeFeaturesText.setText(String.format("%d/%d", activeFeatures, totalFeatures));
        }
    }
    
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.nav_settings) {
            Toast.makeText(this, "Settings coming soon!", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.nav_help) {
            Toast.makeText(this, "Help center coming soon!", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.nav_logout) {
            Toast.makeText(this, "Logout coming soon!", Toast.LENGTH_SHORT).show();
        }
        
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }
}