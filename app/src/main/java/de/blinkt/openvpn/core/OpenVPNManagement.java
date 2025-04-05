package de.blinkt.openvpn.core;

/**
 * Stub implementation of OpenVPNManagement interface from OpenVPN library
 */
public interface OpenVPNManagement {
    enum PauseReason {
        PAUSE_DISCONNECT,
        PAUSE_RECONNECT,
        PAUSE_TRAFFIC,
        PAUSE_IDLE,
        PAUSE_NETWORK,
        SCREENOFF
    }
    
    void resume();
    boolean stopVPN(boolean replaceConnection);
    void pause(PauseReason reason);
    void reconnect();
} 