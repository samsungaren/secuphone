package de.blinkt.openvpn.core;

import android.content.Context;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.blinkt.openvpn.VpnProfile;

/**
 * Stub implementation of ProfileManager from OpenVPN library
 */
public class ProfileManager {
    
    private static ProfileManager instance;
    private final Map<String, VpnProfile> profiles = new HashMap<>();
    
    private ProfileManager() {
        // Private constructor
    }
    
    public static ProfileManager getInstance(Context context) {
        if (instance == null) {
            instance = new ProfileManager();
        }
        return instance;
    }
    
    public void addProfile(VpnProfile profile) {
        profiles.put(profile.getUUID(), profile);
    }
    
    public void saveProfile(Context context, VpnProfile profile) {
        profiles.put(profile.getUUID(), profile);
    }
    
    public VpnProfile getProfileByName(String name) {
        for (VpnProfile profile : profiles.values()) {
            if (profile.getName().equals(name)) {
                return profile;
            }
        }
        return null;
    }
    
    public List<VpnProfile> getProfiles() {
        return new ArrayList<>(profiles.values());
    }
} 