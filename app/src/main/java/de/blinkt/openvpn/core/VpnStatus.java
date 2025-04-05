package de.blinkt.openvpn.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Stub implementation of VpnStatus from OpenVPN library
 */
public class VpnStatus {
    
    private static final List<StateListener> stateListeners = new ArrayList<>();
    private static final List<ByteCountListener> byteCountListeners = new ArrayList<>();
    
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
        stateListeners.add(listener);
    }
    
    public static void removeStateListener(StateListener listener) {
        stateListeners.remove(listener);
    }
    
    public static void addByteCountListener(ByteCountListener listener) {
        byteCountListeners.add(listener);
    }
    
    public static void removeByteCountListener(ByteCountListener listener) {
        byteCountListeners.remove(listener);
    }
    
    public static void updateState(String state, String message, int resId, ConnectionStatus level) {
        for (StateListener listener : stateListeners) {
            listener.updateState(state, message, resId, level);
        }
    }
    
    public static void updateByteCount(long in, long out, long diffIn, long diffOut) {
        for (ByteCountListener listener : byteCountListeners) {
            listener.updateByteCount(in, out, diffIn, diffOut);
        }
    }
} 