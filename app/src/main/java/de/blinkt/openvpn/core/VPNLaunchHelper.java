package de.blinkt.openvpn.core;

import android.content.Context;
import android.content.Intent;
import android.net.VpnService;

import de.blinkt.openvpn.VpnProfile;

/**
 * Stub implementation of VPNLaunchHelper from OpenVPN library
 */
public class VPNLaunchHelper {
    
    /**
     * Start OpenVPN service with the given profile
     * 
     * @param profile VpnProfile to use
     * @param context Application context
     */
    public static void startOpenVpn(VpnProfile profile, Context context) {
        Intent intent = VpnService.prepare(context);
        
        if (intent != null) {
            // VPN permission not yet granted
            // Normally we would show the intent to the user
            return;
        }
        
        Intent serviceIntent = new Intent(context, OpenVPNService.class);
        serviceIntent.putExtra("profileUUID", profile.getUUID());
        context.startService(serviceIntent);
    }
} 