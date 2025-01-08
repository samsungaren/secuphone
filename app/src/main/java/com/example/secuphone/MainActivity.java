package com.example.secuphone;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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
    }
}