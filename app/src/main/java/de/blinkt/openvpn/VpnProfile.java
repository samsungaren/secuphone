package de.blinkt.openvpn;

import android.content.Context;

/**
 * Stub implementation of VpnProfile from OpenVPN library
 */
public class VpnProfile {
    public String mName;
    public String mUsername;
    public String mPassword;
    public String mServerName;
    public int mServerPort;
    public String mDNS1;
    public String mDNS2;
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
    }
    
    public void setProfileCreator(String creator) {
        mProfileCreator = creator;
    }
} 