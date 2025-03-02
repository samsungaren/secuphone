package com.example.secuphone_bycoursor;

import android.app.Dialog;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class VPNActivity extends AppCompatActivity {

    private TextView connectionTimeValue;
    private TextView connectionStatus;
    private ImageView powerButton;
    private ImageView powerButtonRing;
    private boolean isConnected = false;
    private boolean isConnecting = false;
    
    private long connectionStartTime;
    private Handler timerHandler;
    private Runnable timerRunnable;
    
    // Server selection elements
    private LinearLayout serverLocationCard;
    private ImageView countryFlag;
    private TextView countryName;
    private TextView serverIp;
    
    // Current server selection
    private String currentCountry = "united_states";
    private int currentFlagResId = R.drawable.flag_us;
    private String currentIp = "192.168.0.100";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_vpn);
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.vpn_layout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        
        // Initialize views
        connectionTimeValue = findViewById(R.id.connection_time_value);
        powerButton = findViewById(R.id.power_button);
        powerButtonRing = findViewById(R.id.power_button_ring);
        connectionStatus = findViewById(R.id.connection_time_label);
        
        // Initialize server selection views
        serverLocationCard = findViewById(R.id.server_location_card);
        countryFlag = findViewById(R.id.country_flag);
        countryName = findViewById(R.id.country_name);
        serverIp = findViewById(R.id.server_ip);
        
        // Setup back navigation
        ImageButton menuButton = findViewById(R.id.menu_button);
        menuButton.setOnClickListener(v -> finish());
        
        // Setup connection timer
        setupConnectionTimer();
        
        // Setup VPN connection toggle
        setupVpnToggle();
        
        // Setup server selection
        setupServerSelection();
        
        // Initialize interface with disconnected state
        updateVpnState(false);
    }
    
    private void setupConnectionTimer() {
        timerHandler = new Handler(Looper.getMainLooper());
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (isConnected) {
                    updateConnectionTime();
                    timerHandler.postDelayed(this, 1000);
                }
            }
        };
    }
    
    private void updateConnectionTime() {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - connectionStartTime;
        
        long hours = TimeUnit.MILLISECONDS.toHours(elapsedTime);
        elapsedTime -= TimeUnit.HOURS.toMillis(hours);
        
        long minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedTime);
        elapsedTime -= TimeUnit.MINUTES.toMillis(minutes);
        
        long seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedTime);
        
        String timeString = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
        connectionTimeValue.setText(timeString);
    }
    
    private void setupVpnToggle() {
        powerButtonRing.setOnClickListener(v -> {
            if (isConnecting) {
                return; // Ignore clicks while in transition state
            }
            
            if (isConnected) {
                // Start disconnecting
                simulateDisconnect();
            } else {
                // Start connecting
                simulateConnect();
            }
        });
    }
    
    private void setupServerSelection() {
        serverLocationCard.setOnClickListener(v -> {
            if (isConnected || isConnecting) {
                Toast.makeText(this, "Please disconnect VPN before changing server", Toast.LENGTH_SHORT).show();
                return;
            }
            
            showServerSelectionDialog();
        });
    }
    
    private void showServerSelectionDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_server_selection);
        
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        
        // Get the server options
        LinearLayout serverUs = dialog.findViewById(R.id.server_us);
        LinearLayout serverUk = dialog.findViewById(R.id.server_uk);
        LinearLayout serverGermany = dialog.findViewById(R.id.server_germany);
        LinearLayout serverJapan = dialog.findViewById(R.id.server_japan);
        LinearLayout serverNetherlands = dialog.findViewById(R.id.server_netherlands);
        LinearLayout serverSingapore = dialog.findViewById(R.id.server_singapore);
        
        // Get check marks
        ImageView checkUs = dialog.findViewById(R.id.check_us);
        ImageView checkUk = dialog.findViewById(R.id.check_uk);
        ImageView checkGermany = dialog.findViewById(R.id.check_germany);
        ImageView checkJapan = dialog.findViewById(R.id.check_japan);
        ImageView checkNetherlands = dialog.findViewById(R.id.check_netherlands);
        ImageView checkSingapore = dialog.findViewById(R.id.check_singapore);
        
        // Set current server selection
        if (currentCountry.equals("united_states")) {
            checkUs.setVisibility(View.VISIBLE);
        } else if (currentCountry.equals("united_kingdom")) {
            checkUk.setVisibility(View.VISIBLE);
        } else if (currentCountry.equals("germany")) {
            checkGermany.setVisibility(View.VISIBLE);
        } else if (currentCountry.equals("japan")) {
            checkJapan.setVisibility(View.VISIBLE);
        } else if (currentCountry.equals("netherlands")) {
            checkNetherlands.setVisibility(View.VISIBLE);
        } else if (currentCountry.equals("singapore")) {
            checkSingapore.setVisibility(View.VISIBLE);
        }
        
        // Set click listeners
        serverUs.setOnClickListener(v -> {
            selectServer("united_states", R.drawable.flag_us, generateRandomIp());
            dialog.dismiss();
        });
        
        serverUk.setOnClickListener(v -> {
            selectServer("united_kingdom", R.drawable.flag_uk, generateRandomIp());
            dialog.dismiss();
        });
        
        serverGermany.setOnClickListener(v -> {
            selectServer("germany", R.drawable.flag_germany, generateRandomIp());
            dialog.dismiss();
        });
        
        serverJapan.setOnClickListener(v -> {
            selectServer("japan", R.drawable.flag_japan, generateRandomIp());
            dialog.dismiss();
        });
        
        serverNetherlands.setOnClickListener(v -> {
            selectServer("netherlands", R.drawable.flag_netherlands, generateRandomIp());
            dialog.dismiss();
        });
        
        serverSingapore.setOnClickListener(v -> {
            selectServer("singapore", R.drawable.flag_singapore, generateRandomIp());
            dialog.dismiss();
        });
        
        dialog.show();
    }
    
    private void selectServer(String country, int flagResId, String ipAddress) {
        currentCountry = country;
        currentFlagResId = flagResId;
        currentIp = ipAddress;
        
        // Update UI
        countryFlag.setImageResource(flagResId);
        countryName.setText(getStringResourceByName(country));
        serverIp.setText(ipAddress);
    }
    
    private String getStringResourceByName(String resName) {
        int resId = getResources().getIdentifier(resName, "string", getPackageName());
        if (resId != 0) {
            return getString(resId);
        }
        return resName;
    }
    
    private String generateRandomIp() {
        Random random = new Random();
        return String.format(Locale.US, 
                "%d.%d.%d.%d", 
                random.nextInt(256), 
                random.nextInt(256), 
                random.nextInt(256), 
                random.nextInt(256));
    }
    
    private void simulateConnect() {
        isConnecting = true;
        updateConnectingUI();
        
        // Delay to simulate connection establishment
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            isConnected = true;
            isConnecting = false;
            connectionStartTime = System.currentTimeMillis();
            updateVpnState(true);
            timerHandler.post(timerRunnable);
        }, 2000);
    }
    
    private void simulateDisconnect() {
        isConnecting = true;
        updateDisconnectingUI();
        
        // Delay to simulate disconnection
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            isConnected = false;
            isConnecting = false;
            timerHandler.removeCallbacks(timerRunnable);
            updateVpnState(false);
        }, 1500);
    }
    
    private void updateConnectingUI() {
        connectionStatus.setText(R.string.vpn_connecting);
        powerButton.setImageResource(R.drawable.ic_power);
        
        // Use the green power button ring during connecting state
        powerButtonRing.setImageResource(R.drawable.power_button_ring_on);
    }
    
    private void updateDisconnectingUI() {
        connectionStatus.setText(R.string.vpn_disconnecting);
    }
    
    private void updateVpnState(boolean connected) {
        if (connected) {
            // VPN is connected - Green color
            connectionStatus.setText(R.string.vpn_on);
            powerButton.setImageResource(R.drawable.ic_power);
            powerButtonRing.setImageResource(R.drawable.power_button_ring_on);
        } else {
            // VPN is disconnected - Red color
            connectionStatus.setText(R.string.vpn_off);
            powerButton.setImageResource(R.drawable.ic_power_off);
            powerButtonRing.setImageResource(R.drawable.power_button_ring_off);
            connectionTimeValue.setText("00:00:00");
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timerHandler != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
    }
} 