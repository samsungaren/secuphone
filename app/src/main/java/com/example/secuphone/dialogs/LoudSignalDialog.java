package com.example.secuphone.dialogs;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.example.secuphone.R;
import com.example.secuphone.services.LoudSignalService;
import com.example.secuphone.utils.RemoteSignalManager;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

/**
 * Dialog for configuring and starting the loud signal
 */
public class LoudSignalDialog extends DialogFragment {
    
    private static final String ARG_DEVICE_ID = "deviceId";
    private static final String ARG_PAIRED_DEVICE_ID = "pairedDeviceId";
    
    private String deviceId;
    private String pairedDeviceId;
    
    private RadioGroup deviceRadioGroup;
    private RadioButton thisDeviceRadio;
    private RadioButton remoteDeviceRadio;
    private SeekBar durationSeekBar;
    private TextView durationText;
    private SeekBar volumeSeekBar;
    private TextView volumeText;
    private Button cancelButton;
    private Button startSignalButton;
    
    private boolean isSignalRunning = false;
    private RemoteSignalManager remoteSignalManager;
    
    // Broadcast receiver for signal updates
    private BroadcastReceiver signalReceiver;
    
    /**
     * Creates a new instance of the dialog
     */
    public static LoudSignalDialog newInstance(String deviceId, String pairedDeviceId) {
        LoudSignalDialog fragment = new LoudSignalDialog();
        Bundle args = new Bundle();
        args.putString(ARG_DEVICE_ID, deviceId);
        args.putString(ARG_PAIRED_DEVICE_ID, pairedDeviceId);
        fragment.setArguments(args);
        return fragment;
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            if (getArguments() != null) {
                deviceId = getArguments().getString(ARG_DEVICE_ID);
                pairedDeviceId = getArguments().getString(ARG_PAIRED_DEVICE_ID);
            }
            
            remoteSignalManager = RemoteSignalManager.getInstance();
            
            // Create broadcast receiver for signal updates
            signalReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    try {
                        String action = intent.getAction();
                        if ("com.example.secuphone.SIGNAL_TICK".equals(action)) {
                            int secondsLeft = intent.getIntExtra("secondsLeft", 0);
                            updateSignalProgress(secondsLeft);
                        } else if ("com.example.secuphone.SIGNAL_STOPPED".equals(action)) {
                            updateSignalStopped();
                        }
                    } catch (Exception e) {
                        // Log error but don't crash
                    }
                }
            };
            
            // Register the receiver
            IntentFilter filter = new IntentFilter();
            filter.addAction("com.example.secuphone.SIGNAL_TICK");
            filter.addAction("com.example.secuphone.SIGNAL_STOPPED");
            requireContext().registerReceiver(signalReceiver, filter);
        } catch (Exception e) {
            // Handle any initialization errors
        }
    }
    
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_loud_signal, null);
        
        // Initialize views
        deviceRadioGroup = view.findViewById(R.id.deviceRadioGroup);
        thisDeviceRadio = view.findViewById(R.id.thisDeviceRadio);
        remoteDeviceRadio = view.findViewById(R.id.remoteDeviceRadio);
        durationSeekBar = view.findViewById(R.id.durationSeekBar);
        durationText = view.findViewById(R.id.durationText);
        volumeSeekBar = view.findViewById(R.id.volumeSeekBar);
        volumeText = view.findViewById(R.id.volumeText);
        cancelButton = view.findViewById(R.id.cancelButton);
        startSignalButton = view.findViewById(R.id.startSignalButton);
        
        // Set up remote device selection
        if (pairedDeviceId == null || pairedDeviceId.isEmpty()) {
            remoteDeviceRadio.setEnabled(false);
            remoteDeviceRadio.setText(R.string.no_paired_device);
        }
        
        // Set up duration seekbar
        durationSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateDurationText(progress);
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        
        // Set up volume seekbar
        volumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateVolumeText(progress);
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        
        // Set initial values
        updateDurationText(durationSeekBar.getProgress());
        updateVolumeText(volumeSeekBar.getProgress());
        
        // Set up button actions
        cancelButton.setOnClickListener(v -> dismiss());
        
        startSignalButton.setOnClickListener(v -> {
            if (isSignalRunning) {
                stopSignal();
            } else {
                startSignal();
            }
        });
        
        // Check if signal is already running
        isSignalRunning = LoudSignalService.isSignalRunning();
        updateButtonState();
        
        builder.setView(view);
        return builder.create();
    }
    
    private void updateDurationText(int seconds) {
        durationText.setText(seconds + " seconds");
    }
    
    private void updateVolumeText(int volume) {
        volumeText.setText(volume + "%");
    }
    
    private void startSignal() {
        try {
            int duration = durationSeekBar.getProgress();
            int volume = volumeSeekBar.getProgress();
            
            // If duration is less than 5 seconds, enforce minimum
            if (duration < 5) {
                duration = 5;
                durationSeekBar.setProgress(duration);
            }
            
            boolean useThisDevice = thisDeviceRadio.isChecked();
            
            if (useThisDevice) {
                try {
                    // Start local service with better error handling
                    Intent intent = new Intent(requireContext(), LoudSignalService.class);
                    intent.putExtra(LoudSignalService.EXTRA_DURATION, duration);
                    intent.putExtra(LoudSignalService.EXTRA_VOLUME, volume);
                    
                    // Add this line to ensure the service starts correctly
                    intent.setAction("com.example.secuphone.START_SIGNAL");
                    
                    // Use different starting method based on Android version
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        requireContext().startForegroundService(intent);
                    } else {
                        requireContext().startService(intent);
                    }
                    
                    Toast.makeText(requireContext(), R.string.signal_in_progress, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(requireContext(), "Error starting signal: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    return;
                }
            } else {
                // Send command to remote device
                if (pairedDeviceId != null && !pairedDeviceId.isEmpty()) {
                    remoteSignalManager.sendSignalCommand(pairedDeviceId, duration, volume, task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(requireContext(), "Signal sent to paired device", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(requireContext(), "Failed to send signal: " + 
                                (task.getException() != null ? task.getException().getMessage() : "Unknown error"), 
                                Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    Toast.makeText(requireContext(), "No paired device available", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            
            isSignalRunning = true;
            updateButtonState();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void stopSignal() {
        boolean useThisDevice = thisDeviceRadio.isChecked();
        
        if (useThisDevice) {
            // Stop local service
            Intent intent = new Intent(requireContext(), LoudSignalService.class);
            intent.setAction(LoudSignalService.ACTION_STOP_SIGNAL);
            requireContext().startService(intent);
        } else {
            // Send stop command to remote device
            if (pairedDeviceId != null && !pairedDeviceId.isEmpty()) {
                remoteSignalManager.sendStopSignalCommand(pairedDeviceId, task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(requireContext(), "Stop signal sent to paired device", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "Failed to send stop signal: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
        
        isSignalRunning = false;
        updateButtonState();
    }
    
    private void updateButtonState() {
        if (isSignalRunning) {
            startSignalButton.setText(R.string.stop_signal);
            startSignalButton.setBackgroundTintList(requireContext().getColorStateList(R.color.colorError));
            
            // Disable controls while running
            deviceRadioGroup.setEnabled(false);
            thisDeviceRadio.setEnabled(false);
            remoteDeviceRadio.setEnabled(false);
            durationSeekBar.setEnabled(false);
            volumeSeekBar.setEnabled(false);
        } else {
            startSignalButton.setText(R.string.start_signal);
            startSignalButton.setBackgroundTintList(requireContext().getColorStateList(R.color.audio_signal_color));
            
            // Enable controls
            deviceRadioGroup.setEnabled(true);
            thisDeviceRadio.setEnabled(true);
            remoteDeviceRadio.setEnabled(pairedDeviceId != null && !pairedDeviceId.isEmpty());
            durationSeekBar.setEnabled(true);
            volumeSeekBar.setEnabled(true);
        }
    }
    
    private void updateSignalProgress(int secondsLeft) {
        if (isSignalRunning && durationText != null) {
            durationText.setText(getString(R.string.seconds_left, secondsLeft));
        }
    }
    
    private void updateSignalStopped() {
        isSignalRunning = false;
        if (getDialog() != null && getDialog().isShowing()) {
            updateButtonState();
            updateDurationText(durationSeekBar.getProgress());
        }
    }
    
    @Override
    public void onDestroy() {
        try {
            if (signalReceiver != null) {
                try {
                    requireContext().unregisterReceiver(signalReceiver);
                } catch (Exception e) {
                    // Receiver might not be registered
                }
                signalReceiver = null;
            }
        } catch (Exception e) {
            // Fail gracefully
        }
        super.onDestroy();
    }
} 