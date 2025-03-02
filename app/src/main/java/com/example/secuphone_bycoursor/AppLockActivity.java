package com.example.secuphone_bycoursor;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class AppLockActivity extends AppCompatActivity {

    private LinearLayout snapchatApp;
    private LinearLayout facebookApp;
    private LinearLayout tiktokApp;
    private LinearLayout whatsappApp;
    
    private ImageView snapchatLockIcon;
    private ImageView facebookLockIcon;
    private ImageView tiktokLockIcon;
    private ImageView whatsappLockIcon;
    
    private boolean isSnapchatLocked = true;
    private boolean isFacebookLocked = true;
    private boolean isTiktokLocked = false;
    private boolean isWhatsappLocked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_app_lock);
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.app_lock_layout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        
        // Setup back navigation
        ImageButton menuButton = findViewById(R.id.menu_button);
        menuButton.setOnClickListener(v -> finish());
        
        // Initialize app views
        snapchatApp = findViewById(R.id.snapchat_app);
        facebookApp = findViewById(R.id.facebook_app);
        tiktokApp = findViewById(R.id.tiktok_app);
        whatsappApp = findViewById(R.id.whatsapp_app);
        
        // Find lock icons
        snapchatLockIcon = snapchatApp.findViewById(R.id.snapchat_lock_icon);
        facebookLockIcon = facebookApp.findViewById(R.id.facebook_lock_icon);
        tiktokLockIcon = tiktokApp.findViewById(R.id.tiktok_lock_icon);
        whatsappLockIcon = whatsappApp.findViewById(R.id.whatsapp_lock_icon);
        
        // Set initial lock states
        updateLockIcons();
        
        // Setup app click listeners
        setupAppClickListeners();
    }
    
    private void setupAppClickListeners() {
        snapchatApp.setOnClickListener(v -> {
            isSnapchatLocked = !isSnapchatLocked;
            updateLockIcons();
        });
        
        facebookApp.setOnClickListener(v -> {
            isFacebookLocked = !isFacebookLocked;
            updateLockIcons();
        });
        
        tiktokApp.setOnClickListener(v -> {
            isTiktokLocked = !isTiktokLocked;
            updateLockIcons();
        });
        
        whatsappApp.setOnClickListener(v -> {
            isWhatsappLocked = !isWhatsappLocked;
            updateLockIcons();
        });
    }
    
    private void updateLockIcons() {
        // Update lock icons based on current state
        updateLockIcon(snapchatLockIcon, isSnapchatLocked);
        updateLockIcon(facebookLockIcon, isFacebookLocked);
        updateLockIcon(tiktokLockIcon, isTiktokLocked);
        updateLockIcon(whatsappLockIcon, isWhatsappLocked);
    }
    
    private void updateLockIcon(ImageView lockIcon, boolean isLocked) {
        if (lockIcon != null) {
            lockIcon.setImageResource(isLocked ? R.drawable.ic_lock_closed : R.drawable.ic_lock_open);
            lockIcon.setContentDescription(isLocked ? getString(R.string.locked) : getString(R.string.unlocked));
        }
    }
} 