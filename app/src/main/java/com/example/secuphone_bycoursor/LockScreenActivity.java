package com.example.secuphone_bycoursor;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
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

import com.example.secuphone_bycoursor.services.AppLockService;

public class LockScreenActivity extends AppCompatActivity {

    private String packageName;
    private EditText pinInput;
    private TextView appNameText;
    private ImageView appIconView;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_lock_screen);
        
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
        
        // Get the package name of the app we're protecting
        packageName = getIntent().getStringExtra("package_name");
        if (packageName == null) {
            finish();
            return;
        }
        
        // Initialize UI elements
        pinInput = findViewById(R.id.pin_input);
        appNameText = findViewById(R.id.app_name_text);
        appIconView = findViewById(R.id.app_icon);
        Button unlockButton = findViewById(R.id.unlock_button);
        Button cancelButton = findViewById(R.id.cancel_button);
        
        // Set app info
        setAppInfo(packageName);
        
        // Set listeners
        unlockButton.setOnClickListener(this::onUnlockClicked);
        cancelButton.setOnClickListener(v -> finish());
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
            // If we can't find the app info, just use a generic name/icon
            appNameText.setText(R.string.locked_app);
            appIconView.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_lock_closed));
        }
    }
    
    private void onUnlockClicked(View view) {
        String enteredPin = pinInput.getText().toString();
        
        if (enteredPin.isEmpty()) {
            Toast.makeText(this, R.string.enter_pin, Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (AppLockService.verifyPin(this, enteredPin)) {
            // Pin is correct, close this activity to allow app access
            finish();
        } else {
            // Pin is incorrect, show error
            Toast.makeText(this, R.string.incorrect_pin, Toast.LENGTH_SHORT).show();
            pinInput.setText("");
        }
    }
    
    @Override
    public void onBackPressed() {
        // Return to home screen instead of allowing app access
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(homeIntent);
        finish();
    }
} 