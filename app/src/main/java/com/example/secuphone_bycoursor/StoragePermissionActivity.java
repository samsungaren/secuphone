package com.example.secuphone_bycoursor;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class StoragePermissionActivity extends AppCompatActivity {

    private Button grantPermissionButton;
    private Button notNowButton;
    private TextView skipForNowText;
    
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private boolean isPermissionRequested = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_storage_permission);
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.storage_permission_layout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        grantPermissionButton = findViewById(R.id.grant_permission_button);
        notNowButton = findViewById(R.id.not_now_button);
        skipForNowText = findViewById(R.id.skip_for_now_text);
        
        requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    setResult(RESULT_OK);
                    finish();
                } else {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(this, getStoragePermission())) {
                        showRationale();
                    } else {
                        showPermissionDeniedDialog();
                    }
                }
            }
        );
        
        grantPermissionButton.setOnClickListener(v -> requestStoragePermission());
        
        notNowButton.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        
        skipForNowText.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        
        // Handle back button press
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!isPermissionRequested) {
                    Toast.makeText(StoragePermissionActivity.this, R.string.storage_access_required, Toast.LENGTH_SHORT).show();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }
    
    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES);
                isPermissionRequested = true;
            } else {
                setResult(RESULT_OK);
                finish();
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
                isPermissionRequested = true;
            } else {
                setResult(RESULT_OK);
                finish();
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                isPermissionRequested = true;
            } else {
                setResult(RESULT_OK);
                finish();
            }
        }
    }
    
    private void showRationale() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.storage_permission)
            .setMessage(R.string.storage_permission_explanation)
            .setPositiveButton(R.string.grant_permission, (dialog, which) -> {
                requestStoragePermission();
            })
            .setNegativeButton(R.string.not_now, (dialog, which) -> {
                setResult(RESULT_CANCELED);
                finish();
            })
            .setCancelable(false)
            .show();
    }
    
    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.permission_denied)
                .setMessage(R.string.permission_denied_explanation)
                .setPositiveButton(R.string.open_settings, (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                    
                    setResult(RESULT_CANCELED);
                    finish();
                })
                .setNegativeButton(R.string.not_now, (dialog, which) -> {
                    setResult(RESULT_CANCELED);
                    finish();
                })
                .setCancelable(false)
                .show();
    }
    
    private String getStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ requires more specific permissions for media
            // For this app, we'll use READ_MEDIA_IMAGES as a representative permission
            return Manifest.permission.READ_MEDIA_IMAGES;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10-12
            return Manifest.permission.READ_EXTERNAL_STORAGE;
        } else {
            // Android 9 and below
            return Manifest.permission.WRITE_EXTERNAL_STORAGE;
        }
    }
    
    public static boolean hasStoragePermission(AppCompatActivity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ requires more specific permissions for media
            return ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10-12
            return ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            // Android 9 and below
            return ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }
} 