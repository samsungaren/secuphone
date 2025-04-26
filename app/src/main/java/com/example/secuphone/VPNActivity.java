package com.example.secuphone;

import android.app.Dialog;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.secuphone.vpn.OpenVPNConfig;
import com.example.secuphone.vpn.VpnManager;
import com.example.secuphone.vpn.VpnServerConfig;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.ConnectionStatus;

public class VPNActivity extends AppCompatActivity implements VpnManager.VpnStatusListener {

    private static final String TAG = "VPNActivity";
    
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
    private TextView encryptionValue;
    private TextView protocolValue;
    
    // Current server selection
    private String currentCountry = "united_states";
    private int currentFlagResId = R.drawable.flag_us;
    
    // VPN Manager
    private VpnManager vpnManager;
    private VpnServerConfig serverConfig;
    private VpnProfile vpnProfile;
    
    // VPN Permission launcher
    private final ActivityResultLauncher<Intent> vpnPermissionLauncher = 
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), 
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    // Permission granted, start VPN
                    connectToVpn();
                } else {
                    Toast.makeText(this, "VPN permission denied", Toast.LENGTH_SHORT).show();
                    updateVpnState(false);
                }
            });

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
        
        // Initialize VPN Manager
        vpnManager = VpnManager.getInstance(this);
        serverConfig = VpnServerConfig.getInstance(this);
        
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
        encryptionValue = findViewById(R.id.encryption_value);
        protocolValue = findViewById(R.id.protocol_value);
        
        // Setup back navigation
        ImageButton backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> finish());
        
        // Setup settings button
        ImageButton settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(v -> {
            showSettingsDialog();
        });
        
        // Setup connection timer
        setupConnectionTimer();
        
        // Setup VPN connection toggle
        setupVpnToggle();
        
        // Setup server selection
        setupServerSelection();
        
        // Register as VPN listener
        vpnManager.addVpnStatusListener(this);
        
        // Setup initial profile
        setupVpnProfile();
        
        // Initialize interface with current VPN state
        updateVpnState(vpnManager.isVpnActive());
        
        // Update server selection UI
        updateServerUI();
    }
    
    private void setupVpnProfile() {
        // Try to get existing profile or create a new one
        if (vpnProfile == null) {
            // Create a new VPN profile with real configuration
            vpnProfile = OpenVPNConfig.createDefaultProfile(this);
            
            if (vpnProfile != null) {
                Log.d(TAG, "VPN profile created successfully: " + vpnProfile.getName());
                
                // Apply server selection
                String serverId = serverConfig.getSelectedServer();
                OpenVPNConfig.ServerLocation serverLocation = OpenVPNConfig.getServerInfo(serverId);
                
                // Set server and port based on protocol selection
                boolean useTcp = serverConfig.isUsingTcp();
                int port = useTcp ? OpenVPNConfig.TCP_PORT : OpenVPNConfig.UDP_PORT;
                vpnProfile.setServerInfo(serverLocation.getHostname(), port);
                
                Log.d(TAG, "Server set to: " + vpnProfile.mServerName + ":" + vpnProfile.mServerPort);
            } else {
                Log.e(TAG, "Failed to create VPN profile");
            }
        }
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
        View powerButtonContainer = findViewById(R.id.power_button_container);
        powerButtonContainer.setOnClickListener(v -> {
            if (isConnecting) {
                return; // Ignore clicks while in transition state
            }
            
            if (isConnected) {
                // Start disconnecting
                disconnectFromVpn();
            } else {
                // Request VPN permissions and connect
                requestVpnPermission();
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
        
        // Get the server options - just one real server now
        LinearLayout serverUs = dialog.findViewById(R.id.server_us);
        
        // Get check marks
        ImageView checkUs = dialog.findViewById(R.id.check_us);
        
        // Set current server selection
        checkUs.setVisibility(View.VISIBLE);
        
        // Set click listeners
        serverUs.setOnClickListener(v -> {
            selectServer("united_states", R.drawable.flag_us);
            dialog.dismiss();
        });
        
        dialog.show();
    }
    
    private void showSettingsDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_vpn_settings);
        
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        
        // Protocol selection
        View tcpOption = dialog.findViewById(R.id.option_tcp);
        View udpOption = dialog.findViewById(R.id.option_udp);
        
        // Mark current protocol
        tcpOption.setSelected(serverConfig.isUsingTcp());
        udpOption.setSelected(!serverConfig.isUsingTcp());
        
        // Set click listeners
        tcpOption.setOnClickListener(v -> {
            if (isConnected) {
                Toast.makeText(this, "Please disconnect VPN before changing protocol", Toast.LENGTH_SHORT).show();
                return;
            }
            serverConfig.setProtocol(true);
            tcpOption.setSelected(true);
            udpOption.setSelected(false);
            updateServerUI();
        });
        
        udpOption.setOnClickListener(v -> {
            if (isConnected) {
                Toast.makeText(this, "Please disconnect VPN before changing protocol", Toast.LENGTH_SHORT).show();
                return;
            }
            serverConfig.setProtocol(false);
            tcpOption.setSelected(false);
            udpOption.setSelected(true);
            updateServerUI();
        });
        
        // Close button
        View closeButton = dialog.findViewById(R.id.btn_close);
        closeButton.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }
    
    private void selectServer(String country, int flagResId) {
        currentCountry = country;
        currentFlagResId = flagResId;
        serverConfig.setSelectedServer(VpnServerConfig.SERVER_US);
        updateServerUI();
    }
    
    private void updateServerUI() {
        // Set country flag and name
        countryFlag.setImageResource(currentFlagResId);
        
        // Get server info
        OpenVPNConfig.ServerLocation serverLocation = 
            OpenVPNConfig.getServerInfo(serverConfig.getSelectedServer());
        
        // Update UI
        countryName.setText(serverLocation.getName());
        serverIp.setText(serverLocation.getIpAddress());
        
        // Update protocol info
        protocolValue.setText(OpenVPNConfig.getProtocolType());
        
        // Update encryption
        encryptionValue.setText(serverLocation.getEncryption());
    }
    
    private void requestVpnPermission() {
        Intent intent = VpnService.prepare(this);
        if (intent != null) {
            vpnPermissionLauncher.launch(intent);
        } else {
            // VPN permission already granted
            connectToVpn();
        }
    }
    
    private void connectToVpn() {
        if (vpnProfile == null) {
            setupVpnProfile();
            if (vpnProfile == null) {
                Toast.makeText(this, "Failed to create VPN profile", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        
        // Show connecting state
        setConnectingState();
        
        // Убедиться что профиль правильно настроен
        String serverId = serverConfig.getSelectedServer();
        OpenVPNConfig.ServerLocation serverLocation = OpenVPNConfig.getServerInfo(serverId);
        boolean useTcp = serverConfig.isUsingTcp();
        int port = useTcp ? OpenVPNConfig.TCP_PORT : OpenVPNConfig.UDP_PORT;
        
        // Установить текущие данные сервера
        vpnProfile.setServerInfo(serverLocation.getHostname(), port);
        vpnProfile.mUsername = OpenVPNConfig.VPN_USERNAME;
        vpnProfile.mPassword = OpenVPNConfig.VPN_PASSWORD;
        
        Log.d(TAG, "Connecting to VPN server: " + vpnProfile.mServerName + ":" + vpnProfile.mServerPort + 
              " (Protocol: " + (useTcp ? "TCP" : "UDP") + ")");
        
        // Start VPN connection
        boolean started = vpnManager.startVpn(vpnProfile);
        if (!started) {
            Toast.makeText(this, "Failed to start VPN: " + vpnManager.getLastError(), 
                          Toast.LENGTH_SHORT).show();
            updateVpnState(false);
        }
    }
    
    private void disconnectFromVpn() {
        // Show disconnecting state
        setDisconnectingState();
        
        // Stop VPN
        vpnManager.stopVpn();
    }
    
    private void setConnectingState() {
        isConnecting = true;
        connectionStatus.setText(R.string.vpn_connecting);
        
        // Animate power button
        animatePowerButtonConnecting();
    }
    
    private void setDisconnectingState() {
        isConnecting = true;
        connectionStatus.setText(R.string.vpn_disconnecting);
        
        // Animate power button
        animatePowerButtonDisconnecting();
    }
    
    private void updateVpnState(boolean connected) {
        isConnected = connected;
        isConnecting = false;
        
        if (connected) {
            // VPN is connected
            connectionStatus.setText(R.string.vpn_connected);
            powerButtonBg.setBackground(ContextCompat.getDrawable(this, R.drawable.vpn_toggle_background_on));
            connectionStartTime = System.currentTimeMillis();
            timerHandler.post(timerRunnable);
            
            // Update public IP
            publicIpValue.setText(serverIp.getText());
            
        } else {
            // VPN is disconnected
            connectionStatus.setText(R.string.vpn_off);
            powerButtonBg.setBackground(ContextCompat.getDrawable(this, R.drawable.vpn_toggle_background_off));
            timerHandler.removeCallbacks(timerRunnable);
            connectionTimeValue.setText("00:00:00");
            
            // Reset network stats
            downloadSpeed.setText("0.00 Mbps");
            uploadSpeed.setText("0.00 Mbps");
            
            // Update public IP to local IP
            publicIpValue.setText(OpenVPNConfig.getLocalIP());
        }
    }
    
    private void animatePowerButtonConnecting() {
        // Create pulsate animation for connecting
        ObjectAnimator scaleAnimation = ObjectAnimator.ofPropertyValuesHolder(
                powerButtonBg,
                PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.2f, 1.0f),
                PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.2f, 1.0f));
        scaleAnimation.setDuration(1000);
        scaleAnimation.setRepeatCount(ValueAnimator.INFINITE);
        scaleAnimation.start();
    }
    
    private void animatePowerButtonDisconnecting() {
        // Create fade animation for disconnecting
        ObjectAnimator alphaAnimation = ObjectAnimator.ofFloat(powerButtonBg, "alpha", 1.0f, 0.5f, 1.0f);
        alphaAnimation.setDuration(1000);
        alphaAnimation.setRepeatCount(ValueAnimator.INFINITE);
        alphaAnimation.start();
    }
    
    // VpnStatusListener implementation
    @Override
    public void onStateChanged(ConnectionStatus status, String message) {
        runOnUiThread(() -> {
            // Stop any animations
            powerButtonBg.clearAnimation();
            powerButtonBg.setAlpha(1.0f);
            powerButtonBg.setScaleX(1.0f);
            powerButtonBg.setScaleY(1.0f);
            
            // Update UI based on status
            switch (status) {
                case LEVEL_CONNECTED:
                    updateVpnState(true);
                    break;
                    
                case LEVEL_CONNECTING:
                case LEVEL_WAITING_FOR_USER_INPUT:
                    setConnectingState();
                    break;
                    
                case LEVEL_NONETWORK:
                case LEVEL_NOTCONNECTED:
                case LEVEL_AUTH_FAILED:
                case LEVEL_VPNPAUSED:
                default:
                    updateVpnState(false);
                    if (status == ConnectionStatus.LEVEL_AUTH_FAILED) {
                        Toast.makeText(this, "Authentication failed: " + message, Toast.LENGTH_LONG).show();
                    } else if (message != null && !message.isEmpty()) {
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
        });
    }
    
    @Override
    public void onByteCountUpdated(long bytesIn, long bytesOut) {
        runOnUiThread(() -> {
            // Get the VPN statistics
            VpnManager.VpnStatistics stats = vpnManager.getStatistics();
            
            // Update UI with real stats
            downloadSpeed.setText(stats.getFormattedSpeedIn());
            uploadSpeed.setText(stats.getFormattedSpeedOut());
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Unregister VPN listener
        vpnManager.removeVpnStatusListener(this);
        
        // Stop timer
        if (timerHandler != null && timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
    }
} 