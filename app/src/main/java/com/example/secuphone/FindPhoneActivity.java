package com.example.secuphone;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/**
 * Find Phone Activity - Allows users to locate and secure their device
 * This is a redesigned version with improved UI/UX
 */
public class FindPhoneActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "FindPhonePrefs";
    private static final String PROTECTION_ACTIVE_KEY = "protection_active";
    private static final String LAST_UPDATED_KEY = "last_updated";
    private static final String CURRENT_ADDRESS_KEY = "current_address";

    // Sample addresses for demo
    private static final String[] SAMPLE_ADDRESSES = {
            "1234 Main Street, Anytown",
            "567 Park Avenue, Metropolis",
            "789 Ocean Boulevard, Bay City",
            "321 Mountain View, Highland"
    };

    // UI Elements
    private TextView deviceStatus;
    private TextView locationStatus;
    private TextView audioStatus;
    private TextView cameraStatus;
    private MaterialButton activationToggle;
    
    // Map preview elements
    private ImageView mapPreview;
    private TextView locationAccuracy;
    private TextView deviceLocation;
    private TextView locationAddress;
    private TextView lastUpdated;
    private MaterialButton refreshLocation;
    
    // Status tracking
    private boolean isProtectionActive = false;
    private long lastUpdateTimestamp = 0;
    private String currentAddress = "";
    private final Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_find_phone);
        
        // Initialize UI elements
        initializeViews();
        
        // Load saved settings
        loadSettings();
        
        // Update UI based on current settings
        updateUI();
        
        // Set up click listeners
        setupClickListeners();
    }
    
    /**
     * Initialize all view references
     */
    private void initializeViews() {
        // Status indicators
        deviceStatus = findViewById(R.id.device_status);
        locationStatus = findViewById(R.id.location_status);
        audioStatus = findViewById(R.id.audio_status);
        cameraStatus = findViewById(R.id.camera_status);
        
        // Map preview elements
        mapPreview = findViewById(R.id.map_preview);
        locationAccuracy = findViewById(R.id.location_accuracy);
        deviceLocation = findViewById(R.id.device_location);
        locationAddress = findViewById(R.id.location_address);
        lastUpdated = findViewById(R.id.last_updated);
        refreshLocation = findViewById(R.id.refresh_location);
        
        // Activation button
        activationToggle = findViewById(R.id.activation_toggle);
        
        // Back button
        ImageButton backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> finish());
    }
    
    /**
     * Load saved settings from SharedPreferences
     */
    private void loadSettings() {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        isProtectionActive = settings.getBoolean(PROTECTION_ACTIVE_KEY, false);
        lastUpdateTimestamp = settings.getLong(LAST_UPDATED_KEY, 0);
        currentAddress = settings.getString(CURRENT_ADDRESS_KEY, SAMPLE_ADDRESSES[0]);
        
        // If it's the first time or no address saved, generate a random one
        if (currentAddress.isEmpty()) {
            currentAddress = getRandomAddress();
        }
    }
    
    /**
     * Save current settings to SharedPreferences
     */
    private void saveSettings() {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(PROTECTION_ACTIVE_KEY, isProtectionActive);
        editor.putLong(LAST_UPDATED_KEY, lastUpdateTimestamp);
        editor.putString(CURRENT_ADDRESS_KEY, currentAddress);
        editor.apply();
    }
    
    /**
     * Update the UI based on current settings
     */
    private void updateUI() {
        if (isProtectionActive) {
            // Update status indicators
            deviceStatus.setText(R.string.protected_status);
            deviceStatus.setBackgroundResource(R.drawable.status_premium_indicator);
            
            locationStatus.setText(R.string.status_enabled);
            locationStatus.setTextColor(getColor(R.color.success_green));
            
            audioStatus.setText(R.string.status_enabled);
            audioStatus.setTextColor(getColor(R.color.success_green));
            
            cameraStatus.setText(R.string.status_enabled);
            cameraStatus.setTextColor(getColor(R.color.success_green));
            
            // Enable map elements
            mapPreview.setAlpha(1.0f);
            locationAccuracy.setVisibility(View.VISIBLE);
            locationAddress.setText(currentAddress);
            
            // Update last updated text
            updateLastUpdatedText();
            
            // Update activation button
            activationToggle.setText(R.string.deactivate_protection);
        } else {
            // Update status indicators
            deviceStatus.setText(R.string.status_disabled);
            deviceStatus.setBackgroundResource(R.drawable.status_disabled_indicator);
            
            locationStatus.setText(R.string.status_disabled);
            locationStatus.setTextColor(getColor(R.color.text_disabled));
            
            audioStatus.setText(R.string.status_disabled);
            audioStatus.setTextColor(getColor(R.color.text_disabled));
            
            cameraStatus.setText(R.string.status_disabled);
            cameraStatus.setTextColor(getColor(R.color.text_disabled));
            
            // Disable map elements
            mapPreview.setAlpha(0.5f);
            locationAccuracy.setVisibility(View.INVISIBLE);
            
            // Show default last updated text
            lastUpdated.setText(R.string.last_updated);
            
            // Update activation button
            activationToggle.setText(R.string.activate_protection);
        }
    }
    
    /**
     * Update the last updated text based on the last update timestamp
     */
    private void updateLastUpdatedText() {
        if (lastUpdateTimestamp > 0) {
            long now = System.currentTimeMillis();
            long diff = now - lastUpdateTimestamp;
            
            String timeText;
            if (diff < 60000) { // Less than a minute
                timeText = "Last updated: Just now";
            } else if (diff < 3600000) { // Less than an hour
                int minutes = (int) (diff / 60000);
                timeText = "Last updated: " + minutes + " minute" + (minutes > 1 ? "s" : "") + " ago";
            } else if (diff < 86400000) { // Less than a day
                int hours = (int) (diff / 3600000);
                timeText = "Last updated: " + hours + " hour" + (hours > 1 ? "s" : "") + " ago";
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
                timeText = "Last updated: " + sdf.format(new Date(lastUpdateTimestamp));
            }
            
            lastUpdated.setText(timeText);
        } else {
            lastUpdated.setText(R.string.last_updated);
        }
    }
    
    /**
     * Set up click listeners for all interactive elements
     */
    private void setupClickListeners() {
        // Activation toggle button
        activationToggle.setOnClickListener(v -> {
            isProtectionActive = !isProtectionActive;
            
            if (isProtectionActive) {
                // Update timestamp and location on activation
                refreshLocation();
            }
            
            saveSettings();
            updateUI();
            
            // Show appropriate toast message
            if (isProtectionActive) {
                Toast.makeText(this, "Find Phone protection activated", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Find Phone protection deactivated", Toast.LENGTH_SHORT).show();
            }
        });
        
        // Refresh location button
        refreshLocation.setOnClickListener(v -> {
            if (isProtectionActive) {
                refreshLocation();
                Toast.makeText(this, "Location updated", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Please activate protection first", Toast.LENGTH_SHORT).show();
            }
        });
        
        // Feature cards click listeners
        setupFeatureCardListener(R.id.location_card, "Real-time location tracking");
        setupFeatureCardListener(R.id.audio_card, "Audio activation");
        setupFeatureCardListener(R.id.password_lock_card, "Remote password lock");
        setupFeatureCardListener(R.id.delete_message_card, "Message on screen");
        setupFeatureCardListener(R.id.critical_file_card, "Critical data deletion");
        setupFeatureCardListener(R.id.take_photos_card, "Remote camera access");
    }
    
    /**
     * Refresh the location data with animation
     */
    private void refreshLocation() {
        // Show "Updating..." text
        lastUpdated.setText("Updating location...");
        
        // Add a loading animation to the map
        mapPreview.setAlpha(0.7f);
        
        // Simulate a delay for network request
        handler.postDelayed(() -> {
            // Update timestamp
            lastUpdateTimestamp = System.currentTimeMillis();
            
            // Generate a new random address
            currentAddress = getRandomAddress();
            locationAddress.setText(currentAddress);
            
            // Reset map opacity
            mapPreview.setAlpha(1.0f);
            
            // Update UI
            updateLastUpdatedText();
            
            // Save settings
            saveSettings();
        }, 1500);
    }
    
    /**
     * Get a random address from the sample list
     */
    private String getRandomAddress() {
        Random random = new Random();
        return SAMPLE_ADDRESSES[random.nextInt(SAMPLE_ADDRESSES.length)];
    }
    
    /**
     * Set up click listener for a feature card
     * @param cardId The resource ID of the CardView
     * @param featureName The name of the feature for the toast message
     */
    private void setupFeatureCardListener(int cardId, String featureName) {
        CardView card = findViewById(cardId);
        card.setOnClickListener(v -> {
            if (isProtectionActive) {
                Toast.makeText(this, featureName + " feature ready", Toast.LENGTH_SHORT).show();
                
                // Special handling for location card
                if (cardId == R.id.location_card) {
                    refreshLocation();
                }
            } else {
                Toast.makeText(this, "Please activate protection first", Toast.LENGTH_SHORT).show();
            }
        });
    }
} 