package com.example.secuphone;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.secuphone.admin.RemoteLockDeviceAdmin;
import com.example.secuphone.utils.DeviceRegistrationManager;
import com.example.secuphone.utils.RemoteLockManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Logger;
import com.google.firebase.database.ValueEventListener;

/**
 * Test activity to verify Firebase connection and remote lock functionality
 */
public class FirebaseTestActivity extends AppCompatActivity {
    private static final String TAG = "FirebaseTest";
    
    private TextView statusText;
    private Button signInButton;
    private Button registerDeviceButton;
    private Button testLockButton;
    private Button checkAdminButton;
    
    private DeviceRegistrationManager deviceRegistrationManager;
    private RemoteLockManager remoteLockManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_firebase_test);
        
        // Initialize UI
        statusText = findViewById(R.id.statusText);
        signInButton = findViewById(R.id.signInButton);
        registerDeviceButton = findViewById(R.id.registerDeviceButton);
        testLockButton = findViewById(R.id.testLockButton);
        checkAdminButton = findViewById(R.id.checkAdminButton);
        
        // Initialize managers
        deviceRegistrationManager = DeviceRegistrationManager.getInstance(this);
        remoteLockManager = RemoteLockManager.getInstance(this);
        
        // Enable verbose Firebase logging
        FirebaseDatabase.getInstance().setLogLevel(Logger.Level.DEBUG);
        
        // Set up button click listeners
        signInButton.setOnClickListener(v -> signInAnonymously());
        registerDeviceButton.setOnClickListener(v -> registerDevice());
        testLockButton.setOnClickListener(v -> testLockCommand());
        checkAdminButton.setOnClickListener(v -> checkAdminStatus());
        
        // Check Firebase connection
        checkFirebaseConnection();
        
        // Check authentication status
        checkAuthStatus();
    }
    
    /**
     * Check Firebase connection status
     */
    private void checkFirebaseConnection() {
        updateStatus("Checking Firebase connection...");
        
        DatabaseReference connectedRef = FirebaseDatabase.getInstance().getReference(".info/connected");
        connectedRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean connected = Boolean.TRUE.equals(snapshot.getValue(Boolean.class));
                if (connected) {
                    updateStatus("Firebase connection: CONNECTED");
                    Log.d(TAG, "Connected to Firebase");
                } else {
                    updateStatus("Firebase connection: DISCONNECTED");
                    Log.d(TAG, "Disconnected from Firebase");
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                updateStatus("Firebase connection check failed: " + error.getMessage());
                Log.e(TAG, "Firebase connection listener cancelled", error.toException());
            }
        });
    }
    
    /**
     * Check authentication status
     */
    private void checkAuthStatus() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            updateStatus("User is signed in: " + user.getUid());
            Log.d(TAG, "User is signed in: " + user.getUid());
            signInButton.setText("Sign Out");
            signInButton.setOnClickListener(v -> signOut());
        } else {
            updateStatus("No user is signed in");
            Log.d(TAG, "No user is signed in");
            signInButton.setText("Sign In Anonymously");
            signInButton.setOnClickListener(v -> signInAnonymously());
        }
    }
    
    /**
     * Sign in anonymously
     */
    private void signInAnonymously() {
        updateStatus("Signing in anonymously...");
        
        FirebaseAuth.getInstance().signInAnonymously()
            .addOnSuccessListener(authResult -> {
                FirebaseUser user = authResult.getUser();
                updateStatus("Anonymous sign-in successful: " + user.getUid());
                Log.d(TAG, "Anonymous sign-in successful: " + user.getUid());
                checkAuthStatus();
            })
            .addOnFailureListener(e -> {
                updateStatus("Anonymous sign-in failed: " + e.getMessage());
                Log.e(TAG, "Anonymous sign-in failed", e);
            });
    }
    
    /**
     * Sign out
     */
    private void signOut() {
        updateStatus("Signing out...");
        
        FirebaseAuth.getInstance().signOut();
        updateStatus("Signed out");
        Log.d(TAG, "Signed out");
        checkAuthStatus();
    }
    
    /**
     * Register device with Firebase
     */
    private void registerDevice() {
        updateStatus("Registering device...");
        
        deviceRegistrationManager.registerDevice(task -> {
            if (task.isSuccessful()) {
                updateStatus("Device registered successfully");
                Log.d(TAG, "Device registered successfully");
            } else {
                updateStatus("Device registration failed: " + 
                    (task.getException() != null ? task.getException().getMessage() : "Unknown error"));
                Log.e(TAG, "Device registration failed", task.getException());
            }
        });
    }
    
    /**
     * Test lock command
     */
    private void testLockCommand() {
        updateStatus("Testing lock command...");
        
        // Get device ID
        String deviceId = deviceRegistrationManager.getDeviceId();
        
        // Send lock command to this device
        remoteLockManager.sendLockCommand(deviceId, "Test lock message", task -> {
            if (task.isSuccessful()) {
                updateStatus("Lock command sent successfully");
                Log.d(TAG, "Lock command sent successfully");
            } else {
                updateStatus("Failed to send lock command: " + 
                    (task.getException() != null ? task.getException().getMessage() : "Unknown error"));
                Log.e(TAG, "Failed to send lock command", task.getException());
            }
        });
    }
    
    /**
     * Check device admin status
     */
    private void checkAdminStatus() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponent = new ComponentName(this, RemoteLockDeviceAdmin.class);
        boolean isAdmin = dpm.isAdminActive(adminComponent);
        
        updateStatus("Device admin active: " + isAdmin);
        Log.d(TAG, "Device admin active: " + isAdmin);
        
        if (!isAdmin) {
            updateStatus("Requesting device admin permission...");
            remoteLockManager.requestAdminPermission(this);
        }
    }
    
    /**
     * Update status text
     */
    private void updateStatus(String message) {
        runOnUiThread(() -> {
            statusText.append("\n" + message);
            
            // Show toast for important messages
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            
            // Scroll to bottom
            View scrollView = findViewById(R.id.scrollView);
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        });
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // Update device online status
        deviceRegistrationManager.updateOnlineStatus();
        
        // Check admin status
        checkAdminStatus();
    }
} 