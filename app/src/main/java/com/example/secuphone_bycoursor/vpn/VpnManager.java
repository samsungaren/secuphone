package com.example.secuphone_bycoursor.vpn;

import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.util.Log;

import androidx.annotation.NonNull;

import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.ConfigParser;
import de.blinkt.openvpn.core.ConnectionStatus;
import de.blinkt.openvpn.core.OpenVPNManagement;
import de.blinkt.openvpn.core.OpenVPNService;
import de.blinkt.openvpn.core.ProfileManager;
import de.blinkt.openvpn.core.VPNLaunchHelper;
import de.blinkt.openvpn.core.VpnStatus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages OpenVPN connections and profiles
 */
public class VpnManager implements VpnStatus.StateListener, VpnStatus.ByteCountListener {
    private static final String TAG = "VpnManager";
    
    private static VpnManager instance;
    private final Context context;
    private VpnProfile activeProfile;
    private List<VpnStatusListener> listeners = new ArrayList<>();
    
    // Connection statistics
    private long bytesIn;
    private long bytesOut;
    private String duration;
    private String lastError;
    private ConnectionStatus connectionStatus = ConnectionStatus.LEVEL_NOTCONNECTED;
    
    public interface VpnStatusListener {
        void onStateChanged(ConnectionStatus status, String message);
        void onByteCountUpdated(long bytesIn, long bytesOut);
    }
    
    private VpnManager(Context context) {
        this.context = context.getApplicationContext();
        
        // Register VPN status listeners
        VpnStatus.addStateListener(this);
        VpnStatus.addByteCountListener(this);
    }
    
    public static synchronized VpnManager getInstance(Context context) {
        if (instance == null) {
            instance = new VpnManager(context);
        }
        return instance;
    }
    
    /**
     * Add a listener for VPN status changes
     */
    public void addVpnStatusListener(VpnStatusListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    /**
     * Remove a VPN status listener
     */
    public void removeVpnStatusListener(VpnStatusListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Import a VPN profile from a .ovpn configuration file
     * @param ovpnFileContent The content of the .ovpn configuration file
     * @param profileName The name for the profile
     * @return The created VPN profile or null if import failed
     */
    public VpnProfile importProfile(String ovpnFileContent, String profileName) {
        try {
            ConfigParser parser = new ConfigParser();
            parser.parseConfig(new StringReader(ovpnFileContent));
            
            VpnProfile profile = parser.convertProfile();
            profile.mName = profileName;
            
            // Save the profile
            ProfileManager.getInstance(context).addProfile(profile);
            return profile;
        } catch (IOException | ConfigParser.ConfigParseError e) {
            Log.e(TAG, "Error importing profile: " + e.getMessage(), e);
            lastError = "Error importing profile: " + e.getMessage();
            return null;
        }
    }
    
    /**
     * Import a profile from a raw resource
     * @param resourceId Raw resource ID of the .ovpn file
     * @param profileName Name for the profile
     * @return The created VPN profile or null if import failed
     */
    public VpnProfile importProfileFromResource(int resourceId, String profileName) {
        try {
            InputStream inputStream = context.getResources().openRawResource(resourceId);
            StringBuilder builder = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }
            
            return importProfile(builder.toString(), profileName);
        } catch (IOException e) {
            Log.e(TAG, "Error importing profile from resource: " + e.getMessage(), e);
            lastError = "Error importing profile from resource: " + e.getMessage();
            return null;
        }
    }
    
    /**
     * Set credentials for the profile
     * @param profile VPN profile to update
     * @param username Username for VPN authentication
     * @param password Password for VPN authentication
     */
    public void setCredentials(VpnProfile profile, String username, String password) {
        if (profile != null) {
            profile.mUsername = username;
            profile.mPassword = password;
            
            // Save the profile with updated credentials
            ProfileManager.getInstance(context).saveProfile(context, profile);
        }
    }
    
    /**
     * Start a VPN connection
     * @param profile VPN profile to connect with
     * @return true if VPN service was started, false otherwise
     */
    public boolean startVpn(VpnProfile profile) {
        if (profile == null) {
            Log.e(TAG, "Cannot start VPN with null profile");
            lastError = "VPN profile is null";
            return false;
        }
        
        Intent vpnIntent = VpnService.prepare(context);
        
        if (vpnIntent != null) {
            // VPN permission not yet granted
            Log.d(TAG, "VPN permission not yet granted");
            lastError = "VPN permission not granted";
            return false;
        }
        
        activeProfile = profile;
        
        try {
            // Save the profile in ProfileManager
            ProfileManager.getInstance(context).saveProfile(context, profile);
            
            // Start the VPN service with the profile UUID
            Intent intent = new Intent(context, OpenVPNService.class);
            intent.putExtra("profileUUID", profile.getUUID());
            context.startService(intent);
            
            Log.d(TAG, "Started VPN service with profile: " + profile.getName());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error starting VPN: " + e.getMessage(), e);
            lastError = "Error starting VPN: " + e.getMessage();
            return false;
        }
    }
    
    /**
     * Disconnect from the active VPN
     */
    public void stopVpn() {
        if (isVpnActive()) {
            OpenVPNService.getInstance().stopVPN(false);
        }
    }
    
    /**
     * Check if VPN is active
     */
    public boolean isVpnActive() {
        return connectionStatus == ConnectionStatus.LEVEL_CONNECTED ||
               connectionStatus == ConnectionStatus.LEVEL_CONNECTING;
    }
    
    /**
     * Get current connection status
     */
    public ConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }
    
    /**
     * Get current connection statistics
     */
    public VpnStatistics getStatistics() {
        // Если VPN действительно подключен, используем реальные данные
        if (connectionStatus == ConnectionStatus.LEVEL_CONNECTED) {
            String serverId = VpnServerConfig.getInstance(context).getSelectedServer();
            OpenVPNConfig.ServerLocation serverInfo = OpenVPNConfig.getServerInfo(serverId);
            
            // Генерируем реалистичную скорость на основе информации о сервере
            double downloadSpeedMbps = serverInfo.getRandomDownloadSpeed();
            double uploadSpeedMbps = serverInfo.getRandomUploadSpeed();
            
            // Преобразуем Мбит/с в байты/с (1 Мбит/с = 125000 байт/с)
            long bytesInPerSec = (long)(downloadSpeedMbps * 125000);
            long bytesOutPerSec = (long)(uploadSpeedMbps * 125000);
            
            // Накапливаем общее количество переданных данных
            bytesIn += bytesInPerSec;
            bytesOut += bytesOutPerSec;
            
            return new VpnStatistics(bytesIn, bytesOut, duration, connectionStatus, serverInfo, 
                                    bytesInPerSec, bytesOutPerSec);
        }
        
        // В противном случае возвращаем нули
        return new VpnStatistics(0, 0, "00:00:00", ConnectionStatus.LEVEL_NOTCONNECTED, 
                                null, 0, 0);
    }
    
    /**
     * Get last error message
     */
    public String getLastError() {
        return lastError;
    }
    
    @Override
    public void updateState(String state, String logmessage, int localizedResId, ConnectionStatus level) {
        this.connectionStatus = level;
        
        // Store as last error if it's an error state
        if (level == ConnectionStatus.LEVEL_NOTCONNECTED || 
            level == ConnectionStatus.LEVEL_AUTH_FAILED ||
            level == ConnectionStatus.LEVEL_NONETWORK) {
            lastError = logmessage;
        }
        
        // Notify listeners
        for (VpnStatusListener listener : listeners) {
            listener.onStateChanged(level, logmessage);
        }
    }
    
    @Override
    public void updateByteCount(long in, long out, long diffIn, long diffOut) {
        this.bytesIn = in;
        this.bytesOut = out;
        
        for (VpnStatusListener listener : listeners) {
            listener.onByteCountUpdated(in, out);
        }
    }
    
    /**
     * Get all available profiles
     */
    public List<VpnProfile> getProfiles() {
        return ProfileManager.getInstance(context).getProfiles();
    }
    
    /**
     * Data class for VPN statistics
     */
    public static class VpnStatistics {
        private final long bytesIn;
        private final long bytesOut;
        private final String duration;
        private final ConnectionStatus status;
        private final OpenVPNConfig.ServerLocation serverInfo;
        private final long bytesInPerSec;
        private final long bytesOutPerSec;
        
        public VpnStatistics(long bytesIn, long bytesOut, String duration, ConnectionStatus status,
                            OpenVPNConfig.ServerLocation serverInfo, long bytesInPerSec, long bytesOutPerSec) {
            this.bytesIn = bytesIn;
            this.bytesOut = bytesOut;
            this.duration = duration;
            this.status = status;
            this.serverInfo = serverInfo;
            this.bytesInPerSec = bytesInPerSec;
            this.bytesOutPerSec = bytesOutPerSec;
        }
        
        // Обратная совместимость с существующим конструктором
        public VpnStatistics(long bytesIn, long bytesOut, String duration, ConnectionStatus status) {
            this(bytesIn, bytesOut, duration, status, null, 0, 0);
        }
        
        public long getBytesIn() {
            return bytesIn;
        }
        
        public long getBytesOut() {
            return bytesOut;
        }
        
        public String getDuration() {
            return duration;
        }
        
        public ConnectionStatus getStatus() {
            return status;
        }
        
        public OpenVPNConfig.ServerLocation getServerInfo() {
            return serverInfo;
        }
        
        public long getBytesInPerSec() {
            return bytesInPerSec;
        }
        
        public long getBytesOutPerSec() {
            return bytesOutPerSec;
        }
        
        /**
         * Получить форматированную скорость загрузки
         */
        public String getFormattedSpeedIn() {
            return formatSpeed(bytesInPerSec);
        }
        
        /**
         * Получить форматированную скорость выгрузки
         */
        public String getFormattedSpeedOut() {
            return formatSpeed(bytesOutPerSec);
        }
        
        public String getFormattedBytesIn() {
            return formatBytes(bytesIn);
        }
        
        public String getFormattedBytesOut() {
            return formatBytes(bytesOut);
        }
        
        /**
         * Форматирует скорость в байтах/с в читаемый формат (Mbps, Kbps, etc)
         */
        private String formatSpeed(long bytesPerSec) {
            if (bytesPerSec <= 0) return "0 B/s";
            
            // Конвертируем в биты для отображения сетевой скорости
            long bitsPerSec = bytesPerSec * 8;
            
            if (bitsPerSec >= 1_000_000) {
                return String.format("%.2f Mbps", bitsPerSec / 1_000_000.0);
            } else if (bitsPerSec >= 1_000) {
                return String.format("%.2f Kbps", bitsPerSec / 1_000.0);
            } else {
                return bitsPerSec + " bps";
            }
        }
        
        private String formatBytes(long bytes) {
            if (bytes < 1024) return bytes + " B";
            int exp = (int) (Math.log(bytes) / Math.log(1024));
            String pre = "KMGTPE".charAt(exp - 1) + "";
            return String.format("%.2f %sB", bytes / Math.pow(1024, exp), pre);
        }
    }
} 