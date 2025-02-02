package com.example.secuphone;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;


public class MainActivity extends AppCompatActivity {

    private ImageButton vpn_switch;
    private ImageButton lock_switch;
    private ImageButton url_checker_switch;
    private ImageButton find_phone_switch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        vpn_switch = findViewById(R.id.vpn_switch_button);
        vpn_switch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, VpnActivity.class);
                startActivity(intent);
            }
        });

        lock_switch = findViewById(R.id.lock_switch_button);
        lock_switch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, LockActivity.class);
                startActivity(intent);
            }
        });

        url_checker_switch = findViewById(R.id.url_checker_switch);
        url_checker_switch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, URL.class);
                startActivity(intent);
            }
        });

        find_phone_switch = findViewById(R.id.find_phone_switch);
        find_phone_switch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, FindPhoneActivity.class);
                startActivity(intent);
            }
        });

        // Check camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
        } else {
            accessCamera();
        }

        // Check FOREGROUND_SERVICE permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE) != PackageManager.PERMISSION_GRANTED) {
            requestForegroundPermission();
        } else {
            accessForeground();
        }
    }

    private final ActivityResultLauncher<String> requestLauncherPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
       if (isGranted) {
           accessCamera();
           accessForeground();
       } else {
           Toast.makeText(this, "Camera permission in denied", Toast.LENGTH_LONG).show();
       }
    });

    private void requestCameraPermission() {
        requestLauncherPermission.launch(Manifest.permission.CAMERA);
    }

    private void requestForegroundPermission() {
        requestLauncherPermission.launch(Manifest.permission.FOREGROUND_SERVICE);
    }

    private void accessCamera() {
        Toast.makeText(this, "Camera access is granted", Toast.LENGTH_LONG).show();
    }
    private void accessForeground() {
        Toast.makeText(this, "Foreground access is granted", Toast.LENGTH_LONG).show();
    }
}