package com.example.secuphone.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.example.secuphone.R;
import com.example.secuphone.services.LoudSignalService;
import com.example.secuphone.utils.DeviceRegistrationManager;
import com.example.secuphone.utils.RemoteSignalManager;
import com.google.firebase.database.DataSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dialog for selecting a device to signal
 */
public class SignalDeviceSelectionDialog extends DialogFragment {
    private static final String TAG = "SignalDeviceDialog";
    
    private DeviceRegistrationManager deviceRegistrationManager;
    private RemoteSignalManager remoteSignalManager;
    
    private ListView deviceListView;
    private SeekBar durationSeekBar;
    private TextView durationText;
    private SeekBar volumeSeekBar;
    private TextView volumeText;
    
    private List<String> deviceNames = new ArrayList<>();
    private List<String> deviceIds = new ArrayList<>();
    private List<Boolean> deviceOnlineStatus = new ArrayList<>();
    
    private String selectedDeviceId;
    private String selectedDeviceName;
    
    public static SignalDeviceSelectionDialog newInstance() {
        return new SignalDeviceSelectionDialog();
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        deviceRegistrationManager = DeviceRegistrationManager.getInstance(requireContext());
        remoteSignalManager = RemoteSignalManager.getInstance();
    }
    
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_device_selection, null);
        
        // Initialize views
        deviceListView = view.findViewById(R.id.deviceListView);
        durationSeekBar = view.findViewById(R.id.durationSeekBar);
        durationText = view.findViewById(R.id.durationText);
        volumeSeekBar = view.findViewById(R.id.volumeSeekBar);
        volumeText = view.findViewById(R.id.volumeText);
        
        // Set up seekbar listeners
        durationSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateDurationText(progress);
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        volumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateVolumeText(progress);
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // Set initial values
        updateDurationText(durationSeekBar.getProgress());
        updateVolumeText(volumeSeekBar.getProgress());
        
        // Load devices
        loadDevices();
        
        // Set up click listener for device list
        deviceListView.setOnItemClickListener((parent, view1, position, id) -> {
            selectedDeviceId = deviceIds.get(position);
            selectedDeviceName = deviceNames.get(position);
            
            // Show confirmation dialog
            showSignalConfirmationDialog();
        });
        
        builder.setView(view)
               .setTitle(R.string.select_device_to_signal)
               .setNegativeButton(R.string.cancel, (dialog, id) -> dialog.dismiss());
        
        return builder.create();
    }
    
    private void updateDurationText(int seconds) {
        durationText.setText(seconds + " seconds");
    }
    
    private void updateVolumeText(int volume) {
        volumeText.setText(volume + "%");
    }
    
    private void loadDevices() {
        // Show progress dialog while fetching devices
        AlertDialog progressDialog = new AlertDialog.Builder(requireContext())
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
                Toast.makeText(requireContext(), R.string.failed_to_load_devices, Toast.LENGTH_SHORT).show();
                dismiss();
                return;
            }
            
            // Get devices from snapshot
            DataSnapshot devicesSnapshot = task.getResult();
            if (!devicesSnapshot.exists() || !devicesSnapshot.hasChildren()) {
                // No devices found
                Toast.makeText(requireContext(), R.string.no_devices_found, Toast.LENGTH_SHORT).show();
                dismiss();
                return;
            }
            
            // Clear previous data
            deviceNames.clear();
            deviceIds.clear();
            deviceOnlineStatus.clear();
            
            // Current device ID
            String currentDeviceId = deviceRegistrationManager.getDeviceId();
            
            // Add current device first
            deviceIds.add(currentDeviceId);
            deviceNames.add("This device");
            deviceOnlineStatus.add(true);
            
            // Populate device lists with other devices
            for (DataSnapshot deviceSnapshot : devicesSnapshot.getChildren()) {
                String deviceId = deviceSnapshot.getKey();
                
                // Skip current device as we already added it
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
            }
            
            // Create adapter for device list
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(requireContext(), 
                android.R.layout.simple_list_item_1, deviceNames) {
                @Override
                public View getView(int position, View convertView, android.view.ViewGroup parent) {
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
            
            // Set adapter to list view
            deviceListView.setAdapter(adapter);
        });
    }
    
    private void showSignalConfirmationDialog() {
        int duration = durationSeekBar.getProgress();
        int volume = volumeSeekBar.getProgress();
        
        // Ensure minimum duration
        if (duration < 5) {
            duration = 5;
        }
        
        final int finalDuration = duration;
        final int finalVolume = volume;
        
        // Get current device ID
        String currentDeviceId = deviceRegistrationManager.getDeviceId();
        
        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.signal_device)
            .setMessage(getString(R.string.signal_device_description) + "\n\n" + 
                        getString(R.string.selected_device, selectedDeviceName) + "\n" +
                        getString(R.string.signal_duration) + ": " + duration + " seconds\n" +
                        getString(R.string.signal_volume) + ": " + volume + "%")
            .setPositiveButton(R.string.signal_device, (dialog, which) -> {
                // Show progress dialog
                AlertDialog progressDialog = new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.sending_signal_command)
                    .setMessage(R.string.please_wait)
                    .setCancelable(false)
                    .show();
                
                if (selectedDeviceId.equals(currentDeviceId)) {
                    // Signal this device
                    try {
                        // Start local service
                        Intent intent = new Intent(requireContext(), LoudSignalService.class);
                        intent.putExtra(LoudSignalService.EXTRA_DURATION, finalDuration);
                        intent.putExtra(LoudSignalService.EXTRA_VOLUME, finalVolume);
                        
                        // Add action to ensure the service starts correctly
                        intent.setAction("com.example.secuphone.START_SIGNAL");
                        
                        // Use different starting method based on Android version
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            requireContext().startForegroundService(intent);
                        } else {
                            requireContext().startService(intent);
                        }
                        
                        progressDialog.dismiss();
                        Toast.makeText(requireContext(), R.string.signal_in_progress, Toast.LENGTH_SHORT).show();
                        dismiss();
                    } catch (Exception e) {
                        progressDialog.dismiss();
                        Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    // Send command to remote device
                    remoteSignalManager.sendSignalCommand(selectedDeviceId, finalDuration, finalVolume, task -> {
                        progressDialog.dismiss();
                        
                        if (task.isSuccessful()) {
                            Toast.makeText(requireContext(), R.string.signal_sent_success, Toast.LENGTH_SHORT).show();
                        } else {
                            String errorMsg = task.getException() != null ? 
                                task.getException().getMessage() : getString(R.string.unknown_error);
                            Toast.makeText(requireContext(), getString(R.string.signal_failed) + ": " + errorMsg, Toast.LENGTH_SHORT).show();
                        }
                        
                        dismiss();
                    });
                }
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }
} 