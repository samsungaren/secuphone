package com.example.secuphone;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceManager;
import androidx.gridlayout.widget.GridLayout;

import com.example.secuphone.admin.AppLockManager;
import com.example.secuphone.authentication.SignInActivity;
import com.example.secuphone.authentication.SignUpActivity;
import com.example.secuphone.authentication.UserSessionManager;
import com.example.secuphone.services.AppLockService;
import com.example.secuphone.utils.AppLockPreferences;
import com.example.secuphone.utils.PermissionManager;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private DrawerLayout drawerLayout;
    private PermissionManager permissionManager;
    private ProgressBar securityLevelProgress;
    private ProgressBar activeFeaturesProgress;
    private TextView activeFeaturesText;
    private TextView securityLevelPercentage;
    
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
    private boolean isUrlCheckerActive = false;
    private boolean isFindPhoneActive = false;
    private boolean isHiddenFilesActive = false;
    private boolean isAntiSpyActive = false;
    
    // Firebase Authentication
    private FirebaseAuth firebaseAuth;
    private UserSessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        
        // Make status bar transparent explicitly
        getWindow().setStatusBarColor(getResources().getColor(android.R.color.transparent));
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        
        // Initialize Firebase Auth and User Session Manager
        firebaseAuth = FirebaseAuth.getInstance();
        sessionManager = UserSessionManager.getInstance(this);
        
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
        
        // Ensure status bar stays transparent
        getWindow().setStatusBarColor(getResources().getColor(android.R.color.transparent));
        
        // Update feature statuses whenever we return to the main activity
        checkFeatureStatuses();
        updateFeatureStatusUI();
        updateSecurityLevel();
        
        // Update recommendations visibility
        updateRecommendationVisibility();
        
        // Update real-time status
        updateNetworkStatus();
        updateLastScanTime();
        updateThreatsDetected();
        
        // Update navigation drawer to reflect current authentication state
        setupNavigationDrawer();
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
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            
            // VPN status
            isVpnActive = prefs.getBoolean("vpn_active", false);
            
            // App Lock status
            try {
                AppLockPreferences appLockPreferences = new AppLockPreferences(this);
                // Check if PIN is set and any apps are locked
                isAppLockActive = appLockPreferences.isPinSet() && !appLockPreferences.getLockedApps().isEmpty();
            } catch (Exception e) {
                Log.e("MainActivity", "Error accessing AppLockPreferences: " + e.getMessage(), e);
                isAppLockActive = false;
            }
            
            // Anti-Spy status
            isAntiSpyActive = prefs.getBoolean("anti_spy_enabled", false);
            
            // URL Checker is always active by default
            isUrlCheckerActive = true;
            
            // Find Phone status - check if camera permission is granted
            isFindPhoneActive = permissionManager.hasCameraPermission() && permissionManager.hasStoragePermission();
            
            // Hidden Files status - check if storage permission is granted and feature is enabled
            isHiddenFilesActive = permissionManager.hasStoragePermission() && 
                                  prefs.getBoolean("hidden_files_active", false);
        } catch (Exception e) {
            Log.e("MainActivity", "Error checking feature statuses: " + e.getMessage(), e);
        }
    }
    
    private boolean hasLockedApps() {
        // Check if at least one app is locked
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        return !prefs.getStringSet("locked_apps", java.util.Collections.emptySet()).isEmpty();
    }
    
    private void updateFeatureStatusUI() {
        try {
            // Update VPN status
            updateStatusIndicator(vpnStatusIndicator, vpnStatusText, 
                    isVpnActive, R.string.status_enabled, R.string.status_disabled);
            
            // Update App Lock status
            AppLockPreferences appLockPreferences = new AppLockPreferences(this);
            if (appLockPreferences.isPinSet()) {
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
            
            // Update Anti-Spy status
            View antiSpyStatusIndicator = findViewById(R.id.anti_spy_status_indicator);
            TextView antiSpyStatusText = findViewById(R.id.anti_spy_status_text);
            if (antiSpyStatusIndicator != null && antiSpyStatusText != null) {
                updateStatusIndicator(antiSpyStatusIndicator, antiSpyStatusText, 
                        isAntiSpyActive, R.string.status_enabled, R.string.status_disabled);
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error updating feature status UI: " + e.getMessage());
        }
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
        
        // Check if user is logged in
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (sessionManager.isLoggedIn() && user != null && user.isEmailVerified()) {
            // User is properly logged in and verified - show user UI
            guestUI.setVisibility(View.GONE);
            userUI.setVisibility(View.VISIBLE);
            
            // Set user information
            TextView userNameText = headerView.findViewById(R.id.user_name);
            TextView userEmailText = headerView.findViewById(R.id.user_email);
            
            if (userNameText != null && userEmailText != null) {
                String displayName = user.getDisplayName();
                userNameText.setText(displayName != null && !displayName.isEmpty() ? 
                                    displayName : getString(R.string.user));
                userEmailText.setText(user.getEmail());
            }
            
            // Update current plan status
            TextView currentPlanText = headerView.findViewById(R.id.current_plan);
            if (currentPlanText != null) {
                // Here you could check for premium status
                // For now, just show free plan
                currentPlanText.setText(getString(R.string.free_plan));
            }
            
            // Set up upgrade text click listener
            TextView upgradeText = headerView.findViewById(R.id.upgrade_text);
            if (upgradeText != null) {
                upgradeText.setOnClickListener(v -> {
                    Toast.makeText(this, "Upgrade feature coming soon", Toast.LENGTH_SHORT).show();
                });
            }
        } else {
            // User is not logged in or not verified - show guest UI
            guestUI.setVisibility(View.VISIBLE);
            userUI.setVisibility(View.GONE);
            
            // Set up sign in button
            MaterialButton signInButton = headerView.findViewById(R.id.sign_in_button);
            if (signInButton != null) {
                signInButton.setOnClickListener(v -> {
                    startActivity(new Intent(this, SignInActivity.class));
                    drawerLayout.closeDrawer(GravityCompat.START);
                });
            }
            
            // Set up sign up button
            MaterialButton signUpButton = headerView.findViewById(R.id.sign_up_button);
            if (signUpButton != null) {
                signUpButton.setOnClickListener(v -> {
                    startActivity(new Intent(this, SignUpActivity.class));
                    drawerLayout.closeDrawer(GravityCompat.START);
                });
            }
        }
        
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
        securityLevelPercentage = findViewById(R.id.security_level_percentage);
        
        // Initialize recommendations
        setupRecommendations();
        
        // Initialize real-time status
        setupRealTimeStatus();
        
        // Set initial values (these will be updated in onResume)
        securityLevelProgress.setProgress(0);
        activeFeaturesProgress.setProgress(0);
    }
    
    /**
     * Setup recommendation items and their click actions
     */
    private void setupRecommendations() {
        try {
            // Setup app lock recommendation
            View recommendationItem1 = findViewById(R.id.recommendation_item_1);
            ImageButton actionButton1 = findViewById(R.id.recommendation_action_1);
            
            if (recommendationItem1 != null && actionButton1 != null) {
                // Whole item click
                recommendationItem1.setOnClickListener(v -> openAppLockActivity());
                
                // Action button click
                actionButton1.setOnClickListener(v -> openAppLockActivity());
            }
            
            // Setup VPN recommendation
            View recommendationItem2 = findViewById(R.id.recommendation_item_2);
            ImageButton actionButton2 = findViewById(R.id.recommendation_action_2);
            
            if (recommendationItem2 != null && actionButton2 != null) {
                // Whole item click
                recommendationItem2.setOnClickListener(v -> openVpnActivity());
                
                // Action button click
                actionButton2.setOnClickListener(v -> openVpnActivity());
            }
            
            // Setup view all recommendations
            TextView viewAllRecommendations = findViewById(R.id.view_all_recommendations);
            if (viewAllRecommendations != null) {
                viewAllRecommendations.setOnClickListener(v -> 
                    showAllRecommendationsDialog());
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error setting up recommendations: " + e.getMessage(), 
                Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Setup real-time status section and refresh action
     */
    private void setupRealTimeStatus() {
        try {
            // Setup refresh button
            TextView refreshStatus = findViewById(R.id.refresh_status);
            if (refreshStatus != null) {
                refreshStatus.setOnClickListener(v -> refreshSecurityStatus());
            }
            
            // Initialize status indicators
            updateNetworkStatus();
            updateLastScanTime();
            updateThreatsDetected();
        } catch (Exception e) {
            Toast.makeText(this, "Error setting up real-time status: " + e.getMessage(), 
                Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Show a dialog with all security recommendations
     */
    private void showAllRecommendationsDialog() {
        try {
            // Build list of recommendations based on feature status
            StringBuilder recommendations = new StringBuilder();
            
            if (!isAppLockActive) {
                recommendations.append("• Enable App Lock to protect your sensitive apps\n\n");
            }
            
            if (!isVpnActive) {
                recommendations.append("• Connect to VPN for secure browsing\n\n");
            }
            
            if (!isFindPhoneActive) {
                recommendations.append("• Setup Find Phone feature to track your device if lost\n\n");
            }
            
            if (!isHiddenFilesActive) {
                recommendations.append("• Configure Hidden Files to protect your sensitive data\n\n");
            }
            
            // Add general recommendations
            recommendations.append("• Keep your device software up to date\n\n");
            recommendations.append("• Use strong, unique passwords for your accounts\n\n");
            recommendations.append("• Enable biometric authentication when available\n\n");
            
            // Show dialog with recommendations
            new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.recommendations)
                .setMessage(recommendations.toString())
                .setPositiveButton(R.string.ok, null)
                .show();
        } catch (Exception e) {
            Toast.makeText(this, "Error showing recommendations: " + e.getMessage(), 
                Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Update the network security status indicator
     */
    private void updateNetworkStatus() {
        try {
            TextView networkStatusText = findViewById(R.id.network_status_text);
            if (networkStatusText != null) {
                if (isVpnActive) {
                    networkStatusText.setText(R.string.secure);
                    networkStatusText.setTextColor(
                        ContextCompat.getColor(this, R.color.success_green));
                } else {
                    networkStatusText.setText(R.string.insecure);
                    networkStatusText.setTextColor(
                        ContextCompat.getColor(this, R.color.warning_amber));
                }
            }
        } catch (Exception e) {
            // Silently fail
        }
    }
    
    /**
     * Update the last scan time indicator
     */
    private void updateLastScanTime() {
        try {
            TextView lastScanTime = findViewById(R.id.last_scan_time);
            if (lastScanTime != null) {
                // Get last scan time from preferences or use default
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                long lastScan = prefs.getLong("last_scan_time", 0);
                
                if (lastScan == 0) {
                    lastScanTime.setText(R.string.just_now);
                } else {
                    // Calculate time ago
                    long now = System.currentTimeMillis();
                    long diff = now - lastScan;
                    
                    if (diff < 60000) { // Less than 1 minute
                        lastScanTime.setText(R.string.just_now);
                    } else if (diff < 3600000) { // Less than 1 hour
                        lastScanTime.setText(diff / 60000 + " minutes ago");
                    } else if (diff < 86400000) { // Less than 1 day
                        lastScanTime.setText(diff / 3600000 + " hours ago");
                    } else {
                        lastScanTime.setText(diff / 86400000 + " days ago");
                    }
                }
            }
        } catch (Exception e) {
            // Silently fail
        }
    }
    
    /**
     * Update the threats detected indicator
     */
    private void updateThreatsDetected() {
        try {
            TextView threatsDetectedCount = findViewById(R.id.threats_detected_count);
            if (threatsDetectedCount != null) {
                // Get threats count from preferences
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                int threats = prefs.getInt("threats_detected", 0);
                
                threatsDetectedCount.setText(String.valueOf(threats));
                
                // Set color based on threat count
                if (threats == 0) {
                    threatsDetectedCount.setTextColor(
                        ContextCompat.getColor(this, R.color.success_green));
                } else if (threats < 3) {
                    threatsDetectedCount.setTextColor(
                        ContextCompat.getColor(this, R.color.warning_amber));
                } else {
                    threatsDetectedCount.setTextColor(
                        ContextCompat.getColor(this, R.color.error_red));
                }
            }
        } catch (Exception e) {
            // Silently fail
        }
    }
    
    /**
     * Perform a security scan and update the status
     */
    private void refreshSecurityStatus() {
        try {
            // Show a scan in progress dialog
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
            ProgressBar progressBar = new ProgressBar(this);
            progressBar.setPadding(0, 50, 0, 30);
            progressBar.setIndeterminate(true);
            
            builder.setTitle(R.string.scanning)
                   .setMessage(R.string.checking_security_status)
                   .setView(progressBar)
                   .setCancelable(false);
            
            AlertDialog dialog = builder.create();
            dialog.show();
            
            // Simulate scan with a delay
            new Handler().postDelayed(() -> {
                // Update last scan time
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                prefs.edit().putLong("last_scan_time", System.currentTimeMillis()).apply();
                
                // Occasionally simulate threat detection
                if (Math.random() < 0.1) {
                    int currentThreats = prefs.getInt("threats_detected", 0);
                    prefs.edit().putInt("threats_detected", currentThreats + 1).apply();
                }
                
                // Update UI
                updateLastScanTime();
                updateThreatsDetected();
                updateNetworkStatus();
                checkFeatureStatuses();
                updateFeatureStatusUI();
                updateSecurityLevel();
                
                // Dismiss dialog
                dialog.dismiss();
                
                // Show toast
                Toast.makeText(this, R.string.security_scan_complete, Toast.LENGTH_SHORT).show();
            }, 2000);
            
        } catch (Exception e) {
            Toast.makeText(this, "Error refreshing security status: " + e.getMessage(), 
                Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Calculate security level based on enabled features and real-time security factors
     */
    private int calculateSecurityLevel() {
        int baseLevel = 20; // Base security level with no features
        int totalPoints = 0;
        
        // VPN adds 25 points
        if (isVpnActive) totalPoints += 25;
        
        // App Lock adds 20 points
        if (isAppLockActive) totalPoints += 20;
        
        // Anti-Spy adds 15 points
        if (isAntiSpyActive) totalPoints += 15;
        
        // URL Checker adds 15 points
        if (isUrlCheckerActive) totalPoints += 15;
        
        // Find Phone adds 10 points
        if (isFindPhoneActive) totalPoints += 10;
        
        // Hidden Files adds 10 points
        if (isHiddenFilesActive) totalPoints += 10;
        
        // Add threat deduction
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int threats = prefs.getInt("threats_detected", 0);
        
        // Each threat reduces security score by 5 points, up to 20 points
        int threatDeduction = Math.min(threats * 5, 20);
        
        return Math.max(baseLevel + totalPoints - threatDeduction, 0);
    }
    
    /**
     * Update visibility of recommendation items based on feature status
     */
    private void updateRecommendationVisibility() {
        try {
            View recommendationItem1 = findViewById(R.id.recommendation_item_1);
            View recommendationItem2 = findViewById(R.id.recommendation_item_2);
            
            if (recommendationItem1 != null) {
                recommendationItem1.setVisibility(isAppLockActive ? View.GONE : View.VISIBLE);
            }
            
            if (recommendationItem2 != null) {
                recommendationItem2.setVisibility(isVpnActive ? View.GONE : View.VISIBLE);
            }
        } catch (Exception e) {
            // Silently fail
        }
    }
    
    private void setupMenuItemListeners() {
        // Setup VPN Button
        findViewById(R.id.vpn_button).setOnClickListener(v -> {
            if (sessionManager.isLoggedIn()) {
                startActivity(new Intent(this, VPNActivity.class));
            } else {
                showSignInRequiredDialog("VPN");
            }
        });
        
        // Setup App Lock Button
        findViewById(R.id.app_lock_button).setOnClickListener(v -> {
            if (sessionManager.isLoggedIn()) {
                startActivity(new Intent(this, AppLockActivity.class));
            } else {
                showSignInRequiredDialog("App Lock");
            }
        });
        
        // Setup URL Checker Button
        findViewById(R.id.url_checker_button).setOnClickListener(v -> {
            if (sessionManager.isLoggedIn()) {
                startActivity(new Intent(this, URLCheckerActivity.class));
            } else {
                showSignInRequiredDialog("URL Checker");
            }
        });
        
        // Setup Find Phone Button
        findViewById(R.id.find_phone_button).setOnClickListener(v -> {
            if (sessionManager.isLoggedIn()) {
                if (checkAppPermissions()) {
                    // Start FindPhoneActivity
                    startActivity(new Intent(this, FindPhoneActivity.class));
                }
            } else {
                showSignInRequiredDialog("Find Phone");
            }
        });
        
        // Setup Hidden Files Button
        findViewById(R.id.hidden_files_button).setOnClickListener(v -> {
            if (sessionManager.isLoggedIn()) {
                startActivity(new Intent(this, FileHiderActivity.class));
            } else {
                showSignInRequiredDialog("Hidden Files");
            }
        });
        
        // Setup Coming Soon Button
        findViewById(R.id.coming_soon_button).setOnClickListener(v -> {
            Toast.makeText(this, "More features coming soon!", Toast.LENGTH_SHORT).show();
        });
        
        // Setup Anti-Spy Button
        findViewById(R.id.anti_spy_button).setOnClickListener(v -> {
            if (sessionManager.isLoggedIn()) {
                openAntiSpyActivity();
            } else {
                showSignInRequiredDialog("Anti-Spy");
            }
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
     * or redirects to sign in if user is not logged in
     * @param buttonId The ID of the button to set up
     * @param featureName The name of the feature for the toast message
     */
    private void setupPremiumFeatureButton(int buttonId, String featureName) {
        findViewById(buttonId).setOnClickListener(v -> {
            if (sessionManager.isLoggedIn()) {
                // If it's the password manager feature, open the activity directly
                if (featureName.equals("Password Manager")) {
                    openPasswordManagerActivity();
                } else {
                    showPremiumFeatureDialog(featureName);
                }
            } else {
                showSignInRequiredDialog(featureName);
            }
        });
    }
    
    /**
     * Shows a dialog for features that require sign in first
     * @param featureName The name of the feature
     */
    private void showSignInRequiredDialog(String featureName) {
        new MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.sign_in_required))
            .setMessage(getString(R.string.sign_in_to_use_feature, featureName))
            .setPositiveButton(getString(R.string.sign_in), (dialog, which) -> {
                startActivity(new Intent(this, SignInActivity.class));
            })
            .setNegativeButton(getString(R.string.cancel), null)
            .show();
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
     * Count how many security features are currently active
     */
    private int countActiveFeatures() {
        int count = 0;
        
        if (isVpnActive) count++;
        if (isAppLockActive) count++;
        if (isAntiSpyActive) count++;
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
        securityLevelPercentage.setText(securityLevel + "%");
        
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
            logoutUser();
        }
        
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }
    
    /**
     * Open the App Lock activity
     */
    private void openAppLockActivity() {
        try {
            startActivity(new Intent(this, AppLockActivity.class));
        } catch (Exception e) {
            Toast.makeText(this, "Error opening App Lock: " + e.getMessage(), 
                Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Open the VPN activity
     */
    private void openVpnActivity() {
        try {
            startActivity(new Intent(this, VPNActivity.class));
        } catch (Exception e) {
            Toast.makeText(this, "Error opening VPN: " + e.getMessage(), 
                Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Open the Anti-Spy activity
     */
    private void openAntiSpyActivity() {
        try {
            startActivity(new Intent(this, AntiSpyActivity.class));
        } catch (Exception e) {
            Toast.makeText(this, "Error opening Anti-Spy: " + e.getMessage(), 
                Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Opens the PasswordManagerActivity
     */
    private void openPasswordManagerActivity() {
        try {
            startActivity(new Intent(this, PasswordManagerActivity.class));
        } catch (Exception e) {
            Toast.makeText(this, "Error opening Password Manager: " + e.getMessage(), 
                Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Redirects the user to the sign in screen
     */
    private void redirectToSignIn() {
        Intent intent = new Intent(this, SignInActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    /**
     * Logs the user out and redirects to sign in screen
     */
    private void logoutUser() {
        // Show confirmation dialog
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.logout)
            .setMessage(R.string.logout_confirmation)
            .setPositiveButton(R.string.yes, (dialog, which) -> {
                // Clear user session
                sessionManager.logoutUser();
                
                // Redirect to sign in
                redirectToSignIn();
            })
            .setNegativeButton(R.string.no, null)
            .show();
    }
}