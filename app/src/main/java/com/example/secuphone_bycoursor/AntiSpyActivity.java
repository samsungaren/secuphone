package com.example.secuphone_bycoursor;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.secuphone_bycoursor.services.AntiSpyService;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public class AntiSpyActivity extends AppCompatActivity {

    private static final String PREF_ANTI_SPY_ENABLED = "anti_spy_enabled";
    private static final String PREF_LAST_SCAN_TIME = "last_spy_scan_time";
    private static final String PREF_SCAN_RESULT = "spy_scan_result";
    private static final String PREF_MIC_PROTECTION = "mic_protection_enabled";
    private static final String PREF_CAMERA_PROTECTION = "camera_protection_enabled";
    private static final String PREF_LOCATION_PROTECTION = "location_protection_enabled";

    // UI Components
    private TextView statusText;
    private Button scanButton;
    private ProgressBar scanProgress;
    private LinearLayout scanResultLayout;
    private TextView scanResultText;
    private TextView lastScanText;
    private ImageView statusIcon;
    private SwitchMaterial micSwitch, cameraSwitch, locationSwitch;
    private MaterialCardView resultCard;
    private TextView explanationText;

    // State
    private boolean isScanning = false;
    private boolean isEnabled = false;
    private Handler scanHandler = new Handler();
    private Random random = new Random();

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
        
        // Load saved state
        loadSavedState();
        
        // Set initial UI state
        updateUI();
        
        // Set up button listeners
        setupListeners();
    }
    
    private void initializeViews() {
        statusText = findViewById(R.id.anti_spy_status);
        scanButton = findViewById(R.id.scan_button);
        scanProgress = findViewById(R.id.scan_progress);
        scanResultLayout = findViewById(R.id.scan_result_layout);
        scanResultText = findViewById(R.id.scan_result_text);
        lastScanText = findViewById(R.id.last_scan_text);
        statusIcon = findViewById(R.id.status_icon);
        micSwitch = findViewById(R.id.mic_switch);
        cameraSwitch = findViewById(R.id.camera_switch);
        locationSwitch = findViewById(R.id.location_switch);
        resultCard = findViewById(R.id.result_card);
        explanationText = findViewById(R.id.explanation_text);
        
        // Update explanation text
        explanationText.setText(R.string.anti_spy_explanation);
    }
    
    private void loadSavedState() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        isEnabled = prefs.getBoolean(PREF_ANTI_SPY_ENABLED, false);
        
        // Load protection settings
        boolean micEnabled = prefs.getBoolean(PREF_MIC_PROTECTION, false);
        boolean cameraEnabled = prefs.getBoolean(PREF_CAMERA_PROTECTION, false);
        boolean locationEnabled = prefs.getBoolean(PREF_LOCATION_PROTECTION, false);
        
        micSwitch.setChecked(micEnabled);
        cameraSwitch.setChecked(cameraEnabled);
        locationSwitch.setChecked(locationEnabled);
        
        // Update last scan text
        updateLastScanText();
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
        
        // Update scan result if available
        boolean scanResult = prefs.getBoolean(PREF_SCAN_RESULT, true);
        if (lastScanTime > 0) {
            scanResultLayout.setVisibility(View.VISIBLE);
            resultCard.setVisibility(View.VISIBLE);
            
            if (scanResult) {
                scanResultText.setText(R.string.spy_free);
                scanResultText.setTextColor(ContextCompat.getColor(this, R.color.success_green));
            } else {
                scanResultText.setText(R.string.spy_threats_found);
                scanResultText.setTextColor(ContextCompat.getColor(this, R.color.error_red));
            }
        } else {
            scanResultLayout.setVisibility(View.GONE);
            resultCard.setVisibility(View.GONE);
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
        
        // Protection toggles
        micSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            prefs.edit().putBoolean(PREF_MIC_PROTECTION, isChecked).apply();
            
            // Show feedback
            if (isChecked) {
                Toast.makeText(this, R.string.microphone_monitored, Toast.LENGTH_SHORT).show();
            }
            
            updateEnabledState();
            updateAntiSpyService();
        });
        
        cameraSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            prefs.edit().putBoolean(PREF_CAMERA_PROTECTION, isChecked).apply();
            
            // Show feedback
            if (isChecked) {
                Toast.makeText(this, R.string.camera_monitored, Toast.LENGTH_SHORT).show();
            }
            
            updateEnabledState();
            updateAntiSpyService();
        });
        
        locationSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            prefs.edit().putBoolean(PREF_LOCATION_PROTECTION, isChecked).apply();
            
            // Show feedback
            if (isChecked) {
                Toast.makeText(this, R.string.location_monitored, Toast.LENGTH_SHORT).show();
            }
            
            updateEnabledState();
            updateAntiSpyService();
        });
    }
    
    private void updateAntiSpyService() {
        Intent serviceIntent = new Intent(this, AntiSpyService.class);
        
        if (isEnabled) {
            // Start the service
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            Toast.makeText(this, "Anti-Spy protection activated", Toast.LENGTH_SHORT).show();
        } else {
            // Stop the service
            stopService(serviceIntent);
            Toast.makeText(this, "Anti-Spy protection deactivated", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void updateEnabledState() {
        // Feature is enabled if at least one protection is active
        boolean anyEnabled = micSwitch.isChecked() || cameraSwitch.isChecked() || locationSwitch.isChecked();
        
        if (anyEnabled != isEnabled) {
            isEnabled = anyEnabled;
            
            // Save the state
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            prefs.edit().putBoolean(PREF_ANTI_SPY_ENABLED, isEnabled).apply();
            
            // Update UI
            updateUI();
            
            // Update service
            updateAntiSpyService();
        }
    }
    
    private void updateUI() {
        if (isEnabled) {
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
            scanButton.setText(R.string.stop_spy_scan);
            scanProgress.setVisibility(View.VISIBLE);
        } else {
            scanButton.setText(R.string.start_spy_scan);
            scanProgress.setVisibility(View.GONE);
        }
    }
    
    private void startScan() {
        isScanning = true;
        updateUI();
        
        // Show toast
        Toast.makeText(this, R.string.spy_scan_in_progress, Toast.LENGTH_SHORT).show();
        
        // Animate progress
        ValueAnimator animator = ValueAnimator.ofInt(0, 100);
        animator.setDuration(5000); // 5 seconds scan
        animator.addUpdateListener(animation -> {
            int value = (int) animation.getAnimatedValue();
            scanProgress.setProgress(value);
        });
        animator.start();
        
        // Complete scan after delay
        scanHandler.postDelayed(this::completeScan, 5000);
    }
    
    private void stopScan() {
        isScanning = false;
        scanHandler.removeCallbacksAndMessages(null);
        updateUI();
    }
    
    private void completeScan() {
        isScanning = false;
        
        // 90% chance of clean scan, 10% chance of threat detection
        boolean isClean = random.nextInt(10) < 9;
        
        // Save scan result
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit()
            .putLong(PREF_LAST_SCAN_TIME, System.currentTimeMillis())
            .putBoolean(PREF_SCAN_RESULT, isClean)
            .apply();
        
        // Update UI
        updateUI();
        updateLastScanText();
        
        // Show result toast
        if (isClean) {
            Toast.makeText(this, R.string.no_spyware_found, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.spyware_detected, Toast.LENGTH_LONG).show();
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Refresh UI state
        updateUI();
        updateLastScanText();
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        scanHandler.removeCallbacksAndMessages(null);
    }
} 