package de.blinkt.openvpn;

import android.content.Context;

/**
 * Implementation of VpnProfile for OpenVPN
 */
public class VpnProfile {
    public String mName;
    public String mUsername;
    public String mPassword;
    public String mServerName = "13.60.95.237"; // Default server address
    public int mServerPort = 1194; // Default UDP port
    public String mDNS1 = "8.8.8.8"; // Default DNS
    public String mDNS2 = "8.8.4.4"; // Secondary DNS
    private String mProfileCreator;
    private String mConfigData;
    private String mUuid;
    
    public VpnProfile(String uuid) {
        mUuid = uuid;
    }
    
    public String getUUID() {
        return mUuid;
    }
    
    public String getName() {
        return mName;
    }
    
    public void setName(String name) {
        mName = name;
    }
    
    public String getConfigData() {
        return mConfigData;
    }
    
    public void setConfigData(String configData) {
        mConfigData = configData;
        
        // Parse server and port from config if available
        if (configData != null && !configData.isEmpty()) {
            // Look for remote directive
            String[] lines = configData.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("remote ")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 3) {
                        mServerName = parts[1];
                        try {
                            mServerPort = Integer.parseInt(parts[2]);
                        } catch (NumberFormatException e) {
                            // Use default port
                        }
                    }
                    break;
                }
            }
        }
    }
    
    public void setProfileCreator(String creator) {
        mProfileCreator = creator;
    }
    
    public void setServerInfo(String hostname, int port) {
        mServerName = hostname;
        mServerPort = port;
    }
    
    @Override
    public String toString() {
        return mName + " (" + mServerName + ":" + mServerPort + ")";
    }
} 