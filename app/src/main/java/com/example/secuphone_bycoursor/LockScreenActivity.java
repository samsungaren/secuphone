package com.example.secuphone_bycoursor;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.secuphone_bycoursor.admin.AppLockManager;

public class LockScreenActivity extends AppCompatActivity {

    private static final String TAG = "LockScreenActivity";
    private String packageName;
    private EditText pinInput;
    private TextView appNameText;
    private ImageView appIconView;
    private AppLockManager appLockManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            EdgeToEdge.enable(this);
            setContentView(R.layout.activity_lock_screen);
            
            Log.d(TAG, "LockScreenActivity onCreate");
            
            // Prevent screenshots
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, 
                    WindowManager.LayoutParams.FLAG_SECURE);
            
            // Keep screen on top
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
            
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.lock_screen_layout), (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
            
            // Initialize app lock manager
            appLockManager = new AppLockManager(this);
            
            // Get the package name of the app we're protecting
            packageName = getIntent().getStringExtra("package_name");
            Log.d(TAG, "Received package name: " + packageName);
            
            if (packageName == null) {
                Log.e(TAG, "No package name provided");
                Toast.makeText(this, "Error: No app specified", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            
            // Initialize UI elements
            initializeViews();
            
            // Set app info
            setAppInfo(packageName);
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
            Toast.makeText(this, "Error initializing lock screen", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    
    private void initializeViews() {
        try {
            pinInput = findViewById(R.id.pin_input);
            appNameText = findViewById(R.id.app_name_text);
            appIconView = findViewById(R.id.app_icon);
            Button unlockButton = findViewById(R.id.unlock_button);
            Button cancelButton = findViewById(R.id.cancel_button);
            
            if (pinInput == null || appNameText == null || appIconView == null || 
                unlockButton == null || cancelButton == null) {
                Log.e(TAG, "One or more views not found in layout");
                throw new IllegalStateException("Required views not found");
            }
            
            // Set listeners
            unlockButton.setOnClickListener(this::onUnlockClicked);
            cancelButton.setOnClickListener(v -> finish());
            
            // Set focus on PIN input
            pinInput.requestFocus();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing views", e);
            throw e; // Rethrow to be caught by the calling method
        }
    }
    
    private void setAppInfo(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            
            // Set app name
            String appName = pm.getApplicationLabel(appInfo).toString();
            appNameText.setText(appName);
            
            // Set app icon
            Drawable icon = pm.getApplicationIcon(appInfo);
            appIconView.setImageDrawable(icon);
            
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Error getting app info: " + e.getMessage());
            // If we can't find the app info, just use a generic name/icon
            appNameText.setText(R.string.locked_app);
            appIconView.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_lock_closed));
        }
    }
    
    private void onUnlockClicked(View view) {
        try {
            String enteredPin = pinInput.getText().toString().trim();
            
            Log.d(TAG, "Attempting to unlock with PIN of length: " + enteredPin.length());
            
            if (enteredPin.isEmpty()) {
                Toast.makeText(this, R.string.enter_pin, Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Validate that we have a PIN set
            if (!appLockManager.isPinSet()) {
                Log.e(TAG, "No PIN is set in preferences, can't verify");
                Toast.makeText(this, "Security error: No PIN is set", Toast.LENGTH_SHORT).show();
                // Exit to home screen for security
                goToHomeScreen();
                return;
            }
            
            // Verify PIN using AppLockManager
            boolean pinValid = false;
            try {
                pinValid = appLockManager.verifyPin(enteredPin);
                Log.d(TAG, "PIN verification result: " + pinValid);
            } catch (Exception e) {
                Log.e(TAG, "Exception during PIN verification", e);
                Toast.makeText(this, "Error verifying PIN", Toast.LENGTH_SHORT).show();
                pinInput.setText("");
                pinInput.requestFocus();
                return;
            }
            
            if (pinValid) {
                // Pin is correct, close this activity to allow app access
                Log.d(TAG, "PIN correct, allowing access to: " + packageName);
                Toast.makeText(this, R.string.access_granted, Toast.LENGTH_SHORT).show();
                finish();
            } else {
                // Pin is incorrect, show error
                Log.d(TAG, "PIN incorrect, access denied");
                Toast.makeText(this, R.string.incorrect_pin, Toast.LENGTH_SHORT).show();
                pinInput.setText("");
                pinInput.requestFocus();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error verifying PIN", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    public void onBackPressed() {
        // Return to home screen instead of allowing app access
        goToHomeScreen();
    }

    private void goToHomeScreen() {
        try {
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(homeIntent);
            finish();
        } catch (Exception e) {
            Log.e(TAG, "Error going to home screen", e);
            finish(); // Just finish the activity if we can't launch home
        }
    }
} 