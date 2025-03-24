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
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.view.animation.AccelerateDecelerateInterpolator;

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
    private View powerButtonBg;
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
    
    // Connection stats
    private TextView downloadSpeed;
    private TextView uploadSpeed;
    private TextView publicIpValue;
    
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
        powerButtonBg = findViewById(R.id.power_button_bg);
        connectionStatus = findViewById(R.id.connection_status);
        
        // Initialize server selection views
        serverLocationCard = findViewById(R.id.server_location_card);
        countryFlag = findViewById(R.id.country_flag);
        countryName = findViewById(R.id.country_name);
        serverIp = findViewById(R.id.server_ip);
        
        // Initialize connection stats
        downloadSpeed = findViewById(R.id.download_speed);
        uploadSpeed = findViewById(R.id.upload_speed);
        publicIpValue = findViewById(R.id.public_ip_value);
        
        // Setup back navigation
        ImageButton backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> finish());
        
        // Setup settings button
        ImageButton settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(v -> {
            Toast.makeText(this, "VPN Settings coming soon", Toast.LENGTH_SHORT).show();
        });
        
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
                    updateNetworkStats();
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
    
    private void updateNetworkStats() {
        // Generate random network stats for demo
        Random random = new Random();
        double download = 5.0 + random.nextDouble() * 45.0;
        double upload = 3.0 + random.nextDouble() * 15.0;
        
        downloadSpeed.setText(String.format(Locale.US, "%.2f Mbps", download));
        uploadSpeed.setText(String.format(Locale.US, "%.2f Mbps", upload));
    }
    
    private void setupVpnToggle() {
        View powerButtonContainer = findViewById(R.id.power_button_container);
        powerButtonContainer.setOnClickListener(v -> {
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
        
        // Update UI to show connecting state
        updateConnectingUI();
        
        // Simulate connection delay
        new Handler().postDelayed(() -> {
            isConnecting = false;
            isConnected = true;
            
            // Set connection start time
            connectionStartTime = System.currentTimeMillis();
            
            // Update UI to show connected state
            updateVpnState(true);
            
            // Start connection timer
            timerHandler.post(timerRunnable);
            
            // Generate random public IP
            publicIpValue.setText(generateRandomIp());
            
        }, 2000); // 2 second delay
    }
    
    private void simulateDisconnect() {
        isConnecting = true;
        
        // Update UI to show disconnecting state
        updateDisconnectingUI();
        
        // Simulate disconnection delay
        new Handler().postDelayed(() -> {
            isConnecting = false;
            isConnected = false;
            
            // Update UI to show disconnected state
            updateVpnState(false);
            
            // Stop connection timer
            timerHandler.removeCallbacks(timerRunnable);
            
        }, 1000); // 1 second delay
    }
    
    private void updateConnectingUI() {
        powerButtonBg.setBackground(ContextCompat.getDrawable(this, R.drawable.vpn_toggle_background_connecting));
        connectionStatus.setText(R.string.vpn_connecting);
        
        // Animate power button (pulse effect)
        animatePowerButton();
    }
    
    private void updateDisconnectingUI() {
        connectionStatus.setText(R.string.vpn_disconnecting);
    }
    
    private void updateVpnState(boolean connected) {
        if (connected) {
            powerButtonBg.setBackground(ContextCompat.getDrawable(this, R.drawable.vpn_toggle_background_on));
            connectionStatus.setText(R.string.vpn_connected);
            powerButton.setColorFilter(ContextCompat.getColor(this, R.color.text));
        } else {
            powerButtonBg.setBackground(ContextCompat.getDrawable(this, R.drawable.vpn_toggle_background_off));
            connectionStatus.setText(R.string.vpn_off);
            connectionTimeValue.setText("--:--:--");
            downloadSpeed.setText("--.- Mbps");
            uploadSpeed.setText("--.- Mbps");
            publicIpValue.setText("---.---.---.---");
            powerButton.setColorFilter(ContextCompat.getColor(this, R.color.text));
        }
    }
    
    private void animatePowerButton() {
        // Create a pulse animation
        ObjectAnimator scaleDown = ObjectAnimator.ofPropertyValuesHolder(
                powerButton,
                PropertyValuesHolder.ofFloat("scaleX", 1.0f, 0.9f, 1.0f),
                PropertyValuesHolder.ofFloat("scaleY", 1.0f, 0.9f, 1.0f));
        
        scaleDown.setDuration(1000);
        scaleDown.setRepeatCount(ValueAnimator.INFINITE);
        scaleDown.setInterpolator(new AccelerateDecelerateInterpolator());
        scaleDown.start();
        
        // Stop animation when connection is established or failed
        new Handler().postDelayed(() -> {
            if (!isConnecting) {
                scaleDown.cancel();
                powerButton.setScaleX(1.0f);
                powerButton.setScaleY(1.0f);
            }
        }, 2100);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Make sure to remove any pending callbacks
        if (timerHandler != null && timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
    }
} 