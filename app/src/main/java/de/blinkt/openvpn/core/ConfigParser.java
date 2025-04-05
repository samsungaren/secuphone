package de.blinkt.openvpn.core;

import java.io.IOException;
import java.io.StringReader;
import java.util.UUID;

import de.blinkt.openvpn.VpnProfile;

/**
 * Stub implementation of the OpenVPN ConfigParser
 */
public class ConfigParser {
    
    private String serverName = "example.com";
    private int serverPort = 1194;
    private String protocol = "udp";
    private String profileName = "Default Profile";
    
    public static class ConfigParseError extends Exception {
        public ConfigParseError(String msg) {
            super(msg);
        }
    }
    
    public void parseConfig(StringReader reader) throws IOException, ConfigParseError {
        // Stub implementation - would normally parse the OpenVPN config file
    }
    
    public VpnProfile convertProfile() {
        VpnProfile profile = new VpnProfile(UUID.randomUUID().toString());
        profile.mName = profileName;
        profile.mServerName = serverName;
        profile.mServerPort = serverPort;
        return profile;
    }
} 