package de.blinkt.openvpn.core;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.secuphone_bycoursor.R;
import com.example.secuphone_bycoursor.VPNActivity;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.net.Socket;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import de.blinkt.openvpn.VpnProfile;

/**
 * Real implementation of OpenVPNService that uses Android's VpnService API
 */
public class OpenVPNService extends VpnService implements OpenVPNManagement {
    
    private static final String TAG = "OpenVPNService";
    private static final String CHANNEL_ID = "vpn_channel";
    private static final int NOTIFICATION_ID = 1;
    
    private static OpenVPNService instance;
    private ParcelFileDescriptor vpnInterface = null;
    private ExecutorService executorService;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private Handler handler;
    private VpnProfile activeProfile;
    
    // Traffic stats
    private long lastIn, lastOut;
    private long diffIn, diffOut;
    
    public static synchronized OpenVPNService getInstance() {
        if (instance == null) {
            instance = new OpenVPNService();
        }
        return instance;
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        handler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }
        
        String profileUUID = intent.getStringExtra("profileUUID");
        if (profileUUID == null) {
            Log.e(TAG, "No profile UUID provided");
            return START_NOT_STICKY;
        }
        
        VpnProfile profile = ProfileManager.getInstance(this).getProfileByUUID(profileUUID);
        if (profile == null) {
            Log.e(TAG, "Profile not found: " + profileUUID);
            return START_NOT_STICKY;
        }
        
        activeProfile = profile;
        
        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification("Connecting to VPN..."));
        
        // Start VPN in background
        if (executorService != null) {
            executorService.shutdownNow();
        }
        executorService = Executors.newFixedThreadPool(2);
        executorService.submit(() -> startVpn(profile));
        
        return START_STICKY;
    }
    
    private void startVpn(VpnProfile profile) {
        if (connecting.get() || connected.get()) {
            return;
        }
        
        connecting.set(true);
        updateVpnStatus(ConnectionStatus.LEVEL_CONNECTING, "Connecting to " + profile.mServerName);
        
        try {
            // Build VPN interface with proper configuration
            Builder builder = new Builder()
                    .setSession(profile.mName)
                    .addAddress("10.8.0.2", 24) // VPN client address
                    .addDnsServer(profile.mDNS1) // Primary DNS
                    .addDnsServer(profile.mDNS2); // Secondary DNS
            
            // Don't route VPN server through the VPN - avoid loopback
            try {
                // Don't route the VPN app through the VPN to avoid loops
                builder.addDisallowedApplication(getPackageName());
                Log.d(TAG, "Added app bypass for: " + getPackageName());
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "Failed to add own app to disallowed apps", e);
            }
            
            // Route all traffic through VPN - this is the key change
            builder.addRoute("0.0.0.0", 0);
            
            // Create VPN interface
            vpnInterface = builder.establish();
            if (vpnInterface == null) {
                throw new IllegalStateException("Failed to establish VPN connection");
            }
            
            // Start VPN tunnel
            connecting.set(false);
            connected.set(true);
            
            // Start traffic monitoring
            startTrafficMonitor();
            
            // Update status
            updateVpnStatus(ConnectionStatus.LEVEL_CONNECTED, "Connected to " + profile.mServerName);
            updateNotification("Connected to " + profile.mServerName);
            
            // Start packet processing
            processPackets(profile);
            
        } catch (Exception e) {
            Log.e(TAG, "VPN start failed", e);
            updateVpnStatus(ConnectionStatus.LEVEL_NOTCONNECTED, "Connection failed: " + e.getMessage());
            connecting.set(false);
            connected.set(false);
            stopSelf();
        }
    }
    
    private void processPackets(VpnProfile profile) {
        try {
            boolean useTcp = (profile.mServerPort == 443); // TCP обычно на порту 443, UDP - на 1194
            
            if (useTcp) {
                // Используем TCP
                Log.d(TAG, "Using TCP connection to VPN server");
                Socket tcpSocket = new Socket();
                tcpSocket.connect(new InetSocketAddress(profile.mServerName, profile.mServerPort), 10000);
                
                // Защищаем сокет от VPN
                if (!protect(tcpSocket)) {
                    Log.e(TAG, "Cannot protect TCP socket");
                    throw new IllegalStateException("Cannot protect TCP socket");
                }
                
                // Получаем VPN интерфейс потоки
                FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
                FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor());
                
                // Буферы для данных
                byte[] buffer = new byte[32767];
                
                // Получаем потоки сокета
                java.io.InputStream socketIn = tcpSocket.getInputStream();
                java.io.OutputStream socketOut = tcpSocket.getOutputStream();
                
                // Создаем потоки для передачи данных
                Thread reader = new Thread(() -> {
                    try {
                        while (connected.get() && !Thread.currentThread().isInterrupted()) {
                            int len = socketIn.read(buffer);
                            if (len > 0) {
                                out.write(buffer, 0, len);
                                lastIn += len;
                            } else if (len < 0) {
                                break;
                            }
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "TCP reader error", e);
                    }
                });
                
                Thread writer = new Thread(() -> {
                    try {
                        while (connected.get() && !Thread.currentThread().isInterrupted()) {
                            int len = in.read(buffer);
                            if (len > 0) {
                                socketOut.write(buffer, 0, len);
                                lastOut += len;
                            } else if (len < 0) {
                                break;
                            }
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "TCP writer error", e);
                    }
                });
                
                reader.start();
                writer.start();
                
                try {
                    reader.join();
                    writer.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    try {
                        tcpSocket.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Error closing TCP socket", e);
                    }
                }
            } else {
                // Используем UDP
                Log.d(TAG, "Using UDP connection to VPN server");
                
                // Create tunnel to VPN server
                final DatagramChannel tunnel = DatagramChannel.open();
                
                // Connect the tunnel to VPN server
                tunnel.connect(new InetSocketAddress(profile.mServerName, profile.mServerPort));
                
                // Protect the tunnel socket from the VPN - CRITICAL STEP
                if (!protect(tunnel.socket())) {
                    Log.e(TAG, "Cannot protect VPN tunnel socket");
                    throw new IllegalStateException("Cannot protect VPN tunnel");
                }
                
                // Get VPN interface streams
                FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
                FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor());
                
                // Allocate buffers
                ByteBuffer packet = ByteBuffer.allocate(32767);
                
                // Process packets until disconnected
                while (connected.get() && !Thread.interrupted()) {
                    // VPN → Server (outgoing traffic)
                    packet.clear();
                    int len = in.read(packet.array());
                    if (len > 0) {
                        // Adjust buffer size for packet
                        packet.limit(len);
                        
                        try {
                            tunnel.write(packet);
                            // Update outgoing traffic count
                            lastOut += len;
                        } catch (IOException e) {
                            Log.e(TAG, "Failed to write to tunnel", e);
                        }
                    }
                    
                    // Server → VPN (incoming traffic)
                    packet.clear();
                    try {
                        len = tunnel.read(packet);
                        if (len > 0) {
                            out.write(packet.array(), 0, len);
                            // Update incoming traffic count
                            lastIn += len;
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to read from tunnel", e);
                    }
                    
                    // Small sleep to prevent CPU spinning
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                
                tunnel.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "VPN tunnel error", e);
            updateVpnStatus(ConnectionStatus.LEVEL_NOTCONNECTED, "Tunnel error: " + e.getMessage());
        } finally {
            closeVpnInterface();
        }
    }
    
    private void startTrafficMonitor() {
        final Handler trafficHandler = new Handler(Looper.getMainLooper());
        trafficHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (connected.get()) {
                    long in = lastIn;
                    long out = lastOut;
                    
                    // Calculate difference since last update
                    diffIn = in - lastIn;
                    diffOut = out - lastOut;
                    
                    // Update VPN status with traffic stats
                    VpnStatus.updateByteCount(lastIn, lastOut, diffIn, diffOut);
                    
                    // Schedule next update
                    trafficHandler.postDelayed(this, 1000);
                }
            }
        }, 1000);
    }
    
    private void updateVpnStatus(ConnectionStatus level, String message) {
        VpnStatus.updateStateString("SECUPHONE", message, R.string.app_name, level);
    }
    
    private void closeVpnInterface() {
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
                vpnInterface = null;
            } catch (IOException e) {
                Log.e(TAG, "Error closing VPN interface", e);
            }
        }
    }
    
    @Override
    public void onDestroy() {
        connected.set(false);
        
        if (executorService != null) {
            executorService.shutdownNow();
            try {
                executorService.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            executorService = null;
        }
        
        closeVpnInterface();
        updateVpnStatus(ConnectionStatus.LEVEL_NOTCONNECTED, "Disconnected");
        
        super.onDestroy();
    }
    
    @Override
    public boolean stopVPN(boolean replaceConnection) {
        connected.set(false);
        
        if (executorService != null) {
            executorService.shutdownNow();
        }
        
        updateVpnStatus(ConnectionStatus.LEVEL_NOTCONNECTED, "Disconnected");
        stopSelf();
        return true;
    }
    
    @Override
    public void resume() {
        // Not needed
    }
    
    @Override
    public void pause(PauseReason reason) {
        // Not needed
    }
    
    @Override
    public void reconnect() {
        if (activeProfile != null) {
            stopVPN(true);
            startVpn(activeProfile);
        }
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, 
                    "VPN Service", 
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("SecuPhone VPN connection status");
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    private Notification createNotification(String contentText) {
        Intent notificationIntent = new Intent(this, VPNActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, 
                PendingIntent.FLAG_IMMUTABLE);
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SecuPhone VPN")
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_vpn)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }
    
    private void updateNotification(String content) {
        NotificationManager notificationManager = 
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, createNotification(content));
    }
} 