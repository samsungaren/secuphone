package com.example.secuphone;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.secuphone.adapters.AppPermissionAdapter;
import com.example.secuphone.utils.AppPermissionScanner;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AntiSpyActivity extends AppCompatActivity implements AppPermissionAdapter.AppPermissionListener {

    private static final String PREF_LAST_SCAN_TIME = "last_spy_scan_time";

    // UI Components
    private TextView statusText;
    private Button scanButton;
    private ProgressBar scanProgress;
    private TextView lastScanText;
    private ImageView statusIcon;
    private TextView explanationText;
    private RecyclerView appListRecyclerView;
    private View appListCard;
    private TextView appCountText;

    // App Scanner Components
    private List<ApplicationInfo> appsWithPermissions = new ArrayList<>();
    private Map<String, Boolean> hasCameraPermission = new HashMap<>();
    private Map<String, Boolean> hasMicrophonePermission = new HashMap<>();
    private Map<String, Boolean> hasLocationPermission = new HashMap<>();
    private AppPermissionAdapter appAdapter;

    // State
    private boolean isScanning = false;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_anti_spy);
        
        // Set up toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(R.string.anti_spy);
        
        // Initialize UI components
        initializeViews();
        
        // Update last scan time
        updateLastScanText();
        
        // Set initial UI state
        updateUI();
        
        // Set up button listeners
        setupListeners();
        
        // Set up RecyclerView
        setupRecyclerView();
    }
    
    private void initializeViews() {
        statusText = findViewById(R.id.anti_spy_status);
        scanButton = findViewById(R.id.scan_button);
        scanProgress = findViewById(R.id.scan_progress);
        lastScanText = findViewById(R.id.last_scan_text);
        statusIcon = findViewById(R.id.status_icon);
        explanationText = findViewById(R.id.explanation_text);
        appListRecyclerView = findViewById(R.id.app_list_recycler_view);
        appListCard = findViewById(R.id.app_list_card);
        appCountText = findViewById(R.id.app_count_text);
        
        // Update explanation text
        explanationText.setText(R.string.anti_spy_explanation);
    }
    
    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        appListRecyclerView.setLayoutManager(layoutManager);
        appListRecyclerView.setHasFixedSize(true);
        appAdapter = new AppPermissionAdapter(this, appsWithPermissions, 
                hasCameraPermission, hasMicrophonePermission, hasLocationPermission, this);
        appListRecyclerView.setAdapter(appAdapter);
    }
    
    private void updateLastScanText() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        long lastScanTime = prefs.getLong(PREF_LAST_SCAN_TIME, 0);
        
        if (lastScanTime == 0) {
            lastScanText.setText(getString(R.string.last_spy_scan, getString(R.string.never)));
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
            String formattedDate = sdf.format(new Date(lastScanTime));
            lastScanText.setText(getString(R.string.last_spy_scan, formattedDate));
        }
    }
    
    private void setupListeners() {
        // Scan button
        scanButton.setOnClickListener(v -> {
            if (isScanning) {
                stopScan();
            } else {
                startScan();
            }
        });
    }
    
    private void updateUI() {
        // Update Anti-Spy status based on whether there are apps with sensitive permissions
        if (!appsWithPermissions.isEmpty()) {
            statusText.setText(R.string.anti_spy_enabled);
            statusText.setTextColor(ContextCompat.getColor(this, R.color.success_green));
            statusIcon.setImageResource(R.drawable.ic_shield_check);
            statusIcon.setColorFilter(ContextCompat.getColor(this, R.color.success_green));
        } else {
            statusText.setText(R.string.anti_spy_disabled);
            statusText.setTextColor(ContextCompat.getColor(this, R.color.error_red));
            statusIcon.setImageResource(R.drawable.ic_shield_off);
            statusIcon.setColorFilter(ContextCompat.getColor(this, R.color.error_red));
        }
        
        // Set scan button state
        if (isScanning) {
            scanButton.setText(R.string.stop_scan);
            scanProgress.setVisibility(View.VISIBLE);
        } else {
            scanButton.setText(R.string.scan_app_permissions);
            scanProgress.setVisibility(View.GONE);
        }
    }
    
    private void startScan() {
        try {
            isScanning = true;
            updateUI();
            
            // Show scanning feedback
            scanProgress.setProgress(0);
            ValueAnimator animator = ValueAnimator.ofInt(0, 100);
            animator.setDuration(2000);
            animator.addUpdateListener(animation -> {
                int value = (int) animation.getAnimatedValue();
                scanProgress.setProgress(value);
            });
            animator.start();
            
            // Temporarily hide results while scanning
            appListCard.setVisibility(View.GONE);
            
            // Show status
            Toast.makeText(this, R.string.scanning_apps, Toast.LENGTH_SHORT).show();
            
            // Start the actual scan in a background thread
            new Thread(() -> {
                final AppPermissionScanner.AppScanResult result;
                try {
                    result = AppPermissionScanner.scanInstalledApps(AntiSpyActivity.this);
                    
                    // Update UI on main thread
                    mainHandler.post(() -> completeScan(result));
                } catch (Exception e) {
                    mainHandler.post(() -> {
                        Toast.makeText(AntiSpyActivity.this, 
                            "Ошибка при сканировании: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        isScanning = false;
                        updateUI();
                    });
                }
            }).start();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка при запуске сканирования: " + e.getMessage(), 
                Toast.LENGTH_LONG).show();
            isScanning = false;
            updateUI();
        }
    }
    
    private void stopScan() {
        isScanning = false;
        updateUI();
    }
    
    private void completeScan(AppPermissionScanner.AppScanResult result) {
        try {
            // Save scan time
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            long currentTime = System.currentTimeMillis();
            prefs.edit().putLong(PREF_LAST_SCAN_TIME, currentTime).apply();
            
            // Update UI
            isScanning = false;
            updateUI();
            updateLastScanText();
            
            // Show scan complete toast
            Toast.makeText(this, R.string.app_permission_scan_complete, Toast.LENGTH_SHORT).show();
            
            // Update app list
            appsWithPermissions = result.appList;
            hasCameraPermission = result.hasCameraPermission;
            hasMicrophonePermission = result.hasMicrophonePermission;
            hasLocationPermission = result.hasLocationPermission;
            
            // Show app count
            if (result.totalAppsWithSensitivePermissions > 0) {
                appCountText.setText(getString(R.string.apps_with_permissions, 
                        result.totalAppsWithSensitivePermissions));
                appListCard.setVisibility(View.VISIBLE);
                
                // Update adapter with new data
                appAdapter = new AppPermissionAdapter(this, appsWithPermissions, 
                        hasCameraPermission, hasMicrophonePermission, hasLocationPermission, this);
                appListRecyclerView.setAdapter(appAdapter);
                
                // Force RecyclerView to update its layout
                appListRecyclerView.getLayoutManager().requestLayout();
                appListRecyclerView.invalidate();
            } else {
                appCountText.setText(R.string.no_apps_with_permissions);
                appListCard.setVisibility(View.VISIBLE);
            }
            
            // Update status based on scan results
            updateUI();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка при обработке результатов: " + e.getMessage(), 
                Toast.LENGTH_LONG).show();
        }
    }
    
    @Override
    public void onPermissionSettingsChanged(String packageName, int position) {
        try {
            // Wait for user to return from settings
            mainHandler.postDelayed(() -> {
                try {
                    // Check if the app still has sensitive permissions
                    boolean hasPermissions = AppPermissionScanner.refreshAppPermissions(
                        this, packageName, hasCameraPermission, hasMicrophonePermission, hasLocationPermission);
                    
                    if (!hasPermissions) {
                        // If no sensitive permissions remain, remove the app from the list
                        if (position < appsWithPermissions.size()) {
                            appsWithPermissions.remove(position);
                            appAdapter.notifyItemRemoved(position);
                            
                            // Update count
                            int remainingCount = appsWithPermissions.size();
                            if (remainingCount > 0) {
                                appCountText.setText(getString(R.string.apps_with_permissions, remainingCount));
                            } else {
                                appCountText.setText(R.string.no_apps_with_permissions);
                            }
                            
                            // Update UI
                            updateUI();
                        }
                    } else {
                        // Just refresh the item to update permission icons
                        appAdapter.notifyItemChanged(position);
                    }
                } catch (Exception e) {
                    Toast.makeText(this, "Ошибка при обновлении разрешений: " + e.getMessage(), 
                        Toast.LENGTH_SHORT).show();
                }
            }, 500); // Short delay to ensure settings have taken effect
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // Refresh UI when returning to this activity
        updateLastScanText();
        updateUI();
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
} 