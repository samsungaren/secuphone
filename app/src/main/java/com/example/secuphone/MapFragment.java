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

import com.example.secuphone.models.LocationData;
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
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class MapFragment extends Fragment implements OnMapReadyCallback {
    private static final String TAG = "MapFragment";
    private static final String ARG_DEVICE_ID = "deviceId";

    // Google Maps components
    private GoogleMap googleMap;
    private Marker deviceMarker;

    // UI components
    private TextView deviceNameText;
    private TextView lastUpdateText;
    private TextView batteryLevelText;
    private TextView accuracyText;
    private Button refreshButton;
    private ProgressBar progressBar;
    private CardView infoCard;

    // Data
    private String deviceId;
    private FirebaseLocationManager locationManager;
    private ValueEventListener locationListener;
    private LocationData lastLocationData;

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

        // Initially hide the info card until we have data
        infoCard.setVisibility(View.GONE);
        
        // Set up map
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Set up refresh button
        refreshButton.setOnClickListener(v -> refreshLocation());
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
                Log.e(TAG, "Failed to read location updates: " + error.getMessage());
                showLoading(false);
                Toast.makeText(getContext(), "Failed to load location data", Toast.LENGTH_SHORT).show();
            }
        };
        
        locationManager.listenForLocationUpdates(deviceId, locationListener);
    }

    /**
     * Load the last known location for the device
     */
    private void loadLastKnownLocation() {
        showLoading(true);
        
        locationManager.getLastKnownLocation(deviceId, task -> {
            if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                Map<String, Object> locationMap = (HashMap<String, Object>) task.getResult().getValue();
                if (locationMap != null) {
                    updateLocationFromMap(locationMap);
                } else {
                    showCurrentDeviceLocation();
                }
            } else {
                showCurrentDeviceLocation();
            }
        });
    }

    /**
     * Fallback to show current device location if no Firebase data
     */
    private void showCurrentDeviceLocation() {
        if (getContext() == null || googleMap == null) {
            showLoading(false);
            Toast.makeText(getContext(), "No location data available", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            showLoading(true);
            
            if (ContextCompat.checkSelfPermission(requireContext(), 
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                
                FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
                fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                    if (location != null) {
                        Log.d(TAG, "Got current location: " + location.getLatitude() + ", " + location.getLongitude());
                        
                        // Create a LocationData object from the current location
                        LocationData currentLocation = new LocationData(
                                location.getLatitude(),
                                location.getLongitude(),
                                System.currentTimeMillis(),
                                deviceId,
                                location.getAccuracy(),
                                location.getAltitude(),
                                location.getSpeed(),
                                DeviceUtils.getBatteryLevel(requireContext())
                        );
                        
                        // Update the map with this location
                        updateMapMarker(currentLocation);
                        
                        // Update UI elements
                        String deviceName = DeviceUtils.getDeviceName(requireContext());
                        deviceNameText.setText(deviceName != null ? deviceName : "My Device");
                        lastUpdateText.setText("Last update: Just now");
                        accuracyText.setText(String.format("Accuracy: %.1f m", location.getAccuracy()));
                        batteryLevelText.setText("Battery: " + DeviceUtils.getBatteryLevel(requireContext()));
                        
                        // Show the info card
                        infoCard.setVisibility(View.VISIBLE);
                        
                        // Save this location to Firebase
                        FirebaseLocationManager.getInstance().updateLocationData(deviceId, currentLocation, task -> {
                            if (task.isSuccessful()) {
                                Log.d(TAG, "Current location saved to Firebase");
                            } else {
                                Log.e(TAG, "Failed to save location to Firebase", task.getException());
                            }
                        });
                    } else {
                        Log.d(TAG, "Location is null");
                        Toast.makeText(getContext(), "No current location available", Toast.LENGTH_SHORT).show();
                    }
                    showLoading(false);
                }).addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting current location", e);
                    Toast.makeText(getContext(), "Failed to get current location", Toast.LENGTH_SHORT).show();
                    showLoading(false);
                });
            } else {
                Log.d(TAG, "Location permission not granted");
                showLoading(false);
                Toast.makeText(getContext(), "Location permission required", Toast.LENGTH_SHORT).show();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception when getting location", e);
            showLoading(false);
            Toast.makeText(getContext(), "Failed to get location data", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Update the UI with location data from Firebase
     */
    @SuppressLint("DefaultLocale")
    private void updateLocationFromMap(Map<String, Object> locationMap) {
        try {
            Object latObj = locationMap.get("latitude");
            Object lngObj = locationMap.get("longitude");
            
            if (latObj == null || lngObj == null) {
                return;
            }
            
            double latitude = 0;
            double longitude = 0;
            
            // Handle different number formats from Firebase
            if (latObj instanceof Double) {
                latitude = (Double) latObj;
            } else if (latObj instanceof Long) {
                latitude = ((Long) latObj).doubleValue();
            } else if (latObj instanceof String) {
                latitude = Double.parseDouble((String) latObj);
            }
            
            if (lngObj instanceof Double) {
                longitude = (Double) lngObj;
            } else if (lngObj instanceof Long) {
                longitude = ((Long) lngObj).doubleValue();
            } else if (lngObj instanceof String) {
                longitude = Double.parseDouble((String) lngObj);
            }
            
            // Create LocationData object from map
            LocationData locationData = new LocationData();
            locationData.setLatitude(latitude);
            locationData.setLongitude(longitude);
            
            Object timestampObj = locationMap.get("timestamp");
            if (timestampObj instanceof Long) {
                locationData.setTimestamp((Long) timestampObj);
            }
            
            Object deviceIdObj = locationMap.get("deviceId");
            if (deviceIdObj instanceof String) {
                locationData.setDeviceId((String) deviceIdObj);
                deviceNameText.setText((String) deviceIdObj);
            }
            
            Object accuracyObj = locationMap.get("accuracy");
            if (accuracyObj instanceof Double) {
                locationData.setAccuracy((Double) accuracyObj);
                accuracyText.setText(String.format("Accuracy: %.1f m", (Double) accuracyObj));
            } else if (accuracyObj instanceof Long) {
                Double accuracy = ((Long) accuracyObj).doubleValue();
                locationData.setAccuracy(accuracy);
                accuracyText.setText(String.format("Accuracy: %.1f m", accuracy));
            }
            
            Object batteryLevelObj = locationMap.get("batteryLevel");
            if (batteryLevelObj instanceof String) {
                locationData.setBatteryLevel((String) batteryLevelObj);
                batteryLevelText.setText("Battery: " + (String) batteryLevelObj);
            }
            
            // Update the map marker
            updateMapMarker(locationData);
            
            // Update timestamp
            if (locationData.getTimestamp() > 0) {
                String timeAgo = DateUtils.getRelativeTimeSpanString(
                        locationData.getTimestamp(),
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE
                ).toString();
                
                lastUpdateText.setText("Last update: " + timeAgo);
            }
            
            // Show the info card now that we have data
            infoCard.setVisibility(View.VISIBLE);
            
            // Store for later use
            lastLocationData = locationData;
            
            showLoading(false);
            
        } catch (Exception e) {
            Log.e(TAG, "Error updating location from map", e);
            showLoading(false);
        }
    }

    /**
     * Update the map marker with the device location
     */
    private void updateMapMarker(LocationData locationData) {
        if (googleMap == null) {
            return;
        }
        
        LatLng position = new LatLng(locationData.getLatitude(), locationData.getLongitude());
        
        // If marker exists, just update its position
        if (deviceMarker != null) {
            deviceMarker.setPosition(position);
        } else {
            // Create a new marker
            MarkerOptions markerOptions = new MarkerOptions()
                    .position(position)
                    .title("Device Location")
                    .snippet("Last seen: " + DateUtils.getRelativeTimeSpanString(
                            locationData.getTimestamp(),
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS,
                            DateUtils.FORMAT_ABBREV_RELATIVE
                    ))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
            
            deviceMarker = googleMap.addMarker(markerOptions);
        }
        
        // Animate camera to the device location
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 15f));
    }

    /**
     * Force refresh the device location
     */
    private void refreshLocation() {
        loadLastKnownLocation();
    }

    /**
     * Public method to refresh the map from outside the fragment
     */
    public void refreshMap() {
        if (googleMap != null) {
            try {
                if (ContextCompat.checkSelfPermission(requireContext(), 
                        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    
                    // Get current location to center map
                    FusedLocationProviderClient fusedLocationClient = 
                            LocationServices.getFusedLocationProviderClient(requireActivity());
                    fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                        if (location != null) {
                            LatLng currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15f));
                            
                            // Update the UI with current location data
                            showCurrentDeviceLocation();
                        } else {
                            // Fallback to Firebase data
                            loadLastKnownLocation();
                        }
                    }).addOnFailureListener(e -> {
                        // Fallback to Firebase data
                        loadLastKnownLocation();
                    });
                } else {
                    loadLastKnownLocation();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error refreshing map", e);
                loadLastKnownLocation();
            }
        }
    }

    /**
     * Show/hide loading indicator
     */
    private void showLoading(boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        
        // Clean up Firebase listener
        if (locationListener != null && deviceId != null) {
            locationManager.stopListeningForUpdates(deviceId, locationListener);
            locationListener = null;
        }
    }
} 