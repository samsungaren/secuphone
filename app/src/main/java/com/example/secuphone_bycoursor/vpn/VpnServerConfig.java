package com.example.secuphone_bycoursor.vpn;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages VPN server configurations
 */
public class VpnServerConfig {
    private static final String TAG = "VpnServerConfig";
    private static final String CONFIG_FOLDER = "vpn_configs";
    private static final String PREF_SELECTED_SERVER = "selected_vpn_server";
    
    // Server identifier constants - keep only one real server
    public static final String SERVER_US = "main_server";
    
    // Maps server identifiers to display names
    private static final Map<String, String> SERVER_NAMES = new HashMap<>();
    static {
        SERVER_NAMES.put(SERVER_US, "MainServer (13.60.95.237)");
    }
    
    private static VpnServerConfig instance;
    private final Context context;
    private final File configDir;
    private final SharedPreferences preferences;
    
    // Current selected server
    private String selectedServer = SERVER_US;
    
    private VpnServerConfig(Context context) {
        this.context = context.getApplicationContext();
        this.configDir = new File(context.getFilesDir(), CONFIG_FOLDER);
        this.preferences = PreferenceManager.getDefaultSharedPreferences(context);
        
        // Create config directory if it doesn't exist
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        
        // Load previously selected server
        selectedServer = preferences.getString(PREF_SELECTED_SERVER, SERVER_US);
    }
    
    public static synchronized VpnServerConfig getInstance(Context context) {
        if (instance == null) {
            instance = new VpnServerConfig(context);
        }
        return instance;
    }
    
    /**
     * Get the list of available server identifiers
     */
    public List<String> getAvailableServers() {
        return new ArrayList<>(SERVER_NAMES.keySet());
    }
    
    /**
     * Get display name for a server
     */
    public String getServerDisplayName(String serverIdentifier) {
        return SERVER_NAMES.getOrDefault(serverIdentifier, "Unknown Server");
    }
    
    /**
     * Set the selected server
     */
    public void setSelectedServer(String serverIdentifier) {
        if (SERVER_NAMES.containsKey(serverIdentifier)) {
            selectedServer = serverIdentifier;
            preferences.edit().putString(PREF_SELECTED_SERVER, serverIdentifier).apply();
        }
    }
    
    /**
     * Get the currently selected server
     */
    public String getSelectedServer() {
        return selectedServer;
    }
    
    /**
     * Save OVPN configuration content to a file for a specific server
     */
    public boolean saveConfigForServer(String serverIdentifier, String configContent) {
        if (!SERVER_NAMES.containsKey(serverIdentifier)) {
            Log.e(TAG, "Invalid server identifier: " + serverIdentifier);
            return false;
        }
        
        File configFile = new File(configDir, serverIdentifier + ".ovpn");
        
        try (FileOutputStream fos = new FileOutputStream(configFile)) {
            fos.write(configContent.getBytes());
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error saving config file: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Import an OVPN configuration from a content URI
     */
    public boolean importConfigFromUri(String serverIdentifier, Uri uri) {
        if (!SERVER_NAMES.containsKey(serverIdentifier)) {
            Log.e(TAG, "Invalid server identifier: " + serverIdentifier);
            return false;
        }
        
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                return false;
            }
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder builder = new StringBuilder();
            String line;
            
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }
            
            inputStream.close();
            
            return saveConfigForServer(serverIdentifier, builder.toString());
        } catch (IOException e) {
            Log.e(TAG, "Error importing config from URI: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Check if a configuration exists for a server
     */
    public boolean hasConfigForServer(String serverIdentifier) {
        if (!SERVER_NAMES.containsKey(serverIdentifier)) {
            return false;
        }
        
        File configFile = new File(configDir, serverIdentifier + ".ovpn");
        return configFile.exists() && configFile.length() > 0;
    }
    
    /**
     * Get the content of a server's configuration file
     */
    public String getConfigContent(String serverIdentifier) {
        if (!hasConfigForServer(serverIdentifier)) {
            return null;
        }
        
        File configFile = new File(configDir, serverIdentifier + ".ovpn");
        
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    context.getContentResolver().openInputStream(Uri.fromFile(configFile))));
            
            StringBuilder builder = new StringBuilder();
            String line;
            
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }
            
            reader.close();
            return builder.toString();
        } catch (IOException e) {
            Log.e(TAG, "Error reading config file: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Delete a server's configuration file
     */
    public boolean deleteConfigForServer(String serverIdentifier) {
        if (!SERVER_NAMES.containsKey(serverIdentifier)) {
            return false;
        }
        
        File configFile = new File(configDir, serverIdentifier + ".ovpn");
        return configFile.delete();
    }
    
    /**
     * Get the IP address for this server
     */
    public String getServerIpPattern(String serverIdentifier) {
        return "13.60.95.237";
    }
} 