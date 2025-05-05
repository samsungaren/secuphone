package com.example.secuphone;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.secuphone.utils.AppLockPreferences;

public class LockScreenActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "LockScreenActivity";
    private String packageName;
    private EditText pinInput;
    private AppLockPreferences appLockPreferences;
    
    // Counter for failed attempts
    private int failedAttempts = 0;
    // Maximum failed attempts before timeout
    private static final int MAX_FAILED_ATTEMPTS = 5;
    // Lockout delay in ms after exceeding attempts
    private static final long LOCKOUT_DELAY_MS = 30000; // 30 seconds
    // Flag for temporary lockout
    private boolean isTemporarilyLocked = false;
    
    private Button unlockButton;
    private Button cancelButton;
    private TextView errorMsgView;
    private Handler handler = new Handler(Looper.getMainLooper());
    
    // PIN dots views
    private View[] pinDots;
    
    // Numeric keypad buttons
    private Button[] numberButtons;
    private Button btnClear;
    private Button btnDelete;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            EdgeToEdge.enable(this);
            
            // Set window flags first thing
            setupWindowFlags();
            
            setContentView(R.layout.activity_lock_screen);
            
            Log.d(TAG, "LockScreenActivity onCreate started");
            
            // Block closing with back button
            getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    // Don't allow closing lock screen with back button
                    // Instead go to home screen without opening the app
                    goToHomeScreen();
                }
            });
            
            // Handle window insets
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.lock_screen_layout), (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
            
            // Initialize preferences
            appLockPreferences = new AppLockPreferences(this);
            
            // Get the package name of the app we're protecting
            packageName = getIntent().getStringExtra("package_name");
            Log.d(TAG, "LockScreenActivity received package name: " + packageName);
            
            if (packageName == null) {
                Log.e(TAG, "No package name provided");
                Toast.makeText(this, "Error: No app specified", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            
            // If the app isn't actually locked, just finish
            if (!appLockPreferences.isAppLocked(packageName)) {
                Log.d(TAG, "App is not locked, finishing: " + packageName);
                finish();
                return;
            }
            
            // Initialize UI elements
            initializeViews();
            
            // Request focus for the PIN input
            if (pinInput != null) {
                pinInput.requestFocus();
            }
            
            Log.d(TAG, "LockScreenActivity onCreate completed");
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
            Toast.makeText(this, "Error initializing lock screen", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    
    private void setupWindowFlags() {
        try {
            // Prevent screenshots for security
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, 
                    WindowManager.LayoutParams.FLAG_SECURE);
            
            // Make the activity stay on top
            getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | 
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD | 
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | 
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
            );
            
            // Disable animations
            overridePendingTransition(0, 0);
        } catch (Exception e) {
            Log.e(TAG, "Error setting window flags", e);
        }
    }
    
    private void initializeViews() {
        try {
            // Main views
            pinInput = findViewById(R.id.pin_input);
            unlockButton = findViewById(R.id.unlock_button);
            cancelButton = findViewById(R.id.cancel_button);
            errorMsgView = findViewById(R.id.error_message);
            
            // Check that critical views exist
            if (pinInput == null) {
                Log.e(TAG, "PIN input field not found");
                Toast.makeText(this, "Error initializing lock screen", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            
            if (errorMsgView == null) {
                Log.e(TAG, "Error message view not found");
                // We can continue without the error message view
            }
            
            // Initialize PIN dots
            initializePinDots();
            
            // Initialize numeric keypad
            initializeNumericKeypad();

            // Set up PIN input with hidden field
            pinInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    try {
                        updatePinDots(s.length());
                        
                        // Auto-submit when the pin is 6 or more digits
                        if (s.length() >= 6) {
                            onUnlockClicked();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in PIN text change", e);
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });

            // Set button listeners if they're available
            if (unlockButton != null) {
                unlockButton.setOnClickListener(this);
            }
            
            if (cancelButton != null) {
                cancelButton.setOnClickListener(this);
            }
            
            // Force focus to the pinInput to ensure key events are captured
            pinInput.requestFocus();
            
            // Hide keyboard for the PIN input since we're using custom keypad
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(pinInput.getWindowToken(), 0);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error initializing views", e);
            Toast.makeText(this, "Error initializing lock screen", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    
    private void initializePinDots() {
        pinDots = new View[6];
        pinDots[0] = findViewById(R.id.pin_dot_1);
        pinDots[1] = findViewById(R.id.pin_dot_2);
        pinDots[2] = findViewById(R.id.pin_dot_3);
        pinDots[3] = findViewById(R.id.pin_dot_4);
        pinDots[4] = findViewById(R.id.pin_dot_5);
        pinDots[5] = findViewById(R.id.pin_dot_6);
    }
    
    private void initializeNumericKeypad() {
        try {
            numberButtons = new Button[10];
            
            // Initialize number buttons 0-9
            numberButtons[0] = findViewById(R.id.btn_0);
            numberButtons[1] = findViewById(R.id.btn_1);
            numberButtons[2] = findViewById(R.id.btn_2);
            numberButtons[3] = findViewById(R.id.btn_3);
            numberButtons[4] = findViewById(R.id.btn_4);
            numberButtons[5] = findViewById(R.id.btn_5);
            numberButtons[6] = findViewById(R.id.btn_6);
            numberButtons[7] = findViewById(R.id.btn_7);
            numberButtons[8] = findViewById(R.id.btn_8);
            numberButtons[9] = findViewById(R.id.btn_9);
            
            // Set click listeners for all number buttons
            for (int i = 0; i < numberButtons.length; i++) {
                Button btn = numberButtons[i];
                if (btn != null) {
                    btn.setOnClickListener(this);
                } else {
                    Log.e(TAG, "Button " + i + " is null");
                }
            }
            
            // Special buttons
            btnClear = findViewById(R.id.btn_clear);
            btnDelete = findViewById(R.id.btn_delete);
            
            if (btnClear != null) {
                btnClear.setOnClickListener(this);
            } else {
                Log.e(TAG, "Clear button is null");
            }
            
            if (btnDelete != null) {
                btnDelete.setOnClickListener(this);
            } else {
                Log.e(TAG, "Delete button is null");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing numeric keypad", e);
        }
    }
    
    private void updatePinDots(int pinLength) {
        // Update the PIN dots to reflect the current PIN length
        for (int i = 0; i < pinDots.length; i++) {
            if (pinDots[i] != null) {
                if (i < pinLength) {
                    pinDots[i].setBackground(ContextCompat.getDrawable(this, R.drawable.pin_dot_filled));
                } else {
                    pinDots[i].setBackground(ContextCompat.getDrawable(this, R.drawable.pin_dot_empty));
                }
            }
        }
    }
    
    private void onUnlockClicked() {
        String enteredPin = pinInput.getText().toString().trim();
        if (enteredPin.isEmpty()) {
            showError("Enter PIN");
            return;
        }

        if (enteredPin.length() < 6) {
            showError("PIN must be at least 6 digits");
            return;
        }

        if (appLockPreferences.verifyPin(enteredPin)) {
            // Clear error message and proceed
            hideError();
            unlockApp();
        } else {
            handleFailedAttempt();
        }
    }
    
    private void handleFailedAttempt() {
        failedAttempts++;
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            handleMaxAttemptsExceeded();
        } else {
            showError("Incorrect PIN");
            pinInput.setText("");  // Clear PIN input
        }
    }
    
    /**
     * Handle exceeding max attempt limit
     */
    private void handleMaxAttemptsExceeded() {
        isTemporarilyLocked = true;
        unlockButton.setEnabled(false);
        
        String lockMsg = "Too many failed attempts. Please wait " + (LOCKOUT_DELAY_MS / 1000) + " seconds";
        showError(lockMsg);
        
        // Set timer to remove lockout
        handler.postDelayed(() -> {
            isTemporarilyLocked = false;
            failedAttempts = 0;
            unlockButton.setEnabled(true);
            hideError();
            pinInput.setText("");  // Clear PIN input
        }, LOCKOUT_DELAY_MS);
    }
    
    /**
     * Show error message
     */
    private void showError(String message) {
        if (errorMsgView != null) {
            errorMsgView.setText(message);
            errorMsgView.setVisibility(View.VISIBLE);
        }
    }
    
    /**
     * Hide error message
     */
    private void hideError() {
        if (errorMsgView != null) {
            errorMsgView.setVisibility(View.GONE);
        }
    }
    
    /**
     * Go to home screen
     */
    private void goToHomeScreen() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        try {
            // Make sure this activity stays on top
            setupWindowFlags();
            
            // Reset PIN input when activity resumes
            if (pinInput != null) {
                pinInput.setText("");
                pinInput.requestFocus();
            }
            updatePinDots(0);
            
            // Verify the app is still locked (it might have been unlocked by another instance)
            if (packageName != null && !appLockPreferences.isAppLocked(packageName)) {
                Log.d(TAG, "App is no longer locked, finishing lock screen");
                finish();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onResume", e);
        }
    }
    
    @Override 
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            try {
                // Apply immersive mode when we have focus
                getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
                
                // Ensure we have focus on the PIN input
                if (pinInput != null) {
                    pinInput.requestFocus();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in onWindowFocusChanged", e);
            }
        }
    }
    
    /**
     * Unlock app and finish activity
     */
    private void unlockApp() {
        Log.d(TAG, "Unlocking app: " + packageName);
        
        try {
            // Set a flag in preferences to temporarily allow access to this app
            if (packageName != null) {
                // Store the temporarily unlocked app in preferences with timestamp
                long unlockTime = System.currentTimeMillis();
                appLockPreferences.setTemporaryUnlock(packageName, unlockTime);
                
                // Launch the app that was locked
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
                if (launchIntent != null) {
                    Log.d(TAG, "Launching app: " + packageName);
                    
                    // Add flags to ensure app launches cleanly
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    
                    // Make sure app returns to its main activity
                    launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                    
                    // Start the app
                    startActivity(launchIntent);
                    
                    Log.d(TAG, "App launched: " + packageName);
                } else {
                    Log.e(TAG, "No launch intent found for: " + packageName);
                    goToHomeScreen();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error launching app: " + e.getMessage());
            goToHomeScreen();
        } finally {
            // Always finish the lock screen
            finish();
        }
    }
    
    @Override
    public void finish() {
        try {
            super.finish();
            // Remove exit animation
            overridePendingTransition(0, 0);
        } catch (Exception e) {
            Log.e(TAG, "Error in finish", e);
        }
    }
    
    /**
     * Handle cancel button click
     */
    private void onCancelClicked() {
        Log.d(TAG, "User canceled unlock, going to home screen");
        goToHomeScreen();
        finish();
    }
    
    /**
     * Handle button clicks for numeric keypad
     */
    @Override
    public void onClick(View v) {
        // Skip if temporarily locked out
        if (isTemporarilyLocked) {
            return;
        }
        
        int id = v.getId();
        
        // Handle numeric buttons
        if (id == R.id.btn_0) appendToPin("0");
        else if (id == R.id.btn_1) appendToPin("1");
        else if (id == R.id.btn_2) appendToPin("2");
        else if (id == R.id.btn_3) appendToPin("3");
        else if (id == R.id.btn_4) appendToPin("4");
        else if (id == R.id.btn_5) appendToPin("5");
        else if (id == R.id.btn_6) appendToPin("6");
        else if (id == R.id.btn_7) appendToPin("7");
        else if (id == R.id.btn_8) appendToPin("8");
        else if (id == R.id.btn_9) appendToPin("9");
        // Handle special buttons
        else if (id == R.id.btn_clear) clearPin();
        else if (id == R.id.btn_delete) deleteLastDigit();
        // Handle action buttons
        else if (id == R.id.unlock_button) onUnlockClicked();
        else if (id == R.id.cancel_button) onCancelClicked();
    }
    
    /**
     * Append a digit to the PIN
     */
    private void appendToPin(String digit) {
        if (pinInput.length() < 6) { // Limit to 6 digits
            pinInput.append(digit);
        }
    }
    
    /**
     * Clear the PIN completely
     */
    private void clearPin() {
        pinInput.setText("");
        updatePinDots(0);
    }
    
    /**
     * Delete the last digit of the PIN
     */
    private void deleteLastDigit() {
        String currentPin = pinInput.getText().toString();
        if (!currentPin.isEmpty()) {
            pinInput.setText(currentPin.substring(0, currentPin.length() - 1));
        }
    }
} 