package com.example.secuphone;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.secuphone.utils.AppLockPreferences;

public class PinSetupActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "PinSetupActivity";
    
    private EditText pinInput;
    private View[] pinDots;
    private Button[] numberButtons;
    private Button btnDelete;
    private Button nextButton;
    private TextView errorMessageView;
    private TextView pinInstructionText;
    
    private AppLockPreferences appLockPreferences;
    
    private String firstPin = "";
    private boolean isConfirmationMode = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin_setup);
        
        appLockPreferences = new AppLockPreferences(this);
        
        initializeViews();
        setupListeners();
    }
    
    private void initializeViews() {
        // Initialize UI elements
        pinInput = findViewById(R.id.pin_input);
        errorMessageView = findViewById(R.id.error_message);
        pinInstructionText = findViewById(R.id.pin_instruction_text);
        nextButton = findViewById(R.id.next_button);
        
        // Back button navigation
        ImageButton backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> finish());
        
        // Initialize PIN dots
        initializePinDots();
        
        // Initialize numeric keypad
        initializeNumericKeypad();
        
        // Configure pin input listener
        pinInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePinDots(s.length());
                nextButton.setEnabled(s.length() >= 4);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
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
        for (Button btn : numberButtons) {
            if (btn != null) {
                btn.setOnClickListener(this);
            }
        }
        
        // Delete button
        btnDelete = findViewById(R.id.btn_delete);
        
        if (btnDelete != null) {
            btnDelete.setOnClickListener(this);
        }
    }
    
    private void setupListeners() {
        nextButton.setOnClickListener(v -> onNextClicked());
        nextButton.setEnabled(false);
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
    
    private void onNextClicked() {
        String pin = pinInput.getText().toString().trim();
        
        if (pin.length() < 4) {
            showError(getString(R.string.pin_too_short));
            return;
        }
        
        hideError();
        
        if (!isConfirmationMode) {
            // First PIN entry - store and switch to confirmation mode
            firstPin = pin;
            isConfirmationMode = true;
            
            // Clear the input and update UI for confirmation
            pinInput.setText("");
            updatePinDots(0);
            pinInstructionText.setText(R.string.confirm_pin);
            nextButton.setEnabled(false);
        } else {
            // PIN confirmation - check if PINs match
            if (pin.equals(firstPin)) {
                // PINs match - save and finish
                boolean success = appLockPreferences.setPin(pin);
                if (success) {
                    Toast.makeText(this, R.string.pin_set_success, Toast.LENGTH_SHORT).show();
                    
                    // Set result to indicate success
                    setResult(RESULT_OK);
                    
                    // Slight delay before finishing to show toast
                    new Handler(Looper.getMainLooper()).postDelayed(this::finish, 1000);
                } else {
                    showError("Failed to save PIN. Please try again.");
                    resetPinSetup();
                }
            } else {
                // PINs don't match - show error and reset
                showError(getString(R.string.pins_dont_match));
                new Handler(Looper.getMainLooper()).postDelayed(this::resetPinSetup, 1000);
            }
        }
    }
    
    private void resetPinSetup() {
        isConfirmationMode = false;
        firstPin = "";
        pinInput.setText("");
        updatePinDots(0);
        pinInstructionText.setText(R.string.enter_pin);
        nextButton.setEnabled(false);
        hideError();
    }
    
    private void showError(String message) {
        errorMessageView.setText(message);
        errorMessageView.setVisibility(View.VISIBLE);
    }
    
    private void hideError() {
        errorMessageView.setVisibility(View.GONE);
    }
    
    @Override
    public void onClick(View v) {
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
        // Handle delete button
        else if (id == R.id.btn_delete) deleteLastDigit();
    }
    
    private void appendToPin(String digit) {
        if (pinInput.length() < 6) { // Limit to 6 digits
            pinInput.append(digit);
        }
    }
    
    private void deleteLastDigit() {
        String currentPin = pinInput.getText().toString();
        if (!currentPin.isEmpty()) {
            pinInput.setText(currentPin.substring(0, currentPin.length() - 1));
        }
    }
} 