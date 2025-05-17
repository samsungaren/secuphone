package com.example.secuphone;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageButton;
import android.app.AlertDialog;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.RadioButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.FragmentTransaction;
import androidx.annotation.Nullable;

import com.example.secuphone.dialogs.LoudSignalDialog;
import com.example.secuphone.models.LocationData;
import com.example.secuphone.services.LocationTrackingService;
import com.example.secuphone.services.LoudSignalService;
import com.example.secuphone.utils.DeviceUtils;
import com.example.secuphone.utils.FirebaseAuthManager;
import com.example.secuphone.utils.FirebaseLocationManager;
import com.example.secuphone.utils.LocationPermissionManager;
import com.example.secuphone.utils.RemoteSignalManager;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.example.secuphone.admin.RemoteLockDeviceAdmin;
import com.example.secuphone.services.RemoteLockService;
import com.example.secuphone.utils.RemoteLockManager;
import com.example.secuphone.utils.DeviceRegistrationManager;
import com.google.firebase.database.DataSnapshot;
import android.widget.ArrayAdapter;
import android.view.ViewGroup;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Find Phone Activity - Allows users to locate and secure their device
 * This is a redesigned version with improved UI/UX
 */
public class FindPhoneActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "FindPhoneActivity";

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

    // Cards for features
    private CardView trackingCard;
    private CardView remoteBlockingCard;
    private CardView dataWipeCard;
    private CardView audioSignalCard;

    // Indicators for active features
    private View trackingActiveIndicator;
    private View blockingActiveIndicator;
    private View wipeActiveIndicator;
    private View audioActiveIndicator;

    // Activation elements
    private CardView activationCard;
    private Button activateButton;
    private TextView activationStatus;

    // Map Fragment
    private MapFragment mapFragment;
    
    // Managers
    private LocationPermissionManager permissionManager;
    private FirebaseAuthManager authManager;
    private FirebaseLocationManager locationManager;
    private RemoteSignalManager remoteSignalManager;
    private RemoteLockManager remoteLockManager;
    private DeviceRegistrationManager deviceRegistrationManager;
    
    // Device ID
    private String deviceId;
    private boolean isTrackingActive = false;

    // Map in location card
    private GoogleMap locationCardMap;
    private TextView deviceLocation;
    private TextView locationAddress;
    private TextView lastUpdated;
    private Button refreshLocation;

    // Expanded map UI elements
    private TextView expandedDeviceLocation;
    private TextView expandedLocationAddress;
    private TextView expandedLastUpdated;
    private TextView expandedMapAccuracy;
    private Button expandedRefreshLocation;
    
    // Map controls
    private ImageButton zoomInButton;
    private ImageButton zoomOutButton;
    private ImageButton recenterButton;
    
    // Expanded map
    private GoogleMap expandedMap;
    private boolean isMapExpanded = false;
    private LatLng lastKnownLocation;
    private float currentZoomLevel = 15f;

    // Handler for periodic updates
    private final Handler updateHandler = new Handler(Looper.getMainLooper());
    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            if (isTrackingActive) {
                updateLocationCardMap();
                // Schedule next update in 30 seconds
                updateHandler.postDelayed(this, 30000);
            }
        }
    };

    // For paired device (can be null if no paired device)
    private String pairedDeviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_find_phone);
        
        // Initialize managers
        permissionManager = new LocationPermissionManager(this);
        authManager = FirebaseAuthManager.getInstance();
        locationManager = FirebaseLocationManager.getInstance();
        remoteSignalManager = RemoteSignalManager.getInstance();
        remoteLockManager = RemoteLockManager.getInstance(this);
        deviceRegistrationManager = DeviceRegistrationManager.getInstance(this);
        
        // Get device ID
        deviceId = android.provider.Settings.Secure.getString(
                getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
        
        // Get paired device ID
        pairedDeviceId = getPairedDeviceId();
        
        // Register this device with Firebase
        deviceRegistrationManager.registerDevice(task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "Device registered successfully with Firebase");
            } else {
                Log.e(TAG, "Failed to register device with Firebase", task.getException());
            }
        });
        
        // Initialize UI components
        initializeLocationCard();
        initializeFeatureCards();
        initializeActivationCard();
        initializeMapFragment();
        initializeExpandedMapUI();
        
        // Setup remote signal listener
        remoteSignalManager.listenForSignalCommands(deviceId, new RemoteSignalManager.SignalCommandListener() {
            @Override
            public void onSignalCommand(int durationSeconds, int volumeLevel) {
                startSignalFromRemote(durationSeconds, volumeLevel);
            }
            
            @Override
            public void onSignalStop() {
                stopSignalFromRemote();
            }
        });
        
        // Check if device admin is active and start the Remote Lock Service
        checkAndStartRemoteLockService();
        
        // Check if tracking is active
        checkTrackingStatus();
        
        // For testing/demo purposes only
        // setupDevicePairing();
        
        // Log device and paired device information for debugging
        Log.d(TAG, "Device ID: " + deviceId);
        Log.d(TAG, "Paired Device ID: " + pairedDeviceId);
    }

    private void initializeLocationCard() {
        // Get location card elements
        deviceLocation = findViewById(R.id.device_location);
        locationAddress = findViewById(R.id.location_address);
        lastUpdated = findViewById(R.id.last_updated);
        refreshLocation = findViewById(R.id.refresh_location);
        
        // Set up the map in the location card
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapInLocationCard);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
        
        // Set up map click listener to expand map
        View mapClickOverlay = findViewById(R.id.mapClickOverlay);
        if (mapClickOverlay != null) {
            mapClickOverlay.setOnClickListener(v -> expandMapView());
        }
        
        // Find the map container and set initial visibility to GONE
        View mapContainer = findViewById(R.id.mapContainer);
        mapContainer.setVisibility(View.GONE);
        
        // Set up refresh button
        refreshLocation.setOnClickListener(v -> updateLocationCardMap());
        
        // Set initial device location text
        deviceLocation.setText(getString(R.string.current_location));
        
        // Set address initially
        locationAddress.setText(DeviceUtils.getDeviceName(this));
        
        // Set initial timestamp
        lastUpdated.setText(getString(R.string.last_updated));
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        locationCardMap = googleMap;
        
        // Configure map settings
        locationCardMap.getUiSettings().setZoomControlsEnabled(false);
        locationCardMap.getUiSettings().setMapToolbarEnabled(false);
        locationCardMap.getUiSettings().setScrollGesturesEnabled(false);
        locationCardMap.getUiSettings().setRotateGesturesEnabled(false);
        locationCardMap.getUiSettings().setZoomGesturesEnabled(false);
        
        try {
            if (permissionManager.hasLocationPermissions()) {
                locationCardMap.setMyLocationEnabled(true);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Error enabling map location", e);
        }
        
        // Update the map with current location
        updateLocationCardMap();
    }
    
    private void updateLocationCardMap() {
        if (locationCardMap == null) {
                return;
            }
            
        // Update "updating..." text
        lastUpdated.setText("Updating location...");
        
        try {
            if (permissionManager.hasLocationPermissions()) {
                FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
                fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                    if (location != null) {
                        // Update map with current location
                        LatLng currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                        locationCardMap.clear();
                        locationCardMap.addMarker(new MarkerOptions().position(currentLocation).title("Current Location"));
                        locationCardMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15f));
                        
                        // Update address
                        String deviceName = DeviceUtils.getDeviceName(this);
                        locationAddress.setText(deviceName);
                        
                        // Update timestamp
                        lastUpdated.setText("Last updated: Just now");
                        
                        // If tracking is active, update the Firebase location
                        if (isTrackingActive) {
                            LocationData locationData = new LocationData(
                                    location.getLatitude(),
                                    location.getLongitude(),
                                    System.currentTimeMillis(),
                                    deviceId,
                                    location.getAccuracy(),
                                    location.getAltitude(),
                                    location.getSpeed(),
                                    DeviceUtils.getBatteryLevel(this)
                            );
                            
                            locationManager.updateLocationData(deviceId, locationData, null);
                        }
                    } else {
                        lastUpdated.setText("Could not get location");
                    }
                }).addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting location", e);
                    lastUpdated.setText("Error getting location");
                });
            } else {
                lastUpdated.setText("Location permission required");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception getting location", e);
            lastUpdated.setText("Location permission required");
        }
    }

    private void initializeFeatureCards() {
        // Feature cards
        trackingCard = findViewById(R.id.trackingCard);
        remoteBlockingCard = findViewById(R.id.remoteBlockingCard);
        dataWipeCard = findViewById(R.id.dataWipeCard);
        audioSignalCard = findViewById(R.id.audioSignalCard);

        // Feature active indicators
        trackingActiveIndicator = findViewById(R.id.trackingActiveIndicator);
        blockingActiveIndicator = findViewById(R.id.blockingActiveIndicator);
        wipeActiveIndicator = findViewById(R.id.wipeActiveIndicator);
        audioActiveIndicator = findViewById(R.id.audioActiveIndicator);

        // Set click listeners for feature cards
        trackingCard.setOnClickListener(v -> handleTrackingCardClick());
        remoteBlockingCard.setOnClickListener(v -> handleRemoteBlockingCardClick());
        dataWipeCard.setOnClickListener(v -> handleDataWipeCardClick());
        audioSignalCard.setOnClickListener(v -> handleAudioSignalCardClick());
    }

    private void initializeActivationCard() {
        activationCard = findViewById(R.id.activationCard);
        activateButton = findViewById(R.id.activateButton);
        activationStatus = findViewById(R.id.activationStatus);

        activateButton.setOnClickListener(v -> toggleProtection());
    }

    private void initializeMapFragment() {
        // Make sure map container is GONE initially
        View mapContainer = findViewById(R.id.mapContainer);
        mapContainer.setVisibility(View.GONE);
        
        // Initialize map fragment
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(map -> {
                expandedMap = map;
                configureExpandedMap();
            });
        }
    }

    private void checkTrackingStatus() {
        // Check if location service is running
        isTrackingActive = LocationTrackingService.isServiceRunning();
        
        // Update UI based on tracking status
        updateProtectionUI(isTrackingActive);
        
        // Check with Firebase if device is marked as lost
        locationManager.isDeviceLost(deviceId, task -> {
            if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                // Device is marked as lost in Firebase
                showLostDeviceUI();
            }
        });
    }

    private void showLostDeviceUI() {
        // Additional UI updates when device is reported as lost
        runOnUiThread(() -> {
            activationStatus.setText(R.string.device_is_lost);
            activationStatus.setTextColor(getResources().getColor(R.color.colorError, getTheme()));
        });
    }

    private void toggleProtection() {
        if (!authManager.isUserLoggedIn()) {
            // User is not logged in, prompt for sign in
            Toast.makeText(this, "You need to sign in to use this feature", Toast.LENGTH_SHORT).show();
            // TODO: Navigate to sign in screen
                return;
            }
            
        if (!permissionManager.hasLocationPermissions()) {
            // Request location permissions
            permissionManager.requestLocationPermissions(this);
            return;
        }
        
        if (isTrackingActive) {
            // Stop tracking
            stopLocationTracking();
        } else {
            // Start tracking
            startLocationTracking();
        }
    }

    private void startLocationTracking() {
        // Request background location permission if needed (Android 10+)
        if (!permissionManager.hasBackgroundLocationPermission()) {
            permissionManager.requestBackgroundLocationPermission(this);
            return;
        }
        
        // Request notification permission if needed (Android 13+)
        if (!permissionManager.hasNotificationPermission()) {
            // This will be handled in the permission result
            permissionManager.requestLocationPermissions(this);
                return;
            }
            
        // Start the location tracking service
        Intent serviceIntent = new Intent(this, LocationTrackingService.class);
        startForegroundService(serviceIntent);
        
        // Update Firebase to mark device as active
        LocationData initialLocation = new LocationData(0, 0, System.currentTimeMillis(), deviceId);
        initialLocation.setBatteryLevel(DeviceUtils.getBatteryLevel(this));
        
        locationManager.updateLocationData(deviceId, initialLocation, task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "Initial location data sent to Firebase");
                // Update the map card
                updateLocationCardMap();
            } else {
                Log.e(TAG, "Failed to send initial location data", task.getException());
            }
        });
        
        // Update UI
        isTrackingActive = true;
        updateProtectionUI(true);
        
        // Start periodic updates
        updateHandler.post(updateRunnable);
    }

    private void stopLocationTracking() {
        // Stop the location tracking service
        Intent serviceIntent = new Intent(this, LocationTrackingService.class);
        stopService(serviceIntent);
        
        // Update device status in Firebase as not lost
        locationManager.setDeviceLostStatus(deviceId, false, task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "Device marked as not lost in Firebase");
            } else {
                Log.e(TAG, "Failed to update device status in Firebase", task.getException());
            }
        });
        
        // Update UI
        isTrackingActive = false;
        updateProtectionUI(false);
        
        // Stop periodic updates
        updateHandler.removeCallbacks(updateRunnable);
    }

    private void updateProtectionUI(boolean isActive) {
        runOnUiThread(() -> {
            // Update activation button text
            activateButton.setText(isActive ? R.string.deactivate : R.string.activate);
            
            // Update status text
            activationStatus.setText(isActive ? R.string.protection_active : R.string.protection_inactive);
            activationStatus.setTextColor(getResources().getColor(
                    isActive ? R.color.colorSuccess : R.color.colorAccent, 
                    getTheme()));
            
            // Update active indicators
            trackingActiveIndicator.setVisibility(isActive ? View.VISIBLE : View.INVISIBLE);
            
            // For demo purposes, we'll also toggle the other indicators
            // In a real app, these would be controlled by their respective features
            blockingActiveIndicator.setVisibility(View.INVISIBLE);
            wipeActiveIndicator.setVisibility(View.INVISIBLE);
            audioActiveIndicator.setVisibility(View.INVISIBLE);
        });
    }

    // Feature card click handlers
    private void handleTrackingCardClick() {
        // Only update the map in the location card
        updateLocationCardMap();
        
        // Scroll to the top to show the map card
        findViewById(R.id.scrollView).scrollTo(0, 0);
        
        // Show toast with location status
        Toast.makeText(this, "Updating real-time location", Toast.LENGTH_SHORT).show();
    }

    private void handleRemoteBlockingCardClick() {
        // Always show device selection dialog
        showDeviceSelectionDialog();
    }

    /**
     * Shows a dialog for selecting which device to lock
     */
    private void showDeviceSelectionDialog() {
        // Show progress dialog while fetching devices
        AlertDialog progressDialog = new AlertDialog.Builder(this)
            .setTitle(R.string.loading_devices)
            .setMessage(R.string.please_wait)
            .setCancelable(false)
            .show();
            
        // Get all devices for the current user
        deviceRegistrationManager.getUserDevices(task -> {
            // Dismiss progress dialog
            progressDialog.dismiss();
            
            if (!task.isSuccessful() || task.getResult() == null) {
                // Show error message
                new AlertDialog.Builder(this)
                    .setTitle(R.string.error)
                    .setMessage(R.string.failed_to_load_devices)
                    .setPositiveButton(R.string.ok, null)
                    .show();
                return;
            }
            
            // Get devices from snapshot
            DataSnapshot devicesSnapshot = task.getResult();
            if (!devicesSnapshot.exists() || !devicesSnapshot.hasChildren()) {
                // No devices found
                new AlertDialog.Builder(this)
                    .setTitle(R.string.remote_lock_title)
                    .setMessage(R.string.no_devices_found)
                    .setPositiveButton(R.string.ok, null)
                    .show();
                return;
            }
            
            // Create a list of devices
            final Map<String, String> deviceMap = new HashMap<>();
            final List<String> deviceNames = new ArrayList<>();
            final List<String> deviceIds = new ArrayList<>();
            final List<Boolean> deviceOnlineStatus = new ArrayList<>();
            
            // Current device ID
            String currentDeviceId = deviceRegistrationManager.getDeviceId();
            
            // Populate device lists
            for (DataSnapshot deviceSnapshot : devicesSnapshot.getChildren()) {
                String deviceId = deviceSnapshot.getKey();
                
                // Skip current device
                if (deviceId.equals(currentDeviceId)) {
                    continue;
                }
                
                // Get device info
                String model = deviceSnapshot.child("model").getValue(String.class);
                String name = deviceSnapshot.child("name").getValue(String.class);
                Long lastSeen = deviceSnapshot.child("last_seen").getValue(Long.class);
                
                // Create display name
                String displayName = (model != null ? model : "Unknown Device");
                if (name != null && !name.isEmpty()) {
                    displayName += " (" + name + ")";
                }
                
                // Check if device is online
                boolean isOnline = false;
                if (lastSeen != null) {
                    isOnline = deviceRegistrationManager.isDeviceOnline(lastSeen);
                }
                
                // Add to lists
                deviceNames.add(displayName);
                deviceIds.add(deviceId);
                deviceOnlineStatus.add(isOnline);
                deviceMap.put(deviceId, displayName);
            }
            
            // Check if we have any other devices
            if (deviceIds.isEmpty()) {
                new AlertDialog.Builder(this)
                    .setTitle(R.string.remote_lock_title)
                    .setMessage(R.string.no_other_devices)
                    .setPositiveButton(R.string.ok, null)
                    .show();
                return;
            }
            
            // Create adapter for device list
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, 
                android.R.layout.simple_list_item_1, deviceNames) {
    @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View view = super.getView(position, convertView, parent);
                    TextView textView = (TextView) view.findViewById(android.R.id.text1);
                    
                    // Add online/offline indicator
                    if (deviceOnlineStatus.get(position)) {
                        textView.setCompoundDrawablesWithIntrinsicBounds(
                            R.drawable.ic_online_indicator, 0, 0, 0);
                } else {
                        textView.setCompoundDrawablesWithIntrinsicBounds(
                            R.drawable.ic_offline_indicator, 0, 0, 0);
                    }
                    
                    textView.setCompoundDrawablePadding(16);
                    return view;
                }
            };
            
            // Show device selection dialog
            new AlertDialog.Builder(this)
                .setTitle(R.string.select_device_to_lock)
                .setAdapter(adapter, (dialog, which) -> {
                    // Get selected device
                    String selectedDeviceId = deviceIds.get(which);
                    String selectedDeviceName = deviceNames.get(which);
                    
                    // Show lock confirmation dialog
                    showLockConfirmationDialog(selectedDeviceId, selectedDeviceName);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
        });
    }
    
    /**
     * Shows a confirmation dialog for locking a device
     */
    private void showLockConfirmationDialog(String targetDeviceId, String deviceName) {
        // Create a dialog to send a lock command to the selected device
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_remote_lock_device_selection, null);
        builder.setView(dialogView);
        
        // Set up the device name
        TextView deviceNameText = dialogView.findViewById(R.id.deviceNameText);
        deviceNameText.setText(getString(R.string.selected_device, deviceName));
        
        // Set up the lock message field
        EditText lockMessageInput = dialogView.findViewById(R.id.lockMessageInput);
        
        // Build and show the dialog
        builder.setTitle(R.string.remote_lock_title)
               .setMessage(R.string.remote_lock_confirmation)
               .setPositiveButton(R.string.lock_device, null)  // We'll override this below
               .setNegativeButton(R.string.cancel, null);
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
        // Override the positive button to prevent automatic dismissal
        Button lockButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        lockButton.setOnClickListener(v -> {
            // Get message if provided
            String message = lockMessageInput.getText().toString().trim();
            
            // Show progress dialog
            AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.sending_lock_command)
                .setMessage(R.string.please_wait)
                .setCancelable(false)
                .show();
            
            // Send lock command to selected device
            remoteLockManager.sendLockCommand(targetDeviceId, message, task -> {
                // Dismiss progress dialog
                progressDialog.dismiss();
                
                // Dismiss the original dialog
                dialog.dismiss();
                
                if (task.isSuccessful()) {
                    // Show success message
                    Toast.makeText(this, R.string.device_locked_success, Toast.LENGTH_LONG).show();
            } else {
                    // Show error message
                    String errorMsg = task.getException() != null ? 
                            task.getException().getMessage() : 
                            getString(R.string.unknown_error);
                    
                    new AlertDialog.Builder(this)
                        .setTitle(R.string.device_lock_failed)
                        .setMessage(errorMsg)
                        .setPositiveButton(R.string.ok, null)
                        .show();
                }
            });
        });
    }

    private void handleDataWipeCardClick() {
        Toast.makeText(this, "Data wipe feature coming soon", Toast.LENGTH_SHORT).show();
    }

    private void handleAudioSignalCardClick() {
        try {
            // Show dialog to configure and start signal
            LoudSignalDialog dialog = LoudSignalDialog.newInstance(deviceId, pairedDeviceId);
            dialog.show(getSupportFragmentManager(), "loud_signal_dialog");
        } catch (Exception e) {
            // Fallback to direct start in case the dialog has issues
            try {
                Toast.makeText(this, "Starting emergency signal...", Toast.LENGTH_SHORT).show();
                startSignalFromRemote(30, 100); // Use default values
            } catch (Exception ex) {
                Toast.makeText(this, "Could not start signal: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
        }
        
        @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        boolean permissionsGranted = permissionManager.handlePermissionResult(
                requestCode, permissions, grantResults);
        
        if (permissionsGranted) {
            // If background location was requested and granted, start tracking
            if (requestCode == LocationPermissionManager.REQUEST_BACKGROUND_LOCATION) {
                startLocationTracking();
            } else if (requestCode == LocationPermissionManager.REQUEST_LOCATION_PERMISSION) {
                // Check if background permission is needed
                if (!permissionManager.hasBackgroundLocationPermission()) {
                    permissionManager.requestBackgroundLocationPermission(this);
                } else {
                    startLocationTracking();
                }
            }
            } else {
            // Permissions denied, show error message
            Toast.makeText(this, R.string.location_permission_required, Toast.LENGTH_LONG).show();
        }
        }
        
        @Override
    public void onBackPressed() {
        // Check if the expanded map is visible
        View mapContainer = findViewById(R.id.mapContainer);
        if (mapContainer.getVisibility() == View.VISIBLE) {
            // Hide the map with animation
            hideExpandedMap();
            return;
        }
        
        // Otherwise perform default back behavior
        super.onBackPressed();
        }
        
        @Override
    protected void onResume() {
        super.onResume();
        
        // Start periodic updates if tracking is active
        if (isTrackingActive) {
            updateHandler.post(updateRunnable);
        }
        
        // Update device online status
        deviceRegistrationManager.updateOnlineStatus();
        }
        
        @Override
    protected void onPause() {
        super.onPause();
        
        // Remove any pending update callbacks
        updateHandler.removeCallbacks(updateRunnable);
        
        // Update device online status before pausing
        deviceRegistrationManager.updateOnlineStatus();
    }

    /**
     * Initialize UI elements for the expanded map view
     */
    private void initializeExpandedMapUI() {
        // Setup close button for expanded map
        ImageButton closeButton = findViewById(R.id.closeExpandedMapButton);
        closeButton.setOnClickListener(v -> hideExpandedMap());
        
        // Initialize expanded map UI elements
        expandedDeviceLocation = findViewById(R.id.expanded_device_location);
        expandedLocationAddress = findViewById(R.id.expanded_location_address);
        expandedLastUpdated = findViewById(R.id.expanded_last_updated);
        expandedMapAccuracy = findViewById(R.id.expanded_map_accuracy);
        expandedRefreshLocation = findViewById(R.id.expanded_refresh_location);
        
        // Initialize map control buttons
        zoomInButton = findViewById(R.id.zoomInButton);
        zoomOutButton = findViewById(R.id.zoomOutButton);
        recenterButton = findViewById(R.id.recenterButton);
        
        // Set click listeners for map controls
        zoomInButton.setOnClickListener(v -> zoomIn());
        zoomOutButton.setOnClickListener(v -> zoomOut());
        recenterButton.setOnClickListener(v -> recenterMap());
        
        // Set click listener for refresh button in expanded map
        expandedRefreshLocation.setOnClickListener(v -> {
            updateLocationData();
            Toast.makeText(this, R.string.updating_location, Toast.LENGTH_SHORT).show();
        });
    }
    
    /**
     * Zoom in on the map
     */
    private void zoomIn() {
        if (expandedMap != null) {
            currentZoomLevel = Math.min(currentZoomLevel + 1, 20);
            expandedMap.animateCamera(CameraUpdateFactory.zoomTo(currentZoomLevel));
        }
    }
    
    /**
     * Zoom out on the map
     */
    private void zoomOut() {
        if (expandedMap != null) {
            currentZoomLevel = Math.max(currentZoomLevel - 1, 5);
            expandedMap.animateCamera(CameraUpdateFactory.zoomTo(currentZoomLevel));
        }
    }
    
    /**
     * Recenter the map on the last known location
     */
    private void recenterMap() {
        if (expandedMap != null && lastKnownLocation != null) {
            expandedMap.animateCamera(CameraUpdateFactory.newLatLngZoom(lastKnownLocation, currentZoomLevel));
        } else {
            updateLocationData();
        }
    }
    
    /**
     * Hide the expanded map
     */
    private void hideExpandedMap() {
        View mapContainer = findViewById(R.id.mapContainer);
        
        // Animate hiding the map
        mapContainer.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> {
                    mapContainer.setVisibility(View.GONE);
                    isMapExpanded = false;
                })
                .start();
    }

    /**
     * Configure the expanded map settings
     */
    private void configureExpandedMap() {
        if (expandedMap == null) return;
        
        // Configure expanded map settings
        expandedMap.getUiSettings().setZoomControlsEnabled(false);
        expandedMap.getUiSettings().setMapToolbarEnabled(false);
        expandedMap.getUiSettings().setCompassEnabled(true);
        expandedMap.getUiSettings().setRotateGesturesEnabled(true);
        expandedMap.getUiSettings().setTiltGesturesEnabled(true);
        
        try {
            // Apply custom dark map style
            boolean success = expandedMap.setMapStyle(
                    MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style));
            if (!success) {
                Log.e(TAG, "Style parsing failed");
            }
            
            if (permissionManager.hasLocationPermissions()) {
                expandedMap.setMyLocationEnabled(true);
                expandedMap.getUiSettings().setMyLocationButtonEnabled(false);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Error enabling expanded map location", e);
        } catch (Resources.NotFoundException e) {
            Log.e(TAG, "Can't find map style JSON", e);
        }
    }

    /**
     * Expands the map view when the map in location card is clicked
     */
    private void expandMapView() {
        // The map container
        View mapContainer = findViewById(R.id.mapContainer);
        
        // If map container is not already showing
        if (mapContainer.getVisibility() != View.VISIBLE) {
            // Make sure the map is configured
            if (expandedMap == null) {
                SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.mapFragment);
                if (mapFragment != null) {
                    mapFragment.getMapAsync(map -> {
                        expandedMap = map;
                        configureExpandedMap();
                        updateExpandedMap();
                    });
                }
            } else {
                updateExpandedMap();
            }
            
            // Copy data from location card to expanded map UI
            if (expandedDeviceLocation != null && deviceLocation != null) {
                expandedDeviceLocation.setText(deviceLocation.getText());
            }
            
            if (expandedLocationAddress != null && locationAddress != null) {
                expandedLocationAddress.setText(locationAddress.getText());
            }
            
            if (expandedLastUpdated != null && lastUpdated != null) {
                expandedLastUpdated.setText(lastUpdated.getText());
            }
            
            if (expandedMapAccuracy != null) {
                TextView locationAccuracy = findViewById(R.id.location_accuracy);
                if (locationAccuracy != null) {
                    expandedMapAccuracy.setText(locationAccuracy.getText());
                }
            }
            
            // Show the map container with animation
            mapContainer.setAlpha(0f);
            mapContainer.setVisibility(View.VISIBLE);
            mapContainer.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .withEndAction(() -> isMapExpanded = true)
                    .start();
            
        } else {
            // Hide map if already visible
            hideExpandedMap();
        }
    }
    
    /**
     * Update the expanded map with current location
     */
    private void updateExpandedMap() {
        if (expandedMap == null) {
            return;
        }
        
        try {
            if (permissionManager.hasLocationPermissions()) {
                FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
                fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                    if (location != null) {
                        // Update map with current location
                        lastKnownLocation = new LatLng(location.getLatitude(), location.getLongitude());
                        expandedMap.clear();
                        expandedMap.addMarker(new MarkerOptions().position(lastKnownLocation).title("Current Location"));
                        expandedMap.moveCamera(CameraUpdateFactory.newLatLngZoom(lastKnownLocation, currentZoomLevel));
                        
                        // Update accuracy info
                        if (expandedMapAccuracy != null) {
                            String accuracyText = "Accuracy: " + Math.round(location.getAccuracy()) + "m";
                            expandedMapAccuracy.setText(accuracyText);
                        }
                    }
                });
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception getting location for expanded map", e);
        }
    }

    /**
     * Updates the location data from Firebase and refreshes the UI
     */
    private void updateLocationData() {
        // Update both the card map and expanded map
        updateLocationCardMap();
        if (isMapExpanded) {
            updateExpandedMap();
        }
        
        // Update expanded map UI if it's visible
        View mapContainer = findViewById(R.id.mapContainer);
        if (mapContainer.getVisibility() == View.VISIBLE) {
            // Copy data from location card to expanded map UI
            if (expandedDeviceLocation != null && deviceLocation != null) {
                expandedDeviceLocation.setText(deviceLocation.getText());
            }
            
            if (expandedLocationAddress != null && locationAddress != null) {
                expandedLocationAddress.setText(locationAddress.getText());
            }
            
            if (expandedLastUpdated != null && lastUpdated != null) {
                expandedLastUpdated.setText(lastUpdated.getText());
            }
        }
    }

    /**
     * Gets paired device ID from shared preferences
     */
    private String getPairedDeviceId() {
        // Look for stored paired device ID
        String storedPairedDeviceId = getSharedPreferences("device_prefs", MODE_PRIVATE)
                .getString("paired_device_id", "");
                
        if (storedPairedDeviceId.isEmpty()) {
            // For demo/testing, retrieve a test paired device ID if none is stored
            // This allows testing the feature without proper pairing flow
            return getDemoPairedDeviceId();
        }
                
        return storedPairedDeviceId;
    }
    
    /**
     * Gets the name of the paired device
     */
    private String getPairedDeviceName() {
        // First try to get from preferences
        String storedName = getSharedPreferences("device_prefs", MODE_PRIVATE)
                .getString("paired_device_name", "");
                
        if (!storedName.isEmpty()) {
            return storedName;
        }
        
        // If no stored name but we have an ID, use a generic name
        if (pairedDeviceId != null && !pairedDeviceId.isEmpty()) {
            return getString(R.string.paired_device_name);
        }
        
        // No device paired
        return getString(R.string.no_paired_device);
    }
    
    /**
     * Get a demo paired device ID for testing
     * In a real app, this would be retrieved from a proper device pairing system
     */
    private String getDemoPairedDeviceId() {
        // Get this device's ID
        String thisDeviceId = android.provider.Settings.Secure.getString(
                getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
        
        // For testing purposes, we'll create a consistent paired device ID
        // that is different from this device's ID but deterministic
        if (thisDeviceId != null && !thisDeviceId.isEmpty()) {
            // Create a reversed version of the ID to ensure it's different
            StringBuilder reversedId = new StringBuilder(thisDeviceId).reverse();
            
            // Log both IDs for debugging
            Log.d(TAG, "This device ID: " + thisDeviceId);
            Log.d(TAG, "Generated paired device ID: " + reversedId.toString());
            
            // Store the paired device name for UI display
            getSharedPreferences("device_prefs", MODE_PRIVATE)
                .edit()
                .putString("paired_device_name", "Test Paired Device")
                .apply();
            
            return reversedId.toString();
        }
        
        // Fallback to a static ID if we can't get the device ID
        return "DEMO-PAIRED-DEVICE-ID";
    }
    
    /**
     * Handles manually testing/pairing devices
     * This is a development helper method not suitable for production
     */
    private void setupDevicePairing() {
        // Used only for demo to create device pairing
        String thisDeviceId = android.provider.Settings.Secure.getString(
                getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
                
        // Check if this device ID ends with "1" or "2" to differentiate
        if (thisDeviceId != null && thisDeviceId.length() > 3) {
            String lastDigits = thisDeviceId.substring(thisDeviceId.length() - 3);
            
            // Store pairing based on device ID pattern
            String pairedDeviceId = "DEVICE-" + 
                    (lastDigits.equals("001") ? "002" : "001");
                    
            // Store in preferences
            getSharedPreferences("device_prefs", MODE_PRIVATE)
                    .edit()
                    .putString("paired_device_id", pairedDeviceId)
                    .putString("paired_device_name", "Test Paired Device")
                    .apply();
                    
            Toast.makeText(this, "Device paired with: " + pairedDeviceId, Toast.LENGTH_SHORT).show();
        }
    }
    
    // Start the signal service from a remote command
    private void startSignalFromRemote(int durationSeconds, int volumeLevel) {
        try {
            Intent intent = new Intent(this, LoudSignalService.class);
            intent.putExtra(LoudSignalService.EXTRA_DURATION, durationSeconds);
            intent.putExtra(LoudSignalService.EXTRA_VOLUME, volumeLevel);
            
            // Add an action to the intent for better compatibility
            intent.setAction("com.example.secuphone.START_SIGNAL");
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            
            runOnUiThread(() -> {
                try {
                    Toast.makeText(this, R.string.signal_in_progress, Toast.LENGTH_SHORT).show();
                    // Update UI if needed
                    if (audioActiveIndicator != null) {
                        audioActiveIndicator.setVisibility(View.VISIBLE);
                    }
                } catch (Exception e) {
                    // Ignore UI update errors
                }
            });
        } catch (Exception e) {
            runOnUiThread(() -> {
                Toast.makeText(this, "Error starting signal: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }
    }
    
    // Stop the signal service from a remote command
    private void stopSignalFromRemote() {
        try {
            Intent intent = new Intent(this, LoudSignalService.class);
            intent.setAction(LoudSignalService.ACTION_STOP_SIGNAL);
            startService(intent);
            
            runOnUiThread(() -> {
                try {
                    Toast.makeText(this, R.string.signal_stopped, Toast.LENGTH_SHORT).show();
                    // Update UI if needed
                    if (audioActiveIndicator != null) {
                        audioActiveIndicator.setVisibility(View.INVISIBLE);
                    }
                } catch (Exception e) {
                    // Ignore UI update errors
                }
            });
        } catch (Exception e) {
            runOnUiThread(() -> {
                Toast.makeText(this, "Error stopping signal: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }
    }

    /**
     * Check if device admin is active and start the Remote Lock Service if it is
     */
    private void checkAndStartRemoteLockService() {
        // Check if device admin permission is granted
        if (remoteLockManager.isAdminActive()) {
            // Start the service
            Log.d(TAG, "Device admin is active, starting Remote Lock Service");
            remoteLockManager.startRemoteLockService();
            
            // Update UI to show blocking is active
            if (blockingActiveIndicator != null) {
                blockingActiveIndicator.setVisibility(View.VISIBLE);
            }
        } else {
            Log.d(TAG, "Device admin is not active, requesting permission");
            // Request device admin permission
            remoteLockManager.requestAdminPermission(this);
            
            // Update UI to show blocking is not active
            if (blockingActiveIndicator != null) {
                blockingActiveIndicator.setVisibility(View.INVISIBLE);
            }
        }
    }

    /**
     * Handle activity result from admin permission request
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        // Let RemoteLockManager handle the result
        boolean handled = remoteLockManager.handleActivityResult(requestCode, resultCode, data);
        
        if (handled) {
            // Update UI to show service is active
            if (blockingActiveIndicator != null) {
                blockingActiveIndicator.setVisibility(View.VISIBLE);
            }
            
            Toast.makeText(this, R.string.remote_lock_admin_enabled, Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    protected void onDestroy() {
        // Stop listening for signal commands
        remoteSignalManager.stopListeningForSignalCommands(deviceId);
        
        // Stop listening for device updates
        deviceRegistrationManager.stopListeningForDeviceUpdates();
        
        super.onDestroy();
    }
} 