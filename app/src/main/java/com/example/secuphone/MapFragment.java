package com.example.secuphone;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.secuphone.models.DeviceInfo;
import com.example.secuphone.models.LocationData;
import com.example.secuphone.utils.DeviceTrackingManager;
import com.example.secuphone.utils.DeviceUtils;
import com.example.secuphone.utils.FirebaseLocationManager;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapFragment extends Fragment implements OnMapReadyCallback {
    private static final String TAG = "MapFragment";
    private static final String ARG_DEVICE_ID = "deviceId";
    private static final int CURRENT_DEVICE_MARKER_HUE = 120; // Green for current device
    private static final int OTHER_DEVICE_MARKER_HUE = 240; // Blue for other devices

    // Google Maps components
    private GoogleMap googleMap;
    private Marker deviceMarker;
    private Map<String, Marker> deviceMarkers = new HashMap<>();

    // UI components
    private TextView deviceNameText;
    private TextView lastUpdateText;
    private TextView batteryLevelText;
    private TextView accuracyText;
    private Button refreshButton;
    private ProgressBar progressBar;
    private CardView infoCard;
    private TextView deviceCountText;
    private Button showAllDevicesButton;

    // Data
    private String deviceId;
    private FirebaseLocationManager locationManager;
    private DeviceTrackingManager deviceTrackingManager;
    private ValueEventListener locationListener;
    private LocationData lastLocationData;
    private boolean showAllDevices = false;
    private String currentUserId;

    public MapFragment() {
        // Required empty public constructor
    }

    /**
     * Create a new instance of the fragment with a device ID
     */
    public static MapFragment newInstance(String deviceId) {
        MapFragment fragment = new MapFragment();
        Bundle args = new Bundle();
        args.putString(ARG_DEVICE_ID, deviceId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            deviceId = getArguments().getString(ARG_DEVICE_ID);
        }

        locationManager = FirebaseLocationManager.getInstance();
        deviceTrackingManager = DeviceTrackingManager.getInstance();
        
        // Get current user ID
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize UI components
        deviceNameText = view.findViewById(R.id.deviceNameText);
        lastUpdateText = view.findViewById(R.id.lastUpdateText);
        batteryLevelText = view.findViewById(R.id.batteryLevelText);
        accuracyText = view.findViewById(R.id.accuracyText);
        refreshButton = view.findViewById(R.id.refreshButton);
        progressBar = view.findViewById(R.id.mapProgressBar);
        infoCard = view.findViewById(R.id.infoCard);
        deviceCountText = view.findViewById(R.id.deviceCountText);
        showAllDevicesButton = view.findViewById(R.id.showAllDevicesButton);

        // Initially hide the info card until we have data
        infoCard.setVisibility(View.GONE);
        
        if (deviceCountText != null) {
            deviceCountText.setVisibility(View.GONE);
        }
        
        // Set up map
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Set up refresh button
        refreshButton.setOnClickListener(v -> refreshLocation());
        
        // Set up show all devices button
        if (showAllDevicesButton != null) {
            showAllDevicesButton.setOnClickListener(v -> toggleDeviceDisplay());
        }
    }
    
    public void toggleDeviceDisplay() {
        showAllDevices = !showAllDevices;
        
        if (showAllDevices) {
            // Start tracking all devices
            startTrackingAllDevices();
            showAllDevicesButton.setText(R.string.show_current_device);
        } else {
            // Stop tracking all devices and show only current device
            stopTrackingAllDevices();
            showCurrentDeviceOnly();
            showAllDevicesButton.setText(R.string.show_all_devices);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        this.googleMap = googleMap;
        
        // Configure map settings
        googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setCompassEnabled(true);
        googleMap.getUiSettings().setMyLocationButtonEnabled(true);
        googleMap.getUiSettings().setAllGesturesEnabled(true);
        googleMap.getUiSettings().setMapToolbarEnabled(true);
        
        // Set marker click listener for device info
        googleMap.setOnMarkerClickListener(marker -> {
            String markerId = (String) marker.getTag();
            if (markerId != null) {
                updateInfoCardForDevice(markerId);
            }
            // Return false to show the info window as well
            return false;
        });
        
        // Enable showing the user's current location
        try {
            if (ContextCompat.checkSelfPermission(requireContext(), 
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                googleMap.setMyLocationEnabled(true);
                
                // Get current location immediately to center map
                FusedLocationProviderClient fusedLocationClient = 
                        LocationServices.getFusedLocationProviderClient(requireActivity());
                fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                    if (location != null) {
                        LatLng currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15f));
                    }
                });
            } else {
                // Request permission
                requestPermissions(
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        1001);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Error enabling my location", e);
        }
        
        // Start listening for location updates
        startLocationUpdates();
        
        // Also load the last known location immediately
        loadLastKnownLocation();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 1001) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission was granted
                try {
                    googleMap.setMyLocationEnabled(true);
                    loadLastKnownLocation();
                } catch (SecurityException e) {
                    Log.e(TAG, "Error enabling location after permission", e);
                }
            } else {
                Toast.makeText(getContext(), "Location permission required to show your location", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Start listening for real-time location updates
     */
    private void startLocationUpdates() {
        if (locationListener != null) {
            return;
        }
        
        showLoading(true);
        
        locationListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Map<String, Object> locationMap = (HashMap<String, Object>) snapshot.getValue();
                    if (locationMap != null) {
                        updateLocationFromMap(locationMap);
                    }
                }
                showLoading(false);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Location updates cancelled", error.toException());
                showLoading(false);
            }
        };
        
        locationManager.listenForLocationUpdates(deviceId, locationListener);
    }

    private void loadLastKnownLocation() {
        showLoading(true);
        
        locationManager.getLastKnownLocation(deviceId, task -> {
            showLoading(false);
            
            if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                Map<String, Object> locationMap = (HashMap<String, Object>) task.getResult().getValue();
                if (locationMap != null) {
                    updateLocationFromMap(locationMap);
                }
            } else {
                Log.d(TAG, "No last known location found");
                showCurrentDeviceLocation();
            }
        });
    }

    private void showCurrentDeviceLocation() {
        try {
            if (getActivity() == null || 
                    ContextCompat.checkSelfPermission(getActivity(), 
                            Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            
            FusedLocationProviderClient fusedLocationClient = 
                    LocationServices.getFusedLocationProviderClient(requireActivity());
                    
            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null && googleMap != null) {
                    LocationData locationData = new LocationData(
                            location.getLatitude(),
                            location.getLongitude(),
                            System.currentTimeMillis(),
                            deviceId,
                            location.getAccuracy(),
                            location.getAltitude(),
                            location.getSpeed(),
                            DeviceUtils.getBatteryLevel(requireContext())
                    );
                    
                    // Update UI with current location
                    updateMapMarker(locationData);
                    
                    // Show device info
                    lastLocationData = locationData;
                    showDeviceInfo(locationData);
                    
                    // Store location in Firebase if we have an active tracking service
                    if (getActivity() instanceof FindPhoneActivity) {
                        FindPhoneActivity activity = (FindPhoneActivity) getActivity();
                        // Check if the tracking is active through a public property or method
                        try {
                            // Attempt to call method if it exists
                            boolean isTracking = activity.isTrackingActive();
                            if (isTracking) {
                                locationManager.updateLocationData(deviceId, locationData, null);
                            }
                        } catch (Exception e) {
                            // If method doesn't exist, log it and continue
                            Log.e(TAG, "Could not check tracking status: " + e.getMessage());
                            // Default to updating location data anyway
                            locationManager.updateLocationData(deviceId, locationData, null);
                        }
                    }
                }
            });
        } catch (SecurityException e) {
            Log.e(TAG, "Error getting current location", e);
        }
    }

    @SuppressLint("DefaultLocale")
    private void updateLocationFromMap(Map<String, Object> locationMap) {
        try {
            double latitude = 0;
            double longitude = 0;
            long timestamp = 0;
            String deviceId = "";
            double accuracy = 0;
            double altitude = 0;
            float speed = 0;
            String batteryLevel = "";
            
            if (locationMap.containsKey("latitude")) {
                latitude = Double.parseDouble(locationMap.get("latitude").toString());
            }
            
            if (locationMap.containsKey("longitude")) {
                longitude = Double.parseDouble(locationMap.get("longitude").toString());
            }
            
            if (locationMap.containsKey("timestamp")) {
                timestamp = Long.parseLong(locationMap.get("timestamp").toString());
            }
            
            if (locationMap.containsKey("deviceId")) {
                deviceId = locationMap.get("deviceId").toString();
            }
            
            if (locationMap.containsKey("accuracy")) {
                accuracy = Double.parseDouble(locationMap.get("accuracy").toString());
            }
            
            if (locationMap.containsKey("altitude")) {
                altitude = Double.parseDouble(locationMap.get("altitude").toString());
            }
            
            if (locationMap.containsKey("speed")) {
                speed = Float.parseFloat(locationMap.get("speed").toString());
            }
            
            if (locationMap.containsKey("batteryLevel")) {
                batteryLevel = locationMap.get("batteryLevel").toString();
            }
            
            LocationData locationData = new LocationData(
                    latitude, 
                    longitude, 
                    timestamp, 
                    deviceId, 
                    accuracy, 
                    altitude, 
                    speed, 
                    batteryLevel
            );
            
            // Set as lost if the map indicates it
            if (locationMap.containsKey("isLost")) {
                locationData.setLost(Boolean.parseBoolean(locationMap.get("isLost").toString()));
            }
            
            // Update UI with location
            updateMapMarker(locationData);
            
            // Show device info in card
            lastLocationData = locationData;
            showDeviceInfo(locationData);
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing location data", e);
        }
    }

    private void updateMapMarker(LocationData locationData) {
        if (googleMap == null || locationData == null || 
                locationData.getLatitude() == 0 && locationData.getLongitude() == 0) {
            return;
        }
        
        LatLng position = new LatLng(locationData.getLatitude(), locationData.getLongitude());
        
        // Remove existing marker if there is one
        if (deviceMarker != null) {
            deviceMarker.remove();
        }
        
        // Create a new marker
        MarkerOptions markerOptions = new MarkerOptions()
                .position(position)
                .title(DeviceUtils.getDeviceName(requireContext()))
                .snippet("Last seen: " + DateUtils.getRelativeTimeSpanString(
                        locationData.getTimestamp(), 
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS))
                .icon(BitmapDescriptorFactory.defaultMarker(CURRENT_DEVICE_MARKER_HUE));
        
        deviceMarker = googleMap.addMarker(markerOptions);
        if (deviceMarker != null) {
            deviceMarker.setTag(deviceId);
        }
        
        // Move camera to the marker position
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 15f));
    }
    
    private void showDeviceInfo(LocationData locationData) {
        if (locationData == null || getContext() == null) {
            return;
        }
        
        infoCard.setVisibility(View.VISIBLE);
        
        deviceNameText.setText(DeviceUtils.getDeviceName(requireContext()));
        
        // Format the timestamp as relative time string
        CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(
                locationData.getTimestamp(),
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS);
        lastUpdateText.setText(getString(R.string.last_seen, timeAgo));
        
        // Set battery level if available
        if (locationData.getBatteryLevel() != null && !locationData.getBatteryLevel().isEmpty()) {
            batteryLevelText.setText(getString(R.string.battery_level, locationData.getBatteryLevel()));
            batteryLevelText.setVisibility(View.VISIBLE);
        } else {
            batteryLevelText.setVisibility(View.GONE);
        }
        
        // Set accuracy if available
        if (locationData.getAccuracy() > 0) {
            accuracyText.setText(getString(R.string.accuracy_meters, (int)locationData.getAccuracy()));
            accuracyText.setVisibility(View.VISIBLE);
        } else {
            accuracyText.setVisibility(View.GONE);
        }
    }

    private void refreshLocation() {
        if (showAllDevices) {
            refreshAllDevices();
        } else {
            showLoading(true);
            showCurrentDeviceLocation();
        }
    }
    
    public void refreshMap() {
        refreshLocation();
    }
    
    /**
     * Start tracking all devices under the same account
     */
    private void startTrackingAllDevices() {
        if (deviceTrackingManager == null) {
            deviceTrackingManager = DeviceTrackingManager.getInstance();
        }
        
        showLoading(true);
        
        // Clear existing markers except current device
        clearAllDeviceMarkers(false);
        
        // Start tracking all devices
        deviceTrackingManager.startTrackingAllDevices(devices -> {
            updateDeviceMarkers(devices);
            showLoading(false);
            
            // Update device count text
            if (deviceCountText != null) {
                deviceCountText.setVisibility(View.VISIBLE);
                deviceCountText.setText(getString(R.string.devices_connected, devices.size()));
            }
        });
    }
    
    /**
     * Stop tracking all devices and clear markers
     */
    private void stopTrackingAllDevices() {
        if (deviceTrackingManager != null) {
            deviceTrackingManager.stopTrackingAllDevices();
        }
        
        // Clear all device markers except current device
        clearAllDeviceMarkers(false);
        
        // Hide device count text
        if (deviceCountText != null) {
            deviceCountText.setVisibility(View.GONE);
        }
    }
    
    /**
     * Show only the current device on the map
     */
    private void showCurrentDeviceOnly() {
        // Clear all device markers
        clearAllDeviceMarkers(true);
        
        // Show current device location
        showCurrentDeviceLocation();
    }
    
    /**
     * Clear all device markers
     * @param includeCurrentDevice Whether to include current device marker
     */
    private void clearAllDeviceMarkers(boolean includeCurrentDevice) {
        if (googleMap == null) return;
        
        // Remove all markers except current device if needed
        for (Map.Entry<String, Marker> entry : deviceMarkers.entrySet()) {
            String markerId = entry.getKey();
            Marker marker = entry.getValue();
            
            if (includeCurrentDevice || !markerId.equals(deviceId)) {
                if (marker != null) {
                    marker.remove();
                }
            }
        }
        
        // Clear device markers map
        deviceMarkers.clear();
        
        // Restore current device marker if needed
        if (!includeCurrentDevice && deviceMarker != null) {
            deviceMarkers.put(deviceId, deviceMarker);
        }
    }
    
    /**
     * Update all device markers on the map
     */
    private void updateDeviceMarkers(List<DeviceInfo> devices) {
        if (googleMap == null) return;
        
        Map<String, DeviceInfo> deviceInfoMap = new HashMap<>();
        for (DeviceInfo device : devices) {
            deviceInfoMap.put(device.getDeviceId(), device);
        }
        
        // Add or update markers for all devices
        for (DeviceInfo device : devices) {
            String deviceId = device.getDeviceId();
            
            // Skip devices with no location data
            if (device.getLatitude() == 0 && device.getLongitude() == 0) {
                continue;
            }
            
            // Check if we already have a marker for this device
            Marker existingMarker = deviceMarkers.get(deviceId);
            LatLng position = new LatLng(device.getLatitude(), device.getLongitude());
            
            if (existingMarker != null) {
                // Update existing marker
                existingMarker.setPosition(position);
                existingMarker.setTitle(device.getDisplayName());
                
                // Update snippet with last seen time
                CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(
                        device.getLastSeen(),
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS);
                existingMarker.setSnippet("Last seen: " + timeAgo);
            } else {
                // Create a new marker
                float markerHue = deviceId.equals(this.deviceId) ? 
                        CURRENT_DEVICE_MARKER_HUE : OTHER_DEVICE_MARKER_HUE;
                
                MarkerOptions markerOptions = new MarkerOptions()
                        .position(position)
                        .title(device.getDisplayName())
                        .snippet("Last seen: " + DateUtils.getRelativeTimeSpanString(
                                device.getLastSeen(),
                                System.currentTimeMillis(),
                                DateUtils.MINUTE_IN_MILLIS))
                        .icon(BitmapDescriptorFactory.defaultMarker(markerHue));
                
                Marker newMarker = googleMap.addMarker(markerOptions);
                if (newMarker != null) {
                    newMarker.setTag(deviceId);
                    deviceMarkers.put(deviceId, newMarker);
                    
                    // If this is the current device, update our device marker reference
                    if (deviceId.equals(this.deviceId)) {
                        deviceMarker = newMarker;
                    }
                }
            }
        }
        
        // Remove markers for devices no longer in the list
        List<String> markerIdsToRemove = new ArrayList<>();
        for (String markerId : deviceMarkers.keySet()) {
            if (!deviceInfoMap.containsKey(markerId)) {
                markerIdsToRemove.add(markerId);
            }
        }
        
        for (String markerId : markerIdsToRemove) {
            Marker marker = deviceMarkers.get(markerId);
            if (marker != null) {
                marker.remove();
            }
            deviceMarkers.remove(markerId);
        }
        
        // Fit map to include all markers
        if (!deviceMarkers.isEmpty() && deviceMarkers.size() > 1) {
            fitMapToMarkers();
        }
    }
    
    /**
     * Fit the map to show all markers
     */
    private void fitMapToMarkers() {
        if (googleMap == null || deviceMarkers.isEmpty()) {
            return;
        }
        
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (Marker marker : deviceMarkers.values()) {
            builder.include(marker.getPosition());
        }
        
        try {
            LatLngBounds bounds = builder.build();
            int padding = 100; // offset from edges of the map
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
        } catch (Exception e) {
            Log.e(TAG, "Error fitting map to markers", e);
        }
    }
    
    /**
     * Refresh all devices on the map
     */
    private void refreshAllDevices() {
        if (deviceTrackingManager == null) {
            deviceTrackingManager = DeviceTrackingManager.getInstance();
        }
        
        showLoading(true);
        deviceTrackingManager.stopTrackingAllDevices();
        
        // Clear markers
        clearAllDeviceMarkers(true);
        
        // Restart tracking
        deviceTrackingManager.startTrackingAllDevices(devices -> {
            updateDeviceMarkers(devices);
            showLoading(false);
            
            // Update device count
            if (deviceCountText != null) {
                deviceCountText.setVisibility(View.VISIBLE);
                deviceCountText.setText(getString(R.string.devices_connected, devices.size()));
            }
        });
    }
    
    /**
     * Update the info card for a specific device
     */
    private void updateInfoCardForDevice(String deviceId) {
        // If this is the current device, use the existing logic
        if (deviceId.equals(this.deviceId) && lastLocationData != null) {
            showDeviceInfo(lastLocationData);
            return;
        }
        
        // Otherwise, get the device info from the tracking manager
        if (deviceTrackingManager != null) {
            DeviceInfo deviceInfo = deviceTrackingManager.getDeviceInfo(deviceId);
            if (deviceInfo != null) {
                infoCard.setVisibility(View.VISIBLE);
                
                deviceNameText.setText(deviceInfo.getDisplayName());
                
                // Format the timestamp as relative time string
                CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(
                        deviceInfo.getLastSeen(),
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS);
                lastUpdateText.setText(getString(R.string.last_seen, timeAgo));
                
                // Hide battery and accuracy since we don't have that info
                batteryLevelText.setVisibility(View.GONE);
                accuracyText.setVisibility(View.GONE);
            }
        }
    }

    private void showLoading(boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        
        // Stop location updates
        if (locationManager != null && locationListener != null) {
            locationManager.stopListeningForUpdates(deviceId, locationListener);
        }
        
        // Stop device tracking
        if (deviceTrackingManager != null) {
            deviceTrackingManager.stopTrackingAllDevices();
        }
    }
}