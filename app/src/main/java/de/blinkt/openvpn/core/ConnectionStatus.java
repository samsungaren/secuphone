package de.blinkt.openvpn.core;

/**
 * Stub implementation of ConnectionStatus enum from OpenVPN library
 */
public enum ConnectionStatus {
    LEVEL_CONNECTED,         // Connection established
    LEVEL_CONNECTING,        // Connecting to server
    LEVEL_NOTCONNECTED,      // Not connected
    LEVEL_AUTH_FAILED,       // Authentication failure
    LEVEL_WAITING_FOR_USER_INPUT, // Waiting for user input
    LEVEL_NONETWORK,         // No network connectivity
    LEVEL_VPNPAUSED,         // VPN connection is paused
    LEVEL_UNKNOWN,           // Unknown connection state
    
    // For backward compatibility
    LEVEL_START,             // Connection is starting
    LEVEL_STOPPING,          // Connection is stopping
    LEVEL_RECONNECTING,      // Reconnecting to server
    LEVEL_UNUSABLE;          // Connection is unusable
    
    public int getLevel() {
        return ordinal();
    }
    
    public String toString() {
        switch (this) {
            case LEVEL_CONNECTED:
                return "CONNECTED";
            case LEVEL_CONNECTING:
                return "CONNECTING";
            case LEVEL_NOTCONNECTED:
                return "DISCONNECTED";
            case LEVEL_AUTH_FAILED:
                return "AUTH_FAILED";
            case LEVEL_WAITING_FOR_USER_INPUT:
                return "WAITING_FOR_USER_INPUT";
            case LEVEL_NONETWORK:
                return "NO_NETWORK";
            case LEVEL_VPNPAUSED:
                return "PAUSED";
            default:
                return "UNKNOWN";
        }
    }
} 