package com.example.secuphone;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
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

import com.example.secuphone.admin.AppLockManager;

public class LockScreenActivity extends AppCompatActivity {

    private static final String TAG = "LockScreenActivity";
    private String packageName;
    private EditText pinInput;
    private TextView appNameText;
    private ImageView appIconView;
    private AppLockManager appLockManager;
    
    // Счетчик неудачных попыток
    private int failedAttempts = 0;
    // Максимальное количество попыток перед временной блокировкой
    private static final int MAX_FAILED_ATTEMPTS = 5;
    // Задержка в мс после превышения количества попыток
    private static final long LOCKOUT_DELAY_MS = 30000; // 30 секунд
    // Флаг блокировки при превышении попыток
    private boolean isTemporarilyLocked = false;
    
    private Button unlockButton;
    private TextView errorMsgView;
    private Handler handler = new Handler(Looper.getMainLooper());
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            EdgeToEdge.enable(this);
            setContentView(R.layout.activity_lock_screen);
            
            Log.d(TAG, "LockScreenActivity onCreate");
            
            // Prevent screenshots and display over other apps
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, 
                    WindowManager.LayoutParams.FLAG_SECURE);
            
            // Комбинация флагов для надежного удержания поверх других окон
            getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | 
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD | 
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | 
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            );
            
            // Блокируем закрытие активити кнопкой назад
            getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    // Не позволяем закрыть экран блокировки кнопкой назад
                    // Вместо этого идем на домашний экран, не открывая приложение
                    goToHomeScreen();
                }
            });
            
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
            unlockButton = findViewById(R.id.unlock_button);
            Button cancelButton = findViewById(R.id.cancel_button);
            errorMsgView = findViewById(R.id.error_message);
            
            if (pinInput == null || appNameText == null || appIconView == null || 
                unlockButton == null || cancelButton == null) {
                Log.e(TAG, "One or more views not found in layout");
                throw new IllegalStateException("Required views not found");
            }
            
            // Устанавливаем начальное состояние сообщения об ошибке
            if (errorMsgView != null) {
                errorMsgView.setVisibility(View.GONE);
            }
            
            // Set listeners
            unlockButton.setOnClickListener(this::onUnlockClicked);
            cancelButton.setOnClickListener(v -> goToHomeScreen());
            
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
            // Проверяем временную блокировку
            if (isTemporarilyLocked) {
                showError(getString(R.string.too_many_attempts));
                return;
            }
            
            String enteredPin = pinInput.getText().toString().trim();
            
            Log.d(TAG, "Attempting to unlock with PIN of length: " + enteredPin.length());
            
            if (enteredPin.isEmpty()) {
                showError(getString(R.string.enter_pin));
                return;
            }
            
            // Validate that we have a PIN set
            if (!appLockManager.isPinSet()) {
                Log.e(TAG, "No PIN is set in preferences, can't verify");
                showError("Security error: No PIN is set");
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
                showError("Error verifying PIN");
                pinInput.setText("");
                pinInput.requestFocus();
                return;
            }
            
            if (pinValid) {
                // Сбрасываем счетчик при правильном пароле
                failedAttempts = 0;
                hideError();
                
                // Pin is correct, close this activity to allow app access
                Log.d(TAG, "PIN correct, allowing access to: " + packageName);
                Toast.makeText(this, R.string.access_granted, Toast.LENGTH_SHORT).show();
                finish();
            } else {
                // Pin is incorrect, increment counter
                failedAttempts++;
                
                // Check if we exceeded maximum attempts
                if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                    Log.d(TAG, "Maximum failed attempts reached: " + failedAttempts);
                    handleMaxAttemptsExceeded();
                } else {
                    // Show appropriate message
                    int remainingAttempts = MAX_FAILED_ATTEMPTS - failedAttempts;
                    String errorMsg = getString(R.string.incorrect_pin) + 
                            " (" + remainingAttempts + " attempts left)";
                    showError(errorMsg);
                    pinInput.setText("");
                    pinInput.requestFocus();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error verifying PIN", e);
            showError("Error: " + e.getMessage());
        }
    }
    
    /**
     * Обрабатывает ситуацию превышения максимального количества попыток
     */
    private void handleMaxAttemptsExceeded() {
        isTemporarilyLocked = true;
        unlockButton.setEnabled(false);
        
        String lockMsg = getString(R.string.too_many_attempts) + 
                " Please wait " + (LOCKOUT_DELAY_MS / 1000) + " seconds";
        showError(lockMsg);
        
        // Устанавливаем таймер на снятие блокировки
        handler.postDelayed(() -> {
            isTemporarilyLocked = false;
            failedAttempts = 0;
            unlockButton.setEnabled(true);
            hideError();
            pinInput.setText("");
            pinInput.requestFocus();
        }, LOCKOUT_DELAY_MS);
    }
    
    /**
     * Показать сообщение об ошибке
     */
    private void showError(String message) {
        if (errorMsgView != null) {
            errorMsgView.setText(message);
            errorMsgView.setVisibility(View.VISIBLE);
        } else {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Скрыть сообщение об ошибке
     */
    private void hideError() {
        if (errorMsgView != null) {
            errorMsgView.setVisibility(View.GONE);
        }
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
    
    @Override
    protected void onResume() {
        super.onResume();
        // Сбрасываем PIN-код при каждом возврате на экран блокировки
        pinInput.setText("");
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        // Если активити уходит в фон, а мы не разблокировали приложение,
        // то это может означать, что пользователь пытается обойти блокировку
        if (!isFinishing()) {
            goToHomeScreen();
        }
    }
} 