package de.blinkt.openvpn.core;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.IBinder;

import androidx.annotation.Nullable;

/**
 * Stub implementation of OpenVPNService from OpenVPN library
 */
public class OpenVPNService extends VpnService implements OpenVPNManagement {
    
    private static OpenVPNService instance;
    
    public static OpenVPNService getInstance() {
        if (instance == null) {
            instance = new OpenVPNService();
        }
        return instance;
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public void resume() {
        // Stub implementation 
    }
    
    @Override
    public boolean stopVPN(boolean replaceConnection) {
        // Stub implementation 
        return true;
    }
    
    @Override
    public void pause(PauseReason reason) {
        // Stub implementation 
    }
    
    @Override
    public void reconnect() {
        // Stub implementation 
    }
} 