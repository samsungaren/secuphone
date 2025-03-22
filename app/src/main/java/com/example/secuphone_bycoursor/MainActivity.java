package com.example.secuphone_bycoursor;

import android.content.Intent;
import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.OnBackPressedDispatcher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.secuphone_bycoursor.utils.PermissionManager;
import com.google.android.material.navigation.NavigationView;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private DrawerLayout drawerLayout;
    private PermissionManager permissionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        
        // Initialize permission manager
        permissionManager = new PermissionManager(this);
        
        // Setup navigation drawer
        setupNavigationDrawer();
        
        // Setup click listeners for menu items
        setupMenuItemListeners();
        
        // Check permissions on startup
        checkAppPermissions();
        
        // Handle back button press
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }
    
    private void setupNavigationDrawer() {
        drawerLayout = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        
        // Setup menu button to open drawer
        ImageButton menuButton = findViewById(R.id.menu_button);
        menuButton.setOnClickListener(v -> {
            drawerLayout.openDrawer(GravityCompat.START);
        });
        
        // Set up subscribe button in nav header
        View headerView = navigationView.getHeaderView(0);
        headerView.findViewById(R.id.subscribe_button).setOnClickListener(v -> {
            Toast.makeText(this, "Subscribe feature coming soon!", Toast.LENGTH_SHORT).show();
            drawerLayout.closeDrawer(GravityCompat.START);
        });
    }
    
    private void setupMenuItemListeners() {
        // VPN Button Click Listener
        LinearLayout vpnButton = findViewById(R.id.vpn_button);
        vpnButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Launch VPN Activity
                Intent intent = new Intent(MainActivity.this, VPNActivity.class);
                startActivity(intent);
            }
        });
        
        // App Lock Button Click Listener
        LinearLayout appLockButton = findViewById(R.id.app_lock_button);
        appLockButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Launch App Lock Activity
                Intent intent = new Intent(MainActivity.this, AppLockActivity.class);
                startActivity(intent);
            }
        });
        
        // URL Checker Button Click Listener
        LinearLayout urlCheckerButton = findViewById(R.id.url_checker_button);
        urlCheckerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Launch URL Checker Activity
                Intent intent = new Intent(MainActivity.this, URLCheckerActivity.class);
                startActivity(intent);
            }
        });
        
        // Find Phone Button Click Listener
        LinearLayout findPhoneButton = findViewById(R.id.find_phone_button);
        findPhoneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Check permissions before launching activity
                permissionManager.checkCameraAndStoragePermissions(new PermissionManager.OnPermissionResultListener() {
                    @Override
                    public void onPermissionGranted() {
                        // Launch Find Phone Activity
                        Intent intent = new Intent(MainActivity.this, FindPhoneActivity.class);
                        startActivity(intent);
                    }
                    
                    @Override
                    public void onPermissionDenied() {
                        Toast.makeText(MainActivity.this, 
                                R.string.camera_permission_required, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
        
        // Hidden Files Button Click Listener
        LinearLayout hiddenFilesButton = findViewById(R.id.hidden_files_button);
        hiddenFilesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Check permissions before launching activity
                permissionManager.checkStoragePermission(new PermissionManager.OnPermissionResultListener() {
                    @Override
                    public void onPermissionGranted() {
                        // Launch Hidden Files Activity
                        Intent intent = new Intent(MainActivity.this, HiddenFilesActivity.class);
                        startActivity(intent);
                    }
                    
                    @Override
                    public void onPermissionDenied() {
                        Toast.makeText(MainActivity.this, 
                                R.string.storage_access_required, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
    
    /**
     * Check if the app has all required permissions on startup
     */
    private void checkAppPermissions() {
        // We'll proactively check for storage permissions on startup
        // since multiple features require it
        if (!StoragePermissionActivity.hasStoragePermission(this)) {
            permissionManager.checkStoragePermission(new PermissionManager.OnPermissionResultListener() {
                @Override
                public void onPermissionGranted() {
                    // Update security level if needed
                    updateSecurityLevel();
                }
                
                @Override
                public void onPermissionDenied() {
                    // That's okay, user can grant later when needed
                }
            });
        } else {
            updateSecurityLevel();
        }
    }
    
    /**
     * Update the security level progress bar based on enabled security features
     */
    private void updateSecurityLevel() {
        // This would be updated based on which security features are enabled
        // For now it's just a placeholder
    }
    
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.nav_settings) {
            Toast.makeText(this, "Settings coming soon!", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.nav_help) {
            Toast.makeText(this, "Help center coming soon!", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.nav_logout) {
            Toast.makeText(this, "Logout coming soon!", Toast.LENGTH_SHORT).show();
        }
        
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }
}