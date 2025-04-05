package de.blinkt.openvpn.core;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

/**
 * Stub implementation of OpenVPNStatusService from OpenVPN library
 */
public class OpenVPNStatusService extends Service {
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
    }
} 