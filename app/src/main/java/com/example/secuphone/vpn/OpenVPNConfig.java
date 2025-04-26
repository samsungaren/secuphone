package com.example.secuphone.vpn;

import android.content.Context;
import android.util.Log;

import com.example.secuphone.R;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.ProfileManager;

/**
 * Handles default OpenVPN configuration for SecuPhone
 */
public class OpenVPNConfig {
    private static final String TAG = "OpenVPNConfig";
    
    // Server configuration - updated with real server info
    public static final String SERVER_HOSTNAME = "13.60.95.237";
    public static final String SERVER_PROFILE_NAME = "secuphone@13.60.95.237";
    public static final int TCP_PORT = 443;
    public static final int UDP_PORT = 1194;
    public static final String ADMIN_PANEL_PORT = "943";
    
    // Authentication credentials - updated with real credentials
    public static final String VPN_USERNAME = "secuphone";
    public static final String VPN_PASSWORD = "Aren_100$$";
    
    // Server locations
    private static final List<ServerLocation> SERVER_LOCATIONS = new ArrayList<>();
    static {
        // Initialize servers list with just the real server
        SERVER_LOCATIONS.add(new ServerLocation(
            VpnServerConfig.SERVER_US, "Amazon AWS", SERVER_HOSTNAME, SERVER_HOSTNAME, 
            95, 75, "AES-256-CBC"
        ));
    }
    
    // Default protocol is UDP
    private static boolean useTCP = false;
    
    /**
     * Create default VPN profile from bundled configuration
     * @param context Application context
     * @return Configured VPN profile or null if failed
     */
    public static VpnProfile createDefaultProfile(Context context) {
        try {
            // Get OpenVPN config from assets
            InputStream inputStream = context.getAssets().open("profile-5260234578356361246.ovpn");
            StringBuilder builder = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }
            
            String configContent = builder.toString();
            
            // Create profile with UUID
            String uuid = UUID.randomUUID().toString();
            VpnProfile profile = new VpnProfile(uuid);
            
            // Set basic profile properties
            profile.setName(SERVER_PROFILE_NAME);
            profile.setConfigData(configContent);
            profile.mUsername = VPN_USERNAME;
            profile.mPassword = VPN_PASSWORD;
            
            // Get protocol and port info from server config
            boolean useTcp = OpenVPNConfig.useTCP;
            int port = useTcp ? TCP_PORT : UDP_PORT;
            
            // Set server info
            profile.setServerInfo(SERVER_HOSTNAME, port);
            
            // Save the profile
            ProfileManager.getInstance(context).saveProfile(context, profile);
            
            Log.d(TAG, "Created VPN profile: " + profile);
            return profile;
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating default profile: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Получение информации о сервере по ID
     * @param serverId ID сервера
     * @return Информация о сервере или null если не найдено
     */
    public static ServerLocation getServerInfo(String serverId) {
        for (ServerLocation location : SERVER_LOCATIONS) {
            if (location.getId().equals(serverId)) {
                return location;
            }
        }
        return SERVER_LOCATIONS.get(0); // Возвращаем первый сервер по умолчанию
    }
    
    /**
     * Получить список всех доступных серверов
     * @return Список серверов
     */
    public static List<ServerLocation> getAllServers() {
        return Collections.unmodifiableList(SERVER_LOCATIONS);
    }
    
    /**
     * Получить текущий используемый протокол
     * @return "TCP" или "UDP"
     */
    public static String getProtocolType() {
        return useTCP ? "TCP" : "UDP";
    }
    
    /**
     * Переключить используемый протокол
     * @param useTcpProtocol true для TCP, false для UDP
     */
    public static void setProtocol(boolean useTcpProtocol) {
        useTCP = useTcpProtocol;
    }
    
    /**
     * Получить текущий активный порт
     * @return номер порта
     */
    public static int getCurrentPort() {
        return useTCP ? TCP_PORT : UDP_PORT;
    }
    
    /**
     * Получить текущий локальный IP
     * @return IP-адрес устройства
     */
    public static String getLocalIP() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        if (sAddr.indexOf(':') < 0) {
                            return sAddr;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get local IP", e);
        }
        
        // Если не удалось получить IP, возвращаем стандартное значение
        return "192.168.1.1";
    }
    
    /**
     * Класс для хранения информации о VPN серверах
     */
    public static class ServerLocation {
        private final String id;
        private final String name;
        private final String hostname;
        private final String ipAddress;
        private final int downloadSpeed;
        private final int uploadSpeed;
        private final String encryption;
        
        public ServerLocation(String id, String name, String hostname, String ipAddress, 
                             int downloadSpeed, int uploadSpeed, String encryption) {
            this.id = id;
            this.name = name;
            this.hostname = hostname;
            this.ipAddress = ipAddress;
            this.downloadSpeed = downloadSpeed;
            this.uploadSpeed = uploadSpeed;
            this.encryption = encryption;
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
        public String getHostname() { return hostname; }
        public String getIpAddress() { return ipAddress; }
        public int getDownloadSpeed() { return downloadSpeed; }
        public int getUploadSpeed() { return uploadSpeed; }
        public String getEncryption() { return encryption; }
        
        /**
         * Получить случайную скорость загрузки около базовой скорости
         * @return Скорость в Мбит/с с небольшой вариацией
         */
        public double getRandomDownloadSpeed() {
            Random random = new Random();
            // Генерируем значение с вариацией ±15%
            return downloadSpeed * (0.85 + random.nextDouble() * 0.3);
        }
        
        /**
         * Получить случайную скорость отправки около базовой скорости
         * @return Скорость в Мбит/с с небольшой вариацией
         */
        public double getRandomUploadSpeed() {
            Random random = new Random();
            // Генерируем значение с вариацией ±15%
            return uploadSpeed * (0.85 + random.nextDouble() * 0.3);
        }
    }
} 