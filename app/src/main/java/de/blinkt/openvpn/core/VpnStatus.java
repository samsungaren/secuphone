package de.blinkt.openvpn.core;

import android.content.Context;
import android.util.Log;

import java.util.LinkedList;
import java.util.Locale;

/**
 * Status handler class for OpenVPN connection events
 */
public class VpnStatus {
    private static final String TAG = "VpnStatus";
    
    private static final LinkedList<StateListener> stateListeners = new LinkedList<>();
    private static final LinkedList<ByteCountListener> byteCountListeners = new LinkedList<>();
    
    private static ConnectionStatus connectionStatus = ConnectionStatus.LEVEL_NOTCONNECTED;
    
    /**
     * Interface for state change listeners
     */
    public interface StateListener {
        void updateState(String state, String logmessage, int localizedResId, ConnectionStatus level);
    }
    
    /**
     * Interface for byte count listeners
     */
    public interface ByteCountListener {
        void updateByteCount(long in, long out, long diffIn, long diffOut);
    }
    
    public static void addStateListener(StateListener listener) {
        if (!stateListeners.contains(listener)) {
            stateListeners.add(listener);
        }
    }
    
    public static void removeStateListener(StateListener listener) {
        stateListeners.remove(listener);
    }
    
    public static void addByteCountListener(ByteCountListener listener) {
        if (!byteCountListeners.contains(listener)) {
            byteCountListeners.add(listener);
        }
    }
    
    public static void removeByteCountListener(ByteCountListener listener) {
        byteCountListeners.remove(listener);
    }
    
    /**
     * Update connection state and notify listeners
     */
    public static void updateState(String state, String logmessage, int resId, ConnectionStatus status) {
        connectionStatus = status;
        Log.d(TAG, "VPN status updated: " + status + " - " + logmessage);
        
        for (StateListener listener : stateListeners) {
            listener.updateState(state, logmessage, resId, status);
        }
    }
    
    /**
     * Update state string for the VPN connection
     */
    public static void updateStateString(String prefix, String state, int localizedResId, ConnectionStatus level) {
        connectionStatus = level;
        Log.d(TAG, prefix + ": " + state + " - Status: " + level);
        
        // Forward update to normal updateState
        updateState(state, state, localizedResId, level);
    }
    
    /**
     * Update connection byte counts and notify listeners
     */
    public static void updateByteCount(long in, long out, long diffIn, long diffOut) {
        for (ByteCountListener listener : byteCountListeners) {
            listener.updateByteCount(in, out, diffIn, diffOut);
        }
    }
    
    /**
     * Get current connection status
     * @return Current VPN connection status
     */
    public static ConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }
    
    /**
     * Format bytes as human readable string
     */
    public static String humanReadableByteCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) return bytes + " B";
        
        int exp = (int)(Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
        return String.format(Locale.US, "%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }
} 